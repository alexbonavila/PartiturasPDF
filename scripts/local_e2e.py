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
import platform
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import time
import traceback
import uuid
import xml.etree.ElementTree as ET
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_HOME = ROOT / "build/local-e2e"
APP = "com.abdev.partituraspdf"
CLASS = APP + ".storage.saf.SafLocalE2eTest"
STAGES = ("acquire", "restartAndRelease", "lossAndRestore", "restoredAfterRestart")
HOST_ABIS = {
    ("Darwin", "arm64"): "arm64-v8a",
    ("Darwin", "x86_64"): "x86_64",
    ("Windows", "x86_64"): "x86_64",
    ("Linux", "x86_64"): "x86_64",
}


class Blocked(RuntimeError):
    """Missing infrastructure, unsafe identity, or incomplete execution evidence."""


class AdbTransportError(Blocked):
    """Retryable transport loss, only for explicitly read-only readiness operations."""


class BootPending(Blocked):
    """Owned emulator is identified but Android has not finished booting."""


class FunctionalFailure(RuntimeError):
    """A real executed instrumentation assertion failed."""


def execute(command, *, timeout=30, env=None, cwd=ROOT, binary=False, input_text=None):
    """Bounded argv commands. Reject CMD expansion characters in Windows batch launches."""
    if os.name == "nt" and str(command[0]).lower().endswith((".bat", ".cmd")):
        if any(re.search(r'[&|<>^%!\r\n"]', str(argument)) for argument in command):
            raise Blocked("Unsafe CMD metacharacter in batch arguments; use SDK/project paths without & | < > ^ % ! or quotes")
    try:
        return subprocess.run([str(x) for x in command], cwd=cwd, env=env,
                              capture_output=True, text=not binary, timeout=timeout, input=input_text,
                              encoding=None if binary else "utf-8", errors=None if binary else "replace")
    except subprocess.TimeoutExpired as error:
        chunks = [error.stdout or b"", error.stderr or b""]
        output = "\n".join(part.decode("utf-8", "replace") if isinstance(part, bytes) else part for part in chunks)
        raise Blocked(f"Command timed out after {timeout}s: {command}\n{output}") from error
    except OSError as error:
        raise Blocked(f"Cannot execute {command[0]}: {error}") from error


def checked(command, **kwargs):
    result = execute(command, **kwargs)
    if result.returncode:
        raise Blocked(f"Command failed ({result.returncode}): {command}\n{result.stdout}\n{result.stderr}")
    return result.stdout.strip()


def sdk_tool(sdk, relative):
    suffix = (".bat" if "cmdline-tools" in relative else ".exe") if platform.system() == "Windows" else ""
    path = sdk / (relative + suffix)
    if relative.startswith("cmdline-tools/latest/") and not path.is_file():
        candidates = [item for item in (sdk / "cmdline-tools").glob("*/bin/" + Path(relative).name + suffix)
                      if re.fullmatch(r"\d+(?:\.\d+)*", item.parent.parent.name)]
        candidates.sort(key=lambda item: tuple(int(x) for x in item.parent.parent.name.split(".")), reverse=True)
        if candidates:
            path = candidates[0]
    if not path.is_file():
        raise Blocked(f"Missing SDK tool: {path}")
    return path


def discover_sdk(explicit=None):
    """Follow explicit configuration before platform defaults; never change SDK settings."""
    if explicit:
        return Path(explicit).expanduser().resolve()
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(key):
            return Path(os.environ[key]).expanduser().resolve()
    local = ROOT / "local.properties"
    if local.is_file():
        match = re.search(r"(?m)^sdk\.dir\s*=\s*(.+)$", local.read_text(encoding="utf-8"))
        if match:
            value = re.sub(r"\\([\\ :=!])", r"\1", match[1].strip())
            return Path(value).expanduser().resolve()
    if platform.system() == "Darwin":
        return Path.home() / "Library/Android/sdk"
    if platform.system() == "Windows":
        return Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData/Local")) / "Android/Sdk"
    return Path.home() / "Android/Sdk"


def native_architecture():
    machine = platform.machine().lower()
    # Rosetta can make an Apple Silicon host look x86_64 to Python.
    if platform.system() == "Darwin":
        translated = execute(["/usr/sbin/sysctl", "-n", "sysctl.proc_translated"], timeout=10)
        if translated.returncode == 0 and translated.stdout.strip() == "1":
            return "arm64"
        result = execute(["/usr/sbin/sysctl", "-n", "hw.optional.arm64"], timeout=10)
        if result.returncode == 0 and result.stdout.strip() == "1":
            return "arm64"
        if result.returncode != 0 or result.stdout.strip() not in ("0", "1"):
            native = execute(["/usr/sbin/sysctl", "-n", "machdep.cpu.brand_string"], timeout=10)
            if native.returncode or "Intel" not in native.stdout:
                raise Blocked("Cannot verify native Intel macOS CPU; do not assume Rosetta is compatible")
    if platform.system() == "Windows":
        machine = os.environ.get("PROCESSOR_ARCHITEW6432",
                                 os.environ.get("PROCESSOR_ARCHITECTURE", machine)).lower()
    return {"amd64": "x86_64", "aarch64": "arm64"}.get(machine, machine)


def host_image(system=None, architecture=None):
    """The sole API 26 host/image decision, based on native hardware, not Rosetta Python."""
    system = platform.system() if system is None else system
    architecture = native_architecture() if architecture is None else architecture
    abi = HOST_ABIS.get((system, architecture))
    if abi is None:
        raise Blocked(f"Unsupported native host {system}/{architecture}. API 26 requires macOS arm64, "
                      "or native x86_64 macOS/Windows/Linux with hardware acceleration; no software fallback.")
    return {"api": 26, "image": f"system-images;android-26;google_apis;{abi}",
            "abi": abi, "profile": "pixel_c",
            "emulator_prefix": ["/usr/bin/arch", f"-{architecture}"] if system == "Darwin" else []}


def _inspect_host(home, sdk):
    """Read-only host/device probes. Readiness is not proof of successful Android execution."""
    report = {"status": "BLOCKED", "os": platform.system(), "machine": platform.machine(),
              "python": platform.python_version(), "sdk": str(sdk), "checks": {}, "blockers": [],
              "emulation": "NOT RUN", "ready_to_attempt": False,
              "requested_avd": None}
    def fail(message):
        report["blockers"].append(message)
    def probe(name, command):
        try:
            result = execute(command, timeout=20)
            text = result.stdout + "\n" + result.stderr
            report["checks"][name] = {"command": [str(x) for x in command],
                                      "exit": result.returncode, "output": text.strip()}
            return result, text
        except Blocked as error:
            report["checks"][name] = {"error": str(error)}
            return None, ""
    try:
        arch = native_architecture()
        report["native_architecture"] = arch
    except Blocked as error:
        arch = "unknown"
        fail(f"Cannot establish native CPU architecture: {error}")
    selection = None
    try:
        selection = host_image(report["os"], arch)
        report["requested_avd"] = selection
    except Blocked as error:
        fail(str(error))
    if sys.version_info < (3, 10):
        fail("Python 3.10+ is required")
    java_home = os.environ.get("JAVA_HOME")
    java = Path(java_home) / "bin" / ("java.exe" if report["os"] == "Windows" else "java") if java_home else shutil.which("java")
    report["jdk"] = {"java_home": java_home, "executable": str(java) if java else None,
                     "resolved_installation": str(Path(java).resolve().parent.parent) if java else None}
    javac = Path(java).resolve().with_name("javac.exe" if report["os"] == "Windows" else "javac") if java else None
    if not java or not Path(java).is_file() or not javac.is_file():
        fail("JDK 25 is required: set JAVA_HOME to a valid installation")
    else:
        result, output = probe("jdk", [java, "-version"])
        if not result or result.returncode or not re.search(r'(?:java.version\s*=\s*|version ")25(?:\D|$)', output):
            fail("A working JDK 25 is required by the pinned Gradle daemon toolchain")
    tools = {}
    for name, relative in (("adb", "platform-tools/adb"), ("emulator", "emulator/emulator"),
                           ("sdkmanager", "cmdline-tools/latest/bin/sdkmanager"),
                           ("avdmanager", "cmdline-tools/latest/bin/avdmanager")):
        try:
            tools[name] = sdk_tool(sdk, relative)
        except Blocked as error:
            fail(str(error))
    report["tools"] = {key: str(value) for key, value in tools.items()}
    for name, flag in (("adb", "version"), ("emulator", "-version"),
                       ("sdkmanager", "--version"), ("avdmanager", "list")):
        if name in tools:
            # 'avdmanager list device -c' probes command-line/JDK compatibility without changing an AVD.
            command = [tools[name], flag] + (["device", "-c"] if name == "avdmanager" else [])
            if name == "emulator" and selection:
                command = selection["emulator_prefix"] + command
            result, output = probe(name, command)
            if not result or result.returncode:
                fail(f"{name} is installed but cannot execute successfully")
            elif name == "avdmanager" and not re.search(r"(?m)^pixel_c\s*$", output):
                fail("Required Pixel C hardware profile (pixel_c) is missing from avdmanager")
    platforms = []
    for properties in (sdk / "platforms").glob("*/source.properties"):
        if re.search(r"(?m)^AndroidVersion.ApiLevel\s*=\s*37(?:\.0)?\s*$", properties.read_text(encoding="utf-8")) and (properties.parent / "android.jar").is_file():
            platforms.append(str(properties.parent))
    report["platform_api_37"] = platforms
    if not platforms:
        fail("SDK platform API 37 with android.jar is required (platforms;android-37 / android-37.0)")
    build_tools = sdk / "build-tools/37.0.0"
    report["build_tools"] = str(build_tools)
    suffix = ".exe" if report["os"] == "Windows" else ""
    if not (build_tools / ("aapt2" + suffix)).is_file() or not (build_tools / "lib/d8.jar").is_file():
        fail("Build tools 37.0.0 (aapt2 and D8) are required")
    images = []
    for metadata in (sdk / "system-images/android-26").glob("*/*/package.xml"):
        try:
            root = ET.parse(metadata).getroot()
            values = {node.tag.split("}")[-1]: node.text for node in root.iter() if node.text}
            package = next(node for node in root.iter() if node.tag.split("}")[-1] == "localPackage")
            images.append({"package": package.get("path"), "api": values.get("api-level"),
                           "abi": values.get("abi"), "system_img": (metadata.parent / "system.img").is_file(),
                           "path": metadata.parent.relative_to(sdk).as_posix()})
        except (ET.ParseError, StopIteration, OSError) as error:
            fail(f"Invalid installed image metadata {metadata}: {error}")
    report["installed_api26_images"] = images
    report["compatible_api26_images"] = [item for item in images if selection and
        item["abi"] == selection["abi"] and item["api"] == "26" and item["system_img"] and
        item["path"] == (item["package"] or "").replace(";", "/")]
    if selection and not any(item["package"] == selection["image"] for item in report["compatible_api26_images"]):
        fail(f"Requested installed API 26 image is missing or incompatible: {selection['image']}. "
             f"Install and license this exact {selection['abi']} package on the compatible native host.")
    if "emulator" in tools:
        result, output = probe("acceleration", (selection["emulator_prefix"] if selection else []) + [tools["emulator"], "-accel-check"])
        usable = bool(result and result.returncode == 0 and re.search(r"(?m)^accel:\s*\n0\s*\n", output))
        report["acceleration_available"] = usable
        if not usable:
            fail("Hardware acceleration is unavailable/unverified. Enable WHPX/AEHD (Windows), Hypervisor.Framework (macOS), or KVM (Linux), then rerun doctor.")
    else:
        report["acceleration_available"] = False
    if (home / "device.json").exists() or (home / "avd").exists():
        try:
            if selection is None:
                raise Blocked("Cannot validate AVD ownership for an unsupported native host")
            state = load_device(home, selection)
            identity = dict(state, status="OWNED_STOPPED", serial=f"emulator-{state['port']}")
            report["dedicated_avd"] = identity
            if "adb" in tools:
                result, listing = probe("adb_devices", [tools["adb"], "devices", "-l"])
                if not result or result.returncode:
                    fail("Cannot verify dedicated AVD runtime identity: ADB unavailable")
                elif any(line.split()[0] == identity["serial"] for line in listing.splitlines() if line.split()):
                    for key, args in (("name", ["emu", "avd", "name"]), ("qemu", ["shell", "getprop", "ro.kernel.qemu"]),
                                      ("api", ["shell", "getprop", "ro.build.version.sdk"]), ("boot", ["shell", "getprop", "sys.boot_completed"]),
                                      ("abi", ["shell", "getprop", "ro.product.cpu.abi"])):
                        result, output = probe("device_" + key, [tools["adb"], "-s", identity["serial"], *args])
                        identity[key] = result.stdout.strip().splitlines()[0] if result and result.returncode == 0 and result.stdout.strip() else "UNKNOWN"
                    if (identity["name"], identity["qemu"], identity["api"], identity["boot"], identity["abi"]) != (state["name"], "1", "26", "1", selection["abi"]):
                        identity["status"] = "UNVERIFIED"
                        fail("Reserved serial is occupied by an unverified AVD or boot is incomplete; no device changes allowed")
                    else:
                        identity["status"] = "OWNED_BOOTED"
        except Blocked as error:
            report["dedicated_avd"] = {"status": "INVALID", "detail": str(error)}
            fail(str(error))
    else:
        report["dedicated_avd"] = {"status": "UNPROVISIONED", "detail": "Explicit provision is required; doctor creates no AVD"}
    report["ready_to_attempt"] = not report["blockers"]
    report["status"] = "PASS" if report["ready_to_attempt"] else "BLOCKED"
    return report


def doctor(home, sdk):
    try:
        return _inspect_host(home, sdk)
    except (OSError, ValueError, Blocked) as error:
        return {"status": "BLOCKED", "os": platform.system(), "machine": platform.machine(),
                "python": platform.python_version(), "sdk": str(sdk), "checks": {},
                "blockers": [f"Host diagnostic could not complete: {error}"],
                "emulation": "NOT RUN", "ready_to_attempt": False}


def require_host(home, sdk):
    report = doctor(home, sdk)
    if report["status"] != "PASS":
        raise Blocked("Host preflight blocked:\n" + "\n".join(report["blockers"]))
    return report


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
        # Readiness checks exact package metadata, ABI, API and acceleration before creation.
        selection = require_host(home, sdk)["requested_avd"]
        avd_home = home / "avd"
        avd_home.mkdir()
        name = "PartiturasPDF_E2E_API26_" + uuid.uuid4().hex[:12]
        env = dict(os.environ, ANDROID_AVD_HOME=str(avd_home))
        # --device selects a known profile; explicit input avoids an interactive skin prompt.
        output = checked([manager, "create", "avd", "--name", name, "--package", selection["image"],
                          "--device", selection["profile"]], timeout=120, env=env, input_text="no\n")
        (home / "provision.log").write_text(output, encoding="utf-8")
        state.write_text(json.dumps({"version": 1, "name": name, "port": port,
                                     "image": selection["image"], "avd_home": str(avd_home)}, indent=2), encoding="utf-8")
        print(f"Provisioned dedicated AVD {name}; emulator-{port}. No emulator was started.")


def load_device(home, selection=None):
    try:
        selection = host_image() if selection is None else selection
        state = json.loads((home / "device.json").read_text(encoding="utf-8"))
        port = state["port"]
        if (state["version"] != 1 or state["image"] != selection["image"] or not isinstance(port, int)
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
        if values.get("image.sysdir.1", "").replace("\\", "/").rstrip("/") != selection["image"].replace(";", "/"):
            raise ValueError("AVD does not use the approved API 26 native image")
        if "abi.type" in values and values["abi.type"] != selection["abi"]:
            raise ValueError("AVD ABI conflicts with its native image")
        if "tag.id" in values and values["tag.id"] != "google_apis":
            raise ValueError("AVD tag conflicts with its approved image")
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
    completions = re.findall(r"(?m)^INSTRUMENTATION_CODE: (-?\d+)\s*$", output)
    if (completions != ["-1"] or re.search(r"(?m)^INSTRUMENTATION_(?:FAILED|ABORTED)", output)
            or re.search(r"(?m)^INSTRUMENTATION_RESULT: shortMsg=", output)):
        raise Blocked(f"Instrumentation incomplete or crashed: {method}")
    if bundles.index((1, starts[0])) >= bundles.index(ends[0]):
        raise Blocked(f"JUnit result precedes its start: {method}")
    completion = re.search(r"(?m)^INSTRUMENTATION_CODE: -1\s*$", output)
    if completion.start() < max(match.start() for match in re.finditer(r"(?m)^INSTRUMENTATION_STATUS_CODE:", output)):
        raise Blocked(f"Instrumentation result follows runner completion: {method}")
    code, data = ends[0]
    if code in (-1, -2):
        if not data.get("stack", "").strip():
            raise Blocked(f"Missing executed JUnit failure detail: {method}")
        raise FunctionalFailure(data["stack"])
    if code != 0 or "FAILURES!!!" in output:
        raise Blocked(f"Instrumentation incomplete, skipped, or inconsistent: {method}")
    return 1


def validate_screenshot(data):
    """Reject truncated/corrupt screencap output without introducing an image dependency."""
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise Blocked("screencap did not return PNG")
    offset, image_data = 8, False
    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        end = offset + 12 + length
        if end > len(data):
            break
        kind, payload = data[offset + 4:offset + 8], data[offset + 8:end - 4]
        crc = struct.unpack_from(">I", data, end - 4)[0]
        if zlib.crc32(kind + payload) != crc:
            raise Blocked("screencap PNG has a corrupt chunk")
        if offset == 8:
            if kind != b"IHDR" or length != 13 or not all(struct.unpack_from(">II", payload)):
                raise Blocked("screencap PNG has an invalid image header")
        image_data = image_data or kind == b"IDAT"
        if kind == b"IEND" and not length and end == len(data) and image_data:
            return
        offset = end
    raise Blocked("screencap PNG is incomplete")


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
        self.summary["failure_injection"] = self.inject
        self.state = self.adb = self.serial = self.selection = None
        self.process = self.emulator_log = None
        self.fixture_created = self.installed = self.identified = False
        self.settings = {}
        self.ui_failure_cleanup = None
        self._adb_attempt = 1
        self._adb_deadline = None
        self.summary["adb_transport"] = []
        self.summary["setting_changes"] = []
        self.record("adb-transport.json", "[]")

    def record(self, name, value):
        (self.out / name).write_text(value, encoding="utf-8")

    def process_state(self):
        if self.process is None:
            return {"ownership": "not launched by this run", "exit": None}
        return {"ownership": "child of this run", "pid": self.process.pid, "exit": self.process.poll()}

    def transport_event(self, **event):
        self.summary["adb_transport"].append(dict(event, process=self.process_state()))
        self.record("adb-transport.json", json.dumps(self.summary["adb_transport"], indent=2))

    def raw_adb(self, *args, **kwargs):
        """Exactly one command; mutations are NEVER replayed after a lost response."""
        command = [str(self.adb), "-s", self.serial, *args]
        started = time.monotonic()
        timeout = kwargs.pop("timeout", 30)
        if self._adb_deadline is not None:
            timeout = min(timeout, self._adb_deadline - started)
            if timeout <= 0:
                raise AdbTransportError("ADB readiness timeout budget exhausted")
        if self.process is not None and self.process.poll() is not None:
            raise Blocked("Dedicated emulator process terminated; see emulator.log")
        try:
            result = execute(command, timeout=timeout, **kwargs)
        except Blocked as error:
            self.transport_event(command=command, attempt=self._adb_attempt, exit=None,
                                 elapsed_seconds=time.monotonic() - started, error=str(error))
            if isinstance(error.__cause__, subprocess.TimeoutExpired):
                raise AdbTransportError(str(error)) from error
            raise
        self.transport_event(command=command, attempt=self._adb_attempt, exit=result.returncode,
                             elapsed_seconds=time.monotonic() - started,
                             stdout=result.stdout if result.returncode else "",
                             stderr=result.stderr if result.returncode else "")
        if result.returncode:
            message = f"ADB command failed ({result.returncode}): {command}\n{result.stdout}\n{result.stderr}"
            transient = result.returncode == 255 or any(text in (result.stderr + result.stdout).lower()
                for text in ("device offline", "device not found", "transport error", "error: closed"))
            transient = transient or bool(re.search(r"error: device .+ (?:not found|offline)",
                                                    (result.stderr + result.stdout).lower()))
            raise (AdbTransportError if transient else Blocked)(message)
        return result.stdout.strip()

    def recover_readiness(self, label, operation, *, attempts=3, budget=8, delay=0.25):
        """Bounded read-only recovery; defaults: 3 attempts / 8 seconds / 250 ms backoff.

        A newly launched child gets a separate initial-connection window. Semantic identity
        mismatches NEVER retry, even in that window. No mutation or JUnit method enters here.
        """
        started = time.monotonic()
        previous_attempt, previous_deadline = self._adb_attempt, self._adb_deadline
        self._adb_deadline = started + budget
        try:
            for attempt in range(1, attempts + 1):
                self._adb_attempt = attempt
                if self.process is not None and self.process.poll() is not None:
                    self.transport_event(operation=label, attempt=attempt, outcome="process terminated",
                                         elapsed_seconds=time.monotonic() - started)
                    raise Blocked("Dedicated emulator process terminated; see emulator.log")
                try:
                    value = operation()
                except AdbTransportError as error:
                    remaining = self._adb_deadline - time.monotonic()
                    exhausted = attempt == attempts or remaining <= delay
                    self.transport_event(operation=label, attempt=attempt,
                        outcome="exhausted" if exhausted else "retry transport read",
                        elapsed_seconds=time.monotonic() - started, error=str(error))
                    if exhausted:
                        raise
                    time.sleep(delay)
                except Blocked as error:
                    self.transport_event(operation=label, attempt=attempt, outcome="identity/readiness rejected",
                        elapsed_seconds=time.monotonic() - started, error=str(error))
                    raise
                else:
                    self.transport_event(operation=label, attempt=attempt,
                        outcome="recovered" if attempt > 1 else "ready",
                        elapsed_seconds=time.monotonic() - started,
                        identity=dict(self.summary["device"]))
                    return value
        finally:
            self._adb_attempt, self._adb_deadline = previous_attempt, previous_deadline

    def identify(self, *, boot=False):
        return self.recover_readiness("identity", lambda: self._identify_once(boot=boot))

    def _identify_once(self, *, boot=False):
        self.summary["device"]["identity_verified"] = False
        if not self.state or not self.adb or not re.fullmatch(r"emulator-\d+", self.serial or ""):
            raise Blocked("No dedicated device selected")
        if "port" in self.state and self.serial != f"emulator-{self.state['port']}":
            raise Blocked("Selected serial conflicts with owned emulator port")
        name = self.raw_adb("emu", "avd", "name").splitlines()
        self.summary["device"]["observed_name"] = name[0].strip() if name else None
        if not name or name[0].strip() != self.state["name"]:
            raise Blocked("Emulator port belongs to a different AVD; refusing device operations")
        qemu = self.raw_adb("shell", "getprop", "ro.kernel.qemu")
        self.summary["device"]["observed_qemu"] = qemu
        if qemu != "1":
            raise Blocked("Device is not an emulator")
        api = self.raw_adb("shell", "getprop", "ro.build.version.sdk")
        self.summary["device"]["observed_api"] = api
        if api != "26":
            raise Blocked(f"Dedicated emulator is not API 26: {api!r}")
        selection = self.selection or host_image()
        if self.state.get("image") != selection["image"]:
            raise Blocked("Dedicated AVD image conflicts with the native host")
        observed_abi = self.raw_adb("shell", "getprop", "ro.product.cpu.abi")
        self.summary["device"]["observed_abi"] = observed_abi
        if observed_abi != selection["abi"]:
            raise Blocked(f"Emulator ABI {observed_abi!r} does not match approved native API 26 ABI {selection['abi']!r}")
        self.summary["device"].update(serial=self.serial, name=self.state["name"], api=26,
                                       emulator=True, abi=observed_abi)
        if boot and self.raw_adb("shell", "getprop", "sys.boot_completed") != "1":
            raise BootPending("Emulator boot is incomplete")
        self.identified = True
        self.summary["device"]["identity_verified"] = True

    def read_adb(self, *args):
        """Only explicitly approved property/settings reads; revalidate ALL identity on recovery."""
        if not ((len(args) in (2, 3) and args[:2] == ("shell", "getprop")) or
                (len(args) == 5 and args[:3] == ("shell", "settings", "get"))):
            raise Blocked("Not an approved read-only readiness command")
        def read():
            self._identify_once()
            return self.raw_adb(*args)
        return self.recover_readiness("read: " + " ".join(args), read)

    def adb_command(self, *args, **kwargs):
        self.identify()
        return self.raw_adb(*args, **kwargs)

    def prepare_settings(self):
        for namespace, key, value in (("global", "window_animation_scale", "0"),
                ("global", "transition_animation_scale", "0"), ("global", "animator_duration_scale", "0"),
                ("system", "accelerometer_rotation", "0"), ("system", "user_rotation", "0"),
                ("system", "screen_off_timeout", "600000")):
            original = self.read_adb("shell", "settings", "get", namespace, key)
            if not original:
                raise Blocked(f"Empty settings response; original value unknown: {namespace}/{key}")
            if original == value:
                continue
            self.identify()  # No pending mutation if identity fails BEFORE dispatch.
            entry = dict(namespace=namespace, key=key, original=original, target=value, state="response pending")
            self.settings[(namespace, key)] = entry
            self.summary["setting_changes"].append(entry)
            # A failed response may still have changed the setting; cleanup reads it before restoring.
            self.raw_adb("shell", "settings", "put", namespace, key, value)
            entry["state"] = "write acknowledged"
            if self.read_adb("shell", "settings", "get", namespace, key) != value:
                raise Blocked(f"Setting write not verified: {namespace}/{key}")
            entry["state"] = "modified and verified"

    def restore_settings(self):
        for (namespace, key), entry in self.settings.items():
            result = dict(name=f"setting {namespace}/{key}", status="BLOCKED")
            try:
                current = self.read_adb("shell", "settings", "get", namespace, key)
                if current != entry["original"]:
                    if current != entry["target"]:
                        raise Blocked("Setting changed unexpectedly; refusing to overwrite it")
                    arguments = ("delete", namespace, key) if entry["original"] == "null" else (
                        "put", namespace, key, entry["original"])
                    self.adb_command("shell", "settings", *arguments)  # Exactly one restoration write.
                    if self.read_adb("shell", "settings", "get", namespace, key) != entry["original"]:
                        raise Blocked("Restoration could not be verified")
                    result.update(status="PASS", detail="original value restored and read back")
                else:
                    result.update(status="PASS", detail="original value verified; no restoration write needed")
                entry["state"] = "original value verified"
            except Exception as error:
                result["detail"] = str(error)
                entry["state"] = "restoration unverified"
            self.summary["cleanup"].append(result)

    def gradle(self, name, tasks, properties=(), timeout=900, *, instrumentation=False):
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
        if result.returncode and not instrumentation:
            raise Blocked(f"Gradle failed ({result.returncode}); see {name}")
        return result if instrumentation else output

    def stage(self, method, *, cleanup=False):
        self.identify(boot=True)
        label = ("cleanup-" if cleanup else "") + method
        result = {"name": method, "status": "BLOCKED"}
        try:
            process = self.gradle(label + ".log", [":app:localE2eStage"], [
                f"-PlocalE2eSerial={self.serial}", f"-PlocalE2eStage={method}",
                f"-PlocalE2eRun={self.run_id}", f"-PlocalE2eSha256={self.sha}",
                f"-PlocalE2eFailure={self.inject}"], timeout=180, instrumentation=True)
            result["process_exit"] = process.returncode
            # Parse trustworthy completed JUnit evidence BEFORE considering the wrapper exit.
            # A timeout raises Blocked in execute(), so partial output is never promoted.
            parse_instrumentation(process.stdout + "\n" + process.stderr, method)
            if process.returncode:
                raise Blocked(f"Test completed but Gradle/ADB failed ({process.returncode}); see {label}.log")
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
        self.selection = host_image()
        self.state = load_device(self.home, self.selection)
        self.adb = sdk_tool(self.sdk, "platform-tools/adb")
        self.serial = f"emulator-{self.state['port']}"
        self.summary["device"] = dict(self.state, serial=self.serial, api=26, abi=self.selection["abi"])
        listing = checked([self.adb, "devices", "-l"])
        self.record("adb-initial.txt", listing)
        if any(line.split()[0] == self.serial for line in listing.splitlines() if line.split()):
            self.identify()  # Do not touch a different AVD occupying the port.
        else:
            emulator = sdk_tool(self.sdk, "emulator/emulator")
            self.emulator_log = (self.out / "emulator.log").open("wb")
            env = dict(os.environ, ANDROID_AVD_HOME=self.state["avd_home"])
            self.process = subprocess.Popen(self.selection["emulator_prefix"] + [str(emulator), "-avd", self.state["name"],
                "-port", str(self.state["port"]), "-accel", "on", "-no-snapshot-load", "-no-snapshot-save",
                "-no-audio", "-no-boot-anim"], stdout=self.emulator_log,
                stderr=subprocess.STDOUT, env=env)
            # Cold-start ADB may not exist yet. Only the child we just launched gets this
            # longer window, before any installation/settings write; foreign identities stop.
            self.recover_readiness("initial child transport", self._identify_once,
                                   attempts=60, budget=60, delay=0.5)
        deadline = time.monotonic() + 240
        last_error = "not online"
        while time.monotonic() < deadline:
            if self.process and self.process.poll() is not None:
                raise Blocked("Dedicated emulator crashed; see emulator.log")
            try:
                self.identify(boot=True)
                break
            except BootPending as error:
                last_error = str(error)
                # Only boot-incomplete is polled; identity mismatches/transport exhaustion stop.
                time.sleep(2)
        else:
            raise Blocked(f"Emulator boot timeout: {last_error}")
        # Two complete boot/identity samples separated by just 250 ms. Any mismatch stops.
        time.sleep(0.25)
        self.identify(boot=True)
        self.summary["device"]["stable_readiness"] = "two verified boot/identity samples, 250 ms apart"
        locale = self.read_adb("shell", "getprop", "persist.sys.locale")
        if not locale:
            locale = self.read_adb("shell", "getprop", "ro.product.locale")
        if locale.replace("_", "-") != "en-US":
            raise Blocked(f"Dedicated AVD must use en-US; actual locale: {locale!r}")
        self.summary["device"]["locale"] = locale
        self.record("device-properties.txt", self.read_adb("shell", "getprop"))
        self.prepare_settings()
        self.summary["configuration"] = "en-US, natural orientation, animations off, 10 minute display timeout"

    def collect_instrumentation_failures(self):
        """Copy private in-process snapshots before cleanup; never overwrite post-test evidence."""
        root = f"files/local-e2e-{safe_run_id(self.run_id)}-ui-failures"
        roots = self.adb_command("shell", "run-as", APP, "ls", "files").splitlines()
        if root.removeprefix("files/") not in roots:
            return
        events = self.adb_command("shell", "run-as", APP, "ls", root).splitlines()
        self.ui_failure_cleanup = None
        all_copied = bool(events)
        for event in events:
            safe_run_id(event)  # Only UUID event names, never arbitrary paths from device output.
            destination = self.out / "instrumentation-failures" / event
            if destination.exists():
                previous = next((item for item in self.summary.get("instrumentation_failures", [])
                                 if item["event"] == event), None)
                if not previous or previous["errors"] or set(previous["files"]) != {"metadata.json", "screenshot.png", "hierarchy.xml"}:
                    all_copied = False
                continue  # Cleanup diagnostics must not replace the first failure-time snapshots.
            destination.mkdir(parents=True)
            entry = {"event": event, "directory": str(destination.relative_to(self.out)), "files": [], "errors": []}
            for name in ("metadata.json", "screenshot.png", "hierarchy.xml"):
                try:
                    self.identify()
                    result = execute([self.adb, "-s", self.serial, "exec-out", "run-as", APP,
                                      "cat", f"{root}/{event}/{name}"], binary=True)
                    if result.returncode:
                        raise Blocked(f"Cannot retrieve live UI {name}")
                    data = result.stdout
                    (destination / name).write_bytes(data)  # Retain raw failure-time evidence even if validation fails.
                    if name == "screenshot.png":
                        validate_screenshot(data)
                    elif name == "hierarchy.xml":
                        hierarchy = ET.fromstring(data)
                        if hierarchy.tag != "hierarchy" or not hierarchy.findall(".//node"):
                            raise Blocked("Live UI hierarchy is empty or malformed")
                    else:
                        metadata = json.loads(data)
                        if metadata.get("run") != self.run_id or not isinstance(metadata.get("selector"), str):
                            raise Blocked("Live UI metadata does not match this run/selector")
                        if metadata.get("errors"):
                            raise Blocked(f"Live UI capture reported errors: {metadata['errors']}")
                    entry["files"].append(name)
                except Exception as error:
                    all_copied = False
                    entry["errors"].append(f"{name}: {error}")
                    self.summary["diagnostics"].append(f"instrumentation-failures/{event}/{name}: {error}")
            self.summary.setdefault("instrumentation_failures", []).append(entry)
        if all_copied:
            self.ui_failure_cleanup = root

    def collect(self, prefix=""):
        """Best effort; one failing capture never suppresses subsequent host/device evidence."""
        if self.adb:
            try:
                self.record(prefix + "adb-final.txt", checked([self.adb, "devices", "-l"]))
            except Exception as error:
                self.summary["diagnostics"].append(f"adb listing: {error}")
        if not self.identified:
            return
        if self.installed:
            try:
                self.collect_instrumentation_failures()
            except Exception as error:
                self.summary["diagnostics"].append(f"live instrumentation evidence: {error}")
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
            if result.returncode:
                raise Blocked("screencap command failed")
            validate_screenshot(result.stdout)
            (self.out / (prefix + "screenshot.png")).write_bytes(result.stdout)
        except Exception as error:
            self.summary["diagnostics"].append(f"screenshot: {error}")
        try:
            path = f"/data/local/tmp/local-e2e-{self.run_id}.xml"
            self.adb_command("shell", "uiautomator", "dump", path, timeout=30)
            self.adb_command("pull", path, str(self.out / (prefix + "hierarchy.xml")))
            hierarchy = ET.parse(self.out / (prefix + "hierarchy.xml")).getroot()
            if hierarchy.tag != "hierarchy" or not hierarchy.findall(".//node"):
                raise Blocked("UI hierarchy is empty or has an unexpected root")
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
        if self.ui_failure_cleanup:
            try:
                self.adb_command("shell", "run-as", APP, "rm", "-r", self.ui_failure_cleanup)
                self.summary["cleanup"].append({"name": "live UI snapshots copied to host", "status": "PASS"})
            except Exception as error:
                self.summary["cleanup"].append({"name": "live UI snapshots", "status": "BLOCKED", "detail": str(error)})
        if self.fixture_created:
            try:
                path = self.remote_folder()
                self.adb_command("shell", "rm", "-r", path)
                self.summary["cleanup"].append({"name": "fixture", "status": "PASS"})
            except Exception as error:
                self.summary["cleanup"].append({"name": "fixture", "status": "BLOCKED", "detail": str(error)})
        self.restore_settings()
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
        self.summary["host"] = doctor(self.home, self.sdk)
        self.record("doctor.json", json.dumps(self.summary["host"], indent=2))
        if self.summary["host"]["status"] != "PASS":
            raise Blocked("Host preflight blocked: " + "; ".join(self.summary["host"]["blockers"]))
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
                    self.collect()
                    if code == 0 and self.summary["diagnostics"]:
                        self.summary.update(status="BLOCKED", detail="Required device evidence is incomplete; inspect diagnostics")
                        code = 2
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
    parser.add_argument("--sdk", type=Path, default=None)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("doctor", help="Read-only host, SDK, acceleration and owned AVD diagnostics")
    setup = commands.add_parser("provision", help="Explicitly create a new private API 26 AVD; never overwrite")
    setup.add_argument("--port", type=int, default=5580)
    run = commands.add_parser("run")
    run.add_argument("--suite", choices=("saf", "reader", "library", "full", "coverage"), required=True)
    run.add_argument("--inject-failure", action="store_true", help="Deliberate assertion failure after real picker authorization")
    args = parser.parse_args(argv)
    sdk = discover_sdk(args.sdk)
    if args.command == "doctor":
        report = doctor(args.home.resolve(), sdk)
        print(json.dumps(report, indent=2))
        return 0 if report["status"] == "PASS" else 2
    if args.command == "provision":
        try:
            provision(args.home.resolve(), sdk, args.port)
            return 0
        except Blocked as error:
            print(f"BLOCKED: {error}", file=sys.stderr)
            return 2
    return Runner(args.home, sdk, args.suite, args.inject_failure).run()


if __name__ == "__main__":
    sys.exit(main())
