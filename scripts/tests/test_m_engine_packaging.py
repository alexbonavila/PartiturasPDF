"""Synthetic ZIP tests, no build or Android execution. Not run by Codex."""
import sys
from pathlib import Path
import tempfile
import unittest
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import verify_m_engine_packaging as tool

class PackagingTests(unittest.TestCase):
    def apk(self,path,extra=None,markers=True,notices=True):
        with zipfile.ZipFile(path,'w') as archive:
            archive.writestr('classes.dex',b'Lcom/tom_roush/pdfbox/X;Lorg/bouncycastle/X;Lcom/abdev/partituraspdf/pdf/editing/ProductionPdfAdapters;' if markers else b'other')
            if notices:
                for notice in tool.NOTICES:archive.writestr('assets/third_party/'+notice,b'notice')
            if extra:archive.writestr(extra,b'synthetic')
    def test_limited_main_assertions(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'main.apk';self.apk(path)
            self.assertEqual('PASS',tool.inspect(path)['status'])
    def test_fixture_notice_and_dependency_regressions(self):
        for config in [dict(extra='assets/pdf/source.pdf'),dict(notices=False),dict(markers=False)]:
            with tempfile.TemporaryDirectory() as tmp:
                path=Path(tmp)/'main.apk';self.apk(path,**config)
                with self.assertRaises(ValueError):tool.inspect(path)
    def test_test_code_leak(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'main.apk';self.apk(path)
            with zipfile.ZipFile(path,'a') as archive:archive.writestr('classes2.dex',b'Lcom/abdev/partituraspdf/pdf/engine/spike/Test;')
            with self.assertRaises(ValueError):tool.inspect(path)
    def test_cli_failure_is_nonzero(self):
        self.assertEqual(1,tool.main(['--debug','missing','--release','missing','--test','missing']))

if __name__=='__main__':unittest.main()
