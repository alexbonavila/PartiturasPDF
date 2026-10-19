# Phase I — application MVP integration acceptance register

Recorded **2026-10-20, Europe/Madrid**. I12 final audit; implementation delivered,
**formal Phase I acceptance pending**. This document grants no waiver, publication or merge.

## Revision, environment and audit scope

- Branch: `feat/app-mvp-integration-i`; I12 starting/tested source revision:
  `f51cc42df996390cf7e6aacf6cff85e004c9ca17`.
- Original Phase I main/merge base and freshly fetched `origin/main`:
  `cd86db5c4f70201cfbe6970de20cdb3308a970d0`. `git fetch --no-tags origin` succeeded;
  main has not moved. No merge/rebase/reset/cherry-pick or other-worktree change.
- Final delivery revision is the documentation-only commit containing this record. Resolve its
  exact full SHA with `git log -1 --format=%H -- docs/acceptance/I_APP_MVP_ACCEPTANCE.md`.
  The delivery response records it explicitly; a commit cannot embed its own content-addressed SHA.
- Audited14 Phase I commits from610f4b1 throughf51cc42; exact inventory below. I12 makes
  **documentation-only changes**, no new tests or application corrections. Tests execute the same
  source as the final commit: `app` tree `914300e2f5a27cf24d9e254c2c745bdc838af40a`,
  `scripts` tree `aacd1c1652369361ffdb0eb9468f479173095542`. Verify with `git rev-parse HEAD:app HEAD:scripts`.
- Author/committer: alexbonavila <alexbonavila44@gmail.com>; commits local only. No push/PR,
  workflow dispatch, main merge, next phase, benchmark, backup restore or device adoption.
- Linux x86_64; Python3.12.14, JDK25.0.4.1, Gradle9.8.0, AGP9.4.1, JaCoCo0.8.14,
  SDK37/build-tools37.0.0, bundletool1.18.3 already cached. minSdk26 remains unchanged.
  Existing cloud activation only; no dependency/toolchain/environment draft changes needed.
- `AGENTS.md`, testing/change-control/DoD, specification, stack, architecture, original supplied
  Phase I handoff, frozen F/G/H contracts/boundaries, G lifecycle handoff, pre-I closeout,
  translation/coverage records and immutable fixture README/manifest reviewed. Historical status
  paragraphs do not override actual source; no policy edits or inferred acceptance exceptions.

## Implemented scope and ownership review

| Work package | Actual source ownership and audit finding |
|---|---|
| I01–I02 | MainActivity obtains one retained AppHostViewModel through AppHostFactory. ProductionHostSession constructs one AppGraphFactory; the production foundation is required. F/H and one shared navigator start once per graph attempt. AppNavigation revision guards prevent stale route commands. No extra navigation framework/module. |
| I03–I04 | MainActivity/RootPickerBinding use AndroidX StartActivityForResult and original code/URI/flags. RootPickerCoordinator acknowledges launch separately, accepts once, synchronizes the identical F/Foundation navigator and finishes F once. Existing SafRootSelection alone persists root/READ; no duplicate acceptPersistedRoot save, synthetic grant or WRITE capture. F owns listing, registration/favorites and stale generations. |
| I05 | ReaderHostLifetime validates current F row/revision or one-shot registered restore permit, supplies one increasing G intent, and borrows the graph engine through existing foundation.reader. G alone opens, renders, presents and schedules checkpoint; host never fabricates Presented. Registered and transient remain distinct. |
| I06 | Activity UUIDs reject obsolete callbacks. Serialized BACKGROUND/LOW_MEMORY/CRITICAL/RESUME joins pending native/graphical operations. Non-lifecycle-suspended retirement delivery keeps detach possible; no runBlocking, forced recycle or timeout-success. Final quiesce/flush/retire/close and child join precede graph closure on IO. |
| I07 | Bounded versioned AppRestoration saves destination plus logical registered ID only. No raw transient locator/native/bitmap/page serialization. New owners wait for root/READ and preferences, re-resolve registered access and restore actual committed SharedPreferences position. No legacy Room fallback/dual write. Eligible system saved state is required; force-stop/task removal/crash are not equivalent. |
| I08–I10 | Host observes H StateFlow, not another DataStore collector. Ready controls theme/default fit; repeated observations preserve local fit. DeviceLocalePolicy uses delivered primary es/ca/en, unsupported→es; Activity context before resource reads and onStart recheck. Activity-owned Window effects require visible presented Reader + RESUMED + focus and clear on detach/background/Back/disposal. No manual selector/global locale mutation. |
| I11 | Existing R01–R40 matrix reviewed, including uncertain root writes, late picker cancellation, failed-close retained ownership, empty-owner safe discard and preserved secondary diagnostics. Typed provider errors do not establish deletion or mutate favorites. Original defects/cancellation are retained. Source tests pass; Android scheduling and real-grant behavior remain unverified. |
| I12 | Full local audit, fresh regression/artifact/coverage evidence, AAB investigation, Lint disposition, traceability, manual procedure and local PR material. No demonstrated new integration defect required a code correction. Split-language delivery is a documented release-mode concern requiring a separately approved correction/acceptance decision. |

Retained owner → one graph + shared F/H/navigator → one active Reader lifetime → G controller
borrows route/session; G owns frame/lease; Activity owns picker registrations, Surface and Window.
Configuration recreation replaces Activity/consumer, not graph/controller. Process death creates
new owners and revalidates access; no reliable shutdown callback is promised. Terminal disposal
rejects admission, retires Reader, stops F/H, cancels/joins graph users and closes only owned Room.
Application DataStore is borrowed from its existing singleton; it is not recreated per screen.
The worker remains a separate nonexported isolated UID/process and never starts the host graph.

## Fresh automated local validation

All results below are actual I12 executions against the unchanged source revision above.
Run from the repository with JDK25/SDK37 and declared fixture dependencies activated. Generated
logs/reports are local and ephemeral, excluded from commits. Historical results remain historical.

```bash
source /workspace/partituras-tools/activate.sh  # cloud-specific; use your configured toolchain elsewhere
bash ./gradlew :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest \
  :app:lintDebug :app:testDebugUnitTest --rerun --max-workers=4 --console=plain
python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest \
  --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 scripts/local_e2e.py run --suite host
python3 scripts/local_e2e.py doctor
adb devices -l
git diff --check
```

| Check / command above | Environment | Result / actual counts | Evidence / limits |
|---|---|---|---|
| Gradle debug/release/instrumentation APKs | Linux/JDK25; no Android runtime | PASS | `/tmp/i12-gradle.log`; release includes R8, unsigned review artifact, not signed distribution acceptance |
| JVM + existing contract coverage finalizer | Host JVM | PASS620 executed,0 failures/errors/skips | `app/build/test-results/testDebugUnitTest`, HTML under `app/build/reports/tests/testDebugUnitTest`; XML verifier PASS |
| Python infrastructure including localization negative/control cases | Host Python | PASS289, no skipped tests | `/tmp/i12-python.log`; intentional simulated FAIL/BLOCKED output in fixture tests is not an actual suite failure |
| Localization | Host/source XML | PASS155 main +20 debug IDs in each of4 bundles | Complete es/default equality, ca/en/type/format parity; no staging/plural/array resources currently; manual language review NOT RUN |
| Immutable corpus | Host verifier with declared dependencies | PASS15 | `/tmp/i12-fixtures.log`; exact hashes/size/structure/geometry/text/annotations/protection/render, corrupt15 fails as expected |
| Established local host orchestrator | Host only, no device | PASS all8 check groups,620 JVM +289 Python (909 heterogeneous tests, not909 JVM or Android tests) | `build/local-e2e/reports/20261019T224739Z-3ff2dc38f0f546a991ea7a3466d22081/{summary.json,junit.xml,index.html}`; host JUnit8 cases and root JUnit1 group,0 failures/errors/skips; fresh620 original JVM XML retained in `host/jvm-xml` |
| Lint | Android static analysis | PASS0 errors,26 warnings | `app/build/reports/lint-results-debug.{xml,html}`; all dispositions below |
| Actual APK/DEX inventory | Compiled artifacts | PASS0 PDFs in debug/release;15 test PDFs, all canonical hashes match; all421 source test IDs present in ordinary test APK; opt-in SAF class absent | Existing `local_test_evidence.android_inventory/inspect_apk`, aapt2 + ZIP/hash inspection; inventory is NOT execution |
| Policy B source/merged/compiled debug and optimized release rules | Static artifact audit | PASS exact allowlist and both manifest references | Existing verify_backup_policy functions; optimized release paths resolved from aapt2 table (`res/Qq.xml`,`res/4j.xml`) instead of assuming source XML filenames; no actual restore |
| Device discovery | Linux x86_64 | BLOCKED doctor exit2; devices empty | `/tmp/i12-doctor.json`: KVM vmx/svm unavailable, dedicated AVD UNPROVISIONED; no software fallback/new AVD/foreign device |
| Android26/27/36, SAF/native/graphics/process-death | No authorized usable runtime | NOT RUN;0 executed here |421 source tests =407 standard +14 assigned diagnostics,29 controlled invocations,24 standard isolation tests preserved |
| Hosted current-revision checks | GitHub API | BLOCKED access, execution NOT RUN | API CONNECT proxy403; no latest-revision artifacts/run URL, dispatch or incident investigation; historical main runs cannot certify I |
| Diff/approved scope | Git/static review | PASS | No production/test/build/policy/fixture changes in I12; no binary or private-document inputs |

The first bundle inspection tried to dump before Gradle's bundle task completed; explicit external
task dependency corrected the audit harness and final bundle commands pass. The release backup CLI's
source-path assumption failed on optimized resource filenames; resolving actual resource-table paths
with unchanged verifier functions passed. These initial audit-command failures are not hidden or
converted to production defects. No repository infrastructure was modified to bypass them.

The full JVM suite includes197 app-host/navigation methods in11 suites: AppHostViewModelTest18,
HostLibraryIntegrationTest9, RootPickerCoordinatorTest31, ReaderHostLifetimeTest19,
ReaderHostIntegrationTest23, ReaderLifecycleIntegrationTest27, AppRestorationIntegrationTest31,
AppPresentationIntegrationTest29, AppNavigationTest3, ReaderNavigationTest3, AppRestorationTest4.
Existing F/G/H, foundation, SAF contract, checkpoint/writer and cancellation regressions remain.
No new I12 tests are needed because no executable behavior changes. All46 added Phase I Android
methods remain enabled/compiled: host Activity7, startup2, binding6, Library persistence3,
native host23, locale5. Their platform evidence is NOT RUN, not PASS.

## Scoped coverage and source attribution

Existing JaCoCo0.8.14 agent on actual620 JVM execution, debug Kotlin classes including nested,
coroutine and data classes; Linux/JDK25. No hand-written execution data, dropped difficult branch
or shared Gradle/gate change. Reports `/tmp/i12-coverage/{report,reader,all}.xml` and HTML siblings.
Reproduce the existing external script at the end of the I11 handoff, replacing `i11` with `i12`
(in task names and `/tmp` report paths), then execute:

```bash
bash ./gradlew -I /tmp/i12-coverage.init.gradle \
 :app:i12Verify :app:i12ReaderCoverage :app:i12AllCoverage \
 --no-configuration-cache --max-workers=4 --console=plain
```

| Handwritten logic source | LINE covered/total | BRANCH covered/total | Independent result |
|---|---:|---:|---|
| `AppNavigation.kt` | 13/13 (100.00%) | 16/16 (100.00%) | PASS |
| `AppRestoration.kt` | 16/16 (100.00%) | 28/28 (100.00%) | PASS |
| `AppHostViewModel.kt` | 259/262 (98.85%) | 327/386 (84.72%) | PASS |
| `AndroidReaderLifecycle.kt` | 7/7 (100.00%) | 7/7 (100.00%) | PASS |
| `ReaderHostLifetime.kt` | 189/189 (100.00%) | 190/214 (88.79%) | PASS |
| `AppPresentationState.kt` | 20/20 (100.00%) | 41/49 (83.67%) | PASS |
| `AppHostState.kt` | 11/11 (100.00%) | 0/0 — NOT APPLICABLE | PASS (no branches) |
| `RootPickerCoordinator.kt` | 85/85 (100.00%) | 80/86 (93.02%) | PASS |

Combined logic600/603 LINE **99.50%**,689/786 BRANCH **87.66%**. I11's independently affected
RootPickerCoordinator + ReaderHostLifetime:274/274 **100%**,270/300 **90%**. No low-covered new
component is hidden by the aggregate. This assignment adds no business logic.

The unfiltered report deliberately includes Android/Compose misses:601/862 LINE **69.72%**,
689/950 BRANCH **72.53%**. AppHostFactory/ProductionHostSession0/39 lines,0/30 branches;
RootPickerBinding0/17,0/12; AppHost0/106,0/82; AppPresentation0/35,0/2;
MainActivity0/52,0/38. AppLocaleContext1/10 lines is only the pure appLanguage policy forwarding;
its other9 Context/system-resource lines and real Activity integration are unexecuted.
Production session composition, UI, lifecycle/Window/context and registry binding need Android;
they are not marked fully covered. Existing JVM-tested lifecycle policy remains included above.

Compared with dated I11, fresh full recompilation retained the same sources and all gated logic
counters, but the unfiltered Compose branch inventory differs: AppHost88→82 and AppPresentation8→2.
These compiler-generated output differences account for962→950 unfiltered branches; no source,
scope or threshold was changed, and no runtime coverage improvement is claimed from that denominator.
JaCoCo reported no class/execution mismatch. Current reports, not extrapolated historical denominators,
are authoritative for this run. Real Android combined coverage, SAF enforcement, graphical presentation
and isolated worker-process probes remain NOT RUN/NOT MEASURED. No worker exporter is authorized.

## I-T01–I-T17 requirement-to-evidence register

The scenario column preserves the **exact original handoff descriptions**, not merely the Prompt9
summary. Production paths below are relative to `app/src/main/java/com/abdev/partituraspdf`;
JVM suites under `app/src/test/java/com/abdev/partituraspdf`; Android suites under `app/src/androidTest/java/com/abdev/partituraspdf`.
`host/` below means `app/host/`, `nav/` means `app/navigation/`; Android names are in `app/integration/`
unless otherwise specified. All named JVM suites actually ran. Android entries are compiled and
reviewed, not executed. JVM success does not close a combined platform requirement. No waiver.

| ID | Exact scenario / implemented capability | Production symbols | JVM evidence | Android evidence | Local execution | Hosted execution | Required manual evidence | Outstanding limit | Final criterion |
|---|---|---|---|---|---|---|---|---|---|
| I-T01 | One graph/Room/DataStore ownership across configuration recreation | MainActivity; host/AppHostFactory.ProductionHostSession; AppHostViewModel.initialize/release | host/AppHostViewModelTest, AppRestorationIntegrationTest | AppHostActivityTest.recreationRetainsViewModelNavigatorControllersAndSettingsDestination | PASS JVM; NOT RUN Android | NOT RUN | Real recreation and final shutdown | Production Android graph identity unexecuted | **NOT RUN** |
| I-T02 | Library initial route and Settings/back navigation | nav/AppNavigation; host/AppHost; AppHostViewModel.back | nav/AppNavigationTest, ReaderNavigationTest; host/ReaderHostIntegrationTest | AppHostActivityTest.androidBackReturnsFromSettingsWithoutDisposingGraph | PASS JVM; NOT RUN Android | NOT RUN | Library/Settings/Reader Back and repeated Back | Compose/dispatcher unexecuted | **NOT RUN** |
| I-T03 | Real picker accepted: URI + actual flags; READ only | MainActivity; RootPickerBinding.bindRootPicker; RootPickerCoordinator; SafRootSelection | host/RootPickerCoordinatorTest; HostLibraryIntegrationTest | RootPickerBindingTest.actualRegistryForwardsOriginalIntentUriAndFlagsWithoutAuthorizationOnLaunch; storage/saf tests | PASS JVM; NOT RUN Android | NOT RUN | Genuine DocumentsUI grant; inspect actual persisted READ, no WRITE | Registry tests use synthetic result/grant seams; authentic new-host journey NOT RUN | **NOT RUN** |
| I-T04 | Picker cancellation/denial/replacement failure preserves previous valid root | RootPickerCoordinator.result/reconcile/close | host/RootPickerCoordinatorTest | RootPickerBindingTest.canceledRegistryResultCannotPersistEvenWhenItContainsAUri; LibraryPersistenceIntegrationTest.canceledOrInvalidReplacementPreservesAuthorizedRootAndExistingRoomMetadata | PASS JVM; NOT RUN Android | NOT RUN | Cancel/deny/replace/rotate pending picker | No genuine OS picker acceptance execution | **NOT RUN** |
| I-T05 | Folder navigation, rooted Back, refresh, stale results | ProductionHostSession.load; FoundationHost; F LibraryController; same SafFolderNavigation | host/HostLibraryIntegrationTest; library/LibraryConcurrencyTest | LibraryPersistenceIntegrationTest.acceptedRootListsNavigatesRegistersAndPersistsFavoriteAcrossControllerRestart | PASS JVM; NOT RUN Android | NOT RUN | Real nested folders/root-bounded Back, refresh/provider outage | Injected catalog/grant tests do not prove external provider | **NOT RUN** |
| I-T06 | Explicit registration, favorite persistence, temporary open | F LibraryController; RegistrationCoordinator; graph registry/metadata | host/HostLibraryIntegrationTest; library/LibraryRegistrationTest, LibraryDocumentLifecycleTest | LibraryPersistenceIntegrationTest.acceptedRootListsNavigatesRegistersAndPersistsFavoriteAcrossControllerRestart | PASS JVM; NOT RUN Android | NOT RUN | Explicit register/favorite/restart, unregistered temporary open | Room/UI execution NOT RUN | **NOT RUN** |
| I-T07 | F registered/transient open → G one native route intent | AppHostViewModel.library; ReaderHostLifetime.arm/accept; G.open | host/ReaderHostIntegrationTest; ReaderHostLifetimeTest | ReaderHostNativeIntegrationTest.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration | PASS JVM; NOT RUN Android | NOT RUN | One registered/transient F→G open from authentic root | Native fixtures use test file capability, no genuine grant proof | **NOT RUN** |
| I-T08 | Actual page presentation and page turn | G ReaderController/ReaderScreen/ReaderPageSurface; isolated graph engine | reader/ReaderPresentationTest; host/ReaderHostLifetimeTest | ReaderHostNativeIntegrationTest.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen; reader/ReaderNativeIntegrationTest | PASS JVM; NOT RUN Android | NOT RUN | Actual visible page/turn/PAGE/WIDTH/zoom and graphical release | Surface/PixelCopy/native worker NOT RUN | **NOT RUN** |
| I-T09 | Registered last visible page restores; transient does not persist position | ReaderRouteOwner.displayed/background; ReadingCheckpoint; SharedPreferencesReaderPositions | host/AppRestorationIntegrationTest; ReaderLifecycleIntegrationTest | ReaderHostNativeIntegrationTest.syntheticSavedBundleReopensDiskRegisteredScoreAtDurablyPresentedPageWithFreshNativeSession | PASS JVM; NOT RUN Android | NOT RUN | Actual committed page reopen and transient nonpersistence | Synthetic Bundle is not system process-death/disk-failure proof | **NOT RUN** |
| I-T10 | Rotation replaces only graphical consumer, no duplicate open/checkpoint | MainActivity retained ViewModel; ReaderHostLifetime; G graphical consumer | host/AppRestorationIntegrationTest; ReaderHostIntegrationTest | ReaderHostNativeIntegrationTest.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen; AppHostActivityTest | PASS JVM; NOT RUN Android | NOT RUN | Rotate during display/open/picker; unchanged native owner/new consumer | Activity scheduling unexecuted | **NOT RUN** |
| I-T11 | Background/trim/resume/retire ordered correctly | MainActivity lifecycle callbacks; AndroidReaderLifecycle; ReaderHostLifetime.drain/leave | host/ReaderLifecycleIntegrationTest; ReaderHostIntegrationTest | ReaderHostNativeIntegrationTest.genuineActivityBackgroundFlushesAndResumeReopensWithoutNewUserIntent; simulatedLowMemoryReleasesFrameKeepsSessionAndPreservesLocalZoomOnExplicitRecovery; simulatedCriticalMemoryClosesNativeSessionBeforeUserRecovery | PASS JVM; NOT RUN Android | NOT RUN | Background/resume/Back, identified simulated trims, device cleanup | Real OS memory pressure/performance not established | **NOT RUN** |
| I-T12 | Theme SYSTEM/LIGHT/DARK and fit PAGE/WIDTH applied from observed preferences | AppHostViewModel.observePresentation; AppPresentation; ReaderHostLifetime.defaultFit | host/AppPresentationIntegrationTest; settings/SettingsControllerTest | ReaderHostNativeIntegrationTest.confirmedDataStoreThemesApplyWithoutReopeningVisiblePdf; persistedDefaultFitAndLocalMenuRemainDistinctWhileConfirmedChangesStayInSameNativeSession | PASS JVM; NOT RUN Android | NOT RUN | Settings effects, restart, uncertain writes and Reader local fit | Injected night/resource configuration differs from device change | **NOT RUN** |
| I-T13 | es/ca/en and unsupported-language Spanish fallback (API26+) | AppLocaleContext; DeviceLocalePolicy.resolve; MainActivity.attachBaseContext/onStart | host/AppPresentationIntegrationTest; settings/DeliveredLocalePolicyTest | AppLocaleIntegrationTest.deliveredPrimaryLanguagesResolveRealNavigationFeatureAndAccessibilityResources; unsupportedPrimaryNeverUsesSupportedSecondaryAndEmptyInputsUseSpanishResources; productionActivityUsesActualSystemPolicyAndRecreationRetainsHostAndLocalizedControls | PASS JVM; NOT RUN Android | NOT RUN | Actual system es/ca/en/unsupported and recreation, APK and AAB split installs | AAB initial selections differ by language; delivery/switching NOT RUN | **NOT RUN** |
| I-T14 | Keep-screen-on scoped to visible Reader | AppPresentation DisposableEffect; AppPresentationState.readerVisible/keepReaderScreenOn | host/AppPresentationIntegrationTest | ReaderHostNativeIntegrationTest.actualWindowFlagTracksVisibleBackgroundResumedReaderBackAndSettings; presentationDisposalClearsItsWindowBeforeTerminalReaderCleanup | PASS JVM; NOT RUN Android | NOT RUN | Real Window flags/focus/background/Back and no sticky screen-on | JVM eligibility is not Android Window proof | **NOT RUN** |
| I-T15 | Provider revocation/error, missing document, isolated renderer failure | RootPickerCoordinator; ReaderHostLifetime; existing F/G recovery | host/RootPickerCoordinatorTest; ReaderLifecycleIntegrationTest; ReaderHostIntegrationTest; AppRestorationIntegrationTest | ReaderHostNativeIntegrationTest.realWorkerLossUsesExistingReaderRetryWithoutDeletingRegistrationFavoriteOrCommittedPage; injectedProviderUnavailableIsContextualAndRetryRevalidatesBeforeRealNativeOpen; injectedCheckpointFailurePreservesRealEarlierPositionAndSafeReopenAfterEmptyRouteVerification | PASS JVM; NOT RUN Android | NOT RUN | Real revoked READ/provider outage/protected input/native failure/safe reopen | R01–R40 JVM PASS; injected faults do not establish actual outage/revocation | **NOT RUN** |
| I-T16 | No fixture PDFs bundled in release/main APK | Existing androidTest-only asset source set; debug/release APKs | Fixture verifier and APK ZIP/hash audit | Compiled test APK canonical assets and all421 test IDs inspected | PASS artifact checks | NOT RUN | NOT APPLICABLE to the no-fixture-in-production artifact criterion | Does not certify native rendering/annotations | **PASS** |
| I-T17 | Language/accessibility labels, larger fonts, orientation and back actions | AppHost semantics/resources; F/G/H controls/insets | Localization validators; reader/ReaderInteractionTest | AppStartupScreenTest; reader/ReaderScreenTest; library/LibraryScreenTest; settings/SettingsScreenTest | PASS JVM; NOT RUN Android | NOT RUN | Human es/ca/en copy, TalkBack, larger fonts, portrait/landscape, keyboard and Back | No manual/physical accessibility execution | **NOT RUN** |

Final criterion summary: **PASS I-T16; NOT RUN I-T01–I-T15 and I-T17; no observed FAIL**.
Environment/API and hosted prerequisites are **BLOCKED**. Local logic subchecks are PASS but do
not change those final classifications. There is no comprehensive Android/security/phase PASS.

## Android, native, real SAF and process reconstruction acceptance

API26 **NOT RUN**, API27 **NOT RUN**, API36 **NOT RUN**, physical Android8.1 **NOT RUN**.
No connected device, usable KVM or verified dedicated provisioned emulator exists in this cloud.
No new/foreign AVD, physical data, grant, setting or backup was touched. No software fallback.

On an existing owner-verified disposable accelerated emulator, inspect identity **before** installing:

```bash
python3 scripts/local_e2e.py doctor
adb devices -l
# Set ANDROID_SERIAL only after owner confirms this exact disposable emulator and its AVD ownership.
export ANDROID_SERIAL='emulator-<verified-port>'
adb -s "$ANDROID_SERIAL" emu avd name
adb -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk
adb -s "$ANDROID_SERIAL" shell getprop ro.product.cpu.abi
# Must match the separately verified intended API26/27/36; run independently for each:
bash scripts/run_pdfium_ci.sh 26  # use27/36 only on their corresponding authorized emulator
```

The existing CI script runs407 standard source tests with the14 assigned tests excluded by their
existing annotation, reinstalls instrumentation if Gradle removed it, and runs29 controlled native
diagnostic invocations with fail-closed original evidence. Do not run every diagnostic in a single
uncontrolled process or relabel raw expected diagnostic failures as ordinary acceptance PASS.
Inspect actual original JUnit/controlled JSON, complete inventory, counts, skips, API/ABI, SHA and
artifacts; require all24 ordinary native-isolation tests and all46 new I methods. Rerun after any
correction; older source/run counts do not certify latest code. Native worker loss uses only the
test engine's exact PID/start/epoch and existing retirement protocol, never an arbitrary PID kill.

On Windows use PowerShell `$env:ANDROID_SERIAL = 'emulator-<verified-port>'`, the same read-only adb
identity queries and the existing owner-supported runner/CI shell tooling. Do not assume Bash/date
semantics are native PowerShell. `python scripts/local_e2e.py run --suite full` supplies host + owned
API26 Android + authentic opt-in SAF stages after doctor PASS; `--suite coverage` supplies existing
genuine JVM/Android measurements. The opt-in SAF harness is a debug acceptance Activity: its PASS
cannot substitute for the production MainActivity picker-to-Reader journey below. Native Windows/
macOS are NOT RUN here. API27/36 use separately verified runtimes/current compatibility workflow,
not substitutions for26. Never provision, wipe, uninstall or clear data on an unidentified target.

For real process death on the verified disposable emulator, use an eligible task/saved-state path:
open registered score, genuinely present/commit a known page, Home, record task/process identity,
then owner-controlled `adb -s "$ANDROID_SERIAL" shell am kill com.abdev.partituraspdf` while backgrounded.
Confirm the old PID is gone and relaunch the **existing task from Recents**, not a fabricated Bundle.
Record whether Android actually supplied saved state, fresh host/worker identity and restored page.
Without an eligible snapshot expect Library; do not certify Reader restoration from that run.
Repeat with transient input and require Library/no automatic transient access. Force-stop, task
removal, clean graceful fixture disposal and synthetic Bundle reconstruction are different evidence.
No destructive backup, clear-data or document deletion is necessary or authorized by this procedure.

For genuine permission loss, acquire through DocumentsUI first on a test-owned folder. Record actual
READ-only persisted grant. Revoke only that known test grant using an owner-approved Android UI/path;
never release all app grants or infer enforcement from injected callbacks. Reopen/revalidate: preserve
registration/favorite/committed page, show correct contextual revoked/provider/I/O versus deletion,
then reacquire through the real picker. An already acquired descriptor/private read copy can remain
valid; this is not proof grant revalidation is bypassed. Provider outage must be a real controlled
provider event, labeled separately from test-injected AppErrors. Do not modify the original PDFs.

## Physical Android8.1/manual reusable script

Execution is **NOT RUN**, requires owner/device authorization. Use a deliberately prepared test
application/profile and a copied synthetic test folder; no uninstall/clear/data reset or mutation of
user scores. Fresh install requires a clean test context, not deleting an owner's existing app data.
Use at least two valid multi-page canonical copies, one left unregistered, plus protected/malformed
copies where safe. Preserve originals/hashes. Keep screenshots free of personal provider paths.

Record once per run (repeat if changed): **device model**, Android version/API, ABI/build,
**APK full Git revision/hash**, test date/time/timezone, tester, install/distribution mode, folder/provider
identity and authorization. For every row fill Observed, PASS/FAIL, Notes/screenshot; until executed the
status is NOT RUN. Keep failed/blocked evidence; never fill expected behavior as an observation.

| # | Action | Expected behavior | Observed | Result | Notes/screenshots |
|---|---|---|---|---|---|
| 1 | Fresh install in authorized clean test context | Install identified APK; no broad storage/network/account requirement. | — | NOT RUN | — |
| 2 | Launch | Library, no starter greeting; accessible folder/Settings controls. | — | NOT RUN | — |
| 3 | Choose test folder in actual DocumentsUI | Original result accepted once; persisted READ only; correct root listing. | — | NOT RUN | — |
| 4 | Enter subfolders and Back | Correct breadcrumbs/listing; never escape authorized root. | — | NOT RUN | — |
| 5 | Explicitly register one PDF | Independent logical registered identity; no auto-registration of other rows. | — | NOT RUN | — |
| 6 | Toggle favorite | Confirmed favorite survives refresh/restart; no PDF mutation. | — | NOT RUN | — |
| 7 | Open registered PDF | One isolated native opening; real page visible, correct identity. | — | NOT RUN | — |
| 8 | Open unregistered PDF after Back | Temporary read; no fake registration or permanent checkpoint. | — | NOT RUN | — |
| 9 | Navigate pages | Actual posted pages/count/endpoints; no stale page confirmation. | — | NOT RUN | — |
| 10 | PAGE/WIDTH and visual zoom | Correct aspect/scroll/pan; local fit does not silently change stored default. | — | NOT RUN | — |
| 11 | Back to Library, including repeated Back | Detach/cleanup then Library; no frozen UI, stale frame or competing close. | — | NOT RUN | — |
| 12 | Reopen document | Fresh eligible lifetime, same registration/favorite; prior confirmed page where applicable. | — | NOT RUN | — |
| 13 | Rotate during Reader and pending picker | Retained graph/controller; new consumer; no duplicate grant/open/checkpoint. | — | NOT RUN | — |
| 14 | Background/resume; identified test trim scenarios | Serialized release/flush/reopen; LOW versus CRITICAL remain distinct; no hidden render. | — | NOT RUN | — |
| 15 | Close/reopen app; separately test eligible background process kill | Without saved state Library; eligible registered saved navigation re-resolves using new owners. | — | NOT RUN | — |
| 16 | Restore committed position | Previously actually presented and committed page, clamped to count; no requested-page shortcut. | — | NOT RUN | — |
| 17 | Change SYSTEM/LIGHT/DARK and default fit | Only observed preferences apply; local fit distinction and restart persistence hold. | — | NOT RUN | — |
| 18 | Change system primary es/ca/en/unsupported | Correct automatic copy and Spanish unsupported fallback; repeat APK and separately authorized AAB delivery scenarios. | — | NOT RUN | — |
| 19 | Inspect screen-on/focus/system bars | Only visible presented Reader keeps screen awake; clears on background/Back/Settings; controls/insets usable. | — | NOT RUN | — |
| 20 | Exercise real errors and permission recovery | Cancel/denied/revoked/provider unavailable/protected/corrupt; contextual error, preserved metadata, explicit safe recovery. | — | NOT RUN | — |

Also record TalkBack traversal/labels,48dp controls, large-font scrolling, portrait/landscape,
light/dark, keyboard focus and independent Spanish/Catalan/English linguistic review. Sheet-music
bitmap content is not structured accessible notation; no OCR/music-text promise. Physical/tablet
performance/memory and external-provider matrices remain separately scheduled, not measured here.

## Localization and actual AAB investigation

Source audit:155 main strings (3 starter+72F+48G+24H+8app),20 debug SAF strings in each default/es/ca/en;
no plurals/arrays currently, no staging, complete type/format/default-es parity. No new hardcoded UI
text, selector, active legacy LanguageSelection, Locale.setDefault or global-resource mutation was
found in Phase I. AppLocaleContext calls DeviceLocalePolicy with both delivered languages; only
primary es/ca/en wins, unsupported/empty→es even if secondary en/ca. The actual Activity wraps before
resource reads, permits normal Android configuration recreation and checks policy again onStart.
JVM policy tests PASS; configuration/resource/Activity tests compile but are NOT RUN. Injected night
configuration does not prove system changes. Human layout/TalkBack/linguistic checks remain open.

Actual bundletool1.18.3 from existing AGP runtime classpath built a debug AAB and default APK set,
then selected real split files for **synthetic device specifications**, not connected devices:

| Spec | Actual selected APK files | Other supported language resource availability |
|---|---|---|
| API26/x86_64/mdpi/en | base-master,base-mdpi,base-x86_64,base-en | No base-ca or base-es split in this selection |
| API26/x86_64/mdpi/ca | same shared base/density/ABI +base-ca | No base-en or base-es split in this selection |
| API26/x86_64/mdpi/es | same shared base/density/ABI +base-es | No base-en or base-ca split in this selection |

AAB contains ca/en/es resources/splits; no PDFs. aapt2 verifies base-master app_settings default
Spanish **Ajustes**, base-en English **Settings**; ca split **Configuració**. Thus initial language
selection omits other language splits; this is directly demonstrated artifact behavior. It is NOT
proof that Play/device redelivery fails or succeeds after system-language changes. A universal APK
contains all languages; it does not exercise split installation. No APK-set installation, Play flow,
network-dependent language download, offline system switch or AAB release signing was executed.

**Affected mode/risk:** language-split AAB distribution plus device/application locale switching,
especially offline to a not-installed language. The policy can request ca while a prior en-selected
set has no ca; resource fallback may be Spanish until the appropriate split is delivered. No
universal-APK defect is demonstrated. Existing Gradle has default language splitting, no explicit
`bundle.language.enableSplit=false`; host has no Play language-download client. Lint correctly flags
this configuration. Do not equate a complete bundle with all languages installed on every device.

**Minimal proposed correction, NOT IMPLEMENTED:** separately approve `app/build.gradle.kts`
`android { bundle { language { enableSplit = false } } }` to ship all supported copy in the base,
matching the offline automatic-language requirement without a runtime download dependency. An
alternative delivery client/distribution restriction is a distinct owner decision, not inferred here.
Regression must build the approved release AAB, inspect all device selections, install on authorized
API26/27/36 and switch es/ca/en/unsupported offline with Activity recreation and retained ownership.
Review size/distribution effect; preserve minSdk, resource IDs/formats and mandatory gates.
**Owner approval is required before any Gradle packaging edit.** This acceptance item stays open.

Official references: Android [base-module/language splits](https://developer.android.com/guide/app-bundle/configure-base#disable_config_apks)
and [bundletool](https://developer.android.com/tools/bundletool); developer.android.com access was
BLOCKED proxy403 here, so no fresh web-page verification is claimed. Google's versioned
[bundletool1.18.3 README](https://github.com/google/bundletool/blob/1.18.3/README.md) was fetched and
confirms APK-set generation/device-spec extraction. Installed official bundletool/AGP outputs and
Lint are the direct evidence above. No policy/proxy bypass, tool download or repository build change.

Reproduce outside the checkout using this small **external** Gradle init script (e.g.
`/tmp/i12-bundle.init.gradle`); it uses already-installed buildscript dependencies:

```groovy
gradle.beforeProject { p ->
 if (p.path == ':app') p.afterEvaluate {
  p.tasks.register('i12Bundletool', JavaExec) {
   dependsOn 'bundleDebug'
   classpath = p.rootProject.buildscript.configurations.classpath
   mainClass = 'com.android.tools.build.bundletool.BundleToolMain'
   workingDir = p.rootProject.projectDir
   args = (p.findProperty('i12BundleArgs') ?: 'version').split(' ').toList()
  }
 }
}
```

```bash
bash ./gradlew -I /tmp/i12-bundle.init.gradle :app:i12Bundletool \
 '-Pi12BundleArgs=dump config --bundle=app/build/outputs/bundle/debug/app-debug.aab' \
 --offline --no-configuration-cache --max-workers=4 --console=plain
bash ./gradlew -I /tmp/i12-bundle.init.gradle :app:i12Bundletool \
 '-Pi12BundleArgs=build-apks --bundle=app/build/outputs/bundle/debug/app-debug.aab --output=/tmp/i12-language.apks --aapt2=<installed-aapt2>' \
 --offline --no-configuration-cache --max-workers=4 --console=plain
# Write /tmp/i12-device-en.json (repeat ca/es with different new output directories):
# {"supportedAbis":["x86_64"],"supportedLocales":["en"],"screenDensity":160,"sdkVersion":26}
bash ./gradlew -I /tmp/i12-bundle.init.gradle :app:i12Bundletool \
 '-Pi12BundleArgs=extract-apks --apks=/tmp/i12-language.apks --device-spec=/tmp/i12-device-en.json --output-dir=/tmp/i12-splits-en' \
 --offline --no-configuration-cache --max-workers=4 --console=plain
# Inspect actual tables: aapt2 dump resources /tmp/i12-splits-en/base-master.apk, base-en.apk, etc.
```

Use fresh output paths; do not overwrite existing owner artifacts. Replace the documented aapt2
placeholder with the installed SDK executable; this run used build-tools37.0.0. Version/dump/build/
extract commands PASS after correcting external task ordering. Logs `/tmp/i12-bundletool-version.log`,
`i12-bundle-config.log`,`i12-build-apks.log`,`i12-extract-{en,ca,es}.log`; resource dumps under `/tmp/i12-*`.
Debug keystore is the existing local test signing material; nothing was committed or distributed.

## Every Lint warning: disposition and provenance

Fresh0 errors/**26 warning entries**. Previous pre-I24, I05 added unused Reader key25, I08–I10 added
unused banner/AAB27, I11 uses Reader key for actual blocked-cleanup UI26. No I12 new warning or
suppression. Categories total26; multiple locations in a single issue are not additional warnings.

| Category | Count | Historical / Phase I | Disposition |
|---|---:|---|---|
| RedundantLabel |1| Historical MainActivity manifest label | Nonblocking redundancy; retain unchanged manifest; no functional defect demonstrated. |
| GradleDependency |3| Historical core-ktx1.10.1, lifecycle-runtime-ktx2.6.1, activity-compose1.8.0 | Upgrade suggestions, not vulnerability findings; versions/API26 compatibility require separate approved dependency task. |
| NewerVersionAvailable |3| Historical Compose plugin2.2.10, coroutine core/test1.9.0 | Tooling/version suggestions; no unauthorized upgrade or claim latest is validated. |
| PluralsCandidate |6| Historical debug SAF entries in default/en, strings lines18/19/20 | Grammar recommendation, not resource/format parity failure; a future reviewed plural/caller migration requires all locales and tests. No contract/type rewrite to silence it. |
| UnusedResources |7| Historical purple200/500/700,teal200/700,black,white | Starter colors not used by current theme; nonblocking cleanup proposal only, no deletion. |
| UnusedResources |1| Phase I app_integration_boundary | Obsolete banner removed from UI, ID retained in all4 bundles. Not rendered or functional; keep compatibility/parity. Owner can approve later removal after usage review; no silent text/ID deletion. |
| UseKtx |4| Historical AndroidPdfReadEngine.createBitmap, SafUriCodec.toUri twice, SharedPreferencesReaderPositions.edit | Style suggestions in protected sources; current explicit APIs/commit semantics intentional, no rewrite. |
| AppBundleLocaleChanges |1| Phase I AppLocaleContext.kt:23 | Genuine language-delivery risk, not a universal-APK failure; investigated above. Packaging/delivery decision and actual acceptance BLOCKED pending owner scope/runtime. Never suppress to close. |

These are dispositions, not acceptance waivers. Legacy trim callback compiler deprecations remain
visible and named API26 behavior is tested at JVM policy level; modern OS delivery is unexecuted.
An existing unrelated MetadataDatabaseOpenTest nullable-string compile warning is not changed.
No static Lint warning establishes a critical/high security finding by itself; no comprehensive
security or dependency vulnerability certification is claimed.

## Security, architecture and complete Phase I diff review

Reviewed all38 starting Phase I changed paths against original main, including every production
host/navigation/locale file, relevant F/G/H/foundation/SAF/route boundaries, new tests and previous
commits. MainActivity no longer mounts Greeting (preview remains). No feature-internal rewrite,
new dependency/module/manifest permission or changed Gradle/worker/AIDL/schema/backup/CI policy
was found in Phase I. The one shared infrastructure diff is the previously owner-approved
147→155 complete-localization inventory assertion, preserving strict parity/negative cases.
FoundationInstrumentedTest's launcher case now asserts the production Library shell; other
foundation assertions remain. I12 itself changes no tests or executable code.

Actual merged debug/release manifests retain only the existing signature dynamic-receiver
permission; no INTERNET, broad-storage or added WRITE permission. Worker exported=false,
isolatedProcess=true, process=:pdf, no host graph bootstrap. Actual debug/release Policy B rules
allow only file:datastore/app_preferences.preferences_pb; selected-root AtomicFile, Room/WAL/SHM/
journals, reader-position prefs/private cache remain outside the exact allowlist. Proto remains
non-sensitive theme/fit/legacy-language, no provider locator or credential. Actual backup/restore,
D2D and newer conditional transfer paths remain separately unverified; no security waiver.

No document mutation/deletion, alternate renderer, native handle serialization, raw locator-based
restoration, global Activity/Window retention, duplicated graph/Room/DataStore or new network/cloud
flow found in reviewed I source. Navigation IDs are bounded/control-validated and never treated as
permission; underlying registry/read access revalidates. AppHost text uses generic/localized resources,
not raw provider URIs/exception messages. Unexpected exceptions remain developer diagnostics through
the existing Log.e callback; this is not a guarantee against every external exception containing
sensitive details. Do not publish private logcat/provider paths. No hardcoded credential or private
PDF was found in the reviewed diff; a static review/build is not penetration testing or formal audit.

Fixture manifest/binaries and licenses are unchanged. Actual APK test PDFs hash-match canonical
inputs; production debug/release and inspected AAB have0 PDFs. All421 ordinary-source test IDs are
present in test DEX; opt-in local SAF class remains absent. Android inventory has407 standard tests,
14 diagnostic assignments,24 ordinary isolated tests and29 controlled invocations. No tests disabled,
classified differently or fabricated as executed. No full-worker probe data or performance claim.

## Hosted CI, publication and owner decisions

All seven mandatory jobs for this final branch revision remain **NOT RUN**:
`build`, `lint`, `unit-tests`, `instrumented-api26`, `localization`, `pdf-fixtures`, `quality-gate`.
Local checks are independently reported above, not attributed to GitHub. API access was tested once
for the I12 starting SHA and returned CONNECT proxy403; no raw hosted artifacts were obtainable.
No publication is authorized, so no final audit commit workflow/PR URL exists. Git read/fetch success
does not establish Actions API/write access. No credentials/settings change or incident repair.

After explicit owner publication/PR authorization only, request the existing CI workflow against
this branch with compatibility=true. Example for that later authorized step, **NOT EXECUTED**:

```bash
gh workflow run ci.yml --repo alexbonavila/PartiturasPDF \
 --ref feat/app-mvp-integration-i -f compatibility=true
```

Inspect exact head/base, seven final conclusions, separate API27/API36 jobs, original JUnit/controlled
native reports and persistence coverage gate. Current main drift must be rechecked at review time;
if it moves, propose synchronization for approval and revalidate, never silently rebase/merge.
Owner alone decides whether to publish/open a draft PR and eventually merge. Branch protections,
review approvals and final acceptance were not verified/changed here.

Required next decisions/evidence:

1. Authorize a distribution/packaging solution for offline supported-language switching; proposed
   minimal language-split disablement is protected and **not implemented** here.
2. Authorize publication/PR creation separately; local prepared title/body are in
   [I_APP_MVP_PR_DESCRIPTION.md](../integration/I_APP_MVP_PR_DESCRIPTION.md).
3. Execute latest-revision API26, API27, API36, genuine production SAF/Surface/worker recovery,
   actual device locale changes and eligible process-death scenarios; retain original artifacts.
4. Schedule physical Android8.1, manual accessibility/linguistic/visual/provider acceptance. Existing
   C06 real backup/D2D and physical/performance requirements remain open under their own registers.
   No benchmark, destructive backup or unknown-device operation is performed or newly authorized.
5. Review retained Lint items and any actual discovered runtime failures without policy/gate waiver.
   Protected-source fixes require exact reproduction, failing regression and separate approval.

**Recommendation: Ready for owner publication/review, acceptance pending.** This means the local
implementation/audit can enter review, not merge-readiness or formal acceptance. I-T16 is locally
PASS; all platform-combined requirements retain unresolved evidence. No demonstrated critical
integration defect was reproduced by available checks. AAB split omission is verified artifact
behavior and remains an explicit release-mode blocker/owner decision, not an invented runtime PASS.
Stop after I12. No next phase or automatic publication.

## Phase I commit inventory at I12 start

```text
610f4b1 feat(app): add retained host ownership and navigation (I01-I02)
17a6c4e feat(app): connect Library and Settings launcher shell
b4963e7 feat(app): integrate retained SAF picker and Library (I03-I04)
86ee91c docs(app): record SAF integration evidence and I05 handoff
f6e9e18 feat(app): connect Reader opening and safe retirement (I05)
8a21cb5 docs(app): record I05 Reader validation and lifecycle handoff
9ed9c2f feat(app): coordinate Reader lifecycle and memory policy (I06)
a33c937 docs(app): record I06 lifecycle evidence and restoration boundaries
66be7ed feat(app): restore eligible navigation and registered Reader state (I07)
991084f docs(app): record I07 restoration evidence and runtime settings handoff
8634d96 feat(app): apply confirmed settings locale and Reader window effects (I08-I10)
3883f34 docs(app): record I08-I10 presentation evidence and I11 handoff
d3dd071 fix(app): harden root reconciliation and Reader cleanup recovery (I11)
f51cc42 docs(app): record I11 recovery matrix and final-validation handoff
```

The final documentation commit adds this record, the local PR material and README/status/roadmap/
handoff updates; Git history and the delivery response identify its full SHA. All previous dated
evidence is preserved. No logs, APKs, PDFs, generated execution/coverage files or local SDK paths are
introduced as repository configuration.
