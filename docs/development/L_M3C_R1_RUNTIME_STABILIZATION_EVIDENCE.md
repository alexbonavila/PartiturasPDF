# L-M3C-R1 runtime investigation — 2026-11-02

Branch: `feat/reader-advanced-l`. Base:
`512f8588376719a326cbe00b42448b9242e29700`.
Status: **diagnostics published for owner execution; production root cause NOT YET
CONFIRMED; the two Android failures are NOT verified fixed**.

## Evidence and source investigation

The owner reports Windows 11/API26 x86_64 ReaderScreenTest: 47 executed, 45 PASS,
two FAIL, zero skips, with the same two failures on GitHub. No original ordered
runtime trace or CI log archive was supplied in this assignment. No CI was accessed.

- `pageJumpCancelBackAndEscapeKeepViewportFrameZoomAndToolbar` fails at frame
  identity after Cancel/Back/Escape. Later viewport/generation/render assertions
  have not executed successfully.
- `pageJumpFirstLastAndTrimmedInputDispatchExactlyOnce` receives acknowledgments
  `[0,0,0,7,7,7,7,7,0]` instead of `[0,7,0]`; guarded action counts pass according
  to the owner. Duplicate navigation is therefore not established by that report.

Source inspection supports the following candidate chain, not a runtime diagnosis:

1. ReaderPageJumpDialog uses Material3 AlertDialog and requests input focus.
   Dialog/IME/window sizing and insets must be observed on the affected emulator.
2. ReaderScreen measures its full page Box with onSizeChanged, then dispatches
   ReaderAction.Viewport when dimensions differ. Chrome visibility itself does
   not allocate space from that viewport.
3. ReaderController accepts changed viewport dimensions, advances the viewport
   generation and invalidates/schedules rendering. A new render can replace the
   frame and retire its old lease.
4. ReaderPageSurface/ReaderPresenter bind and acknowledge posted frames through
   the existing Presented protocol. Controller acknowledgment is frame-based;
   another acknowledged frame of the same page can schedule another route display
   notification. This alone does not prove duplicate durable persistence.

The supplied results are compatible with IME-related resizing, but do not rule out
legitimate physical resizing, surface rebinding, another render trigger or test
synchronization effects. No production correction is justified without the trace.
ReaderController, ReaderPresenter, ReaderFrame, ReaderPageSurface, ReaderScreen,
dialog, host and window configuration are unchanged. Real viewport changes are
not suppressed and no acknowledgment filtering or deduplication was introduced.

## Diagnostic changes

ReaderScreenTest now emits ordered JSON records under Logcat tag
`ReaderRuntimeSequence` during the existing tests:

- action-before/after, render-start, lease-close, route-displayed;
- composed-state observations and changed global-layout observations;
- explicit modal opening, Cancel, Back, Escape and pre-assertion markers.

Each record includes test identity, local sequence, phase, requested/presented
page, viewport width/height/generation, frame/surface identity-hash tokens, render
and open counts, acknowledgment history, active/maximum leases, graphical-consumer
activity, surface attachment/dimensions, window focus, decor dimensions, visible
window bounds and system bottom inset. Tokens are local diagnostic hints, not
collision-free cross-run identity or binary attestation. Insets are observed
channels, not an authoritative standalone IME visibility classification. State
observations may conflate intermediate Flow emissions; action/render callbacks
provide complementary records, not a claim of a complete system event trace.

Layout duplicates are suppressed only in diagnostic output; test results and
acknowledgments are never filtered. Logging is bounded at 2048 records per test;
a `TRACE TRUNCATED: evidence incomplete` marker means that trace cannot establish
full ordering. No log lock is held while observing frame locks or Android views.
The listener is removed with composition disposal. No document identifiers, input
text, source paths, screenshots or PDF content are logged. No frame lease is
retained by diagnostics. All 47 test methods and their assertions remain enabled,
including the original assertSame and exact acknowledgment sequence assertions.

## Visibility/recovery review

Existing short/large-font recovery tests generally use performScrollTo before
requiring visibility. Other status checks, including
`resourceBackedIdleDoesNotOpenFromComposition`,
`openingStateIsVisibleUntilTheRouteActuallyCompletes`,
`recoverableErrorDispatchesRealRetry` and `retiredUiNeverKeepsAnAttachedImage`,
assert visibility without scrolling the bounded toolbar. The new counter's 48dp
minimum can affect remaining visible space across density/font/window sizes.
This is a static risk, not identification of the unspecified four CI failures.
Their exact identities, bounds and failure logs remain needed. Diagnostics apply
to the whole class so the owner can collect those cases too. No visibility check
was removed, weakened, skipped or speculatively replaced.

## Localization correction

Only `self.assertEqual(155, len(expected))` changed to `164` in
scripts/tests/test_complete_localization.py. All parity, format, Spanish-default,
complete-delivery and staging checks remain intact. No resources or validator
changed. The count must be reconciled with newer main resources at integration;
this branch's count is not a global resource-inventory contract.

## Validation and provenance

Existing Linux x86_64/JDK25/SDK37/Gradle9.8/AGP9.5 environment; approved toolchain
activation used. Commands:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
bash ./gradlew :app:assembleDebugAndroidTest :app:lintDebug \
  --console=plain --max-workers=4
python3 -B -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

- Debug build, instrumentation APK compilation and Lint: PASS. Instrumentation
  compilation/Lint repeated for final diagnostic markers/lock ordering changes.
- Full JVM: **653 PASS**, zero failures/errors/skips; contract coverage gates PASS.
  No production or JVM source changed after that run.
- Full Python: **492 PASS**, zero failures/skips, including corrected count test.
- Localization: PASS; canonical fixtures: **15 PASS**, checksum/structure/render.
- Source scope and whitespace: PASS.
- New production business logic/coverage: not applicable; none introduced.
  Diagnostic Android helper runtime coverage: NOT MEASURED.
- Android execution: **BLOCKED / NOT RUN**; no authorized runnable emulator and
  no /dev/kvm. No device, installation, keyboard or window operation was executed.
  The original two failures have NOT been executed successfully here.

Retained local logs: /tmp/l-m3c-r1-gradle.log,
/tmp/l-m3c-r1-final-compile.log, /tmp/l-m3c-r1-python.log,
/tmp/l-m3c-r1-localization.log and /tmp/l-m3c-r1-fixtures.log; full JVM XML remains
under app/build/test-results/testDebugUnitTest. These are not tracked artifacts.
Historical L-M3B/L-M3C results retain their original failures and limitations;
this new host result does not retroactively relabel them or certify Android fixes.

## Owner execution and decision boundary

On the owner's authorized API26 emulator, use the existing runner's exact-method
selection for the two failing methods, followed by the full ReaderScreenTest class.
Retain unfiltered instrumentation/JUnit output and Logcat `ReaderRuntimeSequence`
records from before launch through completion, at this published revision and with
matching app/test APK identities. Do not clear/reset device data for collection.
Preserve failures and trace truncation, and report the four CI-only method names.
Compare modal markers with viewport actions/generations, render-start records,
frame/surface changes and route-displayed records; include actual physical window
changes before choosing a correction. Do not treat compilation as a runtime PASS.

If evidence requires protected controller/presenter/frame/surface/host or
activity-wide window changes, obtain separate authorization first. No global
viewport freeze or deduplicated acknowledgment assertion is proposed. L-M3D,
API27/recent compatibility, physical-tablet, TalkBack and final acceptance remain
pending. No PR, merge, rebase, tag or release is authorized by this diagnostic work.
