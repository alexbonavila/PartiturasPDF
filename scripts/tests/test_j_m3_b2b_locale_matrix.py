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
                    (directory/'library-visible.json').write_text('{"systemLocales":["en-US"]}')
                    (directory/'instrumentation.txt').write_text(raw({m.LOCALE}))
                    return {'status':'PASS','exit':0}
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


    def test_missing_database_uses_explicit_effective_en_us_not_default(self):
        config='config: mcc310-mnc260-en-rUS-ldltr-sw720dp-v26'
        for value in (None, '', 'null', ' null '):
            with self.subTest(value=value):
                result=m.locale_evidence(dict(locales=value,config=config),['en-US'])
                self.assertEqual('VERIFIED',result['status'])
                self.assertEqual('UNAVAILABLE',result['channels']['database']['state'])
                self.assertEqual(['effective_configuration'],result['verified_channels'])
        self.assertEqual('BLOCKED',m.locale_evidence(dict(locales='null'),['en-US'])['status'])

    def test_explicit_channels_agree_on_full_french_order(self):
        result=m.locale_evidence(dict(locales='fr-FR,en-US',config='config: fr-rFR,en-rUS-ldltr-v36'),['fr-FR','en-US'],['fr-FR','en-US'])
        self.assertEqual('VERIFIED',result['status'])
        self.assertEqual(['database','effective_configuration'],result['verified_channels'])

    def test_contradictions_are_not_treated_as_unavailable(self):
        for data in (dict(locales='es-ES',config='config: en-rUS-ldltr-v26'),
                     dict(locales='en-US',config='config: es-rES-ldltr-v26')):
            result=m.locale_evidence(data,['en-US'])
            self.assertEqual('BLOCKED',result['status'])
            self.assertTrue(any(c['state']=='CONTRADICTORY' for c in result['channels'].values()))

    def test_french_secondary_is_never_inferred(self):
        for settings,observed in [(dict(locales='null',config='config: fr-rFR-ldltr-v26'),['fr-FR','en-US']),
                                  (dict(locales='fr-FR'),['fr-FR']),
                                  (dict(locales='null',config='config: fr-rFR-ldltr-v26'),['fr-FR'])]:
            self.assertEqual('BLOCKED',m.locale_evidence(settings,observed,['fr-FR','en-US'])['status'])
        self.assertEqual('VERIFIED',m.locale_evidence(dict(locales='null',config='config: fr-rFR,en-rUS-ldltr-v26'),['fr-FR','en-US'],['fr-FR','en-US'])['status'])

    def test_malformed_settings_or_instrumentation_blocks(self):
        for value in ('en_US','en-US,','en-US,en-US','null,en-US',42):
            result=m.locale_evidence(dict(locales=value,config='config: en-rUS-ldltr-v26'),['en-US'])
            self.assertEqual('BLOCKED',result['status'])
            self.assertEqual('MALFORMED',result['channels']['database']['state'])
        for observed in (None,[],['en-US','en-US'],['en_US']):
            self.assertEqual('BLOCKED',m.locale_evidence(dict(locales='en-US'),observed)['status'])
        self.assertEqual('BLOCKED',m.locale_evidence(dict(locales='en-US',config='config: en--rUS-ldltr-v26'),['en-US'])['status'])

    def test_config_parser_does_not_invent_regions_or_order(self):
        self.assertEqual(['zh-Hans-CN','en-US'],m.effective_locales('config: b+zh+Hans+CN,en-rUS-ldltr-v36')['locales'])
        for config in ('config: en-rUS-v26','',None):
            self.assertEqual('BLOCKED',m.locale_evidence(dict(locales='null',config=config),['en-US'])['status'])
        self.assertEqual('BLOCKED',m.locale_evidence(dict(locales='null',config='config: en-ldltr-v26'),['en-US'])['status'])

    def test_restoration_stage_validates_when_database_is_absent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);folder=root/'stage';record=self.fixture_stage(folder)
            record['action']='restore';record['command'][record['command'].index('jLocaleAction')+1]='restore'
            record['settings_after'].update(locales='null',config='config: fr-rFR,en-rUS-ldltr-v26')
            m.atomic(folder/'execution.json',record)
            self.assertEqual('PASS',m.validate_stage(folder,{m.LOCALE})['status'])
            coordinator=m.Coordinator('adb')
            entry=dict(identity=record['device'],original_locales=record['tags'],original_settings=record['settings_after'],locale_change_attempted=True)
            # The synthetic files stand in for already completed restoration instrumentation.
            with patch.object(coordinator,'stage',return_value=record),patch.object(m.uuid,'uuid4',return_value=type('Id',(),dict(hex='fixture'))()):
                folder.rename(root/'restore-fixture')
                self.assertEqual('PASS',m.restore_device(coordinator,root,entry))

    def test_early_baseline_contradiction_preserves_raw_channels_without_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);ledger=dict(devices={},configurations={});identity=dict(api='26',serial='emulator-5554',avd='Tablet2')
            coordinator=m.Coordinator('adb')
            def stage(device,expected,tags,action,directory):
                self.assertEqual('probe',action);directory.mkdir()
                for name in ('before','library-visible'):(directory/(name+'.json')).write_text('{"systemLocales":["en-US"]}')
                (directory/'instrumentation.txt').write_text(raw({m.LOCALE}))
                return dict(status='PASS',exit=0)
            with patch.object(coordinator,'settings',return_value=dict(locales='es-ES',config='config: en-rUS-ldltr-v26')),patch.object(coordinator,'stage',side_effect=stage):
                with self.assertRaises(m.runner.Blocked):m.capture_baseline(coordinator,root,ledger,identity)
            entry=m.read_json(root/'matrix.json')['devices']['26']
            self.assertTrue(entry['auxiliary_probe_completed'])
            self.assertFalse(entry['locale_change_attempted']);self.assertFalse(entry['language_change_observed'])
            self.assertIn('NOT REQUIRED',entry['restoration'])
            self.assertEqual('es-ES',entry['baseline_evidence']['channels']['database']['raw'])
            self.assertEqual('CONTRADICTORY',entry['baseline_evidence']['channels']['database']['state'])


    def test_baseline_null_setting_retains_verified_effective_original(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);ledger=dict(devices={},configurations={});identity=dict(api='26',serial='emulator-5554',avd='Tablet2')
            coordinator=m.Coordinator('adb')
            def stage(device,expected,tags,action,directory):
                self.assertEqual('probe',action);directory.mkdir()
                for name in ('before','library-visible'):(directory/(name+'.json')).write_text('{"systemLocales":["en-US"]}')
                (directory/'instrumentation.txt').write_text(raw({m.LOCALE}))
                return dict(status='PASS',exit=0)
            with patch.object(coordinator,'settings',return_value=dict(locales='null',config='config: mcc310-mnc260-en-rUS-ldltr-v26')),patch.object(coordinator,'stage',side_effect=stage):
                entry=m.capture_baseline(coordinator,root,ledger,identity)
            self.assertTrue(entry['baseline_verified']);self.assertTrue(entry['auxiliary_probe_completed'])
            self.assertEqual(['en-US'],entry['original_locales'])
            self.assertEqual('null',entry['original_settings']['locales'])
            self.assertFalse(entry['locale_change_attempted']);self.assertFalse(entry['language_change_observed'])
            self.assertEqual(['effective_configuration'],entry['baseline_evidence']['verified_channels'])

    def test_interrupted_baseline_does_not_restore_or_modify_locale(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);ledger=dict(devices={},configurations={});identity=dict(api='26',serial='emulator-5554',avd='Tablet2')
            coordinator=m.Coordinator('adb')
            with patch.object(coordinator,'settings',return_value=dict(locales='null',config='config: en-rUS-ldltr-v26')),patch.object(coordinator,'stage',side_effect=KeyboardInterrupt) as stage:
                with self.assertRaises(m.runner.Blocked):m.capture_baseline(coordinator,root,ledger,identity)
                self.assertEqual('probe',stage.call_args.args[3])
            entry=m.read_json(root/'matrix.json')['devices']['26']
            self.assertFalse(entry['auxiliary_probe_completed']);self.assertFalse(entry['locale_change_attempted'])
            self.assertIn('interrupted',entry['baseline_reason'])
            with patch.object(coordinator,'stage') as stage:
                self.assertIn('NOT REQUIRED',m.restore_device(coordinator,root,entry));stage.assert_not_called()
            summary=m.report(root)
            self.assertEqual('BLOCKED',summary['status']);self.assertEqual('null',summary['baselines']['26']['original_settings']['locales'])

    def recovery_fixture(self, root):
        folder=root/'probe';record=self.fixture_stage(folder)
        record.update(source=dict(revision='old',branch='test/mvp-stabilization-j',tree='tree',source_digest='digest',build={},dirty=False), installed=dict(app='a',test='b'),action='probe')
        record['command'][record['command'].index('jLocaleAction')+1]='probe'
        record['settings_after']['locales']='en-US'
        for name in ('before','library-visible','recreated-settings-visible'):
            (folder/(name+'.json')).write_text(json.dumps(dict(systemLocales=['en-US'],pid=1)))
        record['files']={p.name:m.digest(p) for p in folder.iterdir() if p.name!='execution.json'}
        m.atomic(folder/'execution.json',record)
        entry=dict(identity=record['device'],probe='probe',baseline_verified=True,original_locales=['en-US'],
                   original_settings=dict(locales='null',config='config: en-rUS-ldltr-v26',wifi='1',airplane='0',mobile='1'))
        ledger=dict(source=record['source'],apks=record['installed'],devices={'26':entry})
        m.atomic(root/'matrix.json',ledger)
        return ledger,record

    def test_recovery_uses_hash_bound_original_not_current_extra_spanish(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);ledger,record=self.recovery_fixture(root)
            entry=m.recovery_entry(root,record['device'])
            self.assertEqual(['en-US'],entry['original_locales'])
            self.assertTrue(entry['locale_change_attempted'])
            self.assertEqual('null',entry['original_settings']['locales'])
            self.assertEqual(ledger,m.read_json(root/'matrix.json'))

    def test_recovery_rejects_changed_boot_fabricated_original_and_bad_probe_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);ledger,record=self.recovery_fixture(root)
            with self.assertRaises(m.runner.Blocked):m.recovery_entry(root,dict(record['device'],boot_id='new'))
            ledger['devices']['26']['original_locales']=['en-US','es-ES'];m.atomic(root/'matrix.json',ledger)
            with self.assertRaises(m.runner.Blocked):m.recovery_entry(root,record['device'])
            ledger['devices']['26']['original_locales']=['en-US'];m.atomic(root/'matrix.json',ledger)
            (root/'probe/before.json').write_text('{}')
            with self.assertRaises(ValueError):m.recovery_entry(root,record['device'])

    def test_restoration_metadata_pass_without_valid_evidence_is_blocked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            self.assertEqual('BLOCKED',m.restoration_verdict(root,{},dict(restoration='PASS',restoration_path='missing')))
            m.atomic(root/'matrix.json',dict(profile='api26-spanish-pilot',devices={},configurations={},recovery=dict(restoration='PASS')))
            result=m.report(root)
            self.assertEqual('BLOCKED',result['status']);self.assertEqual('BLOCKED',result['recovery_status'])
            self.assertEqual(3,result['expected_primary_executions'])

    def test_failed_restore_assertion_retains_fail_independently_of_blocked_matrix(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);folder=root/'restore';folder.mkdir()
            (folder/'instrumentation.txt').write_text(raw({m.LOCALE},failed=True))
            m.atomic(folder/'execution.json',dict(exit=0))
            recovery=dict(restoration='FAIL',restoration_path='restore')
            m.atomic(root/'matrix.json',dict(profile='api26-spanish-pilot',devices={},configurations={},recovery=recovery))
            result=m.report(root)
            self.assertEqual('FAIL',result['recovery_status']);self.assertEqual('FAIL',result['status'])

    def pilot_workflow(self, root, recovery_status, fail=False):
        out=root/'build/j-m3-b2b/pilot'
        for name in ('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):
            f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(b'apk')
        identity=dict(serial='emulator-5554',api='26',avd='Tablet2')
        capture=dict(branch='test/mvp-stabilization-j',revision='new',tree='tree',source_digest='digest',build={},dirty=True)
        c=m.Coordinator('adb');events=[]
        def baseline(*args):
            events.append('probe');entry=dict(identity=identity,original_locales=['en-US'],locale_change_attempted=False)
            args[2]['devices']['26']=entry;return entry
        def restore(c,d,entry):
            events.append('restore');entry['restoration']=recovery_status;return recovery_status
        def stage(device,expected,tags,action,directory):
            events.append((action,expected,tags));return dict(status='FAIL' if fail else 'PASS')
        with patch.object(m,'ROOT',root),patch.object(m,'source',return_value=capture), \
             patch.object(m.runner,'execute',return_value=type('Result',(),dict(returncode=0,stdout='',stderr=''))()), \
             patch.object(c,'discover',return_value={26:identity}),patch.object(c,'install'), \
             patch.object(m,'recovery_entry',return_value=dict(identity=identity)), \
             patch.object(m,'capture_baseline',side_effect=baseline),patch.object(m,'restore_device',side_effect=restore), \
             patch.object(c,'stage',side_effect=stage),patch.object(m,'report',return_value={'status':'PASS'}):
            try: result=m.workflow(c,out,pilot=True,recovery_from=root/'old')
            except (m.runner.Blocked,m.runner.FunctionalFailure):result=None
        return events,result

    def test_pilot_recovers_before_probe_then_only_spanish_three_methods_and_restores(self):
        with tempfile.TemporaryDirectory() as tmp:
            events,result=self.pilot_workflow(Path(tmp),'PASS')
            self.assertEqual(['restore','probe',('change',{m.LOCALE},['es-ES']),
                              ('observe',{m.READER,m.ev.SMOKE},['es-ES']),'restore'],events)
            self.assertEqual('PASS',result['status'])

    def test_pilot_failed_recovery_prevents_baseline_and_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            events,result=self.pilot_workflow(Path(tmp),'BLOCKED')
            self.assertEqual(['restore'],events);self.assertIsNone(result)

    def test_pilot_functional_failure_is_single_attempt_and_still_restores(self):
        with tempfile.TemporaryDirectory() as tmp:
            events,result=self.pilot_workflow(Path(tmp),'PASS',fail=True)
            self.assertEqual(['restore','probe',('change',{m.LOCALE},['es-ES']),'restore'],events)
            self.assertIsNone(result)

    def test_interaction_capability_failures_never_pass_complete_junit(self):
        for reason in ('Primary locale not applied: es-ES','Unsupported remove-language menu',
                       'Missing language removal confirmation','Drag injection rejected'):
            self.assertEqual('BLOCKED',m.classify(raw({m.LOCALE},failed=True,marker='J_LOCALE_BLOCKED: '+reason),{m.LOCALE},0)[0])

    def test_exact_en_us_restoration_rejects_remaining_spanish(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)/'restore';record=self.fixture_stage(folder)
            record['action']='restore';record['tags']=['en-US']
            record['command'][record['command'].index('jLocaleAction')+1]='restore'
            record['command'][record['command'].index('jLocaleTags')+1]='en-US'
            record['settings_after']['locales']='en-US'
            for name in ('before','returned-to-existing-app','library-visible','recreated-settings-visible'):
                (folder/(name+'.json')).write_text(json.dumps(dict(systemLocales=['en-US'],pid=1)))
                record['files'][name+'.json']=m.digest(folder/(name+'.json'))
            m.atomic(folder/'execution.json',record)
            self.assertEqual('PASS',m.validate_stage(folder,{m.LOCALE})['status'])
            for actual in (['en-US','es-ES'],['es-ES']):
                (folder/'library-visible.json').write_text(json.dumps(dict(systemLocales=actual,pid=1)))
                record['files']['library-visible.json']=m.digest(folder/'library-visible.json')
                m.atomic(folder/'execution.json',record)
                with self.assertRaises(ValueError):m.validate_stage(folder,{m.LOCALE})



class ManualLocaleTests(unittest.TestCase):
    identity=dict(serial='emulator-5554',avd='Tablet2',api='26',abi='arm64-v8a',qemu='1',boot='1',boot_id='same-boot')
    capture=dict(branch='test/mvp-stabilization-j',revision='current',tree='tree',source_digest='digest',build={},dirty=True)

    def settings(self,tags):
        return dict(locales='null',config='config: '+','.join(t.replace('-','-r') for t in tags)+'-ldltr-v26',wifi='1',airplane='0',mobile='1')

    def ledger(self):
        entry=dict(identity=self.identity,original_locales=['en-US'],original_settings=self.settings(['en-US']))
        return dict(source=self.capture,devices={'26':entry},configurations={},apks=dict(app='a',test='b')),entry

    def test_primary_locale_accepts_secondary_languages(self):
        examples=[
            (['es-ES'], ['es-ES','en-US']),
            (['ca-ES'], ['ca-ES','es-ES']),
            (['en-US'], ['en-US','es-ES']),
            (['fr-FR','en-US'], ['fr-FR','en-US','es-ES']),
        ]
        for desired, actual in examples:
            self.assertEqual('VERIFIED',m.manual_configuration(self.settings(actual),desired)['status'])
        self.assertEqual('BLOCKED',m.manual_configuration(self.settings(['es-ES','en-US']),['en-US'])['status'])
        self.assertEqual('BLOCKED',m.manual_configuration(self.settings(['fr-FR','es-ES','en-US']),['fr-FR','en-US'])['status'])
        self.assertEqual('BLOCKED',m.manual_configuration(self.settings(['en-US','es-ES']),['en-US'],exact=True)['status'])

    def test_adb_gate_never_invents_instrumentation_or_secondary_language(self):
        result=m.manual_configuration(self.settings(['en-US']),['en-US'])
        self.assertEqual('VERIFIED',result['status']);self.assertEqual('NOT RUN',result['instrumentation'])
        for actual in (['fr-FR'],['en-US','fr-FR']):
            self.assertEqual('BLOCKED',m.manual_configuration(self.settings(actual),['fr-FR','en-US'])['status'])
        self.assertEqual('VERIFIED',m.manual_configuration(self.settings(['fr-FR','en-US']),['fr-FR','en-US'])['status'])

    def test_absent_malformed_and_contradictory_channels_block(self):
        for settings in ({},dict(locales='en-US'),dict(locales='en_US',config=self.settings(['en-US'])['config']),
                         dict(locales='es-ES',config=self.settings(['en-US'])['config'])):
            self.assertEqual('BLOCKED',m.manual_configuration(settings,['en-US'])['status'])

    def test_explicit_correction_requires_new_enter_and_preserves_failed_attempt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);ledger,entry=self.ledger();c=m.Coordinator('adb')
            with patch.object(c,'identity',return_value=self.identity),patch.object(c,'settings',side_effect=[self.settings(['en-US']),self.settings(['es-ES'])]),patch.object(m,'source',return_value=self.capture),patch.object(c,'stage') as stage:
                answers=iter(['','r',''])
                ref=m.manual_selection(c,root,ledger,entry,['es-ES'],1,3,'piloto',lambda prompt:next(answers))
                stage.assert_not_called()
            record=m.read_json(root/ref['path'])
            self.assertEqual(['BLOCKED','VERIFIED'],[a['evidence']['status'] for a in record['attempts']])
            self.assertTrue(all(a['owner_confirmation_at'] for a in record['attempts']))

    def test_cancel_and_interruption_preserve_evidence_without_junit(self):
        for response in ('q',KeyboardInterrupt(),EOFError()):
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);ledger,entry=self.ledger();c=m.Coordinator('adb')
                def ask(prompt):
                    if isinstance(response,BaseException):raise response
                    return response
                with patch.object(c,'stage') as stage:
                    with self.assertRaises(m.runner.Blocked):m.manual_selection(c,root,ledger,entry,['es-ES'],1,3,'piloto',ask)
                    stage.assert_not_called()
                self.assertEqual('BLOCKED',m.read_json(root/entry['selection_attempts'][0]['path'])['status'])

    def test_wrong_selection_blocks_before_junit_and_apk_access(self):
        with tempfile.TemporaryDirectory() as tmp:
            ledger,entry=self.ledger();c=m.Coordinator('adb')
            with patch.object(m,'source',return_value=self.capture),patch.object(c,'identity',return_value=self.identity),patch.object(c,'settings',return_value=self.settings(['en-US'])),patch.object(c,'installed') as apk,patch.object(c,'stage') as stage:
                with self.assertRaises(m.runner.Blocked):m.manual_stage(c,Path(tmp),ledger,entry,['es-ES'],{m.LOCALE},'never')
                stage.assert_not_called();apk.assert_not_called()

    def test_manual_junit_evidence_requires_manual_observation_not_live_return(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)/'stage';record=LocaleCoordinatorTests().fixture_stage(root)
            record['action']='manual';record['command'][record['command'].index('jLocaleAction')+1]='manual'
            record['settings_before']['locales']='fr-FR,en-US'
            for suffix in ('.json','.png','.xml'):
                file=root/('manual-configuration'+suffix)
                if suffix=='.json':file.write_text(json.dumps(dict(systemLocales=['fr-FR','en-US'],liveTransition='NOT RUN')))
                elif suffix=='.png':file.write_bytes(b'\x89PNG\r\n\x1a\nsynthetic')
                else:file.write_text('<hierarchy/>')
                record['files'][file.name]=m.digest(file)
            for name in ('returned-to-existing-app.json','before.json'):
                (root/name).unlink();record['files'].pop(name)
            m.atomic(root/'execution.json',record)
            self.assertEqual('PASS',m.validate_stage(root,{m.LOCALE})['status'])
            file=root/'manual-configuration.json';file.write_text(json.dumps(dict(systemLocales=['fr-FR','en-US'],liveTransition='PASS')))
            record['files'][file.name]=m.digest(file);m.atomic(root/'execution.json',record)
            with self.assertRaises(ValueError):m.validate_stage(root,{m.LOCALE})

    def test_manual_correction_attempts_are_bounded(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);ledger,entry=self.ledger();c=m.Coordinator('adb');answers=iter(['','r','','r',''])
            with patch.object(m,'source',return_value=self.capture),patch.object(c,'identity',return_value=self.identity),patch.object(c,'settings',return_value=self.settings(['en-US'])) as observed:
                with self.assertRaises(m.runner.Blocked):m.manual_selection(c,root,ledger,entry,['es-ES'],1,3,'piloto',lambda prompt:next(answers))
                self.assertEqual(3,observed.call_count)
            record=m.read_json(root/entry['selection_attempts'][0]['path'])
            self.assertEqual('BLOCKED',record['status']);self.assertEqual(3,len(record['attempts']))

    def test_manual_selection_hash_and_attribution_cannot_be_bypassed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);ledger,entry=self.ledger();c=m.Coordinator('adb')
            with patch.object(m,'source',return_value=self.capture),patch.object(c,'identity',return_value=self.identity),patch.object(c,'settings',return_value=self.settings(['es-ES'])):
                ref=m.manual_selection(c,root,ledger,entry,['es-ES'],1,3,'piloto',lambda prompt:'')
            ref['sha256']=m.digest(root/ref['path'])
            self.assertEqual('VERIFIED',m.validate_manual_selection(root,ledger,entry,['es-ES'],ref)['status'])
            record=m.read_json(root/ref['path']);record['attempts'][-1]['owner_confirmation_at']=None
            m.atomic(root/ref['path'],record)
            with self.assertRaises(ValueError):m.validate_manual_selection(root,ledger,entry,['es-ES'],ref)
            ref['sha256']=m.digest(root/ref['path'])
            with self.assertRaises(ValueError):m.validate_manual_selection(root,ledger,entry,['es-ES'],ref)

    def test_manual_instrumentation_branch_returns_before_automatic_settings(self):
        source=(m.ROOT/'app/src/androidTest/java/com/abdev/partituraspdf/app/integration/LocaleMatrixUi.kt').read_text()
        branch=source.split('if (arguments.getString("jLocaleAction") == "manual") {',1)[1].split('        try {',1)[0]
        self.assertIn('locales() != target',branch);self.assertIn('return',branch)
        for forbidden in ('openSettings(','.click(','.drag(','promote(', 'removeExtra('):self.assertNotIn(forbidden,branch)

    def manual_pilot_sequence(self, failure=None):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out=root/'build/j-m3-b2b/manual';ledger,entry=self.ledger();c=m.Coordinator('adb');events=[]
            current=['en-US','es-ES'];steps=iter([['es-ES','en-US'],['en-US','es-ES']])
            def ask(prompt):
                current[:]=next(steps);events.append(('owner',list(current)));return ''
            def stage(device,expected,tags,action,directory):
                events.append((action,expected,list(tags)))
                if tags and tags[0]=='es-ES' and expected=={m.LOCALE}:
                    if failure=='interrupt':raise KeyboardInterrupt
                    if failure=='functional':return {'status':'FAIL'}
                return {'status':'PASS'}
            for name in ('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):
                f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(b'apk')
            # New manual pilot captures today's locale baseline.
            with patch.object(m,'ROOT',root),patch.object(m,'source',return_value=self.capture),patch.object(m.runner,'execute',return_value=type('Result',(),dict(returncode=0,stdout='',stderr=''))()),patch.object(c,'discover',return_value={26:self.identity}),patch.object(c,'install'),patch.object(c,'identity',return_value=self.identity),patch.object(c,'settings',side_effect=lambda serial:self.settings(current)),patch.object(c,'installed',return_value=dict(app=m.digest(root/'app/build/outputs/apk/debug/app-debug.apk'),test=m.digest(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))),patch.object(c,'stage',side_effect=stage),patch.object(m,'validate_stage',side_effect=lambda folder,expected:dict(source=self.capture,installed=dict(app=m.digest(root/'app/build/outputs/apk/debug/app-debug.apk'),test=m.digest(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')))),patch.object(m,'manual_report',return_value=dict(status='PASS',restorations={'26':'PASS'})):
                if failure:
                    with self.assertRaises(KeyboardInterrupt if failure=='interrupt' else m.runner.FunctionalFailure):
                        m.manual_workflow(c,out,pilot=True,ask=ask)
                else:self.assertEqual('PASS',m.manual_workflow(c,out,pilot=True,recovery_from=root/'old',ask=ask)['status'])
            return events

    def test_manual_pilot_sequence_recovers_original_and_restores_without_auto_actions(self):
        events=self.manual_pilot_sequence()
        self.assertEqual([('manual',{m.LOCALE},['en-US','es-ES']),('owner',['es-ES','en-US']),
                              ('manual',{m.LOCALE},['es-ES','en-US']),('manual',{m.READER,m.ev.SMOKE},['es-ES','en-US']),
                              ('owner',['en-US','es-ES']),('manual',{m.LOCALE},['en-US','es-ES'])],events)

    def test_functional_failure_is_not_retried_but_final_manual_restoration_runs(self):
        events=self.manual_pilot_sequence('functional')
        self.assertEqual(1,events.count(('manual',{m.LOCALE},['es-ES','en-US'])))
        self.assertNotIn(('manual',{m.READER,m.ev.SMOKE},['es-ES','en-US']),events)
        self.assertEqual([('owner',['en-US','es-ES']),('manual',{m.LOCALE},['en-US','es-ES'])],events[-2:])

    def test_interrupted_junit_retains_final_manual_restoration_opportunity(self):
        events=self.manual_pilot_sequence('interrupt')
        self.assertEqual([('owner',['en-US','es-ES']),('manual',{m.LOCALE},['en-US','es-ES'])],events[-2:])

    def test_manual_restore_failure_keeps_fail_exit_even_when_operation_is_blocked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out=root/'build/j-m3-b2b/run';out.mkdir(parents=True)
            m.atomic(out/'matrix.json',{})
            for verdict,expected in [('FAIL',1),('BLOCKED',2)]:
                with patch.object(m,'ROOT',root),patch.object(m.platform,'system',return_value='Darwin'),patch.object(m.runner,'native_architecture',return_value='arm64'),patch.object(m,'manual_workflow',side_effect=m.runner.Blocked('interrupted operation')),patch.object(m,'report',return_value=dict(status=verdict)):
                    self.assertEqual(expected,m.main(['manual-run','--output',str(out)]))

    def test_v2_report_preserves_functional_pass_when_cleanup_is_blocked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);ledger,entry=self.ledger()
            ledger.update(profile='manual-api26-spanish-pilot',manual_policy='primary-prefix-v2')
            entry.update(baseline_path='baseline',restoration_status='PENDING')
            ledger['configurations']['26-es-ES']=dict(locale='locale',tests='tests',selection={'path':'selection','sha256':'x'})
            stage=dict(action='manual',device=self.identity,installed=ledger['apks'],
                       source=self.capture,tags=['es-ES','en-US'])
            baseline=dict(stage,tags=['en-US'],action='manual')
            select=dict(attempts=[dict(evidence=dict(locales=['es-ES','en-US']))])
            def validated(folder,expected):
                return baseline if folder.name=='baseline' else stage
            with patch.object(m,'validate_stage',side_effect=validated), \
                 patch.object(m,'read_json',return_value={'systemLocales':['en-US']}), \
                 patch.object(m,'require_locale_evidence'), \
                 patch.object(m,'validate_manual_selection',return_value=select), \
                 patch.object(m,'manual_verified_record',return_value=stage):
                report=m.manual_report(root,ledger)
            self.assertEqual('PASS',report['functional_status'])
            self.assertEqual('BLOCKED',report['restoration_status'])
            self.assertEqual('BLOCKED',report['status'])
            self.assertEqual(3,report['verified_primary_executions'])

    def test_manual_report_keeps_localization_and_live_transition_separate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);ledger,entry=self.ledger();ledger['profile']='manual-api26-spanish-pilot'
            entry.update(recovery_path='recovery',recovery_selection={},restoration_path='restore',restoration_selection={})
            ledger['configurations']['26-es-ES']=dict(locale='locale',tests='tests',selection={})
            record=dict(device=self.identity,tags=['es-ES'],installed=ledger['apks'],source=self.capture,action='manual')
            with patch.object(m,'validate_manual_selection'),patch.object(m,'manual_verified_record',return_value=record),patch.object(m,'validate_stage',return_value=record):
                result=m.manual_report(root,ledger)
            self.assertEqual('PASS',result['status']);self.assertEqual('PASS',result['configurations']['26-es-ES']['localization'])
            self.assertEqual('NOT RUN',result['live_transition']);self.assertEqual('NOT RUN',result['configurations']['26-es-ES']['live_transition'])

if __name__=='__main__':unittest.main()
