"""Host-only regressions: these mocks never establish Android/Pdfium compatibility."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import run_pdfium_process_experiments as runner


def output(method="a_cleanDirectRepeatedValidOpens", code=0, pid=123, start=1000):
    rows = [{"pid": pid, "processStartElapsedMs": start, "class": runner.CLASS,
             "test": method, "invocation": "token", "sequence": i + 1,
             "fixture": fixture, "api": 26}
            for i, fixture in enumerate(runner.SCENARIOS[method])]
    return (f"INSTRUMENTATION_STATUS: class={runner.CLASS}\n"
            f"INSTRUMENTATION_STATUS: test={method}\nINSTRUMENTATION_STATUS_CODE: 1\n"
            f"INSTRUMENTATION_STATUS: class={runner.CLASS}\n"
            f"INSTRUMENTATION_STATUS: test={method}\nINSTRUMENTATION_STATUS_CODE: {code}\n"
            f"INSTRUMENTATION_RESULT: readerPdfiumEvidence={json.dumps(rows)}\n"
            "INSTRUMENTATION_CODE: -1\n")


class ProcessRunnerTests(unittest.TestCase):
    def validate(self, text=None, **kwargs):
        return runner.validate_run(text or output(), "a_cleanDirectRepeatedValidOpens", "token", 1000, 2000, **kwargs)

    def test_completed_selected_test_in_new_process_is_accepted(self):
        result = self.validate(previous=set())
        self.assertEqual("PASS", result["status"])
        self.assertTrue(result["processIsolationVerified"])
        self.assertTrue(result["sequenceComplete"])

    def test_failed_test_remains_fail_even_with_nonzero_adb_exit(self):
        result = self.validate(output(code=-2), previous=set(), adb_exit=1)
        self.assertEqual("FAIL", result["status"])
        self.assertEqual(1, result["adbExit"])

    def test_old_or_reused_process_cannot_be_called_fresh(self):
        with self.assertRaises(runner.Blocked):
            self.validate(previous={(123, 1000)})
        with self.assertRaises(runner.Blocked):
            self.validate(output(start=500), previous=set())

    def test_pid_change_mid_sequence_is_blocked(self):
        text = output().replace('"pid": 123', '"pid": 124', 1)
        with self.assertRaises(runner.Blocked):
            self.validate(text, previous=set())

    def test_wrong_invocation_or_fixture_order_is_blocked(self):
        for text in [output().replace('"token"', '"old"'), output().replace('"01"', '"15"', 1)]:
            with self.subTest(text=text), self.assertRaises(runner.Blocked):
                self.validate(text, previous=set())

    def test_crash_missing_evidence_skip_and_pass_with_transport_error_are_blocked(self):
        for text in ["INSTRUMENTATION_FAILED: crashed", output().replace("INSTRUMENTATION_RESULT:", "missing:"), output(code=-3)]:
            with self.subTest(text=text), self.assertRaises(runner.Blocked):
                self.validate(text, previous=set())
        with self.assertRaises(runner.Blocked):
            self.validate(previous=set(), adb_exit=1)

    def test_physical_unconfirmed_or_wrong_api_device_is_blocked(self):
        for serial, qemu, api, confirmation in [("physical", "0", "26", "physical"),
                                                ("emulator-5554", "1", "26", None),
                                                ("emulator-5554", "1", "28", "emulator-5554")]:
            with self.subTest(serial=serial), self.assertRaises(runner.Blocked):
                runner.verify_emulator(serial, qemu, api, confirmation)

    def test_foreign_package_is_rejected_before_any_adb_action(self):
        instance = runner.Runner("adb", "emulator-5554")
        instance.call = Mock()
        with self.assertRaises(runner.Blocked):
            instance.stop_and_verify(("other.app", runner.APP + ".test"))
        instance.call.assert_not_called()

    def test_surviving_process_prevents_next_instrumentation(self):
        instance = runner.Runner("adb", "emulator-5554")
        instance.call = Mock(return_value="")
        instance.pids = Mock(side_effect=[["123"], [], ["123"], []])
        with self.assertRaises(runner.Blocked):
            instance.stop_and_verify((runner.APP, runner.APP + ".test"))
        self.assertEqual([("shell", "am", "force-stop", runner.APP),
                          ("shell", "am", "force-stop", runner.APP + ".test")],
                         [call.args for call in instance.call.call_args_list])

    def test_manifest_identity_is_derived_and_foreign_target_blocked(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "AndroidManifest.xml"
            xml = ('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                   'package="com.abdev.partituraspdf.test"><instrumentation '
                   'android:name="androidx.test.runner.AndroidJUnitRunner" '
                   'android:targetPackage="com.abdev.partituraspdf"/></manifest>')
            path.write_text(xml)
            test, target, component = runner.manifest_identity(path)
            self.assertEqual(runner.APP, target)
            self.assertEqual(test + "/androidx.test.runner.AndroidJUnitRunner", component)
            path.write_text(xml.replace('targetPackage="com.abdev.partituraspdf"', 'targetPackage="foreign.app"'))
            with self.assertRaises(runner.Blocked):
                runner.manifest_identity(path)

    def test_main_rejects_physical_device_without_mutating_it(self):
        def read(*args, **kwargs):
            return {("shell", "getprop", "ro.kernel.qemu"): "0",
                    ("shell", "getprop", "ro.build.version.sdk"): "26",
                    ("emu", "avd", "name"): "not-owned"}[args]
        with patch.object(runner, "manifest_identity", return_value=(runner.APP+".test",runner.APP,runner.APP+".test/androidx.test.runner.AndroidJUnitRunner")), \
                patch.object(runner.Runner, "call", side_effect=read) as calls, patch("builtins.print"):
            with self.assertRaises(runner.Blocked):
                runner.main(["--serial", "physical", "--confirm", "physical"])
            self.assertFalse(any("am" in call.args or "install" in call.args for call in calls.call_args_list))

    def test_main_rejects_mismatched_installed_apk_before_force_stop(self):
        component=runner.APP+".test/androidx.test.runner.AndroidJUnitRunner"
        def read(*args, **kwargs):
            return {("shell", "getprop", "ro.kernel.qemu"): "1",
                    ("shell", "getprop", "ro.build.version.sdk"): "26",
                    ("emu", "avd", "name"): "owned-avd",
                    ("shell", "pm", "list", "instrumentation"): f"instrumentation:{component} (target={runner.APP})"}[args]
        with patch.object(runner, "manifest_identity", return_value=(runner.APP+".test",runner.APP,component)), \
                patch.object(runner.Runner, "call", side_effect=read) as calls, \
                patch.object(runner.Runner, "installed_hash", return_value="0"*64), \
                patch.object(Path, "read_bytes", return_value=b"expected APK"), patch("builtins.print"):
            with self.assertRaises(runner.Blocked):
                runner.main(["--serial", "emulator-5554", "--confirm", "emulator-5554"])
            self.assertFalse(any("am" in call.args or "install" in call.args for call in calls.call_args_list))


if __name__ == "__main__":
    unittest.main()
