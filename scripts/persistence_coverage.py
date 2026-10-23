#!/usr/bin/env python3
"""Fail-closed persistence coverage preparation, provenance and independent source-group gates."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET
from verify_test_results import verify as verify_junit

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "app/build"
CLASSES = BUILD / "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
OUTPUT = BUILD / "reports/persistence"
JVM = BUILD / "jacoco/testDebugUnitTest.exec"
ANDROID = BUILD / "outputs/code_coverage/debugAndroidTest/connected"
JVM_XML = BUILD / "test-results/testDebugUnitTest"
ANDROID_XML = BUILD / "outputs/androidTest-results/connected"
PREFIX = "com/abdev/partituraspdf/data/"
ADAPTERS = [PREFIX + "metadata/RoomDocumentRegistry", PREFIX + "metadata/RoomDocumentMetadataRepository",
            PREFIX + "preferences/DataStorePreferencesRepository"]


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def java(args, mode: str, *paths: Path) -> dict:
    command = ["java", *(["-Dlocal.coverage.sources=" + str(ROOT / "app/src/main/java")]
                        if getattr(args, "local_output", None) else []), "-cp", args.jacoco_classpath, str(ROOT / "scripts/PersistenceCoverageInputs.java"),
               mode, str(CLASSES), *(str(p) for p in paths)]
    result = subprocess.run(command, text=True, capture_output=True, timeout=120)
    if result.returncode:
        raise ValueError("JaCoCo input/identity validation failed: " + result.stderr.strip())
    return json.loads(result.stdout)


def api(adb: str, serial: str | None = None) -> int:
    result = subprocess.run([adb, *(["-s", serial] if serial else []), "shell", "getprop", "ro.build.version.sdk"], text=True,
                            capture_output=True, timeout=30, check=True)
    level = int(result.stdout.strip())
    if level != 26:
        raise ValueError(f"Actual Android API must be 26, found {level}")
    return level


def current_build(args) -> dict:
    metadata = {"commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
            "gradle": args.gradle_version, "agp": args.agp_version,
            "jdk": args.jdk_version, "jacoco": args.jacoco_version,
            "ci": {name: os.environ.get(name) for name in
                   ["GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_JOB", "GITHUB_SHA"]}}
    if getattr(args, "local_output", None):
        # Supplemental AIDL bytecode is authenticated, never a handwritten report scope.
        metadata["generatedClasses"] = java(args, "generated-inventory")
        metadata["sourceHashes"] = {str(p.relative_to(ROOT)): digest(p) for p in sorted((ROOT / "app/src").rglob("*")) if p.is_file()}
        metadata["buildFiles"] = {name: digest(ROOT / name) for name in [
            "app/build.gradle.kts", "build.gradle.kts", "gradle.properties", "gradle/libs.versions.toml",
            "gradle/wrapper/gradle-wrapper.properties", "scripts/local_e2e.py", "scripts/local_test_evidence.py",
            "scripts/local_coverage.py", "scripts/local-coverage.init.gradle", "scripts/persistence_coverage.py",
            "scripts/PersistenceCoverageInputs.java"] if (ROOT / name).is_file()}
        metadata["localCapture"] = json.loads((Path(args.local_output) / "capture.json").read_text())
        metadata["workingTree"] = subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True)
    return metadata


def prepare(args) -> None:
    # Check device and class inventory first; never destroy evidence when prerequisites fail.
    level = api(args.adb, getattr(args, "serial", None))
    classes = java(args, "inventory")
    tests = java(args, "test-inventory")
    metadata = current_build(args)
    for path in [JVM, ANDROID, JVM_XML, ANDROID_XML, OUTPUT]:
        if not (path.resolve().is_relative_to(BUILD.resolve()) or
                (getattr(args, "local_output", None) and
                 path.resolve().is_relative_to(Path(args.local_output).resolve()))):
            raise ValueError("Generated evidence path escapes app/build")
        if path.is_symlink():
            raise ValueError("Generated evidence paths must not be symlinks")
    for path in [JVM, ANDROID, JVM_XML, ANDROID_XML, OUTPUT]:
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()
    write_json(OUTPUT / "run.json", {"format": 1, "run": str(uuid.uuid4()),
               "started": int(time.time() * 1000), "androidApi": level,
               "build": metadata, "classes": classes, "testClasses": tests})


def check_provenance(run: dict, build: dict, classes: dict, level: int) -> None:
    if run.get("format") != 1 or not run.get("run") or run.get("androidApi") != 26 or level != 26:
        raise ValueError("Missing or invalid API26 run provenance")
    if run.get("build") != build or run.get("classes") != classes:
        raise ValueError("Stale/mismatched build or class provenance")
    if not isinstance(run.get("started"), int) or run["started"] > int(time.time() * 1000):
        raise ValueError("Invalid run timestamp")


def check_execution(run: dict, entry: dict, now: int) -> None:
    """The same session/freshness policy for a single input or combined capture."""
    path = Path(entry["path"])
    if not path.is_file() or path.stat().st_mtime_ns // 1_000_000 < run["started"]:
        raise ValueError("Missing or stale execution file")
    if not entry.get("sessions") or not entry.get("records"):
        raise ValueError("Missing JaCoCo sessions/classes")
    for session in entry["sessions"]:
        # Emulator/device wall clocks may differ slightly; hashes and class IDs also bind inputs.
        if (not session.get("id") or not run["started"] - 5000 <= session.get("start", 0)
                <= session.get("end", 0) <= now + 5000):
            raise ValueError("Stale/invalid execution session")


def check_inputs(run: dict, evidence: dict, jvm: Path, android: list[Path], now: int) -> None:
    if not android or not jvm.is_file():
        raise ValueError("Missing JVM or Android execution data")
    expected = {str(p) for p in [jvm, *android]}
    entries = evidence.get("inputs", [])
    if len(entries) != len(expected) or {e.get("path") for e in entries} != expected:
        raise ValueError("Missing/duplicated execution input inventory")
    android_records = set()
    for entry in entries:
        path = Path(entry["path"])
        check_execution(run, entry, now)
        if path != jvm:
            android_records.update(name for name, record in entry["records"].items()
                                   if record.get("coveredProbes", 0) > 0)
    if not set(ADAPTERS).issubset(android_records):
        raise ValueError("Missing actual Android execution of required persistence adapters")


def test_evidence(directory: Path, smoke: str, minimum: int, started: int) -> dict:
    count = verify_junit(directory, smoke)
    if count < minimum:
        raise ValueError(f"Expected at least {minimum} executed tests, got {count}")
    files = sorted(directory.rglob("*.xml"))
    names = [(case.get("classname"), case.get("name")) for p in files
             for case in ET.parse(p).getroot().iter("testcase")]
    if len(names) != len(set(names)) or any(not cls or not name for cls, name in names):
        raise ValueError("Duplicate or unnamed JUnit execution records")
    if any(p.stat().st_mtime_ns // 1_000_000 < started for p in files):
        raise ValueError("Stale JUnit XML evidence")
    return {"executed": count, "reports": [{"path": str(p.relative_to(ROOT)), "sha256": digest(p)} for p in files]}


def validate(args) -> None:
    run = json.loads((OUTPUT / "run.json").read_text())
    check_provenance(run, current_build(args), java(args, "inventory"), api(args.adb, getattr(args, "serial", None)))
    if run.get("testClasses") != java(args, "test-inventory"):
        raise ValueError("Compiled test-class provenance changed")
    files = sorted(ANDROID.rglob("*.ec"))
    if not JVM.is_file() or not files:
        raise ValueError("Missing JVM or Android execution data; no JVM-only fallback")
    evidence = java(args, "validate", JVM, *files)
    check_inputs(run, evidence, JVM, files, int(time.time() * 1000))
    if getattr(args, "local_output", None):
        native = {"com/abdev/partituraspdf/pdf/android/" + name for name in
                  ["AndroidNativePdfDocument", "AndroidNativePdfPage", "AndroidRenderBuffer"]}
        executed = {name for entry in evidence["inputs"] if Path(entry["path"]) != JVM
                    for name, record in entry["records"].items() if record["coveredProbes"] > 0}
        if not native.issubset(executed):
            raise ValueError("Missing actual Android execution of native PDF classes")
    run["tests"] = {
        "jvm": test_evidence(JVM_XML, "com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect", 128, run["started"]),
        "android": test_evidence(ANDROID_XML, "com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext", 203, run["started"])}
    run["inputs"] = [{**entry, "relativePath": str(Path(entry["path"]).relative_to(ROOT)),
                      "sha256": digest(Path(entry["path"]))} for entry in evidence["inputs"]]
    run["validated"] = int(time.time() * 1000)
    run["result"] = "INPUTS_VALIDATED_NOT_YET_VERIFIED"
    write_json(OUTPUT / "provenance.json", run)


def counter(node, kind: str, expected: int) -> dict:
    matches = [c for c in node.findall("counter") if c.get("type") == kind]
    if not matches and expected == 0:
        return {"covered": 0, "missed": 0, "total": 0, "percent": None}
    if len(matches) != 1:
        raise ValueError(f"Missing/duplicate {kind} counter")
    c = matches[0]
    covered, missed = int(c.get("covered", "-1")), int(c.get("missed", "-1"))
    if min(covered, missed) < 0 or covered + missed != expected:
        raise ValueError(f"Invalid/altered {kind} denominator")
    return {"covered": covered, "missed": missed, "total": expected,
            "percent": 100 * covered / expected if expected else None}


def report(path: Path, scope: str, classes: dict, *, package_prefix: str | None = None) -> dict:
    root = ET.parse(path).getroot()
    packages = root.findall("package")
    prefix = package_prefix or PREFIX + scope
    if root.tag != "report" or len(packages) != 1 or packages[0].get("name") != prefix:
        raise ValueError("Missing/unexpected coverage scope")
    package = packages[0]
    expected = {n: c for n, c in classes.items() if
                (n.rsplit("/", 1)[0] == prefix if package_prefix else n.startswith(prefix + "/"))
                and c.get("reported", True)}
    nodes = package.findall("class")
    if len(nodes) != len(expected) or {n.get("name") for n in nodes} != set(expected):
        raise ValueError("Missing/unexpected/duplicate analyzed class")
    groups = {}
    for entry in expected.values():
        name = entry["source"]
        group = groups.setdefault(name, {"lines": set(), "branches": 0})
        group["lines"].update(entry["lines"])
        group["branches"] += entry["branches"]
    source_nodes = package.findall("sourcefile")
    if not groups or len(source_nodes) != len(groups) or {n.get("name") for n in source_nodes} != set(groups):
        raise ValueError("Missing/unexpected/duplicate handwritten source group")
    measured = {}
    for node in source_nodes:
        name = node.get("name")
        measured[name] = {"LINE": counter(node, "LINE", len(groups[name]["lines"])),
                          "BRANCH": counter(node, "BRANCH", groups[name]["branches"])}
        line_nodes = node.findall("line")
        if (len(line_nodes) != len(groups[name]["lines"]) or
                {int(n.get("nr", "-1")) for n in line_nodes} != groups[name]["lines"]):
            raise ValueError("Missing/duplicate/altered source line evidence")
        values = [(int(n.get("ci", "-1")), int(n.get("mi", "-1")),
                   int(n.get("cb", "-1")), int(n.get("mb", "-1"))) for n in line_nodes]
        if any(min(v) < 0 or v[0] + v[1] == 0 for v in values):
            raise ValueError("Invalid source line evidence")
        if (sum(ci > 0 for ci, mi, cb, mb in values) != measured[name]["LINE"]["covered"] or
                sum(cb for ci, mi, cb, mb in values) != measured[name]["BRANCH"]["covered"] or
                sum(mb for ci, mi, cb, mb in values) != measured[name]["BRANCH"]["missed"]):
            raise ValueError("Counters disagree with source line/branch evidence")
        measured[name]["missedLines"] = [int(n.get("nr")) for n in line_nodes if int(n.get("ci")) == 0]
        measured[name]["linesWithMissedInstructions"] = [int(n.get("nr")) for n in line_nodes if int(n.get("mi")) > 0]
        measured[name]["missedBranches"] = [{"line": int(n.get("nr")), "missed": int(n.get("mb"))}
                                            for n in line_nodes if int(n.get("mb")) > 0]
    class_counts = {n.get("name"): {"LINE": counter(n, "LINE", len(expected[n.get("name")]["lines"])),
                                      "BRANCH": counter(n, "BRANCH", expected[n.get("name")]["branches"])} for n in nodes}
    total_lines = sum(len(g["lines"]) for g in groups.values())
    total_branches = sum(g["branches"] for g in groups.values())
    package_counts = {"LINE": counter(package, "LINE", total_lines), "BRANCH": counter(package, "BRANCH", total_branches)}
    for kind in ["LINE", "BRANCH"]:
        if package_counts[kind]["covered"] != sum(group[kind]["covered"] for group in measured.values()):
            raise ValueError("Package counters disagree with source groups")
    return {"package": package_counts, "sources": measured, "classes": class_counts,
            "path": str(path), "sha256": digest(path)}


def thresholds(scopes: dict) -> list[str]:
    failures = []
    for scope, values in scopes.items():
        for name, counters in {"PACKAGE": values["package"], **values["sources"]}.items():
            for kind, minimum in [("LINE", 90), ("BRANCH", 80)]:
                c = counters[kind]
                if c["total"] and c["covered"] * 100 < minimum * c["total"]:
                    failures.append(f"{scope}/{name} {kind}: {c['covered']}/{c['total']} below {minimum}%")
    return failures


def verify(args) -> None:
    path = OUTPUT / "provenance.json"
    run = json.loads(path.read_text())
    check_provenance(run, current_build(args), java(args, "inventory"), api(args.adb, getattr(args, "serial", None)))
    if run.get("testClasses") != java(args, "test-inventory"):
        raise ValueError("Compiled test-class provenance changed")
    if run.get("result") != "INPUTS_VALIDATED_NOT_YET_VERIFIED":
        raise ValueError("Missing fresh input-validation result")
    for entry in run["inputs"]:
        if digest(Path(entry["path"])) != entry["sha256"]:
            raise ValueError("Execution input changed after validation")
    for group in run["tests"].values():
        for entry in group["reports"]:
            if digest(ROOT / entry["path"]) != entry["sha256"]:
                raise ValueError("JUnit evidence changed after validation")
    scopes = {}
    for scope in ["metadata", "preferences"]:
        xml = OUTPUT / (scope + ".xml")
        if xml.stat().st_mtime_ns // 1_000_000 < run["validated"]:
            raise ValueError("Stale coverage report")
        scopes[scope] = report(xml, scope, run["classes"])
    run["coverage"] = scopes
    run["failures"] = thresholds(scopes)
    run["result"] = "FAIL" if run["failures"] else "PASS"
    write_json(path, run)
    if run["failures"]:
        raise ValueError("; ".join(run["failures"]))
    print("PASS independent persistence package/source coverage gates")


def audit(args) -> None:
    """Reproduce retained evidence offline; never claim this is a new Android test run."""
    run = json.loads((OUTPUT / "provenance.json").read_text())
    if run.get("result") not in ["PASS", "FAIL"] or run.get("androidApi") != 26:
        raise ValueError("Missing completed API26 provenance")
    if run["build"]["commit"] != current_build(args)["commit"]:
        raise ValueError("Check out the captured commit before auditing")
    classes = java(args, "inventory")
    if run["classes"] != classes:
        raise ValueError("Retained classes do not match captured identities/hashes")
    if run.get("testClasses") != java(args, "test-inventory"):
        raise ValueError("Retained test-class provenance mismatch")
    paths = []
    for entry in run["inputs"]:
        path = ROOT / entry["relativePath"]
        allowed = path.resolve().is_relative_to(BUILD.resolve()) or (
            getattr(args, "local_output", None) and path.resolve().is_relative_to(Path(args.local_output).resolve()))
        if not allowed or digest(path) != entry["sha256"]:
            raise ValueError("Retained execution input hash/path mismatch")
        paths.append(path)
    parsed = java(args, "validate", *paths)
    check_inputs(run, parsed, JVM, [p for p in paths if p != JVM], run["validated"])
    for actual, captured in zip(parsed["inputs"], run["inputs"]):
        if actual["sessions"] != captured["sessions"] or actual["records"] != captured["records"] or actual["testRecords"] != captured["testRecords"]:
            raise ValueError("Retained sessions/records differ from captured provenance")
    for kind, directory, smoke, minimum in [
            ("jvm", JVM_XML, "com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect", 128),
            ("android", ANDROID_XML, "com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext", 203)]:
        observed = test_evidence(directory, smoke, minimum, 0)
        if observed != run["tests"][kind]:
            raise ValueError("Retained JUnit evidence differs from provenance")
    scopes = {}
    for scope in ["metadata", "preferences"]:
        scopes[scope] = report(OUTPUT / (scope + ".xml"), scope, classes)
        captured = run["coverage"][scope]
        for key in ["package", "sources", "classes", "sha256"]:
            if scopes[scope][key] != captured[key]:
                raise ValueError("Retained report counters/hashes differ from provenance")
    failures = thresholds(scopes)
    if failures != run["failures"] or bool(failures) != (run["result"] == "FAIL"):
        raise ValueError("Captured verification result cannot be reproduced")
    print("Retained evidence independently reproduced; original producer result: " + run["result"])
    if failures:
        raise ValueError("; ".join(failures))


def test_inputs(args) -> None:
    # Temporary synthetic reader inputs are never stored beneath application report directories.
    with tempfile.TemporaryDirectory(prefix="persistence-validator-classes-") as directory:
        subprocess.run(["javac", "-cp", args.jacoco_classpath, "-d", directory,
                        str(ROOT / "scripts/PersistenceCoverageInputs.java"),
                        str(ROOT / "scripts/tests/PersistenceCoverageInputsTest.java")], check=True)
        subprocess.run(["java", "-cp", directory + os.pathsep + args.jacoco_classpath,
                        "PersistenceCoverageInputsTest", str(CLASSES), str(ROOT / "app/src/main/java")], check=True)


def configure_local(directory: Path, serial: str) -> None:
    """Explicit opt-in capture layout; CI defaults and connected-result paths stay unchanged."""
    import re
    global OUTPUT, ANDROID, ANDROID_XML
    directory = Path(directory).absolute()
    if (not serial or not re.fullmatch(r"emulator-[0-9]+", serial) or
            directory.name != "coverage" or
            not re.fullmatch(r"[0-9]{8}T[0-9]{6}Z-[0-9a-f]{32}", directory.parent.name) or
            not directory.resolve().is_relative_to(ROOT.resolve()) or
            any(p.is_symlink() for p in [directory, *directory.parents])):
        raise ValueError("Unsafe local coverage output/explicit emulator serial")
    OUTPUT = directory / "persistence"
    ANDROID = directory / "inputs/android"
    ANDROID_XML = directory / "android-junit"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["prepare", "validate", "verify", "test-inputs", "audit"])
    for option in ["jacoco-classpath", "gradle-version", "agp-version", "jdk-version", "jacoco-version", "adb"]:
        parser.add_argument("--" + option, required=True)
    parser.add_argument("--serial", default=os.environ.get("LOCAL_PERSISTENCE_SERIAL"))
    parser.add_argument("--local-output", type=Path, default=os.environ.get("LOCAL_PERSISTENCE_OUTPUT"))
    args = parser.parse_args()
    if args.local_output:
        configure_local(args.local_output, args.serial)
    try:
        {"prepare": prepare, "validate": validate, "verify": verify, "test-inputs": test_inputs, "audit": audit}[args.mode](args)
    except (OSError, ValueError, KeyError, TypeError, ET.ParseError, subprocess.SubprocessError) as exc:
        OUTPUT.mkdir(parents=True, exist_ok=True)
        write_json(OUTPUT / "failure.json", {"stage": args.mode, "result": "FAIL", "error": str(exc)})
        print(f"FAIL {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
