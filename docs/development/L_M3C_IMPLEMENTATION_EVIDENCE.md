# L-M3C direct page navigation — 2026-11-02

Branch: `feat/reader-advanced-l`, dedicated Phase L worktree.
Base: `a6569cca7e09943a8bd35180533e81b7645f7c43`.
Implementation: `77bf9d1982515e0becca0cb48dcb857187346a1a`.
This document records the actual local checks against the exact implementation
bytes subsequently committed; committing documentation is not another test run.
Publication is for owner review, not L-M3C acceptance or merge readiness.

## Implementation and safety

[ReaderScreen](../../app/src/main/java/com/abdev/partituraspdf/reader/ReaderScreen.kt)
keeps the existing page-counter wording and adds a minimum-48dp button. Its
entry point requires an eligible intent/session, VISIBLE phase, no failure,
a matching presented page/frame/session, and the existing active graphical
checkpoint consumer. A completed native render or requested page alone is
insufficient. The handler rechecks latest state before capturing the context.

[ReaderPageJumpDialog](../../app/src/main/java/com/abdev/partituraspdf/reader/ReaderPageJumpDialog.kt)
contains the small ephemeral target and two testable guards, plus a Material 3
AlertDialog. The initial value is the acknowledged presented page, 1-based and
selected for editing. It uses the existing ASCII/trim/range validator, localized
input/total/errors/Cancel/Go controls, numeric keyboard, IME Done, initial focus,
scrollable contents and an explicit Escape handler. Standard modal Back/outside
dismissal remains enabled. Back may first dismiss the IME; it must not leave the
Reader while the dialog is present. Dialog-window, keyboard and TalkBack behavior
still require Android execution.

At confirmation the screen reads latest state, rechecks the captured
ReaderNavigationContext and current count, and constructs only
ReaderAction.PageIfCurrent(index, expectedIntent, expectedSession). It clears the
pending target before calling the action callback, so repeated modal callbacks
cannot submit again. Invalid input submits nothing. Context replacement or
ineligible/error state removes the modal without retargeting it. ReaderController
remains unchanged and performs the definitive atomic active-session check.

The target holds no frame/bitmap lease and is not saved state. Opening, editing
and dismissal dispatch no PDF open/render/checkpoint action. The full-size page
and AndroidView stay mounted; viewport measurement, frame release and Presented
callback are unchanged. Modal presence disables page pointer gestures, page
keyboard shortcuts and page custom accessibility actions. Opening/dismissal keep
chrome visible, without changing fit/zoom/page. Exact 25/50/25 behavior outside
the modal remains unchanged. Valid jump rendering still waits for the existing
graphical acknowledgment before checkpointing; this does not establish durability.

Seven Reader resource keys were added to each existing reader.xml bundle. The
complete default/es/ca/en bundles each have **164 string IDs**, exact ID parity,
compatible arguments and identical default/Spanish values. No host, controller,
UI state, pure L-M2 model, native surface/frame, library, core, PDF, storage,
Gradle, dependency, CI, shared validator or canonical fixture changes.

## Focused tests

Six new JVM methods in
[ReaderPageJumpTest](../../app/src/test/java/com/abdev/partituraspdf/reader/ReaderPageJumpTest.kt)
executed as part of the full suite:

- `renderedRequestedPageIsNotAnEligiblePresentedPage`
- `unavailableInconsistentOrDetachedPresentationCannotOpenDialog`
- `inputValidationNeverNavigatesAndMapsFirstAndLastExactly`
- `contextAndFailureChangesCannotRetargetCapturedDialog`
- `validJumpDoesNotCheckpointUntilNewGraphicalAcknowledgment`
- `actionBuiltBeforeReplacementIsRejectedByAtomicControllerGuard`

These use existing synthetic sessions/presenter acknowledgments, not native
Android display evidence. They cover missing/stale/ineligible/failed/detached
contexts; first/last mapping; syntax, overflow, non-ASCII and range rejection;
unchanged viewport identity semantics; no side effects from validation; and
controller rejection of an action constructed before replacement. Existing
L-M2/L-M3A tests, including same-intent replacement, remain enabled.

Eight instrumentation methods were appended to
[ReaderScreenTest](../../app/src/androidTest/java/com/abdev/partituraspdf/reader/ReaderScreenTest.kt):

- `pageJumpOpensOnlyForAcknowledgedPresentedPage`
- `pageJumpFirstLastAndTrimmedInputDispatchExactlyOnce`
- `pageJumpInvalidSyntaxAndRangeCannotDispatch`
- `pageJumpCancelBackAndEscapeKeepViewportFrameZoomAndToolbar`
- `pageJumpModalOwnsTouchAndKeyboardWithoutUnderlyingNavigation`
- `pageJumpReplacementAndIneligibleLifecycleDismissWithoutRetargeting`
- `pageJumpRequestedTargetWaitsForPresentationAndCheckpoint`
- `pageJumpLargeFontDarkDialogRemainsEditableAndDismissible`

ReaderScreenTest now contains 47 methods. These new methods compile but are
**NOT RUN**, using the existing synthetic provider with real Android bitmaps and
surfaces when executed. They do not certify genuine provider access or physical
compositor behavior. All existing assertions, L-M3B scenarios and native tests
are preserved; no diagnostic selection was changed.

## Actual validation

Linux x86_64; existing JDK25.0.4.1, SDK37, Gradle9.8, AGP9.5 toolchain.
Commands (existing toolchain activated first):

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
python3 -B -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

| Check | Actual result |
|---|---|
| Debug build / instrumentation APK compilation / Lint | PASS |
| Full JVM suite | **653 PASS**, zero failures/errors/skips |
| Existing contract coverage verification | PASS |
| Python | **491 PASS / 1 FAIL**, 492 executed, zero skips |
| Localization validator and independent bundle parity | PASS |
| Canonical PDF checksum/structure/render verification | **15 PASS** |
| Scope and whitespace | PASS |
| Android runtime, API26 dialog/overlay/keyboard | **BLOCKED / NOT RUN** |

The sole Python failure is
`test_complete_localization.CompleteLocalizationTests.test_actual_resources_have_complete_ids_and_no_staging`:
protected `self.assertEqual(155, len(expected))`, actual **164** (157 at L-M3B,
plus seven L-M3C keys). Its preceding localization validation passes. The protected
test is unchanged. The owner explicitly permits only this failure for isolated
branch publication; the complete Python suite is **not green** and integration
still requires separately authorized correction. Other FAIL/BLOCKED strings in
host logs are expected synthetic runner-output tests, not extra unittest failures.

The initial sandboxed Gradle attempt failed before tasks because its file-lock
service could not determine a usable wildcard IP. The authorized run with the
sandbox networking restriction lifted completed successfully; no source/build
configuration workaround or failed application test was hidden. Existing compiler
deprecation warnings remain unsuppressed.

## Scoped coverage and provenance

Existing JaCoCo0.8.14 infrastructure consumed the completed full-suite execution
and matching compiled classes, using an external local init script:

```bash
bash ./gradlew -I /tmp/l-m3c-coverage.init.gradle :app:lM3cReport \
  --offline --no-configuration-cache --console=plain --max-workers=4
```

New testable guard logic in ReaderPageJumpDialogKt:

| Method | Lines | Branches |
|---|---:|---:|
| readerPageJumpTarget | 8/8 | 20/20 |
| readerPageJumpAction | 3/3 | 9/10 |
| Combined | **11/11 (100%)** | **29/30 (96.67%)** |

The missed action branch is the nullable session-count access after the preceding
current-context check; null sessions are rejected before it. ReaderPageJumpTarget
constructor/property lines also executed. Standard JaCoCo compiler filtering is
unchanged. The XML contains the whole new file/classes, including unexecuted
Compose code; these method counters explicitly scope the new pure guard logic.
Compose adapter/dialog coverage is **NOT MEASURED / BLOCKED**, not inferred from
JVM counters, compilation or historical UI results. No repository reporting
framework, denominator policy or threshold was changed.

Local artifacts: `/tmp/l-m3c-gradle.log`, `/tmp/l-m3c-python.log`,
`/tmp/l-m3c-localization.log`, `/tmp/l-m3c-fixtures.log`,
`/tmp/l-m3c-coverage/report.xml`, and JUnit XML in
`app/build/test-results/testDebugUnitTest/`. `/tmp/l-m3c-source.json` records
SHA-256 for the exact eight implementation/test/resource files and is checked
against the implementation commit. These generated logs/reports are not Git inputs.

## Remaining acceptance

No authorized runnable emulator is available; `/dev/kvm` is absent. No device,
installation, instrumentation or CI operation was performed. Real API26 modal
input/Back/focus, SurfaceView overlay/touch, large-font layouts and lifetime tests
remain pending. API27/recent compatibility, physical Android8.1 tablet, TalkBack,
orientation and es/ca/en linguistic/manual review are not claimed PASS.

L-M3D configuration/presentation-owner retention remains separate and unimplemented;
this modal intentionally does not persist captured sessions or input across
recreation. The L-M3B runtime limitation and protected count failure remain open.
No full Phase L acceptance, integration readiness, PR, merge, tag or release is
claimed. Publication does not authorize L-M3D.
