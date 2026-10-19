"""Complete delivery is stricter than historical Spanish-first staging support."""
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_localization import validate

ROOT = Path(__file__).resolve().parents[2] / 'app/src/main/res'

class CompleteLocalizationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for locale, copy in [('values', 'Página %1$d'), ('values-es', 'Página %1$d'),
                             ('values-en', 'Page %1$d'), ('values-ca', 'Pàgina %1$d')]:
            (self.root / locale).mkdir()
            self.put(locale, copy)

    def put(self, locale, copy):
        (self.root / locale / 'strings.xml').write_text(
            '<resources><string name="page">'+copy+'</string></resources>', encoding='utf-8')

    def test_complete_bundles_pass(self):
        self.assertEqual([], validate(self.root, require_complete=True))

    def test_english_is_required_even_if_directory_disappears(self):
        (self.root/'values-en/strings.xml').unlink()
        (self.root/'values-en').rmdir()
        self.assertTrue(validate(self.root, require_complete=True))

    def test_english_missing_id_and_wrong_placeholder_are_rejected(self):
        for copy in ('', 'Page %1$s'):
            with self.subTest(copy=copy):
                self.put('values-en', copy)
                self.assertTrue(validate(self.root, require_complete=True))

    def test_complete_delivery_rejects_staging(self):
        for locale in ('values','values-es'):
            (self.root/locale/'feature_spanish_first.xml').write_text(
                '<resources><string name="feature">Nuevo</string></resources>')
        self.assertTrue(validate(self.root, require_complete=True))

    def test_non_spanish_default_is_rejected_despite_matching_formats(self):
        self.put('values', 'Page %1$d')
        self.assertTrue(validate(self.root, require_complete=True))

    def test_duplicate_cross_file_is_rejected(self):
        (self.root/'values-en/other.xml').write_bytes((self.root/'values-en/strings.xml').read_bytes())
        self.assertTrue(validate(self.root, require_complete=True))

    def test_actual_resources_have_complete_ids_and_no_staging(self):
        self.assertEqual([], validate(ROOT, require_complete=True))
        expected = {e.get('name') for p in (ROOT/'values').glob('*.xml')
                    for e in ET.parse(p).getroot() if e.tag == 'string'}
        self.assertEqual(155, len(expected))
        for locale in ('values','values-es','values-en','values-ca'):
            self.assertFalse(list((ROOT/locale).glob('*_spanish_first.xml')))
            actual = {e.get('name') for p in (ROOT/locale).glob('*.xml')
                      for e in ET.parse(p).getroot() if e.tag == 'string'}
            self.assertEqual(expected, actual)

    def test_debug_acceptance_ui_is_also_complete_and_spanish_default(self):
        debug = ROOT.parent.parent / 'debug/res'
        self.assertEqual([], validate(debug, require_complete=True))
        for locale in ('values','values-es','values-ca','values-en'):
            names = [e.get('name') for e in ET.parse(debug/locale/'strings.xml').getroot()]
            self.assertEqual(20, len(names))
