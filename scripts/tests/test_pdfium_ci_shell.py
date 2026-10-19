"""Exercise shell failure propagation with temporary command stubs, never an Android device."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'run_pdfium_ci.sh'


class PdfiumCiShellTests(unittest.TestCase):
    def run_script(self, standard=0, diagnostic=0, serial='emulator-5554', github=False, install_failure=None):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); tools = root/'tools'; tools.mkdir()
            (root/'gradlew').write_text(f'echo standard >> calls\nexit {standard}\n')
            (tools/'adb').write_text(f'''#!/bin/sh
case "$*" in
 devices) printf "emulator-5554 device\\n";;
 *ro.kernel.qemu*) echo 1;;
 *ro.build.version.sdk*) echo 26;;
 "-s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk")
   echo application >> installation-order
   [ "{install_failure}" != "application" ] || exit 1
   echo Success;;
 "-s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
   echo test >> installation-order
   [ "{install_failure}" != "test" ] || exit 1
   echo Success;;
 "-s emulator-5554 shell pm list instrumentation")
   echo instrumentation >> installation-order
   echo "instrumentation:com.abdev.partituraspdf.test/androidx.test.runner.AndroidJUnitRunner (target=com.abdev.partituraspdf)";;
 *) exit 2;;
esac
''')
            (tools/'python3').write_text(f'''#!/bin/sh
echo "$*" >> calls
case "$1" in *run_pdfium_process_experiments.py)
  echo diagnostics >> installation-order
  exit {diagnostic};;
esac
exit 0
''')
            for p in tools.iterdir(): p.chmod(0o755)
            env = os.environ.copy(); env['PATH'] = str(tools)+os.pathsep+env['PATH']
            env['GITHUB_ACTIONS'] = 'true' if github else 'false'
            env.pop('ANDROID_SERIAL',None)
            if serial is not None: env['ANDROID_SERIAL'] = serial
            result = subprocess.run(['bash',str(SCRIPT),'26'],cwd=root,env=env,capture_output=True,text=True,timeout=10)
            calls = (root/'calls').read_text().splitlines() if (root/'calls').exists() else []
            self.installation_order = ((root/'installation-order').read_text().splitlines()
                                       if (root/'installation-order').exists() else [])
            self.output = result.stdout + result.stderr
            return result.returncode,calls

    def test_success_requires_standard_diagnostics_and_smoke_verification(self):
        code,calls=self.run_script()
        self.assertEqual(0,code); self.assertEqual(3,len(calls))
        self.assertIn('--ci',calls[1]); self.assertIn('--confirm emulator-5554',calls[1])
        self.assertIn('verify_test_results.py',calls[2])
        self.assertEqual(['application', 'test', 'instrumentation', 'diagnostics'], self.installation_order)

    def test_standard_failure_still_runs_diagnostics_and_fails_job(self):
        code,calls=self.run_script(standard=1)
        self.assertEqual(1,code); self.assertEqual(2,len(calls)); self.assertIn('--ci',calls[1])
        self.assertEqual(['application', 'test', 'instrumentation', 'diagnostics'], self.installation_order)

    def test_application_install_failure_stops_before_diagnostics_or_success_verification(self):
        code, calls = self.run_script(install_failure='application')
        self.assertNotEqual(0, code)
        self.assertEqual(['application'], self.installation_order)
        self.assertEqual(['standard'], calls)
        self.assertNotIn('Success', self.output)
        self.assertFalse(any('verify_test_results.py' in call or
                             'run_pdfium_process_experiments.py' in call for call in calls))

    def test_test_apk_install_failure_stops_before_diagnostics_or_success_verification(self):
        code, calls = self.run_script(install_failure='test')
        self.assertNotEqual(0, code)
        self.assertEqual(['application', 'test'], self.installation_order)
        self.assertEqual(['standard'], calls)
        self.assertEqual(1, self.output.count('Success'))
        self.assertFalse(any('verify_test_results.py' in call or
                             'run_pdfium_process_experiments.py' in call for call in calls))

    def test_missing_or_failed_diagnostics_cannot_be_hidden_by_standard_pass(self):
        for code in [1,2]:
            with self.subTest(code=code):
                result,calls=self.run_script(diagnostic=code)
                self.assertEqual(1,result); self.assertEqual(2,len(calls))

    def test_physical_or_unconfirmed_local_device_is_rejected_before_gradle(self):
        for serial in ['physical-123',None]:
            with self.subTest(serial=serial):
                result,calls=self.run_script(serial=serial)
                self.assertEqual(2,result); self.assertEqual([],calls)

    def test_only_github_owned_single_emulator_can_be_discovered_noninteractively(self):
        result,calls=self.run_script(serial=None,github=True)
        self.assertEqual(0,result); self.assertIn('--serial emulator-5554',calls[1])
