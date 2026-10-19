"""Host regressions only: synthetic evidence never demonstrates Android execution."""
import copy
import hashlib
import json
import re
from pathlib import Path
import subprocess
import tempfile
import unittest
import sys
from unittest.mock import patch
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "app/src/androidTest/java/com/abdev/partituraspdf/reader"))

import pdfium_diagnostic_evidence as ev
import run_pdfium_process_experiments as runner

ROOT = next(p for p in Path(__file__).resolve().parents if (p / 'gradlew').is_file())
FIXTURES = {r['id']: r for r in json.loads((ROOT / 'test-fixtures/manifest.json').read_text())['files']}
ASSERTION = 'java.lang.AssertionError: Valid fixture 01 did not open/render; inspect ReaderPdfiumProcess PID/sequence: Attempt(rendered=false)'

# These H smoke tests are mandatory standard acceptance, independently of future suite growth.
SETTINGS_TESTS = {
    ev.APP + '.settings.SettingsScreenTest#' + method for method in (
        'loadingHasAccessibleProgressAndNoEnabledOrSelectedDefaults',
        'readyShowsActualSelectionsAndSingleSelectionRadioSemantics',
        'themeSelectionDispatchesTypedActionWithoutOptimisticSelection',
        'fitSelectionDispatchesTypedActionWithoutChangingThemeOrSelection',
        'clickingAlreadySelectedValuesDoesNotDispatchRedundantActions',
        'storageReadFailureOffersRetryWithoutInventingSelections',
        'otherReadFailureUsesSafeGenericMessageAndExplicitRetry',
        'themeWriteFailurePreservesSelectionAndDispatchesOnlyThemeRetry',
        'fitWriteFailurePreservesSelectionAndDispatchesOnlyFitRetry',
        'pendingThemeDisablesItsGroupButFitCanQueueAnIndependentIntent',
        'reconciliationAllowsExplicitReadRecoveryEvenWhileSnapshotIsLoading',
        'independentOperationStatesRemainVisibleAndWriteRetryRequiresReady',
        'newObservedStateAndCallbackAreUsedAfterRecompositionWithoutNavigation',
    )
}


def attempts(method, pid, start, token, api, contaminated):
    engine = 'AndroidPdfReadEngine' if method.endswith('Adapter') or method.startswith('k_') else 'PdfRenderer'
    expected = runner.SCENARIOS[method]
    rows = []
    for i, fixture in enumerate(expected):
        failed = fixture in {'14', '15'} or (contaminated and i == len(expected) - 1 and len(set(expected) & {'14', '15'}) > 0)
        row = dict(pid=pid, processStartElapsedMs=start, api=api, invocation=token, test=method, sequence=i+1,
                   fixture=fixture, engine=engine, sha256Verified=True, sha256=FIXTURES[fixture]['sha256'],
                   size=FIXTURES[fixture]['bytes'], descriptorBytes=FIXTURES[fixture]['bytes'], descriptorValid=True,
                   descriptorClosed=True, cleanupException=None, resourceCloses=1 if engine == 'AndroidPdfReadEngine' else 0,
                   ownership='TRANSFERRED' if engine == 'AndroidPdfReadEngine' else 'direct',
                   constructed=not failed, pageCountRead=not failed, rendered=not failed,
                   ownerCloseAttempted=not failed, ownerCloseSucceeded=None if failed else True,
                   operation='engine.open' if engine == 'AndroidPdfReadEngine' else 'PdfRenderer constructor',
                   exception='java.io.IOException' if failed and engine == 'PdfRenderer' else None)
        row['class'] = ev.PROCESS_CLASS
        row['typedOutcome'] = ('Failure(error=INVALID_DOCUMENT)' if failed else 'Success(open and render)') if engine == 'AndroidPdfReadEngine' else 'not applicable to direct PdfRenderer'
        if not failed:
            row['pageCount'] = FIXTURES[fixture]['expected_pages']
        rows.append(row)
    return rows


def record(selected, number, api=26, contaminated=True):
    cls, method = selected.split('#'); pid = 1000+number; start = 1000+number*200
    rows = attempts(method, pid, start, 'token', api, contaminated) if cls == ev.PROCESS_CLASS else None
    failed = bool(rows and any(r['fixture'] in {'14', '15'} for r in rows) and contaminated)
    value = dict(selected=selected, processIdentity=[pid,start], processIsolationVerified=True,
                 status='FAIL' if failed else 'PASS', completionCode=-2 if failed else 0,
                 stack=ASSERTION if failed else '', invocation='token',
                 processes={'afterStop': {ev.APP: [], ev.APP+'.test': []}})
    if rows is not None:
        value['attempts'] = rows
    return value


def suite(api=26, contaminated=True):
    return [record(selected, i, api, contaminated) for i, selected in enumerate(ev.invocation_plan(runner.SCENARIOS))]


def output(selected, number=0, api=26, contaminated=True, token='token', start=None):
    row = record(selected, number, api, contaminated)
    if start is not None:
        row['processIdentity'][1] = start
    cls, method = selected.split('#')
    identity = dict(api=api, pid=row['processIdentity'][0], processStartElapsedMs=row['processIdentity'][1],
                    invocation=token, test=method)
    identity['class'] = cls
    text = (f'INSTRUMENTATION_STATUS: class={cls}\nINSTRUMENTATION_STATUS: test={method}\n'
            'INSTRUMENTATION_STATUS: numtests=1\nINSTRUMENTATION_STATUS_CODE: 1\n'
            f'INSTRUMENTATION_STATUS: class={cls}\nINSTRUMENTATION_STATUS: test={method}\n'
            'INSTRUMENTATION_STATUS: numtests=1\n')
    if row['status'] == 'FAIL': text += 'INSTRUMENTATION_STATUS: stack='+row['stack']+'\n'
    text += f'INSTRUMENTATION_STATUS_CODE: {row["completionCode"]}\n'
    text += 'INSTRUMENTATION_RESULT: pdfiumProcessIdentity='+json.dumps(identity)+'\n'
    if 'attempts' in row:
        for attempt in row['attempts']:
            attempt['invocation'] = token; attempt['processStartElapsedMs'] = identity['processStartElapsedMs']
        text += 'INSTRUMENTATION_RESULT: readerPdfiumEvidence='+json.dumps(row['attempts'])+'\n'
    return text+'INSTRUMENTATION_CODE: -1\n'


class DiagnosticEvidenceTests(unittest.TestCase):
    def classify(self, rows=None, api=26):
        return ev.classify(suite(api) if rows is None else rows, api, runner.SCENARIOS, FIXTURES)

    def test_every_original_test_is_assigned_once_and_all_isolated_tests_remain_standard(self):
        inv = ev.test_inventory(ROOT, runner.SCENARIOS)
        source_count = sum(
            len(re.findall(r'^\s*@Test\b', path.read_text(encoding='utf-8'), re.M))
            for path in (ROOT / 'app/src/androidTest/java').rglob('*.kt')
        )
        self.assertEqual(source_count, inv['total'])
        self.assertEqual(source_count - len(inv['assigned']), len(inv['standard']))
        self.assertEqual(14, len(inv['assigned']))
        expected_diagnostics = set(ev.CLEAN) | {
            ev.PROCESS_CLASS + '#' + method for method in runner.SCENARIOS
        }
        self.assertEqual(expected_diagnostics, set(inv['assigned']))
        self.assertEqual(len(inv['standard']), len(set(inv['standard'])))
        self.assertEqual(len(inv['assigned']), len(set(inv['assigned'])))
        self.assertTrue(SETTINGS_TESTS <= set(inv['standard']))
        self.assertFalse(SETTINGS_TESTS & set(inv['assigned']))
        self.assertFalse(set(inv['standard']) & set(inv['assigned']))
        self.assertEqual(29, len(ev.invocation_plan(runner.SCENARIOS)))
        self.assertEqual(24, sum('.pdf.android.isolated.' in name for name in inv['standard']))

    def test_settings_cases_are_required_in_complete_standard_junit(self):
        inventory = ev.test_inventory(ROOT, runner.SCENARIOS)
        settings_case = sorted(SETTINGS_TESTS)[0]
        for mutation in ['valid', 'missing', 'unexpected', 'duplicate', 'failed', 'errored', 'skipped']:
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as tmp:
                root = ET.Element('testsuite')
                for selected in inventory['standard']:
                    if selected == settings_case and mutation == 'missing':
                        continue
                    cls, method = selected.split('#')
                    case = ET.SubElement(root, 'testcase', classname=cls, name=method)
                    if selected == settings_case and mutation in {'failed', 'errored', 'skipped'}:
                        ET.SubElement(case, {'failed': 'failure', 'errored': 'error', 'skipped': 'skipped'}[mutation])
                if mutation in {'unexpected', 'duplicate'}:
                    cls, method = settings_case.split('#')
                    ET.SubElement(root, 'testcase', classname=cls,
                                  name='unexpectedSettingsTest' if mutation == 'unexpected' else method)
                ET.ElementTree(root).write(Path(tmp)/'junit.xml')
                if mutation == 'valid':
                    self.assertEqual(len(inventory['standard']),
                                     ev.verify_standard(tmp, inventory)['executed'])
                else:
                    with self.assertRaises(ValueError):
                        ev.verify_standard(tmp, inventory)

    def test_known_defect_requires_both_clean_baselines_and_independent_recoveries(self):
        rows = suite(); result = self.classify(rows)
        self.assertEqual('DIAGNOSTICS_VALIDATED', result['status'])
        self.assertEqual(8, result['rawFailed']); self.assertEqual(8, result['knownPlatformDefects'])
        self.assertEqual(21, result['rawPassed']); self.assertEqual('FAIL', rows[5]['status'])
        self.assertEqual('KNOWN_PLATFORM_DEFECT_REPRODUCED', rows[5]['classification'])

    def poisoned_j_suite(self, api=26):
        rows = suite(api, contaminated=api != 36)
        entry = next(r for r in rows if r['selected'].endswith('#j_encryptedCorruptThenValidAdapter'))
        for row in entry['attempts'][1:3] if api == 36 else entry['attempts'][1:]:
            row['typedOutcome'] = 'Failure(error=DOCUMENT_FORMAT)'
        return rows, entry

    def test_android8_exact_encrypted_corrupt_sequence_preserves_failure_and_recovers(self):
        for api in [26, 27]:
            with self.subTest(api=api):
                rows, entry = self.poisoned_j_suite(api)
                result = self.classify(rows, api)
                self.assertEqual('DIAGNOSTICS_VALIDATED', result['status'])
                self.assertEqual(29, result['invocations'])
                self.assertEqual(8, result['rawFailed'])
                self.assertEqual(8, result['knownPlatformDefects'])
                self.assertEqual('FAIL', entry['status'])
                self.assertEqual(-2, entry['completionCode'])
                self.assertEqual(['01', '14', '15', '01'], [r['fixture'] for r in entry['attempts']])
                index = rows.index(entry)
                recovery = rows[index + 1:index + 3]
                self.assertEqual(
                    ['d_freshProcessRecoveryDirect', 'k_cleanAdapterRepeatedValidOpens'],
                    [r['selected'].split('#')[1] for r in recovery]
                )
                self.assertTrue(all(r['status'] == 'PASS' for r in recovery))
                self.assertTrue(all(r['classification'] == 'CLEAN_NATIVE_ACCEPTANCE_PASSED' for r in recovery))
                self.assertEqual(3, len({tuple(r['processIdentity']) for r in [entry] + recovery}))
                with tempfile.TemporaryDirectory() as tmp:
                    for i, run in enumerate(rows):
                        ev.write_junit(Path(tmp)/f'{i}.xml', run['selected'], run, 'synthetic raw evidence')
                    self.assertEqual(8, sum(ET.parse(p).getroot().get('failures') == '1'
                                            for p in Path(tmp).glob('*.xml')))

    def test_poisoned_format_is_rejected_outside_exact_android8_sequence(self):
        for mutation in ['corrupt-first', 'e-scenario', 'api36', 'permission',
                         'missing-final', 'missing-recovery', 'no-encrypted-format',
                         'unexpected-final']:
            api = 36 if mutation == 'api36' else 26
            rows, entry = self.poisoned_j_suite(api)
            if mutation == 'corrupt-first':
                entry = next(r for r in rows if r['selected'].endswith('#h_corruptEncryptedThenValidAdapter'))
                entry['attempts'][1]['typedOutcome'] = 'Failure(error=DOCUMENT_FORMAT)'
            elif mutation == 'e-scenario':
                entry = next(r for r in rows if r['selected'].endswith('#e_corruptThenValidAdapter'))
                entry['attempts'][1]['typedOutcome'] = 'Failure(error=DOCUMENT_FORMAT)'
            elif mutation == 'permission':
                entry['attempts'][2]['typedOutcome'] = 'Failure(error=PERMISSION_REVOKED)'
            elif mutation == 'missing-final':
                entry['attempts'].pop()
            elif mutation == 'missing-recovery':
                rows.pop(rows.index(entry) + 1)
            elif mutation == 'no-encrypted-format':
                entry['attempts'][1]['typedOutcome'] = 'Failure(error=INVALID_DOCUMENT)'
            elif mutation == 'unexpected-final':
                entry['attempts'][-1]['typedOutcome'] = 'Failure(error=PERMISSION_REVOKED)'
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                self.classify(rows, api)

    def test_unaffected_platform_records_real_success_without_expected_failure(self):
        for api in [26,27,36]:
            result = self.classify(suite(api,False), api)
            self.assertEqual(0, result['rawFailed']); self.assertEqual(29, result['rawPassed'])
            self.assertEqual(0, result['knownPlatformDefects'])

    def test_api36_native_regression_is_never_a_known_platform_exception(self):
        with self.assertRaises(ValueError): self.classify(suite(36),36)

    def test_missing_or_extra_invocation_and_skipped_diagnostic_are_rejected(self):
        for mutation in ['missing','extra','skipped']:
            rows = suite()
            if mutation == 'missing': rows.pop()
            elif mutation == 'extra': rows.append(copy.deepcopy(rows[-1]))
            else: rows[5]['status'] = 'SKIP'
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): self.classify(rows)

    def test_reused_process_wrong_api_wrong_token_and_wrong_fixture_are_rejected(self):
        for mutation in ['reused','api','token','fixture','sha','size','engine','sequence','pid','unverified','alive']:
            rows = suite(); row = rows[5]['attempts'][-1]
            if mutation == 'reused': rows[5]['processIdentity'] = rows[4]['processIdentity']
            elif mutation == 'api': row['api'] = 36
            elif mutation == 'token': row['invocation'] = 'old'
            elif mutation == 'fixture': row['fixture'] = '02'
            elif mutation == 'sha': row['sha256'] = '0'*64
            elif mutation == 'size': row['size'] += 1
            elif mutation == 'engine': row['engine'] = 'fake'
            elif mutation == 'sequence': row['sequence'] = 0
            elif mutation == 'pid': row['pid'] += 1
            elif mutation == 'unverified': rows[5]['processIsolationVerified'] = False
            else: rows[5]['processes']['afterStop'][ev.APP] = ['123']
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): self.classify(rows)

    def test_wrong_error_partial_sequence_and_false_pass_are_rejected(self):
        for mutation in ['error','partial','pass','stack','code','cleanup','leak','count','baseline','recovery','negative','missing-cleanup','missing-exception','missing-owner']:
            rows = suite(); entry = rows[5]; row = entry['attempts'][-1]
            if mutation == 'error': row['exception'] = 'java.lang.NullPointerException'
            elif mutation == 'partial': entry['attempts'].pop()
            elif mutation == 'pass': entry['status'] = 'PASS'; entry['completionCode'] = 0
            elif mutation == 'stack': entry['stack'] = 'java.lang.AssertionError: unrelated failure'
            elif mutation == 'code': entry['completionCode'] = -1
            elif mutation == 'cleanup': row['cleanupException'] = 'failed'
            elif mutation == 'leak': row['descriptorClosed'] = False
            elif mutation == 'count': row['resourceCloses'] = 3
            elif mutation == 'baseline': rows[3]['status'] = 'FAIL'
            elif mutation == 'recovery': rows[6]['status'] = 'FAIL'
            elif mutation == 'missing-cleanup': row.pop('cleanupException')
            elif mutation == 'missing-exception': row.pop('exception')
            elif mutation == 'missing-owner': row.pop('ownerCloseSucceeded')
            else: entry['attempts'][1]['rendered'] = True
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): self.classify(rows)

    def test_fresh_complete_selected_junit_identity_is_required(self):
        selected = ev.DIFFERENTIAL; text = output(selected)
        result = ev.completion(text,selected,'token',1000,1100,set(),26)
        self.assertEqual('PASS',result['status'])
        for mutation in ['old','reused','api','method','token','missing','crash','skip','count','error','duplicate']:
            bad = text; previous = set(); before = 1000
            if mutation == 'old': before = 1500
            elif mutation == 'reused': previous = {(1000,1000)}
            elif mutation == 'api': bad = text.replace('"api": 26','"api": 36')
            elif mutation == 'method': bad = text.replace('test=a0_', 'test=wrong_a0_')
            elif mutation == 'token': bad = text.replace('"token"','"old"')
            elif mutation == 'missing': bad = text.replace('INSTRUMENTATION_RESULT:', 'missing:')
            elif mutation == 'crash': bad = text+'INSTRUMENTATION_FAILED: crashed\n'
            elif mutation == 'skip': bad = text.replace('STATUS_CODE: 0','STATUS_CODE: -3')
            elif mutation == 'count': bad = text.replace('numtests=1','numtests=0')
            elif mutation == 'error': bad = text.replace('INSTRUMENTATION_CODE: -1','INSTRUMENTATION_CODE: 0')
            else: bad = text+text
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                ev.completion(bad,selected,'token',before,1600,previous,26)

    def test_raw_failed_junit_remains_failed_xml_even_when_diagnostic_is_classified(self):
        rows = suite(); self.classify(rows)
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)/'junit.xml'; entry=rows[5]
            ev.write_junit(p,entry['selected'],entry,'original raw failure')
            root = ET.parse(p).getroot()
            self.assertEqual('1',root.get('failures')); self.assertIsNotNone(root.find('testcase/failure'))
            self.assertEqual('original raw failure',root.findtext('system-out'))
            entry['completionCode']=-1
            ev.write_junit(p,entry['selected'],entry,'original error')
            self.assertEqual('1',ET.parse(p).getroot().get('errors'))

    def test_standard_inventory_rejects_missing_extra_duplicate_failed_or_skipped_cases(self):
        inventory={'standard':['C#a','C#b']}
        for mutation in ['valid','missing','extra','duplicate','failed','skipped']:
            with tempfile.TemporaryDirectory() as tmp:
                root=ET.Element('testsuite'); a=ET.SubElement(root,'testcase',classname='C',name='a')
                if mutation!='missing': ET.SubElement(root,'testcase',classname='C',name='b')
                if mutation in {'extra','duplicate'}: ET.SubElement(root,'testcase',classname='C',name='x' if mutation=='extra' else 'a')
                if mutation in {'failed','skipped'}: ET.SubElement(a,'failure' if mutation=='failed' else 'skipped')
                ET.ElementTree(root).write(Path(tmp)/'junit.xml')
                if mutation=='valid': self.assertEqual(2,ev.verify_standard(tmp,inventory)['executed'])
                else:
                    with self.subTest(mutation=mutation), self.assertRaises(ValueError): ev.verify_standard(tmp,inventory)
        with tempfile.TemporaryDirectory() as tmp, self.assertRaises(ValueError): ev.verify_standard(tmp,inventory)

    def test_stale_standard_xml_cannot_satisfy_current_execution(self):
        import os
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'junit.xml'
            path.write_text('<testsuite><testcase classname="C" name="a"/></testsuite>')
            os.utime(path,ns=(1_000_000_000,1_000_000_000))
            with self.assertRaisesRegex(ValueError,'Stale'):
                ev.verify_standard(tmp,{'standard':['C#a']},2000)

    def test_ci_requires_explicit_confirmation_api_and_standard_evidence_before_adb(self):
        with patch.object(runner.Runner,'call') as call:
            with self.assertRaises(runner.Blocked): runner.main(['--serial','emulator-5554','--ci'])
            call.assert_not_called()

    def test_ci_main_runs_all_assigned_invocations_and_retains_failed_junit(self):
        self.run_ci_main('valid')

    def test_ci_main_cannot_succeed_without_complete_standard_acceptance(self):
        self.run_ci_main('missing-standard')

    def test_ci_main_blocks_transport_crash_and_never_accepts_partial_experiments(self):
        self.run_ci_main('crash')

    def run_ci_main(self, mode):
        # End-to-end mocked host orchestration only: no ADB/device execution is claimed.
        component = runner.APP+'.test/androidx.test.runner.AndroidJUnitRunner'
        clock = [10000]; invoked = []; reports = []
        def call(*args, **kwargs):
            if args == ('shell','getprop','ro.kernel.qemu'): return '1'
            if args == ('shell','getprop','ro.build.version.sdk'): return '26'
            if args == ('emu','avd','name'): return 'owned-avd'
            if args == ('shell','getprop','ro.build.fingerprint'): return 'test-image'
            if args == ('shell','pm','list','instrumentation'): return f'instrumentation:{component} (target={runner.APP})'
            if args == ('shell','cat','/proc/uptime'):
                value=clock[0]; clock[0]+=100; return str(value/1000)
            if args[0]=='logcat': return 'raw diagnostic log'
            raise AssertionError(args)
        def execute(command, **kwargs):
            if command[:3]==['git','status','--porcelain']: text=''
            elif command[:3]==['git','rev-parse','HEAD']: text='741c750f65358bd6161b0a5a666d798e8ee70d5b'
            else:
                self.assertEqual('adb',command[0]); self.assertEqual(component,command[-1])
                selected=command[command.index('class')+1]
                token=command[command.index('readerProcessRun')+1]
                invoked.append(selected)
                text=output(selected,len(invoked)-1,token=token,start=clock[0]-90)
                if mode=='crash': text='INSTRUMENTATION_FAILED: crashed'
            return subprocess.CompletedProcess(command,0,text)
        with tempfile.TemporaryDirectory() as tmp:
            standard=Path(tmp)/'standard'; standard.mkdir()
            inv=ev.test_inventory(ROOT,runner.SCENARIOS)
            xml=ET.Element('testsuite')
            for test in inv['standard']:
                cls,method=test.split('#'); ET.SubElement(xml,'testcase',classname=cls,name=method)
            if mode!='missing-standard': ET.ElementTree(xml).write(standard/'results.xml')
            try:
                with patch.object(
                    runner, 'manifest_identity',
                    return_value=(runner.APP + '.test', runner.APP, component)
                ), patch.object(runner.Runner,'call',side_effect=call), \
                     patch.object(runner.Runner,'stop_and_verify',return_value={'afterStop':{runner.APP:[],runner.APP+'.test':[]}}) as stop, \
                     patch.object(runner.Runner,'installed_hash',return_value=hashlib.sha256(b'APK').hexdigest()), \
                     patch.object(Path,'read_bytes',return_value=b'APK'), \
                     patch.object(runner.subprocess,'run',side_effect=execute), patch('builtins.print') as printed:
                    result=runner.main(['--serial','emulator-5554','--confirm','emulator-5554','--ci',
                                        '--expected-api','26','--standard-results',str(standard),'--standard-started-ms','1'])
                    report=Path(printed.call_args.args[0].split(': ',1)[1]); reports.append(report)
                summary=json.loads((report/'summary.json').read_text())
                if mode=='crash':
                    self.assertEqual(2,result); self.assertEqual('BLOCKED',summary['status']); self.assertEqual(1,len(invoked))
                else:
                    self.assertEqual(ev.invocation_plan(runner.SCENARIOS),invoked); self.assertEqual(29,stop.call_count)
                    self.assertEqual(8,sum(ET.parse(p).getroot().get('failures')=='1' for p in report.rglob('junit.xml')))
                    self.assertEqual(29,len(list(report.rglob('logcat.txt'))))
                    self.assertEqual(29,len(list(report.rglob('run.json'))))
                    if mode=='valid':
                        self.assertEqual(0,result); self.assertEqual('DIAGNOSTICS_VALIDATED',summary['status'])
                        expected_count = len(inv['standard'])
                        self.assertEqual(
                            dict(executed=expected_count, passed=expected_count,
                                 failed=0, errored=0, skipped=0),
                            summary['standardAcceptance'],
                        )
                    else:
                        self.assertEqual(2,result); self.assertEqual('BLOCKED',summary['status'])
            finally:
                import shutil
                for report in reports: shutil.rmtree(report)


if __name__ == '__main__': unittest.main()
