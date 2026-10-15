"""Host-only transport fault injection. No emulator or adb is started."""
from pathlib import Path
import json
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
from test_local_e2e import e2e, protocol, X86_IMAGE


class TransportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.runner = e2e.Runner(Path(self.temp.name), Path(self.temp.name) / "sdk")
        self.runner.adb = "adb"
        self.runner.serial = "emulator-5580"
        self.runner.state = {"name": "owned", "port": 5580, "image": X86_IMAGE}
        self.runner.selection = e2e.host_image("Linux", "x86_64")
        self.calls = []
        self.failures = 0
        self.failure_key = "ro.kernel.qemu"
        self.api, self.abi, self.avd, self.qemu = "26", "x86_64", "owned", "1"
        self.settings = {"window_animation_scale": "1", "transition_animation_scale": "1",
                         "animator_duration_scale": "1", "accelerometer_rotation": "1",
                         "user_rotation": "1", "screen_off_timeout": "30000"}
        self.fail_write = None
        self.apply_failed_write = False
        self.execute = patch.object(e2e, "execute", side_effect=self.device).start()
        self.addCleanup(patch.stopall)
        self.sleep = patch.object(e2e.time, "sleep").start()

    def device(self, command, **kwargs):
        self.calls.append(list(command))
        self.assertEqual(command[:3], ["adb", "-s", "emulator-5580"])
        args = command[3:]
        text = ""
        if args == ["emu", "avd", "name"]:
            text = self.avd + "\nOK"
        elif args[:2] == ["shell", "getprop"]:
            key = args[-1]
            if key == self.failure_key and self.failures:
                self.failures -= 1
                return subprocess.CompletedProcess(command, 255, "", "")
            text = {"ro.kernel.qemu": self.qemu, "ro.build.version.sdk": self.api,
                    "ro.product.cpu.abi": self.abi, "sys.boot_completed": "1"}.get(key, "en-US")
        elif args[:2] == ["shell", "settings"]:
            action, _, key, *value = args[2:]
            if action == "get":
                text = self.settings.get(key, "null")
            else:
                failed = key == self.fail_write
                if not failed or self.apply_failed_write:
                    self.settings[key] = value[0] if action == "put" else "null"
                if failed:
                    return subprocess.CompletedProcess(command, 255, "", "")
        else:
            self.fail(f"Unexpected command: {command}")
        return subprocess.CompletedProcess(command, 0, text, "")

    def test_exit_255_retries_whole_identity_and_records_recovery(self):
        self.failures = 1
        self.runner.identify(boot=True)
        events = json.loads((self.runner.out / "adb-transport.json").read_text())
        failure = next(x for x in events if x.get("exit") == 255)
        self.assertEqual(failure["attempt"], 1)
        self.assertIn("elapsed_seconds", failure)
        self.assertEqual(sum(c[3:] == ["emu", "avd", "name"] for c in self.calls), 2)
        self.assertEqual(events[-1]["outcome"], "recovered")
        self.assertEqual(events[-1]["identity"]["serial"], "emulator-5580")
        self.assertEqual(events[-1]["identity"]["api"], 26)
        self.assertEqual(events[-1]["identity"]["abi"], "x86_64")
        self.sleep.assert_called_once_with(0.25)

    def test_persistent_transport_failure_stops_at_three_attempts(self):
        self.failures = 99
        with self.assertRaises(e2e.AdbTransportError):
            self.runner.identify()
        self.assertEqual(sum(c[-1] == "ro.kernel.qemu" for c in self.calls), 3)
        self.assertEqual(self.runner.summary["adb_transport"][-1]["outcome"], "exhausted")
        self.assertEqual(self.sleep.call_count, 2)

    def test_timeout_budget_limits_commands_and_no_extra_attempt_after_expiry(self):
        clock = [0.0]
        def timeout(command, **kwargs):
            self.assertLessEqual(kwargs["timeout"], 8)
            clock[0] = 8.0
            raise e2e.AdbTransportError("transport timeout")
        with patch.object(e2e.time, "monotonic", side_effect=lambda: clock[0]), \
             patch.object(e2e, "execute", side_effect=timeout):
            with self.assertRaises(e2e.AdbTransportError):
                self.runner.identify()
        self.sleep.assert_not_called()
        self.assertEqual(self.runner.summary["adb_transport"][-1]["attempt"], 1)

    def test_foreign_avd_during_recovery_stops_immediately(self):
        self.failures = 1
        self.sleep.side_effect = lambda _: setattr(self, "avd", "foreign")
        with self.assertRaisesRegex(e2e.Blocked, "different AVD"):
            self.runner.identify()
        self.assertEqual(self.sleep.call_count, 1)
        self.assertEqual(self.calls[-1][3:], ["emu", "avd", "name"])

    def test_wrong_api_abi_and_non_emulator_are_not_retried(self):
        for field, value in (("api", "27"), ("api", "36"), ("abi", "arm64-v8a"), ("qemu", "0")):
            with self.subTest(field=field, value=value):
                original = getattr(self, field)
                setattr(self, field, value)
                with self.assertRaises(e2e.Blocked):
                    self.runner.identify()
                setattr(self, field, original)
        self.sleep.assert_not_called()

    def test_api_or_abi_change_during_recovery_is_not_retried_again(self):
        for field, wrong in (("api", "36"), ("abi", "arm64-v8a")):
            self.calls.clear()
            self.failures = 1
            original = getattr(self, field)
            self.sleep.reset_mock()
            self.sleep.side_effect = lambda _, field=field, wrong=wrong: setattr(self, field, wrong)
            with self.assertRaises(e2e.Blocked):
                self.runner.identify()
            self.assertEqual(self.sleep.call_count, 1)
            self.assertFalse(self.runner.summary["device"]["identity_verified"])
            setattr(self, field, original)

    def test_emulator_termination_during_recovery_prevents_further_commands(self):
        process = Mock(pid=123, poll=Mock(return_value=None))
        self.runner.process = process
        self.failures = 1
        self.sleep.side_effect = lambda _: process.poll.configure_mock(return_value=9)
        with self.assertRaisesRegex(e2e.Blocked, "terminated"):
            self.runner.identify()
        self.assertEqual(len(self.calls), 2)
        self.assertEqual(self.runner.summary["adb_transport"][-1]["process"]["exit"], 9)

    def test_foreign_serial_never_receives_any_command(self):
        self.runner.serial = "emulator-5554"
        with self.assertRaisesRegex(e2e.Blocked, "port"):
            self.runner.identify()
        self.execute.assert_not_called()

    def test_safe_read_recovery_revalidates_identity_before_repeating_read(self):
        self.failure_key, self.failures = "persist.sys.locale", 1
        self.assertEqual(self.runner.read_adb("shell", "getprop", "persist.sys.locale"), "en-US")
        self.assertEqual(sum(c[3:] == ["emu", "avd", "name"] for c in self.calls), 2)
        for c in self.calls:
            self.assertNotIn("kill-server", c)
            self.assertNotIn("emulator-5554", c)

    def test_mutating_operations_are_not_accepted_by_read_recovery(self):
        for args in (("install", "apk"), ("shell", "settings", "put", "global", "key", "0"),
                     ("shell", "am", "instrument")):
            with self.assertRaisesRegex(e2e.Blocked, "read-only"):
                self.runner.read_adb(*args)
        self.execute.assert_not_called()

    def test_write_response_lost_is_not_replayed_and_partial_settings_are_restored(self):
        self.fail_write = "transition_animation_scale"
        self.apply_failed_write = True
        with self.assertRaises(e2e.AdbTransportError) as error:
            self.runner.prepare_settings()
        original_error = str(error.exception)
        writes = [c for c in self.calls if c[3:6] == ["shell", "settings", "put"]]
        self.assertEqual([c[-2] for c in writes], ["window_animation_scale", "transition_animation_scale"])
        self.assertEqual(len(self.runner.settings), 2)
        self.fail_write = None
        self.runner.restore_settings()
        self.assertEqual(self.settings["window_animation_scale"], "1")
        self.assertEqual(self.settings["transition_animation_scale"], "1")
        self.assertTrue(all(x["status"] == "PASS" for x in self.runner.summary["cleanup"]))
        self.assertIn("255", original_error)
        self.assertEqual(len([c for c in self.calls if c[-1] == "0" and c[3:6] == ["shell", "settings", "put"]]), 2)

    def test_failed_write_not_applied_needs_no_restoration_write(self):
        self.fail_write = "window_animation_scale"
        with self.assertRaises(e2e.AdbTransportError):
            self.runner.prepare_settings()
        self.fail_write = None
        self.runner.restore_settings()
        writes = [c for c in self.calls if c[3:6] == ["shell", "settings", "put"]]
        self.assertEqual(len(writes), 1)
        self.assertEqual(self.runner.summary["cleanup"][0]["status"], "PASS")
        self.assertIn("no restoration write", self.runner.summary["cleanup"][0]["detail"])

    def test_identity_failure_before_write_does_not_register_a_mutation(self):
        with patch.object(self.runner, "identify", side_effect=e2e.Blocked("foreign AVD")):
            with self.assertRaises(e2e.Blocked):
                self.runner.prepare_settings()
        self.assertEqual(self.runner.settings, {})
        self.assertFalse(any(c[3:6] == ["shell", "settings", "put"] for c in self.calls))

    def test_already_configured_settings_are_not_written_or_restored(self):
        self.settings.update(window_animation_scale="0", transition_animation_scale="0",
                             animator_duration_scale="0", accelerometer_rotation="0",
                             user_rotation="0", screen_off_timeout="600000")
        self.runner.prepare_settings()
        self.runner.restore_settings()
        self.assertEqual(self.runner.settings, {})
        self.assertEqual(self.runner.summary["cleanup"], [])

    def test_unexpected_setting_change_is_preserved_and_cleanup_is_blocked(self):
        self.fail_write = "transition_animation_scale"
        with self.assertRaises(e2e.AdbTransportError):
            self.runner.prepare_settings()
        self.fail_write = None
        self.settings["window_animation_scale"] = "9"
        self.runner.restore_settings()
        self.assertEqual(self.settings["window_animation_scale"], "9")
        self.assertEqual(self.runner.summary["cleanup"][0]["status"], "BLOCKED")

    def test_restoration_read_failure_reports_unverified_and_does_not_write(self):
        self.fail_write = "transition_animation_scale"
        with self.assertRaises(e2e.AdbTransportError):
            self.runner.prepare_settings()
        count = len(self.calls)
        self.fail_write, self.failure_key, self.failures = None, "ro.kernel.qemu", 99
        self.runner.restore_settings()
        self.assertTrue(all(x["status"] == "BLOCKED" for x in self.runner.summary["cleanup"]))
        self.assertFalse(any(c[3:6] == ["shell", "settings", "put"] for c in self.calls[count:]))

    def test_existing_emulator_requires_two_stable_boot_samples_before_settings(self):
        with patch.object(e2e, "host_image", return_value=self.runner.selection), \
             patch.object(e2e, "load_device", return_value=self.runner.state), \
             patch.object(e2e, "sdk_tool", return_value="adb"), \
             patch.object(e2e, "checked", return_value="List of devices attached\nemulator-5554 device\nemulator-5580 device"):
            self.runner.connect()
        boots = [i for i, c in enumerate(self.calls) if c[-1] == "sys.boot_completed"]
        writes = [i for i, c in enumerate(self.calls) if c[3:6] == ["shell", "settings", "put"]]
        self.assertEqual(len(boots), 2)
        self.assertLess(boots[-1], min(writes))
        self.sleep.assert_called_once_with(0.25)
        self.assertTrue(all(c[2] == "emulator-5580" for c in self.calls))

    def test_new_child_transport_can_arrive_after_default_three_attempts(self):
        self.runner.state["avd_home"] = self.temp.name
        self.failures = 5
        process = Mock(pid=123, poll=Mock(return_value=None))
        with patch.object(e2e, "host_image", return_value=self.runner.selection), \
             patch.object(e2e, "load_device", return_value=self.runner.state), \
             patch.object(e2e, "sdk_tool", return_value="adb"), \
             patch.object(e2e, "checked", return_value="List of devices attached"), \
             patch.object(e2e.subprocess, "Popen", return_value=process):
            try:
                self.runner.connect()
            finally:
                self.runner.emulator_log.close()
        recovery = next(e for e in self.runner.summary["adb_transport"]
                        if e.get("operation") == "initial child transport" and e["outcome"] == "recovered")
        self.assertEqual(recovery["attempt"], 6)
        self.assertEqual(recovery["identity"]["name"], "owned")
        self.assertEqual(recovery["process"]["pid"], 123)

    def test_new_child_connection_window_is_still_bounded(self):
        self.runner.process = Mock(pid=123, poll=Mock(return_value=None))
        self.failures = 100
        with self.assertRaises(e2e.AdbTransportError):
            self.runner.recover_readiness("initial child transport", self.runner._identify_once,
                                          attempts=60, budget=60, delay=0.5)
        self.assertEqual(self.runner.summary["adb_transport"][-1]["attempt"], 60)
        self.assertEqual(self.sleep.call_count, 59)
        self.assertFalse(any(c[3:6] == ["shell", "settings", "put"] for c in self.calls))

    def test_real_timeout_is_recoverable_only_for_identity_reads(self):
        original = self.device
        first = [True]
        def timeout_then_success(command, **kwargs):
            if first[0]:
                first[0] = False
                try:
                    raise subprocess.TimeoutExpired(command, kwargs["timeout"])
                except subprocess.TimeoutExpired as error:
                    raise e2e.Blocked("command timed out") from error
            return original(command, **kwargs)
        self.execute.side_effect = timeout_then_success
        self.runner.identify()
        self.assertEqual(self.runner.summary["adb_transport"][-1]["outcome"], "recovered")

    def test_empty_original_setting_response_blocks_before_any_mutation(self):
        self.settings["window_animation_scale"] = ""
        with self.assertRaisesRegex(e2e.Blocked, "original value unknown"):
            self.runner.prepare_settings()
        self.assertEqual(self.runner.settings, {})

    def test_absent_original_setting_is_deleted_and_verified_on_restore(self):
        self.settings["window_animation_scale"] = "null"
        self.fail_write = "transition_animation_scale"
        with self.assertRaises(e2e.AdbTransportError):
            self.runner.prepare_settings()
        self.fail_write = None
        self.runner.restore_settings()
        self.assertEqual(self.settings["window_animation_scale"], "null")
        self.assertEqual(len([c for c in self.calls if c[3:6] == ["shell", "settings", "delete"]]), 1)
        self.assertTrue(all(x["status"] == "PASS" for x in self.runner.summary["cleanup"]))

    def test_failed_restoration_write_is_not_replayed_or_claimed_successful(self):
        self.fail_write = "transition_animation_scale"
        with self.assertRaises(e2e.AdbTransportError):
            self.runner.prepare_settings()
        self.fail_write = "window_animation_scale"
        count = len(self.calls)
        self.runner.restore_settings()
        restores = [c for c in self.calls[count:] if c[3:6] == ["shell", "settings", "put"]]
        self.assertEqual(len(restores), 1)
        self.assertEqual(self.runner.summary["cleanup"][0]["status"], "BLOCKED")
        self.assertEqual(self.runner.summary["setting_changes"][0]["state"], "restoration unverified")

    def test_run_preserves_original_setup_error_when_restoration_also_fails(self):
        self.fail_write = "window_animation_scale"
        self.apply_failed_write = True
        with patch.object(self.runner, "workflow", side_effect=self.runner.prepare_settings), \
             patch.object(self.runner, "collect"), patch.object(e2e, "checked", return_value="baseline"):
            self.assertEqual(self.runner.run(), 2)
        self.assertIn("255", self.runner.summary["detail"])
        self.assertIn("'0'", self.runner.summary["detail"])
        self.assertTrue((self.runner.out / "host-error.log").exists())
        self.assertEqual(self.runner.summary["cleanup"][0]["status"], "BLOCKED")
        self.assertEqual(self.runner.summary["android_e2e"], "NOT RUN")

    def test_functional_failure_is_not_retried_and_still_classified_fail(self):
        self.runner.sha = "a" * 64
        with patch.object(self.runner, "gradle", return_value=subprocess.CompletedProcess([], 255, protocol(-2), "")) as gradle:
            with self.assertRaises(e2e.FunctionalFailure):
                self.runner.stage("acquire")
        gradle.assert_called_once()
        self.assertEqual(self.runner.summary["tests"][0]["status"], "FAIL")

    def test_instrumentation_transport_failure_is_not_retried(self):
        self.runner.sha = "a" * 64
        with patch.object(self.runner, "gradle", return_value=subprocess.CompletedProcess([], 255, "", "")) as gradle:
            with self.assertRaises(e2e.Blocked):
                self.runner.stage("acquire")
        gradle.assert_called_once()
        self.assertEqual(self.runner.summary["tests"][0]["status"], "BLOCKED")


if __name__ == "__main__":
    unittest.main()
