# C06 Policy B real backup/restore acceptance procedure

Status: test-only procedure prepared for owner review. Baseline:
`8927e2868cf9b6ec8bc93a12948c027440199db2`, branch feat/data-persistence-c, PR #10.
No backup, uninstall, restore or D2D has been executed by this cloud task. Mandatory CI on
the baseline is owner-reported PASS; it does not prove runtime transport acceptance.
This procedure does not change production Kotlin, schema, manifest, backup policy or thresholds.

## Scope and test mechanism

Policy B permits only `file:datastore/app_preferences.preferences_pb` in legacy full-backup-content,
cloud-backup and device-transfer. Room and every other private file/domain are omitted. The whole
protobuf is selected, not individual keys. Current owned settings contain no intentionally
sensitive values; never add document references, tokens or credentials to that file.

An explicitly built acceptance test APK declares the **manual-only Instrumentation**,
`com.abdev.partituraspdf.data.backup.BackupPolicyAcceptanceRunner`. It is not the configured
AndroidJUnitRunner, contains no @Test methods and is not called by the ordinary 205-test suite.
The test provider is retained. The normal build retains AndroidJUnitRunner; the acceptance APK
selects the custom runner only when scripts/backup-acceptance.init.gradle is explicitly supplied.
AGP rewrites test-manifest instrumentation, so no extra entry is added to that manifest.
No production Gradle/CI setting changes. No acceptance fixture is shipped in
the application APK. Explicit `am instrument` selection is required.

The seed action uses applicationPreferencesStore and DataStorePreferencesRepository to durably
write DARK / CATALAN / WIDTH (serialized dark / ca / width). It opens MetadataDatabase with the
real default name document-metadata.db, registers a synthetic UUID-bound logical document and
opaque synthetic provider location through RoomDocumentRegistry, then stores favorite=true and
an Unknown page-7 reading hint through RoomDocumentMetadataRepository. It also writes
files/c06-backup-unrelated.txt. There are no real PDF bytes, SAF grants or provider credentials.
The seed refuses an installation with an existing preferences file, any database or the marker file.

Room is closed after seeding and before backup. The report records actual SQLite files while
open and after close; Room may checkpoint/remove WAL/SHM on close. Do not claim a particular
sidecar was backed up when it did not exist. Exclusion is checked for every restored database
file before opening Room, including any main/WAL/SHM/journal that might be restored. Verification
then opens a newly created empty Room database and proves the prior registration and metadata
are absent. It verifies all three restored preferences exactly and the unrelated file is absent.
Reading preferences must not substitute defaults for the expected non-default values.

The runner requires emulator hardware ranchu/goldfish, explicit confirmation argument and a
matching host-created UUID consent marker in cache/c06-backup-consent. Cache is not backed up.
The host recreates the marker after reinstall/transfer; an old archive cannot supply consent.
The production DataStore delegate owns its process scope; acknowledged writes complete before
the host force-stops the test application. No second store owns the same file concurrently.

## Prerequisites and safety boundary

- Windows PowerShell 5.1 or PowerShell 7, Git, the repository-supported JDK25/SDK37/Gradle wrapper,
  Android platform-tools/adb on PATH, and matching debug app/test APKs from this checkout.
- A deliberately **disposable Android Studio AVD**, API26 or newer, x86/x86_64 ranchu/goldfish,
  with no user's installed app/data, account, real documents or grants. Prefer a fresh named
  AVD such as C06-Backup-API26 and a second API31+ AVD to exercise both configuration formats.
- An available Android **local test transport**, not the owner's Google/cloud-account transport.
  The script accepts only android/com.android.internal.backup.LocalTransport,
  com.android.localtransport/.LocalTransport or com.android.localtransport/.LocalTransportService,
  and only if the exact component is returned by bmgr list transports.
- Backup Manager and a recordable secure backup_auto_restore setting (0 or 1). If absent,
  unsupported or ambiguous, stop BLOCKED; do not guess or replace the transport with real cloud.
- Enough space and supported backupnow/list sets/restore commands. Local transport restore-set
  discovery must yield exactly one set; otherwise stop rather than selecting arbitrary data.

The script never selects the first adb device automatically. It requires an emulator-NNNN
serial, verifies qemu=1 and hardware, shows model/API/fingerprint/APK hashes, and demands the
typed phrase `ERASE ONLY <serial> com.abdev.partituraspdf` before any destructive operation.
This is consent for uninstalling/clearing only this test app and its test APK and wiping only
its backup in the selected local transport. No physical device or existing user data is permitted.
Rechecks precede destructive phases. The marker and emulator checks also gate direct fixture use.
These are safeguards against accidental execution, not defenses against a deliberately spoofed
emulator identity or a privileged operator bypassing the procedure.

## Windows execution: local cloud-style transport

From the repository root, first verify branch/HEAD and build without running connected tests:

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --max-workers=4 --console=plain
adb devices -l
# Build a separate manual-runner test APK; copy it outside build outputs before returning to normal builds.
$acceptanceApks = Join-Path $env:TEMP ('C06-AcceptanceApks-' + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $acceptanceApks
.\gradlew.bat -I scripts/backup-acceptance.init.gradle :app:assembleDebugAndroidTest --max-workers=4 --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Acceptance APK build failed' }
Copy-Item '.\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk' (Join-Path $acceptanceApks 'acceptance-test.apk')
Copy-Item '.\app\build\outputs\apk\debug\app-debug.apk' (Join-Path $acceptanceApks 'app-debug.apk')
# Restore default-runner build outputs so ordinary connected tests still use AndroidJUnitRunner.
.\gradlew.bat :app:assembleDebugAndroidTest --max-workers=4 --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Default test APK rebuild failed' }
```

Start the named disposable AVD through Android Studio Device Manager. Record its name and match
its window to an explicit serial; never infer disposability from the serial alone. Example:

```powershell
$serial = 'emulator-5554' # Replace with the deliberately selected disposable AVD.
adb -s $serial emu avd name
adb -s $serial shell getprop ro.kernel.qemu
adb -s $serial shell getprop ro.hardware
adb -s $serial shell getprop ro.build.version.sdk
adb -s $serial shell bmgr list transports
```

Choose the exact listed local component. Run with a new report directory outside Git:

```powershell
$transport = 'com.android.localtransport/.LocalTransport' # Exact available component; no fallback.
$report = Join-Path $env:TEMP ('C06-Backup-' + [guid]::NewGuid().ToString())
.\scripts\Invoke-BackupPolicyAcceptance.ps1 `
  -Serial $serial `
  -AppApk (Join-Path $acceptanceApks 'app-debug.apk') `
  -TestApk (Join-Path $acceptanceApks 'acceptance-test.apk') `
  -Transport $transport `
  -ReportDirectory $report
```

Use an approved local script-execution policy; do not disable machine-wide security settings.
The script collects identities before asking for the exact typed destructive confirmation.
It records/restores the original selected transport, Backup Manager enabled state and auto-restore
setting. Restore-at-install is temporarily disabled to make manual bmgr restore the measured
operation. In order it:

1. Selects the available local transport, wipes only this package's test backup, uninstalls any
   test/app installation on the confirmed disposable AVD, and installs both APKs freshly.
2. Creates consent and invokes seed; requires real instrumentation success, not just adb exit=0.
3. Force-stops the test app, runs `bmgr backupnow com.abdev.partituraspdf` and requires both package
   and overall Success. Saves output and the actual `bmgr list sets` token; no hardcoded token.
4. Uninstalls/reinstalls both APKs. Confirms the restored preference/fixture files are absent
   before manual restore. Runs `bmgr restore <token> com.abdev.partituraspdf`, requiring
   restoreFinished: 0. A missing/failed transport or incompatible command output is FAIL/BLOCKED.
5. Force-stops the app, creates fresh consent and invokes verify. Only successful assertions
   yield PASS for **local cloud-style backup/restore**, never D2D.
6. Restores only changed emulator backup settings. Leaves the test app/evidence for inspection;
   delete the disposable AVD manually after saving reports. Do not reinterpret cleanup failure
   or thrown PowerShell errors as an overall successful procedure.

Reports: identity.json (API/model/fingerprint/serial, UUID, APK SHA256), commands.log (commands,
exit codes and output), seed.txt, backup.txt, restore-sets.txt, restore.txt, verify.txt and result.txt.
Archive them with exact Git SHA/dirty diff, AVD name, OS/tool versions and the owner's signature.
Only synthetic identifiers are included; do not attach real user files or private device evidence.
Transport state failures before consent may have only identity/command logs and no result.txt;
absence of a PASS report never counts as execution evidence.

## Separate D2D verification: only when genuinely supported

**NOT RUN / BLOCKED by default.** LocalTransport backup/restore, an Android API36 test run,
or changing a local-transport parameter to report a device-transfer flag is not proof of an
actual device-to-device migration. There is no portable adb/bmgr command that creates a real
source-to-destination migration link for every AVD/image. Do not fabricate a D2D command or
fall back to copying app files/archives.

Use two empty disposable AVDs only if the selected system images provide a supported migration
wizard/transport and genuine emulator-to-emulator pairing. Record vendor/image/tool documentation,
source/destination serials, the actual D2D transport and evidence that DEVICE_TRANSFER (not CLOUD)
was used. If pairing, debug APK transfer or transport verification is unavailable, stop BLOCKED.
Do not use physical devices or personal accounts to get past that limitation.

Before uninstall, clear, factory-reset or wipe on either AVD, show both identities and separately
require typed owner confirmations naming each serial/package. Source uses a fresh installation
and the same seed fixture; destination starts without this app's data. Example explicit fixture
invocation, **only after** that confirmation and installation on the chosen disposable source:

```powershell
$source = 'emulator-5554'      # Confirmed disposable source.
$destination = 'emulator-5556' # Confirmed disposable destination; different serial.
$token = [guid]::NewGuid().ToString()
adb -s $source shell run-as com.abdev.partituraspdf mkdir -p cache
adb -s $source shell run-as com.abdev.partituraspdf sh -c "'echo $token > cache/c06-backup-consent'"
adb -s $source shell am instrument -w -e action seed `
  -e confirm DISPOSABLE_EMULATOR_ONLY -e token $token `
  com.abdev.partituraspdf.test/com.abdev.partituraspdf.data.backup.BackupPolicyAcceptanceRunner
```

Capture PASS seed output, then close/force-stop the source test application. Perform only the
documented real migration UI/tool sequence for the selected image. This stage is transport-specific,
not automated by the local-cloud script. Keep migration logs proving the tested app participated.
On the destination, ensure the identical app APK is installed/restored according to that supported
migration flow; install only the matching test APK with `adb -s $destination install -t <TestApk>`
after transfer if absent. Do not uninstall/clear the destination app after migration. Do not seed
it, manually copy the protobuf or run bmgr restore from the cloud-style local archive.

Create the destination's cache consent marker with the **same token** and invoke action verify
using the command above with destination/action substituted. Require policy_result=PASS and
INSTRUMENTATION_CODE: -1, plus the same five assertions: exact preferences, no restored database
files/sidecars before Room open, prior registration absent, metadata absent, unrelated file absent.
Capture pre-transfer destination state, D2D transport/connection logs and verification separately
from cloud evidence. If no actual supported migration occurred, report NOT RUN, even if verify
passes against manually assembled files. Cross-platform/non-framework/OEM privileged transfer
behavior remains a separate boundary described in the C06 audit.

## Validation and acceptance boundary

Cloud preparation results (2026-10-14): debug app and instrumentation APK compilation, lint,
the existing JVM task/XML (128 cases), 39 Python infrastructure tests and compiled APK Policy B
verification PASS. Lint retains 0 errors/20 existing warnings. The new runner is selected only
in the explicitly built acceptance test APK; ordinary builds retain AndroidJUnitRunner.
Both packaged runner names are inspected independently; source inventory remains
205 JUnit Android tests. PowerShell 7.5.3 (official release SHA256 verified) parsed the script
and exercised four host refusal cases: physical serial, non-local/cloud transport, missing
local transport and denied typed consent. The latter used a mock identity/transport command function and confirmed that
no install/uninstall/wipe/restore/instrument command ran. These are safety tests, not synthetic
backup PASS. No native Windows/PowerShell5.1 execution or complete transport run is claimed.

Local compilation/lint/JVM/static policy checks demonstrate that the fixture can be built and
the production policy remains unchanged. They are not backup/restore execution. No test transport,
emulator, uninstall/restore or physical-device operation was run in the cloud task.
Never run connectedDebugAndroidTest with the acceptance init script: this APK is only for
the explicitly guarded fixture command. Normal builds/CI never load that script.
The helper has no JUnit tests, so the baseline 205 Android tests and existing assertions remain.
Owner must record actual executed results separately for API26-30 legacy and API31+ newer rules,
and D2D if genuinely supported. Baseline CI PASS does not close these acceptance criteria.
Any CLI variant, denied run-as, missing transport, failed package backup, absent restore token,
unexpected files/preferences or cleanup/settings error is a failure/blocker, never a waived test.
Do not commit, push, merge or expand product functionality as part of this procedure.
