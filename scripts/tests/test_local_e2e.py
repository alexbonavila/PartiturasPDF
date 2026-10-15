"""Safety and failure-contract tests; no SDK, adb, emulator, or Gradle is executed."""
import importlib.util
import json
import base64
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET
import zipfile

X86_IMAGE = "system-images;android-26;google_apis;x86_64"
ARM_IMAGE = "system-images;android-26;google_apis;arm64-v8a"

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
                 "image": X86_IMAGE, "avd_home": str(self.home / "avd")}
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
             patch.object(self.runner, "gradle"), patch.object(self.runner, "connect") as connect, \
             patch.object(e2e, "doctor", return_value={"status": "PASS"}):
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
             patch.object(self.runner, "stage", side_effect=stage), \
             patch.object(e2e, "doctor", return_value={"status": "PASS"}):
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

class ClassificationHardeningTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.runner = e2e.Runner(Path(self.temp.name), Path(self.temp.name) / "sdk")
        self.runner.sha = "a" * 64

    def invoke(self, output, exit_code):
        process = subprocess.CompletedProcess(["gradle"], exit_code, output, "wrapper diagnostics")
        with patch.object(self.runner, "identify"), patch.object(e2e, "execute", return_value=process):
            self.runner.stage("acquire")

    def test_completed_failure_survives_nonzero_gradle_or_adb_exit(self):
        for exit_code in (0, 1, 137):
            with self.subTest(exit_code=exit_code), self.assertRaises(e2e.FunctionalFailure):
                self.invoke(protocol(-2), exit_code)
        self.assertTrue(all(item["status"] == "FAIL" for item in self.runner.summary["tests"]))
        self.assertIn("real failure", (self.runner.out / "acquire.log").read_text())
        self.assertIn("wrapper diagnostics", (self.runner.out / "acquire.log").read_text())

    def test_successful_test_with_failed_wrapper_is_blocked(self):
        with self.assertRaises(e2e.Blocked):
            self.invoke(protocol(), 1)
        self.assertEqual(self.runner.summary["tests"][0]["status"], "BLOCKED")

    def test_partial_failure_crash_or_missing_test_cannot_be_a_functional_fail(self):
        for output in (protocol(-2, final=False), protocol(-2) + "INSTRUMENTATION_FAILED: device died",
                       protocol(-2) + "INSTRUMENTATION_RESULT: shortMsg=Process crashed.", "BUILD FAILED"):
            with self.subTest(output=output), self.assertRaises(e2e.Blocked):
                self.invoke(output, 1)

    def test_timeout_with_partial_assertion_is_blocked_and_retains_output(self):
        with patch.object(self.runner, "identify"), patch.object(e2e.subprocess, "run",
                side_effect=subprocess.TimeoutExpired("gradle", 180, output=protocol(-2).encode())):
            with self.assertRaises(e2e.Blocked):
                self.runner.stage("acquire")
        self.assertEqual(self.runner.summary["tests"][0]["status"], "BLOCKED")
        self.assertIn("real failure", (self.runner.out / "acquire.log").read_text())

    def test_missing_failure_stack_and_false_completion_are_blocked(self):
        for output in (protocol(-2).replace("INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: real failure\n    at Test.kt:1\n", ""),
                       protocol().replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: -10"),
                       protocol() + "INSTRUMENTATION_CODE: -1\n"):
            with self.subTest(output=output), self.assertRaises(e2e.Blocked):
                e2e.parse_instrumentation(output, "acquire")

    def test_real_failure_reaches_exit_one_and_failure_xml_even_with_wrapper_failure(self):
        def workflow():
            self.runner.stage("acquire")
        process = subprocess.CompletedProcess(["gradle"], 1, protocol(-2), "BUILD FAILED")
        with patch.object(self.runner, "workflow", side_effect=workflow), \
             patch.object(self.runner, "identify"), patch.object(e2e, "execute", return_value=process), \
             patch.object(e2e, "checked", return_value="revision"), \
             patch.object(self.runner, "collect"), patch.object(self.runner, "clean"):
            self.assertEqual(self.runner.run(), 1)
        self.assertIsNotNone(ET.parse(self.runner.out / "junit.xml").find("testcase/failure"))
        self.assertEqual(json.loads((self.runner.out / "summary.json").read_text())["status"], "FAIL")

    def test_build_error_without_instrumentation_is_still_blocked(self):
        with patch.object(e2e, "execute", return_value=subprocess.CompletedProcess([], 1, "build failed", "error")):
            with self.assertRaises(e2e.Blocked):
                self.runner.gradle("build.log", [":app:assembleDebug"])
        self.assertIn("build failed", (self.runner.out / "build.log").read_text())

    def test_failure_capture_files_survive_owned_folder_cleanup(self):
        self.runner.adb, self.runner.serial, self.runner.identified = "adb", "emulator-5580", True
        self.runner.fixture_created = True
        def device(*args, **kwargs):
            if args[0] == "pull":
                Path(args[2]).write_text('<hierarchy><node text="real fixture UI"/></hierarchy>')
            return "device log/identity"
        png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
        with patch.object(self.runner, "identify"), patch.object(self.runner, "adb_command", side_effect=device), \
             patch.object(e2e, "checked", return_value="emulator-5580 device"), \
             patch.object(e2e, "execute", return_value=subprocess.CompletedProcess([], 0, png, b"")):
            self.runner.collect()
            self.runner.clean()
        self.assertEqual((self.runner.out / "screenshot.png").read_bytes(), png)
        self.assertIsNotNone(ET.parse(self.runner.out / "hierarchy.xml").find("node"))
        for file in ("logcat.txt", "activity.txt", "package.txt", "adb-final.txt", "emulator-identity.txt"):
            self.assertTrue((self.runner.out / file).is_file(), file)
        self.assertEqual(self.runner.summary["diagnostics"], [])

    def test_truncated_or_corrupt_png_is_not_capture_success(self):
        png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
        e2e.validate_screenshot(png)
        for bad in (b"not PNG", png[:8], png[:-10], png[:40] + b"bad" + png[43:]):
            with self.subTest(data=bad), self.assertRaises(e2e.Blocked):
                e2e.validate_screenshot(bad)

    def test_terminal_result_before_start_or_after_completion_is_blocked(self):
        output = protocol()
        start_end = output.index("INSTRUMENTATION_STATUS_CODE: 1") + len("INSTRUMENTATION_STATUS_CODE: 1\n")
        end = output.index("INSTRUMENTATION_CODE: -1")
        for bad in (output[start_end:end] + output[:start_end] + output[end:],
                    output[end:] + output[:end]):
            with self.assertRaises(e2e.Blocked):
                e2e.parse_instrumentation(bad, "acquire")

    def test_windows_batch_metacharacters_are_blocked_before_launch(self):
        with patch.object(e2e.os, "name", "nt"), patch.object(e2e.subprocess, "run") as launch:
            for command in ([r"C:\SDK&Other\sdkmanager.bat", "--version"],
                            [r"C:\Project\gradlew.bat", "-Pvalue=%SECRET%"]):
                with self.assertRaises(e2e.Blocked):
                    e2e.execute(command)
            launch.assert_not_called()

    def test_grant_loss_telemetry_is_retained_without_counting_an_extra_test(self):
        evidence = "INSTRUMENTATION_STATUS: localE2eEvidence=actual read exception=java.lang.SecurityException\nINSTRUMENTATION_STATUS_CODE: 0\n"
        output = protocol().replace("INSTRUMENTATION_STATUS_CODE: 1\n", "INSTRUMENTATION_STATUS_CODE: 1\n" + evidence)
        self.assertEqual(e2e.parse_instrumentation(output, "acquire"), 1)

    def test_timeout_stderr_is_retained_alongside_stdout(self):
        with patch.object(e2e.subprocess, "run", side_effect=subprocess.TimeoutExpired("gradle", 1, output=b"stdout", stderr=b"stderr")):
            with self.assertRaises(e2e.Blocked) as context:
                e2e.execute(["gradle"], timeout=1)
        self.assertIn("stdout", str(context.exception))
        self.assertIn("stderr", str(context.exception))

    def test_missing_evidence_prevents_overall_pass_without_erasing_functional_result(self):
        def workflow():
            self.runner.summary.update(status="PASS", tests=[{"name": name, "status": "PASS"} for name in e2e.STAGES])
        def collect():
            self.runner.summary["diagnostics"].append("screenshot capture unavailable")
        with patch.object(self.runner, "workflow", side_effect=workflow), patch.object(e2e, "checked", return_value="revision"), \
             patch.object(self.runner, "collect", side_effect=collect), patch.object(self.runner, "clean"):
            self.assertEqual(self.runner.run(), 2)
        self.assertEqual(self.runner.summary["android_e2e"], "PASS")
        self.assertEqual(self.runner.summary["status"], "BLOCKED")
        self.assertIsNotNone(ET.parse(self.runner.out / "junit.xml").find("testcase/error"))


class DoctorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name) / "state"
        self.sdk = Path(self.temp.name) / "Android SDK"
        self.jdk = Path(self.temp.name) / "JDK 25"
        self.system, self.machine, self.arm = "Linux", "x86_64", "0"
        self.translated = "0"
        self.acceleration, self.java_version = 0, "25.0.1"
        self.devices = "List of devices attached\n"
        self.runtime = {}
        for path in ("platform-tools/adb", "platform-tools/adb.exe", "emulator/emulator", "emulator/emulator.exe",
                     "cmdline-tools/19.0/bin/sdkmanager", "cmdline-tools/19.0/bin/sdkmanager.bat",
                     "cmdline-tools/19.0/bin/avdmanager", "cmdline-tools/19.0/bin/avdmanager.bat",
                     "platforms/android-37.0/android.jar", "build-tools/37.0.0/aapt2", "build-tools/37.0.0/aapt2.exe",
                     "build-tools/37.0.0/lib/d8.jar", "system-images/android-26/google_apis/x86_64/system.img"):
            file = self.sdk / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.touch()
        (self.sdk / "platforms/android-37.0/source.properties").write_text("AndroidVersion.ApiLevel=37.0\n")
        self.metadata = self.sdk / "system-images/android-26/google_apis/x86_64/package.xml"
        self.metadata.write_text(f'<repository><localPackage path="{X86_IMAGE}"><type-details>'
                                 '<api-level>26</api-level><abi>x86_64</abi></type-details></localPackage></repository>')
        (self.jdk / "bin").mkdir(parents=True)
        (self.jdk / "bin/java").touch()
        (self.jdk / "bin/java.exe").touch()
        (self.jdk / "bin/javac").touch()
        (self.jdk / "bin/javac.exe").touch()
        self.patches = [patch.object(e2e.platform, "system", side_effect=lambda: self.system),
                        patch.object(e2e.platform, "machine", side_effect=lambda: self.machine),
                        patch.dict(e2e.os.environ, {"JAVA_HOME": str(self.jdk), "PROCESSOR_ARCHITECTURE": "AMD64",
                                                   "PROCESSOR_ARCHITEW6432": "AMD64"}),
                        patch.object(e2e, "execute", side_effect=self.command)]
        for item in self.patches:
            item.start()
            self.addCleanup(item.stop)

    def command(self, command, **kwargs):
        args = [str(x) for x in command]
        output, code = "tool version", 0
        if "sysctl.proc_translated" in args:
            output = self.translated
        elif "hw.optional.arm64" in args:
            output = self.arm
        elif "-accel-check" in args:
            code = self.acceleration
            output = f"accel:\n{code}\nHardware acceleration probe\naccel\n"
        elif Path(args[0]).stem == "java":
            output = f'openjdk version "{self.java_version}"'
        elif "device" in args and "-c" in args:
            output = "pixel_c\n"
        elif "devices" in args:
            output = self.devices
        elif "emu" in args:
            output = self.runtime.get("name", "unknown") + "\nOK"
        elif "getprop" in args:
            output = self.runtime.get(args[-1], "unknown")
        return subprocess.CompletedProcess(args, code, output, "")

    def diagnose(self):
        return e2e.doctor(self.home, self.sdk)

    def test_native_linux_windows_and_intel_mac_are_eligible_not_executed(self):
        for self.system, self.machine in (("Linux", "x86_64"), ("Windows", "AMD64"), ("Darwin", "x86_64")):
            with self.subTest(system=self.system):
                report = self.diagnose()
                self.assertEqual(report["status"], "PASS", report["blockers"])
                self.assertEqual(report["emulation"], "NOT RUN")
                self.assertEqual(report["dedicated_avd"]["status"], "UNPROVISIONED")
                self.assertEqual(len(report["compatible_api26_images"]), 1)

    def test_apple_silicon_native_and_rosetta_require_the_arm_image(self):
        self.system, self.arm = "Darwin", "1"
        for self.machine in ("arm64", "x86_64"):
            report = self.diagnose()
            self.assertEqual(report["native_architecture"], "arm64")
            self.assertEqual(report["status"], "BLOCKED")
            self.assertEqual(report["requested_avd"]["image"], ARM_IMAGE)
            self.assertTrue(any(ARM_IMAGE in item for item in report["blockers"]))

    def test_windows_arm_is_blocked_even_when_python_looks_x86(self):
        self.system = "Windows"
        with patch.dict(e2e.os.environ, {"PROCESSOR_ARCHITEW6432": "ARM64"}):
            self.assertEqual(self.diagnose()["status"], "BLOCKED")

    def test_no_acceleration_blocks_readiness(self):
        self.acceleration = 3
        self.assertFalse(self.diagnose()["ready_to_attempt"])

    def test_missing_image_or_bad_metadata_blocks(self):
        self.metadata.unlink()
        self.assertEqual(self.diagnose()["status"], "BLOCKED")
        self.metadata.write_text("<invalid")
        self.assertTrue(any("metadata" in x for x in self.diagnose()["blockers"]))

    def test_wrong_abi_cannot_be_adopted_as_compatible(self):
        self.metadata.write_text(self.metadata.read_text().replace("<abi>x86_64", "<abi>arm64-v8a"))
        self.assertEqual(self.diagnose()["compatible_api26_images"], [])

    def test_jdk_mismatch_and_missing_sdk_tools_are_reported_together(self):
        self.java_version = "24.0.1"
        (self.sdk / "platform-tools/adb").unlink()
        report = self.diagnose()
        self.assertEqual(report["status"], "BLOCKED")
        self.assertTrue(any("JDK 25" in x for x in report["blockers"]))
        self.assertTrue(any("Missing SDK tool" in x for x in report["blockers"]))

    def test_versioned_command_line_tools_and_windows_suffixes(self):
        self.system = "Windows"
        report = self.diagnose()
        self.assertTrue(report["tools"]["avdmanager"].endswith("19.0/bin/avdmanager.bat"))
        self.assertTrue(report["tools"]["adb"].endswith("adb.exe"))

    def test_corrupt_ownership_is_blocked_without_device_probes(self):
        self.home.mkdir()
        (self.home / "device.json").write_text("{}")
        report = self.diagnose()
        self.assertEqual(report["dedicated_avd"]["status"], "INVALID")
        self.assertNotIn("adb_devices", report["checks"])

    def test_foreign_reserved_serial_is_not_accepted(self):
        self.home.mkdir()
        state, _ = HostTests.make_provision(self)
        self.devices += "emulator-5580 device\nphysical-123 device\n"
        self.runtime = {"name": "somebody-else", "ro.kernel.qemu": "1", "ro.build.version.sdk": "26", "sys.boot_completed": "1", "ro.product.cpu.abi": "x86_64"}
        report = self.diagnose()
        self.assertEqual(report["dedicated_avd"]["status"], "UNVERIFIED")
        self.assertEqual(report["status"], "BLOCKED")
        for key, check in report["checks"].items():
            if key.startswith("device_"):
                self.assertEqual(check["command"][1:3], ["-s", "emulator-5580"])

    def test_compatible_owned_booted_device_is_identified(self):
        self.home.mkdir()
        state, _ = HostTests.make_provision(self)
        self.devices += "emulator-5580 device\n"
        self.runtime = {"name": state["name"], "ro.kernel.qemu": "1", "ro.build.version.sdk": "26", "sys.boot_completed": "1", "ro.product.cpu.abi": "x86_64"}
        self.assertEqual(self.diagnose()["dedicated_avd"]["status"], "OWNED_BOOTED")

    def test_provision_fails_preflight_before_creating_avd_or_running_manager(self):
        with patch.object(e2e, "doctor", return_value={"status": "BLOCKED", "blockers": ["ARM64 API 26 image missing"]}), \
             patch.object(e2e, "checked") as create:
            with self.assertRaisesRegex(e2e.Blocked, "ARM64 API 26"):
                e2e.provision(self.home, self.sdk, 5580)
        create.assert_not_called()
        self.assertFalse((self.home / "avd").exists())

    def test_sdk_selection_explicit_then_environment_then_escaped_local_properties(self):
        root = Path(self.temp.name)
        selected = root / "SDK with spaces"
        with patch.object(e2e, "ROOT", root), patch.dict(e2e.os.environ, {"ANDROID_HOME": str(self.sdk)}, clear=True):
            self.assertEqual(e2e.discover_sdk(selected), selected.resolve())
            self.assertEqual(e2e.discover_sdk(), self.sdk.resolve())
        (root / "local.properties").write_text("sdk.dir=" + str(selected).replace(" ", "\\ ") + "\n")
        with patch.object(e2e, "ROOT", root), patch.dict(e2e.os.environ, {}, clear=True):
            self.assertEqual(e2e.discover_sdk(), selected.resolve())

    def test_missing_jdk_compiler_is_reported_as_incomplete_installation(self):
        (self.jdk / "bin/javac").unlink()
        self.assertTrue(any("JDK 25" in item for item in self.diagnose()["blockers"]))

    def test_diagnostic_io_error_is_blocked_json_not_uncaught_exception(self):
        with patch.object(e2e, "_inspect_host", side_effect=PermissionError("SDK metadata unreadable")):
            report = self.diagnose()
        self.assertEqual(report["status"], "BLOCKED")
        self.assertIn("unreadable", report["blockers"][0])

    def install_arm_image(self):
        metadata = self.sdk / "system-images/android-26/google_apis/arm64-v8a/package.xml"
        metadata.parent.mkdir(parents=True, exist_ok=True)
        metadata.write_text(f'<repository><localPackage path="{ARM_IMAGE}"><type-details>'
                            '<api-level>26</api-level><abi>arm64-v8a</abi></type-details></localPackage></repository>')
        (metadata.parent / "system.img").touch()
        return metadata

    def own_arm_avd(self):
        self.home.mkdir(parents=True, exist_ok=True)
        state, directory = HostTests.make_provision(self)
        state["image"] = ARM_IMAGE
        (self.home / "device.json").write_text(json.dumps(state))
        (directory / "config.ini").write_text("image.sysdir.1=system-images/android-26/google_apis/arm64-v8a/\nabi.type=arm64-v8a\ntag.id=google_apis\n")
        return state, directory

    def test_native_arm64_and_rosetta_are_ready_with_arm_image_and_acceleration(self):
        self.system, self.arm = "Darwin", "1"
        self.install_arm_image()
        for self.machine in ("arm64", "x86_64"):
            self.translated = "1" if self.machine == "x86_64" else "0"
            self.arm = "0" if self.translated == "1" else "1"
            with self.subTest(process_arch=self.machine):
                report = self.diagnose()
                self.assertEqual(report["status"], "PASS", report["blockers"])
                self.assertEqual(report["requested_avd"]["image"], ARM_IMAGE)
                self.assertEqual(report["requested_avd"]["abi"], "arm64-v8a")
                self.assertEqual(report["checks"]["acceleration"]["command"][:2], ["/usr/bin/arch", "-arm64"])
                self.assertEqual(report["emulation"], "NOT RUN")
        self.acceleration = 3
        self.assertEqual(self.diagnose()["status"], "BLOCKED")

    def test_supported_mapping_is_explicit_and_other_hosts_remain_blocked(self):
        for system, arch, expected in (("Darwin", "arm64", ARM_IMAGE), ("Darwin", "x86_64", X86_IMAGE),
                                       ("Windows", "x86_64", X86_IMAGE), ("Linux", "x86_64", X86_IMAGE)):
            self.assertEqual(e2e.host_image(system, arch)["image"], expected)
        for system, arch in (("Linux", "arm64"), ("Windows", "arm64"), ("Darwin", "unknown"), ("FreeBSD", "x86_64")):
            with self.assertRaises(e2e.Blocked):
                e2e.host_image(system, arch)

    def test_arm_image_metadata_must_match_abi_api_and_installed_path(self):
        self.system, self.machine, self.arm = "Darwin", "arm64", "1"
        metadata = self.install_arm_image()
        valid = metadata.read_text()
        for bad in (valid.replace("<abi>arm64-v8a", "<abi>x86_64"),
                    valid.replace("<api-level>26", "<api-level>27"),
                    valid.replace("<api-level>26", "<api-level>36"),
                    valid.replace(ARM_IMAGE, X86_IMAGE)):
            metadata.write_text(bad)
            report = self.diagnose()
            self.assertEqual(report["status"], "BLOCKED")
            self.assertTrue(any(ARM_IMAGE in message for message in report["blockers"]))

    def test_legacy_x86_v1_manifest_remains_valid_and_is_not_rewritten(self):
        self.home.mkdir()
        state, directory = HostTests.make_provision(self)
        original = (self.home / "device.json").read_bytes()
        self.assertEqual(e2e.load_device(self.home), state)
        self.assertEqual(self.diagnose()["status"], "PASS")
        self.assertEqual((self.home / "device.json").read_bytes(), original)
        self.assertEqual(state["version"], 1)

    def test_x86_state_is_never_migrated_or_adopted_on_apple_silicon(self):
        self.home.mkdir()
        _, directory = HostTests.make_provision(self)
        manifest = (self.home / "device.json").read_bytes()
        config = (directory / "config.ini").read_bytes()
        self.system, self.machine, self.arm = "Darwin", "arm64", "1"
        self.install_arm_image()
        self.assertEqual(self.diagnose()["status"], "BLOCKED")
        with self.assertRaises(e2e.Blocked):
            e2e.provision(self.home, self.sdk, 5580)
        self.assertEqual((self.home / "device.json").read_bytes(), manifest)
        self.assertEqual((directory / "config.ini").read_bytes(), config)

    def test_native_arm_v1_state_validates_and_foreign_identity_stays_blocked(self):
        self.system, self.machine, self.arm = "Darwin", "arm64", "1"
        self.install_arm_image()
        state, _ = self.own_arm_avd()
        self.assertEqual(e2e.load_device(self.home), state)
        self.devices += "emulator-5580 device\n"
        self.runtime = {"name": state["name"], "ro.kernel.qemu": "1", "ro.build.version.sdk": "26",
                        "sys.boot_completed": "1", "ro.product.cpu.abi": "arm64-v8a"}
        self.assertEqual(self.diagnose()["dedicated_avd"]["status"], "OWNED_BOOTED")
        self.runtime["name"] = "foreign"
        self.assertEqual(self.diagnose()["status"], "BLOCKED")
        self.runtime["name"] = state["name"]
        self.runtime["ro.product.cpu.abi"] = "x86_64"
        self.assertEqual(self.diagnose()["status"], "BLOCKED")

    def test_arm_avd_config_cannot_substitute_other_api_or_abi(self):
        self.system, self.machine, self.arm = "Darwin", "arm64", "1"
        state, directory = self.own_arm_avd()
        valid = (directory / "config.ini").read_text()
        for bad in (valid.replace("android-26", "android-27"), valid.replace("android-26", "android-36"),
                    valid.replace("abi.type=arm64-v8a", "abi.type=x86_64")):
            (directory / "config.ini").write_text(bad)
            with self.assertRaises(e2e.Blocked):
                e2e.load_device(self.home)

    def test_arm_provision_uses_selected_package_and_creates_backward_compatible_state(self):
        self.system, self.machine, self.arm = "Darwin", "arm64", "1"
        self.install_arm_image()
        captured = []
        def create(command, **kwargs):
            args = [str(value) for value in command]
            captured.append(args)
            self.assertEqual(args[args.index("--package") + 1], ARM_IMAGE)
            name = args[args.index("--name") + 1]
            directory = self.home / "avd" / (name + ".avd")
            directory.mkdir()
            (directory / "config.ini").write_text("image.sysdir.1=system-images/android-26/google_apis/arm64-v8a/\nabi.type=arm64-v8a\n")
            (directory.parent / (name + ".ini")).write_text("path=" + str(directory) + "\n")
            self.assertEqual(kwargs["env"]["ANDROID_AVD_HOME"], str(self.home / "avd"))
            return "created (host mock, not a real AVD)"
        with patch.object(e2e, "checked", side_effect=create):
            e2e.provision(self.home, self.sdk, 5580)
        state = e2e.load_device(self.home)
        self.assertEqual(state["image"], ARM_IMAGE)
        self.assertEqual(state["version"], 1)
        self.assertEqual(len(captured), 1)
        self.assertNotIn("--force", captured[0])

    def test_boot_identity_checks_native_abi_and_rejects_api_substitution(self):
        runner = e2e.Runner(self.home, self.sdk)
        runner.adb, runner.serial = "adb", "emulator-5580"
        runner.selection = e2e.host_image("Darwin", "arm64")
        runner.state = {"name": "owned", "image": ARM_IMAGE}
        with patch.object(runner, "raw_adb", side_effect=["owned\nOK", "1", "26", "arm64-v8a", "1"]):
            runner.identify(boot=True)
        self.assertEqual(runner.summary["device"]["observed_abi"], "arm64-v8a")
        for output in (["owned\nOK", "1", "26", "x86_64"], ["owned\nOK", "1", "27"], ["owned\nOK", "1", "36"]):
            with patch.object(runner, "raw_adb", side_effect=output), self.assertRaises(e2e.Blocked):
                runner.identify(boot=True)

    def test_emulator_launch_forces_native_arm64_and_hardware_acceleration(self):
        self.system, self.machine, self.arm = "Darwin", "x86_64", "1"  # Rosetta Python
        self.own_arm_avd()
        runner = e2e.Runner(self.home, self.sdk)
        with patch.object(e2e, "checked", return_value="List of devices attached\n"), \
             patch.object(e2e.subprocess, "Popen") as launch, \
             patch.object(runner, "recover_readiness"), \
             patch.object(e2e.time, "monotonic", side_effect=[0, 241]):
            with self.assertRaisesRegex(e2e.Blocked, "boot timeout"):
                runner.connect()
        args = launch.call_args.args[0]
        self.assertEqual(args[:2], ["/usr/bin/arch", "-arm64"])
        self.assertEqual(args[args.index("-accel") + 1], "on")
        self.assertNotIn("-wipe-data", args)
        runner.emulator_log.close()

class LiveUiEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.runner = e2e.Runner(Path(self.temp.name), Path(self.temp.name) / "sdk")
        self.runner.adb, self.runner.serial = "adb", "emulator-5580"
        self.event = "a" * 32
        self.root = f"files/local-e2e-{self.runner.run_id}-ui-failures"
        self.png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
        self.payloads = {"metadata.json": json.dumps({"run": self.runner.run_id, "selector": "stable-button-id", "errors": []}).encode(),
                         "screenshot.png": self.png, "hierarchy.xml": b'<hierarchy><node text="failure-time"/></hierarchy>'}

    def listing(self, *args, **kwargs):
        return self.root.removeprefix("files/") if args[-1] == "files" else self.event

    def read(self, command, **kwargs):
        return subprocess.CompletedProcess(command, 0, self.payloads[command[-1].rsplit("/", 1)[-1]], b"")

    def collect(self):
        with patch.object(self.runner, "identify"), patch.object(self.runner, "adb_command", side_effect=self.listing), \
             patch.object(e2e, "execute", side_effect=self.read):
            self.runner.collect_instrumentation_failures()

    def test_live_snapshots_are_separate_from_post_test_and_survive_cleanup(self):
        self.runner.record("screenshot.png", "post-test screenshot")
        self.collect()
        destination = self.runner.out / "instrumentation-failures" / self.event
        self.assertEqual((destination / "screenshot.png").read_bytes(), self.png)
        self.assertEqual(json.loads((destination / "metadata.json").read_bytes())["selector"], "stable-button-id")
        with patch.object(self.runner, "adb_command") as cleanup:
            self.runner.clean()
        cleanup.assert_called_once_with("shell", "run-as", e2e.APP, "rm", "-r", self.root)
        self.assertTrue((destination / "hierarchy.xml").is_file())
        self.assertEqual((self.runner.out / "screenshot.png").read_text(), "post-test screenshot")

    def test_missing_root_and_unavailable_menu_checkpoints_retain_navigation_metadata(self):
        for checkpoint in ("primary storage root missing before configuration", "picker overflow unavailable"):
            metadata = {"run": self.runner.run_id, "selector": "checkpoint: " + checkpoint,
                        "package": "com.android.documentsui", "expectedPickerPackage": "com.android.documentsui",
                        "expectedPrimaryRootTitle": "Renamed API 26 emulator", "visibleRootTitles": ["Downloads"],
                        "errors": []}
            self.payloads["metadata.json"] = json.dumps(metadata).encode()
            self.collect()
            destination = self.runner.out / "instrumentation-failures" / self.event
            self.assertEqual(json.loads((destination / "metadata.json").read_bytes()), metadata)
            self.assertEqual((destination / "screenshot.png").read_bytes(), self.png)
            self.assertTrue((destination / "hierarchy.xml").exists())
            self.assertEqual(self.runner.summary["diagnostics"], [])
            self.assertEqual(self.runner.ui_failure_cleanup, self.root)
            self.event = "b" * 32  # Each checkpoint must survive as a separate event.
        self.assertEqual(len(self.runner.summary["instrumentation_failures"]), 2)

    def test_recollection_never_overwrites_first_snapshots(self):
        self.collect()
        self.payloads["metadata.json"] = b"replacement"
        self.collect()
        saved = self.runner.out / "instrumentation-failures" / self.event / "metadata.json"
        self.assertEqual(json.loads(saved.read_bytes())["selector"], "stable-button-id")
        self.assertEqual(len(self.runner.summary["instrumentation_failures"]), 1)

    def test_partial_or_corrupt_capture_is_retained_and_never_deleted_from_device(self):
        self.payloads["screenshot.png"] = b"truncated screenshot"
        self.collect()
        self.collect()  # A later cleanup capture must not promote partial evidence to complete.
        self.assertIsNone(self.runner.ui_failure_cleanup)
        self.assertTrue(self.runner.summary["diagnostics"])
        destination = self.runner.out / "instrumentation-failures" / self.event
        self.assertEqual((destination / "screenshot.png").read_bytes(), b"truncated screenshot")
        self.assertTrue((destination / "hierarchy.xml").exists())

    def test_capture_errors_keep_raw_metadata_and_preserve_device_originals(self):
        self.payloads["metadata.json"] = json.dumps({"run": self.runner.run_id, "selector": "missing-control",
                                                    "errors": ["screenshot unavailable"]}).encode()
        self.collect()
        saved = self.runner.out / "instrumentation-failures" / self.event / "metadata.json"
        self.assertEqual(json.loads(saved.read_bytes())["selector"], "missing-control")
        self.assertIsNone(self.runner.ui_failure_cleanup)
        self.assertTrue(self.runner.summary["diagnostics"])

    def test_foreign_run_metadata_is_not_marked_safe_to_clean(self):
        self.payloads["metadata.json"] = json.dumps({"run": "other", "selector": "button"}).encode()
        self.collect()
        self.assertIsNone(self.runner.ui_failure_cleanup)
        self.assertIn("does not match", self.runner.summary["diagnostics"][0])

    def test_path_traversal_event_is_rejected_before_reading_or_writing(self):
        self.event = "../../unrelated"
        with self.assertRaises(e2e.Blocked):
            self.collect()
        self.assertFalse((self.runner.out / "instrumentation-failures").exists())

    def test_missing_live_directory_does_not_change_success_or_touch_user_files(self):
        with patch.object(self.runner, "adb_command", return_value="unrelated-file"), patch.object(e2e, "execute") as read:
            self.runner.collect_instrumentation_failures()
            read.assert_not_called()
        self.assertIsNone(self.runner.ui_failure_cleanup)
        self.assertEqual(self.runner.summary["diagnostics"], [])


if __name__ == "__main__":
    unittest.main()
