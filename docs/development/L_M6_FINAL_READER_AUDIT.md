# Phase L — L-M6 final Reader audit — 2026-11-02

**Standalone technical scope ready for owner review; NOT MERGE-READY /
NOT FINALLY ACCEPTED.** No demonstrated production defect requiring a source
correction was found within this audit's source/evidence scope. This is not a
claim that the application is defect-free.

## Revision and scope

Audited feature: `c417db8c17f9b8174f0be37c168a204c88c1cf34` on
`feat/reader-advanced-l`, dedicated Phase L worktree, initially clean.
Original recorded base and current merge base:
`7f00a793a06e988859dbefc3e21814c21ec727dd`.
Cumulative reviewed diff: **25 commits / 34 files**, before these audit documents.
Remote L matched the audited revision. Fetched main:
`c645e07656151b8e32927ed8a634b4f7ae6a0e78`, one commit ahead of the base (Phase K,
PR27); relationship main...L is 1/25. No branch was switched, merged or rebased.

Reviewed AGENTS, testing/completion/change-control policies, L-M1 with its R4
supersession notice, L-M2–L-M4 and R1–R5 evidence, current UI/seek/retention logic,
controller guard, test additions and cumulative file ownership. Original files
outside the changed code remain authoritative. No global status/policy update
is needed to record this branch-specific, conditional readiness decision.

## Implementation/acceptance traceability

S = ReaderScreenTest, I = ReaderInteractionTest, G = ReaderGuardedNavigationTest,
Q = ReaderSeekTest, N = ReaderNativeIntegrationTest, H =
ReaderHostNativeIntegrationTest. Exact package/source paths are under
app/src/{test,androidTest}/java/com/abdev/partituraspdf/{reader,app/integration}.
Implementation files below are under the production reader package.
Automated status refers to applicable baseline owner CI evidence and local JVM
results, not an independently parsed Android XML inventory. Manual qualifiers
remain acceptance gates, even where automated evidence passes.

| Requirement | Implementation | Exact test anchors | Acceptance/evidence |
|---|---|---|---|
| L-M1-T01 exact 25/50/25 | ReaderInteraction.readerTapZone, ReaderScreen single detector | I.exactQuarterZonesExcludeEndpointAndKeepLegacyClassifierUnchanged; I.proportionalClassifierRejectsInvalidInput; S.quarterBoundariesAndHiddenKeyboardControlsKeepOriginalNavigation | JVM + owner UI/CI PASS |
| T02 bounded endpoints/one dispatch | Step existing controller; terminal gate | S.edgeTapsNavigateButEndpointsNeverWrap; I.gestureCompletionIsSingleUseAndCancellationIsTerminal | Automated PASS |
| T03 initial/manual chrome, no timer | ReaderScreen retain Boolean and center action | S.centerTogglesWithoutViewportRenderLeaseOrTimeoutChanges | Owner UI/CI PASS |
| T04 same-intent lifetime/new intent reset | retain(state.intent), ordinary menu/seek state | S.hiddenPreferenceSurvivesFitPageAndZoomButNewIntentShowsToolbar; S.realActivityRecreationRestoresHiddenChromeForSameIntentOnly; S.freshPresentationEntryStartsVisibleEvenForSameIntentValue | Automated PASS; manual rotation PENDING |
| T05 stable viewport/no render for chrome | full-size page + sibling overlays | S.seekChromeTogglePreservesFrameViewportAndRendering; N.f_nativeSurfaceAndCompositeToolbarToggleWithoutNewRendering | Owner aggregate/native CI evidence; physical compositor/manual review separate |
| T06 controls intercept page input | overlay hit regions and Slider | S.toolbarTouchDoesNotLeakAndHiddenRecoveryRemainsReachable; S.seekDragOnlyPreviewsUntilReleaseAndConsumesPointer | Owner UI PASS; modal requirements superseded by R4 |
| T07 drag/pinch/consumed/canceled input | ReaderGestureGate + one pointer detector | I.invalidSamplesSlopAndPointerHistoryCannotRecoverIntoTap; S.dragAndPinchDoNotTurnPages; S.canceledAndOutOfBoundsMotionNeverToggleChrome | JVM + owner UI PASS |
| T08 direct selection/mapping | ReaderPageSeek normalized bounded integer mapping, Slider steps=0 | Q.normalizedMappingIsBoundedForAllSupportedCounts; S.onePageSeekIsDisabledWithoutExtraWork; S.seekFirstLastUsesExactlyTwoGuardedActionsAndThreeAcknowledgments; N.g_hundredPageTransientSeekPreviewsWithoutNativeRenderAndCommitsOnce | Numeric input superseded; JVM and owner native PASS |
| T09 reject invalid target | readerSeekIndex finite/range/count checks | Q.normalizedMappingIsBoundedForAllSupportedCounts; Q.selectionValidationNeverNavigatesAndMapsFirstAndLastExactly | Numeric syntax cases NOT APPLICABLE after R4; replacement validation PASS |
| T10 stale/ineligible context | ReaderNavigationContext, readerSeekAction, atomic pageIfCurrent | G.sameIntentReplacementRejectsCapturedSessionThenAcceptsCurrentSession; Q.actionBuiltBeforeReplacementIsRejectedByAtomicControllerGuard; S.seekReplacementCancelsDragAndCannotNavigateNewIntent | JVM + owner UI PASS |
| T11 requested/presented/durable distinction | unchanged Presented protocol; host flush; current/preview labels | S.requestedPageIsNotReportedAsPresentedDuringPendingRender; Q.validJumpDoesNotCheckpointUntilNewGraphicalAcknowledgment; H.registeredHundredPageSeekFlushesDurablePositionAndReopensThroughHost | JVM + owner native/host PASS; durable assertion only after Back flush |
| T12 keyboard/focus/show-hide | page-local keys, options FocusRequester, slider semantics | S.optionsDismissalRestoresButtonFocusWithoutPageKeyLeakageOrRendering; S.seekKeyboardStaysBounded; S.quarterBoundariesAndHiddenKeyboardControlsKeepOriginalNavigation | Automated PASS; TalkBack/manual focus PENDING; dialog Escape/IME obsolete |
| T13 hidden recovery | independent status/error overlay | S.toolbarTouchDoesNotLeakAndHiddenRecoveryRemainsReachable; S.terminalErrorRemainsReachableAtLargeFontInShortViewportWithoutDeadRetry | Owner automated baseline PASS; real external-provider behavior separate |
| T14 fit/zoom/pan/physical sizing | unchanged geometry/model, onSizeChanged retained | I.zoomAndPanClampToContentAndResizeDoesNotLeavePageUnreachable; S.seekLargeFontAndRealResizeRemainUsable; H.persistedDefaultFitAndLocalMenuRemainDistinctWhileConfirmedChangesStayInSameNativeSession | Automated PASS; actual rotation/manual/device calibration PENDING |
| T15 native SurfaceView overlay/one lease | unchanged AndroidView/presenter, overlay siblings | N.f_nativeSurfaceAndCompositeToolbarToggleWithoutNewRendering; N.c_nativePostedPixelsSurviveOriginalLeaseRecycling; N.g_hundredPageTransientSeekPreviewsWithoutNativeRenderAndCommitsOnce | Owner native evidence; no independent human compositor/flicker attestation |
| T16 background/trim/Back/retire/recreate | existing host route ownership and graphical barriers | H.genuineActivityBackgroundFlushesAndResumeReopensWithoutNewUserIntent; H.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen; N.e_quiescenceDisposesLateRealNativeOutputWithoutPresentationOrAnotherOpen | Existing aggregate CI + specific new host PASS; OS process death not inferred |
| T17 languages/layout/accessibility | five new keys, four bundles, 48dp thumb/buttons | S.seekLargeFontAndRealResizeRemainUsable; S.narrowLargeFontControlsAndDarkThemeRemainReachable; localization validator | Resource/automated PASS; linguistic, TalkBack, physical portrait/landscape PENDING |
| T18 architecture/scope | read-only feature projection; narrow authorized L-M3A guard/action | cumulative source diff, fixture validator and file inventory | No new renderer/write/schema/dependency/host production API. Controller/action change explicitly authorized by L-M3A, not silently introduced |

## Opening, lifecycle, persistence and resources

Registered and transient paths remain distinct; no seek automatically registers
a document. H.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration
checks that boundary. Native N uses real isolated rendering and test-owned route
scheduling, with no durable writer. H uses production host/features, Room and
positions with injected file capabilities; its new registered test asserts durable
page99 only after production Back/retirement and then fresh-controller/session
reopening. Neither is genuine external SAF picker/grant evidence.

The guard runs under the controller dispatch lock, compares published intent/
session plus authoritative active session information, validates bounds, then
uses existing requestPage. Rejected actions do not synthesize failure/checkpoint/
render. Frame retirement, synchronous software draw, Presented capability and
lease ownership are untouched. Native cleanup asserts bitmap recycling, descriptor
closure and unchanged PDF hashes. Rapid/canceled/replaced work has both JVM and
native coverage; no successful presentation is inferred solely from render return.

Compose retain is in-memory, keyed by complete intent equality; only the chrome
Boolean is retained. Options, focus return and unfinished seek reset on recreation.
No URI/frame/session capability is serialized. Actual Activity recreation tests
use a synthetic test-owned controller; production-host lifecycle evidence remains
separate. This distinction limits the restoration claim until manual testing.

## Evidence ledger and coverage

- L-M2 `513a0657eed44b291c381b0e10781898e06a0155` and L-M3A
  `2b1186aa1c6e3e345755cb1aac20d8e18497494c`: local JVM/coverage histories,
  not newly executed Android evidence. Historical numeric parser is removed.
- L-M3C/R5 `e0bcd15d3c84aac43845a3313c705c990878d5f6`: owner acceptance,
  48/48 API26 UI and CI117 SUCCESS; manual slider device unspecified.
- L-M3D `b79850758e579db0987b3cdac389d818c20c72a7`: owner 52/52 API26 UI,
  CI118 all nine jobs SUCCESS; manual rotation expressly unconfirmed.
- L-M4 `c417db8c17f9b8174f0be37c168a204c88c1cf34`: owner both new native/
  host methods PASS with zero failures/errors/skips; CI119 all nine jobs SUCCESS.
  See [new addendum](L_M4_EXECUTION_ADDENDUM.md) and its CI URL.
- CI/job findings above are supplied by the owner, not independently downloaded
  raw logs. Excluded controlled diagnostics, worker killing and broader destructive
  recovery are not assumed executed merely because CI is green.

Current retained matching-source JVM data was analyzed through existing external
JaCoCo tasks, preserving original local report directories:

| Scope | Lines | Branches |
|---|---:|---:|
| Interaction/classifier/gate | 40/41 (97.56%) | 117/118 (99.15%) |
| Current context/navigation | 6/6 (100%) | 6/6 (100%) |
| Controller + PageIfCurrent | 371/375 (98.93%) | 362/435 (83.22%) |
| Seek + target data class | 17/17 (100%) | 54/56 (96.43%) |

These pure/controller scopes exceed 90%/80%; normal JaCoCo compiler filtering and
existing denominators were not changed. Current context denominator is smaller
than the historical numeric-parser report because the owner removed the numeric
feature in R4, not because missed code was filtered. New Compose retention/focus/
interaction code has **no separately measured Android coverage** in the available
artifacts. UI/CI PASS is not numerical UI coverage; classify applicable coverage
closure for final policy review rather than silently waive it.

## Cumulative scope and integration risks

Observed production changes are confined to Reader screen/interaction/navigation/
seek/action/guard and four reader.xml files. Test changes are Reader-owned suites,
the authorized host integration additions and exact localization-count expectation.
No Phase M editing/writing, provider writes, write grants, new renderer, bitmap
cache, prefetch, database/schema, dependency, manifest, CI, production host or SAF
changes. Temporary R3 dialog policy and numeric UI were intentionally removed;
R1–R5 histories remain intact. No unexpected K/M commits were incorporated.

| Finding | Classification / next action |
|---|---|
| No demonstrated current production failure | NO CORRECTION JUSTIFIED; not a guarantee of defect absence |
| Main has Phase K absent from this branch | INTEGRATION GATE: integrate only under separate authorization; validate combined tree |
| Same localization assertion line expects main167 versus L160 | INTEGRATION GATE: current resource-key union is 172; reconcile actual combined resources/count and rerun parity, not blind replacement |
| Both branches edit ReaderHostNativeIntegrationTest | SHARED TEST FILE OVERLAP: preserve K's lazy-row action helper and L's new seek test; no merge simulation/conflict resolution performed |
| Five L-M1 metadata lines use Markdown two-space breaks | CUMULATIVE git diff --check reports trailing whitespace; historical formatting issue, not Reader defect. Cleanup before integration needs separate approval (see below) |
| Very large page-count Float resolution | KNOWN UI LIMITATION: endpoints bounded; not every page addressable by one finite track coordinate. No demonstrated MVP failure or target requiring redesign |
| R1 trace logging in Android tests | TEST-ONLY, bounded and synthetic; not production telemetry or memory/performance evidence |
| No Phase-L matched performance budgets/problem | L-M5 NOT AUTHORIZED: no speculative optimization, caching or resolution changes justified |

No actual merge or combined-source CI has been performed. File overlap is not a
claim that all hunks conflict. Branch-local CI119 does not certify integration
against the fetched Phase K main. Formal main integration remains unauthorized.

## Outstanding acceptance ledger

| Obligation | Status / implication |
|---|---|
| Manual rotation and hidden/visible chrome restoration after L-M3D | PENDING, required manual acceptance; automated recreation PASS is not a substitute |
| Physical Android8.1/API27 tablet | PENDING, required by testing/completion policy before functional phase acceptance; CI API27 is not physical evidence |
| TalkBack, focus ergonomics, contrast, es/ca/en linguistic/layout review | PENDING human acceptance; J08 broad UX audit remains deferred to Phase R without waiving new Reader accessibility |
| Real external SAF/provider behavior | EVIDENCE LIMITATION; injected provider/native file tests do not grant genuine authorization acceptance |
| Genuine OS process death / broader authorized worker-loss recovery | Separate evidence/authorization boundaries; simulated saved bundles and retire/reopen do not prove these |
| Quantitative latency/memory budgets, physical calibration | No approved violation or L-specific before/after measurements; not PASS. Carry policy obligation/owner decision forward; L-M5 optional implementation not authorized |
| Backup/D2D/editing/Phase M integration | Outside independent Phase L; no new support claimed or silently installed |
| Latest combined main CI, scoped coverage closure and whitespace reconciliation | Required before merge readiness under policy; no exception inferred from milestone completion |

Recommendation: owner may review/consolidate the standalone implementation, but
must separately resolve required acceptance and integration gates before declaring
Phase L accepted or merge-ready. No new performance work is evidence-justified.
A PR, merge, tag/release or next milestone requires separate authorization.

## Local audit execution ledger

Read-only git status/branch/HEAD, fetch main/L, merge-base, rev-list, cumulative
log/name/diff review, source/test/policy reads and resource-key union inspection.
No tests, sources, fixtures or policies were modified by this audit.

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
python3 -B -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

Build/instrumentation compilation/Lint PASS; JVM/contract gates UP-TO-DATE with
matching-source retained **651 PASS**, zero failures/errors/skips. Newly executed
Python **492 PASS**, zero failures/skips; localization PASS and **15 fixture PASS**
including structure/render checks. No fresh Android or JVM execution invented.
API26 locally NOT RUN, no authorized runnable emulator or /dev/kvm; owner CI119
is distinct historical exact-source evidence. No new CI access/dispatch occurred.

Coverage rerun used three external /tmp/l-m6-{m2,m3a,seek}.init.gradle scripts
and existing lM2InteractionVerify/lM2NavigationVerify/lM3aVerify/lM4Report tasks,
offline/no-configuration-cache. Initial combined invocation FAILED Gradle task
configuration because the temporary seek report lacked an explicit test-task
dependency. Added that dependency in /tmp only; final combined coverage PASS.
No production/Gradle policy fix or application test failure was concealed.
Logs: /tmp/l-m6-{gradle,python,localization,fixtures,coverage,final-coverage}.log;
reports /tmp/l-m6-coverage/, retained JVM XML app/build/test-results/.

New-document diff/link/privacy checks PASS. **Cumulative base..feature whitespace
check FAILS** on the five historical Markdown hard-break lines listed above.
Automatic approval review rejected the proposed tracked L-M1 whitespace edit as
outside the audit scope. It was not executed or bypassed; L-M1 remains unchanged.
Separate owner approval is required for that cleanup. No raw logs, credentials,
private documents or diagnostic ZIPs are added to Git. This audit commits only
the L-M4 addendum and this report. Documentation publication is not integration
approval or a new test run. No PR, merge, rebase, tag, release or Phase M development.
