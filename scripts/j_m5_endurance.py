#!/usr/bin/env python3
"""Bounded M5-A native endurance; no recovery injection or performance budget."""
import argparse
from contextlib import ExitStack
from datetime import datetime, timezone
import csv
import io
import json
from pathlib import Path
import statistics
import uuid
import j_m4_benchmark as m4

e2e,ev=m4.e2e,m4.ev
ROOT=m4.ROOT
METHOD=ev.APP+'.app.integration.M5EnduranceTest.boundedNativeCycles'


def fixtures():
    rows={r['id']:r for r in json.loads((ROOT/'test-fixtures/manifest.json').read_text())['files'] if r['id'] in ('02','10','05')}
    if set(rows)!={'02','10','05'}:raise ValueError('Missing canonical endurance fixtures')
    for r in rows.values():
        if r['expected_pages']<2 or m4.sha(ev.j_path(ROOT/'test-fixtures',r['path']))!=r['sha256']:raise ValueError('Fixture mismatch')
    return rows


def observations(data,samples,cycles):
    allowed={(data['pid'],data['main_start_ticks'],ev.APP)}|{(j[p+'worker_pid'],j[p+'worker_start_ticks'],ev.APP+':pdf') for j in data['journeys'] for p in ('','next_')}
    if not samples:raise ValueError('Missing actual PSS samples')
    for s in samples:
        for p in s['processes']:
            if (p['pid'],p['start_ticks'],p['name']) not in allowed:raise ValueError('Unattributed process observation')
    for j in data['journeys']:
        for begin,end,prefix in [('open_begin_ns','first_presented_ns',''),('next_begin_ns','next_presented_ns','next_')]:
            if not any(s['begin_ns']<=j[end] and s['end_ns']>=j[begin] and any(p['pid']==j[prefix+'worker_pid'] and p['start_ticks']==j[prefix+'worker_start_ticks'] for p in s['processes']) for s in samples):raise ValueError('Missing phase PSS observation')
    trends={}
    for fid in ('02','10','05'):
        values=[j['closed_main_pss_kb'] for j in data['journeys'] if j['fixture']==fid]
        if len(values)!=cycles:raise ValueError('Incomplete cycle inventory')
        half=cycles//2
        trends[fid]=dict(post_close_kib=values,early_median_kib=statistics.median(values[:half]),late_median_kib=statistics.median(values[-half:]),
            sustained_increase=all(b>a for a,b in zip(values,values[1:])),net_change_kib=values[-1]-values[0])
    return dict(trends=trends,review_required=any(t['sustained_increase'] for t in trends.values()),
        sampled_max_kib={name:max(p['pss_kb'] for s in samples for p in s['processes'] if p['name']==name) for name in (ev.APP,ev.APP+':pdf')})


def finalize(out,report):
    if report['status']=='PASS' and (report.get('complete') is not True or report.get('host_cleanup')!='PASS'):
        raise ValueError('Incomplete endurance campaign cannot be PASS')
    rows=io.StringIO();w=csv.writer(rows);w.writerow(['begin_ns','end_ns','pid','name','start_ticks','pss_kib'])
    for s in report.get('samples',[]):
        for p in s['processes']:w.writerow([s['begin_ns'],s['end_ns'],p['pid'],p['name'],p['start_ticks'],p['pss_kb']])
    ev.j_atomic(out,'samples.csv',rows.getvalue())
    ev.j_atomic(out,'summary.md','# M5-A bounded endurance\n\n'+report['status']+' — execution evidence, not proof of no leaks.\n\n'+json.dumps(dict(cycles=report['cycles'],source=report.get('source',{}).get('revision','UNVERIFIED'),complete=report['complete'],cleanup=report.get('host_cleanup','NOT RUN'),reason=report.get('reason','Review finite observations; no memory budget approved'),observations=report.get('observations',{})),indent=2)+'\n')
    report['artifacts']={n:m4.sha(out/n) for n in ('execution.log','samples.csv','summary.md')}
    ev.j_atomic(out,'summary.json',json.dumps(report,indent=2)+'\n')


def run(args):
    parent=ev.j_path(ROOT,'build/j-m5/reports');parent.mkdir(parents=True,exist_ok=True)
    out=parent/(datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex);out.mkdir();(out/'execution.log').touch()
    report=dict(schema=1,status='BLOCKED',complete=False,cycles=args.cycles,samples=[],acceptance='PENDING OWNER REVIEW',android='NOT RUN')
    probe=None;written=False;sampler=None
    with ExitStack() as lock:
        try:
            lock.enter_context(e2e.exclusive(args.home))
            report['environment']=m4.preflight(args.home,args.sdk,args.serial)
            report['fixtures']=fixtures();report['source']=ev.j_capture(ROOT,uuid.uuid4().hex)
            probe=m4.Probe(args.home,args.sdk,out/'execution.log');probe.identify(boot=True)
            if m4.process(probe,ev.APP+':pdf') is not None:raise e2e.Blocked('Pre-existing worker; refusing adoption')
            probe.gradle('build',[':app:assembleDebug',':app:assembleDebugAndroidTest'],['-I',str(ROOT/'scripts/j-m5.init.gradle'),'--no-configuration-cache'],mode='normal')
            apks={ev.APP:ROOT/'app/build/outputs/apk/debug/app-debug.apk',ev.APP+'.test':ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'}
            report['apks']={k:m4.sha(v) for k,v in apks.items()}
            dump=e2e.checked([e2e.sdk_tool(args.sdk,'build-tools/37.0.0/aapt2'),'dump','xmltree',apks[ev.APP+'.test'],'--file','AndroidManifest.xml'])
            ev.inspect_apk(apks[ev.APP+'.test'],dump,'normal',{METHOD})
            probe.identify(boot=True)
            config=args.home/'avd'/(probe.state['name']+'.avd')/'config.ini'
            if m4.sha(config)!=report['environment']['avd_config_sha256'] or probe.adb_command('shell','cat','/proc/sys/kernel/random/boot_id')!=report['environment']['device']['boot']:raise e2e.Blocked('Device configuration/boot changed before installation')
            if m4.process(probe,ev.APP+':pdf') is not None:raise e2e.Blocked('Worker appeared during build')
            written=True
            for apk in apks.values():probe.adb_command('install','-r',str(apk),timeout=120)
            report['installed']={k:m4.installed(probe,k,v) for k,v in report['apks'].items()}
            probe.adb_command('shell','am','force-stop',ev.APP)
            if m4.process(probe,ev.APP) is not None:raise e2e.Blocked('Application still running')
            nonce=uuid.uuid4().hex;sampler=m4.Sampler(probe,0.25);sampler.thread.start()
            primary=None
            try:
                result=probe.command('instrumentation',[probe.adb,'-s',probe.serial,'shell','am','instrument','-w','-r','-e','class',METHOD.rsplit('.',1)[0]+'#boundedNativeCycles','-e','m5Nonce',nonce,'-e','m5Cycles',str(args.cycles),m4.COMPONENT],timeout=120+args.cycles*180)
                sampler.stop.set();report['android']='EXECUTED'
                report['device']=m4.result_payload(result.stdout+'\n'+result.stderr,nonce,report['fixtures'],method=METHOD,sequence=['02','10','05']*args.cycles,channel='m5')
                if result.returncode:raise e2e.Blocked('Nonzero ADB instrumentation exit')
            except BaseException as error:primary=error;raise
            finally:
                try:sampler.finish()
                except Exception as error:
                    report['sampler_error']=str(error)
                    if primary is None:raise
                finally:report['samples']=sampler.rows
            probe.identify(boot=True)
            if probe.adb_command('shell','cat','/proc/sys/kernel/random/boot_id')!=report['environment']['device']['boot']:raise e2e.Blocked('Boot changed')
            if m4.sha(config)!=report['environment']['avd_config_sha256']:raise e2e.Blocked('AVD configuration changed')
            if m4.process(probe,ev.APP+':pdf') is not None:raise e2e.Blocked('Worker survived cleanup')
            if any(m4.sha(apks[k])!=v or m4.installed(probe,k,v)!=v for k,v in report['apks'].items()):raise e2e.Blocked('APK identity changed')
            current=ev.j_capture(ROOT,report['source']['run'])
            if any(current[k]!=report['source'][k] for k in ('revision','tree','dirty','source_digest','build','expected_jvm')) or fixtures()!=report['fixtures']:raise e2e.Blocked('Source/fixture changed')
            for index,journey in enumerate(report['device']['journeys']):journey['cycle']=index//3+1
            report['observations']=observations(report['device'],report['samples'],args.cycles)
            report.update(status='PASS',complete=True)
        except e2e.FunctionalFailure as error:report.update(status='FAIL',reason=str(error))
        except (Exception,KeyboardInterrupt) as error:report.update(status='BLOCKED',reason=str(error))
        finally:
            if probe and written:
                try:
                    probe.identify(boot=True)
                    if probe.adb_command('shell','cat','/proc/sys/kernel/random/boot_id')!=report['environment']['device']['boot']:raise e2e.Blocked('Boot changed; refusing cleanup')
                    probe.adb_command('shell','am','force-stop',ev.APP)
                    if m4.process(probe,ev.APP) is not None or m4.process(probe,ev.APP+':pdf') is not None:raise e2e.Blocked('Process survived final cleanup')
                    report['host_cleanup']='PASS'
                except Exception as error:
                    report['cleanup_error']=str(error)
                    if report['status']=='PASS':report.update(status='BLOCKED',complete=False)
            if probe:probe.close()
            with (out/'execution.log').open('a') as log:log.write('\n[final] '+report['status']+' '+report.get('reason','')+'\n')
            finalize(out,report)
    print(report['status']+': '+str(out));return {'PASS':0,'FAIL':1,'BLOCKED':2}[report['status']]


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('command',choices=['doctor','run']);p.add_argument('--home',type=Path,default=e2e.DEFAULT_HOME);p.add_argument('--sdk',type=Path);p.add_argument('--serial',required=True);p.add_argument('--cycles',type=int,default=3);a=p.parse_args(argv)
    try:
        if not 2<=a.cycles<=10 or not m4.re.fullmatch(r'emulator-\d+',a.serial):raise e2e.Blocked('Select an owned emulator and 2–10 cycles')
        a.sdk=e2e.discover_sdk(a.sdk)
        if a.command=='doctor':print(json.dumps(m4.preflight(a.home,a.sdk,a.serial),indent=2));return 0
        return run(a)
    except (e2e.Blocked,ValueError,OSError) as error:print('BLOCKED: '+str(error));return 2

if __name__=='__main__':raise SystemExit(main())
