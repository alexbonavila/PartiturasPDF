# M2 Android execution readiness matrix — NOT RUN

2026-10-20. Parent efad21e0f74fdd21e5f56b9c481b68fa8c06a09b. This is preparation, not
execution authorization or device PASS. Catalogue is scripts/j_scenarios.json. All 20 mappings
remain NOT RUN. Nineteen ordinary methods plus the existing useAppContext smoke test form the
proposed first selection: **20 JUnit cases**. This subset is not full mandatory acceptance.

| Scenario | Exact JUnit method | API / fixture ID | Evidence origin | Observable assertions | Existing timeout | Cleanup / approval |
|---|---|---|---|---|---|---|
| J-B-launcher | `com.abdev.partituraspdf.app.integration.AppHostActivityTest.launcherShowsLibraryInsteadOfStarterGreeting` | 26 first / none | production-entry | MainActivity shows usable Library shell and settings action; host destination is LIBRARY | 10 s readiness | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J-B-settings-navigation | `com.abdev.partituraspdf.app.integration.AppHostActivityTest.settingsAndToolbarBackReuseTheSameFeatureOwners` | 26 first / none | production-entry | Settings and toolbar Back retain feature owners and return to Library | 10 s readiness | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J-B-registered-roundtrip | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition` | 26 first / 02 | production-ui-native-test-capabilities | UI registration and favorite; posted score pixels, page 2 and PAGE/WIDTH; UI Back retires route; Settings UI durably changes theme/fit; reopen preserves registration, favorite and page; source and grants unchanged | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J-B-transient | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration` | 26 first / 02 | production-ui-native-test-capabilities | Visible canonical pixels, next page and WIDTH; retired controller on Back; no registration or persisted position; new controller on reopen | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J-B-registered-recreation | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen` | 26 first / 02 | production-ui-native-test-capabilities | Registered presented page flushes; Activity recreation retains native owner; isolated nonexported worker | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J-B-fit-default | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.persistedDefaultFitAndLocalMenuRemainDistinctWhileConfirmedChangesStayInSameNativeSession` | 26 first / 02 | production-ui-native-test-capabilities | Local PAGE override remains distinct from durable WIDTH; confirmed fit change reaches visible frame without session replacement | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J-B-folder-navigation | `com.abdev.partituraspdf.app.integration.LibraryPersistenceIntegrationTest.acceptedRootListsNavigatesRegistersAndPersistsFavoriteAcrossControllerRestart` | 26 first / synthetic | simulated-provider-controller | Controlled root callback lists/enters/backs, registers, favorites and survives controller restart with READ-only take request | 10 s state | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J-B-picker-binding | `com.abdev.partituraspdf.app.integration.RootPickerBindingTest.actualRegistryForwardsOriginalIntentUriAndFlagsWithoutAuthorizationOnLaunch` | 26 first / synthetic | simulated-activity-result | ActivityResultRegistry forwards original tree result and flags; launch alone never authorizes | 10 s exercise | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J-B-read-only-grant-contract | `com.abdev.partituraspdf.app.integration.AppSafLifecycleIntegrationTest.hostPersistsOnlyReturnedReadCapabilityAndRequiresReportedConfirmation` | 26 first / synthetic | simulated-grant-platform | READ-only take requested; reported persisted grant confirmation required; metadata preserved | 10 s metadata | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J-B-genuine-saf | `com.abdev.partituraspdf.storage.saf.SafLocalE2eTest.acquire` | 26 first / 01 | genuine-documentsui-opt-in | Real DocumentsUI selection, persisted READ-only capability, canonical bytes and unrelated grant preservation; debug acceptance Activity, not production MainActivity picker | Separate runner bounds; excluded | Separate SAF mutation approval; NOT selected |
| J06-registered-access | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.registeredAccessFailureMatrixPreservesDurableStateAndOnlyRetriesRecoverableErrors` | 26 first / 02 | production-ui-native-test-capabilities | Exact denied/revoked/unavailable/IO/deleted errors; no native open on failure; identity/favorite/confirmed page/settings retained; UI Retry renders only recoverable failures; no real grant change | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J06-registered-malformed | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.registeredMalformedPdfFailurePreservesFavoritePositionAndPreferences` | 26 first / 15 | production-ui-native-test-capabilities | Native invalid PDF has no successful frame or Retry; safe Back preserves real registration/favorite/position/preferences and source bytes | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J06-registered-protected | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.registeredProtectedPdfFailurePreservesFavoritePositionAndPreferences` | 26 first / 14 | production-ui-native-test-capabilities | Exact PASSWORD_PROTECTED or DOCUMENT_FORMAT; no frame or Retry; safe Back preserves real metadata/preferences and bytes | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J06-checkpoint-native | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.injectedCheckpointFailurePreservesRealEarlierPositionAndSafeReopenAfterEmptyRouteVerification` | 26 first / 02 | production-ui-native-test-capabilities | Real native presentation with failed checkpoint retains earlier durable page; empty route verification permits safe reopen; only expected diagnostics cleared | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J10-settings-reinitialize | `com.abdev.partituraspdf.app.integration.AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization` | 26 first / synthetic | real-isolated-disk-adapters | All six theme/fit combinations survive joined DataStore and Room graph reinitialization; registration/favorite unchanged | 60 s matrix; 10 s observation | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J10-root-authorization | `com.abdev.partituraspdf.data.foundation.FoundationPersistenceTest.rootSelectionReplacementRestartRevocationAndRecovery` | 26 first / synthetic | real-private-file-with-simulated-grants | Remembered root reloads; simulated permission loss fails restore without erasing stored replacement; renewed permission restores it | No wall limit; use external watchdog | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J10-registered-restoration | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.syntheticSavedBundleReopensDiskRegisteredScoreAtDurablyPresentedPageWithFreshNativeSession` | 26 first / 02 | production-ui-native-with-synthetic-bundle | Presented page durably flushes; reconstructed host restores same registered identity/page/favorite using fresh native session | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J10-transient-restoration | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.syntheticTransientSnapshotCannotAutomaticallyReopenOrCreateRegisteredPosition` | 26 first / 02 | production-ui-native-with-synthetic-bundle | Transient snapshot is Library and reconstructed host has no native opening or registered position | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J10-settings-reconstruction | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.syntheticNewHostReloadsActualDataStoreThemeAndDefaultFitWithFreshReader` | 26 first / 02 | production-ui-native-with-synthetic-bundle | Fresh host reads actual DARK/WIDTH DataStore values and creates new visible Reader | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |
| J10-background | `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.genuineActivityBackgroundFlushesAndResumeReopensWithoutNewUserIntent` | 26 first / 02 | production-ui-native-test-capabilities | Actual Activity stop/start suspends/closes route and resumes new native session without new user intent | 15 s state; 20 s visibility/PixelCopy | Dispose owned host/route/jobs/DB and UUID files; no system grant mutation; explicit install/runtime approval required |

Canonical IDs/hashes resolve to the immutable manifest and instrumentation assets, never release
assets. Synthetic/none has no canonical file. Native cases require a working isolated service,
readable copies, adequate render memory and an unlocked foreground screen. Presented frames,
not engine completion, are required. MainActivity smoke/settings initialize the real graph:
use a disposable app installation with no user data. Other capabilities/stores are injected or
isolated as stated. Cleanup detaches surfaces, awaits owned work and closes stores before
removing only test-owned files. Test-owned selected-root clear is not app-data clearing or OS
grant revocation. Activity stop/start/recreate is not process death or shell force-stop.

## Tooling and static readiness

The existing AndroidJUnitRunner can select exact comma-separated Class#method entries. Reuse
local_e2e.execute (bounded argv execution), local_test_evidence.parse_android (exact inventory,
no missing/duplicate/skipped tests) and write_junit. No new runner, CI task or automatic device
path is introduced. The Phase J profile remains host-only and never promotes Android NOT RUN.

Do NOT run local_e2e.py --suite android/full or run_pdfium_ci.sh for this narrow first run.
The former runs unfiltered instrumentation including 14 controlled diagnostics and explicit
worker-loss tests; the latter schedules diagnostics as well. Do not select the entire
ReaderHostNativeIntegrationTest class: its realWorkerLoss method needs separate approval.

Cloud files declare API26 google_apis/x86_64 and 2G RAM; installed image files exist. However,
runner ownership manifest is absent, its reserved AVD identity does not match, /dev/kvm is
absent, and live serial/boot/acceleration/screen are unverified. Current Android readiness is
**BLOCKED**. No AVD was adopted or operated. The owner's native Mac/Windows setup must be checked
there, including native ABI compatibility (macOS ARM64 uses API26 arm64-v8a), not certified here.

## Proposed first API26 commands — ONLY AFTER SEPARATE OWNER AUTHORIZATION

Preconditions: owner explicitly confirms disposable AVD ownership and serial, install permission,
selected methods and ordinary lifecycle operations. Verify ownership using existing load_device
manifest/config/index checks, or separate owner attestation; never adopt an unknown AVD. Owner
must arrange an already booted unlocked emulator separately. No provisioning/reset is authorized.
This document does not authorize running any command below on a device.

From repository root with the configured toolchain, macOS/Linux Bash:

```bash
set -euo pipefail
serial='emulator-<owner-confirmed-port>'
expected_avd='<owner-confirmed-disposable-avd-name>'
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || exit 2
actual_avd=$(adb -s "$serial" emu avd name | tr -d '\r' | sed '/^OK$/d')
[[ "$actual_avd" == "$expected_avd" ]] || exit 2
[[ $(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]] || exit 2
[[ $(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r') == 26 ]] || exit 2
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=4 --console=plain
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$serial" shell pm list instrumentation
export ANDROID_SERIAL="$serial"
```

The following one-off owner command uses existing executor/parser helpers. It checks live
identity again and fails safely for incomplete output/timeouts; it is not run during C1.
It captures failures without turning a nonzero ADB exit into a false PASS. No automatic retry,
cleanup, force-stop or destructive command is issued. The instrumentation framework may restart
the test app as part of launch: disposable app ownership is mandatory.

```python
# Save this block to a temporary LOCAL file, then run python3 <that-file> only after approval.
import sys, json, uuid, hashlib, os, re
from pathlib import Path
from datetime import datetime, timezone
sys.path.insert(0, 'scripts')
import local_e2e as runner
import local_test_evidence as ev
serial = os.environ['ANDROID_SERIAL']
assert re.fullmatch(r'emulator-[0-9]+', serial), 'Disposable emulator serial required'
run = uuid.uuid4().hex
out = Path('build/j-m2-authorized') / run
out.mkdir(parents=True, exist_ok=False)
try:
    identity = {key: runner.checked(['adb', '-s', serial, 'shell', 'getprop', prop])
        for key, prop in [('qemu','ro.kernel.qemu'), ('api','ro.build.version.sdk'), ('abi','ro.product.cpu.abi')]}
    identity['avd'] = runner.checked(['adb','-s',serial,'emu','avd','name'])
    assert identity['qemu']=='1' and identity['api']=='26'
    # Compare this identity with the owner-confirmed values before proceeding.
    assert identity['avd'].replace('\r','').splitlines()[0] == os.environ['M2_EXPECTED_AVD']
    component = ev.APP + '.test/' + ev.NORMAL_RUNNER
    installed = runner.checked(['adb','-s',serial,'shell','pm','list','instrumentation'])
    assert re.findall(r'instrumentation:(\S+) \(target='+re.escape(ev.APP)+r'\)',installed)==[component]
    cat = ev.j_catalogue(Path('scripts/j_scenarios.json'))
    expected = {s['test_method'] for s in cat['scenarios'] if s.get('execution_group')=='m2-first-safe'} | {ev.SMOKE}
    assert expected <= ev.android_inventory(Path('app/src/androidTest'))
    selected = ','.join(c.rsplit('.',1)[0]+'#'+c.rsplit('.',1)[1] for c in sorted(expected))
    (out/'expected.json').write_text(json.dumps(sorted(expected),indent=2))
    source = ev.j_capture(Path.cwd(),run) # host identity probe, not Android execution evidence
    command = ['adb','-s',serial,'shell','am','instrument','-w','-r','-e','class',selected,component]
    metadata = dict(run=run,pre_execution_host_source=source,device=identity,command=command,
                    started=datetime.now(timezone.utc).isoformat(),status='BLOCKED')
    metadata['apk_sha256'] = {p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in
        ('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')}
except (runner.Blocked,ValueError,OSError,AssertionError,KeyError) as error:
    (out/'execution.json').write_text(json.dumps(dict(status='BLOCKED',detail=str(error)),indent=2))
    print('BLOCKED',out,str(error))
    raise SystemExit(2)
cases=[]; blocker=None; code=2
try:
    result=runner.execute(command,timeout=1200)
    output=result.stdout+'\n'+result.stderr
    (out/'instrumentation.txt').write_text(output)
    metadata['exit']=result.returncode
    cases=ev.parse_android(output,expected)
    current=ev.j_capture(Path.cwd(),run)
    if any(source[k]!=current[k] for k in ('revision','tree','source_digest','build','branch')):
        raise ValueError('Source changed during execution')
    if any(c['status']=='FAIL' for c in cases):
        metadata['status']='FAIL'; code=1
    elif result.returncode:
        raise ValueError('Complete JUnit output but nonzero ADB exit')
    else:
        metadata['status']='PASS'; code=0
except ev.IncompleteInstrumentation as error:
    cases=error.cases; blocker=str(error)
except (runner.Blocked,ValueError,OSError) as error:
    blocker=str(error)
    (out/'infrastructure.txt').write_text(blocker) # includes partial timeout output
finally:
    metadata['finished']=datetime.now(timezone.utc).isoformat()
    metadata['blocker']=blocker
    ev.write_junit(out/'junit.xml','m2-authorized-subset',cases,blocker)
    (out/'execution.json').write_text(json.dumps(metadata,indent=2))
    print(metadata['status'],out)
raise SystemExit(code)
```

Before that Python command, also set `export M2_EXPECTED_AVD="$expected_avd"`. Do not save the
temporary command file as an untracked source file in the repository; use a private temporary
location. Preserve identity output, exact source snapshot/revision and APKs, start/end times,
raw output and JUnit. Capture screenshots/logcat on failure before manual cleanup using the
existing documented diagnostics; do not upload raw private evidence automatically. Outputs are
local and unredacted. M2-C2 must reconcile actual execution with Phase J explicitly; this command
does not relabel the host profile's NOT RUN projections. Missing prerequisites are BLOCKED.

PowerShell: use gradlew.bat, the same Python block in a temporary local file, and `python`;
set $env:ANDROID_SERIAL and $env:M2_EXPECTED_AVD only after equivalent explicit ownership/API/name
checks. Never pass an unquoted #method token through a shell. Native Windows/macOS execution
of these proposed instructions remains NOT RUN; Python syntax and source inventory are host-checked.

## Excluded until separately approved

Genuine DocumentsUI grant acquisition/release/revocation, external providers, explicit worker-loss
or crash induction, force-stop, backup/restore, uninstall/data clearing, actual OS process death,
controlled native diagnostics and physical-device acceptance. API27/36 are separate compatibility
runs. No M2-C2 authorization is implied. Historical CI/M2 evidence is not evidence for this run.
