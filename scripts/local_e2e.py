#!/usr/bin/env python3
"""Opt-in local Android E2E. No device reset, functional retries, or physical devices."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import traceback
import uuid
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_HOME = ROOT / "build/local-e2e"
APP = "com.abdev.partituraspdf"
CLASS = APP + ".storage.saf.SafLocalE2eTest"
STAGES = ("acquire", "restartAndRelease", "lossAndRestore", "restoredAfterRestart")
IMAGE = "system-images;android-26;google_apis;x86_64"


class Blocked(RuntimeError):
    """Missing infrastructure, unsafe identity, or incomplete execution evidence."""


class FunctionalFailure(RuntimeError):
    """A real executed instrumentation assertion failed."""


def execute(command, *, timeout=30, env=None, cwd=ROOT, binary=False, input_text=None):
    """No shell interpolation; bounded commands. Caller retains all output."""
    try:
        return subprocess.run([str(x) for x in command], cwd=cwd, env=env,
                              capture_output=True, text=not binary, timeout=timeout, input=input_text,
                              encoding=None if binary else "utf-8", errors=None if binary else "replace")
    except subprocess.TimeoutExpired as error:
        output = error.stdout or b""
        if isinstance(output, bytes):
            output = output.decode("utf-8", "replace")
        raise Blocked(f"Command timed out after {timeout}s: {command}\n{output}") from error
    except OSError as error:
        raise Blocked(f"Cannot execute {command[0]}: {error}") from error


def checked(command, **kwargs):
    result = execute(command, **kwargs)
    if result.returncode:
        raise Blocked(f"Command failed ({result.returncode}): {command}\n{result.stdout}\n{result.stderr}")
    return result.stdout.strip()


def sdk_tool(sdk, relative):
    path = sdk / relative
    if os.name == "nt":
        path = path.with_suffix(".bat" if "cmdline-tools" in relative else ".exe")
    if not path.is_file():
        raise Blocked(f"Missing SDK tool: {path}")
    return path


def safe_run_id(value):
    if not re.fullmatch(r"[a-f0-9]{32}", value):
        raise Blocked("Invalid run identity")
    return value


def fixture():
    manifest = json.loads((ROOT / "test-fixtures/manifest.json").read_text(encoding="utf-8"))
    item = next(x for x in manifest["files"] if x["id"] == "01")
    path = ROOT / "test-fixtures" / item["path"]
    if hashlib.sha256(path.read_bytes()).hexdigest() != item["sha256"]:
        raise Blocked("Canonical fixture hash mismatch; no device was modified")
    return path, item["sha256"]


@contextmanager
def exclusive(home):
    home.mkdir(parents=True, exist_ok=True)
    lock = home / "runner.lock"
    try:
        descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError as error:
        raise Blocked(f"Runner/provision already locked: {lock}. Review stale locks manually.") from error
    try:
        os.write(descriptor, str(os.getpid()).encode())
        os.close(descriptor)
        yield
    finally:
        lock.unlink()


def provision(home, sdk, port):
    """Explicit one-time creation in a private AVD home; never adopt or wipe an AVD."""
    if port < 5554 or port > 5682 or port % 2:
        raise Blocked("Emulator console port must be even, 5554..5682")
    with exclusive(home):
        state = home / "device.json"
        if state.exists() or (home / "avd").exists():
            raise Blocked("Existing provisioning found; refusing to overwrite or adopt it")
        manager = sdk_tool(sdk, "cmdline-tools/latest/bin/avdmanager")
        # Image installation/licenses are an explicit prerequisite, not automatic acceptance.
        if not (sdk / "system-images/android-26/google_apis/x86_64/package.xml").is_file():
            raise Blocked(f"Install and license {IMAGE} with sdkmanager first")
        avd_home = home / "avd"
        avd_home.mkdir()
        name = "PartiturasPDF_E2E_API26_" + uuid.uuid4().hex[:12]
        env = dict(os.environ, ANDROID_AVD_HOME=str(avd_home))
        # --device selects a known profile; explicit input avoids an interactive skin prompt.
        output = checked([manager, "create", "avd", "--name", name, "--package", IMAGE,
                          "--device", "pixel_c"], timeout=120, env=env, input_text="no\n")
        (home / "provision.log").write_text(output, encoding="utf-8")
        state.write_text(json.dumps({"version": 1, "name": name, "port": port,
                                     "image": IMAGE, "avd_home": str(avd_home)}, indent=2), encoding="utf-8")
        print(f"Provisioned dedicated AVD {name}; emulator-{port}. No emulator was started.")


def load_device(home):
    try:
        state = json.loads((home / "device.json").read_text(encoding="utf-8"))
        port = state["port"]
        if (state["version"] != 1 or state["image"] != IMAGE or not isinstance(port, int)
                or port % 2 or not 5554 <= port <= 5682
                or not re.fullmatch(r"PartiturasPDF_E2E_API26_[a-f0-9]{12}", state["name"])
                or Path(state["avd_home"]).resolve() != (home / "avd").resolve()):
            raise ValueError("Invalid dedicated AVD identity")
        config = home / "avd" / (state["name"] + ".avd") / "config.ini"
        if not config.is_file() or config.is_symlink() or not config.resolve().is_relative_to(home.resolve()):
            raise ValueError("Missing or unsafe dedicated AVD config")
        values = dict(tuple(part.strip() for part in line.split("=", 1))
                      for line in config.read_text().splitlines() if "=" in line)
        index = home / "avd" / (state["name"] + ".ini")
        index_values = dict(tuple(part.strip() for part in line.split("=", 1))
                            for line in index.read_text().splitlines() if "=" in line)
        if Path(index_values.get("path", "")).resolve() != config.parent.resolve():
            raise ValueError("AVD index points outside its dedicated directory")
        if values.get("image.sysdir.1", "").replace("\\", "/").rstrip("/") != "system-images/android-26/google_apis/x86_64":
            raise ValueError("AVD does not use the approved API 26 image")
        return state
    except (OSError, KeyError, ValueError, TypeError) as error:
        raise Blocked(f"Dedicated AVD is not safely provisioned: {error}. Run provision first.") from error


def parse_instrumentation(output, method):
    """Validate AndroidJUnitRunner protocol, not adb/Gradle's often-successful exit code."""
    bundles, bundle, key = [], {}, None
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            item = line.split(": ", 1)[1]
            if "=" in item:
                key, value = item.split("=", 1)
                bundle[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            try:
                code = int(line.split(": ", 1)[1])
            except ValueError as error:
                raise Blocked("Malformed instrumentation status") from error
            bundles.append((code, bundle))
            bundle, key = {}, None
        elif key == "stack":
            bundle[key] += "\n" + line
    starts = [b for code, b in bundles if code == 1 and b.get("class") == CLASS and b.get("test") == method]
    ends = [(c, b) for c, b in bundles if c != 1 and b.get("class") == CLASS and b.get("test") == method]
    unexpected = [b for c, b in bundles if "test" in b and (b.get("class") != CLASS or b["test"] != method)]
    if len(starts) != 1 or len(ends) != 1 or unexpected:
        raise Blocked(f"Missing, duplicate or unexpected test results for {method}")
    code, data = ends[0]
    if code in (-1, -2):
        raise FunctionalFailure(data.get("stack", f"JUnit failure in {method}"))
    if (code != 0 or "INSTRUMENTATION_CODE: -1" not in output or
            "INSTRUMENTATION_FAILED" in output or "shortMsg=" in output or "FAILURES!!!" in output):
        raise Blocked(f"Instrumentation incomplete, skipped, or crashed: {method}")
    return 1


class Runner:
    def __init__(self, home, sdk, suite="saf", inject_failure=False):
        self.home, self.sdk, self.suite = home.resolve(), sdk.resolve(), suite
        self.run_id = uuid.uuid4().hex
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.out = self.home / "reports" / (stamp + "-" + self.run_id)
        self.out.mkdir(parents=True, exist_ok=False)
        self.summary = {"run": self.run_id, "suite": suite, "status": "BLOCKED", "tests": [], "expected_tests": len(STAGES) if suite == "saf" else 0,
                        "android_e2e": "NOT RUN", "commit": "UNKNOWN",
                        "device": {"status": "NOT SELECTED"}, "cleanup": [], "diagnostics": []}
        self.inject = "after-grant" if inject_failure else "none"
        self.state = self.adb = self.serial = None
        self.process = self.emulator_log = None
        self.fixture_created = self.installed = self.identified = False
        self.settings = {}

    def record(self, name, value):
        (self.out / name).write_text(value, encoding="utf-8")

    def raw_adb(self, *args, **kwargs):
        return checked([self.adb, "-s", self.serial, *args], **kwargs)

    def identify(self, *, boot=False):
        if not self.state or not self.adb:
            raise Blocked("No dedicated device selected")
        name = self.raw_adb("emu", "avd", "name").splitlines()
        if not name or name[0].strip() != self.state["name"]:
            raise Blocked("Emulator port belongs to a different AVD; refusing device operations")
        if self.raw_adb("shell", "getprop", "ro.kernel.qemu") != "1":
            raise Blocked("Device is not an emulator")
        if self.raw_adb("shell", "getprop", "ro.build.version.sdk") != "26":
            raise Blocked("Dedicated emulator is not API 26")
        if boot and self.raw_adb("shell", "getprop", "sys.boot_completed") != "1":
            raise Blocked("Emulator boot is incomplete")
        self.identified = True

    def adb_command(self, *args, **kwargs):
        self.identify()
        return self.raw_adb(*args, **kwargs)

    def gradle(self, name, tasks, properties=(), timeout=900):
        script = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
        command = ([str(script)] if os.name == "nt" else ["bash", str(script)])
        command += ["-I", str(ROOT / "scripts/local-e2e.init.gradle"), *tasks,
                    "--console=plain", "--max-workers=4", *properties]
        env = dict(os.environ, LOCAL_E2E_ADB=str(self.adb or ""))
        try:
            result = execute(command, timeout=timeout, env=env)
        except Blocked as error:
            self.record(name, str(error))
            raise
        output = result.stdout + "\n" + result.stderr
        self.record(name, output)
        # The instrumentation parser is also required: adb can return 0 for failed tests.
        if result.returncode:
            raise Blocked(f"Gradle failed ({result.returncode}); see {name}")
        return output

    def stage(self, method, *, cleanup=False):
        self.identify(boot=True)
        label = ("cleanup-" if cleanup else "") + method
        result = {"name": method, "status": "BLOCKED"}
        try:
            output = self.gradle(label + ".log", [":app:localE2eStage"], [
                f"-PlocalE2eSerial={self.serial}", f"-PlocalE2eStage={method}",
                f"-PlocalE2eRun={self.run_id}", f"-PlocalE2eSha256={self.sha}",
                f"-PlocalE2eFailure={self.inject}"], timeout=180)
            parse_instrumentation(output, method)
            result["status"] = "PASS"
        except FunctionalFailure as error:
            result.update(status="FAIL", detail=str(error))
            raise
        except Blocked as error:
            result["detail"] = str(error)
            raise
        finally:
            if cleanup:
                self.summary["cleanup"].append(result)
            else:
                self.summary["tests"].append(result)

    def connect(self):
        self.state = load_device(self.home)
        self.adb = sdk_tool(self.sdk, "platform-tools/adb")
        self.serial = f"emulator-{self.state['port']}"
        self.summary["device"] = dict(self.state, serial=self.serial, api=26)
        listing = checked([self.adb, "devices", "-l"])
        self.record("adb-initial.txt", listing)
        if any(line.split()[0] == self.serial for line in listing.splitlines() if line.split()):
            self.identify()  # Do not touch a different AVD occupying the port.
        else:
            emulator = sdk_tool(self.sdk, "emulator/emulator")
            self.emulator_log = (self.out / "emulator.log").open("wb")
            env = dict(os.environ, ANDROID_AVD_HOME=self.state["avd_home"])
            self.process = subprocess.Popen([str(emulator), "-avd", self.state["name"],
                "-port", str(self.state["port"]), "-no-snapshot-load", "-no-snapshot-save",
                "-no-audio", "-no-boot-anim"], stdout=self.emulator_log,
                stderr=subprocess.STDOUT, env=env)
        deadline = time.monotonic() + 240
        last_error = "not online"
        while time.monotonic() < deadline:
            if self.process and self.process.poll() is not None:
                raise Blocked("Dedicated emulator crashed; see emulator.log")
            try:
                self.identify(boot=True)
                break
            except Blocked as error:
                last_error = str(error)
                # Polling boot readiness is not a retry of a functional test.
                time.sleep(2)
        else:
            raise Blocked(f"Emulator boot timeout: {last_error}")
        locale = self.raw_adb("shell", "getprop", "persist.sys.locale")
        if not locale:
            locale = self.raw_adb("shell", "getprop", "ro.product.locale")
        if locale.replace("_", "-") != "en-US":
            raise Blocked(f"Dedicated AVD must use en-US; actual locale: {locale!r}")
        self.summary["device"]["locale"] = locale
        self.record("device-properties.txt", self.raw_adb("shell", "getprop"))
        # Only this provisioned AVD is changed; restore each original setting after evidence capture.
        for namespace, key, value in (("global", "window_animation_scale", "0"),
                ("global", "transition_animation_scale", "0"), ("global", "animator_duration_scale", "0"),
                ("system", "accelerometer_rotation", "0"), ("system", "user_rotation", "0"),
                ("system", "screen_off_timeout", "600000")):
            self.settings[(namespace, key)] = self.adb_command("shell", "settings", "get", namespace, key)
            self.adb_command("shell", "settings", "put", namespace, key, value)
        self.summary["configuration"] = "en-US, natural orientation, animations off, 10 minute display timeout"

    def collect(self, prefix=""):
        """Best effort; one failing capture never suppresses subsequent host/device evidence."""
        if self.adb:
            try:
                self.record(prefix + "adb-final.txt", checked([self.adb, "devices", "-l"]))
            except Exception as error:
                self.summary["diagnostics"].append(f"adb listing: {error}")
        if not self.identified:
            return
        captures = {
            "logcat.txt": ("logcat", "-d", "-v", "threadtime", "-t", "2000"),
            "activity.txt": ("shell", "dumpsys", "activity", "activities"),
            "package.txt": ("shell", "dumpsys", "package", APP),
            "emulator-identity.txt": ("emu", "avd", "name"),
        }
        for name, command in captures.items():
            try:
                self.record(prefix + name, self.adb_command(*command))
            except Exception as error:
                self.summary["diagnostics"].append(f"{name}: {error}")
        try:
            self.identify()
            result = execute([self.adb, "-s", self.serial, "exec-out", "screencap", "-p"], binary=True)
            if result.returncode or not result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
                raise Blocked("screencap did not return a PNG")
            (self.out / (prefix + "screenshot.png")).write_bytes(result.stdout)
        except Exception as error:
            self.summary["diagnostics"].append(f"screenshot: {error}")
        try:
            path = f"/data/local/tmp/local-e2e-{self.run_id}.xml"
            self.adb_command("shell", "uiautomator", "dump", path, timeout=30)
            self.adb_command("pull", path, str(self.out / (prefix + "hierarchy.xml")))
            self.adb_command("shell", "rm", path)
        except Exception as error:
            self.summary["diagnostics"].append(f"hierarchy: {error}")

    def clean(self):
        # Evidence has already been captured. Never uninstall, clear data, wipe, or release a broad URI.
        if self.installed:
            try:
                self.adb_command("shell", "am", "force-stop", APP)
                self.stage("cleanupOwnedGrant", cleanup=True)
                self.adb_command("shell", "am", "force-stop", APP)
            except Exception as error:
                self.summary["cleanup"].append({"status": "BLOCKED", "detail": str(error)})
        if self.fixture_created:
            try:
                path = self.remote_folder()
                self.adb_command("shell", "rm", "-r", path)
                self.summary["cleanup"].append({"name": "fixture", "status": "PASS"})
            except Exception as error:
                self.summary["cleanup"].append({"name": "fixture", "status": "BLOCKED", "detail": str(error)})
        for (namespace, key), value in self.settings.items():
            try:
                arguments = ("delete", namespace, key) if value == "null" else ("put", namespace, key, value)
                self.adb_command("shell", "settings", *arguments)
            except Exception as error:
                self.summary["cleanup"].append({"name": key, "status": "BLOCKED", "detail": str(error)})
        if any(item["status"] != "PASS" for item in self.summary["cleanup"]):
            self.collect(prefix="cleanup-")
        # Stop only the exact emulator child this invocation launched, not an existing emulator.
        if self.process and self.process.poll() is None:
            try:
                self.identify()
                self.raw_adb("emu", "kill")
            except Blocked:
                pass  # The owned process handle remains safe even if ADB is offline.
            try:
                self.process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                if os.name == "nt":
                    # emulator.exe may have a qemu child; terminate only this invocation's tree.
                    checked(["taskkill", "/PID", str(self.process.pid), "/T", "/F"], timeout=20)
                else:
                    self.process.terminate()
                try:
                    self.process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                    self.process.wait(timeout=10)
        if self.emulator_log:
            self.emulator_log.close()

    def remote_folder(self):
        return "/sdcard/Download/PartiturasE2E-" + safe_run_id(self.run_id)

    def workflow(self):
        if self.suite != "saf":
            raise Blocked(f"Suite {self.suite!r} is not implemented. No tests executed.")
        source, self.sha = fixture()
        self.summary["fixture_sha256"] = self.sha
        self.gradle("gradle-build.log", [":app:assembleDebug", ":app:assembleDebugAndroidTest"])
        # Preserve exactly the installed APKs, avoiding accidental reuse from another build.
        apks = [ROOT / "app/build/outputs/apk/debug/app-debug.apk",
                ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]
        for apk in apks:
            if not apk.is_file():
                raise Blocked(f"Missing built APK: {apk}")
            shutil.copy2(apk, self.out / apk.name)
            self.summary.setdefault("apks", {})[apk.name] = hashlib.sha256(
                (self.out / apk.name).read_bytes()).hexdigest()
        try:
            with zipfile.ZipFile(self.out / apks[1].name) as archive:
                descriptor = ("L" + CLASS.replace(".", "/") + ";").encode()
                if not any(descriptor in archive.read(name) for name in archive.namelist() if name.endswith(".dex")):
                    raise Blocked("Opt-in SAF class is absent from the test APK; no device was modified")
        except zipfile.BadZipFile as error:
            raise Blocked("Invalid instrumentation APK") from error
        self.connect()
        for apk in apks:
            self.adb_command("install", "-r", str(self.out / apk.name), timeout=120)
        self.installed = True
        folder = self.remote_folder()
        # mkdir (without -p) refuses an existing folder; only remove it after this succeeds.
        self.adb_command("shell", "mkdir", "-p", "/sdcard/Download")
        self.adb_command("shell", "mkdir", folder)
        self.fixture_created = True
        self.adb_command("push", str(source), folder + "/fixture.pdf")
        self.adb_command("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                         "-d", "file://" + folder + "/fixture.pdf")
        for method in STAGES:
            self.adb_command("shell", "am", "force-stop", APP)
            # Host independently proves that grant revocation did not delete the PDF.
            actual = self.adb_command("shell", "sha256sum", folder + "/fixture.pdf").split()[0]
            if actual != self.sha:
                raise FunctionalFailure("Fixture copy disappeared or changed before " + method)
            self.stage(method)
        if len(self.summary["tests"]) != len(STAGES):
            raise Blocked("Incomplete SAF stages")
        self.summary.update(status="PASS", android_e2e="PASS")

    def reports(self):
        tests = self.summary["tests"]
        self.summary["counts"] = {status: sum(x["status"] == status for x in tests)
                                  for status in ("PASS", "FAIL", "BLOCKED")}
        executed = [item for item in tests if item["status"] in ("PASS", "FAIL")]
        self.summary["android_e2e"] = ("FAIL" if any(x["status"] == "FAIL" for x in executed)
            else "PASS" if len(executed) == len(STAGES) and len(tests) == len(STAGES)
            else "INCOMPLETE" if executed else "NOT RUN")
        # Infrastructure errors are explicit JUnit errors, never fabricated test PASS results.
        cases = list(tests)
        if not tests or (self.summary["status"] != "PASS" and not any(x["status"] != "PASS" for x in tests)):
            cases.append({"name": "host-failure" if self.summary["status"] == "FAIL" else "infrastructure",
                          "status": "FAIL" if self.summary["status"] == "FAIL" else "BLOCKED",
                          "detail": self.summary.get("detail", "No tests executed")})
        suite = ET.Element("testsuite", name="local-e2e-" + self.suite, tests=str(len(cases)),
            failures=str(sum(x["status"] == "FAIL" for x in cases)),
            errors=str(sum(x["status"] == "BLOCKED" for x in cases)), skipped="0")
        for item in cases:
            case = ET.SubElement(suite, "testcase", classname=CLASS, name=item["name"])
            if item["status"] != "PASS":
                ET.SubElement(case, "failure" if item["status"] == "FAIL" else "error",
                              message=item.get("detail", "Incomplete instrumentation"))
        ET.ElementTree(suite).write(self.out / "junit.xml", encoding="utf-8", xml_declaration=True)
        ET.parse(self.out / "junit.xml")  # A missing/malformed XML can never become PASS.
        self.record("summary.json", json.dumps(self.summary, indent=2))
        self.record("index.html", "<!doctype html><meta charset='utf-8'><title>Local E2E</title>"
                    + "<h1>" + html.escape(self.summary["status"]) + "</h1><pre>"
                    + html.escape(json.dumps(self.summary, indent=2)) + "</pre>")

    def run(self):
        code = 2
        self.record("gradle-build.log", "NOT RUN\n")
        try:
            self.summary["commit"] = checked(["git", "rev-parse", "HEAD"])
            self.summary["working_tree"] = checked(["git", "status", "--porcelain"])
            with exclusive(self.home):
                try:
                    self.workflow()
                    code = 0
                except FunctionalFailure as error:
                    self.summary.update(status="FAIL", android_e2e="FAIL", detail=str(error))
                    code = 1
                except (Exception, KeyboardInterrupt) as error:
                    self.record("host-error.log", traceback.format_exc())
                    self.summary.update(status="BLOCKED", detail=f"{type(error).__name__}: {error}")
                    code = 2
                finally:
                    if code:
                        self.collect()
                    # Checkpoint host evidence BEFORE cleanup even if adb has disappeared.
                    self.reports()
                    try:
                        self.clean()
                    except Exception as error:
                        self.summary["cleanup"].append({"status": "BLOCKED", "detail": str(error)})
                    if any(x["status"] != "PASS" for x in self.summary["cleanup"]):
                        if code == 0:
                            self.summary.update(status="BLOCKED", detail="Cleanup incomplete; inspect owned artifacts")
                            code = 2
        except (Exception, KeyboardInterrupt) as error:
            self.record("host-error.log", traceback.format_exc())
            self.summary.update(status="BLOCKED", detail=f"{type(error).__name__}: {error}")
            code = 2
        finally:
            self.reports()
        print(f"{self.summary['status']}: {self.out}")
        return code


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", type=Path, default=DEFAULT_HOME, help="Dedicated AVD state and unique reports")
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "missing-sdk"))))
    commands = parser.add_subparsers(dest="command", required=True)
    setup = commands.add_parser("provision", help="Explicitly create a new private API 26 AVD; never overwrite")
    setup.add_argument("--port", type=int, default=5580)
    run = commands.add_parser("run")
    run.add_argument("--suite", choices=("saf", "reader", "library", "full", "coverage"), required=True)
    run.add_argument("--inject-failure", action="store_true", help="Deliberate assertion failure after real picker authorization")
    args = parser.parse_args(argv)
    if args.command == "provision":
        try:
            provision(args.home.resolve(), args.sdk.resolve(), args.port)
            return 0
        except Blocked as error:
            print(f"BLOCKED: {error}", file=sys.stderr)
            return 2
    return Runner(args.home, args.sdk, args.suite, args.inject_failure).run()


if __name__ == "__main__":
    sys.exit(main())
