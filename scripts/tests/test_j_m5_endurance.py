"""Host-only M5-A integrity regressions; no device operations."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import j_m5_endurance as m5
import test_j_m4_benchmark as m4tests
from test_local_regression import protocol

class M5Tests(unittest.TestCase):
    def data(self):
        helper=m4tests.M4Tests();helper.setUp();base=helper.payload();journeys=[];samples=[]
        for index,fid in enumerate(['02','10','05']*3):
            j=copy.deepcopy(base['journeys'][0]);j['fixture']=fid;j['sha256']=fid*32
            for key in list(j):
                if key.endswith('_ns'):j[key]+=index*10_000_000
            j['closed_main_pss_kb']=100+index
            journeys.append(j)
            samples.append(dict(begin_ns=j['open_begin_ns'],end_ns=j['next_presented_ns'],processes=[dict(pid=99,start_ticks=44,name=m5.ev.APP,pss_kb=150),dict(pid=101,start_ticks=50,name=m5.ev.APP+':pdf',pss_kb=123)]))
        base['journeys']=journeys
        return base,samples

    def test_repeated_fixture_order_has_same_process_and_growth_is_review_only(self):
        data,samples=self.data();r=m5.observations(data,samples,3)
        self.assertTrue(r['review_required']);self.assertEqual([100,103,106],r['trends']['02']['post_close_kib'])
        self.assertEqual(6,r['trends']['02']['net_change_kib'])
        self.assertEqual(123,r['sampled_max_kib'][m5.ev.APP+':pdf'])

    def test_flat_memory_does_not_prove_absence_of_leaks(self):
        data,samples=self.data()
        for j in data['journeys']:j['closed_main_pss_kb']=100
        self.assertFalse(m5.observations(data,samples,3)['review_required'])

    def test_missing_phase_and_wrong_process_token_block(self):
        data,samples=self.data()
        with self.assertRaises(ValueError):m5.observations(data,[],3)
        with self.assertRaises(ValueError):m5.observations(data,samples[:-1],3)
        samples[0]['processes'][0]['start_ticks']=999
        with self.assertRaises(ValueError):m5.observations(data,samples,3)

    def test_sequence_payload_uses_real_junit_identity_and_full_inventory(self):
        data,_=self.data()
        data['main_stat']='99 (app) '+' '.join(['S']+['0']*18+['44'])
        for j in data['journeys']:
            j['worker_stat']=j['next_worker_stat']='101 (worker) '+' '.join(['S']+['0']*18+['50'])
            for prefix in ('presented','next'):
                j[prefix+'_worker_meminfo']='** MEMINFO in pid 101 ['+m5.ev.APP+':pdf] **\n TOTAL 123 10 20\n'
        raw=protocol([(m5.METHOD,0)]).replace('INSTRUMENTATION_CODE: -1','INSTRUMENTATION_RESULT: m5='+json.dumps(data)+'\nINSTRUMENTATION_CODE: -1')
        rows={f:dict(sha256=f*32) for f in ('02','10','05')}
        parsed=m5.m4.result_payload(raw,'a'*32,rows,method=m5.METHOD,sequence=['02','10','05']*3,channel='m5')
        self.assertEqual(9,len(parsed['journeys']))
        with self.assertRaises(ValueError):m5.m4.result_payload(raw,'a'*32,rows,method=m5.METHOD,sequence=['02','10','05']*2,channel='m5')

    def test_cycle_bounds_and_physical_serial_block_before_device_operations(self):
        with patch.object(m5,'run') as run,patch.object(m5.e2e,'discover_sdk') as sdk:
            for count in ('1','11'):
                self.assertEqual(2,m5.main(['run','--serial','emulator-5580','--cycles',count]))
            self.assertEqual(2,m5.main(['run','--serial','physical']))
            run.assert_not_called();sdk.assert_not_called()

    def test_blocked_preflight_retains_only_four_reports_without_device_writes(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(m5,'ROOT',Path(tmp)),patch.object(m5.m4,'preflight',side_effect=m5.e2e.Blocked('unowned')),patch.object(m5.m4,'Probe') as probe:
            from argparse import Namespace
            self.assertEqual(2,m5.run(Namespace(home=Path(tmp)/'home',sdk=Path(tmp),serial='emulator-5580',cycles=3)))
            probe.assert_not_called()
            folder=next((Path(tmp)/'build/j-m5/reports').iterdir())
            self.assertEqual(m5.m4.OUTPUTS,{f.name for f in folder.iterdir()})
            self.assertFalse(json.loads((folder/'summary.json').read_text())['complete'])

    def test_opt_in_packaging_source_and_canonical_inventory(self):
        self.assertNotIn(m5.METHOD,m5.ev.android_inventory(m5.ROOT/'app/src/androidTest'))
        self.assertEqual({m5.METHOD},m5.ev.android_inventory(m5.ROOT/'scripts/j-m5/androidTest',None))
        self.assertEqual(100,m5.fixtures()['05']['expected_pages'])

    def test_incomplete_report_cannot_be_promoted_to_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);(out/'execution.log').touch()
            report=dict(status='BLOCKED',complete=False,cycles=3,samples=[])
            m5.finalize(out,report)
            report['status']='PASS'
            with self.assertRaises(ValueError):m5.finalize(out,report)
            self.assertEqual('BLOCKED',json.loads((out/'summary.json').read_text())['status'])
