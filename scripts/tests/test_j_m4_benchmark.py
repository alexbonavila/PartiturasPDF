"""Synthetic M4 policy fixtures. No emulator, APK install or actual benchmark execution."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from argparse import Namespace
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import j_m4_benchmark as m4
from test_local_regression import protocol


def stat(pid, start):
    return f'{pid} (process name) '+ ' '.join(['S']+['0']*18+[str(start)]+['0']*5)


def mem(pid,name,pss=123):
    return f'** MEMINFO in pid {pid} [{name}] **\n TOTAL {pss} 10 20 30 40 50\n'


def processes_dump(active=''):
    # Minimal synthetic structure from Android 8.0 dumpProcessesLocked, not device evidence.
    return ('ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)\n'
            '  All known processes:\n  *PERS* UID 1000 ProcessRecord{abc 123:system/1000}\n'
            + active + '  Total persistent processes: 1\n'
            '  mProcessesReady=true mSystemReady=true mBooted=true mFactoryTest=0\n')


class M4Tests(unittest.TestCase):
    def setUp(self):
        self.nonce='a'*32
        self.fixtures={f:dict(sha256=f*32) for f in ('02','10')}
        self.data=dict(schema=1,nonce=self.nonce,pid=99,main_stat=stat(99,44),status='COMPLETE',cleanup='PASS',
            process_start_ns=1000000,launch_ns=2000000,library_visible_ns=3000000,library_ready_ns=4000000,idle_memory_begin_ns=4000001,idle_memory_end_ns=4000002,idle_main_pss_kb=150,journeys=[])
        for n,f in enumerate(('02','10')):
            base=10_000_000+n*10_000_000; pid=101+n
            j=dict(fixture=f,sha256=self.fixtures[f]['sha256'],worker_pid=pid,worker_started_ms=1,
                   next_worker_pid=pid,next_worker_started_ms=1,next_worker_stat=stat(pid,50+n),
                   next_worker_epoch=('a' if n==0 else 'b')*8+'-1111-1111-1111-111111111111',
                   worker_epoch=('a' if n==0 else 'b')*8+'-1111-1111-1111-111111111111',worker_stat=stat(pid,50+n),
                   bitmaps_recycled=True,descriptors_closed=True,source_unchanged=True,
                   presented_main_pss_kb=150,closed_main_pss_kb=140,
                   presented_worker_meminfo=mem(pid,m4.ev.APP+':pdf'),next_worker_meminfo=mem(pid,m4.ev.APP+':pdf'))
            for offset,key in enumerate(('open_begin_ns','native_open_begin_ns','native_open_end_ns','first_presented_ns','next_begin_ns','next_presented_ns','closed_ns')):
                j[key]=base+offset*1000000
            for label,stamp in [('presented','first_presented_ns'),('next','next_presented_ns'),('closed','closed_ns')]:
                j[label+'_memory_begin_ns']=j[stamp]+1;j[label+'_memory_end_ns']=j[stamp]+2
            self.data['journeys'].append(j)

    def raw(self, code=0):
        return protocol([(m4.METHOD,code)]).replace('INSTRUMENTATION_CODE: -1',
            'INSTRUMENTATION_RESULT: m4='+json.dumps(self.data)+'\nINSTRUMENTATION_CODE: -1')

    def payload(self): return m4.result_payload(self.raw(),self.nonce,self.fixtures)

    def iteration(self, warmup=False):
        data=self.payload();samples=[]
        for j in data['journeys']:
            samples.append(dict(begin_ns=j['open_begin_ns'],end_ns=j['next_presented_ns'],duration_ms=5,combined_pss_kb=273,processes=[
                dict(pid=99,name=m4.ev.APP,start_ticks=44,pss_kb=150),
                dict(pid=j['worker_pid'],name=m4.ev.APP+':pdf',start_ticks=j['worker_start_ticks'],pss_kb=123)]))
        return dict(iteration=0,warmup=warmup,device=data,memory=samples)

    def test_ram_requires_configured_2gb_and_bounded_reservation(self):
        numeric=m4.ram('hw.ramSize=2048','MemTotal: 1900000 kB')
        self.assertEqual(2048,numeric['configured_mb'])
        self.assertEqual(numeric,m4.ram('hw.ramSize=2G','MemTotal: 1900000 kB'))
        for config,observed in [('hw.ramSize=1024','1900000'),('hw.ramSize=2048','900000'),('hw.ramSize=2048','4000000'),
                                ('hw.ramSize=2048\nhw.ramSize=2048','1900000')]:
            with self.subTest(config=config,observed=observed),self.assertRaises(m4.e2e.Blocked): m4.ram(config,'MemTotal: '+observed+' kB')

    def test_ram_rejects_missing_duplicate_and_unsupported_representations(self):
        for config in ['', 'hw.ramSize=', 'hw.ramSize=2048\nhw.ramSize=2G',
                       'hw.ramSize=2G\nhw.ramSize=2G', 'hw.ramSize=1G',
                       'hw.ramSize=4G', 'hw.ramSize=2g', 'hw.ramSize=2GB',
                       'hw.ramSize=2GiB', 'hw.ramSize=2048M', 'hw.ramSize=2.0G']:
            with self.subTest(config=config),self.assertRaises(m4.e2e.Blocked):
                m4.ram(config,'MemTotal: 1900000 kB')

    def test_ram_2g_still_requires_valid_observed_memtotal(self):
        for meminfo in ['', 'MemTotal: invalid kB', 'MemTotal: 900000 kB',
                        'MemTotal: 4000000 kB', 'MemTotal: 1900000 kB\nMemTotal: 1900000 kB']:
            with self.subTest(meminfo=meminfo),self.assertRaises(m4.e2e.Blocked):
                m4.ram('hw.ramSize=2G',meminfo)

    def test_payload_requires_exact_junit_nonce_fixture_time_and_cleanup(self):
        self.assertEqual(99,self.payload()['pid'])
        for key,value in [('nonce','b'*32),('cleanup','BLOCKED'),('library_ready_ns',1),('idle_main_pss_kb',0)]:
            with patch.dict(self.data,{key:value}),self.assertRaises(ValueError):self.payload()
        for key,value in [('sha256','bad'),('worker_pid',99),('descriptors_closed',False),('source_unchanged',False),('bitmaps_recycled',False)]:
            with patch.dict(self.data['journeys'][0],{key:value}),self.assertRaises(ValueError):self.payload()

    def test_pss_identity_and_formats_are_strict(self):
        self.assertEqual(123,m4.pss(mem(1,'app'),1,'app'))
        self.assertEqual(321,m4.pss('** MEMINFO in pid 1 [app] **\n TOTAL PSS: 321\n',1,'app'))
        for text in [mem(2,'app'),mem(1,'other'),mem(1,'app',0),mem(1,'app')+' TOTAL PSS: 321\n','No process found']:
            with self.subTest(text=text),self.assertRaises(ValueError):m4.pss(text,1,'app')
        with self.assertRaises(ValueError):m4.start_ticks(stat(9,44),10)

    def test_junit_failure_remains_fail_with_nonzero_adb_exit(self):
        result=subprocess.CompletedProcess([],255,self.raw(-2),'')
        with self.assertRaises(m4.e2e.FunctionalFailure):m4.checked_result(result,self.nonce,self.fixtures)
        with self.assertRaises(m4.e2e.Blocked):m4.checked_result(subprocess.CompletedProcess([],255,self.raw(),''),self.nonce,self.fixtures)
        with self.assertRaises(m4.ev.IncompleteInstrumentation):m4.result_payload(self.raw().replace('INSTRUMENTATION_CODE: -1',''),self.nonce,self.fixtures)

    def test_missing_duplicate_and_wrong_test_results_block(self):
        for raw in [self.raw().replace('m4=','other='),self.raw()+'\nINSTRUMENTATION_RESULT: m4={}',
                    self.raw().replace('test=coldProcessAndTwoLocalDocuments','test=other')]:
            with self.subTest(raw=raw),self.assertRaises(ValueError):m4.result_payload(raw,self.nonce,self.fixtures)

    def test_warmup_is_excluded_and_p95_is_nearest_rank(self):
        items=[self.iteration(True)]+[self.iteration() for _ in range(5)]
        result=m4.summarize(items,5)
        self.assertEqual(5,result['latencies']['02_native_open_ms']['n'])
        self.assertEqual(273,result['memory']['combined_observed_max_pss_kb'])
        self.assertEqual(5,m4.percentiles([1,2,3,4,5])['p95_ms'])
        with self.assertRaises(ValueError):m4.summarize(items[:-1],5)

    def test_unavailable_phase_samples_and_wrong_reused_pid_block(self):
        items=[self.iteration(True),self.iteration()]
        items[1]['memory']=items[1]['memory'][:1]
        with self.assertRaisesRegex(ValueError,'Unavailable'):m4.summarize(items,1)
        items[1]=self.iteration();items[1]['memory'][0]['processes'][1]['start_ticks']=999
        with self.assertRaisesRegex(ValueError,'identity'):m4.summarize(items,1)

    def test_finalization_keeps_only_four_outputs_and_atomic_blocked_status(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);(out/'execution.log').write_text('synthetic evidence')
            report=dict(status='BLOCKED',iterations=[self.iteration(True)],complete=False)
            m4.finalize(out,report)
            self.assertEqual(m4.OUTPUTS,{p.name for p in out.iterdir()})
            self.assertFalse(json.loads((out/'summary.json').read_text())['complete'])
            self.assertIn('BLOCKED',(out/'summary.md').read_text())
            self.assertIn('cold_process',(out/'samples.csv').read_text())

    def test_blocked_preflight_performs_no_build_install_or_stop(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(m4,'ROOT',Path(tmp)),patch.object(m4,'preflight',side_effect=m4.e2e.Blocked('No acceleration')),patch.object(m4,'Probe') as probe:
            self.assertEqual(2,m4.run(Namespace(home=Path(tmp)/'home',sdk=Path(tmp)/'sdk',serial='emulator-5580',runs=5,sample_ms=250)))
            probe.assert_not_called()
            directory=next((Path(tmp)/'build/j-m4/reports').iterdir())
            self.assertEqual(m4.OUTPUTS,{p.name for p in directory.iterdir()})
            self.assertFalse((Path(tmp)/'home/runner.lock').exists())

    def test_explicit_selector_and_bounds_block_before_device_commands(self):
        with patch.object(m4.e2e,'discover_sdk',return_value=Path('/fake')),patch.object(m4,'run') as run:
            self.assertEqual(2,m4.main(['run']))
            self.assertEqual(2,m4.main(['run','--serial','physical-device']))
            self.assertEqual(2,m4.main(['run','--serial','emulator-5580','--runs','100']))
            run.assert_not_called()

    def test_opt_in_adapter_is_absent_from_ordinary_inventory(self):
        ordinary=m4.ev.android_inventory(m4.ROOT/'app/src/androidTest')
        self.assertNotIn(m4.METHOD,ordinary)
        self.assertEqual({m4.METHOD},m4.ev.android_inventory(m4.ROOT/'scripts/j-m4/androidTest',None))
        self.assertEqual({'02','10'},set(m4.fixtures()))

    def test_normal_process_exit_is_discarded_not_replaced(self):
        from unittest.mock import Mock
        probe=Mock(adb='adb',serial='emulator-5580')
        replies=[subprocess.CompletedProcess([],0,'101',''),
                 subprocess.CompletedProcess([],0,m4.ev.APP+'\0',''),
                 subprocess.CompletedProcess([],1,'','No such process'),
                 subprocess.CompletedProcess([],1,'','')]
        with patch.object(m4.e2e,'execute',side_effect=replies):self.assertIsNone(m4.process(probe,m4.ev.APP))

    def test_meminfo_query_rejects_unrelated_process(self):
        from unittest.mock import Mock
        probe=Mock(adb='adb',serial='emulator-5580')
        replies=[subprocess.CompletedProcess([],0,'101',''),
                 subprocess.CompletedProcess([],0,'other\0',''),subprocess.CompletedProcess([],0,stat(101,1),'')]
        with patch.object(m4.e2e,'execute',side_effect=replies),self.assertRaises(m4.e2e.Blocked):m4.process(probe,m4.ev.APP)

    def test_foreign_serial_and_concurrent_instrumentation_block(self):
        import contextlib
        selection=dict(image='system-images;android-26;google_apis;x86_64',abi='x86_64')
        with tempfile.TemporaryDirectory() as tmp,contextlib.ExitStack() as stack:
            root=Path(tmp);config=root/'avd/owned.avd/config.ini';config.parent.mkdir(parents=True);config.write_text('hw.ramSize=2048')
            stack.enter_context(patch.object(m4.e2e,'doctor',return_value=dict(status='PASS',dedicated_avd=dict(status='OWNED_BOOTED'))))
            stack.enter_context(patch.object(m4.e2e,'host_image',return_value=selection))
            stack.enter_context(patch.object(m4.e2e,'load_device',return_value=dict(port=5580,name='owned')))
            stack.enter_context(patch.object(m4.e2e,'sdk_tool',return_value=Path('adb')))
            checked=stack.enter_context(patch.object(m4.e2e,'checked',side_effect=['MemTotal: 1900000 kB',processes_dump('  Active instrumentation:\n    Instrumentation #0: ActiveInstrumentation{fake}\n')]))
            with self.assertRaisesRegex(m4.e2e.Blocked,'serial'):m4.preflight(root,root,'emulator-5554')
            checked.assert_not_called()
            with self.assertRaisesRegex(m4.e2e.Blocked,'Concurrent'):m4.preflight(root,root,'emulator-5580')
            self.assertEqual(['shell','dumpsys','activity','processes'],checked.call_args.args[0][-4:])

    def test_api26_process_dump_without_active_instrumentation_is_accepted(self):
        self.assertIsNone(m4.require_no_instrumentation(processes_dump()))

    def test_api26_active_section_or_orphan_record_blocks(self):
        for active in ['  Active instrumentation:\n',
                       '    Instrumentation #0: ActiveInstrumentation{abc}\n',
                       '    InstrumentationRecord{abc}\n']:
            with self.subTest(active=active),self.assertRaisesRegex(m4.e2e.Blocked,'Concurrent'):
                m4.require_no_instrumentation(processes_dump(active))

    def test_unknown_malformed_or_truncated_process_dump_blocks(self):
        valid=processes_dump()
        for output in ['', 'Unknown command: instrumentation',
                       'ACTIVITY MANAGER INSTRUMENTATION\n',
                       valid.split('  Total persistent processes:')[0],
                       valid.replace('RUNNING PROCESSES','UNKNOWN FORMAT'),
                       valid+valid, valid+'Permission Denial\n',
                       valid+'DUMP TIMEOUT: dump timed out\n']:
            with self.subTest(output=output),self.assertRaises(m4.e2e.Blocked):
                m4.require_no_instrumentation(output)

    def test_worker_replacement_preserves_new_pid_epoch_and_start_token(self):
        j=self.data['journeys'][0]
        j.update(next_worker_pid=201,next_worker_stat=stat(201,77),next_worker_epoch='cccccccc-1111-1111-1111-111111111111',
                 next_worker_meminfo=mem(201,m4.ev.APP+':pdf',333))
        data=self.payload()
        self.assertEqual(201,data['journeys'][0]['next_worker_pid'])
        self.assertEqual(77,data['journeys'][0]['next_worker_start_ticks'])
        self.assertEqual(333,data['journeys'][0]['next_worker_pss_kb'])

    def test_partial_finalization_never_writes_pass_and_csv_columns_are_consistent(self):
        import csv
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);(out/'execution.log').write_text('synthetic')
            report=dict(status='BLOCKED',iterations=[self.iteration(True)],complete=False)
            m4.finalize(out,report)
            records=list(csv.reader((out/'samples.csv').read_text().splitlines()))
            self.assertTrue(all(len(r)==len(records[0]) for r in records))
            report['status']='PASS'
            with self.assertRaisesRegex(ValueError,'incomplete'):m4.finalize(out,report)
            self.assertEqual('BLOCKED',json.loads((out/'summary.json').read_text())['status'])

    def test_markdown_write_error_does_not_promote_previous_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);(out/'execution.log').write_text('synthetic')
            report=dict(status='BLOCKED',iterations=[],complete=False)
            m4.finalize(out,report)
            original=m4.ev.j_atomic
            def failing(root,name,value):
                if name=='summary.md':raise OSError('Synthetic disk error')
                return original(root,name,value)
            with patch.object(m4.ev,'j_atomic',side_effect=failing),self.assertRaises(OSError):m4.finalize(out,report)
            self.assertFalse(json.loads((out/'summary.json').read_text())['complete'])


if __name__=='__main__':unittest.main()
