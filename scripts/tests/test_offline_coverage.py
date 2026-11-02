"""Synthetic host-policy fixtures; never evidence of real application coverage."""
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch, Mock
from argparse import Namespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import offline_coverage as offline
from test_local_regression import protocol, manifest, test_apk, SMOKE, OTHER


def put(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not isinstance(data, (str, bytes)):
        data = json.dumps(data)
    path.write_bytes(data if isinstance(data, bytes) else data.encode())


class RetainedFixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.run_dir = self.root / 'producer'
        self.directory = self.run_dir / 'coverage'
        self.work = self.root / 'work'; self.work.mkdir()
        self.serial = 'emulator-5580'
        self.run_id = 'a'*32
        self.path = '/data/data/' + offline.evidence.APP + '/files/local-coverage-' + self.run_id + '/coverage.ec'
        self.selection = dict(all=sorted([SMOKE, OTHER]), standard=sorted([SMOKE, OTHER]), diagnostics=[],
                              diagnostic_invocations=[], diagnostic_status='NOT RUN', notAnnotation=offline.lc.diagnostics.ANNOTATION)
        self.sources = {'app/src/main/aidl/example/Test.aidl': b'interface Test {}',
                        'app/src/main/java/example/Test.kt': b'package example\nclass Test'}
        self.build_tools = {name: b'tool bytes' for name in (
            'app/build.gradle.kts', 'gradle/libs.versions.toml', 'scripts/PersistenceCoverageInputs.java',
            'scripts/persistence_coverage.py', 'scripts/local_coverage.py', 'scripts/local_test_evidence.py',
            'scripts/local-coverage.init.gradle', 'scripts/local_e2e.py', 'build.gradle.kts',
            'gradle.properties', 'gradle/wrapper/gradle-wrapper.properties')}
        self.config = dict(androidCoverage=True, versions=dict(gradle='9.8.0', agp='9.4.1', jdk='25', jacoco='0.8.14'),
                           jacocoClasspath='never executed from fixture')
        self.capture = dict(commit=offline.PRODUCER, run=self.run_id, working_tree='',
            device=dict(api=26, identity_verified=True, emulator=True, serial=self.serial, abi='arm64-v8a',name='owned synthetic AVD'),
            host=dict(status='PASS'),
            versions=self.config['versions'], android_selection=self.selection,
            android_inventory=self.selection['all'], jvm_inventory=sorted([SMOKE, OTHER]))
        for apk_name in ('app-debug.apk', 'app-debug-androidTest.apk'):
            test_apk(self.directory / apk_name)
        self.capture['apks'] = {str(self.directory / name): offline.pc.digest(self.directory / name)
                               for name in ('app-debug.apk', 'app-debug-androidTest.apk')}
        self.capture['apks'].update({str(self.root/'old-app-build'/name): sha
                                     for name,sha in [(Path(path).name,sha) for path,sha in self.capture['apks'].items()]})
        self.build = dict(commit=offline.PRODUCER, workingTree='', localCapture=self.capture,
                          sourceHashes={name: hashlib.sha256(value).hexdigest() for name,value in self.sources.items()},
                          buildFiles={name: hashlib.sha256(value).hexdigest() for name,value in self.build_tools.items()},
                          **self.config['versions'])
        self.classes = {'production': {'synthetic': {'id': '1'}}, 'tests': {}}
        self.run = dict(format=1, run='c05-fixture', androidApi=26, started=int(time.time()*1000)-1000,
                        build=self.build, classes=self.classes['production'], testClasses={})
        self.summary = dict(status='BLOCKED', failures=[], jvm_status='PASS', android_status='PASS',
                            host=self.capture['host'], android_instrumentation={'probes':True},
                            apk_mode=offline.evidence.inspect_apk(self.directory/'app-debug-androidTest.apk',manifest(),'normal',{SMOKE,OTHER}),
                            jvm_tests=2, android_tests=2)
        self.outer = dict(device=copy.deepcopy(self.capture['device']),commit=offline.PRODUCER, working_tree='', run=self.run_id, status='BLOCKED', cleanup=[dict(status='PASS')],
            commands=[dict(log='coverage/instrumentation.log', exit=0, command=['adb', '-s', self.serial, 'shell', 'am', 'instrument', '-w', '-r',
                '-e', 'coverage', 'true', '-e', 'coverageFile', self.path, '-e', 'notAnnotation', offline.lc.diagnostics.ANNOTATION,
                offline.evidence.APP+'.test/'+offline.evidence.NORMAL_RUNNER]),
                dict(log='coverage/app-debug-androidTest.apk.manifest.txt', exit=0,
                     command=['aapt2', 'dump', 'xmltree', str(self.directory/'app-debug-androidTest.apk'), '--file', 'AndroidManifest.xml'])])
        raw = protocol().replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_RESULT: coverageFilePath='+self.path+'\nINSTRUMENTATION_CODE: -1')
        put(self.directory/'instrumentation.log', raw)
        put(self.directory/'app-debug-androidTest.apk.manifest.txt', manifest())
        for group in ('jvm', 'android'):
            xml = '<testsuite tests="2">' + ''.join('<testcase classname="'+name.rsplit('.',1)[0]+'" name="'+name.rsplit('.',1)[1]+'"/>' for name in (SMOKE,OTHER)) + '</testsuite>'
            put(self.directory/(group+'-junit/results.xml'), xml)
        self.binaries = {}
        for name in offline.BINARIES:
            data = ('synthetic '+name).encode(); put(self.directory/name, data)
            self.binaries[name] = (hashlib.sha256(data).hexdigest(), len(data))
        for name in offline.AIDL_FILES:
            put(self.directory/name, b'synthetic retained bytecode')
        self.save()
        self.addCleanup(patch.stopall)
        patch.object(offline, 'BINARIES', self.binaries).start()
        patch.object(offline, 'tree', return_value=set(self.sources)).start()
        patch.object(offline, 'blob', side_effect=lambda revision,name: (self.sources | self.build_tools)[name]).start()
        patch.object(offline.lc, 'android_selection', return_value=self.selection).start()
        patch.object(offline.evidence, 'android_inventory', return_value={SMOKE,OTHER}).start()
        patch.object(offline.evidence, 'inspect_coverage_apk', return_value={'probes': True}).start()

    def save(self):
        for name,value in [('capture.json',self.capture),('configuration.json',self.config),
                           ('classes.json',self.classes),('persistence/run.json',self.run)]:
            put(self.directory/name,value)
        self.summary['artifacts'] = {name:sha for name,sha in offline.artifact_inventory(self.directory).items()
                                     if name not in ('summary.json','junit.xml','index.html')}
        put(self.directory/'summary.json',self.summary)
        put(self.run_dir/'summary.json',self.outer)

    def auth(self):
        return offline.authenticate(self.directory,self.work,self.outer)


class AuthenticationTests(RetainedFixture):
    def test_valid_structure_is_verified_without_touching_original(self):
        before = offline.artifact_inventory(self.run_dir)
        auth = self.auth()
        self.assertEqual('VERIFIED',auth['junit']['status'])
        self.assertEqual(2,auth['junit']['android']['executed'])
        self.assertEqual('BLOCKED',auth['summary']['status'])
        self.assertEqual(before,offline.artifact_inventory(self.run_dir))
        self.assertTrue((self.work/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes/com/abdev/partituraspdf/pdf/android/isolated/IPdfWorker$Stub.class').exists())

    def test_wrong_revision_and_dirty_source_block(self):
        self.capture['commit'] = '0'*40; self.save()
        with self.assertRaisesRegex(ValueError,'revision'):self.auth()
        self.capture['commit'] = offline.PRODUCER; self.capture['working_tree']='dirty';self.save()
        with self.assertRaisesRegex(ValueError,'clean'):self.auth()

    def test_deleted_or_altered_class_and_binary_block(self):
        path=self.directory/offline.AIDL_FILES[2]
        path.unlink()
        with self.assertRaisesRegex(ValueError,'artifact'):self.auth()
        put(path,b'changed')
        with self.assertRaisesRegex(ValueError,'artifact'):self.auth()

    def test_pinned_binary_rejects_rewritten_local_manifest(self):
        put(self.directory/'inputs/android/coverage.ec',b'forged')
        self.save()
        with self.assertRaisesRegex(ValueError,'Pinned binary'):self.auth()

    def test_source_and_tool_hash_mismatches_block(self):
        name=next(iter(self.build['sourceHashes']));self.build['sourceHashes'][name]='0'*64;self.save()
        with self.assertRaisesRegex(ValueError,'source mismatch'):self.auth()
        self.build['sourceHashes'][name]=hashlib.sha256(self.sources[name]).hexdigest()
        self.build['buildFiles']['scripts/PersistenceCoverageInputs.java']='0'*64;self.save()
        with self.assertRaisesRegex(ValueError,'tooling mismatch'):self.auth()

    def test_apk_identity_mismatch_block(self):
        self.capture['apks'][str(self.directory/'app-debug.apk')]='0'*64;self.save()
        with self.assertRaisesRegex(ValueError,'APK identity'):self.auth()

    def test_missing_duplicate_and_failed_junit_block(self):
        path=self.directory/'android-junit/results.xml';original=path.read_text()
        for xml in [original.replace('tests="2"','tests="1"').replace('<testcase classname="'+OTHER.rsplit('.',1)[0]+'" name="example"/>',''),
                    original.replace('</testsuite>',original.split('>',1)[1]),
                    original.replace('/>','><failure/></testcase>',1)]:
            with self.subTest(xml=xml):
                put(path,xml);self.save()
                with self.assertRaises(ValueError):self.auth()

    def test_incomplete_instrumentation_and_wrong_filter_block(self):
        path=self.directory/'instrumentation.log';raw=path.read_text()
        put(path,raw.replace('INSTRUMENTATION_CODE: -1',''));self.save()
        with self.assertRaises(offline.evidence.IncompleteInstrumentation):self.auth()
        put(path,raw);self.outer['commands'][0]['command'][16]='wrong.Annotation';self.save()
        with self.assertRaisesRegex(ValueError,'filter'):self.auth()

    def test_ignored_source_bytes_are_never_silently_dropped(self):
        self.build['sourceHashes']['app/src/ignored-cache.bin']='0'*64;self.save()
        with self.assertRaises(OSError):self.auth()

    def test_stale_original_junit_and_contradictory_device_records_block(self):
        path=self.directory/'android-junit/results.xml'
        os.utime(path,ns=(1,1))
        with self.assertRaisesRegex(ValueError,'Stale original JUnit'):
            self.auth()
        os.utime(path,None)
        self.outer['device']['api']=27;self.save()
        with self.assertRaisesRegex(ValueError,'Contradictory recorded device'):
            self.auth()

    def test_scope_configuration_cannot_narrow_denominators(self):
        prefix='com/abdev/partituraspdf/'
        classes={prefix+'core/A':{'path':'core/A.class'},prefix+'core/B':{'path':'core/B.class'}}
        auth=dict(frozen=dict(production=classes),config=dict(contractPatterns=['/old/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/core/A.class']))
        with self.assertRaisesRegex(ValueError,'denominator'):offline.scopes(auth)


class BinaryEvidenceTests(RetainedFixture):
    def parsed(self):
        auth=self.auth()
        session=dict(id='synthetic session',start=self.run['started']+1,end=self.run['started']+2)
        native={'com/abdev/partituraspdf/pdf/android/'+name for name in
                ('AndroidNativePdfDocument','AndroidNativePdfPage','AndroidRenderBuffer')}
        records={name:dict(coveredProbes=1) for name in set(offline.pc.ADAPTERS)|native}
        entries=[dict(path=str(self.directory/name),sessions=[session],records=copy.deepcopy(records),testRecords={})
                 for name in offline.BINARIES]
        parsed=dict(classes=auth['frozen']['production'],inputs=entries)
        auth['summary']['jvm_input']=dict(copy.deepcopy(entries[0]),sha256=self.binaries['inputs/jvm.exec'][0],retained='inputs/jvm.exec')
        auth['summary']['android_capture']=dict(command=['adb','-s',self.serial,'exec-out','run-as',offline.evidence.APP,'cat','files/local-coverage-'+self.run_id+'/coverage.ec'],exit=0,bytes=self.binaries['inputs/android/coverage.ec'][1],
                                                sha256=self.binaries['inputs/android/coverage.ec'][0])
        return auth,parsed

    def test_session_native_and_persistence_policies_are_reused(self):
        auth,parsed=self.parsed()
        offline.check_binary_evidence(auth,parsed,self.directory)
        parsed['inputs'][1]['sessions'][0]['start']=1
        with self.assertRaisesRegex(ValueError,'session'):
            offline.check_binary_evidence(auth,parsed,self.directory)

    def test_missing_native_records_block(self):
        auth,parsed=self.parsed()
        parsed['inputs'][1]['records'].pop('com/abdev/partituraspdf/pdf/android/AndroidNativePdfPage')
        with self.assertRaisesRegex(ValueError,'native adapter'):
            offline.check_binary_evidence(auth,parsed,self.directory)

    def test_original_jvm_records_and_android_retrieval_cannot_be_relabelled(self):
        auth,parsed=self.parsed()
        auth['summary']['jvm_input']['records']={}
        with self.assertRaisesRegex(ValueError,'JVM input attribution'):
            offline.check_binary_evidence(auth,parsed,self.directory)
        auth['summary']['jvm_input']=dict(copy.deepcopy(parsed['inputs'][0]),sha256=self.binaries['inputs/jvm.exec'][0],retained='inputs/jvm.exec')
        auth['summary']['android_capture']['exit']=1
        with self.assertRaisesRegex(ValueError,'retrieval identity'):
            offline.check_binary_evidence(auth,parsed,self.directory)


class OutputAndPolicyTests(RetainedFixture):
    def args(self,**kwargs):
        return Namespace(producer_run=self.run_dir,output=self.root/'analysis',verify_only=True,
                         java='/not-executed/java',jacoco_classpath='trusted fixture',**kwargs)

    def test_overlap_existing_and_symlink_output_rejected(self):
        for output in (self.run_dir/'new',self.root,self.run_dir):
            args=self.args();args.output=output
            with self.subTest(output=output),self.assertRaises(ValueError):offline.execute(args)
        alias=self.root/'alias'
        alias.symlink_to(self.run_dir,target_is_directory=True)
        args=self.args();args.output=alias/'new'
        with self.assertRaisesRegex(ValueError,'Symlink'):offline.execute(args)

    def test_traversal_symlink_and_unknown_artifacts_rejected(self):
        for name in ('../escape','/escape','x/../escape','x\\escape','x\nclass'):
            with self.assertRaises(ValueError):offline.relative(name)
        put(self.directory/'unrecorded',b'unknown')
        with self.assertRaisesRegex(ValueError,'Uninventoried'):self.auth()

    def test_blocked_writes_new_partial_diagnostics_only(self):
        before=offline.artifact_inventory(self.run_dir)
        with patch.object(offline,'git',return_value=b'analyzer-revision'):
            self.capture['commit']='wrong';self.save()
            before=offline.artifact_inventory(self.run_dir)
            result=offline.execute(self.args())
        self.assertEqual('BLOCKED',result['status'])
        self.assertEqual(before,offline.artifact_inventory(self.run_dir))
        provenance=offline.read_json(self.root/'analysis/provenance.json')
        self.assertEqual(offline.PRODUCER,provenance['producer']['expected_commit'])
        self.assertFalse(provenance['producer']['verified'])
        self.assertEqual('analyzer-revision',provenance['analyzer']['commit'])
        self.assertEqual('POST_HOC_REANALYSIS',result['kind'])
        self.assertEqual('NOT VERIFIED',offline.read_json(self.root/'analysis/junit-verification.json')['status'])

    def test_verification_driver_separates_identities_and_keeps_copies_immutable(self):
        args=self.args();args.java=str(self.root/'java');put(Path(args.java),b'not executed')
        before=offline.artifact_inventory(self.run_dir)
        def analyze(auth,backend,directory,output,verify_only,result):
            self.assertNotEqual(directory,self.directory)
            self.assertTrue(directory.is_relative_to(output))
            self.assertTrue(verify_only)
            return dict(status='PASS',input_verification='PASS',measurements='NOT RUN',scopes={},failures=[])
        with patch.object(offline,'git',return_value=b'analyzer-revision'), \
             patch.object(offline,'trusted_classpath',return_value=str(Path(args.java))), \
             patch.object(offline,'analyze',side_effect=analyze), \
             patch.object(offline.subprocess,'run',return_value=subprocess.CompletedProcess([],0,'','openjdk version "25"')) as process:
            result=offline.execute(args)
        process.assert_called_once_with([args.java,'-version'],capture_output=True,text=True,timeout=30,check=True)
        self.assertEqual('PASS',result['status']);self.assertEqual('NOT RUN',result['measurements'])
        self.assertEqual(before,offline.artifact_inventory(self.run_dir))
        self.assertEqual(before,offline.artifact_inventory(args.output/'producer-copy'))
        provenance=offline.read_json(args.output/'provenance.json')
        self.assertEqual(offline.PRODUCER,provenance['producer']['commit'])
        self.assertTrue(provenance['producer']['verified'])
        self.assertEqual('BLOCKED',provenance['producer']['original_status'])
        self.assertEqual('analyzer-revision',provenance['analyzer']['commit'])
        self.assertTrue(provenance['analyzer']['files'])

    def test_analyzer_does_not_expose_device_build_or_test_operations(self):
        source=(offline.ROOT/'scripts/offline_coverage.py').read_text()
        self.assertNotIn('pc.api(',source)
        self.assertNotIn('pc.prepare(',source)
        self.assertNotIn('pc.verify(',source)
        self.assertNotIn('shell=True',source)
        self.assertNotIn('gradlew',source)
        self.assertNotIn('local_e2e.py run',source)

    def test_untrusted_analyzer_library_is_rejected(self):
        path=self.root/'org.jacoco.core-0.8.14.jar';put(path,b'not trusted')
        with self.assertRaisesRegex(ValueError,'library hash'):offline.trusted_classpath(str(path))


class AnalysisBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name)

    def fixture(self):
        frozen=dict(production={'A':{'path':'A.class'}},tests={})
        auth=dict(frozen=frozen)
        backend=Mock()
        generated={'generated':{'id':'a','probes':2}}
        backend.reader.side_effect=[frozen['production'],{},generated,dict(generatedClasses=generated)]
        selected={name:{'A':{'path':'A.class'}} for name in ('contracts','pdf','metadata','preferences','production')}
        return auth,backend,selected

    def test_verification_only_does_not_generate_reports(self):
        auth,backend,selected=self.fixture()
        with patch.object(offline,'check_binary_evidence'),patch.object(offline,'scopes',return_value=selected):
            result=offline.analyze(auth,backend,Path('/input'),self.output,True)
        self.assertEqual('PASS',result['status']);self.assertEqual('NOT RUN',result['measurements'])
        backend.report.assert_not_called()

    def test_bytecode_and_generated_identity_mismatches_block(self):
        for position in (0,1,3):
            auth,backend,selected=self.fixture()
            sequence=list(backend.reader.side_effect)
            sequence[position]={'forged':'identity'}
            backend.reader.side_effect=sequence
            with self.subTest(position=position),self.assertRaises((ValueError,KeyError)):
                offline.analyze(auth,backend,Path('/input'),self.output,True)
            backend.report.assert_not_called()

    def test_report_gates_and_contract_jvm_only_input(self):
        auth,backend,selected=self.fixture()
        package=dict(package=dict(LINE=dict(covered=90,total=100),BRANCH=dict(covered=8,total=10)),sources={})
        for gate_failures in ([],['real gate failure']):
            auth,backend,selected=self.fixture()
            with patch.object(offline,'check_binary_evidence'),patch.object(offline,'scopes',return_value=selected), \
                 patch.object(offline.lc,'analyzed_report',return_value={'package':package}), \
                 patch.object(offline.lc,'component_gate',return_value=gate_failures), \
                 patch.object(offline.pc,'thresholds',return_value=[]), \
                 patch.object(offline.pc,'digest',return_value='synthetic'), \
                 patch.object(offline,'artifact_inventory',return_value={}):
                result=offline.analyze(auth,backend,Path('/input'),self.output,False)
            self.assertEqual('FAIL' if gate_failures else 'PASS',result['status'])
            self.assertEqual([Path('/input/inputs/jvm.exec')],backend.report.call_args_list[0].args[-1])
            self.assertEqual(2,len(backend.report.call_args_list[1].args[-1]))
            self.assertEqual('INFORMATIONAL',result['scopes']['production']['classification'])

    def test_validated_gate_failure_survives_later_report_blocker(self):
        auth,backend,selected=self.fixture();state={}
        package=dict(package=dict(LINE=dict(covered=0,total=100),BRANCH=dict(covered=0,total=10)),sources={})
        with patch.object(offline,'check_binary_evidence'),patch.object(offline,'scopes',return_value=selected), \
             patch.object(offline.lc,'analyzed_report',side_effect=[{'p':package},ValueError('bad later report')]):
            with self.assertRaisesRegex(ValueError,'later report'):
                offline.analyze(auth,backend,Path('/input'),self.output,False,state)
        self.assertEqual('FAIL',state['status']);self.assertTrue(state['failures'])


class RealHostReportSmokeTests(unittest.TestCase):
    def test_real_jacoco_report_and_identity_reader_without_application_build(self):
        """Compile only a temporary synthetic Java fixture, not any application/test source."""
        import shutil
        cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle')) / 'caches/modules-2/files-2.1'
        java, javac = shutil.which('java'), shutil.which('javac')
        candidates = {name: list(cache.rglob(name)) for name in offline.LIBRARIES} if cache.is_dir() else {}
        if not java or not javac or not candidates or not all(candidates.values()):
            self.skipTest('Existing host JDK/JaCoCo report libraries unavailable; no downloads')
        cp = offline.trusted_classpath(os.pathsep.join(str(paths[0]) for paths in candidates.values()))
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve()
            source=root/'app/src/main/java/com/abdev/partituraspdf/core/Fixture.java'
            put(source,'package com.abdev.partituraspdf.core; public class Fixture { public int answer() { return 42; } }')
            classes=root/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
            classes.mkdir(parents=True)
            subprocess.run([javac,'-d',str(classes),str(source)],capture_output=True,check=True)
            backend=offline.Backend(java,cp,root,classes)
            inventory=backend.reader('inventory')
            self.assertEqual({'com/abdev/partituraspdf/core/Fixture'},set(inventory))
            helper=root/'Write.java'
            put(helper,'import java.io.*; import org.jacoco.core.data.*; class Write { public static void main(String[] a) throws Exception { try(var out=new FileOutputStream(a[0])) { var w=new ExecutionDataWriter(out); w.visitSessionInfo(new SessionInfo("synthetic-host-only",100,200)); } } }')
            binary=root/'synthetic.exec'
            subprocess.run([java,'-cp',cp,str(helper),str(binary)],capture_output=True,check=True)
            backend.report(root,'contracts',inventory,[binary])
            packages=offline.lc.analyzed_report(root/'contracts.xml',inventory)
            self.assertTrue(offline.lc.component_gate('contracts',packages))
            self.assertTrue((root/'contracts.html/index.html').is_file())
            writer=root/'Capture.java'
            put(writer,'import java.io.*; import org.jacoco.core.data.*; class Capture { public static void main(String[] a) throws Exception { try(var out=new FileOutputStream(a[0])) { var w=new ExecutionDataWriter(out); w.visitSessionInfo(new SessionInfo("synthetic-host-only",100,200)); boolean[] p=new boolean[Integer.parseInt(a[2])]; p[0]=true; w.visitClassExecution(new ExecutionData(Long.parseUnsignedLong(a[1],16),"com/abdev/partituraspdf/core/Fixture",p)); } } }')
            entry=next(iter(inventory.values()))
            def capture(identity,probes):
                subprocess.run([java,'-cp',cp,str(writer),str(binary),identity,str(probes)],capture_output=True,check=True)
            capture(entry['id'],entry['probes'])
            self.assertEqual(inventory,backend.reader('validate',binary)['classes'])
            for identity,probes in [(format(int(entry['id'],16)^1,'x'),entry['probes']),
                                    (entry['id'],entry['probes']+1)]:
                capture(identity,probes)
                with self.assertRaisesRegex(ValueError,'Class ID mismatch|Probe count mismatch'):backend.reader('validate',binary)
            # A forged recorded class inventory cannot equal the real reader's output.
            forged=copy.deepcopy(inventory)
            next(iter(forged.values()))['id']='0'
            self.assertNotEqual(forged,backend.reader('inventory'))
