#!/usr/bin/env python3
"""Opt-in J03/J04 observations. Completion is not performance acceptance. No provisioning."""
from __future__ import annotations
import argparse
import csv
from datetime import datetime, timezone
from contextlib import ExitStack
import hashlib
import io
import json
import math
import os
from pathlib import Path
import platform
import re
import statistics
import sys
import tempfile
import threading
import time
import uuid

import local_e2e as e2e
import local_test_evidence as ev

ROOT = e2e.ROOT
METHOD = ev.APP + '.app.integration.M4BenchmarkTest.coldProcessAndTwoLocalDocuments'
COMPONENT = ev.APP + '.test/' + ev.NORMAL_RUNNER
OUTPUTS = {'summary.md', 'summary.json', 'samples.csv', 'execution.log'}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result: raise ValueError('Duplicate JSON field: ' + key)
        result[key] = value
    return result


def positive(value, label):
    if type(value) is not int or value <= 0: raise ValueError('Missing/invalid ' + label)
    return value


def ram(config, meminfo):
    values = [line.split('=', 1)[1].strip() for line in config.splitlines() if re.match(r'^hw\.ramSize\s*=', line)]
    if len(values) != 1 or values[0] not in ('2048', '2G'):
        raise e2e.Blocked('Owned AVD must explicitly configure hw.ramSize=2048 or hw.ramSize=2G; no AVD will be edited')
    matches = re.findall(r'(?m)^MemTotal:\s+(\d+) kB\s*$', meminfo)
    if len(matches) != 1: raise e2e.Blocked('Missing/ambiguous Android MemTotal')
    kb = int(matches[0])
    # AOSP reserves guest RAM. This is a documented compatibility band, not exact physical RAM attestation.
    if not 1536 * 1024 <= kb <= 2048 * 1024:
        raise e2e.Blocked('Android MemTotal incompatible with 2 GiB (allowed 0–25% system reservations)')
    return dict(configured_mb=2048, observed_kb=kb, reserved_fraction=1-kb/(2048*1024),
                policy='Configured 2048 MiB; MemTotal between 1536 and 2048 MiB inclusive')


def pss(text, pid, name):
    headers = re.findall(r'\*\* MEMINFO in pid (\d+) \[([^\]]+)\] \*\*', text)
    if headers != [(str(pid), name)]: raise ValueError('Wrong/ambiguous meminfo process identity')
    modern = re.findall(r'(?m)^\s*TOTAL PSS:\s*(\d+)(?:\s+.*)?$', text)
    legacy = re.findall(r'(?m)^\s*TOTAL\s+(\d+)\s+\d+\s+\d+.*$', text)
    if len(modern) > 1 or len(legacy) > 1 or not (modern or legacy): raise ValueError('Missing/ambiguous PSS')
    values = [int(x) for x in modern+legacy]
    if len(set(values)) != 1 or values[0] <= 0: raise ValueError('Invalid/contradictory PSS')
    return values[0]


def start_ticks(stat, pid):
    if not isinstance(stat,str) or not stat.startswith(str(pid)+' (') or ') ' not in stat:
        raise ValueError('Invalid kernel process identity')
    fields=stat.rsplit(') ',1)[1].split()
    if len(fields)<20 or not fields[19].isdigit() or int(fields[19])<=0:
        raise ValueError('Invalid kernel process start token')
    return int(fields[19])


def percentiles(values):
    if not values or any(not math.isfinite(v) or v <= 0 for v in values): raise ValueError('Missing/invalid latency')
    return dict(n=len(values), median_ms=statistics.median(values), p95_ms=sorted(values)[math.ceil(.95*len(values))-1],
                p95_method='nearest rank; five observations normally makes p95 the maximum')


def fixtures():
    manifest = json.loads((ROOT/'test-fixtures/manifest.json').read_text())
    selected = {r['id']:r for r in manifest['files'] if r['id'] in ('02', '10')}
    if set(selected) != {'02','10'}: raise ValueError('Required canonical vector/raster fixtures absent')
    for record in selected.values():
        path = ev.j_path(ROOT/'test-fixtures', record['path'])
        if record['expected_pages'] < 2 or sha(path) != record['sha256']: raise ValueError('Canonical fixture identity mismatch')
    return selected


class Probe(e2e.Runner):
    """Reuse original ownership/recovery checks; disposable host-only bookkeeping, one retained log."""
    def __init__(self, home, sdk, log):
        self.log, self.log_lock = log, threading.Lock()
        self.scratch = tempfile.TemporaryDirectory(prefix='j-m4-probe-')
        super().__init__(Path(self.scratch.name), sdk, suite='m4')
        self.home = home.resolve()
        self.selection = e2e.host_image()
        self.state = e2e.load_device(self.home, self.selection)
        self.adb = e2e.sdk_tool(sdk, 'platform-tools/adb')
        self.serial = f"emulator-{self.state['port']}"

    def record(self, name, value):
        with self.log_lock:
            with self.log.open('a', encoding='utf-8') as stream: stream.write('\n['+name+']\n'+value+'\n')

    def close(self):
        self.scratch.cleanup()  # Never device cleanup, AVD shutdown or shared home deletion.


def require_no_instrumentation(output):
    # Android 8.0 ActivityManagerService.dumpProcessesLocked: the active section
    # precedes this later dumpAll marker. A header alone may be truncated output.
    header = 'ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)'
    if (output.splitlines().count(header) != 1 or
            not output.lstrip().startswith(header+'\n') or
            len(re.findall(r'(?m)^  Total persistent processes: \d+\s*$', output)) != 1 or
            re.search(r'(?i)unknown command|permission denial|exception|dump timed out', output)):
        raise e2e.Blocked('Unrecognized/incomplete ActivityManager processes dump; instrumentation inactivity unverified')
    if re.search(r'(?i)active[ \t]*instrumentation\b|\bInstrumentation\s*#|\b(?:ActiveInstrumentation|InstrumentationRecord)\s*\{', output):
        raise e2e.Blocked('Concurrent Android instrumentation; retain the shared runner lock')


def preflight(home, sdk, serial=None):
    host = e2e.doctor(home, sdk)
    if host['status'] != 'PASS': raise e2e.Blocked('; '.join(host['blockers']))
    owned = e2e.load_device(home, e2e.host_image())
    actual = f"emulator-{owned['port']}"
    if serial is not None and serial != actual: raise e2e.Blocked('Selected serial differs from the owned AVD port')
    if host.get('dedicated_avd', {}).get('status') != 'OWNED_BOOTED': raise e2e.Blocked('Start the already-owned emulator manually; this tool never starts one')
    config = home/'avd'/(owned['name']+'.avd')/'config.ini'
    adb = e2e.sdk_tool(sdk, 'platform-tools/adb')
    def read(*args): return e2e.checked([adb,'-s',actual,*args],timeout=20)
    memory = ram(config.read_text(), read('shell','cat','/proc/meminfo'))
    require_no_instrumentation(read('shell','dumpsys','activity','processes'))
    boot = read('shell','cat','/proc/sys/kernel/random/boot_id')
    if not re.fullmatch(r'[a-f0-9-]{36}',boot): raise e2e.Blocked('Invalid boot identity')
    apks={name:(sha(ROOT/'app/build/outputs/apk'/relative) if (ROOT/'app/build/outputs/apk'/relative).is_file() else 'NOT BUILT')
          for name,relative in [('application','debug/app-debug.apk'),('instrumentation','androidTest/debug/app-debug-androidTest.apk')]}
    return dict(status='READY FOR AUTHORIZED ATTEMPT',android_benchmark='NOT RUN',existing_host_apk_sha256=apks,
                installed_identity='Verified only after explicit installation in run',avd_config_sha256=sha(config),host=host, device=dict(serial=actual,name=owned['name'],api=26,abi=e2e.host_image()['abi'],boot=boot), ram=memory)


def process(probe, name):
    result = e2e.execute([probe.adb,'-s',probe.serial,'shell','pidof',name],timeout=5)
    probe.record('pidof', result.stdout+'\n'+result.stderr)
    if result.returncode == 1 and not result.stdout.strip() and not result.stderr.strip(): return None
    if result.returncode or not re.fullmatch(r'\d+\s*',result.stdout): raise e2e.Blocked('Missing/ambiguous process discovery for '+name)
    pid = int(result.stdout.strip())
    # Verify PID reuse around every PSS observation; stat field 22 is a kernel start token.
    try:
        cmd = e2e.checked([probe.adb,'-s',probe.serial,'shell','cat',f'/proc/{pid}/cmdline'],timeout=5).split('\0')[0]
        stat = e2e.checked([probe.adb,'-s',probe.serial,'shell','cat',f'/proc/{pid}/stat'],timeout=5)
    except e2e.Blocked:
        # Process termination during a read is expected at Reader close. Never substitute a
        # replacement's data; only discard if a separate read proves that PID disappeared.
        current=e2e.execute([probe.adb,'-s',probe.serial,'shell','pidof',name],timeout=5)
        if ((current.returncode==1 and not current.stdout.strip() and not current.stderr.strip()) or
                (current.returncode==0 and re.fullmatch(r'\d+\s*',current.stdout) and int(current.stdout)!=pid)):
            return None
        raise
    if cmd != name: raise e2e.Blocked('Process identity changed')
    return dict(pid=pid,name=name,start_ticks=start_ticks(stat,pid))



class Sampler:
    def __init__(self, probe, interval):
        self.probe, self.interval = probe, interval
        self.rows, self.errors, self.stop = [], [], threading.Event()
        self.thread = threading.Thread(target=self.loop, name='m4-pss', daemon=True)

    def clock(self):
        raw = e2e.checked([self.probe.adb,'-s',self.probe.serial,'shell','cat','/proc/uptime'],timeout=5)
        if not re.fullmatch(r'\d+\.\d+ \d+\.\d+',raw): raise e2e.Blocked('Invalid monotonic device uptime')
        return int(float(raw.split()[0])*1_000_000_000)

    def once(self):
        begin = self.clock()
        records = []
        for name in (ev.APP,ev.APP+':pdf'):
            identity = process(self.probe,name)
            if identity is None: continue
            result = e2e.execute([self.probe.adb,'-s',self.probe.serial,'shell','dumpsys','-t','5','meminfo',str(identity['pid'])],timeout=8)
            self.probe.record('sample-meminfo',result.stdout+'\n'+result.stderr)
            if result.returncode: raise e2e.Blocked('Memory query failed')
            if process(self.probe,name) != identity:
                self.probe.record('sample-gap','Process changed during query; sample discarded, never attributed to replacement')
                continue
            amount = pss(result.stdout,identity['pid'],name)
            records.append(dict(**identity,pss_kb=amount))
        end = self.clock()
        if records:
            self.rows.append(dict(begin_ns=begin,end_ns=end,processes=records,
                combined_pss_kb=sum(r['pss_kb'] for r in records),duration_ms=(end-begin)/1e6))

    def loop(self):
        try:
            while not self.stop.is_set():
                self.once()
                self.stop.wait(self.interval)  # Sampling cadence, never UI readiness or a functional retry.
        except Exception as error: self.errors.append(str(error))

    def finish(self):
        self.stop.set(); self.thread.join(100)
        if self.thread.is_alive(): raise e2e.Blocked('Memory sampler did not terminate')
        if self.errors: raise e2e.Blocked('PSS sampling incomplete: '+'; '.join(self.errors))


def result_payload(output, nonce, fixture_rows):
    cases = ev.parse_android(output,{METHOD})
    if any(c['status']=='FAIL' for c in cases): raise e2e.FunctionalFailure('Executed M4 JUnit assertion failed')
    matches = re.findall(r'(?m)^INSTRUMENTATION_RESULT: m4=(.*)$',output)
    if len(matches)!=1: raise ValueError('Missing/duplicate M4 result payload')
    data = json.loads(matches[0],object_pairs_hook=unique)
    if data.get('schema')!=1 or data.get('nonce')!=nonce or data.get('status')!='COMPLETE' or data.get('cleanup')!='PASS':
        raise ValueError('Incomplete/mismatched M4 payload')
    positive(data.get('pid'),'main PID')
    data['main_start_ticks']=start_ticks(data.pop('main_stat',None),data['pid'])
    points=[positive(data.get(k),k) for k in ('process_start_ns','launch_ns','library_visible_ns','library_ready_ns')]
    if points!=sorted(points): raise ValueError('Nonmonotonic startup timing')
    positive(data.get('idle_main_pss_kb'),'idle PSS')
    if not data['library_ready_ns']<=positive(data.get('idle_memory_begin_ns'),'idle memory begin')<=positive(data.get('idle_memory_end_ns'),'idle memory end'):
        raise ValueError('Invalid idle memory timing')
    journeys=data.get('journeys',[])
    if len(journeys)!=2 or [j.get('fixture') for j in journeys]!=['02','10']: raise ValueError('Wrong/missing fixture journeys')
    previous_end=data['library_ready_ns']
    for j in journeys:
        pid=positive(j.get('worker_pid'),'worker PID')
        if pid==data['pid'] or not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',j.get('worker_epoch','')): raise ValueError('Invalid worker identity')
        positive(j.get('worker_started_ms'),'worker start')
        j['worker_start_ticks']=start_ticks(j.pop('worker_stat',None),pid)
        if j.get('sha256')!=fixture_rows[j['fixture']]['sha256']: raise ValueError('Wrong fixture identity')
        timestamps=[positive(j.get(k),k) for k in ('open_begin_ns','native_open_begin_ns','native_open_end_ns','first_presented_ns','next_begin_ns','next_presented_ns','closed_ns')]
        if timestamps!=sorted(timestamps) or timestamps[0]<previous_end: raise ValueError('Nonmonotonic Reader timing')
        previous_end=timestamps[-1]
        next_pid=positive(j.get('next_worker_pid'),'next worker PID')
        if next_pid==data['pid'] or not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',j.get('next_worker_epoch','')):
            raise ValueError('Invalid next worker identity')
        positive(j.get('next_worker_started_ms'),'next worker start')
        j['next_worker_start_ticks']=start_ticks(j.pop('next_worker_stat',None),next_pid)
        for label,lower,upper in [('presented','first_presented_ns','next_begin_ns'),('next','next_presented_ns','closed_ns'),('closed','closed_ns',None)]:
            begin=positive(j.get(label+'_memory_begin_ns'),'memory begin');end=positive(j.get(label+'_memory_end_ns'),'memory end')
            if begin<j[lower] or end<begin or (upper and end>j[upper]): raise ValueError('Invalid checkpoint memory timing')
        for k in ('bitmaps_recycled','descriptors_closed','source_unchanged'):
            if j.get(k) is not True: raise ValueError('Incomplete cleanup/source integrity: '+k)
        for k in ('presented_main_pss_kb','closed_main_pss_kb'): positive(j.get(k),k)
        for k in ('presented_worker_meminfo','next_worker_meminfo'):
            j[k.replace('_meminfo','_pss_kb')]=pss(j.pop(k),next_pid if k.startswith('next_') else pid,ev.APP+':pdf')
    return data


def checked_result(process_result, nonce, fixture_rows):
    # Parse trustworthy functional evidence first, even if ADB itself exited nonzero.
    data=result_payload(process_result.stdout+'\n'+process_result.stderr,nonce,fixture_rows)
    if process_result.returncode: raise e2e.Blocked('Complete JUnit output but nonzero ADB exit')
    return data


def summarize(iterations, expected_runs):
    measured=[i for i in iterations if not i['warmup']]
    if len(iterations)!=expected_runs+1 or len(measured)!=expected_runs or not iterations[0]['warmup']: raise ValueError('Incomplete measured iteration inventory')
    metrics={}
    for name,start,end in [('instrumented_cold_process_to_library_ms','process_start_ns','library_visible_ns'),
                           ('cold_activity_to_library_ms','launch_ns','library_visible_ns'),
                           ('library_interaction_ready_ms','launch_ns','library_ready_ns')]:
        metrics[name]=percentiles([(i['device'][end]-i['device'][start])/1e6 for i in measured])
    for fid in ('02','10'):
        rows=[next(j for j in i['device']['journeys'] if j['fixture']==fid) for i in measured]
        for name,start,end in [('native_open_ms','native_open_begin_ns','native_open_end_ns'),('open_to_visible_ms','open_begin_ns','first_presented_ns'),('next_visible_ms','next_begin_ns','next_presented_ns')]:
            metrics[fid+'_'+name]=percentiles([(j[end]-j[start])/1e6 for j in rows])
    for i in iterations:
        allowed={(i['device']['pid'],i['device']['main_start_ticks'],ev.APP)} | {
            (j[prefix+'worker_pid'],j[prefix+'worker_start_ticks'],ev.APP+':pdf')
            for j in i['device']['journeys'] for prefix in ('','next_')}
        if any((p['pid'],p['start_ticks'],p['name']) not in allowed for s in i['memory'] for p in s['processes']):
            raise ValueError('PSS process identity is not present in authoritative test observation')
    samples=[s for i in measured for s in i['memory']]
    if not samples: raise ValueError('No actual sampled PSS')
    main=[p['pss_kb'] for s in samples for p in s['processes'] if p['name']==ev.APP]
    worker=[p['pss_kb'] for s in samples for p in s['processes'] if p['name']==ev.APP+':pdf']
    paired=[s['combined_pss_kb'] for s in samples if len(s['processes'])==2]
    main += [i['device']['idle_main_pss_kb'] for i in measured]+[j[k] for i in measured for j in i['device']['journeys'] for k in ('presented_main_pss_kb','closed_main_pss_kb')]
    worker += [j[k] for i in measured for j in i['device']['journeys'] for k in ('presented_worker_pss_kb','next_worker_pss_kb')]
    paired += [j['presented_main_pss_kb']+j['presented_worker_pss_kb'] for i in measured for j in i['device']['journeys']]
    if not main or not worker or not paired: raise ValueError('Missing main/worker/paired PSS observations')
    # Each fixture/iteration needs observed opening and navigation windows; missing is not zero.
    for iteration in measured:
        for journey in iteration['device']['journeys']:
            for begin,end in [('open_begin_ns','first_presented_ns'),('next_begin_ns','next_presented_ns')]:
                if not any(s['begin_ns']<=journey[end] and s['end_ns']>=journey[begin] and
                           any(p['pid']==journey[('next_' if begin=='next_begin_ns' else '')+'worker_pid'] and
                               p['start_ticks']==journey[('next_' if begin=='next_begin_ns' else '')+'worker_start_ticks']
                               for p in s['processes']) for s in iteration['memory']):
                    raise ValueError('Unavailable sampled worker PSS during '+journey['fixture']+' '+begin)
    return dict(latencies=metrics,memory=dict(main_observed_max_pss_kb=max(main),worker_observed_max_pss_kb=max(worker),
        combined_observed_max_pss_kb=max(paired),samples=len(samples),max_query_duration_ms=max(s['duration_ms'] for s in samples)))


def finalize(out, report):
    if report['status']=='PASS' and (report.get('complete') is not True or report.get('host_cleanup')!='PASS' or
            len(report.get('iterations',[]))!=report.get('runs',-1)+1):
        raise ValueError('Cannot finalize incomplete benchmark as PASS')
    # Only four retained outputs; JSON is written last and is authoritative.
    rows=io.StringIO();writer=csv.writer(rows)
    writer.writerow(['iteration','warmup','kind','fixture','begin_ns','end_ns','pid','name','kernel_start_ticks','worker_epoch','value','unit'])
    for i in report['iterations']:
        d=i['device']
        for name,start,end in [('cold_process','process_start_ns','library_visible_ns'),('library_visible','launch_ns','library_visible_ns'),('library_ready','launch_ns','library_ready_ns')]:
            writer.writerow([i['iteration'],i['warmup'],'latency',name,d[start],d[end],d['pid'],ev.APP,d['main_start_ticks'],'',(d[end]-d[start])/1e6,'ms'])
        writer.writerow([i['iteration'],i['warmup'],'idle_pss','',d['idle_memory_begin_ns'],d['idle_memory_end_ns'],d['pid'],ev.APP,d['main_start_ticks'],'',d['idle_main_pss_kb'],'KiB'])
        for j in d['journeys']:
            for phase,stamp,key in [('presented', 'first_presented_ns','presented_main_pss_kb'),('closed','closed_ns','closed_main_pss_kb')]:
                writer.writerow([i['iteration'],i['warmup'],phase+'_pss',j['fixture'],j[phase+'_memory_begin_ns'],j[phase+'_memory_end_ns'],d['pid'],ev.APP,d['main_start_ticks'],'',j[key],'KiB'])
            for phase,stamp,key in [('presented','first_presented_ns','presented_worker_pss_kb'),('next','next_presented_ns','next_worker_pss_kb')]:
                writer.writerow([i['iteration'],i['warmup'],phase+'_worker_pss',j['fixture'],j[phase+'_memory_begin_ns'],j[phase+'_memory_end_ns'],j[('next_' if phase=='next' else '')+'worker_pid'],ev.APP+':pdf',j[('next_' if phase=='next' else '')+'worker_start_ticks'],j[('next_' if phase=='next' else '')+'worker_epoch'],j[key],'KiB'])
            for name,start,end in [('native_open','native_open_begin_ns','native_open_end_ns'),('open','open_begin_ns','first_presented_ns'),('next','next_begin_ns','next_presented_ns')]:
                writer.writerow([i['iteration'],i['warmup'],name,j['fixture'],j[start],j[end],j[('next_' if name=='next' else '')+'worker_pid'],'worker',j[('next_' if name=='next' else '')+'worker_start_ticks'],j[('next_' if name=='next' else '')+'worker_epoch'],(j[end]-j[start])/1e6,'ms'])
        for s in i['memory']:
            for p in s['processes']:
                writer.writerow([i['iteration'],i['warmup'],'pss','',s['begin_ns'],s['end_ns'],p['pid'],p['name'],p['start_ticks'],'',p['pss_kb'],'KiB'])
    for sample in report.get('partial_samples',[]):
        for process_row in sample['processes']:
            writer.writerow(['partial','unknown','unattributed_pss','',sample['begin_ns'],sample['end_ns'],process_row['pid'],process_row['name'],process_row['start_ticks'],'',process_row['pss_kb'],'KiB'])
    ev.j_atomic(out,'samples.csv',rows.getvalue())
    lines=['# Phase J M4 benchmark', '', '**Status: '+report['status']+'** — execution completeness, not performance acceptance.',
           '', 'Source: `'+report.get('commit','UNKNOWN')+'`; clean: '+str(report.get('clean',False)),
           '', 'Measured iterations: '+str(sum(not i['warmup'] for i in report['iterations']))+'; one warm-up excluded.',
           '', 'Environment and APK identities: see `summary.json`. Raw execution/JUnit and partial diagnostics: `execution.log`.',
           '', '| Latency | n | Median ms | p95 ms |','| --- | --- | --- | --- |']
    for name,m in report.get('measurements',{}).get('latencies',{}).items(): lines.append(f"| {name} | {m['n']} | {m['median_ms']:.3f} | {m['p95_ms']:.3f} |")
    environment=report.get('environment',{})
    lines+=['','## Environment and provenance', '',
        'Host: '+str(environment.get('host',{}).get('os','UNVERIFIED'))+' / '+str(environment.get('host',{}).get('native_architecture','UNVERIFIED')),
        'Device: `'+json.dumps(environment.get('device',{}),sort_keys=True)+'`',
        'RAM: `'+json.dumps(environment.get('ram',{}),sort_keys=True)+'`',
        'APK SHA-256 (host and installed comparisons): `'+json.dumps(report.get('apks',{}),sort_keys=True)+'`',
        'Host cleanup: '+report.get('host_cleanup','NOT RUN'), '',
        '| Iteration | Fixture | Main PSS after close (KiB) | Bitmap/descriptor/source cleanup |',
        '| --- | --- | --- | --- |']
    for i in report['iterations']:
        for j in i['device']['journeys']:
            lines.append(f"| {i['iteration']} ({'warm-up' if i['warmup'] else 'measured'}) | {j['fixture']} | {j['closed_main_pss_kb']} | {j['bitmaps_recycled']}/{j['descriptors_closed']}/{j['source_unchanged']} |")
    lines+=['','Memory observations: `'+json.dumps(report.get('measurements',{}).get('memory',{}),sort_keys=True)+'`',
            '', 'Limits: instrumented debug/JaCoCo build; cold-process startup includes runner/harness initialization. Visible timing ends after UI/PixelCopy observation; it is an upper bound, not exact display scan-out. Readiness includes a real Settings/Back interaction. Reader uses production host/route/UI/native worker with injected canonical local capabilities, not genuine external SAF. PSS queries overlap asynchronously and may straddle phase boundaries; sums are sequential observations, sampled maxima are not instantaneous peaks. Small-n p95 is nearest rank. No thresholds or physical-device acceptance are inferred.',
            '', 'Cleanup/source outcomes and post-close memory: see each fixture in `summary.json`. Findings requiring owner review: '+str(report.get('reason','Review measured distributions and overhead on real hardware; no performance budget is approved.')),
            '', 'M5 NOT STARTED. J08 DEFERRED TO PHASE R.']
    ev.j_atomic(out,'summary.md','\n'.join(lines)+'\n')
    report['artifacts']={name:sha(out/name) for name in ('summary.md','samples.csv','execution.log')}
    report['finished']=datetime.now(timezone.utc).isoformat()
    ev.j_atomic(out,'summary.json',json.dumps(report,indent=2,sort_keys=True)+'\n')


def installed(probe, package, expected):
    path=probe.adb_command('shell','pm','path',package)
    match=re.fullmatch(r'package:(/data/app/[A-Za-z0-9_.=+/-]+\.apk)',path)
    if not match or '..' in match[1]: raise e2e.Blocked('Unexpected/multiple installed APK paths')
    digest=probe.adb_command('shell','sha256sum',match[1]).split()
    if len(digest)!=2 or digest[0]!=expected: raise e2e.Blocked('Installed APK hash does not match host APK')
    return digest[0]


def run(args):
    parent=ev.j_path(ROOT,'build/j-m4/reports');parent.mkdir(parents=True,exist_ok=True)
    out=parent/(datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex)
    ev.j_path(ROOT,out.relative_to(ROOT).as_posix());out.mkdir()
    (out/'execution.log').touch()
    report=dict(schema=1,status='BLOCKED',complete=False,iterations=[],started=datetime.now(timezone.utc).isoformat(),
                runs=args.runs,warmups=1,sample_ms=args.sample_ms,acceptance='PENDING OWNER REVIEW',android='NOT RUN')
    probe=None
    device_written=False
    lock=ExitStack()
    try:
        lock.enter_context(e2e.exclusive(args.home))
        report['environment']=preflight(args.home,args.sdk,args.serial)
        fixture_rows=fixtures();report['fixtures']=fixture_rows
        report['commit']=e2e.checked(['git','rev-parse','HEAD'])
        report['working_tree']=e2e.checked(['git','status','--porcelain'])
        report['clean']=not report['working_tree']
        # Reuse source digest/build attribution without invoking a host test profile.
        capture_run=uuid.uuid4().hex
        report['source_capture']=ev.j_capture(ROOT,capture_run)
        probe=Probe(args.home,args.sdk,out/'execution.log')
        if probe.serial!=args.serial: raise e2e.Blocked('Explicit serial changed')
        probe.identify(boot=True)
        if process(probe,ev.APP+':pdf') is not None: raise e2e.Blocked('Existing PDF worker: another session may own it')
        build=probe.gradle('build', [':app:assembleDebug',':app:assembleDebugAndroidTest'],
            ['-I',str(ROOT/'scripts/j-m4.init.gradle'),'--no-configuration-cache'],mode='normal')
        apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';tests=ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        report['apks']={ev.APP:sha(apk),ev.APP+'.test':sha(tests)}
        dump=e2e.checked([e2e.sdk_tool(args.sdk,'build-tools/37.0.0/aapt2'),'dump','xmltree',tests,'--file','AndroidManifest.xml'])
        ev.inspect_apk(tests,dump,'normal',{METHOD})
        device_written=True  # An uncertain install reply is never replayed.
        for file in (apk,tests): probe.adb_command('install','-r',str(file),timeout=120)
        report['installed_apks']={pkg:installed(probe,pkg,h) for pkg,h in report['apks'].items()}
        previous=set()
        for iteration in range(args.runs+1):
            probe.identify(boot=True)
            cfg=args.home/'avd'/(probe.state['name']+'.avd')/'config.ini'
            if sha(cfg)!=report['environment']['avd_config_sha256']: raise e2e.Blocked('AVD configuration changed')
            if any(sha(f)!=report['apks'][pkg] for f,pkg in [(apk,ev.APP),(tests,ev.APP+'.test')]): raise e2e.Blocked('Host APK changed')
            if probe.adb_command('shell','cat','/proc/sys/kernel/random/boot_id')!=report['environment']['device']['boot']: raise e2e.Blocked('Emulator rebooted')
            if process(probe,ev.APP+':pdf') is not None: raise e2e.Blocked('Worker survived previous iteration')
            probe.adb_command('shell','am','force-stop',ev.APP)
            if process(probe,ev.APP) is not None: raise e2e.Blocked('Application did not stop; cold process cannot be proven')
            nonce=uuid.uuid4().hex
            sampler=Sampler(probe,args.sample_ms/1000)
            sampler.thread.start()
            primary=None
            try:
                result=probe.command('instrumentation', [probe.adb,'-s',probe.serial,'shell','am','instrument','-w','-r',
                    '-e','class',METHOD.replace('.coldProcessAndTwoLocalDocuments','#coldProcessAndTwoLocalDocuments'),
                    '-e','m4Nonce',nonce,COMPONENT],timeout=240)
                report['android']='EXECUTED'
                data=checked_result(result,nonce,fixture_rows)
            except BaseException as error:
                primary=error
                raise
            finally:
                try:
                    sampler.finish()
                except Exception as error:
                    report.setdefault('blockers',[]).append(str(error))
                    if primary is None: raise
                finally:
                    report.setdefault('partial_samples',[]).extend(sampler.rows)
            identity=(data['pid'],data['process_start_ns'])
            if identity in previous: raise e2e.Blocked('Repeated main PID; cold identity not established')
            previous.add(identity)
            if process(probe,ev.APP+':pdf') is not None: raise e2e.Blocked('Native worker cleanup incomplete')
            if {pkg:installed(probe,pkg,h) for pkg,h in report['apks'].items()}!=report['installed_apks']: raise e2e.Blocked('Installation changed')
            item=dict(iteration=iteration,warmup=iteration==0,device=data,memory=sampler.rows)
            report['iterations'].append(item)
            report['partial_samples']=[]
            current=ev.j_capture(ROOT,capture_run)
            if any(current[k]!=report['source_capture'][k] for k in ('revision','tree','dirty','source_digest','build','expected_jvm')) or fixtures()!=fixture_rows:
                raise e2e.Blocked('Source/fixture identity changed')
            finalize(out,report)  # Atomic partial evidence, never provisional PASS.
        report['measurements']=summarize(report['iterations'],args.runs)
        report.update(status='PASS',complete=True)
    except e2e.FunctionalFailure as error: report.update(status='FAIL',reason=str(error))
    except (Exception,KeyboardInterrupt) as error: report.update(status='BLOCKED',reason=str(error))
    finally:
        if probe and device_written:
            try:
                probe.identify(boot=True)
                if probe.adb_command('shell','cat','/proc/sys/kernel/random/boot_id')!=report['environment']['device']['boot']:
                    raise e2e.Blocked('Boot changed before cleanup; refusing stop')
                # Test app only, after attempting normal test-owned teardown. Never kill a supplied PID.
                probe.adb_command('shell','am','force-stop',ev.APP)
                report['host_cleanup']='PASS'
            except Exception as error:
                report['host_cleanup']='BLOCKED';report['cleanup_error']=str(error)
                if report['status']=='PASS': report.update(status='BLOCKED',complete=False)
        if probe: probe.close()
        lock.close()
        with (out/'execution.log').open('a',encoding='utf-8') as log:
            log.write('\n[final] '+report['status']+' '+str(report.get('reason',''))+' '+str(report.get('cleanup_error',''))+'\n')
        finalize(out,report)
    print(report['status']+': '+str(out.relative_to(ROOT)))
    return {'PASS':0,'FAIL':1,'BLOCKED':2}[report['status']]


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['list','doctor','run'])
    parser.add_argument('--sdk',type=Path)
    parser.add_argument('--home',type=Path,default=e2e.DEFAULT_HOME)
    parser.add_argument('--serial')
    parser.add_argument('--runs',type=int,default=5)
    parser.add_argument('--sample-ms',type=int,default=250)
    args=parser.parse_args(argv)
    try:
        args.sdk=e2e.discover_sdk(args.sdk)
        if args.command=='list':
            adb=e2e.sdk_tool(args.sdk,'platform-tools/adb')
            print(e2e.checked([adb,'devices','-l']))
            selection=e2e.host_image()
            emulator=e2e.sdk_tool(args.sdk,'emulator/emulator')
            print('Installed default AVDs (not adopted):\n'+e2e.checked(selection['emulator_prefix']+[emulator,'-list-avds']))
            try:
                owned=e2e.load_device(args.home,selection)
                print('Verified dedicated AVD: '+json.dumps(owned))
            except e2e.Blocked as error:
                print('Dedicated ownership NOT VERIFIED: '+str(error))
            for line in e2e.checked([adb,'devices']).splitlines()[1:]:
                fields=line.split()
                if len(fields)>=2 and fields[1]=='device' and re.fullmatch(r'emulator-\d+',fields[0]):
                    serial=fields[0]
                    print(json.dumps(dict(serial=serial,name=e2e.checked([adb,'-s',serial,'emu','avd','name']),
                        api=e2e.checked([adb,'-s',serial,'shell','getprop','ro.build.version.sdk']),
                        abi=e2e.checked([adb,'-s',serial,'shell','getprop','ro.product.cpu.abi']),ownership='NOT ADOPTED')))
            return 0
        if args.command=='doctor':
            print(json.dumps(preflight(args.home,args.sdk,args.serial),indent=2));return 0
        if not re.fullmatch(r'emulator-\d+',args.serial or ''): raise e2e.Blocked('run requires explicit --serial emulator-NNNN')
        if not 1<=args.runs<=10 or not 100<=args.sample_ms<=1000: raise e2e.Blocked('Use 1–10 measured runs and 100–1000 ms sampling')
        return run(args)
    except (e2e.Blocked,ValueError,OSError) as error:
        print('BLOCKED: '+str(error),file=sys.stderr);return 2


if __name__=='__main__': sys.exit(main())
