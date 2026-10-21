"""Synthetic host-only tests; no device operation or native locale PASS is implied."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import j_m3_b2b_locale_matrix as m


def raw(methods, failed=False, incomplete=False, marker=''):
    lines=[]
    for method in sorted(methods):
        cls,name=method.rsplit('.',1)
        for code in (1,-2 if failed else 0):
            lines += [f'INSTRUMENTATION_STATUS: class={cls}',f'INSTRUMENTATION_STATUS: test={name}',f'INSTRUMENTATION_STATUS: numtests={len(methods)}']
            if code<0:lines.append('INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: '+marker+' failure')
            lines.append(f'INSTRUMENTATION_STATUS_CODE: {code}')
    if not incomplete:lines.append('INSTRUMENTATION_CODE: -1')
    return '\n'.join(lines)


class LocaleCoordinatorTests(unittest.TestCase):
    def test_exact_existing_three_method_inventory(self):
        source=m.ev.android_inventory(m.ROOT/'app/src/androidTest')
        self.assertEqual(3,len(m.EXPECTED));self.assertTrue(m.EXPECTED<=source)
        self.assertEqual({26,27,36},set(m.DEVICES));self.assertEqual(4,len(m.TAGS))

    def test_complete_assertion_failure_beats_nonzero_exit(self):
        self.assertEqual('FAIL',m.classify(raw(m.EXPECTED,failed=True),m.EXPECTED,1)[0])

    def test_complete_pass_requires_zero_exit(self):
        self.assertEqual('PASS',m.classify(raw(m.EXPECTED),m.EXPECTED,0)[0])
        self.assertEqual('BLOCKED',m.classify(raw(m.EXPECTED),m.EXPECTED,1)[0])

    def test_partial_crash_missing_and_skipped_never_pass(self):
        for output in (raw({m.LOCALE}),raw(m.EXPECTED,incomplete=True),'',raw(m.EXPECTED).replace('CODE: 0','CODE: -3')):
            self.assertEqual('BLOCKED',m.classify(output,m.EXPECTED,0)[0])

    def test_unsupported_locale_ui_is_blocked_not_functional_failure(self):
        self.assertEqual('BLOCKED',m.classify(raw({m.LOCALE},failed=True,marker='J_LOCALE_BLOCKED:'),{m.LOCALE},0)[0])

    def test_identity_refuses_physical_wrong_api_and_abi(self):
        c=m.Coordinator('adb')
        with self.assertRaises(m.runner.Blocked): c.identity('physical-device',26)
        def command(*args,**kw):
            if args[-1]=='name':return 'Tablet2\nOK'
            return {'ro.build.version.sdk':'27','ro.product.cpu.abi':'x86_64','ro.kernel.qemu':'1','sys.boot_completed':'1'}.get(args[-1],'boot')
        with patch.object(c,'command',side_effect=command):
            with self.assertRaises(m.runner.Blocked):c.identity('emulator-5554',26)

    def test_dynamic_discovery_ignores_foreign_and_physical(self):
        c=m.Coordinator('adb')
        def command(*args,**kw):
            if args==('devices','-l'):return 'List of devices attached\nphysical device\nemulator-5580 device\nemulator-5554 device'
            return 'OtherAVD' if args[1]=='emulator-5580' else 'Tablet2\nOK'
        with patch.object(c,'command',side_effect=command),patch.object(c,'identity',return_value={'serial':'emulator-5554'}) as identify:
            self.assertEqual({26:{'serial':'emulator-5554'}},c.discover())
            identify.assert_called_once_with('emulator-5554',26)

    def test_duplicate_target_avd_is_blocked(self):
        c=m.Coordinator('adb')
        with patch.object(c,'command',side_effect=['List of devices attached\nemulator-5554 device\nemulator-5556 device','Tablet2','Tablet2']),patch.object(c,'identity',return_value={}):
            with self.assertRaises(m.runner.Blocked):c.discover()

    def test_installed_package_path_confinement(self):
        c=m.Coordinator('adb')
        for path in ('package:/sdcard/private.apk','package:/data/app/a/base.apk\npackage:/data/app/a/split.apk','package:/data/app/a/base.apk;rm'):
            with patch.object(c,'command',return_value=path):
                with self.assertRaises(m.runner.Blocked):c.installed('emulator-5554')

    def test_artifact_path_traversal_and_symlink_blocked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            with self.assertRaises(ValueError):m.confined(root,'../escape')
            (root/'link').symlink_to(root,target_is_directory=True)
            with self.assertRaises(ValueError):m.confined(root,'link/artifact')

    def fixture_stage(self, directory):
        directory.mkdir()
        expected={m.LOCALE};token='a'*32
        device=dict(serial='emulator-5554',avd='Tablet2',api='26',abi='arm64-v8a',boot='1',qemu='1')
        command=['adb','-s',device['serial'],'shell','am','instrument','-w','-r','-e','class',m.LOCALE.rsplit('.',1)[0]+'#'+m.LOCALE.rsplit('.',1)[1],'-e','jLocaleRun',token,'-e','jLocaleTags','fr-FR,en-US','-e','jLocaleAction','change',m.COMPONENT]
        (directory/'instrumentation.txt').write_text(raw(expected));m.ev.write_junit(directory/'junit.xml','test',[dict(name=m.LOCALE,status='PASS')])
        for name in ('library-visible','recreated-settings-visible'):
            (directory/(name+'.json')).write_text(json.dumps(dict(systemLocales=['fr-FR','en-US'],pid=1)))
            (directory/(name+'.png')).write_bytes(b'\x89PNG\r\n\x1a\nsynthetic')
            (directory/(name+'.xml')).write_text('<hierarchy/>')
        for name in ('before','returned-to-existing-app'):
            (directory/(name+'.json')).write_text(json.dumps(dict(systemLocales=['fr-FR','en-US'],pid=1)))
        record=dict(expected=sorted(expected),status='PASS',token=token,command=command,device=device,exit=0,tags=['fr-FR','en-US'],action='change',settings_before=dict(wifi='1',airplane='0',mobile='1'),settings_after=dict(wifi='1',airplane='0',mobile='1',locales='fr-FR,en-US'))
        record['files']={p.name:m.digest(p) for p in directory.iterdir()}
        m.atomic(directory/'execution.json',record)
        return record

    def test_retained_stage_reparsed_not_trusted_status(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'stage';self.fixture_stage(p)
            self.assertEqual('PASS',m.validate_stage(p,{m.LOCALE})['status'])
            (p/'instrumentation.txt').write_text('truncated')
            with self.assertRaises(ValueError):m.validate_stage(p,{m.LOCALE})

    def test_missing_observation_rehashed_still_blocked(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'stage';record=self.fixture_stage(p)
            (p/'library-visible.png').unlink();record['files'].pop('library-visible.png');m.atomic(p/'execution.json',record)
            with self.assertRaises(ValueError):m.validate_stage(p,{m.LOCALE})

    def test_single_french_cannot_prove_ordered_secondary(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'stage';record=self.fixture_stage(p)
            (p/'library-visible.json').write_text(json.dumps(dict(systemLocales=['fr-FR'],pid=1)))
            record['files']['library-visible.json']=m.digest(p/'library-visible.json');m.atomic(p/'execution.json',record)
            with self.assertRaises(ValueError):m.validate_stage(p,{m.LOCALE})

    def test_network_mutation_and_process_replacement_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'stage';record=self.fixture_stage(p);record['settings_after']['wifi']='0';m.atomic(p/'execution.json',record)
            with self.assertRaises(ValueError):m.validate_stage(p,{m.LOCALE})
            record['settings_after']['wifi']='1';(p/'returned-to-existing-app.json').write_text('{"pid":2}')
            record['files']['returned-to-existing-app.json']=m.digest(p/'returned-to-existing-app.json');m.atomic(p/'execution.json',record)
            with self.assertRaises(ValueError):m.validate_stage(p,{m.LOCALE})

    def test_incomplete_matrix_or_restoration_never_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp);m.atomic(p/'matrix.json',dict(configurations={},devices={}))
            self.assertEqual('BLOCKED',m.report(p)['status'])

    def test_command_inventory_tamper_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'stage';record=self.fixture_stage(p);record['command'][10]='all-tests';m.atomic(p/'execution.json',record)
            with self.assertRaises(ValueError):m.validate_stage(p,{m.LOCALE})


    def test_duplicate_xml_and_host_locale_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'stage';record=self.fixture_stage(p)
            record['settings_after']['locales']='fr-FR';m.atomic(p/'execution.json',record)
            with self.assertRaises(ValueError):m.validate_stage(p,{m.LOCALE})
            record['settings_after']['locales']='fr-FR,en-US'
            tree=m.ET.parse(p/'junit.xml');tree.getroot().append(copy.deepcopy(tree.getroot().find('testcase')));tree.write(p/'junit.xml')
            record['files']['junit.xml']=m.digest(p/'junit.xml');m.atomic(p/'execution.json',record)
            with self.assertRaises(ValueError):m.validate_stage(p,{m.LOCALE})

    def test_no_device_access_for_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp);m.atomic(p/'matrix.json',dict(configurations={},devices={}))
            with patch.object(m.runner,'execute',side_effect=AssertionError('device operation forbidden')):
                self.assertEqual('BLOCKED',m.report(p)['status'])


    def test_functional_failure_is_not_retried_and_finally_restores(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out=root/'build/j-m3-b2b/run'
            for name in ('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):
                f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(b'apk')
            identity=dict(serial='emulator-5554',api='26',avd='Tablet2')
            c=m.Coordinator('adb');events=[]
            def stage(device,expected,tags,action,directory):
                events.append(action);directory.mkdir(parents=True)
                if action=='probe':
                    (directory/'before.json').write_text('{"systemLocales":["en-US"]}')
                    return {'status':'PASS'}
                return {'status':'FAIL'}
            capture=dict(branch='test/mvp-stabilization-j',revision='a',tree='b',source_digest='c',build={},dirty=True)
            with patch.object(m,'ROOT',root),patch.object(m,'source',return_value=capture),\
                 patch.object(m.runner,'execute',return_value=type('Result',(),dict(returncode=0,stdout='',stderr=''))()),\
                 patch.object(c,'discover',return_value={26:identity,27:{},36:{}}),patch.object(c,'install'),\
                 patch.object(c,'settings',return_value=dict(wifi='1',airplane='0',mobile='1',locales='en-US')),\
                 patch.object(c,'stage',side_effect=stage),patch.object(m,'restore_device',return_value='PASS') as restore:
                with self.assertRaises(m.runner.FunctionalFailure):m.workflow(c,out)
                self.assertEqual(['probe','change'],events)
                restore.assert_called_once()
                self.assertEqual('FAIL',m.read_json(out/'matrix.json')['configurations']['26-es-ES']['status'])

    def test_failed_assertion_resume_does_not_reexecute(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out=root/'build/j-m3-b2b/run';out.mkdir(parents=True)
            apks={}
            for key,name in [('app','app/build/outputs/apk/debug/app-debug.apk'),('test','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]:
                f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(b'apk');apks[key]=m.digest(f)
            capture=dict(branch='test/mvp-stabilization-j',revision='a',tree='b',source_digest='c',build={},dirty=True)
            identity=dict(serial='emulator-5554',api='26',avd='Tablet2')
            entry=dict(identity=identity,restoration='PASS',original_locales=['en-US'],original_settings={})
            m.atomic(out/'matrix.json',dict(source=capture,apks=apks,devices={'26':entry},configurations={'26-es-ES':{'status':'FAIL'}}))
            c=m.Coordinator('adb')
            with patch.object(m,'ROOT',root),patch.object(m,'source',return_value=capture),\
                 patch.object(c,'discover',return_value={26:identity,27:{},36:{}}),patch.object(c,'install'),\
                 patch.object(c,'stage') as stage,patch.object(m,'restore_device',return_value='PASS'):
                with self.assertRaises(m.runner.FunctionalFailure):m.workflow(c,out,resume=True)
                stage.assert_not_called()

    def test_resume_validated_cells_are_skipped(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out=root/'build/j-m3-b2b/run';out.mkdir(parents=True)
            apks={}
            for key,name in [('app','app/build/outputs/apk/debug/app-debug.apk'),('test','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]:
                f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(b'apk');apks[key]=m.digest(f)
            capture=dict(branch='test/mvp-stabilization-j',revision='a',tree='b',source_digest='c',build={},dirty=True)
            devices={api:dict(serial='emulator-'+str(api),api=str(api),avd=name) for api,name in m.DEVICES.items()}
            entries={str(api):dict(identity=identity,restoration='PASS',original_locales=['en-US'],original_settings={}) for api,identity in devices.items()}
            configs={str(api)+'-'+tag:dict(status='PASS',locale=str(api)+'-'+tag+'-locale',tests=str(api)+'-'+tag+'-tests') for api in devices for tag in m.TAGS}
            m.atomic(out/'matrix.json',dict(source=capture,apks=apks,devices=entries,configurations=configs))
            def validated(directory,expected):
                api,language,country,_=directory.name.split('-');tag=language+'-'+country
                return dict(device=devices[int(api)],tags=[tag] if tag!='fr-FR' else ['fr-FR','en-US'],installed=apks,source=capture)
            c=m.Coordinator('adb')
            with patch.object(m,'ROOT',root),patch.object(m,'source',return_value=capture),\
                 patch.object(c,'discover',return_value=devices),patch.object(c,'install'),\
                 patch.object(c,'stage') as stage,patch.object(m,'restore_device',side_effect=lambda coordinator,directory,entry: entry.update(restoration='PASS') or 'PASS'),\
                 patch.object(m,'validate_stage',side_effect=validated),patch.object(m,'report',return_value={'status':'PASS'}):
                self.assertEqual('PASS',m.workflow(c,out,resume=True)['status']);stage.assert_not_called()


if __name__=='__main__':unittest.main()
