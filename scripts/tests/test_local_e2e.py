"""Safety and failure-contract tests; no SDK, adb, emulator, or Gradle is executed."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET
import zipfile

SPEC = importlib.util.spec_from_file_location("local_e2e", Path(__file__).parents[1] / "local_e2e.py")
e2e = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(e2e)


def protocol(code=0, method="acquire", final=True):
    start = f"INSTRUMENTATION_STATUS: class={e2e.CLASS}\nINSTRUMENTATION_STATUS: test={method}\n"
    return (start + "INSTRUMENTATION_STATUS_CODE: 1\n" + start
            + ("INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: real failure\n    at Test.kt:1\n" if code < 0 else "")
            + f"INSTRUMENTATION_STATUS_CODE: {code}\n"
            + ("INSTRUMENTATION_CODE: -1\n" if final else ""))


class ProtocolTests(unittest.TestCase):
    def test_one_executed_test_is_required(self):
        self.assertEqual(e2e.parse_instrumentation(protocol(), "acquire"), 1)

    def test_assertion_failure_is_functional_not_infrastructure(self):
        for code in (-1, -2):
            with self.subTest(code=code), self.assertRaisesRegex(e2e.FunctionalFailure, "real failure"):
                e2e.parse_instrumentation(protocol(code), "acquire")

    def test_empty_output_or_build_success_cannot_pass(self):
        for output in ("", "BUILD SUCCESSFUL", "OK (0 tests)\nINSTRUMENTATION_CODE: -1"):
            with self.subTest(output=output), self.assertRaises(e2e.Blocked):
                e2e.parse_instrumentation(output, "acquire")

    def test_skip_ignore_or_unknown_status_is_not_pass(self):
        for code in (-3, -4, 2, 99):
            with self.subTest(code=code), self.assertRaises(e2e.Blocked):
                e2e.parse_instrumentation(protocol(code), "acquire")

    def test_crashed_or_unfinished_process_is_blocked(self):
        for output in (protocol(final=False), protocol() + "INSTRUMENTATION_FAILED: crash",
                       protocol() + "INSTRUMENTATION_RESULT: shortMsg=Process crashed."):
            with self.subTest(output=output), self.assertRaises(e2e.Blocked):
                e2e.parse_instrumentation(output, "acquire")

    def test_duplicate_results_are_not_accepted(self):
        with self.assertRaises(e2e.Blocked):
            e2e.parse_instrumentation(protocol() * 2, "acquire")

    def test_wrong_or_extra_test_is_not_accepted(self):
        for output in (protocol(method="wrong"), protocol() + protocol(method="wrong")):
            with self.subTest(output=output), self.assertRaises(e2e.Blocked):
                e2e.parse_instrumentation(output, "acquire")


class HostTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        self.runner = e2e.Runner(self.home, self.home / "sdk")

    def test_reports_are_unique_and_never_overwritten(self):
        other = e2e.Runner(self.home, self.home / "sdk")
        self.assertNotEqual(self.runner.out, other.out)
        self.runner.record("evidence", "keep")
        other.record("evidence", "new")
        self.assertEqual((self.runner.out / "evidence").read_text(), "keep")

    def test_no_execution_emits_blocked_xml_not_a_passing_test(self):
        self.runner.reports()
        suite = ET.parse(self.runner.out / "junit.xml").getroot()
        self.assertEqual(suite.get("errors"), "1")
        self.assertEqual(self.runner.summary["counts"]["PASS"], 0)
        self.assertIsNotNone(suite.find("testcase/error"))

    def test_cleanup_failure_cannot_leave_overall_xml_green(self):
        self.runner.summary.update(status="BLOCKED", tests=[{"name": "acquire", "status": "PASS"}])
        self.runner.reports()
        self.assertEqual(ET.parse(self.runner.out / "junit.xml").getroot().get("errors"), "1")

    def test_unknown_suite_never_builds_or_connects(self):
        self.runner.suite = "coverage"
        with patch.object(self.runner, "gradle") as gradle, patch.object(self.runner, "connect") as connect:
            with self.assertRaisesRegex(e2e.Blocked, "not implemented"):
                self.runner.workflow()
            gradle.assert_not_called()
            connect.assert_not_called()

    def test_lock_never_removes_another_invocations_lock(self):
        with e2e.exclusive(self.home):
            with self.assertRaises(e2e.Blocked):
                with e2e.exclusive(self.home):
                    self.fail("Second runner acquired the lock")
            self.assertTrue((self.home / "runner.lock").exists())
        self.assertFalse((self.home / "runner.lock").exists())

    def test_unprovisioned_device_is_blocked(self):
        with self.assertRaisesRegex(e2e.Blocked, "provision"):
            e2e.load_device(self.home)

    def test_refuses_different_avd_without_mutating_it(self):
        self.runner.adb = Path("adb")
        self.runner.serial = "emulator-5580"
        self.runner.state = {"name": "owned"}
        with patch.object(self.runner, "raw_adb", return_value="someone-else\nOK") as adb:
            with self.assertRaisesRegex(e2e.Blocked, "different AVD"):
                self.runner.adb_command("shell", "rm", "owned-file")
            self.assertEqual(adb.call_args_list, [unittest.mock.call("emu", "avd", "name")])

    def test_non_emulator_and_wrong_api_are_rejected(self):
        self.runner.adb, self.runner.serial, self.runner.state = Path("adb"), "emulator-5580", {"name": "owned"}
        for outputs in (["owned\nOK", "0"], ["owned\nOK", "1", "36"]):
            with self.subTest(outputs=outputs), patch.object(self.runner, "raw_adb", side_effect=outputs):
                with self.assertRaises(e2e.Blocked):
                    self.runner.identify()
        self.assertFalse(self.runner.identified)

    def test_path_traversal_is_rejected_before_deletion(self):
        self.runner.run_id = "../another-directory"
        with self.assertRaises(e2e.Blocked):
            self.runner.remote_folder()

    def test_cleanup_does_not_remove_a_folder_it_never_created(self):
        with patch.object(self.runner, "adb_command") as adb:
            self.runner.clean()
            adb.assert_not_called()

    def test_cleanup_is_bounded_to_owned_folder_and_never_uninstalls(self):
        self.runner.fixture_created = True
        with patch.object(self.runner, "adb_command") as adb:
            self.runner.clean()
            adb.assert_called_once_with("shell", "rm", "-r", self.runner.remote_folder())

    def test_adb_loss_preserves_host_evidence_and_records_cleanup_blocker(self):
        self.runner.record("gradle-build.log", "host evidence")
        self.runner.fixture_created = self.runner.installed = self.runner.identified = True
        self.runner.adb, self.runner.serial = "adb", "emulator-5580"
        with patch.object(self.runner, "identify", side_effect=e2e.Blocked("offline")), \
             patch.object(self.runner, "adb_command", side_effect=e2e.Blocked("offline")), \
             patch.object(e2e, "checked", side_effect=e2e.Blocked("offline")):
            self.runner.collect()
            self.runner.clean()
        self.assertGreaterEqual(len(self.runner.summary["diagnostics"]), 6)
        self.assertTrue(all(x["status"] == "BLOCKED" for x in self.runner.summary["cleanup"]))
        self.assertEqual((self.runner.out / "gradle-build.log").read_text(), "host evidence")

    def test_timeout_retains_partial_output(self):
        with patch.object(e2e.subprocess, "run", side_effect=subprocess.TimeoutExpired("adb", 1, output=b"partial")):
            with self.assertRaisesRegex(e2e.Blocked, "partial"):
                e2e.execute(["adb"], timeout=1)

    def test_functional_failure_is_nonzero_no_retry_and_evidence_precedes_cleanup(self):
        events = []
        def fail():
            events.append("test")
            self.runner.summary["tests"].append({"name": "acquire", "status": "FAIL", "detail": "assertion"})
            raise e2e.FunctionalFailure("assertion")
        with patch.object(e2e, "checked", return_value="commit"), \
             patch.object(self.runner, "workflow", side_effect=fail) as workflow, \
             patch.object(self.runner, "collect", side_effect=lambda: events.append("capture")), \
             patch.object(self.runner, "clean", side_effect=lambda: events.append("cleanup")):
            self.assertEqual(self.runner.run(), 1)
            workflow.assert_called_once()
        self.assertEqual(events, ["test", "capture", "cleanup"])
        self.assertEqual(json.loads((self.runner.out / "summary.json").read_text())["status"], "FAIL")

    def test_cleanup_failure_changes_success_to_blocked(self):
        def success():
            self.runner.summary.update(status="PASS", android_e2e="PASS")
        def fail_cleanup():
            self.runner.summary["cleanup"].append({"status": "BLOCKED", "detail": "offline"})
        with patch.object(e2e, "checked", return_value="commit"), \
             patch.object(self.runner, "workflow", side_effect=success), \
             patch.object(self.runner, "clean", side_effect=fail_cleanup):
            self.assertEqual(self.runner.run(), 2)
        self.assertEqual(self.runner.summary["status"], "BLOCKED")

    def test_stage_gradle_failure_retains_blocked_test_result(self):
        self.runner.sha = "a" * 64
        with patch.object(self.runner, "identify"), \
             patch.object(self.runner, "gradle", side_effect=e2e.Blocked("build failed")):
            with self.assertRaises(e2e.Blocked):
                self.runner.stage("acquire")
        self.assertEqual(self.runner.summary["tests"][0]["status"], "BLOCKED")

    def test_provision_refuses_existing_state_before_sdk_commands(self):
        (self.home / "device.json").write_text("{}")
        with patch.object(e2e, "checked") as command:
            with self.assertRaisesRegex(e2e.Blocked, "overwrite"):
                e2e.provision(self.home, self.home / "sdk", 5580)
            command.assert_not_called()

    def test_provision_rejects_invalid_ports(self):
        for port in (5553, 5555, 5684):
            with self.subTest(port=port), self.assertRaises(e2e.Blocked):
                e2e.provision(self.home, self.home / "sdk", port)

    def make_provision(self):
        state = {"version": 1, "name": "PartiturasPDF_E2E_API26_0123456789ab", "port": 5580,
                 "image": e2e.IMAGE, "avd_home": str(self.home / "avd")}
        directory = self.home / "avd" / (state["name"] + ".avd")
        directory.mkdir(parents=True)
        (directory / "config.ini").write_text("image.sysdir.1=system-images/android-26/google_apis/x86_64/\n")
        (directory.parent / (state["name"] + ".ini")).write_text("path=" + str(directory) + "\n")
        (self.home / "device.json").write_text(json.dumps(state))
        return state, directory

    def test_owned_avd_config_and_index_must_agree(self):
        state, directory = self.make_provision()
        self.assertEqual(e2e.load_device(self.home), state)
        (directory.parent / (state["name"] + ".ini")).write_text("path=/other/user/avd\n")
        with self.assertRaisesRegex(e2e.Blocked, "outside"):
            e2e.load_device(self.home)

    def test_different_image_cannot_be_adopted(self):
        _, directory = self.make_provision()
        (directory / "config.ini").write_text("image.sysdir.1=system-images/android-36/google_apis/x86_64/\n")
        with self.assertRaisesRegex(e2e.Blocked, "approved API 26"):
            e2e.load_device(self.home)

    def test_partial_functional_run_has_an_xml_error(self):
        self.runner.summary["tests"] = [{"name": "acquire", "status": "PASS"}]
        self.runner.reports()
        self.assertEqual(self.runner.summary["android_e2e"], "INCOMPLETE")
        self.assertIsNotNone(ET.parse(self.runner.out / "junit.xml").find("testcase/error"))

    def mock_apks(self, root, include_class=True):
        for relative in ("app/build/outputs/apk/debug/app-debug.apk",
                         "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
            apk = root / relative
            apk.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(apk, "w") as archive:
                descriptor = ("L" + e2e.CLASS.replace(".", "/") + ";").encode() if include_class else b"other"
                archive.writestr("classes.dex", descriptor)

    def test_missing_opt_in_class_blocks_before_device_operations(self):
        root = self.home / "repository"
        self.mock_apks(root, include_class=False)
        with patch.object(e2e, "ROOT", root), patch.object(e2e, "fixture", return_value=(root / "fixture.pdf", "a" * 64)), \
             patch.object(self.runner, "gradle"), patch.object(self.runner, "connect") as connect:
            with self.assertRaisesRegex(e2e.Blocked, "absent"):
                self.runner.workflow()
            connect.assert_not_called()

    def test_mock_workflow_runs_four_distinct_stages_with_force_stops_and_hash_checks(self):
        root = self.home / "repository"
        self.mock_apks(root)
        events = []
        def adb(*args, **kwargs):
            events.append(args)
            return "a" * 64 + "  fixture.pdf" if "sha256sum" in args else "Success"
        def stage(method):
            events.append(("stage", method))
            self.runner.summary["tests"].append({"name": method, "status": "PASS"})
        with patch.object(e2e, "ROOT", root), patch.object(e2e, "fixture", return_value=(root / "fixture.pdf", "a" * 64)), \
             patch.object(self.runner, "gradle"), patch.object(self.runner, "connect"), \
             patch.object(self.runner, "adb_command", side_effect=adb), \
             patch.object(self.runner, "stage", side_effect=stage):
            self.runner.workflow()
        self.assertEqual([x[1] for x in events if x[0] == "stage"], list(e2e.STAGES))
        for index, event in enumerate(events):
            if event[0] == "stage":
                self.assertEqual(events[index - 2], ("shell", "am", "force-stop", e2e.APP))
                self.assertEqual(events[index - 1][1], "sha256sum")
        self.assertEqual(self.runner.summary["status"], "PASS")
        self.assertTrue(self.runner.fixture_created)

    def test_missing_executable_is_infrastructure_error(self):
        with patch.object(e2e.subprocess, "run", side_effect=FileNotFoundError("missing")):
            with self.assertRaises(e2e.Blocked):
                e2e.execute(["adb"])

    def test_failed_command_does_not_become_empty_success(self):
        with patch.object(e2e, "execute", return_value=Mock(returncode=1, stdout="failure", stderr="error")):
            with self.assertRaisesRegex(e2e.Blocked, "failure"):
                e2e.checked(["adb"])


if __name__ == "__main__":
    unittest.main()
