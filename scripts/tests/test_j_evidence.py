"""Synthetic host evidence tests; none certifies Android execution."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import unittest.mock
from datetime import datetime, timezone
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import local_test_evidence as ev

ROOT = Path(__file__).resolve().parents[2]


class PhaseJEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        stamp = datetime.now(timezone.utc).isoformat()
        self.capture = dict(schema=1, tier='A', device='NOT SELECTED', run='a'*32, revision='b'*40, tree='c'*40,
            source_digest='d'*64, branch='test/j', environment={'python':'test'}, started=stamp,
            expected_jvm=['Example.test'], build={p:'e'*64 for p in (
                'app/build.gradle.kts','gradle/libs.versions.toml','gradle/wrapper/gradle-wrapper.properties')})
        self.catalogue = ev.j_catalogue(ROOT/'scripts/j_scenarios.json')
        # Synthetic fixture has one test; real mapped identities are checked separately below.
        self.catalogue['scenarios'][0]['test_cases'] = []
        self.summary = dict(suite='host',run=self.capture['run'],commit=self.capture['revision'],status='PASS',
                            host_checks=[],commands=[],j_jvm_artifacts={})
        for s in self.catalogue['scenarios']:
            if not s['implemented'] or s['tier'] != 'A': continue
            name = s['check']; log = 'host/'+name+'.log'
            ev.j_atomic(self.root,log,'Completed check\n')
            self.summary['host_checks'].append(dict(name=name,status='PASS',log=log,tests=1))
            self.summary['commands'].append(dict(command=['host-tool',name],log=log,exit=0,
                run=self.capture['run'],revision=self.capture['revision'],started=stamp,finished=stamp,
                sha256=hashlib.sha256(ev.j_path(self.root,log).read_bytes()).hexdigest()))
        self.xml('<testsuite tests="1" failures="0"><testcase classname="Example" name="test"/></testsuite>')

    def xml(self, text):
        name='host/jvm-xml/TEST-Example.xml'
        ev.j_atomic(self.root,name,text)
        self.summary['j_jvm_artifacts'][name]=hashlib.sha256(ev.j_path(self.root,name).read_bytes()).hexdigest()

    def report(self, complete=True, current=None):
        return ev.j_report(self.root,self.capture,self.summary,self.catalogue,
                            current=current or self.capture,complete=complete)

    def test_repository_jvm_inventory_includes_debug_source_set(self):
        expected = ev.android_inventory(ROOT / 'app/src/test', smoke=None)
        debug = ev.android_inventory(ROOT / 'app/src/testDebug', smoke=None)
        self.assertTrue(debug)
        with unittest.mock.patch('subprocess.check_output', side_effect=lambda args, **kw:
                'a'*40 if args[1:3] == ['rev-parse', 'HEAD'] else
                'b'*40 if args[1] == 'rev-parse' else
                'test/j' if args[1] == 'branch' else
                '\n'.join(('app/build.gradle.kts', 'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties'))
                if args[1] == 'ls-files' else ''), \
             unittest.mock.patch('subprocess.run', return_value=unittest.mock.Mock(returncode=0, stdout='JDK', stderr='')):
            capture = ev.j_capture(ROOT, 'a'*32)
        self.assertEqual(expected | debug, set(capture['expected_jvm']))

    def test_valid_versioned_report_and_artifact_hashes(self):
        result=self.report()
        self.assertEqual('PASS',result['status'])
        self.assertEqual('NOT RUN',result['android'])
        self.assertTrue((self.root/'phase-j/report.html').is_file())
        self.assertEqual(result,json.loads((self.root/'phase-j/summary.json').read_text()))
        self.assertIn('DEFERRED BY OWNER',[r['status'] for r in result['scenarios']])
        for row in result['scenarios']:
            for a in row['artifacts']:
                self.assertEqual(a['sha256'],hashlib.sha256(ev.j_path(self.root,a['path']).read_bytes()).hexdigest())

    def test_absent_or_bad_identity_is_rejected(self):
        for key in ('revision','tree','source_digest','build','expected_jvm','branch','environment'):
            with self.subTest(key=key):
                candidate=copy.deepcopy(self.capture);candidate.pop(key)
                with self.assertRaises(ValueError):ev.j_identity(candidate)
        self.capture['revision']='not-a-sha'
        with self.assertRaises(ValueError):self.report()

    def test_changed_build_or_revision_rejected(self):
        for key in ('revision','source_digest','tree','branch','build'):
            current=copy.deepcopy(self.capture);current[key]= 'f'*40 if key in ('revision','tree') else ('other' if key=='branch' else ('f'*64 if key=='source_digest' else {p:'f'*64 for p in current['build']}))
            with self.subTest(key=key),self.assertRaises(ValueError):self.report(current=current)

    def test_mismatched_run_and_revision(self):
        for key in ('run','commit'):
            saved=self.summary[key];self.summary[key]='wrong'
            with self.subTest(key=key),self.assertRaises(ValueError):self.report()
            self.summary[key]=saved

    def test_missing_or_partial_checks_cannot_pass(self):
        self.summary['host_checks'].pop()
        self.assertEqual('BLOCKED',self.report()['status'])
        self.assertEqual('BLOCKED',self.report(complete=False)['status'])

    def test_interrupted_all_green_is_not_pass(self):
        self.assertEqual('BLOCKED',self.report(complete=False)['status'])

    def test_duplicate_and_unknown_checks_rejected(self):
        self.summary['host_checks'].append(copy.deepcopy(self.summary['host_checks'][0]))
        with self.assertRaises(ValueError):self.report()
        self.summary['host_checks'][-1]['name']='unexpected'
        with self.assertRaises(ValueError):self.report()

    def test_failed_command_never_passes(self):
        self.summary['commands'][0]['exit']=1
        with self.assertRaises(ValueError):self.report()
        self.summary['host_checks'][0]['status']='FAIL'
        self.summary['status']='FAIL'
        self.assertEqual('FAIL',self.report()['status'])

    def test_missing_duplicate_and_stale_command_rejected(self):
        command=self.summary['commands'].pop()
        with self.assertRaises(ValueError):self.report()
        self.summary['commands'] += [command,copy.deepcopy(command)]
        with self.assertRaises(ValueError):self.report()
        self.summary['commands'].pop();self.summary['commands'][-1]['run']='f'*32
        with self.assertRaises(ValueError):self.report()

    def test_corrupt_or_absent_log_rejected(self):
        path=ev.j_path(self.root,self.summary['commands'][0]['log'])
        path.write_text('tampered')
        with self.assertRaises(ValueError):self.report()
        path.unlink()
        with self.assertRaises(ValueError):self.report()

    def test_junit_failed_skipped_duplicate_missing_or_corrupt_rejected(self):
        for xml in ('broken','<testsuite/>',
            '<testsuite><testcase classname="Example" name="test"><failure/></testcase></testsuite>',
            '<testsuite><testcase classname="Example" name="test"><skipped/></testcase></testsuite>',
            '<testsuite><testcase classname="Example" name="test"/><testcase classname="Example" name="test"/></testsuite>',
            '<testsuite><testcase classname="Other" name="test"/></testsuite>'):
            self.xml(xml)
            with self.subTest(xml=xml),self.assertRaises((ValueError,ev.ET.ParseError)):self.report()

    def test_junit_content_hash_rejected_after_mutation(self):
        ev.j_path(self.root,'host/jvm-xml/TEST-Example.xml').write_text('<testsuite/>')
        with self.assertRaises(ValueError):self.report()

    def test_unsafe_paths_and_symlink_parents_rejected(self):
        for path in ('../escape','/tmp/escape','host/../../escape','C:\\escape','host\\escape',''):
            with self.subTest(path=path),self.assertRaises(ValueError):ev.j_path(self.root,path)
        try:(self.root/'link').symlink_to(self.root/'host',target_is_directory=True)
        except OSError:self.skipTest('Symlinks unavailable on host')
        with self.assertRaises(ValueError):ev.j_path(self.root,'link/jvm.log')

    def test_redacted_junit_remains_parseable_and_preserves_tests(self):
        self.capture['expected_jvm'] = ['com.example.pdf.Tests.test']
        self.xml('<testsuite tests="1" failures="0"><testcase classname="com.example.pdf.Tests" name="test"/>'
                 '<system-out>Read /Users/private/Personal score.pdf content://provider/secret</system-out></testsuite>')
        self.report()
        public = ev.ET.parse(self.root / 'phase-j/artifacts/host/jvm-xml/TEST-Example.xml').getroot()
        self.assertEqual('1', public.get('tests'))
        cases = list(public.iter('testcase'))
        self.assertEqual(1, len(cases))
        self.assertEqual('com.example.pdf.Tests', cases[0].get('classname'))
        text = ev.ET.tostring(public, encoding='unicode')
        for private in ('Personal', '/Users', 'content://', 'secret'):
            self.assertNotIn(private, text)

    def test_unimplemented_scenario_cannot_claim_pass(self):
        catalogue = copy.deepcopy(self.catalogue)
        next(s for s in catalogue['scenarios'] if not s['implemented'])['status'] = 'PASS'
        path = self.root / 'catalogue.json'
        path.write_text(json.dumps(catalogue))
        with self.assertRaises(ValueError):
            ev.j_catalogue(path)

    def test_public_redaction(self):
        raw={'serial':'private-id','text':'content://provider/private /Users/Alex/secret.pdf C:\\Users\\Alex\\score.pdf personal.pdf emulator-5554'}
        public=json.dumps(ev.j_redact(raw))
        for token in ('private-id','content://','/Users','Alex','personal.pdf','5554'):
            self.assertNotIn(token,public)

    def test_atomic_finalization_and_html_escaping(self):
        self.summary['commands'][0]['command']=['tool','<script>alert(1)</script>']
        self.report()
        self.assertFalse(list(self.root.rglob('*.tmp')))
        self.assertNotIn('<script>',(self.root/'phase-j/report.html').read_text())

    def test_device_identity_and_wrong_tier_rejected(self):
        for key,value in [('device',{'serial':'emulator-5554','api':27}),('tier','B')]:
            capture=copy.deepcopy(self.capture);capture[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):ev.j_identity(capture)
        self.summary['suite']='android'
        with self.assertRaises(ValueError):self.report()

    def test_stale_and_missing_timestamps_rejected(self):
        command=self.summary['commands'][0]
        command['started']='2000-01-01T00:00:00+00:00'
        with self.assertRaises(ValueError):self.report()
        command.pop('finished')
        with self.assertRaises(ValueError):self.report()

    def test_nonexecuted_status_vocabulary_is_preserved(self):
        for status in ('BLOCKED','NOT RUN','NOT APPLICABLE','DEFERRED BY OWNER'):
            self.summary['host_checks'][0]['status']=status
            result=self.report()
            self.assertEqual(status,result['scenarios'][0]['status'])
            self.assertEqual('BLOCKED',result['status'])

    def test_device_mappings_resolve_to_real_tests_without_claiming_execution(self):
        ordinary = ev.android_inventory(ROOT / 'app/src/androidTest')
        opt_in = ev.android_inventory(ROOT / 'scripts/local-e2e/androidTest', smoke=None)
        report = self.report()
        manifest = {f['id']: f for f in json.loads((ROOT / 'test-fixtures/manifest.json').read_text())['files']}
        for scenario in self.catalogue['scenarios']:
            if not scenario['implemented'] or scenario['tier'] != 'B':
                continue
            with self.subTest(id=scenario['id']):
                self.assertIn(scenario['test_method'], ordinary | opt_in)
                row = next(r for r in report['scenarios'] if r['id'] == scenario['id'])
                self.assertEqual('NOT RUN', row['status'])
                self.assertIsNone(row['executed_test_method'])
                self.assertEqual(self.capture['revision'], row['source_revision'])
                self.assertEqual('NOT SELECTED', row['device'])
                if scenario['fixture']['id'] in manifest:
                    self.assertEqual(manifest[scenario['fixture']['id']]['sha256'], row['fixture']['sha256'])
        self.assertEqual('PASS', report['status']) # Host PASS is never device PASS.

    def test_device_scenario_cannot_become_host_command_or_claim_pass(self):
        path = self.root / 'catalogue.json'
        for field, value in [('status', 'PASS'), ('check', 'android'), ('test_method', '')]:
            catalogue = copy.deepcopy(self.catalogue)
            scenario = next(s for s in catalogue['scenarios'] if s['implemented'] and s['tier'] == 'B')
            scenario[field] = value
            path.write_text(json.dumps(catalogue))
            with self.subTest(field=field), self.assertRaises(ValueError):
                ev.j_catalogue(path)

    def test_recovery_jvm_mappings_resolve_and_require_verified_execution(self):
        catalogue = ev.j_catalogue(ROOT / 'scripts/j_scenarios.json')
        source = ev.android_inventory(ROOT / 'app/src/test', smoke=None)
        mapped = catalogue['scenarios'][0]['test_cases']
        self.assertTrue(mapped)
        for case in mapped:
            self.assertIn(case['test_method'], source)
        case = copy.deepcopy(mapped[0])
        case['test_method'] = 'Example.test'
        self.catalogue['scenarios'][0]['test_cases'] = [case]
        row = self.report()['scenarios'][0]['test_cases'][0]
        self.assertEqual('PASS', row['status'])
        self.assertEqual('Example.test', row['executed_test_method'])
        self.assertEqual('validated-full-jvm-inventory', row['provenance'])
        case['test_method'] = 'Missing.test'
        with self.assertRaises(ValueError):
            self.report()
        self.summary['host_checks'][0]['status'] = 'BLOCKED'
        row = self.report()['scenarios'][0]['test_cases'][0]
        self.assertEqual('BLOCKED', row['status'])
        self.assertIsNone(row['executed_test_method'])

    def test_recovery_mappings_reject_duplicate_ids_and_missing_attribution(self):
        path = self.root / 'catalogue.json'
        for mutation in ('duplicate', 'missing'):
            catalogue = ev.j_catalogue(ROOT / 'scripts/j_scenarios.json')
            case = catalogue['scenarios'][0]['test_cases'][0]
            if mutation == 'duplicate':
                case['id'] = catalogue['scenarios'][0]['id']
            else:
                case.pop('failure_classification')
            path.write_text(json.dumps(catalogue))
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                ev.j_catalogue(path)

    def test_invalid_catalogue_rejected(self):
        p=self.root/'catalogue.json'
        bad=copy.deepcopy(self.catalogue);bad['scenarios'].append(bad['scenarios'][0])
        p.write_text(json.dumps(bad))
        with self.assertRaises(ValueError):ev.j_catalogue(p)


if __name__=='__main__':unittest.main()
