# L-M3B viewport-stable Reader chrome — 2026-11-02

Base: `2b1186aa1c6e3e345755cb1aac20d8e18497494c` on
`feat/reader-advanced-l`, dedicated `/workspace/PartiturasPDF-L` worktree.
No main/K/M integration. This is a locally validated implementation candidate,
not full L-M3B acceptance: API26 overlay execution and a protected localization
count correction remain outstanding. Local commits do not constitute new runs.

## Implementation

ReaderScreen keeps one full-size page Box and the existing AndroidView factory,
update/release and Presented callback. This page alone measures ReaderViewport.
The old fixed toolbar/page allocation becomes a separate top-aligned scrollable
Surface overlay. Its practical maximum height remains 128dp/30% for short layouts;
controls wrap and remain scrollable at large fonts. The host's app header remains
outside this milestone: this is not an app-wide fullscreen redesign.

Toolbar is initially visible and remembered by ReaderOpenIntent. Center taps
toggle it manually; there is no auto-hide timer. Page, fit, zoom and recomposition
do not reset the preference. New intents reset it. Options is local to the intent
and closes on unavailable/error/canceled lifecycle state. Hidden toolbar controls
are removed from composition, focus and semantics. Configuration recreation is
NOT guaranteed here; presentation-owner identity belongs to L-M3D.

Recovery/status remains in a separate bounded scrollable overlay when needed,
independently of hidden preference; failures also expose Back. Empty normal status
creates no overlay. Overlay pointer hit regions are above the sibling page and
observe rather than consume child events, allowing scrolling/clicking controls.
This relies on normal sibling hit testing, not coordinate taps or a second page
gesture detector. zIndex is not claimed as proof of SurfaceView compositor ordering.

One page detector now uses readerTapZone and finishTap. It validates finite x/y
bounds on down and subsequent changes, rejects out-of-bounds gestures, preserves
pan/pinch processing through ReaderGestureGate and calls cancel in finally.
Only normal, eligible release maps to Step(-1), local toggle or Step(+1).
Cancellation never dispatches. Legacy readerEdgeStep stays unchanged for tests.
Page descriptions remain tied to presentedPage/frame, not requested-only pages.

Focused unmodified **C** toggles chrome; existing Left/Right, Up/Down, +/- and 0
shortcuts remain. Menu state/modifier checks prevent page-key handling there;
button Enter/Space and system Back are untouched. Reader surface gains named
Show controls / Hide controls custom accessibility actions. Existing actions,
fit/zoom, errors and 48dp control heights remain; TalkBack/manual checks are pending.
Only reader_controls_show and reader_controls_hide were added in all four bundles.
Spanish default equals values-es; es/ca/en keys and formats match.

## Tests and evidence boundaries

ReaderScreenTest adds five methods:

- centerTogglesWithoutViewportRenderLeaseOrTimeoutChanges: repeated touch toggles,
  no timer, same attached surface/frame, viewport/generation/open/render/lease counts.
- quarterBoundariesAndHiddenKeyboardControlsKeepOriginalNavigation: exact 25%/75%
  touches, hidden preference across page step, focused C and accessibility toggle.
- canceledAndOutOfBoundsMotionNeverToggleChrome: canceled and outside/return input.
- toolbarTouchDoesNotLeakAndHiddenRecoveryRemainsReachable: genuine toolbar touches,
  background hit region, hidden error and Back.
- hiddenPreferenceSurvivesFitPageAndZoomButNewIntentShowsToolbar.

Existing edge endpoint, drag/pinch, fit/zoom, keyboard/tab/menu, large-font/dark,
requested/presented and graphical lifetime safety assertions remain unchanged.
ReaderScreenTest now has 39 methods; the new scenarios use synthetic sessions and
real Android bitmaps/surfaces, not native-PDF execution evidence.

ReaderNativeIntegrationTest adds
f_nativeSurfaceAndCompositeToolbarToggleWithoutNewRendering using canonical PDF02,
the isolated native engine, posted score ink, window PixelCopy differences in the
actual toolbar rectangle, real touch navigation and stable surface/frame/viewport/
render/open/lease observations. The class has eight methods including its existing
controlled diagnostic method; no diagnostic annotations or selections changed.
Window-buffer differences and a separately copied native surface are useful
assertions, not independent proof of every device compositor behavior. Real API26
execution and visual/manual review remain mandatory. No renderer or Z-order
workaround was introduced; a demonstrated overlay failure requires owner review.

## Validation

Linux x86_64; existing JDK25.0.4.1, Android SDK37, Gradle9.8, AGP9.5 toolchain:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
python3 -B -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

- Debug build, instrumentation APK compilation, Lint: PASS on final source.
- JVM: 647 PASS, zero failures/errors/skips; existing contract coverage gate PASS.
- Localization validator: PASS. All four resource bundles contain 157 string IDs.
- Canonical fixtures: 15 PASS, including structural/render checks.
- Python: **FAIL — 491 PASS, one failure, zero skips, 492 executed**.
  test_complete_localization.CompleteLocalizationTests.test_actual_resources_have_complete_ids_and_no_staging
  fails solely at the protected `self.assertEqual(155, len(expected))`: actual 157.
  The validator/parity assertion before it succeeds; static bundle parity was also
  checked independently. scripts/tests/test_complete_localization.py is UNCHANGED.
  This is the explicit known integration dependency permitted by the assignment,
  not a green suite or an approved threshold exception. Separate owner correction
  remains required; resources and validation were not weakened to bypass it.
- An intermediate native-test compile failed for missing test/resource imports;
  imports were corrected and final instrumentation compilation passed. Earlier
  failed/intermediate attempts are not counted as final successful executions.
- API26/other Android runtime: BLOCKED / NOT RUN (no authorized runnable emulator;
  /dev/kvm absent). No Android operations or CI execution occurred.
- New Compose adapter/chrome LINE/BRANCH coverage: NOT MEASURED / BLOCKED by Android
  execution availability. No UI coverage is inferred from JVM results or historical
  L-M2/L-M3A counters. Pure navigation/gate/controller business logic is unchanged;
  no new architecture or test framework was added and coverage gates remain intact.

Logs are local artifacts, not Git inputs: /tmp/l-m3b-final-checks.log,
/tmp/l-m3b-final-python.log, /tmp/l-m3b-final-fixtures.log and JUnit XML under
app/build/test-results/testDebugUnitTest. Source SHA-256 manifest:
/tmp/l-m3b-source.json. Tests were not rerun or relabeled merely by committing.

## Exclusions and handoff

Exactly ReaderScreen, its two reader-owned instrumentation suites, four reader.xml
bundles and this evidence document changed. No controller/UI-state/interaction
model, native surface/frame, host/core/PDF/storage/library, dependency, Manifest,
CI or shared validation script changes. No owner token, jump dialog, PageIfCurrent
UI wiring, L-M3C or L-M3D work. No push, PR, merge, tag or release.

Next gates: owner-authorized count correction; API26 overlay/touch/focus/lifetime
execution; applicable API27/physical-tablet, TalkBack, language, orientation and
large-font review. L-M3C owns page jump; L-M3D owns configuration/lifecycle retention.
Do not present compilation, local commits or this document as full acceptance.
