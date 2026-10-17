"""New private stores must remain outside every compiled-source Policy B allowlist."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[2]
class FoundationBackupTests(unittest.TestCase):
    def test_each_transport_selects_only_settings_not_root_positions_or_room(self):
        legacy=ET.parse(ROOT/'app/src/main/res/xml/backup_rules.xml').getroot()
        extraction=ET.parse(ROOT/'app/src/main/res/xml/data_extraction_rules.xml').getroot()
        for rules in (legacy, extraction.find('cloud-backup'), extraction.find('device-transfer')):
            self.assertIsNotNone(rules)
            self.assertEqual([('file','datastore/app_preferences.preferences_pb')],[(e.get('domain'),e.get('path')) for e in rules])
            for domain,path in [('file','foundation/selected-root.bin'),('sharedpref','reader_positions.xml'),('database','document-metadata.db')]:
                self.assertNotIn((domain,path),[(e.get('domain'),e.get('path')) for e in rules])
    def test_production_store_paths_are_separate_from_settings_proto(self):
        sources=ROOT/'app/src/main/java/com/abdev/partituraspdf/data/foundation'
        root=(sources/'PrivateSelectedRootRepository.kt').read_text()
        positions=(sources/'SharedPreferencesReaderPositions.kt').read_text()
        self.assertIn('"foundation/selected-root.bin"',root)
        self.assertIn('getSharedPreferences("reader_positions"',positions)
        self.assertNotIn('app_preferences',root+positions)
