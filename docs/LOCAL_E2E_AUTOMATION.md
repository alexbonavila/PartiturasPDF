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

## Prerequisites and one-time provisioning

Use Python **3.10+**, the repository-supported JDK/Gradle toolchain, Android SDK
platform/build tools required by the project, SDK command-line tools (`latest`),
platform-tools, emulator, and hardware virtualization. Set `ANDROID_HOME` (or
`ANDROID_SDK_ROOT`); ensure Gradle uses the same SDK. On Windows use PowerShell,
`python` instead of `python3`, and `.bat` for SDK tools. Run from the repository
root. Close concurrent Gradle builds and keep the working tree stable during a run.

Install the API 26 image and accept its licenses yourself:

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "emulator" "system-images;android-26;google_apis;x86_64"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
python scripts/local_e2e.py provision --port 5580
python scripts/local_e2e.py run --suite saf
$LASTEXITCODE
```

Linux/macOS (an x86_64 host/image with working acceleration is required):

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platform-tools' 'emulator' 'system-images;android-26;google_apis;x86_64'
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
python3 scripts/local_e2e.py provision --port 5580
python3 scripts/local_e2e.py run --suite saf
```

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
   real `SecurityException` on read, then selects the folder again through the
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

On failure the runner independently attempts screenshot, UI hierarchy, recent
logcat, package/activity state and ADB/emulator diagnostics before cleanup. Capture
errors are recorded individually, so disconnected ADB cannot erase host evidence.
The emulator stdout/stderr is retained when launched by this run. Logs may contain
local package/device paths: inspect before sharing. Reports/APKs are ignored under
`build/`; do not commit them.

After a real successful run, exercise failure diagnostics on the same disposable AVD:

```powershell
python scripts/local_e2e.py run --suite saf --inject-failure
if ($LASTEXITCODE -ne 1) { throw "Expected a real controlled assertion failure" }
Get-ChildItem build/local-e2e/reports | Sort-Object Name -Descending | Select-Object -First 2
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

### Cloud validation evidence

On the implementation branch based on main `c5db5b4`:

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
