# L-M4 advanced Reader integration and regression — 2026-11-02

**READY FOR OWNER API26 VALIDATION — NOT RUNTIME ACCEPTED.**
Branch: `feat/reader-advanced-l`; baseline:
`b79850758e579db0987b3cdac389d818c20c72a7`.
Test implementation revision: `444a2a10e34206dbcf9e69a434656fe0beff36cb`.
Local checks refer to these exact test bytes; the documentation commit is not a new run.
See [L-M3D execution addendum](L_M3D_EXECUTION_ADDENDUM.md). Baseline owner evidence
is 52/52 ReaderScreenTest, CI118 all nine jobs including API26/27/36, JVM651,
Python492 and 15 fixtures PASS. Manual rotation/chrome restoration is unconfirmed.
No historical execution report is overwritten and no new CI run is claimed.

## Initial gap assessment and provenance

Source inspection found no conclusively demonstrated production defect. Existing
coverage is substantial; two genuine integration gaps remain in the advanced UX:
held seek dragging with an actual isolated native PDF, and seek completion through
the production host into durable registered-position flush and fresh reopening.
Synthetic Compose results cannot establish either. Only two tests are added; no
new framework, production code, fixture, dependency or performance budget.

Abbreviations below resolve to actual repository test classes:
S = reader.ReaderScreenTest; N = reader.ReaderNativeIntegrationTest;
H = app.integration.ReaderHostNativeIntegrationTest; P =
app.integration.AppPersistenceIntegrationTest. JVM classes are under reader/.
Owner CI118 is baseline aggregate evidence, not independently extracted per-method
JUnit verification for every listed class. In particular, controlled diagnostics
may be excluded; their source existence is not execution evidence. New methods
are compiled **NOT RUN**. N uses genuine isolated rendering with a test-owned
route; H uses production host/features, real isolated engine, Room/preferences and
injected file capabilities. Neither is genuine external SAF authorization.

## Requirement-to-test matrix

| Area | Existing exact methods / capability | Gap/action |
|---|---|---|
| Registered/transient opening | H.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration; H.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition | Existing real host paths; new registered seek/reopen below |
| 25/50/25 and endpoints | S.quarterBoundariesAndHiddenKeyboardControlsKeepOriginalNavigation; S.edgeTapsNavigateButEndpointsNeverWrap; ReaderInteractionTest pure geometry | No duplicate zoning test needed |
| Direct seek | S.seekDragOnlyPreviewsUntilReleaseAndConsumesPointer; S.seekFirstLastUsesExactlyTwoGuardedActionsAndThreeAcknowledgments | New N.g_hundredPageTransientSeekPreviewsWithoutNativeRenderAndCommitsOnce supplies native held-drag boundary |
| Graphical acknowledgment | S.requestedPageIsNotReportedAsPresentedDuringPendingRender; S.seekPendingRequestDoesNotClaimPresentation; ReaderGuardedNavigationTest.validGuardedJumpSchedulesOnceAndWaitsForGraphicalAcknowledgment | Distinguish render/request from actual posted consumer; no authority changes |
| Zoom/fit/resize | S.fitChangesPreservePageAndUseOneOpeningAuthority; S.tallWidthPageCanScrollToItsBottomWithoutPageTurnOrNativeRerender; S.resizingReportsUsableViewportOnceAndIgnoresDrawStatusChanges; H.persistedDefaultFitAndLocalMenuRemainDistinctWhileConfirmedChangesStayInSameNativeSession | Existing bounds/real resize; manual physical calibration pending |
| Persistence | H.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen; H.syntheticSavedBundleReopensDiskRegisteredScoreAtDurablyPresentedPageWithFreshNativeSession; P.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization | New H.registeredHundredPageSeekFlushesDurablePositionAndReopensThroughHost connects seek to durable flush/reopen |
| Lifecycle | H.genuineActivityBackgroundFlushesAndResumeReopensWithoutNewUserIntent; S.realActivityRecreationRestoresHiddenChromeForSameIntentOnly; S.recreationDismissesOptionsAndResetsUnfinishedSeekWithoutNavigation | Actual Activity recreation versus synthetic host/saved-Bundle reconstruction explicitly distinct; manual rotation pending |
| Recovery | S.canceledOpeningOffersAnActualExplicitRecovery; S.recoverableErrorDispatchesRealRetry; H.injectedProviderUnavailableIsContextualAndRetryRevalidatesBeforeRealNativeOpen; H.injectedCheckpointFailurePreservesRealEarlierPositionAndSafeReopenAfterEmptyRouteVerification | Provider failure injection is not genuine external-provider acceptance |
| Native ownership/cleanup | N.c_nativePostedPixelsSurviveOriginalLeaseRecycling; N.e_quiescenceDisposesLateRealNativeOutputWithoutPresentationOrAnotherOpen; N cleanup checks each bitmap/descriptor/session/source | Reuse lifecycle cleanup for new native seek; no benchmark or budget added |
| Rapid operations | N.b_hundredPagesRestoreClampAndRapidNavigationKeepOneNativeLease; ReaderSchedulingTest.rapidRequestsKeepOneActiveRenderAndOnlyTheLatestPendingPage; ReaderSessionLifetimeTest.supersessionDuringRenderDiscardsOldLeaseWithoutNativeCancellation | Existing native and JVM cancellation scopes; no cache/prefetch introduced |
| Accessibility/focus | S.optionsDismissalRestoresButtonFocusWithoutPageKeyLeakageOrRendering; S.seekKeyboardStaysBounded; S.seekLargeFontAndRealResizeRemainUsable; S.toolbarTouchDoesNotLeakAndHiddenRecoveryRemainsReachable | Baseline automated evidence; human TalkBack/linguistic/layout review pending |
| Compatibility | Owner CI118 API26/API27/API36 at baseline SHA | New test revision requires fresh owner execution/CI; no runtime inferred from compilation |

## Two added integration methods

N.g_hundredPageTransientSeekPreviewsWithoutNativeRenderAndCommitsOnce opens the
immutable canonical fixture05 (100 pages) through the existing isolated engine.
It waits for real graphical acknowledgment, then performs the R5 timed pointer
movement while holding DOWN. Preview must say page100 while requested page,
frame, lease-production count and route-displayed history remain at page0. UP
must produce exactly one additional lease/rendered target, [0,99] route display
history, same SurfaceView, one opening, one active render and maximum one lease.
The existing @After barrier verifies all leases recycled exactly once, descriptors
closed once, native owners closed and copied PDFs matching canonical SHA256.
This is a transient request; the test route's displayed callback is scheduling
only, **not a durable checkpoint or registry assertion**.

H.registeredHundredPageSeekFlushesDurablePositionAndReopensThroughHost explicitly
registers fixture05, opens via production host, then selects the last page using
the accessibility SetProgress action. This complements rather than replaces the
native pointer-drag test. It requires real VISIBLE state and PixelCopy ink/paper
checks, requested/presented page99 and unchanged native session/open count. Only
**after production Back flush/retirement** does it assert durable position99 and
matching descriptor open/close counters. Reopen must create a fresh controller/
native session with the same registered DocumentRef and restore presented page99.
Source bytes must remain unchanged. These test provider/graph capabilities do not
establish genuine SAF grants, physical display perception or OS process death.

Registered durable restore and native-transient scheduling are intentionally
separate evidence types. No scheduled result is relabeled durable. Existing
failure/replacement/graphical gate tests remain enabled. Worker killing and
controlled diagnostic methods are not newly selected or executed here.

## Available local validation

Existing Linux x86_64/JDK25/SDK37/Gradle9.8/AGP9.5 toolchain:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
python3 -B -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

Build, full instrumentation compilation, Lint and existing contract coverage gate:
PASS. JVM task UP-TO-DATE: retained matching-source XML **651 PASS**, zero failures/
errors/skips; no fresh JVM execution claimed. Newly executed Python **492 PASS**,
zero failures/skips. Localization PASS; **15 fixture PASS** with checksum/structure/
render verification. Scope/whitespace PASS. No new production logic or coverage
denominator. Existing pure seek scope measured from matching JVM data remains
**16/16 lines, 54/56 branches** (100%/96.43%) via existing external reporting:

```bash
bash ./gradlew -I /tmp/l-m4-coverage.init.gradle :app:lM4Report \
  --offline --no-configuration-cache --console=plain --max-workers=4
```

New native/host integration runtime and Android coverage are **NOT RUN / NOT
MEASURED**. No synthetic percentages or memory measurements are manufactured.
Logs: /tmp/l-m4-gradle.log, /tmp/l-m4-python.log, /tmp/l-m4-localization.log,
/tmp/l-m4-fixtures.log, /tmp/l-m4-coverage/report.xml; retained JVM XML in
app/build/test-results/testDebugUnitTest. Generated logs/ZIPs are not Git inputs.
Documentation commits do not represent new test execution.

## Acceptance and remaining risks

API26 runtime **NOT RUN / BLOCKED**, no authorized runnable emulator or /dev/kvm.
The owner should run the two exact new methods on Windows/API26, then broad CI at
the published source. Full results must include existing ordinary suites and
preserve controlled diagnostic selection rules; do not run excluded worker-loss
operations merely for this milestone. Preserve original JUnit output, method
inventory, logs, source/device and built/installed APK attribution; classify
missing/skipped/failed evidence accurately.

The new tests are ready for owner execution, not proven integration PASS. Native
100-page pointer synchronization, host checkpoint-flush/reopen and cleanup need
actual device verification. Manual L-M3D rotation/chrome behavior remains pending,
along with TalkBack, es/ca/en linguistic/layout review, physical Android8.1 tablet,
genuine external SAF/provider scenarios, real process-death acceptance and any
previously deferred Phase L obligations. New-revision API27/API36 CI is pending.
No quantitative performance budget or physical-device memory/latency acceptance
is inferred from finite resource assertions or CI. Optional L-M5 remains separately
authorized; no optimization is justified here. No source correction or protected
component change was required. No PR, merge, rebase, tag, release, L-M5, L-M6 or
Phase M development.
