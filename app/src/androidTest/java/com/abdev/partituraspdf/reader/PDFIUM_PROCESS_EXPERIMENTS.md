# Phase G: controlled Pdfium process-state experiments

Status: PREPARED, NOT EXECUTED in Codex (no device/KVM). This is test-only tooling,
not an acceptance waiver, production repair, or instruction to change CI.

`ReaderPdfiumProcessStateTest` uses real canonical bytes and direct PdfRenderer or
production AndroidPdfReadEngine. Every valid attempt reads page count and renders
page zero, then closes the bitmap/page/session/descriptor before the next attempt.
Negative input must be rejected. A valid reopen failure is a **test failure**.
No fixture is skipped, no error is reset/suppressed, no retry is introduced.

## Verified invocation runner (preferred)

CI #74's full-suite results on `2c39ae0` are NOT controlled fresh-process results.
The suite ran the methods after other native tests. Do not infer A/B/C or recovery
from those results. The original manual recipe below is an alternative, not a
second mandatory run of the same experiments.

`run_pdfium_process_experiments.py` now automates the isolation checks. After
building and deploying both APKs on the confirmed disposable emulator (see below):

```powershell
python app/src/androidTest/java/com/abdev/partituraspdf/reader/run_pdfium_process_experiments.py --serial emulator-5554
```

Use `python3` on hosts where that is the Python 3 executable. The runner asks for
explicit confirmation of the displayed serial/AVD/API. It derives its actual
component from the merged test manifest, verifies `pm list instrumentation`, and
compares installed APK SHA-256 values with this checkout's APKs before any stop.
The current merged manifest identifies:

- Test package: `com.abdev.partituraspdf.test`.
- Target: `com.abdev.partituraspdf`.
- Runner: `androidx.test.runner.AndroidJUnitRunner`.

These are observed build values, not assumptions used to select arbitrary packages.
A foreign manifest, physical device, changed emulator identity, mismatched APK, dirty checkout, missing result,
crash, reused process identity, skipped test or wrong invocation is rejected.
The runner never installs, uninstalls, clears data, creates an AVD or edits CI.
It force-stops only the two verified project packages on the confirmed emulator.

Before EACH selected-method launch it records both packages' PIDs, stops them,
verifies PID absence and reads device uptime. The test exports actual native attempt
JSON via the instrumentation result Bundle, including PID/start time, selected
class/method and an invocation token. The runner requires one executed selected
JUnit test, a consistent new PID/start identity within the launch interval, expected
fixture ordering, and no identity reused from previous invocations. Device-uptime
rollback is rejected so emulator reboot cannot masquerade as process-only recovery. Each negative
sequence is followed by separate direct AND adapter recovery invocations.

Evidence is retained under a unique `build/reader-pdfium-controlled/` directory:
`summary.json`, per-invocation raw instrumentation and tagged logcat. The summary
includes revision, installed APK digests, image fingerprint, API, PID before/after
stop, process start time, ordered outcomes and actual PASS/FAIL/BLOCKED status.
A real JUnit failure stays FAIL, including with a nonzero adb exit if full trustworthy
execution evidence exists. Incomplete evidence is BLOCKED. No test failure is
accepted as compatibility PASS. A full-suite run does not replace these checks.

The runner is manual-only: neither Android tests nor CI execute this Python file.
Its host safety/evidence regressions can run without adb or an emulator:

```powershell
python -m unittest discover -s app/src/androidTest/java/com/abdev/partituraspdf/reader -p test_pdfium_process_runner.py -v
```

Mocked host tests validate orchestration guards only; they cannot demonstrate an
Android process restart, native recovery, or API compatibility.

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
| k_cleanAdapterRepeatedValidOpens | production adapter | valid 01, valid 01 again, valid 02 |
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
[xml]$Merged = Get-Content app/build/intermediates/merged_manifest/debugAndroidTest/mergeDebugAndroidTestManifest/AndroidManifest.xml
$Namespace = 'http://schemas.android.com/apk/res/android'
$Test = $Merged.manifest.package
$App = $Merged.manifest.instrumentation.GetAttribute('targetPackage', $Namespace)
$RunnerName = $Merged.manifest.instrumentation.GetAttribute('name', $Namespace)
if ($App -ne 'com.abdev.partituraspdf' -or $Test -ne "$App.test" -or $RunnerName -ne 'androidx.test.runner.AndroidJUnitRunner') { throw 'BLOCKED: foreign manifest' }
$Runner = "$Test/$RunnerName"
adb -s $Serial shell pm list instrumentation
# Verify this component/target matches the installed entry before proceeding.
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
Run-One 'k_cleanAdapterRepeatedValidOpens'
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

Codex: all controlled experiments A-K **NOT RUN**; fresh-process isolation **NOT DEMONSTRATED**.
No controlled Android results, finalizer classification, or physical-device PASS
is asserted by this document. Populate owner reports from actual execution before
choosing root-cause Case A/B/C/D/E or proposing architectural changes.


## Native mechanism and conditional engineering proposal — 2026-10-19

This is an evaluation requested by the owner, NOT an implemented architecture or a
claim that same-process contamination has been proved on the CI image. No shared
adapter/manifest/CI/dependency changes are made. Controlled execution remains the
prerequisite for choosing an architectural remedy.

Source-level evidence: Android 8's PdfUtils checks Pdfium's last error immediately
after library initialization and on close/page-count operations. Historical Pdfium
has a process-global last-error variable on non-Windows builds. These sources
explain a plausible persistent-error mechanism, including close/finalizer warnings:

- https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/jni/android/graphics/pdf/PdfUtils.cpp
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.1.0_r1/core/jni/android/graphics/pdf/PdfUtils.cpp
- https://github.com/chromium/pdfium/blob/chromium/3071/fpdfsdk/fpdfview.cpp

The historical Pdfium branch is contextual source evidence, not proof of the exact
native binary packaged in the emulator. The runner records the image fingerprint.
Public issue reports mentioned in the owner's brief have not been independently
verified here; no particular issue is asserted to match our image. Current hosted
full-suite failures establish valid-byte native-open failures after earlier negative
tests, not a controlled fresh-process/negative/recovery sequence. No supported
Android public API to reset this native error state has been demonstrated.

### Options and decision boundary

A. **Reliable prevalidation:** a header/hash check proves bytes, not renderability.
A separate full PDF parser would add its own security/memory/licensing obligations
and may not predict platform-version-specific Pdfium failures. Prevalidation cannot
currently guarantee that every native poisoning trigger is excluded. Do not choose
this as the sole repair without proving that guarantee.

B. **Remote native process:** if both real engine and direct controlled sequences
prove process poisoning, this is the smallest plausible containment using the
existing rendering engine without restarting the UI application. Prefer a private
bound service with `exported=false` and `isolatedProcess=true`, accepting only owned
seekable PFD capabilities, not private paths/SAF URI access. A regular `:pdf_renderer`
process is simpler but retains app UID/private-data privileges; process separation
alone does not sandbox untrusted native parsing. Native sessions/requests need
parent-validated epochs/tokens, one render worker and one public retained lease.
Binder death invalidates the remote session; a poisoned service must be replaced
before a later valid open. Restarting the whole app is not a recovery solution.

Proposed files for a separately approved change (none created by this task):

- `app/src/main/java/com/abdev/partituraspdf/pdf/android/RemotePdfReadEngine.kt`:
  implement existing PdfReadEngine/PdfReadSession/RenderedPage contracts; retain
  transferred input ownership and own service binding/cancellation/close.
- `app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/PdfRenderService.kt`:
  own native PdfRenderer/session/worker and explicit poisoned-process shutdown.
- `app/src/main/aidl/com/abdev/partituraspdf/pdf/android/isolated/IPdfRenderService.aidl`:
  proposed private open(PFD, token, limits), render(epoch, token, bounded request,
  outputPFD), cancel(token), close(epoch), typed status/metadata and death handling.
  Exact IPC schema needs owner-approved design review; no public A1 change assumed.
- `app/build.gradle.kts`: enable AIDL generation if the approved IPC design uses
  AIDL (a separate owner-approved build change; no dependency is assumed).
- `app/src/main/AndroidManifest.xml`: private service declaration and isolation.
- `app/src/main/java/com/abdev/partituraspdf/app/di/AppGraphFactory.kt`: select/wire
  approved engine and binding lifetime. G consumes the same public session API.
- Matching shared adapter/service Android and JVM tests in a separate approved scope.

Do not send full bitmap byte arrays in Binder. API 26 lacks public API 27 SharedMemory:
evaluate a caller-created bounded file/PFD-backed RGBA buffer (FileChannel mapping),
validate dimensions/stride/length/tokens before allocation, and return only metadata
and descriptors over Binder. The child bitmap, shared output buffer, parent Bitmap
and Surface buffers create additional peak memory. One public G lease does not mean
one process-wide allocation. Account for every concurrent buffer under the existing
budget or obtain a separately approved budget design; benchmark latency/RSS/native
memory on constrained hardware before claiming 2 GB tablet suitability.

Cancellation must await native completion or verified service death before releasing
shared output. Never recycle a caller bitmap while G's graphical consumer remains
attached. Keep stale-generation rejection, checkpoint identity, zero prefetch and
one scheduled render. A child crash must not become a fake successful page; preserve
cancellation and expose an appropriate existing typed conflict/failure, followed by
explicit safe reopening. Close every transferred FD/session on cancellation/death.

C. **Independent updated engine:** a maintained Android PDFium distribution could
avoid platform-native state, but requires verified API 26/native ABI support,
security updates, APK-size/memory measurement and full transitive license review.
MuPDF's AGPL/commercial licensing requires explicit legal/licensing approval; an
Android wrapper's license alone does not establish compatibility of bundled native
code. No engine, version, dependency, or license change is selected or added here.

Conditional recommendation: first run A-K with verified process identity. If clean
opens fail, investigate image/input/adapter lifetime instead of assuming poisoning.
If negative input consistently breaks both engines and a new process restores them,
seek owner approval for option B's ADR/prototype and measured buffer budget. If only
the adapter fails, propose its smallest demonstrated ownership fix before any IPC.
Do not implement any of these shared changes based solely on source correlation.

Proposed migration and acceptance after approval:

1. Preserve failing controlled sequences and exact image/commit evidence.
2. Approve the IPC/security/memory ADR, interfaces, affected files and licensing.
3. Implement and validate the shared adapter/service behind existing public contracts.
4. Verify valid/invalid/valid recovery with the SAME parent app PID and a replaced
   native-service identity, on API 26/27/36. Test corrupt and encrypted inputs separately
   and in both orders; cover crashes, cancellation, close failures and stale tokens.
5. Verify FD accounting, oversized/malformed IPC rejection, one render/lease,
   graphical detach/recycle safety, rapid navigation, all 13 valid canonical fixtures
   and negative fixtures. Measure peak memory/latency; retain physical acceptance debt.
6. Wire only through separately approved shared composition, rerun mandatory gates,
   and review integration responsibilities. No Phase I implementation is authorized here.

### Minimal proposed hosted execution step (requires separate CI authorization)

Do not edit the current workflow under this task. An owner-approved diagnostic step
could reuse its existing owned API 26/27 emulator, SDK, runner and APK deployment:

1. Build and install the exact app/test APK pair, record SHA/image/explicit serial.
2. Invoke the verified runner with that disposable serial and explicit confirmation.
3. Preserve `summary.json`, every raw instrumentation result and tagged logcat with
   an always-run artifact upload, including when a sequence fails.
4. Inspect clean baselines before interpreting negative/recovery results. Require
   verified process identity per invocation; report failed valid reopens as FAIL.
5. Keep the complete instrumentation suite and existing quality gate. Isolated valid
   acceptance must not conceal same-process invalid-to-valid production failure.
6. Review findings before authorizing any shared-adapter/process implementation.

This proposal introduces no workflow inputs, filters, skips, or CI policy changes.
