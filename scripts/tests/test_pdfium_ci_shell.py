"""Exercise shell failure propagation with temporary command stubs, never an Android device."""
import os
from pathlib import Path
import shutil
import shlex
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

SCRIPT = Path(__file__).resolve().parents[1] / 'run_pdfium_ci.sh'
TIMEOUT = 15


def _msys_runtime(bash):
    """Identify an existing Git/MSYS distribution, never Windows' WSL launcher."""
    bash = Path(bash)
    for runtime in (bash.parent, bash.parent.parent/'usr'/'bin'):
        if all((runtime/name).is_file() for name in (
                'bash.exe', 'sh.exe', 'env.exe', 'awk.exe', 'tr.exe',
                'date.exe', 'mkdir.exe', 'cat.exe')):
            return runtime
    return None


def _select_bash(*, windows=None, environ=None, which=None):
    windows = os.name == 'nt' if windows is None else windows
    environ = os.environ if environ is None else environ
    which = shutil.which if which is None else which
    explicit = environ.get('PDFIUM_TEST_BASH')
    if explicit and not Path(explicit).is_absolute():
        raise RuntimeError('PDFIUM_TEST_BASH must be an absolute executable path')
    candidates = [explicit] if explicit else [which('bash')]
    if windows and not explicit:
        git = which('git')
        if git:
            for parent in list(Path(git).resolve().parents)[:3]:
                candidates += [parent/'bin'/'bash.exe', parent/'usr'/'bin'/'bash.exe']
        for variable, suffix in [('ProgramFiles', 'Git/bin/bash.exe'),
                                 ('ProgramFiles(x86)', 'Git/bin/bash.exe'),
                                 ('LOCALAPPDATA', 'Programs/Git/bin/bash.exe')]:
            if environ.get(variable): candidates.append(Path(environ[variable])/suffix)
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            path = Path(candidate).resolve()
            if not windows: return path
            runtime = _msys_runtime(path)
            if runtime is not None: return (runtime/'bash.exe').resolve()
    raise RuntimeError('Real Bash capability unavailable: on Windows select existing Git/MSYS Bash '
                       'with PDFIUM_TEST_BASH (absolute bash.exe path). WSL is not used; no test is skipped.')


class _WindowsJob:
    """Own only this test's gated Bash process tree; await exit before deleting its cwd.

    Standard-library Win32 API, no taskkill, process enumeration or admin rights.
    The child waits on stdin until assignment, so no fixture command can escape.
    """
    def __init__(self):
        import ctypes
        from ctypes import wintypes as w
        self.c = ctypes
        self.w = w
        self.api = ctypes.WinDLL('kernel32', use_last_error=True)
        signatures = {
            'CreateJobObjectW': ([w.LPVOID, w.LPCWSTR], w.HANDLE),
            'CreateIoCompletionPort': ([w.HANDLE, w.HANDLE, ctypes.c_size_t, w.DWORD], w.HANDLE),
            'SetInformationJobObject': ([w.HANDLE, ctypes.c_int, w.LPVOID, w.DWORD], w.BOOL),
            'OpenProcess': ([w.DWORD, w.BOOL, w.DWORD], w.HANDLE),
            'AssignProcessToJobObject': ([w.HANDLE, w.HANDLE], w.BOOL),
            'QueryInformationJobObject': ([w.HANDLE, ctypes.c_int, w.LPVOID, w.DWORD, w.LPVOID], w.BOOL),
            'TerminateJobObject': ([w.HANDLE, w.UINT], w.BOOL),
            'GetQueuedCompletionStatus': ([w.HANDLE, ctypes.POINTER(w.DWORD),
                ctypes.POINTER(ctypes.c_size_t), ctypes.POINTER(w.LPVOID), w.DWORD], w.BOOL),
            'CloseHandle': ([w.HANDLE], w.BOOL),
        }
        for name, (arguments, result) in signatures.items():
            function = getattr(self.api, name)
            function.argtypes = arguments
            function.restype = result
        self.job = self.port = None
        self.assigned = self.empty = False
        try:
            self.job = self.api.CreateJobObjectW(None, None)
            if not self.job: raise ctypes.WinError(ctypes.get_last_error())
            self.port = self.api.CreateIoCompletionPort(w.HANDLE(-1), None, 0, 1)
            if not self.port: raise ctypes.WinError(ctypes.get_last_error())
            class Association(ctypes.Structure):
                _fields_ = [('key', w.LPVOID), ('port', w.HANDLE)]
            association = Association(1, self.port)
            if not self.api.SetInformationJobObject(self.job, 7, ctypes.byref(association), ctypes.sizeof(association)):
                raise ctypes.WinError(ctypes.get_last_error())
        except BaseException as primary:
            try: self.close()
            except BaseException as cleanup:
                raise BaseExceptionGroup('Job creation and cleanup failed', [primary, cleanup])
            raise

    def attach(self, process):
        # PROCESS_SET_QUOTA | PROCESS_TERMINATE, required for job assignment.
        handle = self.api.OpenProcess(0x0101, False, process.pid)
        if not handle: raise self.c.WinError(self.c.get_last_error())
        primary = None
        try:
            if not self.api.AssignProcessToJobObject(self.job, handle):
                raise self.c.WinError(self.c.get_last_error())
            self.assigned = True
        except BaseException as failure:
            primary = failure
            raise
        finally:
            if not self.api.CloseHandle(handle):
                cleanup = self.c.WinError(self.c.get_last_error())
                if primary is not None:
                    raise BaseExceptionGroup('Job assignment and handle closure failed', [primary, cleanup])
                raise cleanup

    def wait(self):
        if not self.assigned or self.empty: return
        deadline = time.monotonic() + TIMEOUT
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0: raise TimeoutError('Owned Bash process tree did not exit')
            message, key, process = self.w.DWORD(), self.c.c_size_t(), self.w.LPVOID()
            if not self.api.GetQueuedCompletionStatus(self.port, self.c.byref(message),
                    self.c.byref(key), self.c.byref(process), max(1, int(remaining*1000))):
                raise self.c.WinError(self.c.get_last_error())
            if key.value == 1 and message.value == 4:  # JOB_OBJECT_MSG_ACTIVE_PROCESS_ZERO
                class Accounting(self.c.Structure):
                    _fields_ = [('times', self.c.c_longlong * 4), ('faults', self.w.DWORD),
                                ('total', self.w.DWORD), ('active', self.w.DWORD),
                                ('terminated', self.w.DWORD)]
                accounting = Accounting()
                if not self.api.QueryInformationJobObject(self.job, 1, self.c.byref(accounting),
                        self.c.sizeof(accounting), None):
                    raise self.c.WinError(self.c.get_last_error())
                if accounting.active == 0:
                    self.empty = True
                    return

    def terminate(self):
        if self.assigned and not self.empty:
            if not self.api.TerminateJobObject(self.job, 1): raise self.c.WinError(self.c.get_last_error())
            self.wait()

    def close(self):
        errors = []
        try: self.terminate()
        except BaseException as error: errors.append(error)
        for name in ('port', 'job'):
            handle = getattr(self, name)
            if handle:
                if not self.api.CloseHandle(handle): errors.append(self.c.WinError(self.c.get_last_error()))
                setattr(self, name, None)
        if errors: raise BaseExceptionGroup('Owned process cleanup failed', errors)


def _run_owned(bash, root, env, *, windows=None):
    windows = os.name == 'nt' if windows is None else windows
    # No shell startup file, user PATH translation or absolute Windows script argument.
    # All fixture programs have LF endings and run in the selected Bash's namespace.
    launcher = '''IFS= read -r permission
[[ "$permission" == "owned-test" ]] || exit 97
export PATH="$PWD/tools:/usr/bin:/bin"
export BASH_ENV="$PWD/test-environment.sh"
exec "$BASH" ./run_pdfium_ci.sh 26
'''
    job = _WindowsJob() if windows else None
    process = None
    primary = None
    try:
        process = subprocess.Popen([str(bash), '--noprofile', '--norc', '-c', launcher],
            cwd=root, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, start_new_session=not windows)
        try:
            if job is not None: job.attach(process)
            stdout, stderr = process.communicate(b'owned-test\n', timeout=TIMEOUT)
            if job is not None: job.wait()
            return subprocess.CompletedProcess(process.args, process.returncode,
                stdout.decode('utf-8', errors='backslashreplace'),
                stderr.decode('utf-8', errors='backslashreplace'))
        except BaseException as failure:
            cleanup_errors = []
            try:
                if job is not None and job.assigned: job.terminate()
                elif not windows:
                    try: os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError: pass  # Positive evidence: owned group is gone.
            except BaseException as cleanup: cleanup_errors.append(cleanup)
            try:
                if process.poll() is None: process.kill()  # Exact Popen-owned process handle.
                stdout, stderr = process.communicate(timeout=TIMEOUT)
                failure.add_note('Owned Bash stdout/stderr: '+repr((stdout, stderr)))
            except BaseException as cleanup: cleanup_errors.append(cleanup)
            if cleanup_errors:
                raise BaseExceptionGroup('Bash execution and owned cleanup failed', [failure, *cleanup_errors])
            raise
    except BaseException as failure:
        primary = failure
        raise
    finally:
        cleanup_errors = []
        if job is not None:
            try: job.close()
            except BaseException as cleanup: cleanup_errors.append(cleanup)
        if process is not None:
            for stream in (process.stdin, process.stdout, process.stderr):
                if stream is not None:
                    try: stream.close()
                    except BaseException as cleanup: cleanup_errors.append(cleanup)
        if cleanup_errors:
            raise BaseExceptionGroup('Bash resource closure failed',
                ([primary] if primary is not None else []) + cleanup_errors)


def _write_shell(path, source):
    path.write_text(source, encoding='utf-8', newline='\n')
    path.chmod(0o755)


class PdfiumCiShellTests(unittest.TestCase):
    def run_script(self, standard=0, diagnostic=0, serial='emulator-5554', github=False,
                   install_failure=None, verification=0, devices='emulator-5554 device\n'):
        with tempfile.TemporaryDirectory(prefix='pdfium shell ') as tmp:
            root = Path(tmp); tools = root/'tools'; tools.mkdir()
            bash = _select_bash()
            _write_shell(root/'run_pdfium_ci.sh', SCRIPT.read_text(encoding='utf-8'))
            _write_shell(root/'test-environment.sh', '''trap 'wait' EXIT
# Bind fake commands explicitly: never fall through to a machine's real ADB/Python.
adb() { "$PWD/tools/adb" "$@"; }
python3() { "$PWD/tools/python3" "$@"; }
''')
            _write_shell(root/'gradlew', f'echo standard >> calls\nexit {standard}\n')
            _write_shell(tools/'adb', f'''#!/bin/sh
printf '%s\\n' "$*" >> adb-calls
case "$*" in
 devices) printf '%s' {shlex.quote(devices)};;
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
            _write_shell(tools/'python3', f'''#!/bin/sh
echo "$*" >> calls
case "$1" in *run_pdfium_process_experiments.py)
  echo diagnostics >> installation-order
  exit {diagnostic};;
 *verify_test_results.py) exit {verification};;
esac
exit 0
''')
            env = os.environ.copy()
            for variable in ('BASH_ENV', 'ENV', 'CDPATH', 'SHELLOPTS', 'BASHOPTS',
                             'MSYS2_ARG_CONV_EXCL', 'MSYS2_ENV_CONV_EXCL', 'MSYS_NO_PATHCONV'):
                env.pop(variable, None)
            for variable in list(env):
                if variable.startswith('BASH_FUNC_'): env.pop(variable)
            env['GITHUB_ACTIONS'] = 'true' if github else 'false'
            env.pop('ANDROID_SERIAL',None)
            if serial is not None: env['ANDROID_SERIAL'] = serial
            result = _run_owned(bash, root, env)
            self.addCleanup(lambda: self.assertFalse(root.exists(), 'Owned temporary cwd was retained'))
            calls = (root/'calls').read_text().splitlines() if (root/'calls').exists() else []
            self.installation_order = ((root/'installation-order').read_text().splitlines()
                                       if (root/'installation-order').exists() else [])
            self.adb_calls = (root/'adb-calls').read_text().splitlines() if (root/'adb-calls').exists() else []
            self.output = result.stdout + result.stderr
            self.detail = f'bash={bash}; exit={result.returncode}; stdout={result.stdout!r}; stderr={result.stderr!r}'
            return result.returncode,calls

    def test_success_requires_standard_diagnostics_and_smoke_verification(self):
        code,calls=self.run_script()
        self.assertEqual(0,code,self.detail); self.assertEqual(3,len(calls))
        self.assertIn('--ci',calls[1]); self.assertIn('--confirm emulator-5554',calls[1])
        self.assertIn('verify_test_results.py',calls[2])
        self.assertEqual('standard',calls[0])
        self.assertEqual(1,sum('run_pdfium_process_experiments.py' in call for call in calls))
        self.assertEqual(1,sum('verify_test_results.py' in call for call in calls))
        self.assertEqual(['application', 'test', 'instrumentation', 'diagnostics'], self.installation_order)
        self.assertEqual([
            '-s emulator-5554 shell getprop ro.kernel.qemu',
            '-s emulator-5554 shell getprop ro.build.version.sdk',
            '-s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk',
            '-s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
            '-s emulator-5554 shell pm list instrumentation'], self.adb_calls, self.detail)

    def test_standard_failure_still_runs_diagnostics_and_fails_job(self):
        code,calls=self.run_script(standard=1)
        self.assertEqual(1,code,self.detail); self.assertEqual(2,len(calls)); self.assertIn('--ci',calls[1])
        self.assertEqual(['application', 'test', 'instrumentation', 'diagnostics'], self.installation_order)

    def test_application_install_failure_stops_before_diagnostics_or_success_verification(self):
        code, calls = self.run_script(install_failure='application')
        self.assertEqual(1, code, self.detail)
        self.assertEqual(['application'], self.installation_order)
        self.assertEqual(['standard'], calls)
        self.assertNotIn('Success', self.output)
        self.assertFalse(any('verify_test_results.py' in call or
                             'run_pdfium_process_experiments.py' in call for call in calls))

    def test_test_apk_install_failure_stops_before_diagnostics_or_success_verification(self):
        code, calls = self.run_script(install_failure='test')
        self.assertEqual(1, code, self.detail)
        self.assertEqual(['application', 'test'], self.installation_order)
        self.assertEqual(['standard'], calls)
        self.assertEqual(1, self.output.count('Success'))
        self.assertFalse(any('verify_test_results.py' in call or
                             'run_pdfium_process_experiments.py' in call for call in calls))

    def test_missing_or_failed_diagnostics_cannot_be_hidden_by_standard_pass(self):
        for code in [1,2,127]:
            with self.subTest(code=code):
                result,calls=self.run_script(diagnostic=code)
                self.assertEqual(1,result,self.detail); self.assertEqual(2,len(calls))

    def test_physical_or_unconfirmed_local_device_is_rejected_before_gradle(self):
        for serial in ['physical-123',None]:
            with self.subTest(serial=serial):
                result,calls=self.run_script(serial=serial)
                self.assertEqual(2,result,self.detail); self.assertEqual([],calls); self.assertEqual([],self.adb_calls)

    def test_only_github_owned_single_emulator_can_be_discovered_noninteractively(self):
        result,calls=self.run_script(serial=None,github=True)
        self.assertEqual(0,result,self.detail); self.assertIn('--serial emulator-5554',calls[1]); self.assertEqual('devices',self.adb_calls[0])

    def test_standard_exit_127_is_retained_and_diagnostics_run_once(self):
        code,calls=self.run_script(standard=127)
        self.assertEqual(1,code,self.detail)
        self.assertEqual(2,len(calls),self.detail)
        self.assertEqual('standard',calls[0])
        self.assertIn('run_pdfium_process_experiments.py',calls[1])
        self.assertEqual(['application','test','instrumentation','diagnostics'],self.installation_order)

    def test_failed_final_verification_cannot_report_success(self):
        code,calls=self.run_script(verification=3)
        self.assertEqual(3,code,self.detail)
        self.assertEqual(3,len(calls),self.detail)
        self.assertIn('verify_test_results.py',calls[-1])

    def test_ci_discovery_refuses_missing_ambiguous_or_unauthorized_devices(self):
        for devices in ['', 'emulator-5554 device\nemulator-5556 device\n',
                        'emulator-5554 offline\n', 'physical-123 device\n']:
            with self.subTest(devices=devices):
                code,calls=self.run_script(serial=None,github=True,devices=devices)
                self.assertEqual(2,code,self.detail)
                self.assertEqual([],calls)
                self.assertEqual(['devices'],self.adb_calls)
                self.assertEqual([],self.installation_order)


class PdfiumShellHarnessTests(unittest.TestCase):
    def test_lf_utf8_stubs_and_space_paths_are_preserved(self):
        with tempfile.TemporaryDirectory(prefix='shell fixture ') as tmp:
            root=Path(tmp);path=root/'fake command'
            _write_shell(path,"#!/bin/sh\necho 'català'\n")
            self.assertEqual("#!/bin/sh\necho 'català'\n".encode('utf-8'),path.read_bytes())
        self.assertFalse(root.exists())

    def test_windows_selection_refuses_wsl_and_finds_git_runtime(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);wsl=root/'Windows'/'System32'/'bash.exe'
            wsl.parent.mkdir(parents=True);wsl.touch()
            git=root/'Git'/'cmd'/'git.exe';git.parent.mkdir(parents=True);git.touch()
            runtime=root/'Git'/'usr'/'bin';runtime.mkdir(parents=True)
            for name in ['bash.exe','sh.exe','env.exe','awk.exe','tr.exe','date.exe','mkdir.exe','cat.exe']:
                (runtime/name).touch()
            which=lambda name: str(wsl if name=='bash' else git)
            self.assertEqual((runtime/'bash.exe').resolve(),_select_bash(windows=True,environ={},which=which))
            with self.assertRaisesRegex(RuntimeError,'capability unavailable'):
                _select_bash(windows=True,environ={'PDFIUM_TEST_BASH':str(wsl)},which=which)
            with self.assertRaisesRegex(RuntimeError,'absolute'):
                _select_bash(windows=True,environ={'PDFIUM_TEST_BASH':'bash.exe'},which=which)

    def test_missing_bash_is_an_explicit_error_not_a_skip(self):
        with self.assertRaisesRegex(RuntimeError,'capability unavailable'):
            _select_bash(windows=False,environ={},which=lambda name:None)

    def test_owned_windows_job_is_assigned_before_gate_and_joined_before_closing(self):
        order=[]
        job=Mock(assigned=True)
        process=Mock(args=['synthetic'],returncode=0)
        job.attach.side_effect=lambda child:order.append(('attach',child))
        def communicate(token=None,timeout=None):
            self.assertEqual(b'owned-test\n',token)
            self.assertEqual([('attach',process)],order)
            order.append(('run',process));return b'captured',b''
        process.communicate.side_effect=communicate
        job.wait.side_effect=lambda:order.append(('wait',process))
        job.close.side_effect=lambda:order.append(('close',process))
        module=sys.modules[__name__]
        with patch.object(module,'_WindowsJob',return_value=job), patch.object(subprocess,'Popen',return_value=process):
            result=_run_owned(Path('synthetic-bash'),Path('synthetic-root'),{},windows=True)
        self.assertEqual(0,result.returncode)
        self.assertEqual('captured',result.stdout)
        self.assertEqual(['attach','run','wait','close'],[event[0] for event in order])
        for stream in (process.stdin,process.stdout,process.stderr):stream.close.assert_called_once()

    def test_timeout_remains_a_failure_and_terminates_only_owned_job(self):
        job=Mock(assigned=True)
        process=Mock(args=['synthetic'],returncode=1)
        process.poll.return_value=1
        failure=subprocess.TimeoutExpired('synthetic-bash',TIMEOUT)
        process.communicate.side_effect=[failure,(b'partial output',b'captured failure')]
        module=sys.modules[__name__]
        with patch.object(module,'_WindowsJob',return_value=job), patch.object(subprocess,'Popen',return_value=process):
            with self.assertRaises(subprocess.TimeoutExpired) as caught:
                _run_owned(Path('synthetic-bash'),Path('synthetic-root'),{},windows=True)
        self.assertIs(failure,caught.exception)
        job.terminate.assert_called_once();job.close.assert_called_once()
        process.kill.assert_not_called()
        self.assertIn('partial output',str(caught.exception.__notes__))
        for stream in (process.stdin,process.stdout,process.stderr):stream.close.assert_called_once()

    def test_cleanup_permission_failure_cannot_be_hidden_by_success(self):
        job=Mock(assigned=True)
        problem=PermissionError('Synthetic owned cleanup failure')
        job.close.side_effect=problem
        process=Mock(args=['synthetic'],returncode=0)
        process.communicate.return_value=(b'',b'')
        module=sys.modules[__name__]
        with patch.object(module,'_WindowsJob',return_value=job), patch.object(subprocess,'Popen',return_value=process):
            with self.assertRaises(BaseExceptionGroup) as caught:
                _run_owned(Path('synthetic-bash'),Path('synthetic-root'),{},windows=True)
        self.assertIn(problem,caught.exception.exceptions)
