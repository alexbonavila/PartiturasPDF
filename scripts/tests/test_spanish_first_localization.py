"""Explicit Spanish-first staging never exempts completed translations or Spanish validity."""
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_localization import validate

class SpanishFirstTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for locale in ('values', 'values-ca', 'values-es'):
            (self.root / locale).mkdir()
            self.put(locale, 'strings.xml', '<string name="existing">%1$s</string>')
        self.new = '<string name="library_empty">Sin partituras %1$d</string>'
    def put(self, locale, file, content):
        (self.root / locale / file).write_text('<resources>'+content+'</resources>')
    def stage(self):
        self.put('values', 'library_spanish_first.xml', self.new)
        self.put('values-es', 'library.xml', self.new)
    def test_spanish_stage_passes_without_claiming_catalan_translation(self):
        self.stage()
        self.assertEqual([], validate(self.root))
    def test_spanish_is_mandatory(self):
        self.stage()
        (self.root/'values-es/library.xml').unlink()
        self.assertTrue(validate(self.root))
    def test_existing_translation_remains_mandatory(self):
        self.stage()
        self.put('values-ca','strings.xml','<string name="other">Otro</string>')
        self.assertTrue(validate(self.root))
    def test_staged_fallback_must_be_spanish_copy(self):
        self.stage()
        self.put('values-es','library.xml',self.new.replace('Sin partituras','Diferente'))
        self.assertTrue(validate(self.root))
    def test_present_translation_still_requires_format_contract(self):
        self.stage()
        self.put('values-ca','library.xml',self.new.replace('%1$d','%1$s'))
        self.assertTrue(validate(self.root))
    def test_unstaged_keys_keep_parity(self):
        self.put('values','library.xml',self.new)
        self.put('values-es','library.xml',self.new)
        self.assertTrue(validate(self.root))
    def test_duplicate_malformed_and_bad_format_are_rejected(self):
        self.stage()
        for content in (self.new+self.new, self.new.replace('%1$d','%q'), '<string>'):
            self.put('values','library_spanish_first.xml',content)
            self.assertTrue(validate(self.root))
    def test_completed_starter_cannot_be_reclassified(self):
        self.stage()
        self.put('values','library_spanish_first.xml', '<string name="app_name">App</string>')
        self.put('values-es','library.xml','<string name="app_name">App</string>')
        self.assertTrue(validate(self.root))
