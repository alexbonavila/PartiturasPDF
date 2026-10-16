"""L03 host policy/fault injection only. Synthetic inputs never establish application coverage."""
import copy
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET
from test_local_e2e import e2e
from test_local_regression import protocol as android_protocol
import local_coverage as coverage
import persistence_coverage as pc


class CoverageRunnerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.r = e2e.Runner(self.root / 'local', self.root / 'sdk', 'coverage')
        self.r.adb, self.r.serial = 'adb', 'emulator-5580'
        self.runtime = dict(vars(e2e), ROOT=self.root)
        self.c = coverage.CoverageRun(self.r, self.runtime)
        self.c.android_expected = {coverage.evidence.SMOKE}
        self.c.unchanged = Mock()
        self.r.identify = Mock()
        self.r.adb_command = Mock(return_value='instrumentation:' + coverage.evidence.APP + '.test/' +
                                     coverage.evidence.NORMAL_RUNNER + ' (target=' + coverage.evidence.APP + ')')
        self.calls = []

    def protocol(self, *, code=0, complete=True, coverage_path=True):
        output = android_protocol([(coverage.evidence.SMOKE, code)])
        if not complete:
            output = output.replace('INSTRUMENTATION_CODE: -1', '')
        if coverage_path:
            path = '/data/data/' + coverage.evidence.APP + '/files/local-coverage-' + self.r.run_id + '/coverage.ec'
            output = output.replace('INSTRUMENTATION_CODE: -1',
                                    'INSTRUMENTATION_RESULT: coverageFilePath=' + path + '\nINSTRUMENTATION_CODE: -1')
        return output

    def command(self, name, command, **kwargs):
        self.calls.append(list(command))
        self.r.record(name, self.output)
        return subprocess.CompletedProcess(command, self.exit, self.output, '')

    def android(self, output=None, exit=0, binary=b'NOT REAL COVERAGE: parser must reject this'):
        self.output, self.exit = output or self.protocol(), exit
        self.r.command = self.command
        self.runtime['execute'] = Mock(return_value=subprocess.CompletedProcess([], 0, binary, b''))
        return self.c.android()

    def test_coverage_dispatch_and_full_groups_unchanged(self):
        with patch('local_coverage.CoverageRun') as run:
            self.r.workflow()
            run.assert_called_once()
            self.assertEqual(['coverage'], [g['name'] for g in self.r.summary['groups']])
        self.r.suite = 'full'
        with patch.object(self.r, 'workflow_host'), patch.object(self.r, 'workflow_android'), patch.object(self.r, 'workflow_saf'):
            self.r.workflow()
        self.assertEqual(['host', 'android', 'saf'], [g['name'] for g in self.r.summary['groups']])

    def test_blocked_host_does_not_build_connect_or_delete_inputs(self):
        old = self.root / 'old.exec'; old.write_bytes(b'old')
        self.runtime['doctor'] = Mock(return_value=dict(status='BLOCKED', blockers=['no acceleration']))
        with patch.object(self.r, 'connect') as connect, patch.object(self.r, 'gradle') as build:
            with self.assertRaisesRegex(e2e.Blocked, 'no acceleration'):
                self.c.run()
            connect.assert_not_called(); build.assert_not_called()
        self.assertEqual(b'old', old.read_bytes())
        self.assertEqual('BLOCKED', json.loads((self.c.directory / 'summary.json').read_text())['status'])
        self.assertIsNotNone(ET.parse(self.c.directory / 'junit.xml').getroot().find('testcase/error'))

    def test_foreign_identity_stops_before_gradle(self):
        self.r.identify.side_effect = e2e.Blocked('Foreign AVD')
        with patch.object(self.r, 'gradle') as build:
            with self.assertRaisesRegex(e2e.Blocked, 'Foreign'):
                self.c.gradle('build.log', [':app:localCoverageConfiguration'])
            build.assert_not_called()

    def test_gradle_uses_normal_mode_explicit_serial_and_existing_scopes(self):
        with patch.object(self.r, 'gradle') as build:
            self.c.gradle('report.log', [':app:localPdfCoverageReport'])
        args, kwargs = build.call_args
        self.assertEqual('normal', kwargs['mode'])
        self.assertIn('-PlocalCoverageSerial=emulator-5580', args[2])
        self.assertIn('--no-configuration-cache', args[2])
        self.assertNotIn(':app:connectedDebugAndroidTest', args[1])

    def test_coverage_arguments_exact_serial_private_uuid_and_normal_runner(self):
        self.android()
        command = self.calls[0]
        self.assertEqual(['adb', '-s', 'emulator-5580', 'shell', 'am', 'instrument', '-w', '-r'], command[:8])
        self.assertIn('coverage', command); self.assertIn('true', command)
        self.assertIn('coverageFile', command)
        self.assertEqual(coverage.evidence.APP + '.test/' + coverage.evidence.NORMAL_RUNNER, command[-1])
        self.assertNotIn('class', command)
        self.assertEqual(['adb', '-s', 'emulator-5580', 'exec-out', 'run-as'], self.runtime['execute'].call_args.args[0][:5])
        self.assertEqual(1, self.r.summary['coverage']['android_tests'])
        self.assertTrue(self.c.remote_created)

    def test_missing_coverage_acknowledgement_blocks_even_passing_tests(self):
        with self.assertRaisesRegex(e2e.Blocked, 'acknowledge'):
            self.android(self.protocol(coverage_path=False))
        self.runtime['execute'].assert_not_called()

    def test_missing_binary_blocks_and_retains_empty_file(self):
        with self.assertRaisesRegex(e2e.Blocked, 'missing or unreadable'):
            self.android(binary=b'')
        self.assertEqual(b'', (self.c.directory / 'inputs/android/coverage.ec').read_bytes())

    def test_complete_assertion_is_fail_even_with_nonzero_adb(self):
        with self.assertRaises(e2e.FunctionalFailure):
            self.android(self.protocol(code=-2), exit=255)
        self.assertIsNotNone(ET.parse(self.c.directory / 'android-junit/junit.xml').getroot().find('testcase/failure'))
        self.assertEqual(1, len(self.calls))

    def test_incomplete_instrumentation_is_blocked_not_failure_or_retry(self):
        with self.assertRaises(e2e.Blocked):
            self.android(self.protocol(complete=False), exit=255)
        self.assertIsNotNone(ET.parse(self.c.directory / 'android-junit/junit.xml').getroot().find('testcase/error'))
        self.assertEqual(1, len(self.calls))

    def test_bad_process_exit_after_success_cannot_pass(self):
        with self.assertRaisesRegex(e2e.Blocked, 'nonzero ADB'):
            self.android(exit=255)
        self.runtime['execute'].assert_not_called()

    def test_ambiguous_instrumentation_runner_is_blocked_before_capture(self):
        self.r.adb_command.return_value = ('instrumentation:' + coverage.evidence.APP + '.test/' +
            coverage.evidence.NORMAL_RUNNER + ' (target=' + coverage.evidence.APP + ')\n') * 2
        with self.assertRaisesRegex(e2e.Blocked, 'ambiguous'):
            self.c.android()
        self.assertFalse(self.c.remote_created)

    def test_failed_install_is_not_retried_or_followed_by_instrumentation(self):
        self.r.adb_command.side_effect = e2e.Blocked('uncertain install')
        with self.assertRaisesRegex(e2e.Blocked, 'uncertain install'):
            self.c.android()
        self.assertEqual(1, self.r.adb_command.call_count)
        self.assertFalse(self.c.remote_created)

    def test_wrong_build_inventory_rejected(self):
        self.c.fingerprint = Mock(return_value={'changed': {}})
        self.c.frozen = {'captured': {}}
        self.c.unchanged = coverage.CoverageRun.unchanged.__get__(self.c)
        with self.assertRaisesRegex(e2e.Blocked, 'inventory changed'):
            self.c.unchanged()

    def test_wrong_apk_hash_rejected(self):
        path = self.root / 'app.apk'; path.write_bytes(b'new')
        self.c.frozen = {}; self.c.fingerprint = Mock(return_value={})
        self.c.apks = {str(path): 'previous-build'}
        self.c.unchanged = coverage.CoverageRun.unchanged.__get__(self.c)
        with self.assertRaisesRegex(e2e.Blocked, 'APK changed'):
            self.c.unchanged()

    def run_fixture(self, failure=None, cleanup_error=False, interrupted=False):
        self.c.prepare = Mock()
        self.c.jvm = Mock()
        self.c.android = Mock(side_effect=KeyboardInterrupt() if interrupted else failure)
        self.c.analyze = Mock()
        self.c.remote_created = True
        self.c.remote = 'files/local-coverage-' + self.r.run_id
        if interrupted:
            self.c.instrumentation_started = True
        if cleanup_error:
            self.r.adb_command.side_effect = e2e.Blocked('lost cleanup response')
        return self.c.run()

    def test_successful_cleanup_removes_only_acknowledged_uuid_output(self):
        self.run_fixture()
        self.r.adb_command.assert_called_once_with('shell', 'run-as', coverage.evidence.APP, 'rm', '-r', self.c.remote)
        self.assertEqual('PASS', self.c.result['status'])

    def test_cleanup_uncertainty_prevents_pass_and_keeps_evidence(self):
        with self.assertRaises(e2e.Blocked):
            self.run_fixture(cleanup_error=True)
        self.assertEqual('BLOCKED', self.c.result['status'])
        self.assertTrue((self.c.directory / 'index.html').is_file())
        self.assertEqual('BLOCKED', self.r.summary['cleanup'][0]['status'])

    def test_original_failure_and_cleanup_blocker_are_both_preserved(self):
        with self.assertRaises(e2e.FunctionalFailure):
            self.run_fixture(e2e.FunctionalFailure('assertion'), cleanup_error=True)
        report = json.loads((self.c.directory / 'summary.json').read_text())
        self.assertEqual('FAIL', report['status'])
        self.assertIn('assertion', report['failures'])
        self.assertTrue(any('cleanup' in b for b in report['blockers']))

    def test_interrupted_collection_force_stops_only_target_and_retains_reports(self):
        with self.assertRaises(KeyboardInterrupt):
            self.run_fixture(interrupted=True)
        self.assertEqual(('shell', 'am', 'force-stop', coverage.evidence.APP), self.r.adb_command.call_args_list[0].args)
        self.assertTrue((self.c.directory / 'summary.json').is_file())

    def test_retained_artifacts_have_hashes_and_wrapper_is_not_self_hashed(self):
        path = self.c.directory / 'raw.log'; path.write_text('original failure')
        self.c.save()
        report = json.loads((self.c.directory / 'summary.json').read_text())
        self.assertEqual(pc.digest(path), report['artifacts']['raw.log'])
        self.assertNotIn('summary.json', report['artifacts'])


class CoveragePolicyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        # macOS temporary paths can traverse /var -> /private/var. Use the canonical
        # fixture root; production deliberately rejects any unresolved symlink ancestor.
        self.root = Path(self.temp.name).resolve()

    def test_explicit_serial_is_backward_compatible_and_never_unqualified_locally(self):
        result = subprocess.CompletedProcess([], 0, '26', '')
        with patch.object(pc.subprocess, 'run', return_value=result) as command:
            self.assertEqual(26, pc.api('adb', 'emulator-5580'))
            self.assertEqual(['adb', '-s', 'emulator-5580', 'shell'], command.call_args.args[0][:4])
            pc.api('adb')
            self.assertEqual(['adb', 'shell'], command.call_args.args[0][:2])

    def test_api27_and_api36_cannot_satisfy_local_coverage(self):
        for api in ('27', '36'):
            with patch.object(pc.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, api, '')):
                with self.assertRaises(ValueError): pc.api('adb', 'emulator-5580')

    def test_local_paths_do_not_fabricate_connected_junit_or_change_ci_defaults(self):
        output = self.root / 'reports' / ('20261015T100000Z-' + 'a'*32) / 'coverage'
        with patch.object(pc, 'ROOT', self.root), patch.object(pc, 'OUTPUT'), patch.object(pc, 'ANDROID'), patch.object(pc, 'ANDROID_XML'):
            pc.configure_local(output, 'emulator-5580')
            self.assertEqual(output / 'android-junit', pc.ANDROID_XML)
            self.assertEqual(output / 'inputs/android', pc.ANDROID)
            self.assertNotIn('connected', str(pc.ANDROID_XML))

    def test_symlink_ancestor_is_still_rejected_with_canonical_fixture_root(self):
        alias = self.root / 'alias'
        output = alias / 'reports' / ('20261015T100000Z-' + 'a'*32) / 'coverage'
        original = Path.is_symlink
        # Simulate a symlink ancestor without requiring Windows symlink privileges.
        with patch.object(pc, 'ROOT', self.root), patch.object(Path, 'is_symlink', autospec=True,
                side_effect=lambda path: path == alias or original(path)):
            with self.assertRaisesRegex(ValueError, 'Unsafe local coverage'):
                pc.configure_local(output, 'emulator-5580')

    def test_unsafe_output_or_physical_serial_rejected(self):
        for directory, serial in [(self.root / 'coverage', 'emulator-5580'),
                                   (self.root / ('20261015T100000Z-' + 'a'*32) / 'coverage', 'physical')]:
            with patch.object(pc, 'ROOT', self.root):
                with self.assertRaises(ValueError): pc.configure_local(directory, serial)

    def test_junit_exact_inventory_rejects_missing_duplicate_and_skipped(self):
        path = self.root / 'junit.xml'
        path.write_text('<testsuite tests="1"><testcase classname="A" name="a"/></testsuite>')
        self.assertEqual(1, coverage.exact_junit(self.root, {'A.a'}))
        with self.assertRaises(ValueError): coverage.exact_junit(self.root, {'A.a', 'A.b'})
        path.write_text('<testsuite tests="2"><testcase classname="A" name="a"/><testcase classname="A" name="a"/></testsuite>')
        with self.assertRaises(ValueError): coverage.exact_junit(self.root, {'A.a'})
        path.write_text('<testsuite tests="1"><testcase classname="A" name="a"><skipped/></testcase></testsuite>')
        with self.assertRaises(ValueError): coverage.exact_junit(self.root, {'A.a'})

    def package(self, lines=90, branches=8):
        return dict(package={'LINE': dict(covered=lines, total=100), 'BRANCH': dict(covered=branches, total=10)}, sources={})

    def test_existing_exact_thresholds_pass_and_independent_scopes_fail(self):
        for name in ('contracts', 'pdf'):
            self.assertEqual([], coverage.component_gate(name, {'P': self.package()}))
            self.assertTrue(coverage.component_gate(name, {'P': self.package(lines=89)}))
            self.assertTrue(coverage.component_gate(name, {'P': self.package(branches=7)}))

    def test_informational_packages_do_not_create_unapproved_gates(self):
        packages = {'ui': self.package(lines=0, branches=0), 'contract': self.package()}
        self.assertEqual(45, coverage.aggregate(packages)['LINE']['percent'])
        # Only approved scoped selections are sent to component_gate.
        self.assertEqual([], coverage.component_gate('contracts', {'contract': packages['contract']}))

    def test_new_suite_reuses_binary_validator_not_file_hash_only(self):
        source = (Path(__file__).parents[1] / 'local-coverage.init.gradle').read_text()
        self.assertIn("dependsOn 'validatePersistenceCoverageInputs'", source)
        self.assertIn('project.tasks.pdfAdapterCoverageReport.classDirectories', source)
        self.assertIn("['metadata', 'preferences']", source)
        self.assertNotIn("dependsOn 'connectedDebugAndroidTest'", source)

class LocalReportValidationTests(unittest.TestCase):
    def setUp(self):
        import test_persistence_coverage as fixtures
        self.fixture = fixtures.CoveragePolicyTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.tearDown)

    def test_actual_compiled_source_model_required_in_general_report(self):
        path, classes = self.fixture.report_fixture({'Owned.kt': (90, 100, 8, 10)})
        packages = coverage.analyzed_report(path, classes)
        self.assertEqual(100, coverage.aggregate(packages)['LINE']['total'])
        self.assertEqual(90, coverage.aggregate(packages)['LINE']['covered'])
        self.assertEqual([], coverage.component_gate('pdf', packages))
        tree = ET.parse(path)
        tree.getroot().find('package/sourcefile/counter').set('covered', '99')
        tree.write(path)
        with self.assertRaises(ValueError): coverage.analyzed_report(path, classes)

    def test_general_report_rejects_missing_classes_packages_and_sources(self):
        for node in ('class', 'sourcefile', 'package'):
            path, classes = self.fixture.report_fixture({'Owned.kt': (90, 100, 8, 10)})
            tree = ET.parse(path)
            parent = tree.getroot() if node == 'package' else tree.getroot().find('package')
            parent.remove(parent.find(node)); tree.write(path)
            with self.assertRaises(ValueError): coverage.analyzed_report(path, classes)

    def test_combined_totals_are_weighted_counters_never_average_percentages(self):
        one = {'package': {'LINE': {'total': 100, 'covered': 10}, 'BRANCH': {'total': 0, 'covered': 0}}}
        two = {'package': {'LINE': {'total': 1, 'covered': 1}, 'BRANCH': {'total': 0, 'covered': 0}}}
        totals = coverage.aggregate({'a': one, 'b': two})
        self.assertEqual(11, totals['LINE']['covered'])
        self.assertEqual(101, totals['LINE']['total'])
        self.assertNotEqual(55, totals['LINE']['percent'])
        self.assertIsNone(totals['BRANCH']['percent'])

    def test_replayed_sessions_and_missing_records_still_rejected_locally(self):
        jvm, android, inputs = self.fixture.input_fixture()
        inputs['inputs'][1]['sessions'][0]['start'] = 1
        with self.assertRaisesRegex(ValueError, 'session'):
            pc.check_inputs({'started': self.fixture.started}, inputs, jvm, [android], self.fixture.started + 1000)
        _, _, inputs = self.fixture.input_fixture()
        inputs['inputs'][1]['records'] = {}
        with self.assertRaises(ValueError):
            pc.check_inputs({'started': self.fixture.started}, inputs, jvm, [android], self.fixture.started + 1000)

class CoverageApkAndClassificationTests(unittest.TestCase):
    def test_probe_runtime_markers_required_even_if_build_flag_enabled(self):
        import zipfile
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / 'app.apk'
            markers = [b'$jacocoInit', b'$jacocoData', b'Lcom/vladium/emma/rt/RT;', b'Lorg/jacoco/agent/rt/internal_']
            for missing in range(4):
                with zipfile.ZipFile(apk, 'w') as archive:
                    archive.writestr('classes.dex', b'\0'.join(m for i,m in enumerate(markers) if i != missing))
                with self.assertRaises(ValueError): coverage.evidence.inspect_coverage_apk(apk)
            with zipfile.ZipFile(apk, 'w') as archive: archive.writestr('classes.dex', b'\0'.join(markers))
            self.assertTrue(coverage.evidence.inspect_coverage_apk(apk)['probes'])

    def test_jvm_failure_requires_complete_cases_not_a_partial_assertion(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            path = directory / 'junit.xml'
            path.write_text('<testsuite tests="1" failures="1"><testcase classname="A" name="a"><failure/></testcase></testsuite>')
            self.assertTrue(coverage.complete_jvm_failure(directory, {'A.a'}))
            with self.assertRaises(ValueError): coverage.complete_jvm_failure(directory, {'A.a', 'A.b'})
            path.write_text('<testsuite tests="1" errors="1"><testcase classname="A" name="a"><error/></testcase></testsuite>')
            with self.assertRaises(ValueError): coverage.complete_jvm_failure(directory, {'A.a'})

    def test_parent_package_never_absorbs_nested_package_denominators(self):
        import test_persistence_coverage as fixtures
        fixture = fixtures.CoveragePolicyTests(); fixture.setUp()
        self.addCleanup(fixture.tearDown)
        path, classes = fixture.report_fixture({'Owned.kt': (90, 100, 8, 10)})
        tree = ET.parse(path); parent = tree.getroot().find('package')
        child = copy.deepcopy(parent); child.set('name', parent.get('name') + '/child')
        old = child.find('class').get('name'); new = child.get('name') + '/Nested'
        child.find('class').set('name', new)
        tree.getroot().append(child); tree.write(path)
        classes[new] = dict(classes[old])
        parsed = coverage.analyzed_report(path, classes)
        self.assertEqual(2, len(parsed))
        self.assertEqual(200, coverage.aggregate(parsed)['LINE']['total'])

class LocalFreshnessAndFinalizationTests(unittest.TestCase):
    def test_prepare_checks_all_destinations_before_deleting_any_evidence(self):
        from argparse import Namespace
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); build = root / 'app/build'; build.mkdir(parents=True)
            old = build / 'jvm.exec'; old.write_bytes(b'previous authentic evidence')
            args = Namespace(adb='adb', serial='emulator-5580', local_output=None)
            with patch.object(pc, 'BUILD', build), patch.object(pc, 'JVM', old), \
                    patch.object(pc, 'ANDROID', root / 'outside-build'), patch.object(pc, 'api', return_value=26), \
                    patch.object(pc, 'java', return_value={'class': {}}), patch.object(pc, 'current_build', return_value={}):
                with self.assertRaises(ValueError): pc.prepare(args)
            self.assertEqual(b'previous authentic evidence', old.read_bytes())

    def test_dirty_source_content_change_is_bound_even_when_status_string_unchanged(self):
        from argparse import Namespace
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); output = root / 'coverage'; output.mkdir()
            (output / 'capture.json').write_text('{}')
            source = root / 'app/src/main/java/example.kt'; source.parent.mkdir(parents=True)
            source.write_text('before')
            args = Namespace(local_output=output, gradle_version='9', agp_version='9', jdk_version='25', jacoco_version='0.8.14')
            with patch.object(pc, 'ROOT', root), patch.object(pc.subprocess, 'check_output', return_value='same status'):
                before = pc.current_build(args)
                source.write_text('after')
                after = pc.current_build(args)
            self.assertEqual(before['workingTree'], after['workingTree'])
            self.assertNotEqual(before['sourceHashes'], after['sourceHashes'])

    def test_outer_evidence_or_settings_cleanup_blocker_updates_coverage_report(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); runner = e2e.Runner(root, root / 'sdk', 'coverage')
            run = coverage.CoverageRun(runner, vars(e2e)); runner.coverage_run = run
            run.result['status'] = 'PASS'
            runner.summary.update(status='BLOCKED', detail='cleanup', groups=[dict(name='coverage', status='PASS')])
            runner.summary['cleanup'].append(dict(name='setting', status='BLOCKED', detail='unknown settings state'))
            runner.reports()
            result = json.loads((run.directory / 'summary.json').read_text())
            self.assertEqual('BLOCKED', result['status'])
            self.assertIn('unknown settings state', result['blockers'])
            self.assertIsNotNone(ET.parse(run.directory / 'junit.xml').getroot().find('testcase/error'))


class CaptureFailureBoundaryTests(unittest.TestCase):
    setUp = CoverageRunnerTests.setUp

    def test_incompatible_jvm_binary_cannot_be_a_threshold_fail(self):
        self.c.jvm_expected = {'A.a'}
        source = self.root / 'app/build/test-results/testDebugUnitTest'
        source.mkdir(parents=True)
        (source / 'TEST-A.xml').write_text('<testsuite tests="1"><testcase classname="A" name="a"/></testsuite>')
        self.c.gradle = Mock(return_value=subprocess.CompletedProcess([], 1, 'verifyContractCoverage failed', ''))
        self.c.validate_jvm = Mock(side_effect=ValueError('Class ID mismatch'))
        with self.assertRaisesRegex(ValueError, 'Class ID mismatch'):
            self.c.jvm()
        self.assertEqual([], self.c.result['failures'])

    def test_preparation_failure_cannot_retain_a_previous_jvm_exec_as_this_capture(self):
        source = self.root / 'app/build/jacoco/testDebugUnitTest.exec'
        source.parent.mkdir(parents=True); source.write_bytes(b'previous-run fixture, not real coverage')
        self.c.frozen = {}  # Inventoried classes, but not yet prepared a new capture.
        self.c.prepare = Mock(side_effect=e2e.Blocked('clock skew'))
        with self.assertRaises(e2e.Blocked): self.c.run()
        self.assertFalse((self.c.directory / 'inputs/jvm.exec').exists())
        self.assertEqual(b'previous-run fixture, not real coverage', source.read_bytes())

    def test_uncertain_uuid_mkdir_is_not_adopted_retried_or_deleted(self):
        self.c.prepare = Mock()
        self.c.jvm = Mock()
        self.c.android = Mock(side_effect=e2e.Blocked('lost mkdir response'))
        self.c.remote_pending = True
        self.c.remote = 'files/local-coverage-' + self.r.run_id
        with self.assertRaises(e2e.Blocked): self.c.run()
        self.r.adb_command.assert_not_called()
        self.assertEqual('BLOCKED', self.r.summary['cleanup'][0]['status'])



class CoverageResultPrecedenceTests(unittest.TestCase):
    setUp = CoverageRunnerTests.setUp

    def test_verified_scope_failure_survives_a_later_report_blocker(self):
        self.c.prepare = Mock(); self.c.jvm = Mock(); self.c.android = Mock()
        def analyze():
            self.c.result['failures'].append('Room/source.kt LINE: 8/10 below 90%')
            raise e2e.Blocked('later report could not be authenticated')
        self.c.analyze = analyze
        with self.assertRaises(e2e.FunctionalFailure): self.c.run()
        self.assertEqual('FAIL', self.c.result['status'])
        self.assertIn('later report could not be authenticated', self.c.result['blockers'])
        self.assertIsNotNone(ET.parse(self.c.directory / 'junit.xml').getroot().find('testcase/failure'))


if __name__ == "__main__":
    unittest.main()
