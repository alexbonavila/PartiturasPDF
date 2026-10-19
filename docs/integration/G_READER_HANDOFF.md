# Phase G — single-page reader integration handoff

## A. Purpose, scope and evidence identity

Phase G is the read-only, single-page local PDF reader. It supplies a route-local controller,
bounded rendering, Compose controls, software-surface presentation, page navigation, typed
recovery and Spanish-first accessibility resources. It adds no PDF writes, editor, annotation,
Drive, SAF authorization flow, production Activity/navigation wiring or alternative PDF engine.

Implementation/test revision: `89a200c44b971f50b80eb4cba7809c26a07603c0`.
Base inspected on 2026-10-18: `facf3084d60e8727037ac701213d20320c5b5cd1` (`main`).
Prompt 6 adds this document without changing the implementation. Its commit and exact published
head are recorded in the delivery report and Git history; no hosted PASS is attributed to a
future documentation commit. Previous implementation commits are preserved.

This is an integration contract and evidence register, not owner acceptance or merge approval.
Physical/manual and hosted evidence remain distinct. Existing normative policies are unchanged.

## B. Exact implemented entry points

Types belong to `com.abdev.partituraspdf.reader`; dependencies are the existing core/PDF APIs.
Constructor and member signatures:

```kotlin
class ReaderController(
    ownerScope: CoroutineScope,
    private val route: ReaderRoute,
    private val renderCeiling: RenderLimits,
    private val heapAvailability: () -> HeapAvailability,
    initialFit: PageFit,
)

val state: StateFlow<ReaderUiState>
fun open(intent: ReaderOpenIntent)
fun dispatch(action: ReaderAction)
suspend fun quiesce(trim: ReaderTrim): Outcome<Unit>
fun resume()
suspend fun retire(): Outcome<Unit>

data class ReaderOpenIntent(val sequence: Long, val request: DocumentOpenRequest)
data class ReaderViewport(val widthPx: Int, val heightPx: Int)

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onAction: (ReaderAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)
```

`ReaderOpenIntent` requires `sequence >= 0`. I issues a new, increasing sequence for each new
user opening intent. Recomposition/configuration recreation reuses the existing intent/controller.
Older sequences are ignored; reusing a sequence for a different request is invalid. A new
process creates new owners; never restore live sessions, frames or transient Android capabilities.

Current immutable snapshot (including feature-internal recovery capabilities):

```kotlin
data class ReaderUiState(
    val phase: ReaderPhase = ReaderPhase.IDLE,
    val intent: ReaderOpenIntent? = null,
    val session: ReadSessionInfo? = null,
    val requestedPage: PageIndex? = null,
    val presentedPage: PageIndex? = null,
    val viewport: ReaderViewport? = null,
    val viewportGeneration: Long = 0,
    val fit: PageFit = PageFit.PAGE,
    val frame: ReaderFrame? = null,
    val failure: ReaderFailure? = null,
    val checkpoint: ReaderCheckpoint = ReaderCheckpoint.None,
    internal val cancellationRetry: ReaderRetry = ReaderRetry.NONE,
    internal val checkpointConsumer: ReaderPresentation? = null,
)

sealed interface ReaderAction {
    data class Viewport(val value: ReaderViewport) : ReaderAction
    data class Fit(val value: PageFit) : ReaderAction
    data class Page(val index: Int) : ReaderAction
    data class Step(val delta: Int) : ReaderAction
    data object Retry : ReaderAction
    data class Presented(val presentation: ReaderPresentation) : ReaderAction
}
```

`Step` requires -1 or +1 and resolves relative navigation atomically in the controller.
`ReaderScreen` supplies actual positive content-pixel viewport changes, excluding its chrome.
Only the current graphical consumer supplies `Presented`; I must not fabricate this action.
Neither the snapshot nor its frame/capabilities is navigation or saved-process state.

Phases: IDLE, OPENING, READY, RENDERING, RENDERED, VISIBLE, ERROR, CANCELED, QUIESCED, RETIRED.
`ReaderFailure(stage, error, retry)` retains typed `AppError` and OPEN/PLAN/RENDER/PRESENTATION
stage. Recovery is NONE/OPEN/RENDER/PRESENTATION, with live-capability eligibility checked by G.
Checkpoint states are None, Scheduling, Canceled and `Unconfirmed(result: Outcome<Unit>)`.

## C. Dependencies supplied by Phase I

I obtains one `ReaderRoute` through the existing application composition/foundation, and supplies:

- A retained controller-owning coroutine scope, preferably the host's Main scope. G owns a child
  SupervisorJob; it does not cancel the supplied parent. Await retirement before parent cancellation.
- The approved immutable render ceiling, intersected by G with session and heap limits.
- A fresh `HeapAvailability` supplier, using runtime maximum heap and current used heap; total
  device RAM is not a substitute. Do not freeze one startup snapshot for all allocations.
- Initial `PageFit` from the approved preference policy. Reader fit changes are feature-local;
  G does not persist settings or access a settings implementation.
- Stable registered or transient `DocumentOpenRequest` intents. Transient access must be reacquired
  after process death; it is not a fabricated permanent URI grant.
- State collection, `controller::dispatch`, and a host-owned back callback for `ReaderScreen`.

I delegates opening exclusively to `controller.open(intent)`: it must not independently call
`route.open()` for the same intent. Do not mutate/close the route externally while G is active.
No production integration Activity, lifecycle observer or service locator is installed by G.

## D. Route/session/bitmap ownership

I retains the route. The route owns its `PdfReadSession`; G borrows it and never directly closes
it. G calls route open/current/displayed as required, but not route background/close.
G exclusively owns each delivered `RenderedPage` lease and lends it to one graphical consumer.
Stale unpublished leases are closed; replacement waits for the previous consumer's safe detach.

`ReaderFrame` retirement prevents new consumers and awaits explicit graphical release.
`ReaderPresenter` consumes synchronously on `ReaderPageSurface` through a software Canvas from
`SurfaceHolder.lockCanvas()`, not a hardware bitmap display list. No persistent bitmap copy or
image cache is created in production. Detachment and drawing are serialized on the UI thread.
Only after drawing and `unlockCanvasAndPost()` can an eligible, current post callback acknowledge.
A discarded delivery does not consume the acknowledgement; subsequent eligible drawing can retry it.

This establishes the implemented lifetime protocol. JVM protocol tests pass; actual Android
Surface/PixelCopy execution remains NOT RUN here. Post completion is not physical scan-out,
proof against occlusion, universal GPU/OEM certification, or durable position storage.

## E. Host lifecycle operation order

Never use `runBlocking` on the main thread. Lifecycle operations suspend; they do not force
release after a timeout. Keep the owner scope alive until G's barrier completes.

1. **Initial open:** create/retain route and controller, collect state into `ReaderScreen`, supply
   one accepted intent. The screen reports its usable viewport; G plans/renders and the surface
   reports actual drawing. Composition itself does not open the route.
2. **Another document:** send a new increasing `ReaderOpenIntent`. G invalidates/cancels old work,
   waits for native completion and graphical detach, then delegates replacement to the route.
   I must not pre-close the borrowed session or open a competing route.
3. **Background:** await `controller.quiesce(ReaderTrim.BACKGROUND)`. Keep state delivery active
   until QUIESCED removes the consumer, or explicitly unmount the screen first. Do not stop a
   lifecycle-aware collector while leaving a consumer attached and then await its detach.
   After G's barrier, call and inspect `route.background()`. Apply the existing trim policy's
   closeSession decision and inspect `route.close()` when closing is required.
4. **LOW_MEMORY:** await quiescence; pending work is cancelled and the page is released. The
   policy does not request closing the session. Remain paused until explicit recovery is safe.
   No prefetch or automatic replacement session runs in the background.
5. **CRITICAL:** await quiescence, inspect host flush and close results, and close the route as
   required by the current policy. Do not treat LOW_MEMORY and CRITICAL as identical.
6. **Resume:** first finish G quiescence and every host route mutation/flush. Then call `resume()`.
   G queries `route.current()` before reusing a session and checks both object identity and info.
   A closed route reopens via the retained intent and G's existing open mechanism; an unrelated
   session is rejected. Interrupted opens/renders and explicit failure recovery remain distinct.
   Same closed native objects must not be supplied again by the host.
7. **Activity recreation:** retain route/controller/intent where eligible. Dispose the old screen's
   consumer without retiring the controller; recreate the screen from its current StateFlow.
   The new consumer may attach after the old one detaches. Do not duplicate opening or retain an
   obsolete surface callback. Local visual transformations are screen state, not durable PDF state.
8. **Leaving/final disposal:** quiesce first, inspect background flush, await `retire()`, then close
   the route and inspect the result. Cancel the owning scope only after resource barriers/closure.
   A retired controller cannot be reused; later navigation creates a new controller as appropriate.

All trims currently cancel pending work and release the page. Only BACKGROUND/CRITICAL request
session closure. Obtain flags from `ReaderMemoryPolicy.trim`, not copied alternative constants.
Repeated quiescence/retirement is supported. Unexpected programming/cleanup defects propagate;
they are not converted into fabricated storage errors. Handle typed route failures explicitly:
a failed close does not imply that its native session is still usable or that writes rolled back.
The complete Activity-to-route lifecycle sequence remains Phase I integration, not tested end to end.

## F. Presented-page checkpoints

Requested page, rendered frame, presented page and checkpoint result are separate state.
Render completion and attachment alone never call `route.displayed(session, page)`.
Current-frame identity, active consumer, eligible view and completed software draw/post are required.
Stale/hidden/detached consumers are rejected. Repeated drawing and screen replacement do not
implicitly duplicate an uncertain checkpoint. Explicit retry requires the current valid draw.

The existing production route is the sole new SharedPreferences position authority. G never
reads/writes legacy Room reading positions or imports the concrete checkpoint implementation.
For registered documents, displayed schedules a coalesced asynchronous checkpoint. Success is
`Unconfirmed`, not a completed durable write. Synchronous scheduling failures remain observable;
storage failure can retry without rerendering the live page. Closed/stale sessions require reopening.
The ReaderRoute API exposes no asynchronous write-result stream to G. I must call and inspect
background/close flush results; G cannot infer deferred write success/failure through this interface.
Transient documents have no permanent writer and the UI states that no permanent position is saved.

## G. Memory policy and performance limits

Use the existing `ReaderMemoryPolicy` and `boundedRenderLimits`: one active route session, one
scheduled native render, one retained PDF page lease and zero adjacent-page prefetch.
The dynamic bitmap budget is min(16 MiB, maximum heap / 16, currently free heap / 4), converted
to ARGB pixel bounds and intersected with host/session dimension and pixel ceilings.
These are bitmap allocation bounds, not total process memory or proven 2 GB tablet performance.
Native PDF allocations, Android surface/window buffers, Compose and other application allocations
are additional. Do not enlarge the budget or add a cache to make benchmarks appear better.

Native acceptance tests use a tighter 512x512 ceiling (at most 1 MiB raw ARGB estimated).
They emit `ReaderNativeAcceptance` logcat JSON with API, fixture/session/page, heap, dimensions,
allocation bytes and render duration when executed. PixelCopy output copies are test-only.
No Android measurements were executed here: emulator RAM, heap, render latency and native/surface
memory remain unmeasured. No physical constrained-memory performance claim is made.

## H. UI and recovery

PAGE fits the whole page; WIDTH preserves aspect ratio and permits vertical scrolling to its end.
Visual zoom is 1x-4x, not native tiled/high-resolution zoom. Pan is clamped to content and resized
bounds. Page changes preserve zoom and reset pan; a new document resets the visual transformation.
The screen reports positive usable viewports and keeps chrome allocation stable to avoid render loops.

Previous/next controls and edge bands respect endpoints without wrap. Gesture arbitration excludes
pinch, drag, scroll and consumed events from page taps. Relative actions preserve rapid input.
Controls have at least 48dp targets, resource labels, selected fit states, page/total and zoom semantics,
and bounded accessibility actions. Keyboard shortcuts are local to page focus, avoid modifier-key
capture, and do not replace normal control/menu traversal. Large-font chrome/errors can scroll.
A bitmap of sheet music is not structured accessible musical text; no OCR is implemented.

Recovery matrix:

| Typed failure | Recovery / retained state |
| --- | --- |
| Recoverable permission/provider/I/O or invalidated-session conflict | Recheck access and explicitly reopen; do not reuse known-invalid native state. G does not launch authorization. |
| Render resource limit | Retry render with a valid session after resources/fit change; no automatic busy loop. |
| Checkpoint storage scheduling failure | Keep current graphical page; retry its current draw capability without claiming durability. |
| Malformed/protected/unsupported input, missing registration, reported deletion, input-size limit | No ineffective retry button. Back to host/document/access review; a genuinely new intent can open again. |
| Invalid page/render request | Choose a valid page/viewport/fit or go back; never submit the invalid native request. |
| Cancellation/programming defect | Preserve cancellation or propagate defects; never relabel them as benign storage errors. |

There are 48 `reader_` keys with identical Spanish definitions in default and values-es
`reader_spanish_first.xml`. No shared strings changed, no manual language selector was added.
Staged English/Catalan delivery remains deferred, not translated PASS. I owns device-locale application.

## I. Work reserved for Phase I

I supplies retained application composition, navigation/back stack, genuine Activity Result/SAF
capabilities, process restoration policy, preference-derived initial fit, automatic device locale,
Activity recreation/lifecycle/trim coordination and window keep-awake/immersive flags. It also owns
flush/close failure handling and full library-reader-settings journeys. None is installed by G.
Registered restoration uses the existing route; transient process restoration requires reacquired
access. No full application integration, independent external-provider acceptance or backup/restore
acceptance follows from component tests.

## J. Validation, publication and limitations

Local validation on 2026-10-18 rechecks the unchanged implementation/test baseline `89a200c` plus
this documentation. Raw local logs are temporary review artifacts; Git retains source/tests and
this handoff, not generated outputs. The final delivery identifies the document/publication SHA.

| Evidence | Result / limitation |
| --- | --- |
| Debug APK, instrumentation APK, release APK including R8 | PASS local build; not Android execution. |
| JVM JUnit | 355 executed, 0 failures/errors/skips; 87 reader tests. Existing result verifier used. |
| Python infrastructure | 256 PASS. |
| Lint | 0 errors, 21 existing warnings. |
| Localization | PASS: 48 identical Spanish reader keys; English/Catalan debt remains. |
| Canonical corpus | 15 PASS with host verifier; this is not Android PdfRenderer execution. |
| Production packaging | Debug/release APKs contain 0 PDFs; instrumentation APK contains 15 canonical PDFs. |
| Reader logic JaCoCo 0.8.14 | 478/486 lines (98.35%), 507/590 branches (85.93%). Full controller/frame/state/render-plan/interaction/presenter/recovery scope, no difficult logic excluded. |
| Android inventory | 307 methods compiled, including 36 G methods (30 screen + 6 native). Compilation is not discovery/execution. |
| Local Android API26 | BLOCKED: no device; emulator acceleration reports KVM requires vmx/svm. No software fallback or foreign AVD adoption. |
| Hosted API26/27/36 and mandatory CI | BLOCKED / NOT RUN from this environment. `api.github.com` CONNECT tunnel receives proxy 403. No run/artifact URL or hosted PASS exists in this evidence register. |
| Native Android/Surface/PixelCopy coverage | NOT RUN; no combined JVM/Android coverage or native performance is fabricated. |

Reproducible host commands (activate the existing cloud toolchain where needed):

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease \
  :app:lintDebug :app:testDebugUnitTest --rerun --max-workers=4 --console=plain
python scripts/verify_test_results.py app/build/test-results/testDebugUnitTest \
  --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
python -m unittest discover -s scripts/tests -v
python scripts/validate_localization.py
python test-fixtures/scripts/verify_fixtures.py
git diff --check
```

The actual existing `.github/workflows/ci.yml` supports a boolean `compatibility` input:

```bash
gh workflow run ci.yml --repo alexbonavila/PartiturasPDF --ref feat/reader-g -f compatibility=true
```

When API access is unavailable, the owner can use GitHub Actions -> CI -> Run workflow, choose
`feat/reader-g`, enable compatibility and run. The manual workflow runs mandatory jobs plus
compatibility-api27 (google_apis/x86) and compatibility-api36 (google_apis/x86_64); API26 uses
Google APIs/x86_64. A branch-only feature push does not trigger this main/PR workflow. Create a
Draft PR targeting main or dispatch the supported workflow; never infer execution from a push.

On the final published SHA, inspect build, lint, unit-tests, instrumented-api26, localization,
pdf-fixtures and quality-gate. Download `instrumentation-api26-reports`,
`instrumentation-api27-reports`, and `instrumentation-api36-reports` when available. Inspect
original JUnit XML, counts, failures/errors/skips, all 36 G methods, native/PixelCopy results,
logs and commit identity. A run summary alone is not sufficient. After any correction, older
runs no longer certify the new head. Keep the PR Draft; no merge or auto-merge is authorized.

### G-T01 through G-T12 evidence matrix

All JVM evidence is host-only (API not applicable). All Android entries below refer to API26,
API27 and API36 pending execution. Hosted run URL for every row: **none available / API blocked**.
A partial JVM PASS never certifies the full Android requirement.

| ID | Production component | Actual test / evidence | JVM | Android | Status / remaining limitation |
| --- | --- | --- | --- | --- | --- |
| G-T01 | ReaderController.open | ReaderOpeningTest.registeredAndTransientOpenOnlyThroughRouteAndRespectRestoredPage | PASS | NativeIntegration.a_readableCanonicalCorpusUsesNativeRenderingAndGPublication compiled only | PARTIAL; full host/registration/SAF integration remains I. |
| G-T02 | Controller page state, Screen counter | ReaderOpeningTest; ReaderNativeIntegrationTest.b_hundredPagesRestoreClampAndRapidNavigationKeepOneNativeLease; ReaderScreenTest.edgeTapsNavigateButEndpointsNeverWrap | PASS logic | NOT RUN | Native restoration is a test-route scenario, not production durable restoration proof. |
| G-T03 | RenderPlan, Controller, PageSurface | ReaderNativeIntegrationTest.a_readableCanonicalCorpusUsesNativeRenderingAndGPublication | Planning PASS only | NOT RUN | Real PdfRenderer publication must execute. |
| G-T04 | ReaderRenderPlan | ReaderRenderPlanTest.dynamicHeapPolicyCanBeStricterThanNativeLimits and host/session limits | PASS | NativeIntegration.a bounds compiled only | Android allocation observations pending. |
| G-T05 | Controller worker, Frame | ReaderSchedulingTest.rapidRequestsKeepOneActiveRenderAndOnlyTheLatestPendingPage; NativeIntegration.b | PASS | NOT RUN | One-render/lease observations on native renderer pending. |
| G-T06 | Controller cancellation, Frame, Presenter | ReaderLifecycleTest.quiescenceWaitsForDelayedNativeCancellationAndClosesLateSuccess; NativeIntegration.c/e | PASS protocol | NOT RUN | No JVM claim of Android recycling safety. |
| G-T07 | Presenter, PageSurface, Controller Ack | ReaderPresentationTest.renderDoesNotAcknowledgeAndOnlyAttachedConsumerCanReportPresentation; ReaderScreenTest render/attachment/hidden-surface cases | PASS protocol | NOT RUN | Draw/post is not scan-out or durable persistence. |
| G-T08 | Controller lifecycle | ReaderLifecycleTest.everyTrimReleasesPageAndHostOwnsFlushAndSessionClosure; ReaderScreenTest.trimCanAwaitGraphicalDetachWithoutBlockingMainAndResumeOnlyAfterHostFinishes | PASS simulation | NOT RUN | Activity coordination belongs to I. |
| G-T09 | G pipeline / existing native adapter | ReaderNativeIntegrationTest.a; existing AndroidPdfFixturesInstrumentedTest corpus/100-page/geometry cases | Host fixture verifier PASS | NOT RUN | Unknown raw rotation/annotation support is not fabricated. |
| G-T10 | ReaderRecovery, Controller | ReaderRecoveryTest; ReaderNativeIntegrationTest.d_invalidRequestsAndClosedNativeSessionCannotBeReusedOnResume and z_protectedAndCorruptInputsSurfaceActualAdapterFailuresWithoutPublishingABitmap | PASS recovery | NOT RUN | Protected-input categories limited to actual adapter outcomes. |
| G-T11 | ReaderScreen, Interaction | ReaderInteractionTest.scrollPanAndPinchNeverBecomeEdgeTapsOnRelease; ReaderScreenTest.keyboardShortcutsAreLocalToPageFocusAndRespectModifierKeys plus semantics/fit/font cases | PASS pure logic | NOT RUN | Visual review/TalkBack/physical behavior not certified. |
| G-T12 | Read-only G boundary, packaging | Complete source diff; canonical SHA verifier; debug/release APK ZIP inventory; native test cleanup source hashes | PASS static/host | Runtime hash guard NOT RUN | No production PDFs or new writes; no full host flow claim. |

`NativeIntegration.a/b/c/d/e` in the table abbreviates the methods in
`ReaderNativeIntegrationTest`, not another framework. `c` is
`c_nativePostedPixelsSurviveOriginalLeaseRecycling`; `e` is
`e_quiescenceDisposesLateRealNativeOutputWithoutPresentationOrAnotherOpen`.

### Deferred acceptance and decision

- PHYSICAL TABLET ACCEPTANCE: DEFERRED BY OWNER / NOT RUN. Do not request or operate a personal device.
- C06 destructive backup/reinstall/restore and genuine transfer acceptance: DEFERRED / NOT RUN;
  separate authorization is required. This handoff does not execute or waive it.
- External SAF providers: not independently verified by G component tests.
- Manual TalkBack, screenshots, visual/contrast/orientation review: NOT RUN here.
- Staged English/Catalan reader translations: DEFERRED mandatory delivery.
- Physical 2 GB memory/performance and broad OEM/GPU safety: unmeasured.
- Phase I integrated navigation, authorization and lifecycle: not implemented by G.

No new blocking production defect was reproduced by available local checks. Native/graphical
acceptance remains incomplete, not PASS. The branch/document can be reviewed; mandatory latest-head
hosted CI and applicable compatibility evidence remain required before any merge recommendation.
Only the human owner may approve and merge. No branch protection or policy exception is implied.

## Current merged F/G/H snapshot — 2026-10-19

Verified Git baseline: `adcca2bd07693e0d985eee1c611bc931be1dfed1`. F merged in [PR #18](https://github.com/alexbonavila/PartiturasPDF/pull/18), G plus isolated native renderer in [PR #19](https://github.com/alexbonavila/PartiturasPDF/pull/19), H in [PR #20](https://github.com/alexbonavila/PartiturasPDF/pull/20). This section supersedes earlier *current* claims below that these components or their foundation/policy PRs remain unimplemented/unmerged. Dated historical results remain unchanged.

- **F:** folder browsing, bounded registration lookup, explicit registration, metadata/favorites and registered/transient open requests, with Compose presentation. No global search or file mutations.
- **G:** bounded one-page reading, page navigation, PAGE/WIDTH fitting, visual-only zoom, lifecycle/frame ownership and presented-page checkpoint scheduling. Production native work is isolated in a nonexported `:pdf` service. No editing, annotations or adjacent-page prefetch.
- **H:** theme SYSTEM/LIGHT/DARK and page-fit PAGE/WIDTH settings, typed commands, authoritative observation, explicit retries and uncertain-write reconciliation. No manual language selector.
- **I remains unimplemented:** starter `MainActivity` has no real F/G/H navigation, picker Activity Results, retained host lifecycle or global theme/fit/locale effect wiring. Implemented components do not prove a navigable app.

The owner-supplied handoff attributes [post-merge CI 39251879166](https://github.com/alexbonavila/PartiturasPDF/actions/runs/39251879166) to this exact baseline: nine jobs PASS (seven mandatory plus API27/36), 420 JVM and 357 ordinary Android tests on each API26/27/36, plus separately validated controlled PDFium diagnostics. This is attributed baseline evidence; raw hosted artifacts have not been independently downloaded in this closeout. Known raw diagnostic failures must remain visible under the existing classification policy. Neither historical CI nor a merge certifies the changed closeout revision.

See [FGH_PRE_I_CLOSEOUT](../acceptance/FGH_PRE_I_CLOSEOUT.md) for current translation/coverage results, source attribution, Phase-I ownership and remaining acceptance. Physical Android8.1, backup/restore/D2D, external providers and manual accessibility remain open. Performance/memory measurement is deferred until after a functional Phase I; none is authorized here. The specification's 25% edge zones/center toggle versus G's 15%-minimum-48dp bands is a later owner product-fit decision, not changed here.
