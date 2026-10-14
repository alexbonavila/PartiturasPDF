"""Synthetic policy evidence only; real binary parsing is tested by the Java infrastructure task."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("persistence_coverage", SCRIPTS / "persistence_coverage.py")
pc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pc)


class CoveragePolicyTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.started = int(time.time() * 1000) - 1000

    def tearDown(self):
        self.temporary.cleanup()

    def report_fixture(self, groups):
        root = ET.Element("report", name="policy-test-only")
        package = ET.SubElement(root, "package", name=pc.PREFIX + "metadata")
        classes = {}
        totals = [0, 0, 0, 0]
        for index, (source, counts) in enumerate(groups.items()):
            covered, lines, branches_covered, branches = counts
            name = pc.PREFIX + "metadata/Fixture" + str(index)
            classes[name] = {"source": source, "lines": list(range(1, lines + 1)), "branches": branches}
            for node in [ET.SubElement(package, "class", name=name), ET.SubElement(package, "sourcefile", name=source)]:
                ET.SubElement(node, "counter", type="LINE", covered=str(covered), missed=str(lines - covered))
                if branches:
                    ET.SubElement(node, "counter", type="BRANCH", covered=str(branches_covered), missed=str(branches - branches_covered))
            source_node = package.findall("sourcefile")[-1]
            for line in range(1, lines + 1):
                ET.SubElement(source_node, "line", nr=str(line), ci=str(int(line <= covered)),
                              mi=str(int(line > covered)), cb=str(branches_covered if line == 1 else 0),
                              mb=str(branches - branches_covered if line == 1 else 0))
            totals = [a + b for a, b in zip(totals, counts)]
        ET.SubElement(package, "counter", type="LINE", covered=str(totals[0]), missed=str(totals[1] - totals[0]))
        if totals[3]:
            ET.SubElement(package, "counter", type="BRANCH", covered=str(totals[2]), missed=str(totals[3] - totals[2]))
        file = self.root / "test-only.xml"
        ET.ElementTree(root).write(file)
        return file, classes

    def test_exact_thresholds_succeed(self):
        file, classes = self.report_fixture({"Owned.kt": (90, 100, 8, 10)})
        measured = pc.report(file, "metadata", classes)
        self.assertEqual([], pc.thresholds({"metadata": measured}))

    def test_line_and_branch_thresholds_fail_independently(self):
        for counts, kind in [((89, 100, 8, 10), "LINE"), ((90, 100, 7, 10), "BRANCH")]:
            file, classes = self.report_fixture({"Owned.kt": counts})
            failures = pc.thresholds({"metadata": pc.report(file, "metadata", classes)})
            self.assertTrue(any(kind in f for f in failures))

    def test_package_average_cannot_hide_insufficient_source(self):
        file, classes = self.report_fixture({"Under.kt": (85, 100, 8, 10), "High.kt": (99, 100, 10, 10)})
        failures = pc.thresholds({"metadata": pc.report(file, "metadata", classes)})
        self.assertTrue(any("Under.kt" in f for f in failures))
        self.assertFalse(any("PACKAGE" in f for f in failures))

    def test_zero_branches_are_na_but_lines_still_gate(self):
        file, classes = self.report_fixture({"Owned.kt": (90, 100, 0, 0)})
        measured = pc.report(file, "metadata", classes)
        self.assertIsNone(measured["sources"]["Owned.kt"]["BRANCH"]["percent"])
        self.assertEqual([], pc.thresholds({"metadata": measured}))
        file, classes = self.report_fixture({"Owned.kt": (89, 100, 0, 0)})
        self.assertTrue(pc.thresholds({"metadata": pc.report(file, "metadata", classes)}))

    def test_missing_source_or_counter_is_rejected(self):
        for mutate in ["source", "line", "branch", "class"]:
            file, classes = self.report_fixture({"Owned.kt": (90, 100, 8, 10)})
            tree = ET.parse(file); package = tree.getroot().find("package")
            source = package.find("sourcefile")
            if mutate == "source": package.remove(source)
            elif mutate == "class": package.remove(package.find("class"))
            else: source.remove(source.find("counter[@type='" + mutate.upper() + "']"))
            tree.write(file)
            with self.assertRaises(ValueError): pc.report(file, "metadata", classes)

    def test_duplicate_unexpected_or_changed_denominator_rejected(self):
        for mutate in ["duplicate", "unexpected", "denominator", "negative"]:
            file, classes = self.report_fixture({"Owned.kt": (90, 100, 8, 10)})
            tree = ET.parse(file); package = tree.getroot().find("package")
            source = package.find("sourcefile")
            if mutate == "duplicate": package.append(copy.deepcopy(source))
            elif mutate == "unexpected": source.set("name", "Unowned.kt")
            elif mutate == "denominator": source.find("counter").set("missed", "0")
            else: source.find("counter").set("covered", "-1")
            tree.write(file)
            with self.assertRaises(ValueError): pc.report(file, "metadata", classes)

    def test_inconsistent_line_evidence_and_package_counters_rejected(self):
        for mutate in ["line", "package", "missing_line"]:
            file, classes = self.report_fixture({"Owned.kt": (90, 100, 8, 10)})
            tree = ET.parse(file); package = tree.getroot().find("package")
            source = package.find("sourcefile")
            if mutate == "line": source.find("line").set("cb", "0")
            elif mutate == "package":
                package.find("counter").set("covered", "91")
                package.find("counter").set("missed", "9")
            else: source.remove(source.find("line"))
            tree.write(file)
            with self.assertRaises(ValueError): pc.report(file, "metadata", classes)

    def test_wrong_scope_and_malformed_report_rejected(self):
        file, classes = self.report_fixture({"Owned.kt": (90, 100, 8, 10)})
        with self.assertRaises(ValueError): pc.report(file, "preferences", classes)
        file.write_text("<truncated")
        with self.assertRaises(ET.ParseError): pc.report(file, "metadata", classes)

    def test_stale_commit_versions_classes_api_and_missing_provenance_rejected(self):
        build = {"commit": "fixture", "jacoco": "0.8.14"}; classes = {"Fixture": {"id": "abc"}}
        valid = {"format": 1, "run": "test-only", "androidApi": 26, "build": build,
                 "classes": classes, "started": self.started}
        pc.check_provenance(valid, build, classes, 26)
        for field, value in [("build", {"commit": "other"}), ("classes", {}), ("androidApi", 27),
                             ("format", 2), ("run", ""), ("started", int(time.time()*1000) + 100000)]:
            invalid = {**valid, field: value}
            with self.assertRaises(ValueError): pc.check_provenance(invalid, build, classes, 26)

    def input_fixture(self):
        jvm = self.root / "fixture.exec"; android = self.root / "fixture.ec"
        # These bytes are NOT passed to the Java parser or used as coverage. Policy data is separate.
        jvm.write_bytes(b"policy fixture"); android.write_bytes(b"policy fixture")
        session = {"id": "test-only", "start": self.started + 10, "end": self.started + 100}
        inputs = [{"path": str(p), "sessions": [session], "records": {name: {"coveredProbes": 1} for name in pc.ADAPTERS}}
                  for p in [jvm, android]]
        return jvm, android, {"inputs": inputs}

    def test_valid_input_policy_and_missing_android_adapter_rejection(self):
        jvm, android, evidence = self.input_fixture()
        run = {"started": self.started}
        pc.check_inputs(run, evidence, jvm, [android], int(time.time()*1000))
        for name in pc.ADAPTERS:
            bad = copy.deepcopy(evidence); bad["inputs"][1]["records"][name]["coveredProbes"] = 0
            with self.assertRaises(ValueError): pc.check_inputs(run, bad, jvm, [android], int(time.time()*1000))

    def test_missing_files_sessions_inventory_and_old_sessions_rejected(self):
        jvm, android, evidence = self.input_fixture()
        run = {"started": self.started}
        with self.assertRaises(ValueError): pc.check_inputs(run, evidence, jvm, [], int(time.time()*1000))
        for field, value in [("sessions", []), ("records", {}), ("path", str(self.root / "absent.ec"))]:
            bad = copy.deepcopy(evidence); bad["inputs"][1][field] = value
            with self.assertRaises(ValueError): pc.check_inputs(run, bad, jvm, [android], int(time.time()*1000))
        bad = copy.deepcopy(evidence); bad["inputs"][1]["sessions"][0]["start"] = self.started - 6000
        with self.assertRaises(ValueError): pc.check_inputs(run, bad, jvm, [android], int(time.time()*1000))
        import os
        os.utime(android, (1, 1))
        with self.assertRaises(ValueError): pc.check_inputs(run, evidence, jvm, [android], int(time.time()*1000))
        android.unlink()
        with self.assertRaises(ValueError): pc.check_inputs(run, evidence, jvm, [android], int(time.time()*1000))

    def test_offline_audit_rejects_wrong_commit_and_classes_without_live_device(self):
        completed = {"result": "PASS", "androidApi": 26, "build": {"commit": "captured"},
                     "classes": {"fixture": {"sha256": "captured"}}}
        (self.root / "provenance.json").write_text(json.dumps(completed))
        with patch.object(pc, "OUTPUT", self.root), patch.object(pc, "api") as device:
            with patch.object(pc, "current_build", return_value={"commit": "different"}):
                with self.assertRaises(ValueError): pc.audit(None)
            with patch.object(pc, "current_build", return_value={"commit": "captured"}), \
                    patch.object(pc, "java", return_value={"fixture": {"sha256": "different"}}):
                with self.assertRaises(ValueError): pc.audit(None)
            device.assert_not_called()

    def test_junit_missing_failed_skipped_and_stale_evidence_rejected(self):
        import os
        with self.assertRaises(ValueError): pc.test_evidence(self.root, "Fixture.run", 1, self.started)
        file = self.root / "junit.xml"
        valid = '<testsuite tests="1"><testcase classname="Fixture" name="run"/></testsuite>'
        file.write_text(valid)
        with patch.object(pc, "ROOT", self.root):
            self.assertEqual(1, pc.test_evidence(self.root, "Fixture.run", 1, self.started)["executed"])
        for child in ["failure", "error", "skipped"]:
            file.write_text(valid.replace('/>', '><' + child + '/></testcase>'))
            with self.assertRaises(ValueError): pc.test_evidence(self.root, "Fixture.run", 1, self.started)
        file.write_text(valid.replace('</testsuite>', '<testcase classname="Fixture" name="run"/></testsuite>'))
        with self.assertRaises(ValueError): pc.test_evidence(self.root, "Fixture.run", 1, self.started)
        file.write_text(valid)
        with self.assertRaises(ValueError): pc.test_evidence(self.root, "Fixture.missing", 1, self.started)
        with self.assertRaises(ValueError): pc.test_evidence(self.root, "Fixture.run", 2, self.started)
        os.utime(file, (1, 1))
        with self.assertRaises(ValueError): pc.test_evidence(self.root, "Fixture.run", 1, self.started)


if __name__ == "__main__":
    unittest.main()
