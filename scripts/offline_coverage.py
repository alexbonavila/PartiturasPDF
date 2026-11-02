#!/usr/bin/env python3
"""Read-only producer verification and separate host-only POST_HOC_REANALYSIS. No collection."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import html
import json
import os
import platform
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

# Importing the shared policy must not update caches in a producer checkout.
sys.dont_write_bytecode = True
import local_coverage as lc
import local_test_evidence as evidence
import persistence_coverage as pc

ROOT = Path(__file__).resolve().parents[1]
PRODUCER = 'ad08aeeb28526fa6e57195f35c32d351b9ee4692'
BINARIES = {
    'inputs/jvm.exec': ('3d3f470690be04adc9005c42cc7984091e77c2993cf5ac8e68aeeac20efde961', 239586),
    'inputs/android/coverage.ec': ('4de7636926a8cc391336d9187fa5dec2e48ad23cf6f3190ec3513f2eea59effa', 260764),
}
# Existing project libraries only. Never load executable jars chosen by an untrusted capture.
LIBRARIES = {
    'org.jacoco.core-0.8.14.jar': '28abbf0eea5a08e4f24097f2fbac663ca17c341c25c3a04d90d6cd325943c995',
    'org.jacoco.report-0.8.14.jar': 'a3e2026060ab8b8d5c650706406234bb4c033dfd5376afeb8b1666e8ed27c453',
    'asm-9.9.jar': '03d99a74ad1ee5c71334ef67437f4ef4fe3488caa7c96d8645abc73c8e2017d4',
    'asm-commons-9.9.jar': 'db2f6f26150bbe7c126606b4a1151836bcc22a1e05a423b3585698bece995ff8',
    'asm-tree-9.9.jar': '42178f3775c9c63f9e5e1446747d29b4eca4d91bd6e75e5c43cfa372a47d38c6',
}
AIDL_FILES = ['classes/javac/debug/com/abdev/partituraspdf/pdf/android/isolated/IPdfWorker' + suffix + '.class'
              for suffix in ('', '$Default', '$Stub', '$Stub$Proxy', '$_Parcel')]
TOOLS = ('scripts/offline_coverage.py', 'scripts/OfflineCoverageReport.java',
         'scripts/PersistenceCoverageInputs.java', 'scripts/persistence_coverage.py',
         'scripts/local_coverage.py', 'scripts/local_test_evidence.py', 'scripts/verify_test_results.py',
         'app/src/androidTest/java/com/abdev/partituraspdf/reader/pdfium_diagnostic_evidence.py',
         'app/src/androidTest/java/com/abdev/partituraspdf/reader/run_pdfium_process_experiments.py')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def safe_path(value):
    require(not any(ord(c) < 32 for c in str(value)) and '..' not in Path(value).parts, 'Unsafe path spelling')
    path = Path(os.path.abspath(value))
    require(not any(p.is_symlink() for p in (path, *path.parents)), 'Symlink path rejected')
    return path


def relative(name):
    path = PurePosixPath(name)
    require(not any(ord(c) < 32 for c in name) and bool(name) and not path.is_absolute() and '..' not in path.parts and
            '\\' not in name and str(path) == name and name != '.', 'Unsafe artifact path')
    return path


def atomic(path, value):
    tmp = path.with_name(path.name + '.tmp')
    with tmp.open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2, sort_keys=True)
        stream.write('\n')
    tmp.replace(path)


def read_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate JSON field')
            result[key] = value
        return result
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)


def git(*args):
    return subprocess.run(['git', '--no-optional-locks', '-c', 'protocol.allow=never', '-C', str(ROOT), *args],
                          env=dict(os.environ, GIT_NO_LAZY_FETCH='1', GIT_TERMINAL_PROMPT='0'), check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120).stdout


def blob(revision, name):
    relative(name)
    return git('show', revision + ':' + name)


def tree(revision, prefix):
    rows = git('ls-tree', '-rz', '--full-tree', revision, '--', prefix).split(b'\0')
    result = set()
    for row in filter(None, rows):
        meta, name = row.split(b'\t', 1)
        require(meta.split()[0] in (b'100644', b'100755'), 'Non-regular producer source')
        result.add(name.decode('utf-8'))
    return result


def artifact_inventory(directory):
    result = {}
    for path in sorted(directory.rglob('*')):
        require(not path.is_symlink(), 'Symlink inside evidence')
        require(path.is_dir() or path.is_file(), 'Non-regular evidence entry')
        if path.is_file():
            result[path.relative_to(directory).as_posix()] = pc.digest(path)
    return result


def verify_artifacts(directory, summary):
    actual = artifact_inventory(directory)
    expected = summary['artifacts']
    require(isinstance(expected, dict) and bool(expected), 'Missing captured artifact inventory')
    for name, sha in expected.items():
        relative(name)
        require(re.fullmatch('[0-9a-f]{64}', sha) is not None, 'Malformed artifact hash')
        require(actual.get(name) == sha, 'Missing/altered artifact: ' + name)
    wrappers = {'summary.json', 'junit.xml', 'index.html'}
    require(set(actual) - wrappers == set(expected), 'Uninventoried or missing producer artifacts')
    for name in ('capture.json', 'configuration.json', 'classes.json', 'persistence/run.json',
                 'instrumentation.log', 'app-debug.apk', 'app-debug-androidTest.apk',
                 'app-debug-androidTest.apk.manifest.txt'):
        require(name in expected, 'Required artifact missing: ' + name)
    require(set(AIDL_FILES) <= set(expected), 'Missing original javac/AIDL class family')
    for name, (sha, size) in BINARIES.items():
        path = directory / name
        require(actual.get(name) == sha and path.stat().st_size == size, 'Pinned binary identity mismatch: ' + name)
    return actual


def reconstruct_sources(build, destination):
    hashes = build['sourceHashes']
    tracked = tree(PRODUCER, 'app/src')
    require(tracked <= set(hashes), 'Incomplete producer source inventory')
    for name, sha in hashes.items():
        relative(name)
        require(name.startswith('app/src/'), 'Unexpected producer source path')
        # Ignored original bytes (e.g. Python caches) cannot be recreated from Git.
        data = blob(PRODUCER, name) if name in tracked else (safe_path(ROOT / name)).read_bytes()
        require(hashlib.sha256(data).hexdigest() == sha, 'Producer source mismatch/missing retained bytes: ' + name)
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    for name, sha in build['buildFiles'].items():
        relative(name)
        require(hashlib.sha256(blob(PRODUCER, name)).hexdigest() == sha, 'Producer tooling mismatch: ' + name)
    required_tools = {'app/build.gradle.kts', 'gradle/libs.versions.toml', 'scripts/PersistenceCoverageInputs.java',
                      'scripts/persistence_coverage.py', 'scripts/local_coverage.py', 'scripts/local_test_evidence.py',
                      'scripts/local-coverage.init.gradle', 'scripts/local_e2e.py',
                      'build.gradle.kts', 'gradle.properties', 'gradle/wrapper/gradle-wrapper.properties'}
    require(required_tools <= set(build['buildFiles']), 'Incomplete original tooling attribution')
    return hashes


def reconstruct_classes(directory, destination):
    target = destination / 'app/build/intermediates'
    for compiler in ('built_in_kotlinc', 'javac'):
        for variant, task in (('debug', 'Debug'), ('debugUnitTest', 'DebugUnitTest'), ('debugAndroidTest', 'DebugAndroidTest')):
            source = directory / 'classes' / compiler / variant
            if source.is_dir():
                suffix = 'Kotlin' if compiler == 'built_in_kotlinc' else 'JavaWithJavac'
                shutil.copytree(source, target / compiler / variant / ('compile' + task + suffix) / 'classes', copy_function=shutil.copy2)
    return target / 'built_in_kotlinc/debug/compileDebugKotlin/classes'


def junit(directory, expected, started=0):
    count = lc.exact_junit(directory, expected)
    for path in directory.rglob('*.xml'):
        require(path.stat().st_mtime_ns // 1_000_000 >= started, 'Stale original JUnit evidence')
        for suite in ET.parse(path).getroot().iter('testsuite'):
            if suite.get('tests') is not None:
                require(int(suite.get('tests')) == len(list(suite.iter('testcase'))), 'Incorrect JUnit suite count')
    return dict(executed=count, passed=count, failed=0, errors=0, skipped=0)


def authenticate(directory, work, outer):
    summary = read_json(directory / 'summary.json')
    require(summary['status'] == 'BLOCKED' and not summary['failures'], 'Original producer must remain BLOCKED without functional failures')
    manifest = verify_artifacts(directory, summary)
    capture = read_json(directory / 'capture.json')
    config = read_json(directory / 'configuration.json')
    frozen = read_json(directory / 'classes.json')
    run = read_json(directory / 'persistence/run.json')
    build = run['build']
    require(capture['commit'] == build['commit'] == outer['commit'] == PRODUCER, 'Incorrect producer revision')
    require(capture['working_tree'] == build['workingTree'] == outer['working_tree'] == '', 'Execution source was not clean')
    require(build['localCapture'] == capture, 'Captured build identity mismatch')
    require(run['format'] == 1 and run['androidApi'] == 26 and run['run'] and
            type(run['started']) is int and 0 < run['started'], 'Invalid producer run identity')
    require(capture['run'] == outer['run'] and re.fullmatch('[0-9a-f]{32}', capture['run']) is not None,
            'Producer run identifiers disagree')
    require(outer['status'] == 'BLOCKED' and not any(c['status'] != 'PASS' for c in outer['cleanup']), 'Producer/cleanup classification mismatch')
    device = capture['device']
    require(device['api'] == 26 and device['identity_verified'] is True and device['emulator'] is True and
            re.fullmatch('emulator-[0-9]+', device['serial']) is not None, 'Recorded API26 identity not verified')
    require(device['abi'] in ('arm64-v8a', 'x86_64') and device['name'], 'Missing native API26 AVD identity')
    require(all(outer['device'].get(key) == value for key, value in device.items()
                if key in ('api', 'abi', 'serial', 'name', 'image', 'boot_id')), 'Contradictory recorded device identity')
    require(capture['host']['status'] == 'PASS' and summary['host'] == capture['host'], 'Original host readiness mismatch')
    require(config['androidCoverage'] is True and config['versions'] == capture['versions'], 'Coverage configuration mismatch')
    for key in ('gradle', 'agp', 'jdk', 'jacoco'):
        require(config['versions'][key] == build[key], 'Original tool versions disagree')
    require(config['versions']['jacoco'] == '0.8.14', 'Unsupported JaCoCo version')
    reconstruct_sources(build, work)
    selection = lc.android_selection(work)
    expected_jvm = evidence.android_inventory(work / 'app/src/test', evidence.APP + '.ExampleUnitTest.addition_isCorrect')
    if (work / 'app/src/testDebug').is_dir():
        expected_jvm |= evidence.android_inventory(work / 'app/src/testDebug', None)
    require(capture['android_selection'] == selection and capture['android_inventory'] == selection['all'] and
            capture['jvm_inventory'] == sorted(expected_jvm), 'Captured/source test inventory mismatch')
    checked = dict(status='VERIFIED', jvm=junit(directory / 'jvm-junit', expected_jvm, run['started']),
                   android=junit(directory / 'android-junit', set(selection['standard']), run['started']),
                   diagnostics=dict(status='NOT RUN', methods=selection['diagnostics']))
    require(summary['jvm_status'] == summary['android_status'] == 'PASS' and
            summary['jvm_tests'] == checked['jvm']['executed'] and summary['android_tests'] == checked['android']['executed'],
            'Producer JUnit summary mismatch')
    raw = (directory / 'instrumentation.log').read_text(encoding='utf-8')
    cases = evidence.parse_android(raw, set(selection['standard']), allow_isolated_pdf_progress=True)
    require(all(c['status'] == 'PASS' for c in cases), 'Original instrumentation assertions failed')
    entries = [entry for entry in outer['commands'] if entry['log'] == 'coverage/instrumentation.log']
    require(len(entries) == 1 and type(entries[0]['exit']) is int and entries[0]['exit'] == 0, 'Missing/ambiguous instrumentation command or exit')
    command = entries[0]['command']
    require(command[1:3] == ['-s', device['serial']], 'Instrumentation device mismatch')
    path = '/data/data/' + evidence.APP + '/files/local-coverage-' + capture['run'] + '/coverage.ec'
    lc.validate_android_selection(command, path)
    require(raw.splitlines().count('INSTRUMENTATION_RESULT: coverageFilePath=' + path) == 1, 'Coverage output acknowledgement mismatch')
    apks = capture['apks']
    for name in ('app-debug.apk', 'app-debug-androidTest.apk'):
        hashes = [sha for name_path, sha in apks.items() if Path(name_path).name == name]
        require(len(hashes) == 2 and set(hashes) == {manifest[name]}, 'Captured APK identity mismatch')
    manifest_entries = [e for e in outer['commands'] if e['log'] == 'coverage/app-debug-androidTest.apk.manifest.txt']
    require(len(manifest_entries) == 1 and type(manifest_entries[0]['exit']) is int and manifest_entries[0]['exit'] == 0, 'Missing original APK manifest inspection')
    manifest_command = manifest_entries[0]['command']
    require(manifest_command[1:3] == ['dump', 'xmltree'] and
            Path(manifest_command[3]).name == 'app-debug-androidTest.apk' and
            manifest_command[4:] == ['--file', 'AndroidManifest.xml'] and
            manifest_command[3] in apks, 'Original APK manifest command identity mismatch')
    require(evidence.inspect_coverage_apk(directory / 'app-debug.apk') == summary['android_instrumentation'],
            'Original coverage APK inspection mismatch')
    require(evidence.inspect_apk(directory / 'app-debug-androidTest.apk',
                         (directory / 'app-debug-androidTest.apk.manifest.txt').read_text(), 'normal', set(selection['all']))
            == summary['apk_mode'], 'Original APK mode inspection mismatch')
    require(run['classes'] == frozen['production'] and run['testClasses'] == frozen['tests'], 'Frozen class provenance mismatch')
    return dict(summary=summary, capture=capture, config=config, run=run, frozen=frozen,
                manifest=manifest, junit=checked, classes=reconstruct_classes(directory, work))


def trusted_classpath(value):
    selected = {}
    for text in value.split(os.pathsep):
        path = safe_path(text)
        if path.name in LIBRARIES:
            require(path.name not in selected, 'Duplicate analyzer library')
            require(pc.digest(path) == LIBRARIES[path.name], 'Analyzer library hash mismatch')
            selected[path.name] = path
    require(set(selected) == set(LIBRARIES), 'Required existing JaCoCo/ASM libraries unavailable; no downloads attempted')
    return os.pathsep.join(str(selected[name]) for name in LIBRARIES)


class Backend:
    def __init__(self, java, classpath, work, classes):
        self.java, self.cp, self.work, self.classes = java, classpath, work, classes

    def reader(self, mode, *files):
        command = [self.java, '-Dlocal.coverage.sources=' + str(self.work / 'app/src/main/java'),
                   '-cp', self.cp, str(ROOT / 'scripts/PersistenceCoverageInputs.java'), mode,
                   str(self.classes), *map(str, files)]
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=180, check=True)
        except subprocess.CalledProcessError as error:
            raise ValueError('JaCoCo identity validation failed: ' + error.stderr.strip()) from error
        return json.loads(result.stdout)

    def report(self, output, scope, classes, inputs):
        target = output / (scope + '-classes.txt')
        target.write_text(''.join(str(self.classes / c['path']) + '\n' for c in classes.values()), encoding='utf-8')
        try:
            subprocess.run([self.java, '-cp', self.cp, str(ROOT / 'scripts/OfflineCoverageReport.java'),
                            str(output / (scope + '.xml')), str(output / (scope + '.html')),
                            str(self.work / 'app/src'), str(target), *map(str, inputs)],
                           check=True, capture_output=True, text=True, timeout=180)
        except subprocess.CalledProcessError as error:
            raise ValueError('JaCoCo report generation failed: ' + error.stderr.strip()) from error


def check_binary_evidence(auth, parsed, directory):
    require(parsed['classes'] == auth['frozen']['production'], 'Retained Kotlin class identity/probe mismatch')
    inputs = [directory / name for name in BINARIES]
    require([entry['path'] for entry in parsed['inputs']] == list(map(str, inputs)), 'Binary input order/identity mismatch')
    pc.check_inputs(auth['run'], parsed, inputs[0], inputs[1:], int(datetime.now(timezone.utc).timestamp() * 1000))
    native = {'com/abdev/partituraspdf/pdf/android/' + name for name in
              ('AndroidNativePdfDocument', 'AndroidNativePdfPage', 'AndroidRenderBuffer')}
    records = parsed['inputs'][1]['records']
    require(native <= {name for name, value in records.items() if value['coveredProbes'] > 0}, 'Missing genuine Android native adapter records')
    original = auth['summary']['jvm_input']
    require(original['sha256'] == BINARIES['inputs/jvm.exec'][0] and original['retained'] == 'inputs/jvm.exec',
            'Original JVM binary hash/retained identity mismatch')
    for key in ('sessions', 'records', 'testRecords'):
        require(parsed['inputs'][0][key] == original[key], 'Original JVM input attribution mismatch')
    captured = auth['summary']['android_capture']
    command = captured['command']
    require(command[1:] == ['-s', auth['capture']['device']['serial'], 'exec-out', 'run-as', evidence.APP, 'cat',
                           'files/local-coverage-' + auth['capture']['run'] + '/coverage.ec'],
            'Original Android retrieval command mismatch')
    require(type(captured['exit']) is int and captured['exit'] == 0 and captured['bytes'] == BINARIES['inputs/android/coverage.ec'][1] and
            captured['sha256'] == BINARIES['inputs/android/coverage.ec'][0], 'Original Android retrieval identity mismatch')


def scopes(auth):
    classes = auth['frozen']['production']
    result = {}
    for name, key in (('contracts', 'contractPatterns'), ('pdf', 'pdfPatterns')):
        paths = auth['config'][key]
        marker = '/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/'
        require(paths and len(paths) == len(set(paths)), 'Missing/duplicate captured scope patterns')
        wanted = set()
        for path in paths:
            require(path.count(marker) == 1, 'Unexpected captured compiled class path')
            name_path = path.split(marker)[1]
            relative(name_path)
            wanted.add(name_path)
        selected = {n: c for n, c in classes.items() if c['path'].replace('\\', '/') in wanted}
        # Independent source policy: captured configuration must not quietly narrow an existing gate.
        prefixes = ('com/abdev/partituraspdf/core/', 'com/abdev/partituraspdf/storage/api/',
                    'com/abdev/partituraspdf/pdf/api/') if name == 'contracts' else ('com/abdev/partituraspdf/pdf/android/',)
        require(set(selected) == {n for n in classes if n.startswith(prefixes)} and
                len(selected) == len(wanted), 'Captured scope denominator mismatch')
        result[name] = selected
    for scope in ('metadata', 'preferences'):
        result[scope] = {n: c for n, c in classes.items() if n.startswith(pc.PREFIX + scope + '/')}
    result['production'] = {n: c for n, c in classes.items() if c.get('production')}
    require(all(result.values()), 'Empty coverage scope')
    return result


def analyze(auth, backend, directory, output, verify_only, result=None):
    result = result if result is not None else {}
    require(backend.reader('inventory') == auth['frozen']['production'], 'Production bytecode mismatch')
    require(backend.reader('test-inventory') == auth['frozen']['tests'], 'Test bytecode mismatch')
    generated = backend.reader('generated-inventory')
    require(generated and not set(generated) & set(auth['frozen']['production']), 'Missing/overlapping generated identities')
    parsed = backend.reader('validate', *(directory / name for name in BINARIES))
    require(parsed['generatedClasses'] == generated, 'Generated identity drift')
    check_binary_evidence(auth, parsed, directory)
    selected = scopes(auth)
    atomic(output / 'binary-verification.json', parsed)
    result['input_verification'] = 'PASS'
    if verify_only:
        return dict(status='PASS', input_verification='PASS', measurements='NOT RUN', scopes={}, failures=[], generatedClasses=generated)
    measured, failures = {}, []
    result.update(scopes=measured, failures=failures, measurements='PARTIAL')
    for name, classes in selected.items():
        inputs = [directory / n for n in BINARIES]
        backend.report(output, name, classes, inputs[:1] if name == 'contracts' else inputs)
        packages = lc.analyzed_report(output / (name + '.xml'), classes)
        measured[name] = dict(package=lc.aggregate(packages), packages=packages,
                              classification='INFORMATIONAL' if name == 'production' else 'GATED')
        if name in ('contracts', 'pdf'):
            failures.extend(lc.component_gate(name, packages))
        elif name in ('metadata', 'preferences'):
            require(len(packages) == 1, 'Unexpected persistence package set')
            failures.extend(pc.thresholds({name: next(iter(packages.values()))}))
        if failures:
            result['status'] = 'FAIL'
    return dict(status='FAIL' if failures else 'PASS', measurements='VERIFIED_POST_HOC',
                scopes=measured, failures=failures, generatedClasses=generated,
                reports={name: dict(xml=pc.digest(output / (name + '.xml')),
                                    html=artifact_inventory(output / (name + '.html'))) for name in selected})


def execute(args):
    original, output = safe_path(args.producer_run), safe_path(args.output)
    require(original.is_dir() and not output.exists(), 'Input must exist; output must be new')
    require(not output.is_relative_to(original) and not original.is_relative_to(output), 'Input/output overlap')
    # Validate the entire input tree before creating any output or following a retained path.
    before = artifact_inventory(original)
    require(output.parent.is_dir(), 'Output parent must already exist')
    output.mkdir()
    result = dict(kind='POST_HOC_REANALYSIS', status='BLOCKED', mode='VERIFY_ONLY' if args.verify_only else 'ANALYZE',
                  measurements='NOT RUN', input_verification='NOT VERIFIED', worker_coverage='BLOCKED / NOT MEASURED', failures=[], blockers=[],
                  limitations=['Producer remains BLOCKED; measurements are retrospective.',
                               'App-process/shared class probes do not attest isolated-worker execution.',
                               'Recorded APK inspection is preserved; no new installed-binary attestation.'])
    provenance = dict(producer=dict(expected_commit=PRODUCER, verified=False),
                      analyzer=dict(command=sys.argv, python=sys.version, platform=platform.platform(),
                                    time=datetime.now(timezone.utc).isoformat(), output=str(output)))
    layout = None
    try:
        provenance['analyzer'].update(commit=git('rev-parse', 'HEAD').decode().strip(),
                                      files={name: pc.digest(ROOT / name) for name in TOOLS},
                                      working_tree=git('status', '--porcelain').decode())
        atomic(output / 'provenance.json', provenance)
        atomic(output / 'inputs-manifest.json', before)
        atomic(output / 'junit-verification.json', dict(status='NOT VERIFIED'))
        snapshot = output / 'producer-copy'
        shutil.copytree(original, snapshot, copy_function=shutil.copy2)
        require(artifact_inventory(snapshot) == before, 'Immutable evidence copy mismatch')
        work = output / 'retained-layout'; work.mkdir()
        auth = authenticate(snapshot / 'coverage', work, read_json(snapshot / 'summary.json'))
        provenance['producer'].update(commit=PRODUCER, metadata_verified=True, original_status='BLOCKED', capture=auth['capture'], build=auth['run']['build'],
                                      configuration=auth['config'], original_run=auth['run'],
                                      classes=auth['frozen'], artifacts=auth['manifest'])
        layout = artifact_inventory(work)
        provenance['reconstructed_layout'] = layout
        classpath = trusted_classpath(args.jacoco_classpath or auth['config']['jacocoClasspath'])
        require(Path(args.java).is_absolute() and Path(args.java).name in ('java', 'java.exe') and
                safe_path(args.java).is_file(), 'Explicit absolute Java executable required')
        version = subprocess.run([args.java, '-version'], capture_output=True, text=True, timeout=30, check=True)
        version_text = version.stderr + version.stdout
        major = re.search(r'(?:openjdk|java) version "([0-9]+)', version_text)
        require(major is not None and int(major[1]) >= 17, 'Existing host JDK17+ required for analysis helpers')
        provenance['analyzer'].update(java=version_text, jacoco='0.8.14',
                                      libraries={str(p): pc.digest(Path(p)) for p in classpath.split(os.pathsep)})
        atomic(output / 'junit-verification.json', auth['junit'])
        result.update(analyze(auth, Backend(args.java, classpath, work, auth['classes']), snapshot / 'coverage', output, args.verify_only, result))
    except (OSError, ValueError, KeyError, TypeError, AttributeError, IndexError, ET.ParseError, subprocess.SubprocessError) as error:
        result['blockers'].append(str(error))
    finally:
        try:
            unchanged = artifact_inventory(original) == before and (not (output / 'producer-copy').exists() or
                        artifact_inventory(output / 'producer-copy') == before)
        except (OSError, ValueError):
            unchanged = False
        if not unchanged:
            result['status'] = 'BLOCKED'; result['measurements'] = 'INVALIDATED'; result['input_verification'] = 'INVALIDATED'
            result['blockers'].append('Original evidence changed during analysis')
        try:
            layout_unchanged = layout is None or artifact_inventory(output / 'retained-layout') == layout
            analyzer_unchanged = 'files' not in provenance['analyzer'] or all(
                pc.digest(ROOT / name) == sha for name, sha in provenance['analyzer']['files'].items())
        except (OSError, ValueError):
            layout_unchanged = analyzer_unchanged = False
        if not layout_unchanged:
            result.update(status='BLOCKED', measurements='INVALIDATED', input_verification='INVALIDATED')
            result['blockers'].append('Reconstructed input bytes changed during analysis')
        if not analyzer_unchanged:
            result.update(status='BLOCKED', measurements='INVALIDATED', input_verification='INVALIDATED')
            result['blockers'].append('Analyzer sources changed during analysis')
        result['integrity_checks'] = dict(original_and_copy_unchanged=unchanged,
                                          layout_unchanged=layout_unchanged, analyzer_sources_unchanged=analyzer_unchanged)
        provenance['producer']['verified'] = bool(provenance['producer'].get('metadata_verified') and
                                                   result['input_verification'] == 'PASS')
        atomic(output / 'provenance.json', provenance)
        atomic(output / 'summary.json', result)
        page = '<!doctype html><meta charset="utf-8"><h1>POST_HOC_REANALYSIS</h1><pre>' + html.escape(json.dumps(result, indent=2)) + '</pre>'
        with (output / 'index.html').open('x', encoding='utf-8') as stream:
            stream.write(page)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--producer-run', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path, help='New directory, outside the retained run')
    parser.add_argument('--java', required=True, help='Absolute existing JDK java executable')
    parser.add_argument('--jacoco-classpath', help='Optional relocated existing jars; required jars are hash-pinned')
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    try:
        result = execute(args)
        print(result['status'] + ': POST_HOC_REANALYSIS; original producer remains BLOCKED')
        return {'PASS': 0, 'FAIL': 1, 'BLOCKED': 2}[result['status']]
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print('BLOCKED: ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
