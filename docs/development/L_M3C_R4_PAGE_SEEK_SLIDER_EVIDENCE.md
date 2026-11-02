# L-M3C-R4 page seek slider — 2026-11-02

**IMPLEMENTED — API26 RUNTIME VALIDATION PENDING.**
Branch: `feat/reader-advanced-l`; base:
`cf28072845111812e9dc1410f3684f2ea920b1c1`.
The owner explicitly superseded numeric-dialog UX. This is not phase acceptance.

## Design and implementation

The reference pattern is page seeking in
[forScore](https://forscore.co/kb/seek-bar/) and
[MobileSheets](https://www.zubersoft.download/manuals/MobileSheets.pdf): a compact
horizontal progress/seek control rather than a keyboard page-number editor.
These links are product references supplied by the owner; this implementation
uses standard Material3 Slider and no proprietary assets, code, thumbnails or
reference-product behavioral claims from independent runtime inspection.

ReaderScreen retains its top toolbar and adds a sibling bottom Surface overlay
inside the existing safeDrawingPadding area. Chrome toggling removes/recreates
both controls without allocating space away from the full-size PDF SurfaceView.
The presented-page counter is ordinary text again. The bottom overlay owns its
pointer hit region; the slider is at least 48dp high. The caption displays first,
last and either confirmed current page or explicitly labeled preview. Requested
pages never become a confirmed caption before graphical acknowledgment.

ReaderPageSeekControl remembers only a captured intent/session context and integer
preview, never a frame or bitmap. onValueChange updates that local pair only;
it dispatches nothing. onValueChangeFinished takes and clears it before resolving
one PageIfCurrent. The helper rechecks current context, genuinely eligible active
presentation, bounds, and presented/requested no-op destinations. The controller
still makes the definitive atomic check. No legacy Page action is used.

Context-keyed Slider composition cancels the old pointer detector on replacement;
remembered selection resets with the context. Loss of eligibility and canceled
drag/press interactions clear preview. Hiding chrome disposes the entire control.
The original row/page gestures, pan/zoom, PAGE/WIDTH, Back and recovery remain.
No native open/render/checkpoint call exists in the preview path. Rendering and
persistence authority remain entirely in the unchanged controller/presenter/
frame/surface protocol. Source separation establishes the no-dispatch property;
Android tests proving actual gesture behavior remain NOT RUN.

ReaderPageSeek supplies pure eligibility, normalized mapping and guarded-action
helpers. A continuous 0..1 Float slider with zero discrete steps uses constant
space for all counts. Finite in-range fractions map with Double multiplication
and rounded Long conversion to bounded 0-based indices. Endpoints map exactly;
no count-sized tick/step collection is created. Very large counts exceed Float
position resolution, so not every page is individually addressable by a single
track coordinate; labels/actions still use bounded integer indices. No claim of
pixel-level individual selection for billions of pages is made. Keyboard and
accessibility adjustments are completed selections, not PDF preview rendering.
A one-page document displays page 1 with a disabled slider.

## Removed functionality and preserved history

Deleted ReaderPageJumpDialog.kt and ReaderDialogImePolicy.kt. No Dialog,
FocusRequester, numeric editor, IME handler, Escape handler or Window.Callback
policy remains. Reusable presentation/context validation moved to ReaderPageSeek.
Removed the unused numeric parser/result from ReaderPageNavigation; context and
PageIfCurrent contracts remain unchanged. Corresponding three numeric parser JVM
tests are superseded by seek mapping/bounds tests. Context/guard/frame/checkpoint
regressions remain, migrated to ReaderSeekTest.

Seven obsolete reader_jump strings were removed from all four bundles; three
seek strings added, yielding **160** IDs per complete locale bundle. Default
Spanish, explicit Spanish, Catalan and English parity/format checks remain.
Only the expected count in test_complete_localization changed from 164 to 160.
No new dependency, resource assets, fixture or policy-gate changes.

L-M1 now has an explicit R4 supersession notice. Historical L-M3C and R1/R2/R3
failures and NOT RUN results remain untouched. The experimental IME approach is
abandoned deliberately, not reclassified as successful. No keyboard input is
requested by this UI, so the removed dialog cannot initiate the observed IME
resize chain; Android execution must still establish that the slider itself
preserves viewport/frame behavior.

## Tests and actual validation

ReaderSeekTest retains six prior guard/graphical tests with integer selection
rather than numeric input and adds mapping cases for 1/2/8/50/100/10000/Int.MAX_VALUE,
endpoints and neighboring fractions, invalid/nonfinite positions and stale bounds.
The seven methods execute with synthetic sessions/presenter acknowledgments.
ReaderPageNavigationTest retains its two context tests. Existing guarded controller,
lease/cleanup, scheduling and native tests are unchanged.

Ten dialog/IME-only instrumentation methods are replaced by nine slider methods:

- seekChromeTogglePreservesFrameViewportAndRendering
- seekFirstLastUsesExactlyTwoGuardedActionsAndThreeAcknowledgments
- seekDragOnlyPreviewsUntilReleaseAndConsumesPointer
- seekCancelPreservesPageFitZoomAndFrame
- seekPendingRequestDoesNotClaimPresentation
- seekReplacementCancelsDragAndCannotNavigateNewIntent
- seekLargeFontAndRealResizeRemainUsable
- seekKeyboardStaysBounded
- onePageSeekIsDisabledWithoutExtraWork

ReaderScreenTest now has **48 methods**. Strict relevant guarantees are carried
forward: same frame/surface/viewport/generation on chrome changes; exact [0,7,0]
acknowledgments and three renders/two guarded actions; no intermediate rendering;
no-op/cancel/stale rejection; one opening and maximum one bitmap lease. These
instrumentation tests compile but have NOT executed. R1 diagnostics remain.

Commands, existing Linux x86_64/JDK25/SDK37/Gradle9.8/AGP9.5 toolchain:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
python3 -B -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

Debug build, instrumentation compilation, Lint and existing contract coverage:
PASS. Full JVM **651 PASS**, zero failures/errors/skips. Python **492 PASS**, zero
failures/skips. Localization PASS; canonical fixtures **15 PASS**, including
structure/render verification. Scope/whitespace PASS. Initial/intermediate JVM
runs failed one migrated test that still expected the old data-class name in
its toString assertion. The expected name was corrected to ReaderSeekTarget;
no safety assertions were removed to obtain the final PASS.

Existing JaCoCo reporting via an external /tmp/l-m3c-r4-coverage.init.gradle consumed
matching full JVM execution/classes, with no repository build changes:

```bash
bash ./gradlew -I /tmp/l-m3c-r4-coverage.init.gradle :app:lM3cR4Report \
  --offline --no-configuration-cache --console=plain --max-workers=4
```

ReaderPageSeekKt pure logic: **16/16 lines (100%), 54/56 branches (96.43%)**;
ReaderSeekTarget constructor/property lines also covered. Entire new pure class
scope is reported with normal compiler filtering. Compose control/gesture coverage
is NOT MEASURED; no Android coverage is inferred from JVM results. Local logs are
/tmp/l-m3c-r4-verified-gradle.log, /tmp/l-m3c-r4-python.log,
/tmp/l-m3c-r4-localization.log, /tmp/l-m3c-r4-fixtures.log and
/tmp/l-m3c-r4-coverage/report.xml; these are not Git inputs. Committing is not a
new execution.

## Runtime handoff and remaining limitations

No authorized runnable emulator and no /dev/kvm are available. Android API26,
API27/recent compatibility, physical Android8.1 tablet, TalkBack, keyboard, RTL,
large-font portrait/landscape and actual pointer cancellation remain **NOT RUN**.
Finite Float precision is an explicit large-count limitation. The four separately
reported CI-only status/recovery visibility failures are not changed here.
No new memory budget or physical-device acceptance is claimed.

On the owner's authorized API26 emulator, build matching APKs and use the existing
runner to execute the nine named seek methods, then the full ReaderScreenTest
class (48 completed methods required). For PowerShell with the sole selected
owner-approved serial in ANDROID_SERIAL:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.reader.ReaderScreenTest" --console=plain
```

This installs/runs test packages and needs owner device authorization. Preserve
original JUnit XML, raw output, R1 ReaderRuntimeSequence Logcat, exact source/APK/
device identities, skipped/failing results and incomplete evidence. Require strict
zero-failure results; compilation alone cannot close R4. No data reset/uninstall
or excluded diagnostic class is part of this procedure. L-M3D remains NOT STARTED.
No PR, merge, rebase, tag or release.
