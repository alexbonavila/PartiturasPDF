"""Verified native process experiments; manual raw-results mode or mandatory CI validation."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
import pdfium_diagnostic_evidence as evidence

APP = "com.abdev.partituraspdf"
CLASS = APP + ".reader.ReaderPdfiumProcessStateTest"
ANDROID = "{http://schemas.android.com/apk/res/android}"
SCENARIOS = {
    "a_cleanDirectRepeatedValidOpens": ["01", "01", "02"],
    "k_cleanAdapterRepeatedValidOpens": ["01", "01", "02"],
    "b_corruptThenValidDirect": ["01", "15", "01"],
    "c_encryptedThenValidDirect": ["01", "14", "01"],
    "d_freshProcessRecoveryDirect": ["01", "02"],
    "e_corruptThenValidAdapter": ["01", "15", "01"],
    "f_encryptedThenValidAdapter": ["01", "14", "01"],
    "g_corruptEncryptedThenValidDirect": ["01", "15", "14", "01"],
    "h_corruptEncryptedThenValidAdapter": ["01", "15", "14", "01"],
    "i_encryptedCorruptThenValidDirect": ["01", "14", "15", "01"],
    "j_encryptedCorruptThenValidAdapter": ["01", "14", "15", "01"],
}


class Blocked(RuntimeError):
    pass


def manifest_identity(path):
    root = ET.parse(path).getroot()
    entries = root.findall("instrumentation")
    if len(entries) != 1:
        raise Blocked("Expected exactly one merged instrumentation entry")
    package = root.get("package")
    runner = entries[0].get(ANDROID + "name")
    target = entries[0].get(ANDROID + "targetPackage")
    if (package, target, runner) != (APP + ".test", APP, "androidx.test.runner.AndroidJUnitRunner"):
        raise Blocked("Merged manifest does not identify the authorized project/runner")
    return package, target, package + "/" + runner


def verify_emulator(serial, qemu, api, confirmation):
    if not re.fullmatch(r"emulator-[0-9]+", serial) or qemu.strip() != "1":
        raise Blocked("Only an explicitly selected disposable Android emulator is authorized")
    if api.strip() not in {"26", "27", "36"} or confirmation != serial:
        raise Blocked("Wrong API or missing explicit emulator identity confirmation")


def validate_run(output, method, token, before_ms, after_ms, previous, adb_exit=0):
    """Executed JUnit evidence and process identity are independent of adb exit status."""
    starts = re.findall(r"^INSTRUMENTATION_STATUS_CODE: 1\s*$", output, re.M)
    completed = re.findall(r"^INSTRUMENTATION_STATUS_CODE: (0|-[1-4])\s*$", output, re.M)
    classes = re.findall(r"^INSTRUMENTATION_STATUS: class=(.+)$", output, re.M)
    methods = re.findall(r"^INSTRUMENTATION_STATUS: test=(.+)$", output, re.M)
    data = re.findall(r"^INSTRUMENTATION_RESULT: readerPdfiumEvidence=(.+)$", output, re.M)
    if (len(starts) != 1 or len(completed) != 1 or not classes or not methods or
            set(classes) != {CLASS} or set(methods) != {method} or len(data) != 1 or
            not re.search(r"^INSTRUMENTATION_CODE: -1\s*$", output, re.M)):
        raise Blocked("Missing, incomplete, crashed, or unexpected executed-test evidence")
    if completed[0] in {"-3", "-4"}:
        raise Blocked("Skipped/assumed experiment is not execution acceptance")
    rows = json.loads(data[0])
    if not rows:
        raise Blocked("No native attempt evidence")
    identity = (rows[0]["pid"], rows[0]["processStartElapsedMs"])
    # /proc/uptime and Android process-start ticks have finite clock resolution. PID absence
    # after force-stop, a start inside this launch interval, and no reused identity are ALL needed.
    if identity in previous or identity[0] <= 0 or not before_ms - 20 <= identity[1] <= after_ms + 20:
        raise Blocked("Process was reused or process start is outside the verified launch interval")
    expected = SCENARIOS[method]
    if len(rows) > len(expected):
        raise Blocked("Unexpected extra PDF attempts")
    for index, row in enumerate(rows):
        if ((row["pid"], row["processStartElapsedMs"]) != identity or row["class"] != CLASS or
                row["test"] != method or row["invocation"] != token or row["sequence"] != index + 1 or
                row["fixture"] != expected[index] or row["api"] not in {26, 27, 36}):
            raise Blocked("Attempt order, process, or invocation evidence mismatch")
    status = "PASS" if completed[0] == "0" else "FAIL"
    if status == "PASS" and len(rows) != len(expected):
        raise Blocked("Passing JUnit result has an incomplete native sequence")
    if adb_exit and status != "FAIL":
        raise Blocked("Nonzero transport exit without trustworthy executed JUnit failure")
    return {"status": status, "adbExit": adb_exit, "processIdentity": identity, "attempts": rows,
            "sequenceComplete": len(rows) == len(expected), "processIsolationVerified": True}


class Runner:
    def __init__(self, adb, serial):
        self.adb, self.serial = adb, serial

    def call(self, *args, allow_absent=False, timeout=30):
        command = [self.adb, "-s", self.serial, *args]
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, timeout=timeout, encoding="utf-8", errors="replace")
        if result.returncode and not (allow_absent and result.returncode == 1 and not result.stdout.strip()):
            raise Blocked(f"ADB command failed: {args}: {result.stdout}")
        return result.stdout

    def pids(self, package):
        text = self.call("shell", "pidof", package, allow_absent=True).strip()
        if text and not re.fullmatch(r"[0-9 ]+", text):
            raise Blocked("Cannot establish process identity")
        return text.split()

    def stop_and_verify(self, packages):
        if set(packages) != {APP, APP + ".test"}:
            raise Blocked("Refusing to stop unrelated packages")
        before = {package: self.pids(package) for package in packages}
        for package in packages:
            self.call("shell", "am", "force-stop", package)
        remaining = {package: self.pids(package) for package in packages}
        if any(remaining.values()):
            raise Blocked("Previous relevant process has not exited")
        return {"beforeStop": before, "afterStop": remaining}

    def installed_hash(self, package):
        paths = self.call("shell", "pm", "path", package).strip().splitlines()
        if len(paths) != 1 or not re.fullmatch(r"package:/[A-Za-z0-9_./+=~\-]+\.apk", paths[0]):
            raise Blocked("Expected one project APK; split/unknown deployment is not verified")
        result = self.call("shell", "sha256sum", paths[0][8:]).split()
        if not result or not re.fullmatch(r"[0-9a-f]{64}", result[0]):
            raise Blocked("Installed APK digest unavailable")
        return result[0]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--confirm", help="Explicitly confirm that this serial is an owned disposable emulator")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--ci", action="store_true", help="Run all separately assigned tests and validate known-defect/recovery evidence")
    parser.add_argument("--expected-api", type=int, choices=[26, 27, 36])
    parser.add_argument("--standard-results", type=Path, help="Required standard JUnit inventory for CI mode")
    parser.add_argument("--standard-started-ms", type=int, help="Host timestamp before the standard Gradle invocation")
    args = parser.parse_args(argv)
    if args.ci and (not args.confirm or args.expected_api is None or args.standard_results is None or args.standard_started_ms is None or args.standard_started_ms <= 0):
        raise Blocked("CI requires explicit emulator confirmation, API and fresh standard results")
    repo = next(parent for parent in Path(__file__).resolve().parents if (parent / "gradlew").is_file())
    manifest = repo / "app/build/intermediates/merged_manifest/debugAndroidTest/mergeDebugAndroidTestManifest/AndroidManifest.xml"
    test, target, component = manifest_identity(manifest)
    runner = Runner(args.adb, args.serial)
    # All preflight operations are read-only. No install, AVD creation, clearing or uninstalling.
    qemu = runner.call("shell", "getprop", "ro.kernel.qemu")
    api = runner.call("shell", "getprop", "ro.build.version.sdk")
    avd = runner.call("emu", "avd", "name")
    print(f"Selected {args.serial}; API {api.strip()}; AVD {avd.strip()}")
    confirmation = args.confirm or input(f"Type {args.serial} to confirm a disposable emulator: ")
    verify_emulator(args.serial, qemu, api, confirmation)
    if args.expected_api is not None and int(api) != args.expected_api:
        raise Blocked("Selected emulator API differs from the mandatory CI API")
    installed = runner.call("shell", "pm", "list", "instrumentation").splitlines()
    if f"instrumentation:{component} (target={target})" not in installed:
        raise Blocked("Installed instrumentation does not match merged manifest")
    apk_hashes = {}
    for package, relative in [(target, "app/build/outputs/apk/debug/app-debug.apk"),
                              (test, "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")]:
        digest = hashlib.sha256((repo / relative).read_bytes()).hexdigest()
        if runner.installed_hash(package) != digest:
            raise Blocked("Installed APK differs from this checkout's build; deploy both APKs first")
        apk_hashes[package] = digest
    image = runner.call("shell", "getprop", "ro.build.fingerprint").strip()
    dirty = subprocess.run(["git", "status", "--porcelain"], cwd=repo, check=True,
                           stdout=subprocess.PIPE, text=True).stdout
    if dirty:
        raise Blocked("Commit/review the checkout before claiming an exact revision; no device mutation performed")
    revision = subprocess.run(["git", "rev-parse", "HEAD"], cwd=repo, check=True,
                              stdout=subprocess.PIPE, text=True).stdout.strip()
    root = repo / "build/reader-pdfium-controlled" / (time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()) + "-" + uuid.uuid4().hex)
    root.mkdir(parents=True, exist_ok=False)
    summary = {"revision": revision, "serial": args.serial, "api": api.strip(),
               "component": component, "avd": avd.strip(), "image": image, "apkSha256": apk_hashes, "runs": [], "status": "BLOCKED"}
    inventory = evidence.test_inventory(repo, SCENARIOS) if args.ci else None
    fixtures = {row["id"]: row for row in json.loads((repo / "test-fixtures/manifest.json").read_text())["files"]}
    previous = set()
    last_after_ms = -1
    order = list(SCENARIOS)
    # Recovery is separately invoked AFTER every negative sequence for both engines.
    order = order[:2] + [item for method in order[2:] if method != "d_freshProcessRecoveryDirect"
                         for item in (method, "d_freshProcessRecoveryDirect", "k_cleanAdapterRepeatedValidOpens")]
    selected_tests = evidence.invocation_plan(SCENARIOS) if args.ci else [CLASS + "#" + method for method in order]
    try:
        for number, selected in enumerate(selected_tests):
            selected_class, method = selected.split("#")
            directory = root / f"{number:02d}-{method}"
            directory.mkdir()
            entry = {"method": method, "selected": selected, "api": int(api), "revision": revision, "status": "BLOCKED"}
            summary["runs"].append(entry)
            if runner.call("emu", "avd", "name") != avd or runner.call("shell", "getprop", "ro.build.fingerprint").strip() != image:
                raise Blocked("Selected emulator identity changed; refusing further mutations")
            entry["processes"] = runner.stop_and_verify((target, test))
            before_ms = int(float(runner.call("shell", "cat", "/proc/uptime").split()[0]) * 1000)
            if before_ms < last_after_ms - 20:
                raise Blocked("Device reboot/clock reset cannot establish process-only recovery")
            token = uuid.uuid4().hex
            command = [args.adb, "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
                       "-e", "class", selected, "-e", "readerProcessRun", token, component]
            try:
                result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        text=True, timeout=180, encoding="utf-8", errors="replace")
                (directory / "instrumentation.txt").write_text(result.stdout, encoding="utf-8")
                after_ms = int(float(runner.call("shell", "cat", "/proc/uptime").split()[0]) * 1000)
                if selected_class == CLASS:
                    entry.update(validate_run(result.stdout, method, token, before_ms, after_ms, previous, result.returncode))
                    if any(row["api"] != int(api) for row in entry["attempts"]):
                        raise Blocked("Native evidence API does not match selected emulator")
                if args.ci:
                    observed = evidence.completion(result.stdout, selected, token, before_ms, after_ms, previous, int(api))
                    if selected_class == CLASS and tuple(entry["processIdentity"]) != tuple(observed["processIdentity"]):
                        raise Blocked("Rule/process-operation identity mismatch")
                    entry.update(observed)
                    if result.returncode and entry["status"] != "FAIL":
                        raise Blocked("Transport error without complete failing JUnit evidence")
                    evidence.write_junit(directory / "junit.xml", selected, entry, result.stdout)
                previous.add(tuple(entry["processIdentity"]))
                last_after_ms = after_ms
                entry.update({"beforeLaunchElapsedMs": before_ms, "afterRunElapsedMs": after_ms, "invocation": token})
            except subprocess.TimeoutExpired as failure:
                raw = failure.stdout or b""
                (directory / "instrumentation.txt").write_bytes(raw if isinstance(raw, bytes) else raw.encode())
                raise Blocked("Instrumentation timeout; no completion accepted") from failure
            finally:
                log = runner.call("logcat", "-d", "-v", "threadtime", "ReaderPdfiumProcess:I",
                                  "ReaderNativeDifferential:I", "PdfRenderer:V", "PdfUtils:V", "System.err:W", "AndroidRuntime:E", "*:S")
                (directory / "logcat.txt").write_text(log, encoding="utf-8")
                (directory / "run.json").write_text(json.dumps(entry, indent=2), encoding="utf-8")
        if args.ci:
            summary["diagnostics"] = evidence.classify(summary["runs"], int(api), SCENARIOS, fixtures)
            summary["inventory"] = inventory
            summary["standardAcceptance"] = evidence.verify_standard(args.standard_results, inventory, args.standard_started_ms)
            summary["status"] = "DIAGNOSTICS_VALIDATED"
        else:
            summary["status"] = "FAIL" if any(run["status"] == "FAIL" for run in summary["runs"]) else "PASS"
    except (Blocked, KeyError, ValueError, ET.ParseError) as failure:
        summary["status"] = "BLOCKED"
        summary["blocker"] = str(failure)
    finally:
        (root / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
        print(f"{summary['status']}: {root}")
    return {"PASS": 0, "DIAGNOSTICS_VALIDATED": 0, "FAIL": 1, "BLOCKED": 2}[summary["status"]]


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (Blocked, FileNotFoundError, subprocess.TimeoutExpired, EOFError, ET.ParseError) as error:
        print(f"BLOCKED: {error}")
        raise SystemExit(2)
