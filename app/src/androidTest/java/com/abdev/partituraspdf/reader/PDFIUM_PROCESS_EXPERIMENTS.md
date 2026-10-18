# Phase G: controlled Pdfium process-state experiments

Status: PREPARED, NOT EXECUTED in Codex (no device/KVM). This is test-only tooling,
not an acceptance waiver, production repair, or instruction to change CI.

`ReaderPdfiumProcessStateTest` uses real canonical bytes and direct PdfRenderer or
production AndroidPdfReadEngine. Every valid attempt reads page count and renders
page zero, then closes the bitmap/page/session/descriptor before the next attempt.
Negative input must be rejected. A valid reopen failure is a **test failure**.
No fixture is skipped, no error is reset/suppressed, no retry is introduced.

## Experimental design

Run EACH selected method in a separate instrumentation invocation, after stopping
both test/application processes on a deliberately selected disposable emulator.
Inside a method the entire sequence runs in one process; PID/process-start evidence
must be identical for all its attempts. Different JUnit methods, class sorting,
new coroutine threads, or a new invocation UUID alone DO NOT prove process isolation.
Running the whole class/suite together is useful regression evidence, but is NOT a
clean-process experiment: earlier classes/methods may already have poisoned Pdfium.

| Method | Engine | Within-process sequence |
| --- | --- | --- |
| a_cleanDirectRepeatedValidOpens | direct | valid 01, valid 01 again, valid 02 |
| b_corruptThenValidDirect | direct | valid 01, corrupt 15, valid 01 again |
| c_encryptedThenValidDirect | direct | valid 01, protected 14, valid 01 again |
| d_freshProcessRecoveryDirect | direct | valid 01, valid 02; invoke AFTER each negative experiment in a fresh process |
| e_corruptThenValidAdapter | production adapter | valid 01, corrupt 15, valid 01 again |
| f_encryptedThenValidAdapter | production adapter | valid 01, protected 14, valid 01 again |
| g_corruptEncryptedThenValidDirect | direct | valid 01, corrupt 15, protected 14, valid 01 again |
| h_corruptEncryptedThenValidAdapter | production adapter | valid 01, corrupt 15, protected 14, valid 01 again |
| i_encryptedCorruptThenValidDirect | direct | valid 01, protected 14, corrupt 15, valid 01 again |
| j_encryptedCorruptThenValidAdapter | production adapter | valid 01, protected 14, corrupt 15, valid 01 again |

Logs tagged `ReaderPdfiumProcess` contain API, PID, process start time, invocation,
method, sequence, fixture ID, engine, verified size/hash/descriptor state,
construction/page-count/render milestones, actual native exception or typed error,
resource-close count, ownership, descriptor closure and cleanup failures. Copy
bytes are verified again through `pread`, preserving offset. Only synthetic IDs
are logged. Cleanup exceptions fail the test; they are not ignored. If cleanup
fails before a valid reopen, record that sequence as incomplete, not contamination
proved. Finalizer warnings must be retained separately in logcat.

## Prerequisites and Windows PowerShell execution

Build the SAME revision's APKs, with the repository-supported JDK/SDK:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=4 --console=plain
adb devices -l
```

Use an already owned disposable emulator, never a physical device, unrelated AVD,
or user installation. Do not wipe, clear data, uninstall, reprovision, or change
settings. The commands below install the test builds and force-stop ONLY their two
packages. Select/confirm identity before those operations. Run separately on API
26 and 27; repeat on API 36 as a control.

```powershell
$Serial = 'emulator-5554' # Replace deliberately; never auto-select the first device.
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'BLOCKED: emulator required' }
$IsEmulator = (adb -s $Serial shell getprop ro.kernel.qemu | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $IsEmulator -ne '1') { throw 'BLOCKED: emulator identity not verified' }
adb -s $Serial emu avd name
$Api = (adb -s $Serial shell getprop ro.build.version.sdk | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $Api -notin @('26','27','36')) { throw 'BLOCKED: wrong API' }
if ((Read-Host "Confirm disposable emulator: type $Serial") -cne $Serial) { throw 'Cancelled' }
$App = 'com.abdev.partituraspdf'
$Test = "$App.test"
$Runner = "$Test/androidx.test.runner.AndroidJUnitRunner"
$Class = "$App.reader.ReaderPdfiumProcessStateTest"
$Root = Join-Path (Get-Location) ('build/reader-pdfium-process/' + [guid]::NewGuid())
New-Item -ItemType Directory -Force $Root | Out-Null
git rev-parse HEAD | Set-Content (Join-Path $Root 'revision.txt')
adb -s $Serial shell getprop ro.build.fingerprint | Set-Content (Join-Path $Root 'image.txt')
adb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: app install failed' }
adb -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: test install failed' }

function Run-One([string]$Method) {
    foreach ($Package in @($App,$Test)) {
        adb -s $Serial shell am force-stop $Package
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: cannot stop selected test package' }
        $Remaining = (adb -s $Serial shell pidof $Package | Out-String).Trim()
        if ($Remaining) { throw "BLOCKED: process still alive: $Package" }
    }
    $Tag = "$Method-$([guid]::NewGuid())"
    $Dir = Join-Path $Root $Tag
    New-Item -ItemType Directory $Dir | Out-Null
    adb -s $Serial shell am instrument -w -r -e class "$Class#$Method" -e readerProcessRun $Tag $Runner 2>&1 |
        Tee-Object -FilePath (Join-Path $Dir 'instrumentation.txt')
    $TransportExit = $LASTEXITCODE
    adb -s $Serial logcat -d -v threadtime ReaderPdfiumProcess:I ReaderFocusAcceptance:I System.err:W PdfRenderer:V PdfUtils:V AndroidRuntime:E '*:S' |
        Set-Content (Join-Path $Dir 'logcat.txt')
    # adb exit zero alone does NOT imply a passing JUnit test. Preserve failures and
    # continue to recovery D; inspect actual instrumentation status/test counts.
    "adbExit=$TransportExit; serial=$Serial; api=$Api; invocation=$Tag" |
        Set-Content (Join-Path $Dir 'identity.txt')
    if ($TransportExit -ne 0) { throw 'BLOCKED: instrumentation transport failed' }
}
Run-One 'a_cleanDirectRepeatedValidOpens'
Run-One 'b_corruptThenValidDirect'
Run-One 'd_freshProcessRecoveryDirect'
Run-One 'c_encryptedThenValidDirect'
Run-One 'd_freshProcessRecoveryDirect'
Run-One 'e_corruptThenValidAdapter'
Run-One 'd_freshProcessRecoveryDirect'
Run-One 'f_encryptedThenValidAdapter'
Run-One 'd_freshProcessRecoveryDirect'
Run-One 'g_corruptEncryptedThenValidDirect'
Run-One 'd_freshProcessRecoveryDirect'
Run-One 'h_corruptEncryptedThenValidAdapter'
Run-One 'd_freshProcessRecoveryDirect'
Run-One 'i_encryptedCorruptThenValidDirect'
Run-One 'd_freshProcessRecoveryDirect'
Run-One 'j_encryptedCorruptThenValidAdapter'
Run-One 'd_freshProcessRecoveryDirect'
```

For bash/macOS/Linux use the same `adb -s <owned-emulator> shell am instrument`
command and stop/verify both packages before EACH invocation. Keep unique report
files; do not clear logcat or erase evidence. No emulator boot is performed here.

For JUnit XML/HTML instead of raw runner output, use the existing connected Gradle
task with `ANDROID_SERIAL` set to that same confirmed emulator and select ONE
method per invocation. Verify processes stopped first, retain the PID logs, and
copy reports after EACH run before Gradle can overwrite them:

```powershell
$env:ANDROID_SERIAL = $Serial
.\gradlew.bat :app:connectedDebugAndroidTest --rerun-tasks --max-workers=4 --console=plain `
  "-Pandroid.testInstrumentationRunnerArguments.class=$Class#b_corruptThenValidDirect" `
  "-Pandroid.testInstrumentationRunnerArguments.readerProcessRun=$([guid]::NewGuid())"
# Preserve app/build/outputs/androidTest-results/connected and
# app/build/reports/androidTests/connected in a unique directory, plus tagged logcat.
```

Record actual test failure/error/skip counts. Missing results, process crashes,
transport errors, or an initial valid-open failure make a controlled sequence
BLOCKED/inconclusive, not PASS. A successful Gradle build is not Android execution.

## Targeted keyboard evidence

On that same disposable emulator, separately invoke:

```powershell
adb -s $Serial shell am instrument -w -r -e class 'com.abdev.partituraspdf.reader.ReaderScreenTest#tabTraversalReachesControlsAndMenuFitWithoutCapturingPageShortcuts' $Runner
adb -s $Serial shell am instrument -w -r -e class 'com.abdev.partituraspdf.reader.ReaderScreenTest#keyboardShortcutsAreLocalToPageFocusAndRespectModifierKeys' $Runner
```

Collect `ReaderFocusAcceptance` logs before/open/during selection/after removal,
actual JUnit outcomes, and the revision/API identity. Popup focus is distinct from
Activity window focus. Both PAGE/WIDTH keyboard selections, returned options focus,
Tab navigation and Back must pass; no TalkBack acceptance is implied.

## Interpretation and production boundary

- Clean A passes, B/C valid reopen fails, fresh D passes (verified distinct process):
  process-state contamination supported. Compare adapter E/F and both input orders G/H/I/J.
- Only one engine fails: investigate its ownership/metadata/cleanup boundary.
- Byte/descriptor/close assertions fail: resource/input defect, not native poisoning
  established. Preserve the exact failed operation and exception.
- A or D fails in a genuinely fresh process: broader native/image/input issue;
  contamination alone is not established. Default-suite ordering is not a fresh test.
- All selected sequences pass: hypothesis not reproduced in that environment.

If the same real engine sequence fails in one application process after a valid
baseline, it can affect users opening an invalid then valid PDF. Test ordering is
not a production fix. Shared-adapter edits, a separate renderer process, native
changes, dependencies, manifest or CI isolation require separate owner approval.
Do not reset native globals, fabricate successful opens, reorder away failures,
skip negatives, retry automatically, or declare Phase G compatible without evidence.

## Evidence register

Codex: all experiments A-J **NOT RUN**; fresh-process isolation **NOT DEMONSTRATED**.
No controlled Android results, finalizer classification, or physical-device PASS
is asserted by this document. Populate owner reports from actual execution before
choosing root-cause Case A/B/C/D/E or proposing architectural changes.
