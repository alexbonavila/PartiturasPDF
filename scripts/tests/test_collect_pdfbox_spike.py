"""Source-only regression tests. Mocks never operate a device."""
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import collect_pdfbox_spike as c

NAME = 'm-pdfbox-spike-12345678-1234-1234-1234-123456789abc'
PAYLOAD = b'%PDF\x00\xff\r\n'
ENTRY = dict(id='01', expected_pages=1, expected_status='valid', sha256='a'*64)

def data():
    return dict(candidate=c.CANDIDATE, api=26, pid=12, case='identity01', manifest_sha256='b'*64,
                cleanup='GENERATED_OUTPUTS_RETAINED_BY_OWNER_REQUEST', measurements=[], outputs=[dict(
                    file='result-2.pdf', bytes=len(PAYLOAD), sha256=hashlib.sha256(PAYLOAD).hexdigest(),
                    fixture='01', source_sha256='a'*64, indices=[0], extra_quarter_turns=0,
                    approach='WORKING_COPY', pages=1)])

class CollectorTests(unittest.TestCase):
    def check(self, value): return c.validate_evidence(value, 'b'*64, {'01': ENTRY}, 26)
    def test_valid_namespace(self): self.assertEqual(NAME, c.namespace(NAME))
    def test_bad_names(self):
        for name in [NAME+'/../x', '../'+NAME, 'm-pdfbox-spike-x', NAME.upper(), '']:
            with self.subTest(name=name), self.assertRaises(c.Blocked): c.namespace(name)
    def test_valid_recipe(self): self.assertEqual(1,len(self.check(data())))
    def test_reject_traversal_and_duplicate_recipes(self):
        for value in ['../result.pdf', 'x/result.pdf', 'C:\\x.pdf', 'result-1.pdf/..']:
            d=data(); d['outputs'][0]['file']=value
            with self.assertRaises(c.Blocked): self.check(d)
        d=data(); d['outputs']*=2
        with self.assertRaises(c.Blocked): self.check(d)
    def test_metadata_errors(self):
        for field,value in [('bytes',0),('bytes',c.OUTPUT_LIMIT+1),('fixture','99'),('source_sha256','f'*64),
                            ('indices',[1]),('indices',[0,0]),('extra_quarter_turns',4),('sha256','bad')]:
            d=data(); d['outputs'][0][field]=value
            with self.subTest(field=field),self.assertRaises(c.Blocked): self.check(d)
        d=data(); d['api']=27
        with self.assertRaises(c.Blocked): self.check(d)
    def test_json_invalid_duplicate_missing_oversized(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/'evidence.json'
            for raw in [b'{', b'\xff', b'{"x":1,"x":2}']:
                p.write_bytes(raw)
                with self.assertRaises(c.Blocked): c.load_json(p)
            with self.assertRaises(c.Blocked): c.load_json(p,1)
            p.unlink()
            with self.assertRaises(c.Blocked): c.load_json(p)
    def test_path_symlink_and_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(c.Path,'is_symlink',return_value=True):
                with self.assertRaises(c.Blocked): c.safe_path(Path(directory),'safe.pdf')
            for path in ['../x','/x','a/../b','C:/x']:
                with self.assertRaises(c.Blocked): c.safe_path(Path(directory),path)
    def fake_process(self, payload, code=0):
        process=Mock(stdout=io.BytesIO(payload), stderr=io.BytesIO(b''), returncode=code)
        process.wait.return_value=code
        return process
    def test_binary_transfer_exact_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/'x.pdf'
            with patch.object(c.subprocess,'Popen',return_value=self.fake_process(PAYLOAD)) as start:
                c.transfer(['adb','explicit-args'],p,100)
            self.assertEqual(PAYLOAD,p.read_bytes())
            self.assertFalse(start.call_args.kwargs['shell'])
            with self.assertRaises(FileExistsError): c.transfer([],p,100)
    def test_transfer_error_and_excess(self):
        for payload,code,limit in [(PAYLOAD,1,100),(PAYLOAD,0,1)]:
            with tempfile.TemporaryDirectory() as directory:
                with patch.object(c.subprocess,'Popen',return_value=self.fake_process(payload,code)):
                    with self.assertRaises(c.Blocked): c.transfer([],Path(directory)/'x',limit)
    def test_collection_truncated_and_hash_mismatch_report_blocked(self):
        from argparse import Namespace
        for payload in [b'', b'x'*len(PAYLOAD)]:
            with tempfile.TemporaryDirectory() as directory:
                out=Path(directory)/'collection'
                args=Namespace(serial='emulator-5554',commit='c'*40,namespace=[NAME],output=str(out),adb='adb')
                def remote(command,destination,limit):
                    destination.write_bytes(json.dumps(data()).encode() if destination.name=='evidence.json' else payload)
                with patch.object(c,'canonical',return_value=('b'*64,{'01':ENTRY})), \
                     patch.object(c.subprocess,'run',return_value=Mock(returncode=0,stdout=b'26\n',stderr=b'')), \
                     patch.object(c,'transfer',side_effect=remote):
                    self.assertEqual(2,c.collect(args))
                self.assertEqual('BLOCKED',json.loads((out/'collection-manifest.json').read_text())['status'])
    def test_empty_recipe_is_not_negative_pass(self):
        d=data();d['outputs']=[]
        self.assertEqual([],self.check(d))

if __name__=='__main__': unittest.main()
