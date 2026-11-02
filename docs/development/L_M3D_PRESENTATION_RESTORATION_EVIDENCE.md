# L-M3D presentation restoration and focus — 2026-11-02

**IMPLEMENTED — OWNER API26 VALIDATION PENDING.**
Branch: `feat/reader-advanced-l`; starting source:
`e0bcd15d3c84aac43845a3313c705c990878d5f6`.
See [L-M3C closeout](L_M3C_ACCEPTANCE_CLOSEOUT.md) for the owner's acceptance of
R5. Those results apply to R5 only; no historical report is rewritten.

## Reader-private ownership design

ReaderScreen changes only chrome visibility from remember(state.intent) to
**retain(state.intent)** using the already installed Compose runtime 1.10.4.
No dependency or build change. Inspected the exact runtime-retain source plus
Compose UI AndroidComposeView/CompositionLocals/LifecycleRetainedValuesStoreOwner:
AndroidComposeView installs a lifecycle-aware retained store through its existing
ViewTree lifecycle/ViewModel owners. That framework store retains exited values
during compatible Activity recreation and discards normal foreground composition
exits. No application ViewModel or global state owner was introduced.

The retained value is a mutable Boolean. The complete immutable ReaderOpenIntent
is an **in-memory comparison key**, including sequence and request; it is never
serialized. Runtime retain compares full key equality, not just the sequence,
hash or current session. Different sequence/request discards the prior value and
starts visible. Normal route removal discards its presentation lifetime, so a
fresh entry starts visible even for an equal intent value. Process death loses
the in-memory store and starts visible, as allowed. There is no saved-state
Bundle, URI string/fingerprint, frame, bitmap, native session or grant saved by
this change. The framework keeps comparison keys in memory during retention;
this is not document/session restoration or authorization to reopen a PDF.

Same-intent page, fit, zoom, background/quiesce/resume and ordinary recomposition
do not change the Boolean key. Real viewport changes remain measured normally;
no freeze, debounce, native frame preservation promise across physical rotation,
or graphical acknowledgment shortcut was added. Existing zoom/pan policy is
unchanged; this change does not introduce a new transform-restoration policy.

Options visibility, seek pending target, FocusRequester and focus-return request
remain ordinary remembered state. They are not retained or saveable. Recreation
starts with menu closed and no unfinished seek. Session/intent invalidation and
cancellation retain the existing seek guards. PageIfCurrent remains the sole
seek dispatch and controller remains the definitive atomic authority.

## Focus and accessibility

ReaderButton accepts an optional Modifier so the Options button can own a
Reader-local FocusRequester. Menu dismissal and fit selection request focus back
to that available visible button; invalid/unavailable or hidden chrome does not
request it. Focus return is presentation-only and emits no ReaderAction.
Page key handling remains attached to its sibling page node; button/menu/slider
keyboard events are not redirected into the page. No IME, editor or modal is added.

Existing localized show/hide custom accessibility actions, confirmed/unpresented
page labels, resource-backed seek descriptions, recovery overlay, safe insets,
48dp Slider thumb and button heights are unchanged. No new visible strings or
linguistic behavior was required. Source/compilation review is not TalkBack or
manual focus acceptance. Light/dark, large fonts, short viewports, keyboard and
actual Activity recreation still require owner execution at the new revision.

## Regression mapping

All existing 48 ReaderScreenTest methods remain enabled. Four appended methods:

| Method | Intended proof (compiled, NOT RUN here) |
|---|---|
| realActivityRecreationRestoresHiddenChromeForSameIntentOnly | ActivityScenario.recreate retains hidden chrome, same opening authority and one lease; a new intent resets visible |
| recreationDismissesOptionsAndResetsUnfinishedSeekWithoutNavigation | Open menu is not restored; a held drag is discarded across actual Activity recreation with no guarded action |
| optionsDismissalRestoresButtonFocusWithoutPageKeyLeakageOrRendering | Keyboard Escape returns to Options; button arrow does not navigate the page or render |
| freshPresentationEntryStartsVisibleEvenForSameIntentValue | Normal foreground composition removal discards chrome preference, without another open/render |

ReaderScreenTest now contains **52 methods**. ReaderTestComposition adds a bounded
ActivityScenario recreation hook: a temporary Application lifecycle callback
reinstalls the same test content on Activity start, before the resumed frame ends
the framework retention window, and unregisters in finally. The exact same
install call site is used initially and on recreation. No Activity references
are retained in production state. This is actual Activity recreation, not a
recomposition or StateRestorationTester simulation.

The synthetic Reader controller remains test-owned across that recreation. These
methods verify real Android/Compose recreation with synthetic PDF sessions;
they are **not** production route reconstruction, OS process death or SAF tests.
Existing host/native lifecycle integration remains separate and unmodified.
The foreground removal method tests presentation lifetime, not real host Back.

Existing coverage reused without weakening:

- hiddenPreferenceSurvivesFitPageAndZoomButNewIntentShowsToolbar and chrome toggle
  tests: page/fit/zoom lifetime, no rendering solely for chrome, show/hide action.
- tabTraversalReachesControlsAndMenuFitWithoutCapturingPageShortcuts and keyboard
  tests: toolbar/menu/page focus and local shortcut ownership.
- seekCancelPreservesPageFitZoomAndFrame, replacement, drag and first/last tests:
  canceled/stale target rejection, no intermediate render, [0,7,0], one lease.
- trimCanAwaitGraphicalDetachWithoutBlockingMainAndResumeOnlyAfterHostFinishes,
  retirement and graphical lifetime tests: existing Reader ownership boundaries.
- hidden recovery, large-font/dark, real resize and requested/presented tests:
  reachable recovery, 48dp control, actual dimension changes and no false position.

## Actual validation and provenance

Existing Linux x86_64/JDK25/SDK37/Gradle9.8/AGP9.5 environment:

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

Debug build/instrumentation compilation/Lint PASS. Full JVM **651 PASS**, zero
failures/errors/skips; contract coverage gate PASS. Python **492 PASS**, zero
failures/skips. Localization PASS, unchanged 160 IDs; canonical fixtures **15 PASS**
with checksum/structure/render checks. Scope/whitespace PASS. The final fourth
Android test was followed by successful instrumentation compilation/Lint; no
production/JVM changes followed the successful full JVM run.

Initial compile attempts failed on two positional Back callbacks after adding the
optional modifier, then on scenario access through the helper's generic TestRule.
Named onClick arguments and an explicit typed caller recreation callback corrected
those compile errors. Final compilation passes; these are not Android runtime
failures or successful executions of the new tests.

Existing external JaCoCo reporting was reused with matching full-suite data:

```bash
bash ./gradlew -I /tmp/l-m3d-coverage.init.gradle :app:lM3dReport \
  --offline --no-configuration-cache --console=plain --max-workers=4
```

Unchanged pure seek logic: **16/16 lines, 54/56 branches** (100% / 96.43%). New
retention/focus Compose integration coverage is **NOT MEASURED / BLOCKED**, not
inferred from that pure scope or historical CI. No extracted business policy,
threshold change or parallel reporting framework was added. Applicable UI
coverage and owner acceptance remain outstanding.

Local logs: /tmp/l-m3d-gradle.log, /tmp/l-m3d-final-gradle.log,
/tmp/l-m3d-verified-gradle.log, /tmp/l-m3d-final-compile.log,
/tmp/l-m3d-python.log, /tmp/l-m3d-localization.log, /tmp/l-m3d-fixtures.log,
/tmp/l-m3d-coverage/report.xml and full JVM XML under app/build/test-results/.
These are not Git inputs. Committing docs is not another test execution.

## Boundaries and owner handoff

No protected host, navigation, controller, presenter, frame, SurfaceView, storage,
PDF, manifest, Gradle, CI or localization changes. No shared contract request is
currently necessary for Boolean restoration because the installed Compose store
already supplies the required Activity lifetime. If actual production host
composition replacement fails the retention assumptions, document that boundary
and request a scoped owner contract rather than serializing document identity.

**API26 RUNTIME NOT RUN**: no authorized runnable emulator and no /dev/kvm.
The owner should execute the four new methods then all 52 ReaderScreenTest methods
on the published SHA with matching APKs, preserving raw JUnit/Logcat and device
attribution. Use the existing exact-method runner or the class selector:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.reader.ReaderScreenTest" --console=plain
```

This installs/runs packages and requires an explicitly selected owner-authorized
device. None was operated here. Retain real viewport-change evidence separately
from chrome/focus-only transitions. API27/recent, physical Android8.1 tablet,
TalkBack/linguistic, orientation and final owner acceptance remain pending.
No final L-M3D/Phase L acceptance, PR, merge, rebase, tag or release is claimed.
L-M4 and Phase M were not started.
