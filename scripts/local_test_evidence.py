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


def android_inventory(directory, smoke=SMOKE):
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
    if smoke is not None and smoke not in tests:
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


def inspect_coverage_apk(apk):
    """Build flags alone do not prove the installed debug APK carries probes/runtime."""
    with zipfile.ZipFile(apk) as archive:
        dex = b"".join(archive.read(n) for n in archive.namelist() if n.endswith(".dex"))
    if not all(marker in dex for marker in (b"$jacocoInit", b"$jacocoData", b"Lcom/vladium/emma/rt/RT;",
                                            b"Lorg/jacoco/agent/rt/internal_")):
        raise ValueError("APK lacks genuine Android JaCoCo probes/coverage runtime")
    return {"probes": True, "emma_bridge": True, "jacoco_runtime": True}


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


# Optional Phase J evidence; the existing runner remains the sole executor.
J_STATUSES = {'PASS', 'FAIL', 'BLOCKED', 'NOT RUN', 'NOT APPLICABLE', 'DEFERRED BY OWNER'}


def j_path(root, relative):
    """Confine every artifact, including existing parents; never follow symlinks."""
    import os
    root = Path(root).absolute()
    path = Path(relative)
    if not isinstance(relative, str) or not relative or path.is_absolute() or '\\' in relative or ':' in relative or '..' in path.parts:
        raise ValueError('Unsafe artifact path')
    target = root / path
    for item in [root, *root.parents, target, *target.parents]:
        if item.is_symlink():
            raise ValueError('Symlink artifact path')
    if not target.resolve().is_relative_to(root.resolve()):
        raise ValueError('Artifact escapes run')
    return target


def j_atomic(root, relative, data):
    import os
    import uuid
    target = j_path(root, relative)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = j_path(root, relative + '.' + uuid.uuid4().hex + '.tmp')
    try:
        with temporary.open('x', encoding='utf-8') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def j_redact(value):
    """Public reports are projections; raw evidence stays local and is not publication-safe."""
    if isinstance(value, dict):
        return {k: '[DEVICE]' if k.lower() in ('serial', 'android_id', 'device_id') else j_redact(v) for k, v in value.items()}
    if isinstance(value, list):
        return [j_redact(v) for v in value]
    if not isinstance(value, str):
        return value
    # Multiword document names and URI segments cannot be safely separated from log prose.
    value = '\n'.join('[PRIVATE_DOCUMENT_CONTEXT]' if re.search(r'(?i)\.pdf(?![\w.])|(?:content|file)://', line) else line
                      for line in value.split('\n'))
    value = re.sub(r'(?i)(?:content|file)://[^\s<>"\']+', '[PRIVATE_URI]', value)
    value = re.sub(r'(?i)\b[A-Z]:[\\/][^\s<>"\']+', '[PRIVATE_PATH]', value)
    value = re.sub(r'(?<![\w.])/(?:[^\s<>"\']+)', '[PRIVATE_PATH]', value)
    value = re.sub(r'(?i)\b[^\s<>"\']+\.pdf(?![\w.])', '[PRIVATE_PDF]', value)
    value = re.sub(r'\bemulator-\d+\b|\bserial[=: ]+[^\s,;]+', '[DEVICE]', value, flags=re.I)
    return value


def j_redact_xml(path):
    """Preserve JUnit structure/counts; redact attribute and text values only."""
    tree = ET.parse(path)
    for node in tree.getroot().iter():
        node.attrib.update({key: j_redact(value) for key, value in node.attrib.items()})
        if node.text:
            node.text = j_redact(node.text)
        if node.tail:
            node.tail = j_redact(node.tail)
    result = ET.tostring(tree.getroot(), encoding='unicode')
    ET.fromstring(result)
    return result


def j_capture(root, run):
    import hashlib
    import platform
    import subprocess
    from datetime import datetime, timezone
    root = Path(root)
    def git(*args):
        return subprocess.check_output(['git', *args], cwd=root, text=True, timeout=15).strip()
    names = set(git('ls-files', '--cached', '--others', '--exclude-standard').splitlines())
    hashes = {}
    for name in sorted(names):
        path = j_path(root, name)
        if path.is_file():
            hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    build_names = ('app/build.gradle.kts', 'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties')
    capture = dict(schema=1, run=run, revision=git('rev-parse', 'HEAD'), branch=git('branch', '--show-current'),
        tree=git('rev-parse', 'HEAD^{tree}'), dirty=bool(git('status', '--porcelain')),
        source_digest=hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest(),
        build={name: hashes[name] for name in build_names},
        environment=dict(os=platform.system(), architecture=platform.machine(), python=platform.python_version()),
        expected_jvm=sorted(android_inventory(root / 'app/src/test', smoke=None) |
                            android_inventory(root / 'app/src/testDebug', smoke=None)),
        started=datetime.now(timezone.utc).isoformat(), device='NOT SELECTED', tier='A')
    java = subprocess.run(['java', '-version'], text=True, capture_output=True, timeout=15)
    capture['environment']['java'] = (java.stdout + java.stderr).strip() if java.returncode == 0 else 'UNAVAILABLE'
    j_identity(capture)
    return capture


def j_identity(capture):
    if not isinstance(capture, dict):
        raise ValueError('Invalid capture')
    if capture.get('tier') != 'A' or capture.get('device') != 'NOT SELECTED':
        raise ValueError('Device/API execution forbidden in M1')
    if capture.get('schema') != 1 or not re.fullmatch('[0-9a-f]{32}', str(capture.get('run', ''))):
        raise ValueError('Invalid run identity')
    for key, size in [('revision', 40), ('tree', 40), ('source_digest', 64)]:
        if not re.fullmatch('[0-9a-f]{' + str(size) + '}', str(capture.get(key, ''))):
            raise ValueError('Missing/invalid source identity')
    expected = {'app/build.gradle.kts', 'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties'}
    if not isinstance(capture.get('build'), dict) or set(capture['build']) != expected or any(
            not isinstance(v, str) or not re.fullmatch('[0-9a-f]{64}', v) for v in capture['build'].values()):
        raise ValueError('Missing/invalid build identity')
    tests = capture.get('expected_jvm', [])
    if not isinstance(tests, list) or not tests or any(not isinstance(t, str) for t in tests) or len(tests) != len(set(tests)):
        raise ValueError('Missing/duplicate expected JUnit inventory')
    if not capture.get('branch') or not capture.get('environment') or not capture.get('started'):
        raise ValueError('Missing attribution')


def j_catalogue(path):
    catalogue = json.loads(Path(path).read_text(encoding='utf-8'))
    if catalogue.get('schema') != 1:
        raise ValueError('Unknown catalogue schema')
    entries = catalogue['scenarios']
    ids = [s['id'] for s in entries]
    checks = [s['check'] for s in entries if s['implemented'] and s['tier'] == 'A']
    if len(ids) != len(set(ids)) or len(checks) != len(set(checks)):
        raise ValueError('Duplicate scenario')
    if set(checks) != {'jvm', 'lint', 'debug', 'release', 'fixture-dependencies', 'python', 'localization', 'fixtures'}:
        raise ValueError('Incomplete Tier A catalogue')
    if any(s.get('status', 'NOT RUN') not in ('NOT RUN', 'DEFERRED BY OWNER') for s in entries if not s['implemented']):
        raise ValueError('Unimplemented scenarios cannot claim execution')
    for scenario in entries:
        if scenario['implemented'] and scenario['tier'] != 'A':
            if scenario['tier'] != 'B' or scenario.get('status') != 'NOT RUN' or 'check' in scenario:
                raise ValueError('Device catalogue is descriptive only; execution not authorized')
            for field in ('test_method', 'evidence_origin', 'fixture', 'expected_outcome', 'execution', 'test_environment'):
                if not scenario.get(field):
                    raise ValueError('Missing device scenario attribution')
            if not re.fullmatch(r'[\w.]+', scenario['test_method']):
                raise ValueError('Invalid mapped JUnit method')
    if not any(s['packages'] == ['J08'] and not s['implemented'] and s.get('status') == 'DEFERRED BY OWNER' for s in entries):
        raise ValueError('J08 must stay deferred')
    return catalogue


def j_report(root, capture, summary, catalogue, *, current, complete=False):
    """Validate original evidence before creating a redacted, versioned public projection."""
    import hashlib
    import html
    from datetime import datetime, timezone
    from verify_test_results import verify
    j_identity(capture)
    j_identity(current)
    if any(current[k] != capture[k] for k in ('run', 'revision', 'tree', 'source_digest', 'build', 'branch', 'expected_jvm')):
        raise ValueError('Source/build changed during run')
    if summary.get('suite') != 'host':
        raise ValueError('Phase J M1 is host-only')
    if summary.get('run') != capture['run'] or summary.get('commit') != capture['revision']:
        raise ValueError('Mismatched run/revision')
    checks = summary.get('host_checks', [])
    names = [c['name'] for c in checks]
    allowed = {s['check'] for s in catalogue['scenarios'] if s['implemented'] and s['tier'] == 'A'}
    if len(names) != len(set(names)) or not set(names) <= allowed:
        raise ValueError('Duplicate/unexpected evidence')
    rows = []
    for scenario in catalogue['scenarios']:
        row = dict(id=scenario['id'], packages=scenario['packages'], status=scenario.get('status', 'NOT RUN'), artifacts=[])
        if scenario['implemented'] and scenario['tier'] == 'B':
            row.update({key: scenario[key] for key in ('test_method', 'evidence_origin', 'fixture', 'expected_outcome', 'test_environment')})
            row.update(status='NOT RUN', executed_test_method=None, source_revision=capture['revision'],
                       source_digest=capture['source_digest'], execution_environment='NOT RUN: device execution not authorized',
                       device='NOT SELECTED')
        if scenario['implemented'] and scenario['tier'] == 'A':
            item = next((x for x in checks if x['name'] == scenario['check']), None)
            if item:
                status = item['status']
                if status not in J_STATUSES:
                    raise ValueError('Invalid status')
                row.update(status=status, tests=item.get('tests'), provenance='host-subprocess')
                commands = [c for c in summary.get('commands', []) if c.get('log') == item['log']]
                if status in ('PASS', 'FAIL'):
                    if len(commands) != 1:
                        raise ValueError('Missing/duplicate command')
                    command = commands[0]
                    if command.get('exit') is None or (status == 'PASS' and command['exit'] != 0):
                        raise ValueError('Invalid command outcome')
                    if command.get('run') != capture['run'] or command.get('revision') != capture['revision']:
                        raise ValueError('Stale command provenance')
                    if not command.get('started') or not command.get('finished'):
                        raise ValueError('Missing command timestamps')
                    start = datetime.fromisoformat(command['started'])
                    end = datetime.fromisoformat(command['finished'])
                    if not start.tzinfo or not end.tzinfo or start < datetime.fromisoformat(capture['started']) or end < start:
                        raise ValueError('Stale/invalid command timestamps')
                    log = j_path(root, item['log'])
                    if not log.is_file() or log.stat().st_size == 0:
                        raise ValueError('Missing/empty command evidence')
                    if hashlib.sha256(log.read_bytes()).hexdigest() != command.get('sha256'):
                        raise ValueError('Corrupt/stale command log')
                    row['command'] = {k: command[k] for k in ('command', 'exit', 'started', 'finished')}
                    public = 'phase-j/artifacts/' + item['log']
                    j_atomic(root, public, j_redact(log.read_text(encoding='utf-8')))
                    row['artifacts'].append(dict(path=public, sha256=hashlib.sha256(j_path(root,public).read_bytes()).hexdigest()))
                    if scenario['check'] == 'jvm' and status == 'PASS':
                        folder = j_path(root, 'host/jvm-xml')
                        paths = list(folder.rglob('*.xml'))
                        for path in paths:
                            relative = path.relative_to(root).as_posix()
                            j_path(root, relative)
                            if hashlib.sha256(path.read_bytes()).hexdigest() != summary.get('j_jvm_artifacts', {}).get(relative):
                                raise ValueError('Corrupt/stale JUnit artifact')
                        if set(summary.get('j_jvm_artifacts', {})) != {p.relative_to(root).as_posix() for p in paths}:
                            raise ValueError('Missing JUnit artifact')
                        count = verify(folder)
                        cases = [t.get('classname') + '.' + t.get('name') for p in paths for t in ET.parse(p).getroot().iter('testcase')]
                        if len(cases) != len(set(cases)) or count != item.get('tests') or set(cases) != set(capture['expected_jvm']):
                            raise ValueError('Incomplete/duplicate JUnit inventory')
                        row['test_inventory'] = cases
                        for path in sorted(folder.glob('*.xml')):
                            ref = 'phase-j/artifacts/host/jvm-xml/' + path.name
                            j_atomic(root, ref, j_redact_xml(path))
                            row['artifacts'].append(dict(path=ref, sha256=hashlib.sha256(j_path(root,ref).read_bytes()).hexdigest()))
        rows.append(row)
    active = [r for r,s in zip(rows,catalogue['scenarios']) if s['implemented'] and s['tier'] == 'A']
    status = 'FAIL' if any(r['status'] == 'FAIL' for r in active) else (
        'PASS' if complete and summary.get('status') == 'PASS' and all(r['status'] == 'PASS' for r in active) else 'BLOCKED')
    document = dict(schema=1, profile='phase-j', capture=capture, finished=datetime.now(timezone.utc).isoformat(),
        complete=complete, status=status, scenarios=rows, diagnostics='NOT RUN', android='NOT RUN',
        diagnostic_policy='unchanged; controlled outcomes are never ordinary PASS')
    # Redact values only; relative, validated public artifact references remain navigable.
    public = j_redact(document)
    j_atomic(root, 'phase-j/capture.json', json.dumps(j_redact(capture), indent=2))
    j_atomic(root, 'phase-j/summary.json', json.dumps(public, indent=2))
    j_atomic(root, 'phase-j/report.html', '<!doctype html><meta charset="utf-8"><title>Phase J Tier A</title><pre>' + html.escape(json.dumps(public,indent=2)) + '</pre>')
    return public


if __name__ == "__main__":
    try:
        if len(sys.argv) != 3 or sys.argv[1] != "fixture-dependencies":
            raise ValueError("Usage: local_test_evidence.py fixture-dependencies REQUIREMENTS")
        print(json.dumps(fixture_dependencies(sys.argv[2]), indent=2))
    except (ValueError, OSError, importlib.metadata.PackageNotFoundError) as error:
        print(f"BLOCKED {error}", file=sys.stderr)
        sys.exit(2)
