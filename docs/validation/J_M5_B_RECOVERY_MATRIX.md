# M5-B recovery evidence selection

2026-11-02. Baseline `3a6fb21960e5ff5ccd3b8410b37e245e42df0e62`, branch
`test/mvp-stabilization-j`. Documentation/static selection only. All eight
current-revision executions: **NOT RUN**. No device or installation authorization
is granted by this document.

## Exact matrix

All methods are existing Android JUnit methods, not JVM tests. Native host methods
use production controllers/Reader/native isolation with injected local capabilities
and test-owned storage; they do not prove genuine external SAF authorization.

| Exact JUnit identity | Recovery requirement / capability | Historical evidence | Preservation / limitations | Current revision |
| --- | --- | --- | --- | --- |
| `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen` | Actual Activity recreation retains host/native session; page 2 flushes durably. | M2 and M3-A independently reviewed PASS | Registered identity and committed page; replacement presentation consumer. No OS death. | NOT RUN |
| `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.genuineActivityBackgroundFlushesAndResumeReopensWithoutNewUserIntent` | Actual ActivityScenario CREATED/RESUMED; same Reader entry, fresh native session. | M2 independently reviewed PASS | Transient logical intent and safe route closure. Does not assert all registered metadata. | NOT RUN |
| `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.backgroundWaitsForDelayedRealNativeRenderOutputAndDiscardsItsBitmap` | Actual native render with test-controlled noncancellable output delivery; real Activity background/resume. | Owner-reported M3 aggregate only; no individually rechecked artifact here | Obsolete bitmap recycled, route closes before resumed visibility. Delay is injected, not a native hang. | NOT RUN |
| `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.syntheticSavedBundleReopensDiskRegisteredScoreAtDurablyPresentedPageWithFreshNativeSession` | Synthetic saved Bundle; fresh host/controllers and reopened real Room; native Reader. | M2 independently reviewed PASS | Same registered identity, committed page and favorite; fresh controller/session. Not OS process death. | NOT RUN |
| `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.syntheticTransientSnapshotCannotAutomaticallyReopenOrCreateRegisteredPosition` | Synthetic Bundle and new host over test-owned disk storage. | M2 independently reviewed PASS | Transient snapshot returns Library, no native reopening or registered position. Not OS death. | NOT RUN |
| `com.abdev.partituraspdf.app.integration.AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization` | Real isolated Room/DataStore; joined replacement owners; six SYSTEM/LIGHT/DARK × PAGE/WIDTH pairs. | M2 and M3-A independently reviewed PASS | Preferences, registration and favorite survive reinitialization. Not production process restart. | NOT RUN |
| `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.injectedCheckpointFailurePreservesRealEarlierPositionAndSafeReopenAfterEmptyRouteVerification` | Real native presentation with injected PRIVATE_STORAGE_UNAVAILABLE checkpoint failure. | M2 independently reviewed PASS | Earlier committed page survives; authoritative empty route permits safe reopening. Not actual disk failure. | NOT RUN |
| `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.injectedProviderUnavailableIsContextualAndRetryRevalidatesBeforeRealNativeOpen` | Injected provider error; real UI Retry followed by native opening. | Owner-reported M3 aggregate only; no individually rechecked artifact here | Exact contextual error, zero opens before recovery, one successful native open after Retry; safe Back. Not external SAF outage. | NOT RUN |

M2 attribution: [independently reviewed 20/20 API26](J_M2_C2B_ANDROID_API26_EVIDENCE.md),
execution `83e0094cea8a20a90635f82b97b84610a11d2a2e`.
M3-A attribution: [independently reviewed compatibility](J_M3_A2_ANDROID_COMPATIBILITY_EVIDENCE.md),
execution `a9905d497979520cfabf59e038b51f0ed453a8a3`.
[Final M3 aggregate](J_M3_CLOSEOUT.md) is owner-reported 631 JVM/431 Android PASS;
its archive is unavailable here, so it is not independently verified per-method
execution evidence for the two aggregate-only rows. No historical result is relabeled
as current-revision PASS. Six preference combinations still count as one JUnit method.

## Future execution checkpoints — separate exact-device approval

Owner must approve the exact disposable, test-owned API26 emulator, installation and
these eight methods with their ordinary lifecycle operations. No unknown AVD may be
adopted. Keep the original test-owned configuration unchanged. Close competing
build/device campaigns; use the existing shared runner-home lock. The selected
checkpoint/provider errors are injected contracts, not actual grant revocation.

From the repository root on macOS, after that authorization and publication of the
selected source, choose the approved revision explicitly:

```bash
export ANDROID_SERIAL=emulator-5580  # illustrative; must match verified ownership
export M5B_EXPECTED_SHA='<owner-approved-full-execution-commit>'
python3 scripts/j_m4_benchmark.py doctor --home build/local-e2e --serial "$ANDROID_SERIAL"
# Build ordinary APKs, without either opt-in M4/M5 init script:
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
# Install only after confirmation that this installation/data is test-owned:
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Do not uninstall, clear app data, force-stop unrelated packages, change grants or run
full classes. Installation failure or hash mismatch is BLOCKED; do not automatically
retry. Instrumentation may restart the target app as part of launch. These tests use
UUID-owned fixtures/storage and their existing joined teardown; test source bytes and
existing preservation assertions must remain intact.

The following one-off owner command uses existing executor, ownership, parser and
JUnit writer APIs. It is documentation, not a new maintained runner. It explicitly
selects eight methods with AndroidJUnitRunner's comma-separated `Class#method`
argument. **No smoke, class-wide discovery, worker-loss or diagnostic method is added.**
The 1200-second watchdog bounds the campaign; it is not a timeout increase in any test.
No automatic functional retry, device cleanup or configuration change is issued.

```bash
python3 - <<'PYCODE'
import os, sys, json, uuid
from pathlib import Path
sys.path.insert(0, 'scripts')
import local_e2e as runner
import local_test_evidence as ev
import j_m4_benchmark as shared
expected = {
    'com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen',
    'com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.genuineActivityBackgroundFlushesAndResumeReopensWithoutNewUserIntent',
    'com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.backgroundWaitsForDelayedRealNativeRenderOutputAndDiscardsItsBitmap',
    'com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.syntheticSavedBundleReopensDiskRegisteredScoreAtDurablyPresentedPageWithFreshNativeSession',
    'com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.syntheticTransientSnapshotCannotAutomaticallyReopenOrCreateRegisteredPosition',
    'com.abdev.partituraspdf.app.integration.AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization',
    'com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.injectedCheckpointFailurePreservesRealEarlierPositionAndSafeReopenAfterEmptyRouteVerification',
    'com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.injectedProviderUnavailableIsContextualAndRetryRevalidatesBeforeRealNativeOpen',
}
serial = os.environ['ANDROID_SERIAL']
revision = os.environ['M5B_EXPECTED_SHA']
home = Path('build/local-e2e')
sdk = runner.discover_sdk(None)
run_id = uuid.uuid4().hex
parent = ev.j_path(Path.cwd(), 'build/j-m5-b/reports')
parent.mkdir(parents=True, exist_ok=True)
out = parent / run_id
out.mkdir()
(out / 'execution.log').touch()
summary = dict(status='BLOCKED', complete=False, run=run_id, expected=sorted(expected))
cases = []
probe = None
try:
    with runner.exclusive(home):
        summary['environment'] = shared.preflight(home, sdk, serial)
        assert runner.checked(['git','rev-parse','HEAD']) == revision
        assert not runner.checked(['git','status','--porcelain'])
        assert len(expected) == 8 and expected <= ev.android_inventory(Path('app/src/androidTest'))
        summary['source'] = ev.j_capture(Path.cwd(), run_id)
        probe = shared.Probe(home, sdk, out/'execution.log')
        probe.identify(boot=True)
        apks = {ev.APP: Path('app/build/outputs/apk/debug/app-debug.apk'),
                ev.APP+'.test': Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')}
        summary['apks'] = {k: shared.sha(v) for k,v in apks.items()}
        dump = runner.checked([runner.sdk_tool(sdk,'build-tools/37.0.0/aapt2'),
            'dump','xmltree',apks[ev.APP+'.test'],'--file','AndroidManifest.xml'])
        ev.inspect_apk(apks[ev.APP+'.test'], dump, 'normal', expected)
        summary['installed'] = {k: shared.installed(probe,k,v) for k,v in summary['apks'].items()}
        selector = ','.join(name.rsplit('.',1)[0]+'#'+name.rsplit('.',1)[1] for name in sorted(expected))
        command = [probe.adb,'-s',serial,'shell','am','instrument','-w','-r',
                   '-e','class',selector,ev.APP+'.test/'+ev.NORMAL_RUNNER]
        command = [str(x) for x in command]
        summary['command'] = command
        result = probe.command('instrumentation',command,timeout=1200)
        summary['adb_exit'] = result.returncode
        raw = result.stdout+'\n'+result.stderr
        (out/'instrumentation.log').write_text(raw,encoding='utf-8')
        cases = ev.parse_android(raw,expected)  # strict default; no diagnostic opt-in
        assert len(cases)==8 and len({c['name'] for c in cases})==8
        if any(c['status']=='FAIL' for c in cases):
            summary.update(status='FAIL', reason='Executed JUnit failure')
        else:
            assert result.returncode==0
            probe.identify(boot=True)
            assert probe.adb_command('shell','cat','/proc/sys/kernel/random/boot_id') == summary['environment']['device']['boot']
            assert all(shared.sha(apks[k])==v and shared.installed(probe,k,v)==v for k,v in summary['apks'].items())
            current = ev.j_capture(Path.cwd(),run_id)
            assert all(current[k]==summary['source'][k] for k in ('revision','tree','dirty','source_digest','build','expected_jvm'))
            summary.update(status='PASS', complete=True)
except ev.IncompleteInstrumentation as error:
    cases = error.cases
    summary['reason'] = str(error)  # incomplete stays BLOCKED, never eight-test PASS
except (Exception,KeyboardInterrupt) as error:
    summary['reason'] = str(error)
finally:
    if probe: probe.close()  # host scratch only, no device operations
    summary['cases'] = cases
    blocker = summary.get('reason','Incomplete evidence') if summary['status']=='BLOCKED' else None
    ev.write_junit(out/'junit.xml','M5-B recovery',cases,blocker=blocker)
    summary['artifacts'] = {p.name: shared.sha(p) for p in out.iterdir() if p.is_file()}
    ev.j_atomic(out,'summary.json',json.dumps(summary,indent=2)+'\n')
print(summary['status'], out)
raise SystemExit({'PASS':0,'FAIL':1,'BLOCKED':2}[summary['status']])
PYCODE
```

Keep the whole report directory, including timeout/partial execution.log and any
infrastructure error in JUnit XML. A synthetic infrastructure XML case is not an
executed Android test. PASS requires eight unique completions, zero failures/errors/
skips, valid runner completion, ADB exit 0 and unchanged source/device/APK identity.
Review private local paths/logs before sharing; do not commit raw evidence.

## Infrastructure limits and acceptance boundaries

The existing primitives support this explicit eight-method selection and raw parsing.
The general local runner's full Android suite is not a suitable selector; do not invoke
it as a substitute. Its host-only Phase J profile is not Android execution evidence.
There is no existing dedicated M5-B CLI/report profile; the one-off procedure above
requires owner review and exact-device authorization before use. If a maintained
single-command profile becomes necessary, propose it separately rather than silently
adding a runner here. A build or static inventory check is never runtime PASS.

Synthetic reconstruction is not genuine OS process death; injected provider errors
are not external SAF acceptance. Existing tests do not uniformly PixelCopy-assert every
recovery endpoint; retain their actual visibility/controller assertions without claiming
stronger graphical evidence. Do not include worker termination, OS process killing,
permission revocation, backup/restore, D2D or destructive operations.

M4 and M5-A scoped closures remain unchanged. M5-A's `review_required=true` and PSS
growth remain unresolved. No memory budget, prolonged stability, physical-device
calibration or leak-free claim is approved. J08 remains **DEFERRED TO PHASE R**.
M5-B execution and scope closure are pending; no next phase starts automatically.
