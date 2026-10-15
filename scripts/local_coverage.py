"""Opt-in orchestration around the existing C05 JaCoCo validator and Gradle reports.

Mocked policy tests cannot certify coverage. All real inputs must pass the Java reader.
"""
from __future__ import annotations
import html
import json
from pathlib import Path
import shutil
import sys
import time
import xml.etree.ElementTree as ET

import local_test_evidence as evidence
import persistence_coverage as pc


def file_inventory(directory):
    return {p.relative_to(directory).as_posix(): pc.digest(p) for p in sorted(directory.rglob('*')) if p.is_file()}


def analyzed_report(path, classes):
    """Reuse C05's independent compiled model/counter/line validator for every package."""
    root = ET.parse(path).getroot()
    expected = {name: c for name, c in classes.items() if c.get('reported', True)}
    packages = {name.rsplit('/', 1)[0] for name in expected}
    nodes = root.findall('package')
    if root.tag != 'report' or len(nodes) != len(packages) or {n.get('name') for n in nodes} != packages:
        raise ValueError('Unexpected/missing coverage report package inventory')
    measured = {}
    # A temporary XML projection retains the original per-package counters/lines verbatim.
    for package in nodes:
        projection = path.with_name(path.stem + '-package.xml')
        try:
            report = ET.Element('report', name='validated-package')
            report.append(package)
            ET.ElementTree(report).write(projection)
            measured[package.get('name')] = pc.report(projection, '', expected, package_prefix=package.get('name'))
            measured[package.get('name')].pop('path')
            measured[package.get('name')].pop('sha256')
        finally:
            projection.unlink(missing_ok=True)
    return measured


def aggregate(packages):
    result = {}
    for kind in ('LINE', 'BRANCH'):
        total = sum(p['package'][kind]['total'] for p in packages.values())
        covered = sum(p['package'][kind]['covered'] for p in packages.values())
        result[kind] = dict(covered=covered, missed=total-covered, total=total,
                            percent=100*covered/total if total else None)
    return result


def component_gate(name, packages):
    # A1 and B5 existing rules are aggregate scope rules; C05 also gates each source independently.
    return pc.thresholds({name: dict(package=aggregate(packages), sources={})})


def exact_junit(directory, expected):
    evidence_count = pc.verify_junit(directory)
    names = [case.get('classname') + '.' + case.get('name') for p in directory.rglob('*.xml')
             for case in ET.parse(p).getroot().iter('testcase')]
    if len(names) != len(set(names)) or set(names) != set(expected) or evidence_count != len(expected):
        raise ValueError('Missing/duplicate/unexpected executed JUnit test inventory')
    return evidence_count


def complete_jvm_failure(directory, expected):
    """Only completed, exact inventory assertion evidence is a functional failure."""
    names, failed = [], False
    for path in directory.rglob('*.xml'):
        root = ET.parse(path).getroot()
        if root.tag not in ('testsuite', 'testsuites'):
            raise ValueError('Invalid JVM evidence root')
        for suite in root.iter('testsuite'):
            if any(int(suite.get(key, '0')) for key in ('errors', 'skipped', 'disabled')):
                raise ValueError('Incomplete JVM evidence')
        for case in root.iter('testcase'):
            if case.find('error') is not None or case.find('skipped') is not None:
                raise ValueError('Incomplete JVM evidence')
            names.append(str(case.get('classname')) + '.' + str(case.get('name')))
            failed |= case.find('failure') is not None
    if len(names) != len(set(names)) or set(names) != set(expected):
        raise ValueError('Incomplete JVM test inventory')
    return failed


class CoverageRun:
    def __init__(self, runner, runtime):
        self.r = runner
        self.runtime = runtime
        self.root, self.Blocked, self.Failure = runtime['ROOT'], runtime['Blocked'], runtime['FunctionalFailure']
        self.directory = runner.out / 'coverage'
        self.directory.mkdir(exist_ok=True)
        self.result = dict(status='BLOCKED', jvm_tests=None, android_tests=None,
                           coverage='NOT RUN', jvm_status='NOT RUN', android_status='NOT RUN',
                           failures=[], blockers=[], components={}, informational={})
        runner.summary['coverage'] = self.result
        self.remote = None
        self.remote_created = False
        self.remote_pending = False
        self.coverage_read_attempted = False
        self.config = None
        self.instrumentation_started = False
        self.instrumentation_completed = False

    def gradle(self, log, tasks, *, tests=False):
        # Reuse Runner.gradle command/logging and normal APK mode, adding only our opt-in init script.
        self.r.identify(boot=True)
        return self.r.gradle('coverage/' + log, tasks, [
            '-I', str(self.root / 'scripts/local-coverage.init.gradle'), '--no-configuration-cache',
            '-PlocalCoverageOutput=' + str(self.directory),
            '-PlocalCoverageSerial=' + self.r.serial,
            '-PlocalCoveragePython=' + sys.executable,
            *(['--no-build-cache'] if tests else [])], mode='normal', instrumentation=tests)

    def fingerprint(self):
        return dict(production=pc.java(self.args, 'inventory'), tests=pc.java(self.args, 'test-inventory'))

    def unchanged(self):
        if self.fingerprint() != self.frozen:
            raise self.Blocked('Compiled production/test class inventory changed during capture')
        if hasattr(self, 'jvm_sha') and (
                pc.digest(self.root / 'app/build/jacoco/testDebugUnitTest.exec') != self.jvm_sha or
                pc.digest(self.directory / 'inputs/jvm.exec') != self.jvm_sha):
            raise self.Blocked('JVM execution input changed after capture')
        for path, sha in self.apks.items():
            if pc.digest(Path(path)) != sha:
                raise self.Blocked('Coverage APK changed during capture')
        if evidence.android_inventory(self.root / 'app/src/androidTest') != self.android_expected:
            raise self.Blocked('Android test source inventory changed during capture')

    def retain_classes(self):
        intermediates = self.root / 'app/build/intermediates'
        for variant, task in [('debug', 'Debug'), ('debugUnitTest', 'DebugUnitTest'), ('debugAndroidTest', 'DebugAndroidTest')]:
            for compiler, suffix in [('built_in_kotlinc', 'Kotlin'), ('javac', 'JavaWithJavac')]:
                source = intermediates / compiler / variant / ('compile' + task + suffix) / 'classes'
                if source.is_dir():
                    shutil.copytree(source, self.directory / 'classes' / compiler / variant, dirs_exist_ok=True)

    def save(self):
        self.result['artifacts'] = file_inventory(self.directory)
        # Avoid recursive/self-invalidating digests of report wrappers.
        for name in ('summary.json', 'junit.xml', 'index.html'):
            self.result['artifacts'].pop(name, None)
        pc.write_json(self.directory / 'summary.json', self.result)
        evidence.write_junit(self.directory / 'junit.xml', 'local-authentic-coverage', [
            dict(name='coverage', status=self.result['status'],
                 detail='; '.join(self.result['failures'] + self.result['blockers']))])
        links = ''.join('<li><a href="' + html.escape(name, quote=True) + '">' + html.escape(name) + '</a></li>'
                        for name in self.result['artifacts'] if name.endswith(('.html', '.xml', '.json', '.log')))
        (self.directory / 'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Local coverage</title><h1>' +
            self.result['status'] + '</h1><ul>' + links + '</ul><pre>' + html.escape(json.dumps(self.result, indent=2)) + '</pre>', encoding='utf-8')

    def prepare(self):
        # A failed host preflight does not build, provision, connect or delete previous evidence.
        host = self.runtime['doctor'](self.r.home, self.r.sdk)
        self.r.record('coverage/doctor.json', json.dumps(host, indent=2))
        self.result['host'] = host
        if host['status'] != 'PASS':
            raise self.Blocked('Coverage preflight blocked: ' + '; '.join(host['blockers']))
        if (not self.directory.resolve().is_relative_to(self.root.resolve()) or
                any(path.is_symlink() for path in [self.directory, *self.directory.parents])):
            raise self.Blocked('Coverage reports require a non-symlink home within this checkout; no AVD was touched')
        self.android_expected = evidence.android_inventory(self.root / 'app/src/androidTest')
        smoke = evidence.APP + '.ExampleUnitTest.addition_isCorrect'
        self.jvm_expected = evidence.android_inventory(self.root / 'app/src/test', smoke)
        # Debug JVM test helpers are intentionally part of the existing actual JVM suite.
        if (self.root / 'app/src/testDebug').exists():
            self.jvm_expected |= evidence.android_inventory(self.root / 'app/src/testDebug', None)
        if not self.r.identified:
            self.r.connect()
        else:
            self.r.identify(boot=True)
        self.gradle('build.log', [':app:localCoverageConfiguration', '--rerun-tasks', '--no-build-cache'])
        self.config = json.loads((self.directory / 'configuration.json').read_text())
        if self.config.get('androidCoverage') is not True:
            raise self.Blocked('Build did not certify Android JaCoCo instrumentation')
        from argparse import Namespace
        versions = self.config['versions']
        self.args = Namespace(jacoco_classpath=self.config['jacocoClasspath'],
            gradle_version=versions['gradle'], agp_version=versions['agp'], jdk_version=versions['jdk'],
            jacoco_version=versions['jacoco'], adb=str(self.r.adb), serial=self.r.serial, local_output=self.directory)
        self.apks = {}
        for relative in ('debug/app-debug.apk', 'androidTest/debug/app-debug-androidTest.apk'):
            source = self.root / 'app/build/outputs/apk' / relative
            target = self.directory / source.name
            shutil.copy2(source, target)
            self.apks[str(source)] = pc.digest(source)
            self.apks[str(target)] = pc.digest(target)
        self.result['android_instrumentation'] = evidence.inspect_coverage_apk(self.directory / 'app-debug.apk')
        self.result['apk_mode'] = self.r.verify_apk_mode(self.directory / 'app-debug-androidTest.apk', 'normal', self.android_expected)
        self.frozen = self.fingerprint()
        pc.write_json(self.directory / 'classes.json', self.frozen)
        self.retain_classes()
        # Clock skew would invalidate C05's real session freshness policy; never rewrite timestamps.
        device_seconds = int(self.r.adb_command('shell', 'date', '+%s'))
        if abs(time.time() - device_seconds) > 4:
            raise self.Blocked('Host/device clocks differ too much for authentic C05 session validation')
        capture = dict(run=self.r.run_id, commit=self.r.summary.get('commit'),
            working_tree=self.r.summary.get('working_tree'), host=host, device=self.r.summary['device'],
            versions=versions, apks=self.apks, jvm_inventory=sorted(self.jvm_expected),
            android_inventory=sorted(self.android_expected))
        pc.write_json(self.directory / 'capture.json', capture)
        # Existing C05 prepare is invoked only AFTER full ownership, boot and build checks.
        self.gradle('prepare.log', [':app:preparePersistenceCoverageRun'])
        self.unchanged()
        self.started = json.loads((self.directory / 'persistence/run.json').read_text())['started']
        # A previous A1 report cannot establish this run's finalizer outcome.
        contract = self.root / 'app/build/reports/tests/testDebugUnitTest'
        for path in (contract / 'contract-coverage.xml', contract / 'contract-coverage'):
            if path.is_symlink():
                raise self.Blocked('Contract report output must not be a symlink')
        (contract / 'contract-coverage.xml').unlink(missing_ok=True)
        if (contract / 'contract-coverage').exists():
            shutil.rmtree(contract / 'contract-coverage')

    def jvm(self):
        self.result['jvm_status'] = 'BLOCKED'
        result = self.gradle('jvm.log', [':app:testDebugUnitTest'], tests=True)
        source = self.root / 'app/build/test-results/testDebugUnitTest'
        target = self.directory / 'jvm-junit'
        shutil.copytree(source, target)
        if result.returncode:
            if complete_jvm_failure(target, self.jvm_expected):
                self.result['jvm_tests'] = len(self.jvm_expected)
                self.result['jvm_status'] = 'FAIL'
                raise self.Failure('Executed JVM assertion failure; inspect coverage/jvm-junit')
            self.result.update(jvm_tests=len(self.jvm_expected), jvm_status='PASS')
            self.validate_jvm()
            # A completed contract threshold violation is also a genuine FAIL, never a tool blocker.
            report = self.root / 'app/build/reports/tests/testDebugUnitTest/contract-coverage.xml'
            if report.is_file() and report.stat().st_mtime_ns // 1_000_000 >= self.started:
                classes_root = self.root / 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
                paths = {Path(p).resolve() for p in self.config['contractPatterns']}
                classes = {n: c for n, c in self.frozen['production'].items() if (classes_root / c['path']).resolve() in paths}
                failures = component_gate('contracts', analyzed_report(report, classes))
                if failures:
                    self.result['failures'].extend(failures)
                    self.result['jvm_tests'] = len(self.jvm_expected)
                    shutil.copy2(report, self.directory / 'contracts.xml')
                    raise self.Failure('; '.join(failures))
            raise self.Blocked('JVM/contract verification failed without complete trustworthy assertion evidence')
        self.result['jvm_tests'] = exact_junit(target, self.jvm_expected)
        if 'verifyContractCoverage' not in result.stdout + result.stderr:
            raise self.Blocked('Existing contract finalizer not evidenced')
        execution = self.root / 'app/build/jacoco/testDebugUnitTest.exec'
        if not execution.is_file() or not execution.stat().st_size:
            raise self.Blocked('Missing real JVM JaCoCo execution data')
        (self.directory / 'inputs').mkdir(exist_ok=True)
        shutil.copy2(execution, self.directory / 'inputs/jvm.exec')
        self.validate_jvm()
        self.jvm_sha = pc.digest(execution)
        contract = self.root / 'app/build/reports/tests/testDebugUnitTest'
        shutil.copy2(contract / 'contract-coverage.xml', self.directory / 'contracts.xml')
        shutil.copytree(contract / 'contract-coverage', self.directory / 'contracts.html')
        self.unchanged()
        self.result['jvm_status'] = 'PASS'

    def validate_jvm(self):
        execution = self.root / 'app/build/jacoco/testDebugUnitTest.exec'
        parsed = pc.java(self.args, 'validate', execution)
        if len(parsed['inputs']) != 1 or Path(parsed['inputs'][0]['path']) != execution:
            raise self.Blocked('JVM binary input inventory mismatch')
        pc.check_execution({'started': self.started}, parsed['inputs'][0], int(time.time() * 1000))
        self.result['jvm_input'] = dict(parsed['inputs'][0], sha256=pc.digest(execution), retained='inputs/jvm.exec')
        self.unchanged()

    def android(self):
        self.result['android_status'] = 'BLOCKED'
        for name in ('app-debug.apk', 'app-debug-androidTest.apk'):
            self.r.adb_command('install', '-r', str(self.directory / name), timeout=120)
        component = evidence.APP + '.test/' + evidence.NORMAL_RUNNER
        registered = self.r.adb_command('shell', 'pm', 'list', 'instrumentation')
        import re
        matches = re.findall(r'instrumentation:(\S+) \(target=' + re.escape(evidence.APP) + r'\)', registered)
        if matches != [component]:
            raise self.Blocked('Coverage instrumentation identity ambiguous or incorrect')
        self.r.adb_command('shell', 'run-as', evidence.APP, 'mkdir', '-p', 'files')
        self.remote = 'files/local-coverage-' + self.r.run_id
        self.remote_pending = True
        self.r.adb_command('shell', 'run-as', evidence.APP, 'mkdir', self.remote)
        self.remote_pending = False
        self.remote_created = True  # No -p: refuse an existing directory instead of adopting it.
        coverage_path = '/data/data/' + evidence.APP + '/' + self.remote + '/coverage.ec'
        self.r.identify(boot=True)
        command = [self.r.adb, '-s', self.r.serial, 'shell', 'am', 'instrument', '-w', '-r',
                   '-e', 'coverage', 'true', '-e', 'coverageFile', coverage_path, component]
        cases, blocker = [], None
        directory = self.directory / 'android-junit'
        directory.mkdir(exist_ok=True)
        try:
            self.instrumentation_started = True
            process = self.r.command('coverage/instrumentation.log', command, timeout=1200)
            cases = evidence.parse_android(process.stdout + '\n' + process.stderr, self.android_expected)
            self.instrumentation_completed = True
            self.result['android_tests'] = len(cases)
            if any(c['status'] == 'FAIL' for c in cases):
                self.result['android_status'] = 'FAIL'
                raise self.Failure('Executed Android assertion failed; inspect coverage/android-junit')
            if process.returncode:
                raise self.Blocked('Complete Android output but nonzero ADB exit')
            # The runner's coverage report must name OUR requested file, not an old default.
            if not any(line.strip() == 'INSTRUMENTATION_RESULT: coverageFilePath=' + coverage_path
                       for line in process.stdout.splitlines()):
                raise self.Blocked('AndroidJUnitRunner did not acknowledge the exact coverage output')
        except evidence.IncompleteInstrumentation as error:
            cases, blocker = error.cases, str(error)
            self.result['android_tests'] = len(cases)
            raise self.Blocked(blocker) from error
        except self.Blocked as error:
            blocker = str(error)
            raise
        finally:
            evidence.write_junit(directory / 'junit.xml', 'coverage-ordinary-android', cases, blocker)
        exact_junit(directory, self.android_expected)
        self.result['android_status'] = 'PASS'
        self.retrieve_android()
        self.unchanged()

    def retrieve_android(self):
        self.coverage_read_attempted = True
        self.r.identify(boot=True)
        # Binary exec-out, never text decoding or a broad device pull. Exactly one read.
        process = self.runtime['execute']([self.r.adb, '-s', self.r.serial, 'exec-out', 'run-as',
            evidence.APP, 'cat', self.remote + '/coverage.ec'], binary=True, timeout=60)
        path = self.directory / 'inputs/android/coverage.ec'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(process.stdout)  # Preserve corrupt/incomplete data for diagnosis too.
        self.result['android_capture'] = dict(command=[str(x) for x in process.args],
            exit=process.returncode, bytes=len(process.stdout), sha256=pc.digest(path),
            stderr=process.stderr.decode('utf-8', 'replace'))
        if process.returncode or not process.stdout:
            raise self.Blocked('Real Android coverage file missing or unreadable')

    def analyze(self):
        # C05's real binary validator, same-build inventories, session and JUnit policy run first.
        self.gradle('reports.log', [':app:metadataPersistenceCoverageReport',
            ':app:preferencesPersistenceCoverageReport', ':app:localPdfCoverageReport',
            ':app:localProductionCoverageReport'])
        self.unchanged()
        # Verify C05 separately so its detailed FAIL provenance survives without hiding other scopes.
        process = self.gradle('verify-persistence.log', [':app:verifyPersistenceCoverage'], tests=True)
        provenance = json.loads((self.directory / 'persistence/provenance.json').read_text())
        if provenance.get('result') not in ('PASS', 'FAIL'):
            raise self.Blocked('Incomplete C05 persistence verification')
        if process.returncode and provenance['result'] != 'FAIL':
            raise self.Blocked('Persistence verification exited unexpectedly')
        self.result['failures'].extend(provenance['failures'])
        self.result['components'].update(provenance['coverage'])
        all_classes = self.frozen['production']
        handwritten = {name: c for name, c in all_classes.items() if c.get('production')}
        source_inventory = analyzed_report(self.directory / 'production.xml', handwritten)
        for package, counters in source_inventory.items():
            suffix = package.removeprefix('com/abdev/partituraspdf/')
            scope = ('A1' if suffix.startswith(('core/', 'storage/api', 'pdf/api')) else
                     'B5' if suffix == 'pdf/android' else
                     'C05' if suffix in ('data/metadata', 'data/preferences') else 'INFORMATIONAL')
            counters['classification'] = scope
        self.result['source_inventory'] = source_inventory
        self.result['informational'] = {n: c for n, c in source_inventory.items() if c['classification'] == 'INFORMATIONAL'}
        for scope, config, xml in [('contracts', 'contractPatterns', 'contracts.xml'), ('pdf', 'pdfPatterns', 'pdf.xml')]:
            paths = {Path(path).resolve() for path in self.config[config]}
            class_root = self.root / 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
            classes = {n: c for n, c in all_classes.items() if (class_root / c['path']).resolve() in paths}
            if not classes:
                raise self.Blocked('Empty existing coverage scope: ' + scope)
            packages = analyzed_report(self.directory / xml, classes)
            counters = aggregate(packages)
            self.result['components'][scope] = dict(package=counters, packages=packages)
            self.result['failures'].extend(component_gate(scope, packages))
        self.result['coverage'] = 'VERIFIED'
        self.result['inputs'] = provenance['inputs']
        if self.result['failures']:
            raise self.Failure('; '.join(self.result['failures']))

    def run(self):
        try:
            self.prepare()
            self.jvm()
            self.android()
            self.analyze()
            self.result['status'] = 'PASS'
        except self.Failure as error:
            self.result['status'] = 'FAIL'
            if str(error) not in self.result['failures']:
                self.result['failures'].append(str(error))
            raise
        except BaseException as error:
            self.result['blockers'].append(str(error))
            if self.result['failures']:
                self.result['status'] = 'FAIL'
                raise self.Failure('; '.join(self.result['failures'])) from error
            raise
        finally:
            if hasattr(self, 'started'):
                execution = self.root / 'app/build/jacoco/testDebugUnitTest.exec'
                if execution.is_file() and not (self.directory / 'inputs/jvm.exec').exists():
                    (self.directory / 'inputs').mkdir(exist_ok=True)
                    shutil.copy2(execution, self.directory / 'inputs/jvm.exec')
            if self.instrumentation_completed and not self.coverage_read_attempted:
                try:
                    self.retrieve_android()  # Preserve a completed failed test's real dump too; never replay capture.
                except Exception as error:
                    self.result['blockers'].append('Android coverage retention: ' + str(error))
            if self.remote_pending:
                self.r.summary['cleanup'].append(dict(name='owned coverage directory creation', status='BLOCKED',
                    detail='Creation reply uncertain; refusing to adopt, retry or delete the UUID directory'))
            self.save()  # Before cleanup; complete/partial binary inputs and logs are retained.
            if self.remote_created:
                try:
                    if self.instrumentation_started and not self.instrumentation_completed:
                        self.r.adb_command('shell', 'am', 'force-stop', evidence.APP)
                    self.r.adb_command('shell', 'run-as', evidence.APP, 'rm', '-r', self.remote)
                    self.r.summary['cleanup'].append(dict(name='owned coverage output', status='PASS'))
                except Exception as error:
                    self.result['blockers'].append('Owned coverage cleanup: ' + str(error))
                    self.r.summary['cleanup'].append(dict(name='owned coverage output', status='BLOCKED', detail=str(error)))
                    if self.result['status'] == 'PASS':
                        self.result['status'] = 'BLOCKED'
                        self.save()
                        raise self.Blocked('Owned coverage cleanup failed') from error
            self.save()
