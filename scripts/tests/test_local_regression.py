"""Local orchestration contracts. Device/subprocess responses are simulated here."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET
import zipfile
from test_local_e2e import e2e
import local_test_evidence as evidence

SMOKE = evidence.SMOKE
OTHER = e2e.APP + '.storage.Test.example'
# Fixed, whole-second epochs a minute apart: no dependency on wall-clock advancement.
JVM_XML_OLD_NS = 1_600_000_000_000_000_000
JVM_XML_FRESH_NS = JVM_XML_OLD_NS + 60_000_000_000


def protocol(statuses=None, complete=True):
    statuses = statuses or [(SMOKE, 0), (OTHER, 0)]
    lines = []
    for identity, code in statuses:
        cls, method = identity.rsplit('.', 1)
        bundle = f'INSTRUMENTATION_STATUS: class={cls}\nINSTRUMENTATION_STATUS: test={method}\nINSTRUMENTATION_STATUS: numtests={len(statuses)}\n'
        lines += [bundle + 'INSTRUMENTATION_STATUS_CODE: 1\n', bundle +
                  ('INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: actual failure\n' if code < 0 else '') +
                  f'INSTRUMENTATION_STATUS_CODE: {code}\n']
    return ''.join(lines) + ('INSTRUMENTATION_CODE: -1\n' if complete else '')


def manifest(runner=evidence.NORMAL_RUNNER, target=e2e.APP):
    return f'''E: manifest
  A: package="{e2e.APP}.test"
  E: instrumentation
    A: android:name(0x01010003)="{runner}"
    A: android:targetPackage(0x01010021)="{target}"
  E: application
'''


def test_apk(path, saf=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    data = b''
    for identity in (SMOKE, OTHER):
        cls, method = identity.rsplit('.', 1)
        data += ('L' + cls.replace('.', '/') + ';').encode() + b'\0' + method.encode() + b'\0'
    if saf:
        data += ('L' + e2e.CLASS.replace('.', '/') + ';').encode()
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('classes.dex', data)


class EvidenceTests(unittest.TestCase):
    def test_all_expected_tests_and_smoke_execute(self):
        cases = evidence.parse_android(protocol(), {SMOKE, OTHER})
        self.assertEqual(len(cases), 2)
        self.assertTrue(all(c['status'] == 'PASS' for c in cases))

    def test_actual_assertion_failure_retains_failure_detail(self):
        cases = evidence.parse_android(protocol([(SMOKE, 0), (OTHER, -2)]), {SMOKE, OTHER})
        self.assertEqual(cases[-1]['status'], 'FAIL')
        self.assertIn('actual failure', cases[-1]['detail'])

    def test_zero_incomplete_skipped_crashed_and_missing_smoke_block(self):
        for output in ('INSTRUMENTATION_CODE: -1\n', protocol(complete=False),
                       protocol([(SMOKE, -3), (OTHER, 0)]),
                       protocol() + 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n',
                       protocol([(OTHER, 0)])):
            with self.subTest(output=output), self.assertRaises(evidence.IncompleteInstrumentation):
                evidence.parse_android(output, {SMOKE, OTHER})

    def test_duplicate_unexpected_out_of_order_and_inconsistent_counts_block(self):
        valid = protocol()
        for output in (valid + valid, protocol([(SMOKE, 0), (e2e.CLASS + '.acquire', 0)]),
                       valid.replace('numtests=2', 'numtests=3', 1),
                       'INSTRUMENTATION_CODE: -1\n' + valid.removesuffix('INSTRUMENTATION_CODE: -1\n'),
                       valid.replace('INSTRUMENTATION_STATUS_CODE: 1', 'INSTRUMENTATION_STATUS_CODE: 0', 1),
                       valid.replace('numtests=2', 'numtests=invalid')):
            with self.subTest(output=output), self.assertRaises(evidence.IncompleteInstrumentation):
                evidence.parse_android(output, {SMOKE, OTHER})

    def test_incomplete_failure_keeps_partial_evidence_but_cannot_be_a_complete_fail(self):
        with self.assertRaises(evidence.IncompleteInstrumentation) as error:
            evidence.parse_android(protocol([(SMOKE, -2), (OTHER, 0)], complete=False), {SMOKE, OTHER})
        self.assertEqual(error.exception.cases[0]['status'], 'FAIL')

    def test_apk_manifest_runner_target_and_modes_are_verified(self):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'test.apk'
            test_apk(apk)
            self.assertFalse(evidence.inspect_apk(apk, manifest(), 'normal', {SMOKE, OTHER})['opt_in_saf'])
            for dump in (manifest('com.abdev.partituraspdf.data.backup.BackupPolicyAcceptanceRunner'),
                         manifest(target='foreign'), manifest().replace('.test"', '.foreign"'),
                         manifest() + '  E: instrumentation\n'):
                with self.assertRaises(ValueError):
                    evidence.inspect_apk(apk, dump, 'normal')
            with self.assertRaises(ValueError):
                evidence.inspect_apk(apk, manifest(), 'saf')
            test_apk(apk, saf=True)
            self.assertTrue(evidence.inspect_apk(apk, manifest(), 'saf')['opt_in_saf'])
            with self.assertRaises(ValueError):
                evidence.inspect_apk(apk, manifest(), 'normal')

    def test_missing_required_class_or_method_blocks_apk_selection(self):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'test.apk'
            test_apk(apk)
            for required in ({e2e.APP + '.Absent.test'}, {SMOKE.rsplit('.', 1)[0] + '.absentMethod'}):
                with self.assertRaisesRegex(ValueError, 'missing from APK'):
                    evidence.inspect_apk(apk, manifest(), 'normal', required)

    def test_inventory_includes_existing_ordinary_tests_not_manual_runners(self):
        inventory = evidence.android_inventory(e2e.ROOT / 'app/src/androidTest')
        self.assertIn(SMOKE, inventory)
        self.assertGreater(len(inventory), 200)
        self.assertFalse(any('BackupPolicyAcceptanceRunner' in n or 'SafLocalE2eTest' in n for n in inventory))
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'Unknown.kt'
            path.write_text('package sample\n@Test @Unknown fun unsupported() {}')
            with self.assertRaises(ValueError):
                evidence.android_inventory(temporary)


class OrchestrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.runner = e2e.Runner(self.root / 'local', self.root / 'sdk')

    def test_cli_dispatch_preserves_saf_and_injection_and_adds_new_suites(self):
        for suite in ('saf', 'host', 'android', 'full', 'reader', 'library', 'coverage'):
            with patch.object(e2e, 'Runner') as runner, patch.object(e2e, 'discover_sdk', return_value=self.root):
                runner.return_value.run.return_value = 2
                self.assertEqual(e2e.main(['run', '--suite', suite]), 2)
                self.assertEqual(runner.call_args.args[2], suite)
        with patch.object(e2e, 'Runner') as runner:
            runner.return_value.run.return_value = 1
            self.assertEqual(e2e.main(['run', '--suite', 'saf', '--inject-failure']), 1)
            self.assertTrue(runner.call_args.args[3])
        with self.assertRaises(SystemExit):
            e2e.main(['run', '--suite', 'host', '--inject-failure'])

    def test_unimplemented_placeholders_remain_blocked_without_build_or_device(self):
        for suite in ('reader', 'library', 'coverage'):
            self.runner.suite = suite
            with patch.object(self.runner, 'connect') as connect, patch.object(self.runner, 'gradle') as build:
                with self.assertRaises(e2e.Blocked):
                    self.runner.workflow()
                connect.assert_not_called()
                build.assert_not_called()

    def host_command(self, name, command, **kwargs):
        if name == 'host/jvm.log':
            path = self.root / 'app/build/test-results/testDebugUnitTest/TEST-one.xml'
            if path.exists():
                os.utime(path, ns=(JVM_XML_FRESH_NS, JVM_XML_FRESH_NS))
        log = '> Task :app:verifyContractCoverage\n' if name == 'host/jvm.log' else (
            'Ran 2 tests in 0.1s\n\nOK\n' if name == 'host/python.log' else 'PASS')
        self.runner.record(name, log)
        return subprocess.CompletedProcess(command, 0, log, '')

    def prepare_jvm(self):
        path = self.root / 'app/build/test-results/testDebugUnitTest/TEST-one.xml'
        path.parent.mkdir(parents=True)
        path.write_text('<testsuite><testcase classname="sample" name="actual"/></testsuite>')
        os.utime(path, ns=(JVM_XML_OLD_NS, JVM_XML_OLD_NS))

    def test_host_suite_requires_no_device_and_retains_actual_jvm_xml(self):
        self.prepare_jvm()
        self.runner.suite = 'host'
        with patch.object(e2e, 'ROOT', self.root), patch.object(self.runner, 'command', side_effect=self.host_command), \
             patch.object(self.runner, 'connect') as connect, patch.object(e2e, 'doctor') as doctor:
            self.runner.workflow()
        connect.assert_not_called()
        doctor.assert_not_called()
        self.assertEqual(self.runner.summary['groups'][0]['status'], 'PASS')
        self.assertEqual(self.runner.summary['groups'][0]['tests'], 3)
        self.assertTrue((self.runner.out / 'host/jvm-xml/TEST-one.xml').exists())
        self.assertEqual(self.runner.summary['backup_acceptance'], 'NOT RUN')

    def test_fresh_jvm_fixture_survives_coarse_clock_and_nonadvancing_touch(self):
        real_utime = os.utime
        def coarse_utime(path, *, ns, **kwargs):
            quantum = 2_000_000_000  # Simulate two-second filesystem timestamp resolution.
            real_utime(path, ns=tuple(value // quantum * quantum for value in ns), **kwargs)
        with patch.object(Path, 'touch', return_value=None), patch.object(os, 'utime', side_effect=coarse_utime):
            self.prepare_jvm()
            path = self.root / 'app/build/test-results/testDebugUnitTest/TEST-one.xml'
            before = path.stat().st_mtime_ns
            with patch.object(e2e, 'ROOT', self.root), patch.object(self.runner, 'command', side_effect=self.host_command):
                self.runner.workflow_host()
            self.assertNotEqual(path.stat().st_mtime_ns, before)
        self.assertEqual(self.runner.summary['host_checks'][0]['status'], 'PASS')
        self.assertEqual(self.runner.summary['host_checks'][0]['tests'], 1)
        self.assertEqual(e2e.verify_junit(self.runner.out / 'host/jvm-xml'), 1)

    def test_nonadvancing_touch_cannot_promote_stale_jvm_xml_to_fresh(self):
        self.prepare_jvm()
        path = self.root / 'app/build/test-results/testDebugUnitTest/TEST-one.xml'
        before = path.stat().st_mtime_ns
        def unchanged(name, command, **kwargs):
            if name == 'host/jvm.log':
                path.touch()  # Simulated timestamp collision, not evidence of a new report.
                return subprocess.CompletedProcess(command, 0, '> Task :app:verifyContractCoverage', '')
            return self.host_command(name, command, **kwargs)
        with patch.object(Path, 'touch', return_value=None), patch.object(e2e, 'ROOT', self.root), \
             patch.object(self.runner, 'command', side_effect=unchanged):
            with self.assertRaises(e2e.Blocked):
                self.runner.workflow_host()
        self.assertEqual(path.stat().st_mtime_ns, before)
        self.assertEqual(self.runner.summary['host_checks'][0]['status'], 'BLOCKED')
        self.assertTrue(all(c['status'] == 'PASS' for c in self.runner.summary['host_checks'][1:]))
        self.assertEqual(list((self.runner.out / 'host/jvm-xml').glob('*.xml')), [])

    def test_missing_host_dependency_is_blocked_and_other_checks_still_execute(self):
        self.prepare_jvm()
        def command(name, argv, **kwargs):
            if name == 'host/fixture-dependencies.log':
                return subprocess.CompletedProcess(argv, 1, '', 'ModuleNotFoundError: pypdf')
            return self.host_command(name, argv)
        with patch.object(e2e, 'ROOT', self.root), patch.object(self.runner, 'command', side_effect=command):
            with self.assertRaises(e2e.Blocked):
                self.runner.workflow_host()
        checks = self.runner.summary['host_checks']
        self.assertEqual(next(c for c in checks if c['name'] == 'fixture-dependencies')['status'], 'BLOCKED')
        self.assertEqual(checks[-1]['name'], 'fixtures')
        self.assertEqual(checks[-1]['status'], 'NOT RUN')

    def test_missing_jvm_or_zero_python_evidence_is_not_pass(self):
        with patch.object(e2e, 'ROOT', self.root), patch.object(self.runner, 'command', side_effect=self.host_command):
            with self.assertRaises(e2e.Blocked):
                self.runner.workflow_host()
        self.assertEqual(self.runner.summary['host_checks'][0]['status'], 'BLOCKED')
        self.prepare_jvm()
        def command(name, argv, **kwargs):
            if name == 'host/python.log':
                return subprocess.CompletedProcess(argv, 0, 'Ran 0 tests in 0s\nOK', '')
            return self.host_command(name, argv)
        with patch.object(e2e, 'ROOT', self.root), patch.object(self.runner, 'command', side_effect=command):
            with self.assertRaises(e2e.Blocked):
                self.runner.workflow_host()

    def test_stale_jvm_xml_cannot_satisfy_a_new_run(self):
        self.prepare_jvm()
        def stale(name, command, **kwargs):
            return subprocess.CompletedProcess(command, 0, '> Task :app:verifyContractCoverage\nRan 2 tests in 0s\nOK', '')
        with patch.object(e2e, 'ROOT', self.root), patch.object(self.runner, 'command', side_effect=stale):
            with self.assertRaises(e2e.Blocked):
                self.runner.workflow_host()
        self.assertEqual(self.runner.summary['host_checks'][0]['status'], 'BLOCKED')
        self.assertEqual(list((self.runner.out / 'host/jvm-xml').glob('*.xml')), [])

    def test_host_validator_failure_identifies_underlying_check(self):
        self.prepare_jvm()
        def command(name, argv, **kwargs):
            if name == 'host/localization.log':
                return subprocess.CompletedProcess(argv, 1, 'FAIL missing translation', '')
            return self.host_command(name, argv)
        with patch.object(e2e, 'ROOT', self.root), patch.object(self.runner, 'command', side_effect=command):
            with self.assertRaisesRegex(e2e.FunctionalFailure, 'localization'):
                self.runner.workflow_host()

    def test_full_aggregates_independent_failures_without_backup_or_early_pass(self):
        self.runner.suite = 'full'
        events = []
        def failed_android():
            self.runner.identified = True
            events.append('android')
            raise e2e.FunctionalFailure('actual Android assertion')
        def saf():
            events.append('saf')
            self.runner.summary['tests'] = [{'name': n, 'status': 'PASS'} for n in e2e.STAGES]
            self.runner.summary['status'] = 'PASS'  # Legacy SAF result cannot mask other groups.
        with patch.object(self.runner, 'workflow_host', side_effect=lambda: events.append('host')), \
             patch.object(self.runner, 'workflow_android', side_effect=failed_android), \
             patch.object(self.runner, 'workflow_saf', side_effect=saf), \
             patch.object(self.runner, 'collect', side_effect=lambda prefix: events.append(prefix)), \
             patch.object(self.runner, 'workflow_backup_preflight') as backup:
            with self.assertRaises(e2e.FunctionalFailure):
                self.runner.workflow()
        self.assertEqual(events, ['host', 'android', 'android-', 'saf'])
        self.assertEqual([g['status'] for g in self.runner.summary['groups']], ['PASS', 'FAIL', 'PASS'])
        self.assertEqual(json.loads((self.runner.out / 'summary.json').read_text())['status'], 'FAIL')
        backup.assert_not_called()

    def test_full_blocked_group_does_not_prevent_independent_remaining_groups(self):
        self.runner.suite = 'full'
        with patch.object(self.runner, 'workflow_host', side_effect=e2e.Blocked('missing JDK')), \
             patch.object(self.runner, 'workflow_android') as android, patch.object(self.runner, 'workflow_saf') as saf:
            with self.assertRaises(e2e.Blocked):
                self.runner.workflow()
        android.assert_called_once()
        saf.assert_called_once()
        self.assertEqual(self.runner.summary['groups'][0]['status'], 'BLOCKED')

    def test_cleanup_failure_is_recorded_without_overwriting_group_evidence(self):
        self.runner.suite = 'host'
        self.runner.summary.update(groups=[{'name': 'host', 'status': 'PASS', 'tests': 1}], status='PASS')
        self.runner.summary['cleanup'].append({'name': 'setting', 'status': 'BLOCKED', 'detail': 'offline'})
        self.runner.group_reports()
        self.assertEqual(self.runner.summary['status'], 'BLOCKED')
        self.assertEqual(self.runner.summary['groups'][0]['status'], 'PASS')
        self.assertEqual(ET.parse(self.runner.out / 'junit.xml').getroot().get('errors'), '1')

    def test_blocked_saf_stage_cannot_be_promoted_to_pass_by_four_result_records(self):
        self.runner.summary.update(groups=[{'name': 'saf', 'status': 'BLOCKED', 'tests': 3}],
            tests=[{'name': n, 'status': 'BLOCKED' if i == 3 else 'PASS'} for i, n in enumerate(e2e.STAGES)])
        self.runner.group_reports()
        self.assertEqual(self.runner.summary['saf_e2e'], 'INCOMPLETE')
        self.assertEqual(self.runner.summary['status'], 'BLOCKED')
        self.assertEqual(ET.parse(self.runner.out / 'saf-junit.xml').getroot().get('errors'), '2')

    def test_gradle_normal_and_saf_modes_disable_incompatible_apk_cache_reuse(self):
        for mode in ('normal', 'saf'):
            with patch.object(e2e, 'execute', return_value=subprocess.CompletedProcess([], 0, 'BUILD SUCCESSFUL', '')) as execute:
                self.runner.gradle(mode + '.log', [':app:assembleDebugAndroidTest'], mode=mode)
            command = execute.call_args.args[0]
            self.assertEqual('-I' in command, mode == 'saf')
            self.assertIn('--rerun-tasks', command)
            self.assertIn('--no-build-cache', command)
            self.assertFalse(any('backup-acceptance.init' in str(c) for c in command))

class AndroidWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.runner = e2e.Runner(self.root / 'local', self.root / 'sdk', 'android')
        self.runner.adb, self.runner.serial = 'adb', 'emulator-5580'
        self.output, self.exit = protocol(), 0
        self.runner_component = evidence.NORMAL_RUNNER
        test_apk(self.root / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
        app = self.root / 'app/build/outputs/apk/debug/app-debug.apk'
        app.parent.mkdir(parents=True)
        app.write_bytes(b'app APK fixture')
        self.commands = []

    def command(self, name, command, **kwargs):
        self.commands.append(command)
        output = manifest() if name.endswith('.manifest.txt') else self.output
        self.runner.record(name, output)
        return subprocess.CompletedProcess(command, 0 if name.endswith('.manifest.txt') else self.exit, output, '')

    def android(self, expected_runner=None):
        def adb(*args, **kwargs):
            if args[:4] == ('shell', 'pm', 'list', 'instrumentation'):
                return f'instrumentation:{e2e.APP}.test/{self.runner_component} (target={e2e.APP})'
            return 'Success'
        def connect():
            self.runner.identified = True
        with patch.object(e2e, 'ROOT', self.root), patch.object(e2e, 'doctor', return_value={'status': 'PASS'}), \
             patch.object(e2e.evidence, 'android_inventory', return_value={SMOKE, OTHER}), \
             patch.object(e2e, 'sdk_tool', return_value='aapt2'), patch.object(self.runner, 'gradle') as build, \
             patch.object(self.runner, 'connect', side_effect=connect), patch.object(self.runner, 'identify'), \
             patch.object(self.runner, 'adb_command', side_effect=adb), \
             patch.object(self.runner, 'command', side_effect=self.command):
            try:
                self.runner.workflow_android()
            finally:
                self.build_calls = build.call_args_list

    def test_entire_normal_suite_uses_one_explicit_serial_and_normal_runner(self):
        self.android()
        command = self.commands[-1]
        self.assertEqual(command, ['adb', '-s', 'emulator-5580', 'shell', 'am', 'instrument', '-w', '-r',
                                   e2e.APP + '.test/' + evidence.NORMAL_RUNNER])
        self.assertEqual(self.build_calls[0].kwargs['mode'], 'normal')
        self.assertEqual(len(self.runner.summary['android_cases']), 2)
        self.assertFalse(self.runner.installed)  # Never run SAF grant cleanup for ordinary tests.
        self.assertEqual(e2e.verify_junit(self.runner.out / 'android', required_test=SMOKE), 2)
        self.assertTrue((self.runner.out / 'android/instrumentation.log').exists())

    def test_completed_android_assertion_is_fail_even_when_adb_exits_nonzero(self):
        self.output, self.exit = protocol([(SMOKE, 0), (OTHER, -2)]), 255
        with self.assertRaises(e2e.FunctionalFailure):
            self.android()
        self.assertEqual(ET.parse(self.runner.out / 'android/junit.xml').getroot().get('failures'), '1')
        self.assertEqual(len([c for c in self.commands if 'instrument' in c]), 1)

    def test_success_protocol_with_failed_adb_exit_is_blocked(self):
        self.exit = 255
        with self.assertRaises(e2e.Blocked):
            self.android()
        self.assertEqual(ET.parse(self.runner.out / 'android/junit.xml').getroot().get('errors'), '1')

    def test_crash_with_partial_failure_is_blocked_and_raw_output_survives(self):
        self.output = protocol([(SMOKE, -2), (OTHER, 0)], complete=False)
        with self.assertRaises(e2e.Blocked):
            self.android()
        xml = ET.parse(self.runner.out / 'android/junit.xml').getroot()
        self.assertEqual(xml.get('errors'), '1')
        self.assertEqual(xml.get('failures'), '1')
        self.assertIn('actual failure', (self.runner.out / 'android/instrumentation.log').read_text())

    def test_opt_in_apk_is_rejected_before_connect_or_install(self):
        test_apk(self.root / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk', saf=True)
        with self.assertRaisesRegex(e2e.Blocked, 'mode mismatch'):
            self.android()
        self.assertFalse(self.runner.identified)
        self.assertFalse(any('instrument' in c for c in self.commands))

    def test_wrong_installed_runner_stops_before_instrumentation(self):
        self.runner_component = e2e.APP + '.data.backup.BackupPolicyAcceptanceRunner'
        with self.assertRaisesRegex(e2e.Blocked, 'runner identity'):
            self.android()
        self.assertFalse(any('instrument' in c for c in self.commands))

    def test_full_does_not_reuse_device_after_android_infrastructure_blocker(self):
        self.runner.suite = 'full'
        with patch.object(self.runner, 'workflow_host'), \
             patch.object(self.runner, 'workflow_android', side_effect=e2e.Blocked('foreign AVD')), \
             patch.object(self.runner, 'workflow_saf') as saf:
            with self.assertRaises(e2e.Blocked):
                self.runner.workflow()
        saf.assert_not_called()
        self.assertEqual(self.runner.summary['groups'][-1]['status'], 'NOT RUN')


class BackupPreflightTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.runner = e2e.Runner(self.root, self.root / 'sdk', 'backup-preflight')
        self.runner.backup_serial = 'emulator-5600'
        self.calls = []
        self.avd = 'C06_disposable'
        self.transports = '* android/com.android.internal.backup.LocalTransport'

    def command(self, name, command, **kwargs):
        self.calls.append(command)
        label = name.split('/')[-1].removesuffix('.log')
        output = {'state': 'device', 'avd': self.avd + '\nOK', 'qemu': '1', 'hardware': 'ranchu',
                  'api': '26', 'boot': '1', 'transports': self.transports, 'enabled': 'Backup Manager is currently disabled',
                  'auto-restore': '1', 'help': 'bmgr backupnow restore list sets', 'powershell': '7.5.3'}[label]
        self.runner.record(name, output)
        return subprocess.CompletedProcess(command, 0, output, '')

    def preflight(self):
        with patch.object(e2e, 'sdk_tool', return_value='adb'), patch.object(e2e.shutil, 'which', return_value='pwsh'), \
             patch.object(self.runner, 'command', side_effect=self.command):
            self.runner.workflow_backup_preflight()

    def test_discovery_is_read_only_and_never_executes_destructive_acceptance(self):
        self.preflight()
        allowed = {('get-state',), ('emu', 'avd', 'name'), ('shell', 'getprop', 'ro.kernel.qemu'),
                   ('shell', 'getprop', 'ro.hardware'), ('shell', 'getprop', 'ro.build.version.sdk'),
                   ('shell', 'getprop', 'sys.boot_completed'), ('shell', 'bmgr', 'list', 'transports'),
                   ('shell', 'bmgr', 'enabled'), ('shell', 'settings', 'get', 'secure', 'backup_auto_restore'),
                   ('shell', 'bmgr')}
        device_calls = [c for c in self.calls if c[0] == 'adb']
        self.assertEqual({tuple(c[3:]) for c in device_calls}, allowed)
        self.assertTrue(all(c[:3] == ['adb', '-s', 'emulator-5600'] for c in device_calls))
        shell_calls = [c for c in self.calls if c[0] == 'pwsh']
        self.assertEqual(len(shell_calls), 1)
        self.assertIn('-NoProfile', shell_calls[0])
        self.assertEqual(shell_calls[0][-1], '$PSVersionTable.PSVersion.ToString()')
        self.assertEqual(self.runner.summary['backup_discovery']['acceptance'], 'NOT RUN')
        self.assertEqual(self.runner.summary['backup_discovery']['device_transfer'], 'NOT RUN')
        self.assertEqual(self.runner.summary['backup_discovery']['consent'], 'NOT REQUESTED')
        self.assertIsNone(self.runner.adb)  # Normal cleanup/diagnostics cannot touch this separate device.

    def test_unsupported_powershell_version_blocks_acceptance_readiness(self):
        original = self.command
        def unsupported(name, argv, **kwargs):
            if name.endswith('powershell.log'):
                return subprocess.CompletedProcess(argv, 0, '4.0', '')
            return original(name, argv, **kwargs)
        with patch.object(e2e, 'sdk_tool', return_value='adb'), patch.object(e2e.shutil, 'which', return_value='pwsh'), \
             patch.object(self.runner, 'command', side_effect=unsupported):
            with self.assertRaisesRegex(e2e.Blocked, 'prerequisites'):
                self.runner.workflow_backup_preflight()
        self.assertEqual(self.runner.summary['backup_discovery']['powershell_version'], '4.0')
        self.assertEqual(self.runner.summary['backup_acceptance'], 'NOT RUN')

    def test_missing_local_transport_blocks_without_enabling_or_changing_it(self):
        self.transports = '* account/CloudTransport'
        with self.assertRaisesRegex(e2e.Blocked, 'prerequisites'):
            self.preflight()
        self.assertFalse(any('enable' in c or 'transport' in c or 'restore' in c for c in self.calls))

    def test_reserved_saf_serial_and_physical_devices_are_rejected_before_adb(self):
        for serial in ('emulator-5580', 'physical-123'):
            self.runner.backup_serial = serial
            with self.assertRaises(e2e.Blocked):
                self.preflight()
        self.assertEqual(self.calls, [])

    def test_saf_avd_name_on_another_port_is_also_rejected(self):
        self.avd = 'PartiturasPDF_E2E_API26_' + 'a' * 12
        with self.assertRaisesRegex(e2e.Blocked, 'SAF-owned'):
            self.preflight()
        self.assertEqual(len(self.calls), 2)

    def test_cli_preflight_requires_explicit_serial_and_never_runs_acceptance_script(self):
        with patch.object(e2e, 'Runner') as runner:
            runner.return_value.run.return_value = 0
            self.assertEqual(e2e.main(['backup-preflight', '--serial', 'emulator-5600']), 0)
            self.assertEqual(runner.call_args.args[2], 'backup-preflight')
            self.assertEqual(runner.return_value.backup_serial, 'emulator-5600')
        with self.assertRaises(SystemExit):
            e2e.main(['backup-preflight'])

class DependencyAndDiscoveryTests(unittest.TestCase):
    def test_declared_fixture_ranges_are_verified_without_installing(self):
        with tempfile.TemporaryDirectory() as temporary:
            requirements = Path(temporary) / 'requirements.txt'
            requirements.write_text('# required tools\nPyMuPDF>=1.24,<2\npypdf>=5,<7\n')
            with patch.object(evidence.importlib.metadata, 'version', side_effect=['1.24.0', '6.2.0']):
                self.assertEqual(evidence.fixture_dependencies(requirements), {'PyMuPDF': '1.24.0', 'pypdf': '6.2.0'})
            with patch.object(evidence.importlib.metadata, 'version', side_effect=evidence.importlib.metadata.PackageNotFoundError('PyMuPDF')):
                with self.assertRaises(evidence.importlib.metadata.PackageNotFoundError):
                    evidence.fixture_dependencies(requirements)

    def test_incompatible_unknown_or_missing_dependency_spec_is_blocked(self):
        with tempfile.TemporaryDirectory() as temporary:
            requirements = Path(temporary) / 'requirements.txt'
            for line, version in [('PyMuPDF>=1.24,<2', '2.0.0'), ('pypdf>=5,<7', '4.0'),
                                  ('pypdf>=5,<7', '6.0rc1'), ('unrecognized', '1'), ('# empty', '1')]:
                requirements.write_text(line)
                with patch.object(evidence.importlib.metadata, 'version', return_value=version):
                    with self.assertRaises(ValueError):
                        evidence.fixture_dependencies(requirements)

    def test_java_tests_are_not_silently_excluded_from_inventory(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'ExampleInstrumentedTest.kt').write_text('package com.abdev.partituraspdf\n@Test fun useAppContext() {}')
            (root / 'JavaTest.java').write_text('package sample;\n@Test public void runs() {}')
            self.assertEqual(evidence.android_inventory(root), {SMOKE, 'sample.JavaTest.runs'})

    def test_api26_bmgr_usage_on_stderr_is_accepted_as_read_only_discovery(self):
        with tempfile.TemporaryDirectory() as temporary:
            runner = e2e.Runner(Path(temporary), Path(temporary) / 'sdk', 'backup-preflight')
            runner.backup_serial = 'emulator-5600'
            outputs = ['device', 'C06_backup\nOK', '1', 'ranchu', '26', '1',
                       '* android/com.android.internal.backup.LocalTransport',
                       'Backup Manager currently disabled', '1', '', '7.5.3']
            def command(name, argv, **kwargs):
                out = outputs.pop(0)
                return subprocess.CompletedProcess(argv, 0, out, 'bmgr backupnow restore list sets' if name.endswith('help.log') else '')
            with patch.object(e2e, 'sdk_tool', return_value='adb'), patch.object(e2e.shutil, 'which', return_value='pwsh'), \
                 patch.object(runner, 'command', side_effect=command):
                runner.workflow_backup_preflight()
            self.assertEqual(runner.summary['backup_discovery']['mode'], 'READ_ONLY')

    def test_unavailable_backup_manager_is_blocked_even_when_command_exits_zero(self):
        with tempfile.TemporaryDirectory() as temporary:
            runner = e2e.Runner(Path(temporary), Path(temporary) / 'sdk', 'backup-preflight')
            runner.backup_serial = 'emulator-5600'
            outputs = ['device', 'C06_backup\nOK', '1', 'ranchu', '26', '1',
                       '* android/com.android.internal.backup.LocalTransport', '', '1', 'bmgr backupnow restore list sets', '7.5.3']
            with patch.object(e2e, 'sdk_tool', return_value='adb'), patch.object(e2e.shutil, 'which', return_value='pwsh'), \
                 patch.object(runner, 'command', side_effect=lambda argv, *args, **kwargs:
                              subprocess.CompletedProcess([], 0, outputs.pop(0), '')):
                with self.assertRaisesRegex(e2e.Blocked, 'prerequisites'):
                    runner.workflow_backup_preflight()
