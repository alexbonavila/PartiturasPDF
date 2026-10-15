# Local Android E2E automation

## Scope and execution status

This opt-in infrastructure exercises real API 26 DocumentsUI and production SAF
adapters through the existing debug-only `SafGrantAcceptanceActivity`. It does
not implement a reader/library UI, change storage contracts, request broad storage
permissions, or replace ordinary CI. Only `saf` is implemented. `reader`, `library`,
`full`, and `coverage` return **BLOCKED / exit 2**, without executing substitute tests.

The cloud implementation validation compiles the opt-in test APK and tests the
host failure contracts with mocks. **Real unattended SAF success, the deliberate
on-device failure capture, and Android regression execution are NOT RUN here**:
this environment has no attached emulator or KVM. Compilation is not acceptance.
The selectors and system permission behavior still require a real API 26 run.
No phase/device acceptance or coverage gate is waived.

## Host diagnostics and prerequisites

Start with `python3 scripts/local_e2e.py doctor` (`python` on Windows). The command
prints JSON and exits **0** for prerequisite readiness, **2** for BLOCKED. It does
not start an AVD, install an APK, clear data, grant/release permissions or claim an
Android test PASS. Provisioning and SAF runs enforce the same preflight; a failed
run retains `doctor.json` alongside its summary, JUnit and HTML reports.

The diagnostic reports native OS/CPU (including Rosetta detection), Python,
JDK executable/version, selected SDK, SDK tool paths/versions, API 37 platform,
37.0.0 build tools, installed API 26 image metadata/ABIs, `emulator -accel-check`,
and dedicated AVD ownership. If an owned AVD is online it checks only its reserved
serial, name, emulator flag, API and boot status. An absent AVD is UNPROVISIONED,
which permits explicit provisioning; malformed ownership or a foreign occupant
blocks operation. Readiness is permission to **attempt** boot, not proof of boot.

Required: Python **3.10+**, a working **JDK 25** (`JAVA_HOME`), SDK command-line
tools, platform-tools, emulator, platform `platforms;android-37.0` (API 37), build
tools `build-tools;37.0.0`, and the installed
API 26 Google APIs image selected for the native host (`x86_64`, or `arm64-v8a`
on Apple Silicon). The profile is `pixel_c`.
The platform check accepts metadata API 37 and 37.0; use the package available in
your SDK catalog. The installed cloud SDK package is `platforms;android-37.0`.
Command-line tools can be `latest` or a numbered installation: the runner selects
`latest` first, then the highest installed numeric version. It never auto-installs
packages, accepts licenses, or changes system virtualization settings.

SDK selection: explicit global `--sdk`, then `ANDROID_HOME`, `ANDROID_SDK_ROOT`,
repository `local.properties` (`sdk.dir`), then the platform default SDK directory.
Windows batch launches reject CMD metacharacters (`& | < > ^ % !` and quotes)
in arguments; use ordinary SDK/project paths (spaces are supported).
Use the same SDK for Gradle and the runner; resolve conflicting local settings
before execution. Explicit global options precede `doctor`, `provision`, or `run`.

| Host | L01B native image selection | Execution evidence |
| --- | --- | --- |
| Windows x86_64 | x86_64 API 26 image plus usable WHPX/AEHD (or another emulator-reported usable accelerator) | Host probes/dispatch covered by mocks; Windows boot/E2E NOT RUN |
| Intel macOS | `system-images;android-26;google_apis;x86_64` plus usable Hypervisor.Framework | Host probes covered by mocks; native macOS/E2E NOT RUN |
| Apple Silicon, including x86 Python under Rosetta | `system-images;android-26;google_apis;arm64-v8a` plus usable Hypervisor.Framework | Host selection/provisioning/ABI checks covered by mocks; native boot and SAF E2E NOT RUN here |
| Windows ARM / Linux ARM / all other combinations | BLOCKED; no mapping authorized | NOT RUN |
| Linux x86_64 | Same image plus usable KVM | Builds/probes executed here; KVM unavailable, E2E NOT RUN |

`host_image()` is the single compatibility decision used by doctor, provisioning,
ownership checks and emulator launch. It uses native hardware detection (including
`sysctl.proc_translated` and `hw.optional.arm64` for Rosetta), not Python's architecture alone. Installed
image metadata must match the selected package, directory, API **26**, ABI and
actual `system.img`. API 27/36 images do not satisfy this suite. On macOS, emulator
version/acceleration probes and launch explicitly use `/usr/bin/arch -arm64` or
`-x86_64` according to the native host. Every launch includes **`-accel on`**;
failed hardware acceleration must fail boot, never fall back to software.

The version-1 ownership manifest remains unchanged in shape: `image` records the
actual selected SDK package. Existing valid x86_64 v1 state stays usable on the
supported x86_64 hosts without rewriting it. ARM64 v1 state is validated against
the ARM64 package/configuration on Apple Silicon. A manifest/config image mismatch,
conflicting `abi.type`/tag, foreign name/path, or a booted device reporting the wrong
API or ABI blocks operations. Run reports distinguish selected `abi` from the
boot validation's `observed_abi`. No AVD is adopted, wiped or migrated automatically.
An x86_64 state copied to Apple Silicon stays untouched and BLOCKED; preserve it
and explicitly choose a **new private state directory**, for example
`python3 scripts/local_e2e.py --home build/local-e2e-arm64 doctor` followed by
`--home build/local-e2e-arm64 provision`, instead of editing its manifest.
For the owner's reported UNPROVISIONED ARM64 state, the normal default home is
appropriate once doctor passes. These choices create no new Git branch.

## Windows PowerShell procedure

These commands use native Windows SDK `.bat`/`.exe` names. Their path/argument
selection is unit-tested and PowerShell syntax is checked using PowerShell on the
cloud Linux host. **Native Windows provisioning and execution are not verified**.
Install Python/JDK/SDK first; set `JAVA_HOME` to your JDK 25 and `ANDROID_HOME` to
your SDK. Run from the repository root; close concurrent Gradle builds.

```powershell
# Substitute actual installation paths; do not copy cloud-specific paths.
$env:JAVA_HOME = 'C:\Tools\jdk-25'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
# Android Studio normally installs latest; if numbered, use that directory instead.
$sdkmanager = "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat"
& $sdkmanager 'platform-tools' 'emulator' 'platforms;android-37.0' 'build-tools;37.0.0' 'system-images;android-26;google_apis;x86_64'
if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed' }
& $sdkmanager --licenses
if ($LASTEXITCODE -ne 0) { throw 'Resolve SDK licenses before continuing' }
python scripts/local_e2e.py doctor
if ($LASTEXITCODE -ne 0) { throw 'Resolve doctor blockers; do not provision' }
python scripts/local_e2e.py provision --port 5580
if ($LASTEXITCODE -ne 0) { throw 'Provisioning blocked; preserve existing state' }
python scripts/local_e2e.py run --suite saf
$runExit = $LASTEXITCODE  # 0 PASS, 1 FAIL, 2 BLOCKED
$report = Get-ChildItem build/local-e2e/reports -Directory | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
Get-Content (Join-Path $report.FullName 'summary.json')
Get-Content (Join-Path $report.FullName 'junit.xml')
Invoke-Item (Join-Path $report.FullName 'index.html')
Write-Host "Runner exit: $runExit; report: $($report.FullName)"
```

Do not rerun `provision` over existing state. Subsequent runs use `doctor` and
`run`. For a controlled failure after authentic authorization:

```powershell
python scripts/local_e2e.py run --suite saf --inject-failure
if ($LASTEXITCODE -ne 1) { throw 'Expected a completed functional failure, not PASS/BLOCKED' }
$report = Get-ChildItem build/local-e2e/reports -Directory | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
'summary.json','junit.xml','index.html','acquire.log','screenshot.png','hierarchy.xml','logcat.txt','adb-final.txt' | ForEach-Object {
    if (-not (Test-Path (Join-Path $report.FullName $_))) { throw "Missing evidence: $_" }
}
```

## Intel macOS procedure

Use the repository-pinned JDK 25 and a native x86_64 Mac. These commands are
reviewed and their POSIX syntax checked; **native macOS execution is NOT RUN here**.
If native hardware is Apple Silicon, use the following ARM64 procedure instead.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
if [ "$(/usr/sbin/sysctl -n hw.optional.arm64 2>/dev/null)" = 1 ] ||
   [ "$(/usr/sbin/sysctl -n sysctl.proc_translated 2>/dev/null)" = 1 ]; then
  echo 'Use the Apple Silicon ARM64 procedure below'
else
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platform-tools' 'emulator' 'platforms;android-37.0' 'build-tools;37.0.0' 'system-images;android-26;google_apis;x86_64' &&
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses &&
  python3 scripts/local_e2e.py doctor &&
  python3 scripts/local_e2e.py provision --port 5580 &&
  python3 scripts/local_e2e.py run --suite saf
fi
```

## Apple Silicon macOS procedure

Use a native ARM64 JDK 25 and the **ARM64** API 26 Google APIs image. If those
packages are already installed, start at `doctor`; do not reinstall or recreate an
existing owned AVD. No SDK path specific to the owner's machine is embedded.
The following commands are reviewed/syntax-checked and host behavior is tested
with mocks; **this cloud has no native ARM64 acceleration, so native macOS boot,
authentic SAF and controlled-failure capture are NOT RUN here**. The owner's
reported native CPU/image and working Hypervisor.Framework are prerequisites,
not evidence that the SAF suite passed.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
if [ "$(/usr/sbin/sysctl -n hw.optional.arm64 2>/dev/null)" != 1 ] &&
   [ "$(/usr/sbin/sysctl -n sysctl.proc_translated 2>/dev/null)" != 1 ]; then
  echo 'BLOCKED: this procedure requires native Apple Silicon hardware'
else
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platform-tools' 'emulator' 'platforms;android-37.0' 'build-tools;37.0.0' 'system-images;android-26;google_apis;arm64-v8a' &&
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses &&
  python3 scripts/local_e2e.py doctor &&
  python3 scripts/local_e2e.py provision --port 5580 &&
  python3 scripts/local_e2e.py run --suite saf
fi
```

Expected doctor fields on a ready Apple Silicon host: `native_architecture=arm64`,
`requested_avd.abi=arm64-v8a`, package
`system-images;android-26;google_apis;arm64-v8a`, `acceleration_available=true`,
and `dedicated_avd.status=UNPROVISIONED` before first provisioning. Its diagnostic
PASS means ready to **attempt** boot. A real run must additionally verify the
reserved AVD identity, API 26, `ro.product.cpu.abi=arm64-v8a`, completed boot and
all four authentic SAF stages with retained evidence. Native Python is preferred;
a Rosetta Python process still selects ARM64 hardware and launches the emulator
through `arch -arm64`, never the x86_64 guest image or software fallback.

For an existing owned AVD, run only `doctor` then `run` (no provisioning). To inspect
the exact report directory printed by the runner, substitute it below:

```bash
report='build/local-e2e/reports/<printed-UTC-and-UUID>'
cat "$report/summary.json" "$report/junit.xml"
open "$report/index.html"
python3 scripts/local_e2e.py run --suite saf --inject-failure
result=$?
test "$result" -eq 1 || { echo "Controlled failure NOT demonstrated: exit $result"; exit 2; }
# Use the newly printed report path; verify screenshot.png, hierarchy.xml,
# logcat.txt, adb-final.txt and acquire.log before sharing the complete directory.
```

Linux uses the same `python3` commands, a native x86_64 image and usable KVM;
configure `JAVA_HOME`/`ANDROID_HOME` for that installation. A working SDK/build does
not substitute for a usable accelerator.

Provisioning creates a uniquely named Pixel C AVD in **`build/local-e2e/avd`**,
not the normal user AVD home, plus an ownership manifest. It never overwrites or
adopts existing provisioning and never uses `-wipe-data`. Port 5580 must be free;
choose another even port during provisioning if needed. Do not edit the ownership
manifest or copy an existing user's AVD into this directory. Failed partial
provisioning is left for owner inspection, not silently overwritten. Keep the
local state directory private and trusted. A stale `runner.lock` requires manual
review (confirm its process exited) before removal. Do not delete provisioning
while its emulator is running. Provisioning is never run by ordinary tests/CI.

Optional global paths precede the subcommand, for example:

```powershell
python scripts/local_e2e.py --sdk "$env:ANDROID_HOME" --home "C:\E2E\Partituras" provision
python scripts/local_e2e.py --sdk "$env:ANDROID_HOME" --home "C:\E2E\Partituras" run --suite saf
```

The runner starts only its owned AVD if stopped, verifies its name, serial,
`ro.kernel.qemu`, API level and boot completion, and addresses every device command
with `adb -s emulator-<reserved-port>`. A different AVD on that port is BLOCKED,
without installing or deleting anything there. Other devices may stay connected.
There is no `adb kill-server`, package clear, uninstall, or physical-device mode.
An already-running owned emulator is left running; a child launched by this run
is terminated after evidence collection and cleanup.

Use stock **en-US**, an unlocked AVD without a PIN, and the API 26 Google APIs
DocumentsUI (`com.android.documentsui`). Fresh provisioned images normally have
this locale; any other locale is BLOCKED before installation. If needed, set
English (United States) once in this dedicated AVD's Settings before running.
During execution the runner fixes natural rotation, disables animations and sets
a ten-minute display timeout, restoring the original settings afterward. API 26
picker layout/English accessibility labels are intentional compatibility bounds;
unsupported OEM/localized pickers fail visibly rather than using coordinate taps.

## Authentic stages and isolation

1. Verify the canonical `01-single-a4.pdf` hash against `test-fixtures/manifest.json`.
   Build with `-I scripts/local-e2e.init.gradle`, retain APK copies and SHA-256s,
   install on the owned emulator and copy the PDF to a newly created
   `/sdcard/Download/PartiturasE2E-<random-run-id>/fixture.pdf`.
2. `acquire`: save the pre-existing OS grant set and debug selection, launch the
   existing localized debug activity, click Select, navigate the **real** system
   tree picker and select only that unique folder. Assert persisted READ without
   WRITE, production catalog/seekable PDF access, and the actual PDF hash.
3. Host force-stops the target package. A separate `restartAndRelease`
   instrumentation invocation proves persisted access in a new process, then
   calls the real production release API for only that run's tree.
4. Host force-stops again and independently checks that the PDF copy still exists
   with its original hash. `lossAndRestore` checks known-grant revocation and a
   real `SecurityException` on read, OS URI READ denial and production listing
   `PERMISSION_DENIED`, then selects the folder again through the
   picker and verifies restored access. Denied access is never called deletion.
5. Another host force-stop precedes `restoredAfterRestart`, proving restored access
   also survives process death. All four functional stages must execute exactly
   once and pass. No functional retry is performed.
6. After evidence is saved, a separate `cleanupOwnedGrant` invocation releases
   only this tree, verifies other grants are unchanged, restores the prior debug
   selection and deletes only its run-state file. Host cleanup deletes only the
   UUID folder it successfully created. Application Room/DataStore/user PDFs and
   all pre-existing grants are untouched. Installed debug/test APKs stay on the
   dedicated AVD. If ADB or ownership verification fails, cleanup is BLOCKED and
   reports explicitly identify the retained owned artifacts; do not clear app
   data to conceal a failure.

AndroidJUnitRunner is launched by the explicit Gradle `:app:localE2eStage` Exec
task with a single selected method and serial-qualified ADB. This avoids Gradle's
ordinary connected-device discovery touching other devices. The init script adds
`scripts/local-e2e/androidTest` only when explicitly requested. Ordinary Gradle/CI
commands neither compile nor discover these picker tests; the existing regression
suite and coverage thresholds remain unchanged. Rebuild the ordinary test APK
without `-I` before running the standard regression suite.

## Results and controlled failure

Every invocation gets a new `build/local-e2e/reports/<UTC>-<UUID>/` directory with
`summary.json`, standard `junit.xml`, `index.html`, commit/dirty-tree identity,
selected-device identity (or explicit NOT SELECTED), counts and Gradle logs.
Executed stages also retain raw AndroidJUnitRunner protocol/Gradle output; the
runner requires one matching start and terminal test result and runner completion.
Missing/duplicate/skipped/crashed results cannot pass, regardless of ADB's exit
code. XML/HTML are generated from the validated protocol; they are not copied
from potentially stale connected-test reports. Infrastructure failures are JUnit
errors and have **BLOCKED / exit 2**; real assertion failures have **FAIL / exit 1**;
only all stages plus successful cleanup yield **PASS / exit 0**.

After execution (including success), the runner independently attempts screenshot, UI hierarchy, recent
logcat, package/activity state and ADB/emulator diagnostics before cleanup. Capture
errors are recorded individually, so disconnected ADB cannot erase host evidence.
The emulator stdout/stderr is retained when launched by this run. Logs may contain
local package/device paths: inspect before sharing. Reports/APKs are ignored under
`build/`; do not commit them.

After a real successful run, exercise failure diagnostics on the same disposable AVD:

```powershell
python scripts/local_e2e.py run --suite saf --inject-failure
if ($LASTEXITCODE -ne 1) { throw "Expected a real controlled assertion failure" }
Get-ChildItem build/local-e2e/reports | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 2
```

This intentionally fails **after authentic authorization** in `acquire`; it is
not a fake host result. Expect one failed functional test, nonzero exit, screenshot,
hierarchy, logs, retained instrumentation output and bounded owned-artifact cleanup.
A BLOCKED infrastructure run is not a successful controlled-failure demonstration.
Neither the success nor deliberate failure has been executed in this cloud.

## Dependency and validation

The sole new dependency is **`androidx.test.uiautomator:uiautomator:2.3.0`** in the
version catalog and `androidTestImplementation`. It is required for cross-process
system picker interaction. The official artifact is Apache-2.0, AndroidX/AOSP
maintained, minSdk 19 (compatible with API 26), and uses the existing JUnit family.
No production dependency or separate test server is introduced. The stable pinned
version avoids an unrelated test-framework upgrade; this is not a comprehensive
vulnerability audit. See [official release notes](https://developer.android.com/jetpack/androidx/releases/test-uiautomator#2.3.0).
No new application-visible strings/resources are added; activity labels continue
to use existing en/ca/es resources. English selectors refer to the test AVD's
system UI, not untranslated application copy.

```bash
python3 -m unittest discover -s scripts/tests -v
bash ./gradlew -I scripts/local-e2e.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --max-workers=4 --console=plain
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
# Ordinary instrumentation APK (opt-in tests excluded):
bash ./gradlew :app:assembleDebugAndroidTest --max-workers=4 --console=plain
```

Host tests cover failed assertions, missing/duplicate/skipped results, process
crashes, timeouts with partial logs, foreign AVD/API rejection, unique reports,
locks, path traversal, partial cleanup and ADB loss. They cannot establish real
picker or OS grant behavior. Existing JVM/regression and API 26/27/36 gates remain
mandatory. Future `coverage` must collect matching JVM/Android execution data and
compiled artifacts with provenance, reject stale/incompatible data, report
per-source lines/branches, and preserve current thresholds; none is claimed by
this first SAF runner.

### Baseline validation evidence

Historical evidence for baseline `3357497` (based on main `c5db5b4`):

- Opt-in debug and instrumentation APK build: **PASS**, with the E2E class checked
  inside the test APK's DEX (not merely a successful assemble task).
- Lint: **PASS**, zero errors (20 existing warnings).
- JVM regression tests and existing contract coverage gate: **PASS**, 146 tests,
  zero failures. This does not measure Android SAF E2E coverage.
- Python infrastructure: **PASS**, 71 tests (39 existing + 32 new host tests).
- Localization: **PASS**, English/Catalan/Spanish resources and format arguments.
- Canonical PDF validation: **PASS**, all 15 checksums, structure and rendering cases.
- Ordinary instrumentation APK: **PASS compilation**, with the opt-in class checked
  absent from DEX. The existing Android suite was **NOT RUN**.
- Gradle-to-ADB stage bridge: **PASS with a fake ADB executable**, verifying explicit
  serial/argument dispatch and configuration-cache compatibility. This was a host
  infrastructure check, not an Android test or authentic grant demonstration.
- Actual runner CLI without provisioning: **BLOCKED**, exit 2, zero Android tests;
  unique XML/HTML/summary and APK/hash/build evidence retained under ignored `build/`.
- Real API 26 unattended PASS and controlled-failure screenshot evidence: **NOT RUN**.
  API 27/36 and physical-device acceptance are unchanged and not established here.

## L01 SAF review and remaining evidence

Source inspection used the exact AOSP **android-8.0.0_r1** tag:
[DocumentsUI layout](https://github.com/aosp-mirror-neo/platform_packages_apps_documentsui/blob/android-8.0.0_r1/res/layout/fragment_pick.xml),
[roots](https://github.com/aosp-mirror-neo/platform_packages_apps_documentsui/blob/android-8.0.0_r1/res/layout/fragment_roots.xml),
[directory row](https://github.com/aosp-mirror-neo/platform_packages_apps_documentsui/blob/android-8.0.0_r1/res/layout/item_doc_list.xml),
[BaseActivity](https://github.com/aosp-mirror-neo/platform_packages_apps_documentsui/blob/android-8.0.0_r1/src/com/android/documentsui/BaseActivity.java), and
[picker result](https://github.com/aosp-mirror-neo/platform_packages_apps_documentsui/blob/android-8.0.0_r1/src/com/android/documentsui/picker/ActionHandler.java).
They confirm `roots_list`, `android:id/title`, `android:id/button1`, the English
Show roots/Show internal storage labels, root selection resetting the backstack,
and ACTION_OPEN_TREE returning a real tree URI. These checks support the selectors;
they do not establish that they have run against the installed emulator image.
The existing bounded waits and coordinate-free authentic picker flow are retained.

After release and a host force-stop, the test now requires all of: absent persisted
READ, production `PERMISSION_REVOKED`, actual OS URI READ denial, actual input read
throwing **SecurityException**, and production listing `PERMISSION_DENIED`. The
host independently verifies the unchanged fixture hash before this stage. Initial
already-authorized access is rejected before changing the debug selection, so
pre-existing authorization cannot silently invalidate the loss test. No existing
grant is released. Successful loss evidence is included in the instrumentation
protocol as `localE2eEvidence`; the raw stage log retains its observed exception.

The [API 26 ContentProvider source](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/content/ContentProvider.java)
shows missing URI authorization throwing `SecurityException` in
`enforceReadPermissionInner`. Its separate AppOps-denial path can produce
`FileNotFoundException`; that result, a provider crash/null stream, or a missing
file is **not accepted** as proof of this test's grant-loss scenario. Actual device
exception behavior remains **NOT RUN** here; keep strict assertions and investigate
an unexpected result with retained diagnostics rather than broadening the catch.

L01 fixes wrapper classification: a single matching started/completed JUnit failure
with stack and completed, non-crashed AndroidJUnitRunner output yields **FAIL / 1**
even if Gradle/ADB exits nonzero. Missing completion, duplicate/missing tests, skips,
crashes, timeouts (even with a partial assertion), or wrapper failure after a passed
test yield **BLOCKED / 2**. Full stdout/stderr, including partial timeout output,
are retained. The nonzero Gradle-wrapper case was also exercised through the real
Gradle Exec task with a fake ADB executable (host-only; no Android execution).
No assertion is retried. Capture files are written before owned
cleanup; cleanup diagnostics use a separate prefix and cannot erase original
failure captures. PNG chunk/header/CRC validation and non-empty hierarchy XML checks reject corrupt
capture output. A functional PASS with failed required captures is overall BLOCKED;
a genuine functional failure remains FAIL with capture problems recorded separately.
Mock tests check file retention, not genuine device screenshots.

L01 cloud validation: Python infrastructure **100 tests PASS** (61 local-runner,
39 existing infrastructure); **146 JVM tests PASS** and contract coverage gate;
debug, opt-in instrumentation, ordinary instrumentation and release builds PASS;
Lint zero errors; localization and all 15 fixture checks PASS. The opt-in class is
present only in its opt-in test APK, absent from ordinary instrumentation and
release APKs. No dependency, CI, policy, threshold or production changes were made.
`doctor` ran on Linux x86_64 and correctly reported **BLOCKED** because acceleration
is unavailable. Native Windows/macOS probes are tested with fixtures/mocks;
PowerShell parsing and POSIX command syntax are checked locally, not claimed as
native OS E2E execution. API 26 SAF PASS, controlled functional-failure screenshots,
and existing Android regression execution remain **NOT RUN**. No API 27/36 or
physical-device acceptance is established by this task.

## L01B validation and remaining owner execution

L01B modifies only the host runner, host regression tests and this document. The
SAF Kotlin tests/selectors, production sources, permissions, contracts, schemas,
dependencies, CI gates and coverage thresholds are unchanged. No ARM64-specific
DocumentsUI selector change is justified without actual UI evidence.

Host regression checks cover the four authorized native host/image combinations,
Rosetta detection, missing/mismatched ARM64 metadata, API 27/36 rejection, unchanged
legacy x86_64 v1 ownership, ARM64 v1 provisioning, foreign identity, conflicting
config/ABI, boot-time API/ABI checks and forced native accelerated emulator launch.
These tests use SDK/device fixtures and mocks; they are not emulator boot tests.
Python infrastructure: **110 PASS** (71 local-runner + 39 existing infrastructure,
10 new tests relative to L01). JVM: **146 PASS**. Debug/opt-in APK, Lint,
localization and all 15 fixture checks PASS; ordinary instrumentation APK compiles
with the opt-in class absent. The opt-in APK contains the class. No build-system
or dependency change is needed for native ARM64 instrumentation.

Real cloud doctor still reports BLOCKED for unavailable KVM on Linux x86_64; this
says nothing about the owner's Hypervisor.Framework readiness. Native macOS boot,
API 26 SAF success, controlled-failure screenshots, and existing Android regression
execution are **NOT RUN** here. Owner acceptance must retain separate reports for
an actual ARM64 SAF PASS and `run --suite saf --inject-failure` returning **1** with
screenshot, hierarchy, logcat, stage protocol, revision and native AVD identity.
A BLOCKED run or mocked host fixture is not that evidence. The same manual command
and report-inspection steps above apply after successful ARM64 provisioning.

## L01C: activity readiness and live missing-UI evidence

A real owner API 26 ARM64 run at `2238edc` failed in `acquire`, looking for the
localized “Choose folder” button before DocumentsUI opened. The activity was
reported launched/resumed. Logcat recorded `pressMenu()` before launch, and a
later host screenshot showed launcher customization; that later screenshot does
**not** establish what was visible at the original assertion. Menu is unnecessary
for this flow and has been removed; the change does not claim that it alone was
the confirmed cause. The disposable AVD must remain unlocked as documented above.

Debug controls now have resource IDs (`saf_accept_select_button`,
`saf_accept_check_button`, `saf_accept_release_button`, `saf_accept_status`). Their
existing en/ca/es strings and grant behavior are unchanged. The opt-in test waits
for the target `SafGrantAcceptanceActivity` to be RESUMED, shown and focused and
for the ID-selected button to be enabled, clickable and have visible bounds
before clicking. It does not send a Menu key, match a localized application label,
or retry failed functional assertions. System DocumentsUI selectors and all real
persisted-grant, force-stop, loss, reacquisition and PDF-hash assertions remain.
The small readiness wait lives only in debug code, with debug-only JVM tests;
release code and release unit-test source sets do not depend on it.

Immediately before a missing-selector/readiness assertion is reported, the running
instrumentation attempts screenshot and hierarchy capture. Missing picker/root
and grant-result UI failures also capture before escaping to JUnit. Each event
gets its own UUID directory in the target debug app's private files:
`files/local-e2e-<run-id>-ui-failures/<event-id>/`. It contains `screenshot.png`,
`hierarchy.xml` and `metadata.json` (selector/readiness description, instrumentation
class/method argument, timestamps, current UI package and capture errors).
Capture errors cannot replace the original assertion failure. No grant is faked
or released for diagnostic purposes.

Before normal cleanup, the serial/ownership-checked host retrieves only those
run-owned files via `run-as` into:

```text
<printed report directory>/instrumentation-failures/<event-id>/metadata.json
<printed report directory>/instrumentation-failures/<event-id>/screenshot.png
<printed report directory>/instrumentation-failures/<event-id>/hierarchy.xml
```

These are **failure-time instrumentation snapshots**. Root-level `screenshot.png`
and `hierarchy.xml` remain **post-test host snapshots**, and `cleanup-*` diagnostics
remain separate. Recollection never overwrites the first event. JSON records the
relative event paths and copy errors. Partial/corrupt raw event payloads are kept
for inspection and are not marked valid; original private snapshots are deleted
only after all three files validate and are copied. If copying fails, the original
run-owned directory remains on the disposable emulator. No unrelated private file,
existing grant or production data is deleted. Host evidence survives cleanup.

L01C validation: **117 Python tests PASS** (7 new retention/isolation regressions)
and **152 JVM tests PASS** (6 new readiness/failure-reporting tests). The JVM tests
exercise missing/disabled controls, activity/window readiness predicates, bounded
waiting, capture-before-report ordering and preservation of the assertion when
capture fails. They are deterministic callback-based tests, not real UI execution.
Debug and opt-in instrumentation build and Lint PASS; localization and 15 PDF
fixtures PASS. Ordinary instrumentation packaging excludes the opt-in test class;
release packaging excludes the debug harness/readiness helper and its IDs. No
production sources, dependencies, policies, coverage gates or CI settings changed.

Real Android execution of this correction is **NOT RUN** in cloud (no usable
emulator acceleration). On the owner's **already provisioned** disposable API 26
ARM64 AVD, update to this branch commit and execute:

```bash
python3 scripts/local_e2e.py doctor && python3 scripts/local_e2e.py run --suite saf
# Only after an actual normal run, exercise the deliberate functional failure:
python3 scripts/local_e2e.py run --suite saf --inject-failure
```

Do not re-provision, wipe or clear the existing application. Keep the exact printed
report directory. A missing-UI failure must retain the live event files separately
from the root post-test captures, or an explicit capture/copy error explaining
what is unavailable. The deliberate after-grant assertion remains a functional
failure with existing host diagnostics; it does not synthesize a missing selector.
An authentic SAF PASS, failure-time screenshots from a real missing-UI event, and
Android regression-suite execution remain pending until actually run.

## L01D: API 26 DocumentsUI root discovery

The owner's authentic macOS Apple Silicon/API 26 ARM64 run at
`a9d1fdf4871cfe18918d4aaf8815759cf0b4d6a5` reached DocumentsUI. After opening
its roots drawer, the test failed to find internal storage, pressed Back and
cancelled the picker. The later missing `More options` assertion was on the debug
activity, whose status was `Folder selection cancelled`. This was a navigation
defect, not evidence that DocumentsUI had never launched.

The correction removes Back entirely from root discovery. It checks the foreground
package before each picker lookup/click/gesture and stops with live evidence if
DocumentsUI disappears. Discovery uses these API 26 properties, reviewed against
[AOSP DocumentsUI android-8.0.0_r1](https://github.com/aosp-mirror-neo/platform_packages_apps_documentsui/tree/android-8.0.0_r1)
and [ExternalStorageProvider](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/packages/ExternalStorageProvider/src/com/android/externalstorage/ExternalStorageProvider.java):

- Roots are under `com.android.documentsui:id/roots_list`; row titles use
  `android:id/title`. The primary volume title is `Settings.Global.DEVICE_NAME`,
  with the provider's localized `root_internal_storage` string only when unset.
  It is not necessarily `Build.MODEL` or the English `Internal storage` label.
  Duplicate matching titles fail safely rather than selecting an arbitrary root.
- The main `toolbar` navigation `ImageButton` has DocumentsUI's localized
  `drawer_open` description. It opens the drawer; it is not a close toggle.
- If primary storage is missing, capture its current screen first. On sliding
  layouts, swipe left **within the resource-identified `drawer_roots` view** and
  wait for `roots_list` to disappear while DocumentsUI stays foreground. This is
  a view-bounded drawer gesture, not an arbitrary coordinate tap or Back press.
  A permanent wide-layout roots pane needs no dismissal. The runner still requires
  the dedicated emulator's `en-US` locale (left-to-right layout).
- The framework overflow `ImageButton` is constrained to the main `toolbar` and
  uses Android's `action_menu_overflow_description` string. Its popup entry is
  `android:id/title` with DocumentsUI's localized `menu_advanced_show` value.
  Enable it through that real menu, reopen roots and discover the primary title
  again. No missing-root fallback selects another provider or injects a grant.

Missing roots generate a checkpoint **before changing screens**. Unavailable
picker overflow/storage menu controls also generate immediate checkpoints, in
addition to bounded selector-timeout evidence. Each capture remains a separate
UUID event in the existing `instrumentation-failures/` report directory; successful
configuration does not by itself turn a diagnostic checkpoint into a JUnit failure.
Metadata now includes the expected picker package, expected primary-root title and
visible root titles, alongside actual foreground package, timestamps and capture
errors. Host post-test snapshots remain separate and cannot overwrite these events.
All original read-only grant, exact provider/tree, canonical PDF, force-stop,
revocation, reacquisition and unrelated-grant assertions remain in place.

Cloud validation of this revision:

- `python -m unittest discover -s scripts/tests -v`: **118 PASS**, including the
  new missing-root/menu checkpoint metadata and retention regression.
- `bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest :app:assembleRelease --max-workers=4 --console=plain`:
  **PASS**; **165 JVM tests**, no failures/errors/skips; Lint has zero errors and
  the same 20 existing warnings. Thirteen new debug-only JVM tests cover discovery,
  drawer/configuration ordering, unexpected picker closure, missing roots/menus,
  capture failures and the device-name/localized-fallback title rule.
- `bash ./gradlew -I scripts/local-e2e.init.gradle :app:assembleDebugAndroidTest --max-workers=4 --console=plain`:
  **PASS**. DEX inspection finds the opt-in class only in the opt-in test APK.
  Rebuilding without the init script excludes it from ordinary instrumentation;
  release APK inspection excludes the debug harness/helpers/IDs and PDF fixtures.
- Localization **PASS** (English/Catalan/Spanish); canonical fixture validation
  **PASS** (15 fixtures); `git diff --check` **PASS**.

These host tests exercise orchestration with callbacks; APK compilation and AOSP
source review do not prove actual drawer gestures or menu rendering. Authentic
SAF E2E and the regular Android device suite are **NOT RUN** in this cloud
environment: no attached device and no usable emulator acceleration. Production
SAF sources, permissions, schemas, dependencies, coverage thresholds and CI gates
are unchanged. No physical device, application data or existing grant was cleared.

Owner verification on the **already provisioned, dedicated API 26 ARM64 AVD**:

```bash
python3 scripts/local_e2e.py doctor
python3 scripts/local_e2e.py run --suite saf
```

Keep the printed report directory and its full instrumentation output. A genuine
PASS requires the existing acquisition, force-stop persistence, revocation and
reacquisition stages to execute successfully on Android. If discovery still fails,
inspect the earliest missing-root checkpoint and subsequent menu/foreground event,
not only the host's post-test screenshot. Do not re-provision or wipe the AVD.

## L01E: bounded ADB readiness recovery and partial setup cleanup

The owner reports that all five authentic SAF stages (including cleanup) passed on
the dedicated macOS Apple Silicon/API 26 ARM64 emulator. Two subsequent deliberate
failure runs stopped before instrumentation: `getprop ro.kernel.qemu` and
`getprop ro.build.version.sdk` returned 255 with empty output. One occurred in
`connect -> settings preparation -> adb_command -> identify`. These are reported
owner results; this cloud revision has not independently executed those runs.

Previously every guarded ADB operation ran a fresh identity check through
`checked()`. A single failed property query immediately raised BLOCKED, including
after a successful earlier identification. Settings originals were recorded before
writes, but cleanup neither distinguished a lost write response from an unsent
write nor verified successful restoration. Functional tests were not the cause.

The runner now has a read-only recovery boundary:

- Identity and explicitly permitted `getprop` / `settings get` readiness reads get
  **at most 3 attempts, 8 seconds total per operation, 250 ms between attempts**.
  The time budget includes command execution; individual command timeouts are
  limited to the remaining budget. After transport loss, repeat the complete AVD
  name, exact serial/owned port, emulator flag, API 26 and host-selected ABI check
  before using the result. Actual identity mismatches stop immediately.
- Only a child emulator newly launched by this invocation gets an initial
  transport-connection window of **at most 60 attempts / 60 seconds total**, with
  **500 ms** backoff. Every attempt still verifies identity as soon as ADB replies;
  a foreign identity never receives that grace period. This accommodates ADB not
  yet being available during cold start. Existing emulators use the shorter limit.
- Exit 255, explicit offline/not-found/closed transport errors and subprocess
  timeouts can trigger read-only recovery. Other errors remain BLOCKED. Persistent
  transport failures exhaust the limit visibly; they are never converted to PASS.
  A terminated emulator child stops recovery, with its PID/exit status recorded.
  For an already-running emulator there is no owned process handle; diagnostics
  explicitly state that process ownership/exit status is unavailable.
- Boot-incomplete polling retains its 240-second limit after initial connection.
  Identity mismatch or exhausted transport recovery stops it. Before any settings
  write or APK installation, require two complete boot/identity samples separated
  by **250 ms**. Already stable emulators incur only that short stability interval.
- Installation, writes, fixture operations and instrumentation methods are never
  automatically replayed after a lost response. A retry of identity BEFORE a
  write is not a retry of the write. No `adb kill-server`, arbitrary-device fallback,
  physical device, emulator wipe, application clear or unrelated emulator operation
  is introduced. Completed JUnit assertion evidence still produces FAIL/exit 1,
  even when the Gradle wrapper exits nonzero. Missing execution remains BLOCKED/2.

Settings are tracked only when a write is about to be dispatched after successful
identity verification; already-correct settings are untouched. Each entry records
its original/target value and whether the write is pending, acknowledged or read
back successfully. A lost response is **uncertain**, not proof that nothing changed.
On cleanup, revalidate the device and read the current value. If it is already the
original, verify that fact without writing. If it equals this run's target, attempt
one restoration (delete a previously absent key), then read back the original.
An unexpected third value is preserved and reported BLOCKED. Failed or unverifiable
restoration is explicitly recorded and does not replace the original setup/test
error. A failed restoration response is not automatically replayed or called PASS.
Other modified settings still receive independent restoration attempts.

Every unique report contains `adb-transport.json` (an empty array if no ADB command
ran), also embedded as `adb_transport` in `summary.json`. Events include exact
serial-targeted argv, attempt, exit status or timeout, elapsed seconds, failed
stdout/stderr, recovery outcome, verified identity after success and owned process
state when available. `setting_changes` and `cleanup` in `summary.json` distinguish
confirmed restoration from uncertainty. Existing host-error, Gradle/instrumentation,
JUnit, HTML, screenshot, hierarchy, logcat and live checkpoint artifacts remain.

Inspect the **exact report directory printed by the run**, for example:

```bash
REPORT='build/local-e2e/reports/<printed-run-directory>'
python3 -m json.tool "$REPORT/adb-transport.json"
python3 -m json.tool "$REPORT/summary.json"
```

Windows PowerShell (set the exact printed path):

```powershell
$report = 'build/local-e2e/reports/<printed-run-directory>'
Get-Content -Raw "$report/adb-transport.json" | ConvertFrom-Json | Format-List
$summary = Get-Content -Raw "$report/summary.json" | ConvertFrom-Json
$summary.setting_changes | Format-List
$summary.cleanup | Format-List
```

L01E cloud validation: **144 Python tests PASS** (26 new transport/setup regressions),
**165 JVM tests PASS**, debug/release and ordinary/opt-in instrumentation APK builds
PASS, Lint zero errors and 20 existing warnings, English/Catalan/Spanish localization
PASS and all 15 canonical fixtures PASS. The ordinary instrumentation APK excludes
the opt-in class; release excludes debug harness/helpers/IDs and PDF fixtures.
Only the host runner, its tests and this documentation changed; no application,
Gradle, dependency, contract, schema, CI gate or coverage threshold changed.

Fault-injection tests cover transport recovery/exhaustion, the total timeout budget,
foreign AVD/serial, wrong API/ABI (also after recovery starts), emulator exit, cold
start, stable boot samples, lost write responses, untouched/partially changed
settings, unverified cleanup, original error retention and no functional JUnit
retries. They simulate host command responses; they do not prove real ADB stability.

Authentic execution of **this revision is NOT RUN** in cloud: no attached emulator
and no usable KVM acceleration. On the owner's already-provisioned dedicated AVD,
without re-provisioning or disturbing `emulator-5554`, run:

```bash
python3 scripts/local_e2e.py doctor
python3 scripts/local_e2e.py run --suite saf
python3 scripts/local_e2e.py run --suite saf --inject-failure
```

The first suite must execute its real Android stages and return 0. The deliberate
scenario must reach its genuine after-grant assertion, retain all evidence, clean
only owned state and return **1/FAIL**, not a fabricated PASS. A setup failure still
returns 2/BLOCKED with zero tests if instrumentation never starts. Collect both
printed report directories, transport events and verified cleanup records for
owner review. Real Mac transport-recovery and controlled-failure evidence remain
pending; host mocks and APK compilation do not establish those results.
