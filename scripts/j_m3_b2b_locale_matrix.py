"""Opt-in sequential locale coordinator; reuses Phase J execution and evidence helpers.

Only run/resume/restore write to the three owner-named disposable emulators.
No provisioning, resets, shell locale setters, or network changes are implemented.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import platform
import sys
import uuid
from datetime import datetime, timezone
import xml.etree.ElementTree as ET

import local_e2e as runner
import local_test_evidence as ev

ROOT = Path(__file__).resolve().parents[1]
DEVICES = {26: 'Tablet2', 27: 'Tablet_27_Edited', 36: 'Tablet_36'}
TAGS = ('es-ES', 'ca-ES', 'en-US', 'fr-FR')
LOCALE = ev.APP + '.app.integration.AppLocaleIntegrationTest.productionActivityUsesActualSystemPolicyAndRecreationRetainsHostAndLocalizedControls'
READER = ev.APP + '.app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition'
EXPECTED = {LOCALE, READER, ev.SMOKE}
COMPONENT = ev.APP + '.test/' + ev.NORMAL_RUNNER


def now():
    return datetime.now(timezone.utc).isoformat()


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def atomic(path, value):
    ev.j_atomic(path.parent, path.name, json.dumps(value, indent=2))


def confined(root, relative):
    return ev.j_path(root, relative)


def read_json(path):
    return json.loads(path.read_text())


def ordered_locales(raw):
    """Parse explicit BCP47 language lists, never supply a missing language/region."""
    if not isinstance(raw, list) or not raw or any(not isinstance(t, str) for t in raw):
        raise ValueError('Missing/malformed ordered instrumentation locales')
    tags=[]
    for value in raw:
        if not re.fullmatch(r'[a-zA-Z]{2,3}(?:-[a-zA-Z]{4})?(?:-(?:[a-zA-Z]{2}|[0-9]{3}))?', value):
            raise ValueError('Malformed explicit locale tag')
        pieces=value.split('-')
        tags.append('-'.join([pieces[0].lower(), *[x.title() if len(x)==4 else x.upper() for x in pieces[1:]]]))
    if len(tags)!=len(set(tags)):raise ValueError('Duplicate ordered locale')
    return tags


def database_locales(raw):
    channel=dict(raw=raw, state='UNAVAILABLE', locales=None)
    if raw is None or (isinstance(raw,str) and raw.strip() in ('', 'null')):
        return channel
    try:
        if not isinstance(raw,str):raise ValueError('Non-text setting')
        channel.update(state='EXPLICIT',locales=ordered_locales(raw.strip().split(',')))
    except ValueError as error:channel.update(state='MALFORMED',reason=str(error))
    return channel


def effective_locales(raw):
    """Read explicit Android resource qualifiers in order; no substring/default inference.

    Supported AOSP form: config: [mcc...-][mnc...-]en-rUS,fr-rFR-ldltr-...
    BCP47 resource qualifiers (b+zh+Hans+CN) are also explicit. Unknown forms block
    when no other independent explicit channel establishes the full ordering.
    """
    channel=dict(raw=raw,state='UNAVAILABLE',locales=None)
    if raw is None or raw=='':return channel
    if not isinstance(raw,str):return dict(channel,state='MALFORMED',reason='Non-text effective configuration')
    lines=re.findall(r'(?m)^config:\s*(\S+)\s*$',raw)
    if len(lines)!=1:return dict(channel,state='MALFORMED',reason='Missing/duplicate config line')
    body=re.sub(r'^(?:mcc[0-9]+-)?(?:mnc[0-9]+-)?','',lines[0])
    # Layout direction delimits the locale list in the actual am get-config output.
    match=re.match(r'^(.*?)-(?:ldltr|ldrtl)(?:-|$)',body)
    if not match:
        return dict(channel,state='UNAVAILABLE',reason='No supported full locale qualifier boundary')
    locales=match[1]
    if not locales:
        return dict(channel,reason='No explicit effective locale')
    try:
        tags=[]
        for qualifier in locales.split(','):
            if qualifier.startswith('b+'):
                tag=qualifier[2:].replace('+','-')
            else:
                if not re.fullmatch(r'[a-zA-Z]{2,3}(?:-r(?:[A-Z]{2}|[0-9]{3}))?',qualifier):
                    raise ValueError('Unsupported/malformed locale qualifier')
                tag=qualifier.replace('-r','-')
            tags.append(tag)
        channel.update(state='EXPLICIT',locales=ordered_locales(tags))
    except ValueError as error:channel.update(state='MALFORMED',reason=str(error))
    return channel


def locale_evidence(settings, observed, expected=None):
    """Require instrumentation plus an independently explicit full ordered channel.

    Missing DB values are unavailable, not contradictory and never a default.
    Primary-only evidence cannot certify a French secondary English locale.
    """
    channels=dict(database=database_locales(settings.get('locales')),
                  effective_configuration=effective_locales(settings.get('config')),
                  instrumentation=dict(raw=observed,state='UNAVAILABLE' if observed is None else 'MALFORMED',locales=None))
    result=dict(status='BLOCKED',channels=channels)
    try:
        actual=ordered_locales(observed)
        channels['instrumentation'].update(state='EXPLICIT',locales=actual)
        if expected is not None and actual!=ordered_locales(expected):
            raise ValueError('Instrumentation does not match exact required locale order')
        independent=[]
        for name in ('database','effective_configuration'):
            channel=channels[name]
            if channel['state']=='MALFORMED':raise ValueError(name+': malformed locale evidence')
            if channel['state']=='EXPLICIT':
                if channel['locales']!=actual:
                    channel['state']='CONTRADICTORY'
                    raise ValueError(name+': explicit ordered locales contradict instrumentation')
                independent.append(name)
        if not independent:
            raise ValueError('No independent explicit full ordered locale channel; Settings UI evidence required')
        result.update(status='VERIFIED',locales=actual,verified_channels=independent,reason=None)
    except ValueError as error:result['reason']=str(error)
    return result


def require_locale_evidence(settings, observed, expected=None):
    evidence=locale_evidence(settings,observed,expected)
    if evidence['status']!='VERIFIED':raise ValueError(evidence['reason'])
    return evidence


def classify(output, expected, code):
    """Preserve complete functional failures, block partial or unexplained process errors."""
    try:
        cases = ev.parse_android(output, expected)
    except ev.IncompleteInstrumentation as error:
        return 'BLOCKED', error.cases, str(error)
    if 'J_LOCALE_BLOCKED:' in output:
        return 'BLOCKED', cases, 'Unsupported or unverified Settings capability'
    if any(c['status'] == 'FAIL' for c in cases):
        return 'FAIL', cases, None
    if 'J_LOCALE_BLOCKED:' in output or code:
        return 'BLOCKED', cases, 'Incomplete locale UI capability or nonzero process exit'
    return 'PASS', cases, None


def source():
    return ev.j_capture(ROOT, uuid.uuid4().hex)


def same_source(a, b):
    return all(a[k] == b[k] for k in ('revision', 'branch', 'tree', 'source_digest', 'build', 'dirty'))


class Coordinator:
    def __init__(self, adb):
        self.adb = str(adb)

    def command(self, *args, binary=False, timeout=30):
        result = runner.execute([self.adb, *args], binary=binary, timeout=timeout)
        if result.returncode:
            raise runner.Blocked(f'ADB command failed: {args}: {result.stderr}')
        return result.stdout if binary else result.stdout.strip()

    def discover(self):
        text = self.command('devices', '-l')
        found = {}
        for line in text.splitlines():
            parts = line.split()
            if len(parts) < 2 or parts[1] != 'device' or not re.fullmatch(r'emulator-\d+', parts[0]):
                continue
            serial = parts[0]
            name = self.command('-s', serial, 'emu', 'avd', 'name').replace('\r', '').splitlines()[0]
            if name not in DEVICES.values():
                continue # Never adopt an unrelated emulator or physical device.
            api = next(api for api, expected in DEVICES.items() if name == expected)
            if api in found:
                raise runner.Blocked('Duplicate target AVD identity')
            found[api] = self.identity(serial, api)
        return found

    def identity(self, serial, api):
        if not re.fullmatch(r'emulator-\d+', serial) or api not in DEVICES:
            raise runner.Blocked('Invalid selected emulator')
        def prop(key):
            return self.command('-s', serial, 'shell', 'getprop', key)
        name = self.command('-s', serial, 'emu', 'avd', 'name').replace('\r', '').splitlines()[0]
        result = dict(serial=serial, avd=name, api=prop('ro.build.version.sdk'),
                      abi=prop('ro.product.cpu.abi'), qemu=prop('ro.kernel.qemu'),
                      boot=prop('sys.boot_completed'), boot_id=self.command('-s', serial, 'shell', 'cat', '/proc/sys/kernel/random/boot_id'))
        if name != DEVICES[api] or result['api'] != str(api) or result['abi'] != 'arm64-v8a' or result['qemu'] != '1' or result['boot'] != '1':
            raise runner.Blocked('Target identity, native ABI or boot mismatch')
        return result

    def settings(self, serial):
        def setting(namespace, key):
            return self.command('-s', serial, 'shell', 'settings', 'get', namespace, key)
        return dict(locales=setting('system', 'system_locales'),
                    config=self.command('-s', serial, 'shell', 'am', 'get-config'),
                    wifi=setting('global', 'wifi_on'), airplane=setting('global', 'airplane_mode_on'),
                    mobile=setting('global', 'mobile_data'))

    def installed(self, serial):
        values = {}
        for key, package in [('app', ev.APP), ('test', ev.APP + '.test')]:
            paths = self.command('-s', serial, 'shell', 'pm', 'path', package).splitlines()
            if len(paths) != 1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~-]+/base.apk', paths[0]):
                raise runner.Blocked('Expected one installed base APK, not split/unknown paths')
            data = self.command('-s', serial, 'exec-out', 'cat', paths[0][8:], binary=True, timeout=120)
            values[key] = hashlib.sha256(data).hexdigest()
        return values

    def install(self, device, apks, audit):
        # Recheck before every write; never replay uncertain installation replies.
        serial=device['serial'];api=int(device['api'])
        record=dict(status='BLOCKED',device=device,started=now(),host_apks={k:digest(p) for k,p in apks.items()},actions=[])
        try:
            for path in apks.values():
                if self.identity(serial,api)!=device:raise runner.Blocked('Device changed before install')
                output=self.command('-s',serial,'install','-r',str(path),timeout=120)
                record['actions'].append(dict(apk=path.name,output=output))
            installed=self.installed(serial)
            if installed!=record['host_apks']:raise runner.Blocked('Installed APK bytes differ from matching host APKs')
            registered=self.command('-s',serial,'shell','pm','list','instrumentation')
            matches=re.findall(r'instrumentation:(\S+) \(target='+re.escape(ev.APP)+r'\)',registered)
            if matches!=[COMPONENT]:raise runner.Blocked('Wrong or ambiguous installed instrumentation')
            record.update(status='PASS',installed=installed,instrumentation=registered)
            return installed
        except (runner.Blocked,OSError,ValueError) as error:
            record['error']=str(error);raise
        finally:
            record['finished']=now();atomic(audit,record)

    def collect(self, serial, token, directory):
        # Only this invocation's confined test-owned files; no arbitrary pull or guest deletion.
        remote = 'files/j-locale-' + token
        listing = self.command('-s', serial, 'shell', 'run-as', ev.APP, 'ls', remote)
        for name in listing.splitlines():
            if not re.fullmatch(r'[a-z0-9-]+\.(png|xml|json)', name):
                raise runner.Blocked('Unsafe locale evidence filename')
            data = self.command('-s', serial, 'exec-out', 'run-as', ev.APP, 'cat', remote + '/' + name, binary=True)
            (directory / name).write_bytes(data)

    def stage(self, device, expected, tags, action, directory):
        directory.mkdir(parents=True, exist_ok=False)
        serial, api = device['serial'], int(device['api'])
        token = uuid.uuid4().hex
        metadata = dict(status='BLOCKED', started=now(), expected=sorted(expected), device=device,
                        tags=tags, action=action, token=token, source=source())
        cases, blocker = [], None
        try:
            if self.identity(serial, api) != device:
                raise runner.Blocked('Emulator restarted/replaced before stage')
            processes=self.command('-s',serial,'shell','dumpsys','activity','processes')
            if re.search(r'(?im)^.*(?:mActiveInstrumentation|mInstrumentation|mInstr=).*'+re.escape(ev.APP),processes):
                raise runner.Blocked('Previous instrumentation may still be running; no concurrent recovery launch')
            metadata['settings_before'] = self.settings(serial)
            command = [self.adb, '-s', serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                       ','.join(m.rsplit('.', 1)[0] + '#' + m.rsplit('.', 1)[1] for m in sorted(expected)),
                       '-e', 'jLocaleRun', token, '-e', 'jLocaleTags', ','.join(tags), '-e', 'jLocaleAction', action, COMPONENT]
            metadata['command'] = command
            result = runner.execute(command, timeout=1200)
            raw = result.stdout + '\n' + result.stderr
            (directory / 'instrumentation.txt').write_text(raw)
            metadata['exit'] = result.returncode
            metadata['status'], cases, blocker = classify(raw, expected, result.returncode)
            # Capability absence is BLOCKED even though the instrumentation uses an AssertionError.
            if 'J_LOCALE_BLOCKED:' in raw:
                metadata['status'], blocker = 'BLOCKED', 'Unsupported/unverified real Settings UI; inspect live artifacts'
            if self.identity(serial, api) != device:
                raise runner.Blocked('Android restart: no genuine existing-application transition proven')
            metadata['settings_after'] = self.settings(serial)
            if any(metadata['settings_before'][k] != metadata['settings_after'][k] for k in ('wifi','airplane','mobile')):
                raise runner.Blocked('Unexpected network setting change')
            self.collect(serial, token, directory)
            metadata['installed'] = self.installed(serial)
            if not same_source(metadata['source'], source()):
                raise runner.Blocked('Source changed during instrumentation')
            if metadata['status'] == 'PASS':
                names = ['library-visible', 'recreated-settings-visible'] if expected == {LOCALE} else ['reader-visible', 'reader-menu-page', 'reader-menu-width']
                metadata['locale_evidence']={}
                for name in names:
                    observation = read_json(directory / (name + '.json'))
                    evidence=locale_evidence(metadata['settings_after'],observation.get('systemLocales'),None if action=='probe' else tags)
                    metadata['locale_evidence'][name]=evidence
                    if evidence['status']!='VERIFIED':raise runner.Blocked(evidence['reason'])
                    for suffix in ('.png','.xml'):
                        if not (directory / (name + suffix)).is_file() or not (directory / (name + suffix)).stat().st_size:
                            raise runner.Blocked('Missing live UI observation')
                if action != 'probe' and expected == {LOCALE} and not (directory/'returned-to-existing-app.json').exists():
                    raise runner.Blocked('No genuine return observation')
        except (runner.Blocked, OSError, ValueError, KeyError, KeyboardInterrupt) as error:
            blocker = str(error) or 'Interrupted; process output may be incomplete'
            if not (directory/'instrumentation.txt').exists() and '\n' in blocker:
                partial=blocker.split('\n',1)[1]
                (directory/'instrumentation.txt').write_text(partial)
                try: cases=ev.parse_android(partial,expected)
                except ev.IncompleteInstrumentation as incomplete: cases=incomplete.cases
            if metadata['status'] != 'FAIL': metadata['status'] = 'BLOCKED'
            (directory / 'infrastructure.txt').write_text(blocker)
            try:
                if self.identity(serial, api) == device: self.collect(serial, token, directory)
            except (runner.Blocked, OSError, ValueError) as collection_error:
                (directory / 'collection-error.txt').write_text(str(collection_error))
        finally:
            metadata.update(finished=now(), blocker=blocker)
            ev.write_junit(directory/'junit.xml', 'j-locale-stage', cases, blocker)
            metadata['files'] = {p.name:digest(p) for p in directory.iterdir() if p.is_file() and p.name != 'execution.json'}
            atomic(directory/'execution.json', metadata)
        return metadata


def validate_stage(directory, expected):
    record = read_json(directory/'execution.json')
    if record['expected'] != sorted(expected): raise ValueError('Stage selection changed')
    if record['status'] != 'PASS' or not re.fullmatch(r'[a-f0-9]{32}',record['token']): raise ValueError('Unvalidated stage')
    if not {'instrumentation.txt','junit.xml'} <= set(record['files']):raise ValueError('Raw/JUnit missing from evidence hashes')
    for name, sha in record['files'].items():
        if digest(confined(directory, name)) != sha: raise ValueError('Evidence hash mismatch')
    raw = (directory/'instrumentation.txt').read_text()
    status, cases, _ = classify(raw, expected, record['exit'])
    if status != 'PASS' or record['status'] != 'PASS' or 'J_LOCALE_BLOCKED:' in raw: raise ValueError('Not a validated PASS')
    xml = ET.parse(directory/'junit.xml').getroot()
    if len(xml.findall('testcase'))!=len(expected) or any(xml.get(k)!=v for k,v in dict(tests=str(len(expected)),failures='0',errors='0',skipped='0').items()) or {n.get('classname')+'.'+n.get('name') for n in xml.findall('testcase')} != expected or any(xml.findall('.//'+n) for n in ('failure','error','skipped')):
        raise ValueError('XML/raw result disagreement')
    device=record['device']
    cmd=record['command']
    selected=','.join(m.rsplit('.',1)[0]+'#'+m.rsplit('.',1)[1] for m in sorted(expected))
    if cmd[1:11] != ['-s',device['serial'],'shell','am','instrument','-w','-r','-e','class',selected] or cmd[-1]!=COMPONENT:
        raise ValueError('Command inventory/identity mismatch')
    for key,value in [('jLocaleRun',record['token']),('jLocaleTags',','.join(record['tags'])),('jLocaleAction',record['action'])]:
        if cmd.count(key)!=1 or cmd[cmd.index(key)+1]!=value: raise ValueError('Command locale metadata mismatch')
    if device['avd']!=DEVICES[int(device['api'])] or device['abi']!='arm64-v8a' or device['qemu']!='1' or device['boot']!='1':
        raise ValueError('Device identity mismatch')
    for k in ('wifi','airplane','mobile'):
        if record['settings_before'][k]!=record['settings_after'][k]: raise ValueError('Network state changed')
    names=['library-visible','recreated-settings-visible'] if expected=={LOCALE} else ['reader-visible','reader-menu-page','reader-menu-width']
    for name in names:
        for suffix in ('.json','.png','.xml'):
            if name+suffix not in record['files']: raise ValueError('Observation missing from hash inventory')
        if not (directory/(name+'.png')).read_bytes().startswith(b'\x89PNG\r\n\x1a\n'): raise ValueError('Invalid screenshot')
        ET.parse(directory/(name+'.xml'))
        observation=read_json(directory/(name+'.json'))
        require_locale_evidence(record['settings_after'],observation.get('systemLocales'),None if record['action']=='probe' else record['tags'])
    if record['action']!='probe' and expected=={LOCALE}:
        if 'returned-to-existing-app.json' not in record['files']: raise ValueError('Missing existing-app return')
        if any(n not in record['files'] for n in ('before.json','returned-to-existing-app.json')):raise ValueError('Transition observation is not hash-bound')
        before=read_json(directory/'before.json');after=read_json(directory/'returned-to-existing-app.json')
        if before['pid']!=after['pid']: raise ValueError('Fresh process is not locale transition')
    return record


def retained_failure(directory, expected):
    try:
        record=read_json(directory/'execution.json')
        output=(directory/'instrumentation.txt').read_text()
        return classify(output,expected,record.get('exit',2))[0]=='FAIL'
    except (OSError,ValueError,KeyError):return False


def restoration_verdict(directory, ledger, entry):
    try:
        path=confined(directory,entry['restoration_path'])
        if retained_failure(path,{LOCALE}):return 'FAIL'
        record=validate_stage(path,{LOCALE})
        if (entry.get('restoration')!='PASS' or record['action']!='restore'
                or not same_source(ledger['source'],record['source']) or record['installed']!=ledger['apks']
                or record['tags']!=entry['original_locales'] or record['device']!=entry['identity']
                or any(record['settings_after'][k]!=entry['original_settings'][k] for k in ('wifi','airplane','mobile'))):
            return 'BLOCKED'
        return 'PASS'
    except (OSError,ValueError,KeyError):return 'BLOCKED'


def report(directory):
    ledger = read_json(directory/'matrix.json')
    verdicts = {}
    for key, item in ledger['configurations'].items():
        try:
            locale = validate_stage(confined(directory,item['locale']), {LOCALE})
            tests = validate_stage(confined(directory,item['tests']), {READER,ev.SMOKE})
            api,tag=key.split('-',1)
            wanted=[tag] if tag!='fr-FR' else ['fr-FR','en-US']
            if tag not in TAGS or int(api) not in DEVICES or locale['device']!=ledger['devices'][api]['identity'] or tests['device']!=locale['device'] or locale['tags']!=wanted:
                raise ValueError('Wrong matrix cell/device')
            if locale['tags'] != tests['tags'] or locale['installed'] != ledger['apks'] or tests['installed'] != ledger['apks']:
                raise ValueError('Configuration/APK mismatch')
            if not same_source(ledger['source'],locale['source']) or not same_source(ledger['source'],tests['source']):
                raise ValueError('Source mismatch')
            original=read_json(confined(directory,item['locale'])/'before.json')['systemLocales']
            transition='PASS' if original!=wanted else 'NOT APPLICABLE: already target locale'
            verdicts[key] = dict(junit='PASS', visible='PASS', transition=transition, contextual_errors='BLOCKED')
        except (OSError,ValueError,KeyError) as error:
            verdicts[key] = dict(junit=item.get('status','BLOCKED'), visible='BLOCKED', transition='BLOCKED', reason=str(error))
            if any(retained_failure(confined(directory,item[k]),inventory) for k,inventory in [('locale',{LOCALE}),('tests',{READER,ev.SMOKE})] if k in item):
                verdicts[key]['junit']='FAIL'
            elif verdicts[key]['junit'] == 'PASS': verdicts[key]['junit'] = 'BLOCKED'
    pilot = ledger.get('profile') == 'api26-spanish-pilot'
    selected = {'26-es-ES'} if pilot else {f'{api}-{tag}' for api in DEVICES for tag in TAGS}
    restored = set(ledger['devices']) == ({'26'} if pilot else {'26','27','36'})
    restorations = {}
    for key, entry in ledger['devices'].items():
        restorations[key] = restoration_verdict(directory,ledger,entry)
        restored = restored and restorations[key]=='PASS'
    recovery = ledger.get('recovery')
    recovery_status = restoration_verdict(directory,ledger,recovery) if recovery else 'NOT REQUESTED'
    if pilot: restored = restored and recovery_status=='PASS'
    complete = set(verdicts)==selected and all(v['junit']=='PASS' and v['visible']=='PASS' for v in verdicts.values()) and restored
    return dict(status='PASS' if complete else 'FAIL' if any(v['junit']=='FAIL' for v in verdicts.values()) or 'FAIL' in restorations.values() or recovery_status=='FAIL' else 'BLOCKED',
                configurations=verdicts, restoration_recorded=restored, restorations=restorations,
                profile=ledger.get('profile','full-matrix'), recovery=ledger.get('recovery'), recovery_status=recovery_status, contextual_errors='BLOCKED',
                baselines={key:{k:entry.get(k) for k in ('identity','probe','baseline_verified','baseline_status','baseline_reason','baseline_evidence','original_settings','instrumentation_before','instrumentation_after','auxiliary_probe_completed','probe_status','probe_exit','locale_change_attempted','language_change_observed','restoration')} for key,entry in ledger['devices'].items()},
                expected_primary_executions=3 if pilot else 36, expected_methods_per_cell=3,
                verified_primary_executions=3*sum(v['junit']=='PASS' for v in verdicts.values()),
                boundary='Selected UI matrix only; not offline/AAB/physical/coverage acceptance')


def capture_baseline(coordinator, directory, ledger, identity):
    key=str(identity['api']);probe='probe-'+uuid.uuid4().hex
    entry=dict(identity=identity,probe=probe,baseline_verified=False,baseline_status='BLOCKED',
               auxiliary_probe_completed=False,locale_change_attempted=False,
               baseline_evidence=locale_evidence({},None),
               language_change_observed=None,restoration='NOT REQUIRED: no locale modification attempted')
    ledger['devices'][key]=entry
    atomic(directory/'matrix.json',ledger)
    try:
        entry['original_settings']=coordinator.settings(identity['serial'])
        entry['baseline_evidence']=locale_evidence(entry['original_settings'],None)
        atomic(directory/'matrix.json',ledger)
        result=coordinator.stage(identity,{LOCALE},['en-US'],'probe',directory/probe)
        probe_log=directory/probe/'instrumentation.txt'
        if probe_log.is_file():
            try:
                completed=ev.parse_android(probe_log.read_text(),{LOCALE})
                entry['auxiliary_probe_completed']=len(completed)==1
            except ev.IncompleteInstrumentation:pass
        entry['probe_status']=result.get('status','BLOCKED')
        entry['probe_exit']=result.get('exit')
        before=read_json(directory/probe/'before.json').get('systemLocales')
        after_path=directory/probe/'library-visible.json'
        after=read_json(after_path).get('systemLocales') if after_path.exists() else None
        entry['instrumentation_before']=before;entry['instrumentation_after']=after
        if before is not None and after is not None:
            entry['language_change_observed']=before!=after
        entry['baseline_evidence']=locale_evidence(entry['original_settings'],before)
        if not entry['auxiliary_probe_completed']:raise runner.Blocked('Auxiliary probe has no complete single-method output')
        if result.get('status')!='PASS':raise runner.Blocked('Original-state probe incomplete: '+str(result.get('blocker')))
        if after is None or ordered_locales(before)!=ordered_locales(after):
            raise runner.Blocked('Probe endpoints do not establish unchanged ordered languages')
        if entry['baseline_evidence']['status']!='VERIFIED':raise runner.Blocked(entry['baseline_evidence']['reason'])
        entry.update(original_locales=ordered_locales(before),baseline_verified=True,baseline_status='VERIFIED')
        return entry
    except (runner.Blocked,OSError,ValueError,KeyError,KeyboardInterrupt) as error:
        entry['baseline_reason']=str(error) or 'Original-state probe interrupted; locale modification not attempted'
        raise runner.Blocked(entry['baseline_reason']) from error
    finally:
        atomic(directory/'matrix.json',ledger)


def restore_device(coordinator, directory, entry):
    if not entry.get('locale_change_attempted',True):
        entry['restoration']='NOT REQUIRED: no locale modification attempted'
        return entry['restoration']
    tags = entry['original_locales']
    folder = 'restore-' + uuid.uuid4().hex
    result = coordinator.stage(entry['identity'], {LOCALE}, tags, 'restore', directory/folder)
    entry['restoration_path'] = folder
    entry['restoration'] = result['status']
    if result['status']=='PASS':
        try: result=validate_stage(directory/folder,{LOCALE})
        except (OSError,ValueError,KeyError) as error:
            entry.update(restoration='BLOCKED',restoration_error=str(error))
            return entry['restoration']
        if any(result['settings_after'][k] != entry['original_settings'][k] for k in ('wifi','airplane','mobile')):
            entry['restoration']='BLOCKED'
    return entry['restoration']


def recovery_entry(previous, identity):
    """Bind recovery to the old verified probe, not today's contaminated locale list."""
    ledger=read_json(previous/'matrix.json')
    entry=ledger['devices']['26']
    probe=validate_stage(confined(previous,entry['probe']),{LOCALE})
    if (entry['identity']!=identity or probe['device']!=identity or not entry.get('baseline_verified')
            or entry['original_locales']!=['en-US'] or not same_source(probe['source'],ledger['source'])
            or probe['installed']!=ledger['apks'] or probe['action']!='probe'):
        raise runner.Blocked('Recovery original/device/probe attribution mismatch')
    if 'before.json' not in probe['files']:raise runner.Blocked('Original probe endpoint is not hash-bound')
    if ledger['source']['branch']!='test/mvp-stabilization-j' or ledger['source']['dirty']:
        raise runner.Blocked('Original execution source was not the clean working branch')
    observed=read_json(confined(previous,entry['probe'])/'before.json')['systemLocales']
    require_locale_evidence(entry['original_settings'],observed,entry['original_locales'])
    return dict(identity=identity,original_locales=entry['original_locales'],
                original_settings=entry['original_settings'],locale_change_attempted=True,
                provenance=dict(previous_run=previous.name,matrix_sha256=digest(previous/'matrix.json'),
                                probe=entry['probe'],probe_execution_sha256=digest(confined(previous,entry['probe'])/'execution.json'),
                                source=ledger['source']))


def workflow(coordinator, directory, resume=False, pilot=False, recovery_from=None):
    capture = source()
    if capture['branch'] != 'test/mvp-stabilization-j': raise runner.Blocked('Wrong branch')
    apks = {k:ROOT/p for k,p in dict(app='app/build/outputs/apk/debug/app-debug.apk', test='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk').items()}
    if not resume:
        directory.mkdir(parents=True, exist_ok=False)
        build=runner.execute(['bash','./gradlew',':app:assembleDebug',':app:assembleDebugAndroidTest','--max-workers=4','--console=plain'],timeout=900)
        (directory/'build.txt').write_text(build.stdout+'\n'+build.stderr)
        if build.returncode: raise runner.Blocked('Matching APK compilation failed; see build.txt')
        if not same_source(capture,source()):raise runner.Blocked('Source changed during APK build')
    hashes = {k:digest(p) for k,p in apks.items()}
    if resume:
        ledger = read_json(directory/'matrix.json')
        if ledger.get('profile')=='api26-spanish-pilot': raise runner.Blocked('Pilot is single attempt; no resume into full matrix')
        if not same_source(ledger['source'],capture) or ledger['apks'] != hashes: raise runner.Blocked('Cannot resume different source/APKs')
    else:
        ledger = dict(schema=1, started=now(), source=capture, apks=hashes, devices={},configurations={})
        if pilot: ledger['profile']='api26-spanish-pilot'
    atomic(directory/'matrix.json',ledger)
    devices = coordinator.discover()
    selected_apis = (26,) if pilot else tuple(DEVICES)
    if not set(selected_apis) <= set(devices): raise runner.Blocked(f'Required named ARM64 APIs {selected_apis} must already be booted; none is started automatically')
    for api in selected_apis:
        identity = devices[api];key=str(api)
        entry=ledger['devices'].get(key)
        if entry and entry['identity'] != identity: raise runner.Blocked('Resume device/boot identity changed')
        if entry and not entry.get('baseline_verified',True):raise runner.Blocked('Prior baseline unverified; preserve it and start a new run')
        if entry and entry.get('restoration') != 'PASS' and entry.get('locale_change_attempted',True):
            if restore_device(coordinator,directory,entry)!='PASS':
                atomic(directory/'matrix.json',ledger);raise runner.Blocked('Restore previous interrupted device before proceeding')
        recovery=None
        if pilot:
            if recovery_from is None: raise runner.Blocked('Pilot requires retained original-state recovery evidence')
            recovery=recovery_entry(recovery_from,identity)
        coordinator.install(identity,apks,directory/('install-'+uuid.uuid4().hex+'.json'))
        if pilot:
            ledger['recovery']=recovery
            atomic(directory/'matrix.json',ledger)
            try:
                restore_device(coordinator,directory,recovery)
            finally:
                atomic(directory/'matrix.json',ledger)
            if recovery.get('restoration')!='PASS':raise runner.Blocked('Pre-pilot exact recovery unverified; matrix not started')
        if not entry:
            entry=capture_baseline(coordinator,directory,ledger,identity)
        entry['restoration']='PENDING';atomic(directory/'matrix.json',ledger)
        try:
            for tag in (('es-ES',) if pilot else TAGS):
                case=f'{api}-{tag}';old=ledger['configurations'].get(case)
                if old:
                    if old.get('status')=='FAIL' or any(retained_failure(confined(directory,old[k]),inventory) for k,inventory in [('locale',{LOCALE}),('tests',{READER,ev.SMOKE})] if k in old):
                        raise runner.FunctionalFailure('No automatic retry of failed assertions')
                    try:
                        previous_locale=validate_stage(confined(directory,old['locale']),{LOCALE})
                        previous_tests=validate_stage(confined(directory,old['tests']),{READER,ev.SMOKE})
                        wanted=[tag] if tag!='fr-FR' else ['fr-FR','en-US']
                        if previous_locale['device']!=identity or previous_tests['device']!=identity or previous_locale['tags']!=wanted or previous_tests['tags']!=wanted or previous_locale['installed']!=hashes or previous_tests['installed']!=hashes:
                            raise ValueError('Resume cell identity mismatch')
                        if not same_source(previous_locale['source'],capture) or not same_source(previous_tests['source'],capture):raise ValueError('Resume source changed')
                        continue
                    except (ValueError,OSError,KeyError): pass
                tags=[tag] if tag!='fr-FR' else ['fr-FR','en-US']
                prefix=case+'-'+uuid.uuid4().hex
                item=dict(status='BLOCKED',locale=prefix+'-locale',tests=prefix+'-tests')
                ledger['configurations'][case]=item;atomic(directory/'matrix.json',ledger)
                entry['locale_change_attempted']=True
                atomic(directory/'matrix.json',ledger)
                one=coordinator.stage(identity,{LOCALE},tags,'change',directory/item['locale'])
                if one['status']!='PASS':
                    item['status']=one['status'];raise runner.FunctionalFailure('Locale assertion failed') if one['status']=='FAIL' else runner.Blocked('Locale change not verified')
                two=coordinator.stage(identity,{READER,ev.SMOKE},tags,'observe',directory/item['tests'])
                item['status']=two['status'];atomic(directory/'matrix.json',ledger)
                if two['status']!='PASS': raise runner.FunctionalFailure('Functional assertion failed') if two['status']=='FAIL' else runner.Blocked('Incomplete observations')
        finally:
            try: restore_device(coordinator,directory,entry)
            except (runner.Blocked,OSError,ValueError) as error: entry.update(restoration='BLOCKED',restoration_error=str(error))
            atomic(directory/'matrix.json',ledger)
        if entry['restoration']!='PASS': raise runner.Blocked('Exact restoration unverified')
    return report(directory)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',default='adb')
    sub=parser.add_subparsers(dest='command',required=True)
    sub.add_parser('inspect')
    for cmd in ('run','resume','restore','report','pilot'):
        p=sub.add_parser(cmd);p.add_argument('--output',required=True,type=Path)
        if cmd=='pilot':p.add_argument('--recovery-from',required=True,type=Path)
    args=parser.parse_args(argv);coordinator=Coordinator(args.adb)
    try:
        if args.command in ('run','resume','restore','pilot') and (platform.system()!='Darwin' or runner.native_architecture()!='arm64'):
            raise runner.Blocked('Device-changing workflow requires the approved native Apple Silicon macOS host')
        if args.command=='inspect':
            result=dict(status='INSPECTION ONLY',source=source(),host=dict(os=platform.system(),native_architecture=runner.native_architecture()),adb=coordinator.adb,devices=coordinator.discover(),
                        locale_method='AOSP Settings resource IDs; runtime capability unverified until UI execution',expected=sorted(EXPECTED))
            result['all_targets_present']=set(result['devices'])==set(DEVICES)
            result['runtime_locale_capability']='NOT VERIFIED'
        else:
            directory=args.output.absolute()
            # Require local per-run confinement, reject symlink parents before any device operation.
            confined(ROOT,str(directory.relative_to(ROOT)))
            if directory.parent != ROOT/'build/j-m3-b2b': raise ValueError('Output must be build/j-m3-b2b/<unique-run>')
            if args.command=='report': result=report(directory)
            elif args.command=='restore':
                ledger=read_json(directory/'matrix.json')
                for entry in ledger['devices'].values(): restore_device(coordinator,directory,entry)
                atomic(directory/'matrix.json',ledger);result=report(directory)
            else:
                previous=None
                if args.command=='pilot':
                    previous=args.recovery_from.absolute()
                    confined(ROOT,str(previous.relative_to(ROOT)))
                    if previous.parent != ROOT/'build/j-m3-b2b' or previous==directory:
                        raise ValueError('Recovery must reference a separate retained build/j-m3-b2b run')
                result=workflow(coordinator,directory,args.command=='resume',args.command=='pilot',previous)
            atomic(directory/'summary.json',result)
        print(json.dumps(result,indent=2));return {'PASS':0,'FAIL':1}.get(result.get('status'),2 if args.command!='inspect' else 0)
    except (runner.Blocked,OSError,ValueError,KeyError) as error:
        if hasattr(args,'output') and (args.output/'matrix.json').is_file():
            try:
                result=report(args.output);result['operation_error']=str(error);result['status']='BLOCKED' if result['status']!='FAIL' else 'FAIL';atomic(args.output/'summary.json',result)
            except (OSError,ValueError,KeyError): pass
        print('BLOCKED:',error);return 2
    except runner.FunctionalFailure as error:
        if hasattr(args,'output') and (args.output/'matrix.json').is_file():
            result=report(args.output);result.update(status='FAIL',operation_error=str(error));atomic(args.output/'summary.json',result)
        print('FAIL:',error);return 1


if __name__=='__main__':
    raise SystemExit(main())
