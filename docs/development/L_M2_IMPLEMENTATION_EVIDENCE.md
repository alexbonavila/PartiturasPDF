# L-M2 pure navigation and gesture model — 2026-11-02

## Source and boundaries

Branch `feat/reader-advanced-l`, isolated worktree `/workspace/PartiturasPDF-L`.
Starting revision: `5310645c5b1d503262748accb2a14f28adec8536`.
Implementation follows [L-M1](L_M1_READER_UX_CONTRACT.md).
No K/M source was copied, merged or edited. No shared APIs, resources, host,
controller, UI state, Compose screen, native PDF code or build configuration changed.
This document records execution against the starting revision plus the exact
Reader production/test additions committed with it; committing does not represent
another test run. L-M2 remains subject to owner review, not formally accepted.

## Implemented signatures and behavior

- `readerTapZone(x: Float, width: Int): ReaderTapZone?` supplies PREVIOUS, CONTROLS,
  NEXT under exact [0,25%), [25%,75%), [75%,100%) geometry. Double intermediates
  avoid rounding the Int width to Float before comparison. Invalid/nonfinite x,
  nonpositive width and the viewport endpoint are rejected. Widths 1/2/3 and
  representable neighbors of boundaries have explicit tests.
- Existing `readerEdgeStep()` signature and implementation remain byte-for-byte
  unchanged. ReaderScreen continues using the old mapping until L-M3 approval.
  Image layout, zoom and pan mathematics are also unchanged.
- ReaderGestureGate keeps compatible `update()` and `isTap()` queries. Invalid
  slop, pointer counts, deltas/zoom, consumed input, multi-pointer history and
  excessive movement reject taps. New `cancel()` is terminal; `finishTap()` returns
  eligibility once and then permanently closes that gesture. Call finishTap only
  after normal release; call cancel on cancellation. The existing screen adapter
  still queries isTap once; new APIs are preparation for L-M3, not new UI wiring.
  Pan/pinch transformation handling remains compatible for valid gestures.
- `validateReaderPageNumber(input: String, pageCount: Int?): ReaderPageInput`
  accepts trimmed ASCII digits, including leading zeros, only when the parsed Int
  is in 1..pageCount. InvalidNumber distinguishes syntax/overflow from OutOfRange
  (zero, outside bounds or unavailable/invalid count). Valid.index is zero-based.
- `readerNavigationContext(state): ReaderNavigationContext?` captures the existing
  ReaderOpenIntent and immutable ReadSessionInfo in READY/RENDERING/RENDERED/VISIBLE.
  Other phases, missing intent and missing session are ineligible.
- `readerNavigationContextIsCurrent(captured, latest): Boolean` compares against a
  fresh eligible context. Intent sequence/request, session ID/source generation,
  count and revision participate; viewport changes do not. Generation is a cache
  boundary, never attestation of unchanged PDF bytes. No action, native work or
  checkpoint is emitted by these functions. Context is ephemeral, not serialized.

## Tests and actual validation

ReaderInteractionTest: nine existing methods preserved plus four new methods
covering proportional boundaries, invalid input, one-shot completion/cancellation
and sticky rejection. ReaderPageNavigationTest: five new methods covering ASCII
input, overflow/ranges, conversion (1/2/10/50/100-page counts and Int.MAX_VALUE),
lifecycle eligibility, missing/replaced intent/session and generation/count changes.
Full JVM suite: **642 PASS**, zero failures/errors/skips. Both focused classes
executed as part of that full run: 13 interaction and five navigation methods.
No filtered-suite coverage-finalizer result is substituted for the full suite.

Commands, using the existing JDK25/Android SDK37 activation on Linux x86_64:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 -B -m unittest discover -s scripts/tests -v
git diff --check
```

Results: debug and instrumentation APK compilation PASS; Lint PASS; existing
contract coverage verification PASS; Python **492 PASS**; localization PASS;
canonical structural/render fixture verification **15 PASS**; whitespace PASS.
Existing compiler deprecation warnings remain; no suppression or gate change.
Android instrumentation **NOT RUN / BLOCKED**: no authorized runnable emulator,
and `/dev/kvm` is unavailable. APK compilation is not device execution. API27,
physical tablet, overlay, touch and accessibility acceptance remain pending.
No GitHub Actions access, dispatch, push, PR or merge was performed.

## Actual scoped coverage

JaCoCo 0.8.14, Gradle 9.8.0, JDK25.0.4.1. An external init script consumed the
actual `app/build/jacoco/testDebugUnitTest.exec` and matching compiled debug classes;
no repository Gradle changes. Each scope independently passed 90% lines / 80%
branches. Standard JaCoCo Kotlin/generated-code filtering remains unchanged.

| Scope | Exact class patterns under com/abdev/partituraspdf/reader | LINE | BRANCH |
|---|---|---:|---:|
| Interaction | ReaderInteractionKt*, ReaderGestureGate*, ReaderTapZone* | 40/41 (97.56%) | 117/118 (99.15%) |
| Navigation | ReaderPageNavigationKt*, ReaderNavigationContext*, ReaderPageInput* | 12/12 (100%) | 28/28 (100%) |

ReaderGestureGate individually has 12/12 lines and 48/48 branches. Interaction
includes the existing layout/legacy-edge functions in ReaderInteractionKt; it does
not imply Android coverage. Unchanged ReaderTransform/ReaderImageLayout data classes
are outside this focused scope. No new business logic was excluded for low coverage.
Generated nested classes matching the patterns were included.

External reproduction after the full JVM run:

```bash
bash ./gradlew -I /tmp/l-m2-coverage.init.gradle :app:lM2InteractionVerify :app:lM2NavigationVerify --offline --no-configuration-cache --console=plain --max-workers=4
```

Local, uncommitted evidence: `/tmp/l-m2-gradle.log`, `/tmp/l-m2-python.log`,
`/tmp/l-m2-fixtures.log`, `/tmp/l-m2-coverage.log`, `/tmp/l-m2-coverage/*.xml` and
JUnit XML in `app/build/test-results/testDebugUnitTest/`. The source SHA-256 manifest
is `/tmp/l-m2-source.json`. No private device data or evidence binaries are committed.

## L-M3 and concurrency handoff

Pure context validation is **not atomic controller authorization**. A session may
change after validation and before the existing untagged ReaderAction.Page(index)
is accepted. L-M3 must revalidate immediately before dispatch, disable/dismiss stale
modal state, and require actual presented-page eligibility for opening its dialog.
The context helper does not itself prove an acknowledged visible page.

If an atomic guarantee is required, submit a narrow Reader-only action/dispatch
proposal for owner review (expected intent/session token checked under the existing
controller lock). Do not add it silently or change host contracts. No second
navigation controller is needed.

L-M3 also owns replacing the legacy edge adapter, wiring finishTap/cancel correctly,
validating full x/y bounds through the gesture adapter, center-toggle chrome,
presented-page dialog, localized resources, modal focus/keyboard arbitration,
viewport-stable overlays and real API26 SurfaceView Z-order/touch evidence.
All native generation, presentation acknowledgment, detach/lease and checkpoint
contracts remain unchanged. Do not infer L-M3/L-M4 readiness or acceptance from
this pure-model milestone.
