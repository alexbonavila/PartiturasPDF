"""Read-only APK and executed-test evidence checks for the opt-in local orchestrator."""
from pathlib import Path
import importlib.metadata
import json
import sys
import re
import xml.etree.ElementTree as ET
import zipfile

APP = "com.abdev.partituraspdf"
NORMAL_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
SAF_CLASS = APP + ".storage.saf.SafLocalE2eTest"
SMOKE = APP + ".ExampleInstrumentedTest.useAppContext"


def android_inventory(directory):
    """Inventory the repository's existing plain JUnit 4 Kotlin/Java tests; reject unknown forms."""
    tests = set()
    for path in sorted(Path(directory).rglob("*")):
        if path.suffix not in (".kt", ".java"):
            continue
        source = re.sub(r"/\*.*?\*/|//[^\n]*", "", path.read_text(encoding="utf-8"), flags=re.S)
        pattern = r"@Test(?:\([^)]*\))?\s+fun\s+(\w+)\s*\(" if path.suffix == ".kt" else (
            r"@Test(?:\([^)]*\))?\s+(?:public\s+)?void\s+(\w+)\s*\(")
        methods = re.findall(pattern, source)
        if len(methods) != len(re.findall(r"@Test\b", source)):
            raise ValueError(f"Unsupported JUnit inventory syntax: {path}")
        if methods:
            declaration = re.search(r"(?m)^package\s+([\w.]+)", source)
            if not declaration:
                raise ValueError(f"Missing test package: {path}")
            package = declaration[1]
            for method in methods:
                tests.add(f"{package}.{path.stem}.{method}")
    if SMOKE not in tests:
        raise ValueError("Required smoke test absent from ordinary source inventory")
    return tests


def inspect_apk(apk, manifest_dump, mode, inventory=()):
    """Check actual compiled manifest and DEX, never a build filename or configured default."""
    package = re.search(r"package=\"([^\"]+)\"", manifest_dump)
    instrumentation = re.findall(r"(?ms)^\s*E: instrumentation\b(.*?)(?=^\s*E:|\Z)", manifest_dump)
    if not package or package[1] != APP + ".test" or len(instrumentation) != 1:
        raise ValueError("Unexpected test APK package or instrumentation declarations")
    node = instrumentation[0]
    def attribute(name):
        match = re.search(r"android:" + name + r"(?:\([^)]*\))?=\"([^\"]+)\"", node)
        return match[1] if match else None
    if attribute("name") != NORMAL_RUNNER or attribute("targetPackage") != APP:
        raise ValueError("Test APK must select the normal AndroidJUnitRunner and target application")
    with zipfile.ZipFile(apk) as archive:
        dex = b"".join(archive.read(n) for n in archive.namelist() if n.endswith(".dex"))
    has_saf = ("L" + SAF_CLASS.replace(".", "/") + ";").encode() in dex
    if not dex or has_saf != (mode == "saf"):
        raise ValueError("Ordinary/opt-in APK mode mismatch")
    for test in inventory:
        class_name, method = test.rsplit(".", 1)
        if ("L" + class_name.replace(".", "/") + ";").encode() not in dex or method.encode() + b"\0" not in dex:
            raise ValueError(f"Ordinary test missing from APK: {test}")
    return {"mode": mode, "runner": NORMAL_RUNNER, "target": APP, "opt_in_saf": has_saf}


class IncompleteInstrumentation(ValueError):
    def __init__(self, message, cases):
        super().__init__(message)
        self.cases = cases


def parse_android(output, expected):
    """Validate a complete unfiltered AndroidJUnitRunner run, retaining executed failure details."""
    cases, starts, finished, bundle, stack_key = [], set(), set(), {}, False
    total = None
    try:
        for line in output.splitlines():
            if line.startswith("INSTRUMENTATION_STATUS: "):
                item = line.split(": ", 1)[1]
                if "=" in item:
                    key, value = item.split("=", 1)
                    bundle[key] = value
                    stack_key = key == "stack"
            elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
                code = int(line.split(": ", 1)[1])
                identity = bundle.get("class", "") + "." + bundle.get("test", "")
                if "class" not in bundle or "test" not in bundle:
                    raise ValueError("Missing JUnit class/test identity")
                if identity not in expected:
                    raise ValueError(f"Unexpected ordinary test: {identity}")
                declared = int(bundle["numtests"])
                if declared <= 0 or total not in (None, declared):
                    raise ValueError("Inconsistent instrumentation test count")
                total = declared
                if code == 1:
                    if identity in starts:
                        raise ValueError("Duplicate JUnit start")
                    starts.add(identity)
                else:
                    if identity not in starts or identity in finished:
                        raise ValueError("Missing/duplicate/out-of-order JUnit result")
                    if code not in (0, -1, -2) or (code < 0 and not bundle.get("stack", "").strip()):
                        raise ValueError("Skipped, incomplete or unexplained JUnit result")
                    finished.add(identity)
                    cases.append({"name": identity, "status": "FAIL" if code < 0 else "PASS",
                                  "detail": bundle.get("stack", "")})
                bundle, stack_key = {}, False
            elif stack_key:
                bundle["stack"] += "\n" + line
        completions = re.findall(r"(?m)^INSTRUMENTATION_CODE: (-?\d+)\s*$", output)
        if (completions != ["-1"] or re.search(r"(?m)^INSTRUMENTATION_(?:FAILED|ABORTED)", output)
                or re.search(r"(?m)^INSTRUMENTATION_RESULT: shortMsg=", output)):
            raise ValueError("Instrumentation incomplete or crashed")
        completion = re.search(r"(?m)^INSTRUMENTATION_CODE: -1\s*$", output)
        if completion.start() < output.rfind("INSTRUMENTATION_STATUS_CODE:"):
            raise ValueError("Test result follows runner completion")
        if not cases or starts != finished or finished != set(expected) or len(cases) != total:
            raise ValueError("Zero, missing or incomplete ordinary tests (including required smoke)")
        if "FAILURES!!!" in output and not any(c["status"] == "FAIL" for c in cases):
            raise ValueError("Inconsistent failure summary")
        return cases
    except (ValueError, KeyError) as error:
        raise IncompleteInstrumentation(str(error), cases) from error


def write_junit(path, name, cases, blocker=None):
    cases = list(cases)
    if blocker:
        cases.append({"name": "infrastructure", "status": "BLOCKED", "detail": blocker})
    root = ET.Element("testsuite", name=name, tests=str(len(cases)),
                      failures=str(sum(c["status"] == "FAIL" for c in cases)),
                      errors=str(sum(c["status"] == "BLOCKED" for c in cases)),
                      skipped=str(sum(c["status"] == "NOT RUN" for c in cases)))
    for item in cases:
        class_name, _, method = item["name"].rpartition(".")
        node = ET.SubElement(root, "testcase", classname=class_name or name, name=method or item["name"])
        if item["status"] != "PASS":
            kind = {"FAIL": "failure", "BLOCKED": "error", "NOT RUN": "skipped"}[item["status"]]
            ET.SubElement(node, kind, message=item.get("detail", item["status"]))
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def fixture_dependencies(path):
    """Check installed declared fixture tools without installing or importing their code."""
    def numeric(value):
        if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)*(?:\.post[0-9]+)?", value):
            raise ValueError(f"Unverified dependency version form: {value}")
        parts = tuple(map(int, value.split(".post")[0].split(".")))
        return parts + (0,) * max(0, 3 - len(parts))
    found = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([\w-]+)>=(\d+(?:\.\d+)*),<(\d+(?:\.\d+)*)", line)
        if not match:
            raise ValueError(f"Unsupported fixture requirement: {line}")
        name, lower, upper = match.groups()
        version = importlib.metadata.version(name)
        if not numeric(lower) <= numeric(version) < numeric(upper):
            raise ValueError(f"Missing compatible fixture dependency: {name} {version}; required {line}")
        found[name] = version
    if not found:
        raise ValueError("No declared fixture dependencies")
    return found


if __name__ == "__main__":
    try:
        if len(sys.argv) != 3 or sys.argv[1] != "fixture-dependencies":
            raise ValueError("Usage: local_test_evidence.py fixture-dependencies REQUIREMENTS")
        print(json.dumps(fixture_dependencies(sys.argv[2]), indent=2))
    except (ValueError, OSError, importlib.metadata.PackageNotFoundError) as error:
        print(f"BLOCKED {error}", file=sys.stderr)
        sys.exit(2)
