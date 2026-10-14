"""Backup regressions: reject broad rules and verify the actual checked-in policy."""
import copy
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_backup_policy as policy


class BackupPolicyTests(unittest.TestCase):
    def setUp(self):
        self.legacy = ET.fromstring('<full-backup-content><include domain="file" path="datastore/app_preferences.preferences_pb"/></full-backup-content>')
        self.extraction = ET.fromstring('<data-extraction-rules><cloud-backup/><device-transfer/></data-extraction-rules>')
        for section in self.extraction:
            section.append(copy.deepcopy(self.legacy[0]))
        self.manifest = ET.Element("manifest")
        ET.SubElement(self.manifest, "application", {
            policy.ANDROID + "allowBackup": "true",
            policy.ANDROID + "fullBackupContent": "@xml/backup_rules",
            policy.ANDROID + "dataExtractionRules": "@xml/data_extraction_rules",
        })

    def test_checked_in_rules_and_manifest_are_restricted(self):
        root = Path(__file__).resolve().parents[2] / "app/src/main"
        policy.verify_rules(ET.parse(root / "res/xml/backup_rules.xml").getroot(),
                            ET.parse(root / "res/xml/data_extraction_rules.xml").getroot())
        policy.verify_manifest(ET.parse(root / "AndroidManifest.xml").getroot())

    def test_empty_allowlist_in_any_transport_is_rejected(self):
        for index in range(3):
            legacy, extraction = copy.deepcopy(self.legacy), copy.deepcopy(self.extraction)
            section = legacy if index == 0 else extraction[index - 1]
            section.remove(section[0])
            with self.assertRaises(ValueError): policy.verify_rules(legacy, extraction)

    def test_directory_traversal_other_files_and_domains_are_rejected(self):
        for attribute, values in {
            "path": [".", "datastore", "datastore/", "../databases/document-metadata.db", "/datastore/app_preferences.preferences_pb", "datastore/other.preferences_pb", "files/datastore/app_preferences.preferences_pb"],
            "domain": ["database", "root", "sharedpref", "device_file", "external"],
        }.items():
            for value in values:
                for index in range(3):
                    legacy, extraction = copy.deepcopy(self.legacy), copy.deepcopy(self.extraction)
                    section = legacy if index == 0 else extraction[index - 1]
                    section[0].set(attribute, value)
                    with self.assertRaises(ValueError): policy.verify_rules(legacy, extraction)

    def test_extra_room_or_sqlite_sidecar_include_is_rejected(self):
        for name in ["document-metadata.db", "document-metadata.db-wal", "document-metadata.db-shm", "document-metadata.db-journal"]:
            for index in range(3):
                legacy, extraction = copy.deepcopy(self.legacy), copy.deepcopy(self.extraction)
                section = legacy if index == 0 else extraction[index - 1]
                ET.SubElement(section, "include", domain="database", path=name)
                with self.assertRaises(ValueError): policy.verify_rules(legacy, extraction)

    def test_missing_duplicate_or_unrestricted_transfer_section_is_rejected(self):
        for action in ["missing", "duplicate", "extra"]:
            extraction = copy.deepcopy(self.extraction)
            if action == "missing": extraction.remove(extraction[1])
            elif action == "duplicate": extraction[1].tag = "cloud-backup"
            else: ET.SubElement(extraction, "unknown-transport")
            with self.assertRaises(ValueError): policy.verify_rules(self.legacy, extraction)

    def test_disabled_backup_wrong_references_and_custom_agent_are_rejected(self):
        for name, value in [("allowBackup", "false"), ("fullBackupContent", "true"),
                            ("dataExtractionRules", "@xml/unrestricted"), ("backupAgent", ".UnsafeAgent")]:
            manifest = copy.deepcopy(self.manifest)
            manifest[0].set(policy.ANDROID + name, value)
            with self.assertRaises(ValueError): policy.verify_manifest(manifest)

    def test_missing_manifest_backup_attributes_are_rejected(self):
        for name in ["allowBackup", "fullBackupContent", "dataExtractionRules"]:
            manifest = copy.deepcopy(self.manifest)
            del manifest[0].attrib[policy.ANDROID + name]
            with self.assertRaises(ValueError): policy.verify_manifest(manifest)

    def test_compiled_xml_parser_preserves_unsafe_extra_include(self):
        dump = 'E: full-backup-content (line=1)\n  E: include (line=2)\n    A: domain="file"\n    A: path="datastore/app_preferences.preferences_pb"\n'
        parsed = policy.xmltree(dump)
        policy.verify_rules(parsed, self.extraction)
        unsafe = dump + '  E: include (line=3)\n    A: domain="database"\n    A: path="."\n'
        with self.assertRaises(ValueError): policy.verify_rules(policy.xmltree(unsafe), self.extraction)


if __name__ == "__main__":
    unittest.main()
