"""Independent-oracle regressions over generated private PDFs; NOT executed by Codex."""
from argparse import Namespace
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import collect_pdfbox_spike as c
import verify_pdfbox_outputs as v

class VerifierTests(unittest.TestCase):
    def pdf(self, rotations=(0,0), reverse=False, missing=False):
        from pypdf import PdfWriter
        from pypdf.generic import DecodedStreamObject, NameObject
        writer=PdfWriter()
        indices=list(reversed(range(len(rotations)))) if reverse else list(range(len(rotations)))
        for index in indices:
            page=writer.add_blank_page(200,300)
            stream=DecodedStreamObject();stream.set_data(f'q {index} 0 10 10 re f Q'.encode())
            if not missing: page[NameObject('/Contents')]=writer._add_object(stream)
            page.rotate(rotations[index])
        return writer
    def test_wrong_order_rotation_and_missing_contents(self):
        source=self.pdf()
        for output in [self.pdf(reverse=True),self.pdf(rotations=(90,0)),self.pdf(missing=True)]:
            with self.assertRaises(AssertionError): v.structure(source,output,0,0,0)
    def test_multiple_and_empty_content_streams(self):
        from pypdf.generic import ArrayObject, DecodedStreamObject, NameObject
        writer=self.pdf();page=writer.pages[0]
        page.pop('/Contents');self.assertEqual(b'',v.page_contents(page))
        a,b=DecodedStreamObject(),DecodedStreamObject();a.set_data(b'a');b.set_data(b'b')
        page[NameObject('/Contents')]=ArrayObject([writer._add_object(a),writer._add_object(b)])
        self.assertEqual(b'a\nb\n',v.page_contents(page))
    def test_wrong_annotation_page_is_rejected(self):
        from pypdf.generic import DictionaryObject, NameObject, ArrayObject
        source,output=self.pdf(),self.pdf()
        for writer,wrong in [(source,False),(output,True)]:
            annotation=DictionaryObject({NameObject('/Type'):NameObject('/Annot'),NameObject('/Subtype'):NameObject('/Text'),
                NameObject('/P'):writer.pages[1 if wrong else 0].indirect_reference})
            writer.pages[0][NameObject('/Annots')]=ArrayObject([writer._add_object(annotation)])
        with self.assertRaisesRegex(AssertionError,'containing output page'): v.structure(source,output,0,0,0)
    def test_unknown_filter_and_cycle_block(self):
        from pypdf.generic import StreamObject,NameObject,DictionaryObject
        stream=StreamObject();stream._data=b'unknown';stream[NameObject('/Filter')]=NameObject('/JPXDecode')
        with self.assertRaises(v.Unsupported): v.semantic(stream)
        loop=DictionaryObject();loop[NameObject('/Self')]=loop
        with self.assertRaises(v.Unsupported): v.semantic(loop)
    def test_incomplete_flate_blocks(self):
        from pypdf.generic import StreamObject,NameObject
        stream=StreamObject();stream._data=b'x';stream[NameObject('/Filter')]=NameObject('/FlateDecode')
        with self.assertRaises((v.Unsupported, __import__('zlib').error)): v.decoded(stream)
    def test_pixel_metrics_detect_changed_notation(self):
        from PIL import Image
        a=Image.new('RGB',(10,10),'white');b=a.copy();b.putpixel((5,5),(0,0,0))
        metrics,diff=v.image_metrics(a,b)
        self.assertEqual('REVIEW',metrics['status']);self.assertEqual(1,metrics['different_pixels'])
        self.assertEqual(255,metrics['max_channel_difference']);self.assertGreater(metrics['rmse'],0)
        self.assertEqual('FAIL',v.image_metrics(a,Image.new('RGB',(11,10)))[0]['status'])
    def test_reference_rotations_90_180_270(self):
        import fitz
        for turns in (1,2,3):
            with self.subTest(turns=turns),fitz.open() as source:
                page=source.new_page(width=200,height=300);page.insert_text((30,40),'SCORE 123 #')
                with fitz.open(stream=source.tobytes(),filetype='pdf') as output:
                    original=source[0].rotation
                    source[0].set_rotation((original+90*turns)%360)
                    output[0].set_rotation((original+90*turns)%360)
                    metrics,images,diff=v.visual(source[0],output[0],144)
                    self.assertTrue(metrics['exact_equal']);self.assertEqual('PASS',metrics['status'])
                    source[0].set_rotation(original)
    def test_incomplete_and_unsafe_aggregate(self):
        self.assertEqual('BLOCKED',v.aggregate([dict(status='PASS')],['missing']))
        self.assertEqual('KNOWN_UNSAFE',v.aggregate([dict(status='KNOWN_UNSAFE')],[]))
        self.assertEqual('FAIL',v.aggregate([dict(status='FAIL'),dict(status='PASS')],[]))
        self.assertEqual(27,sum(v.expected_recipes().values()))
    def collection(self, root, approach='WORKING_COPY', fixture='01'):
        import fitz
        fixtures=root/'fixtures';fixtures.mkdir()
        with fitz.open() as doc:
            page=doc.new_page(width=200,height=300);page.insert_text((30,40),'Synthetic score A')
            content=doc.tobytes()
        entries=[]
        for i in range(1,16):
            name=f'{i:02d}.pdf';(fixtures/name).write_bytes(content)
            entries.append(dict(id=f'{i:02d}',path=name,bytes=len(content),sha256=hashlib.sha256(content).hexdigest(),
                                expected_status='valid',expected_pages=1))
        (fixtures/'manifest.json').write_text(json.dumps(dict(files=entries)))
        mh=c.sha(fixtures/'manifest.json');collection=root/'collection';collection.mkdir()
        ns='m-pdfbox-spike-12345678-1234-1234-1234-123456789abc';local=collection/ns;local.mkdir()
        recipe=dict(file='result-2.pdf',bytes=len(content),sha256=hashlib.sha256(content).hexdigest(),fixture=fixture,
                    source_sha256=entries[0]['sha256'],indices=[0],pages=1,extra_quarter_turns=0,approach=approach)
        (local/'result-2.pdf').write_bytes(content)
        data=dict(candidate=c.CANDIDATE,api=26,pid=123,case='identity01',manifest_sha256=mh,outputs=[recipe],
                  measurements=[],cleanup='GENERATED_OUTPUTS_RETAINED_BY_OWNER_REQUEST')
        (local/'evidence.json').write_text(json.dumps(data))
        meta=dict(status='COLLECTED',schema=1,api=26,serial='emulator-5554',candidate=c.CANDIDATE,
                  declared_commit='c'*40,manifest_sha256=mh,namespaces=[dict(namespace=ns,
                  evidence_sha256=c.sha(local/'evidence.json'),files=[{k:recipe[k] for k in ('file','bytes','sha256')}])])
        (collection/'collection-manifest.json').write_text(json.dumps(meta))
        return Namespace(collection=str(collection),fixtures=str(fixtures),report=str(root/'report'),dpi=144,retain_images=False)
    def test_reports_preserve_subset_and_nonzero_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);args=self.collection(root)
            self.assertEqual(1,v.verify(args))
            report=json.loads((root/'report/validation-report.json').read_text())
            self.assertEqual('BLOCKED',report['status']);self.assertTrue(report['missing'])
            self.assertEqual('PASS',report['outputs'][0]['status'])
            self.assertTrue((root/'report/validation-report.md').is_file())
    def test_known_unsafe_never_becomes_positive(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);args=self.collection(root,'STOCK_IMPORT','12')
            self.assertNotEqual(0,v.verify(args))
            report=json.loads((root/'report/validation-report.json').read_text())
            self.assertEqual('KNOWN_UNSAFE',report['outputs'][0]['status'])
    def test_rehashed_tampering_and_missing_output_block(self):
        for missing in (False,True):
            with tempfile.TemporaryDirectory() as directory:
                root=Path(directory);args=self.collection(root)
                pdf=next((root/'collection').glob('*/result-2.pdf'))
                if missing: pdf.unlink()
                else: pdf.write_bytes(b'changed')
                self.assertNotEqual(0,v.verify(args))
    def test_complete_rotated_output_pipeline(self):
        import fitz
        for turns in (1,2,3):
            with self.subTest(turns=turns),tempfile.TemporaryDirectory() as directory:
                root=Path(directory);args=self.collection(root)
                local=next(p for p in (root/'collection').iterdir() if p.is_dir())
                output=local/'result-2.pdf'
                with fitz.open(stream=output.read_bytes(),filetype='pdf') as doc:
                    doc[0].set_rotation(90*turns);changed=doc.tobytes()
                output.write_bytes(changed)
                data=json.loads((local/'evidence.json').read_text())
                recipe=data['outputs'][0];recipe.update(extra_quarter_turns=turns,bytes=len(changed),sha256=c.sha(output))
                (local/'evidence.json').write_text(json.dumps(data))
                mp=root/'collection/collection-manifest.json';meta=json.loads(mp.read_text())
                meta['namespaces'][0].update(evidence_sha256=c.sha(local/'evidence.json'),
                    files=[{k:recipe[k] for k in ('file','bytes','sha256')}])
                mp.write_text(json.dumps(meta))
                self.assertEqual(1,v.verify(args)) # Deliberately incomplete corpus, page itself must pass.
                report=json.loads((root/'report/validation-report.json').read_text())
                self.assertEqual('PASS',report['outputs'][0]['status'])
                self.assertTrue(report['outputs'][0]['pages'][0]['visual']['exact_equal'])
    def test_bad_dpi_and_existing_report_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);args=self.collection(root);args.dpi=1000
            with self.assertRaises(c.Blocked):v.verify(args)
            args.dpi=144;(root/'report').mkdir()
            with self.assertRaises(c.Blocked):v.verify(args)

if __name__=='__main__': unittest.main()
