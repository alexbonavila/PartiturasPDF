"""Behavioral tests for the fail-closed CI validators; never edit app resources."""

from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from quality_gate import MANDATORY, failures
from validate_localization import arguments, validate
from verify_test_results import verify


class LocalizationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def bundle(self, en, ca=None, es=None):
        for locale, text in zip(("values", "values-ca", "values-es"),
                                (en, en if ca is None else ca, en if es is None else es)):
            directory = self.root / locale
            directory.mkdir(exist_ok=True)
            (directory / "strings.xml").write_text(f"<resources>{text}</resources>")

    def test_complete_translations_and_reordered_indices(self):
        self.bundle('<string name="progress">%1$s: %2$d</string>',
                    '<string name="progress">%2$d: %1$s</string>')
        self.assertEqual([], validate(self.root))

    def test_missing_language_file(self):
        self.bundle('<string name="app_name">App</string>')
        (self.root / "values-ca/strings.xml").unlink()
        self.assertTrue(validate(self.root))

    def test_missing_resource(self):
        self.bundle('<string name="a">A</string><string name="b">B</string>',
                    '<string name="a">A</string>')
        self.assertTrue(any("missing translation: b" in e for e in validate(self.root)))

    def test_type_index_and_multiplicity_mismatches(self):
        for translation in ("%1$d", "%2$s", "%1$s %1$s", "No argument"):
            with self.subTest(translation=translation):
                self.bundle('<string name="a">%1$s</string>',
                            f'<string name="a">{translation}</string>')
                self.assertTrue(validate(self.root))

    def test_relative_arguments_escaped_percent_and_newline(self):
        self.assertEqual(arguments("%1$s %1$s"), arguments("%s %&lt;s".replace("&lt;", "<")))
        self.assertEqual({}, arguments("100%% %n"))
        with self.assertRaises(ValueError):
            arguments("%<s")

    def test_date_conversion_mismatch(self):
        self.bundle('<string name="date">%1$tY</string>',
                    '<string name="date">%1$tm</string>')
        self.assertTrue(validate(self.root))

    def test_malformed_argument_is_not_ignored(self):
        for value in ("%1$", "%q", "%0$s", "%tq", "100%"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                arguments(value)

    def test_plural_categories_can_differ_but_arguments_cannot(self):
        en = '<plurals name="pages"><item quantity="one">%1$d page</item><item quantity="other">%1$d pages</item></plurals>'
        ca = '<plurals name="pages"><item quantity="many">%1$d pàgines</item><item quantity="other">%1$d pàgines</item></plurals>'
        self.bundle(en, ca)
        self.assertEqual([], validate(self.root))
        self.bundle(en, ca.replace("%1$d", "%1$s"))
        self.assertTrue(validate(self.root))

    def test_arrays_and_styled_strings(self):
        self.bundle('<string-array name="items"><item>%1$s</item><item>B</item></string-array><string name="bold"><b>%1$d</b> items</string>')
        self.assertEqual([], validate(self.root))
        self.bundle('<string-array name="items"><item>A</item><item>B</item></string-array>',
                    '<string-array name="items"><item>A</item></string-array>')
        self.assertTrue(validate(self.root))

    def test_reject_empty_duplicate_untranslated_and_extra_resources(self):
        for xml in ('<string name="a"/>',
                    '<string name="a">A</string><string name="a">B</string>',
                    '<string name="a" translatable="false">A</string>',
                    '<string name="a">A</string><string name="extra">B</string>'):
            with self.subTest(xml=xml):
                self.bundle('<string name="a">A</string>', xml)
                self.assertTrue(validate(self.root))

    def test_formatted_false_literals_and_flag_mismatch(self):
        literal = '<string name="a" formatted="false">100% complete</string>'
        self.bundle(literal)
        self.assertEqual([], validate(self.root))
        self.bundle(literal, literal.replace('formatted="false"', 'formatted="true"'))
        self.assertTrue(validate(self.root))

    def test_malformed_xml_and_alias_fail(self):
        self.bundle('<string name="a">A</string>', '<string name="a">@string/b</string>')
        self.assertTrue(validate(self.root))
        (self.root / "values-es/strings.xml").write_text("<resources>")
        self.assertTrue(validate(self.root))


class TestEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def report(self, xml):
        (self.root / "TEST.xml").write_text(xml)

    def test_missing_empty_and_malformed_reports_fail(self):
        with self.assertRaises(ValueError):
            verify(self.root)
        for xml in ("<testsuite/>", "<unrelated/>"):
            self.report(xml)
            with self.assertRaises(ValueError):
                verify(self.root)
        self.report("<testsuite>")
        with self.assertRaises(ET.ParseError):
            verify(self.root)

    def test_failed_error_skipped_and_disabled_fail(self):
        for kind in ("failure", "error", "skipped"):
            self.report(f'<testsuite><testcase name="smoke"><{kind}/></testcase></testsuite>')
            with self.assertRaises(ValueError):
                verify(self.root)
        self.report('<testsuite disabled="1"><testcase name="smoke"/></testsuite>')
        with self.assertRaises(ValueError):
            verify(self.root)

    def test_required_smoke_must_execute(self):
        self.report('<testsuites><testsuite tests="1"><testcase classname="Smoke" name="executed"/></testsuite></testsuites>')
        self.assertEqual(1, verify(self.root, "Smoke.executed"))
        with self.assertRaises(ValueError):
            verify(self.root, "Smoke.missing")


class QualityGateTests(unittest.TestCase):
    def test_all_mandatory_checks_successful(self):
        self.assertEqual([], failures({job: {"result": "success"} for job in MANDATORY}))

    def test_missing_failed_cancelled_and_skipped_checks_block(self):
        for result in (None, "failure", "cancelled", "skipped", "neutral"):
            for job in MANDATORY:
                with self.subTest(result=result, job=job):
                    needs = {name: {"result": "success"} for name in MANDATORY}
                    if result is None:
                        del needs[job]
                    else:
                        needs[job]["result"] = result
                    self.assertEqual([job], failures(needs))

    def test_compatibility_required_on_scheduled_and_requested_runs(self):
        needs = {job: {"result": "success"} for job in MANDATORY}
        needs["compatibility"] = {"result": "skipped"}
        self.assertEqual([], failures(needs))
        self.assertEqual(["compatibility"], failures(needs, True))
        needs["compatibility"]["result"] = "success"
        self.assertEqual([], failures(needs, True))


if __name__ == "__main__":
    unittest.main()
