# L-M3C-R5 API26 seek stabilization — 2026-11-02

**IMPLEMENTED — API26 RUNTIME VALIDATION PENDING.**
Branch: `feat/reader-advanced-l`; starting revision:
`b2e3037ebca9bc821f0fcfdc136d4b4b4c8a7a93`.
This records scoped corrections for owner review, not Android runtime PASS.

## Owner evidence and preserved baseline

The owner reports 48 executed / 44 PASS / 4 FAIL / zero skipped on API26.
Raw execution archives were not attached here; these are owner-reported results.
The passing seekFirstLastUsesExactlyTwoGuardedActionsAndThreeAcknowledgments
establishes two guarded actions, three renders, [0,7,0] acknowledgments, one opening
and maximum one lease at R4 according to that report. Its test body is unchanged.
All 48 instrumentation methods remain present. No dialog/IME investigation or
implementation was restarted; historical R4 results are not retroactively changed.

## A — held-drag preview

The original test injected one down and one long move. The owner's observation
proves that preview was still at the acknowledged page at assertion time; it does
not independently establish whether onValueChange never fired or preview cleared.
No local Android execution or raw callback trace confirms that distinction.

Inspected the exact installed Material3 1.4.0 Slider.kt source from Google's Maven
source artifact. SliderImpl combines sliderTapModifier/detectTapGestures and a
horizontal draggable with touch-slop arbitration; its drag state receives deltas
and onDragStopped performs gesture completion. A single synthetic displacement
is not a sufficiently diagnostic reproduction of an established multi-event drag.
No overlay interception or production preview-state defect is demonstrated by
source inspection. The preview implementation is unchanged.

The test now starts at a proportional in-control position and emits five timed
move samples crossing slop and continuing to the end of the track, then leaves
the pointer DOWN while checking preview. Synthetic event intervals are 16ms;
there is no thread sleep, retry or relaxed assertion. ReaderRuntimeSequence emits
seek-drag-held-before-preview-assertion before the original strict preview check.
The held phase still requires zero PageIfCurrent actions, identical frame, no
additional render and unchanged acknowledged page. Only subsequent UP may navigate
once, with no leaked Step action. This is a justified test-interaction correction
candidate, **not a confirmed runtime fix**. If the same preview assertion fails,
collect the retained diagnostic marker and actual Slider callback/interaction
sequence before altering production cancellation or preview logic.

## B — 48dp measured control

Material3 SliderImpl appends minimumInteractiveComponentSize, requiredSizeIn,
sliderSemantics and gesture modifiers after the caller modifier. Its inner
layout height is max(track height, thumb height); the default Thumb uses 44dp.
Thus an outer heightIn(48dp) and minimum-target padding do not by themselves imply
48dp inner semantics bounds. The owner's 44dp measurement agrees with this source.
It does not independently prove the framework's expanded pointer target was only
44dp; that distinction is preserved.

ReaderPageSeekControl now supplies the standard SliderDefaults.Thumb with an
explicit 48dp height, preserving its colors, enabled behavior and interaction
source. The inner measured slider/gesture/semantics layout now has the intended
minimum height under normal available constraints, rather than only padded space.
The strict 48dp test remains unchanged. This is the only functional production
layout correction. No viewport space is reserved: it remains a bottom sibling
overlay on the unchanged full-size Reader surface. Actual large-font/short-window
measurement still requires API26 validation.

## C and D — unambiguous counter assertions

ReaderScreen adds reader_toolbar_counter to its existing counter Text. No text,
layout, semantics role or accessibility behavior is otherwise changed.

- activeSoftwareSurfaceDrawAcknowledgesExactlyOnePage now selects that toolbar
  counter, asserts its exact page text and visibility, and preserves its exact
  acknowledgment, bitmap and lease assertions.
- requestedPageIsNotReportedAsPresentedDuringPendingRender independently selects
  toolbar counter and reader_seek_position, requiring both to show the unpresented
  message. It requires zero matching pending-page counter nodes globally, then
  preserves the acknowledgment gate until rendering is released.

These are confirmed test-selector defects: duplicate correct labels in distinct
components are legitimate. No production presentation authority changed.

## Scope and validation

Exactly four files change: ReaderPageSeekControl.kt, ReaderScreen.kt,
ReaderScreenTest.kt and this document. No controller/presenter/frame/surface,
host, storage, renderer, navigation contract, resources, dependency, Gradle or CI
changes. No instrumentation assertion was disabled or deduplicated. All 48 methods
retain their relevant guarantees; the passing guarded first/last journey is intact.

Existing Linux x86_64/JDK25/SDK37/Gradle9.8/AGP9.5 toolchain:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
python3 -B -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

- Debug build, instrumentation compilation and Lint: PASS.
- Full JVM: **651 PASS**, zero failures/errors/skips; contract coverage gates PASS.
- Python: **492 PASS**, zero failures/skips.
- Localization: PASS; no resource changes, complete 160-ID locale inventory.
- Canonical fixtures: **15 PASS**, checksum/structure/render checks.
- Scope and whitespace: PASS.
- Existing pure seek scope with matching JVM data: **16/16 lines, 54/56 branches**
  (100% / 96.43%). Existing external JaCoCo reporting reused, no policy changes.
- New Compose layout/Android interaction coverage: NOT MEASURED. No new pure
  business logic is introduced and no Android coverage is inferred from JVM data.
- Android API26: **NOT RUN / BLOCKED**, no authorized runnable emulator and no
  /dev/kvm. The four affected tests have not been executed successfully here.

Logs: /tmp/l-m3c-r5-gradle.log, /tmp/l-m3c-r5-python.log,
/tmp/l-m3c-r5-localization.log, /tmp/l-m3c-r5-fixtures.log,
/tmp/l-m3c-r5-coverage/report.xml; full JVM XML remains under
app/build/test-results/testDebugUnitTest. Coverage command:

```bash
bash ./gradlew -I /tmp/l-m3c-r5-coverage.init.gradle :app:lM3cR5Report \
  --offline --no-configuration-cache --console=plain --max-workers=4
```

Framework inspection artifacts /tmp/l-r5-Slider.kt and the source jar are local
only. Committing these source bytes/docs does not create another test execution.

## Owner runtime verification

On the exact published revision with matching APK identities and an explicitly
selected owner-authorized API26 emulator, run the four named methods first, then
all 48 ReaderScreenTest methods. Retain JUnit XML, original output and unfiltered
ReaderRuntimeSequence logs. In PowerShell, after verifying the sole intended AVD
and setting ANDROID_SERIAL to its approved serial:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.reader.ReaderScreenTest#seekDragOnlyPreviewsUntilReleaseAndConsumesPointer,com.abdev.partituraspdf.reader.ReaderScreenTest#seekLargeFontAndRealResizeRemainUsable,com.abdev.partituraspdf.reader.ReaderScreenTest#activeSoftwareSurfaceDrawAcknowledgesExactlyOnePage,com.abdev.partituraspdf.reader.ReaderScreenTest#requestedPageIsNotReportedAsPresentedDuringPendingRender" --console=plain
# Preserve focused results before the full class; stop on any failure.
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.reader.ReaderScreenTest" --console=plain
```

Those commands install/run APKs and require owner device authorization; none was
executed here. Require 4/4 then 48/48 with zero failures/skips, authentic held drag,
48dp measured control, preserved [0,7,0] journey and viewport/lease invariants.
No reset/uninstall/worker-kill or full unrelated suite is requested. API27/recent,
physical tablet, TalkBack and manual acceptance remain pending. Earlier separate
CI-only visibility findings are not silently closed. L-M3D is NOT STARTED.
No PR, merge, rebase, tag or release.
