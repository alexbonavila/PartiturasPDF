# L-M3C-R2 IME viewport investigation — 2026-11-02

Branch: `feat/reader-advanced-l`. Starting implementation:
`e25908312d3a113d7d6e029bc028ed35f3f42f95`.
**Evidence-only fallback: correction NOT IMPLEMENTED; API26 verification pending.**
This report must not be read as successful runtime stabilization.

## Confirmed operational cause and provenance

The owner supplied the R1 ordered diagnostic findings in the R2 assignment:
1080×1884 → 1080×1459 → 1080×1884 viewport changes as the IME appears/disappears,
generations 1 through 7 and seven renders of the original page in the dismissal
test. The jump test has two guarded actions, one opening, ten renders and ten
acknowledgments, with maximum one active lease. Surface identity remained stable;
visibleBottom changed from 1920 to 1495, systemInsetBottom from 0 to 425, and
intermediate animation heights were observed. The historical class result remains
45 PASS / 2 FAIL / 0 skipped, 47 executed, as reported by the owner. Raw Logcat
and JUnit archives were not attached to this task; these are owner-provided
findings, not an independent replay performed here.

IME-driven viewport resizing is therefore the established operational cause in
the supplied evidence. The safe platform/window-policy correction is a separate,
unverified question. ReaderScreen onSizeChanged and Viewport dispatch, controller
render invalidation, frame retirement, graphical acknowledgment and checkpoint
ownership remain unchanged. Genuine changed dimensions must continue to render;
no viewport freeze, height filtering, debounce or acknowledgment deduplication is
introduced.

## Exact framework investigation

Inspected the installed Compose UI Android **1.10.4** classes.jar from the Gradle
cache, with javap, then retrieved the matching source artifact from Google's Maven
repository (`androidx/compose/ui/ui-android/1.10.4/ui-android-1.10.4-sources.jar`).
No dependency or repository build file changed. AndroidDialog.android.kt shows:

- DialogWrapper is remembered by parent view/density and disposed with its
  composition. DialogWindowProvider is the supported access to its Window.
- Dialog's SideEffect calls updateParameters on parameter updates.
- updateParameters unconditionally sets Window.softInputMode: with
  decorFitsSystemWindows=true it selects SOFT_INPUT_ADJUST_UNSPECIFIED (0);
  with false on API <31 it selects SOFT_INPUT_ADJUST_RESIZE (16); on API31+
  it selects SOFT_INPUT_ADJUST_NOTHING (48).

Consequently, setting decorFitsSystemWindows=false alone is not an API26 fix.
A one-time DisposableEffect override through DialogWindowProvider is insufficient
as a general solution because framework updates can overwrite it. This is a
source-level counterexample to that candidate's lifetime guarantee, not a new
runtime observation. A child SideEffect is also not accepted here as a guarantee
of ordering against all parent dialog parameter updates without execution evidence.

ADJUST_NOTHING is available on API26, but prevents automatic window adjustment;
using it alone does not establish that the editor and all actions remain reachable
above the IME, particularly in landscape/large-font layouts. IME-inset handling
must be verified on that platform rather than assumed from modern edge-to-edge
behavior. No speculative flag mutation, parent Activity policy, reflection,
listener workaround or replacement dialog implementation was installed.

## Decision and next correction boundary

No safe, sufficiently established implementation was produced in the authorized
scope during this pass. The assignment permits independently valid evidence work
in that case, so only this report is published. This does not establish that a
Reader-private correction is impossible or that an Activity change is necessary.
No protected-component authorization is requested on the current evidence.

The next candidate needs explicit dialog-window lifetime ownership that cannot
be overwritten by Compose updates, minimal adjustment-bit changes with prior-state
restoration, focus only after policy application, and independently observed IME
avoidance for the dialog contents. It must be tested through repeated opening,
editing/recomposition, Cancel/Back/Escape, large fonts and real physical resizing
before claiming the original failures fixed. A larger custom dialog/window owner
should receive owner design review before replacing the existing AlertDialog.
Activity-wide soft-input changes remain outside authorization.

The original strict frame, viewport/generation, render/open, lease and exact
acknowledgment assertions remain untouched. R1 ReaderRuntimeSequence diagnostics
remain available. The four separately reported CI-only visibility failures remain
outside this correction; no unrelated status/recovery layout or assertion changed.
L-M3D is NOT STARTED.

## Available validation

On the unchanged production/test source at the starting revision, using the
existing Linux x86_64/JDK25/SDK37/Gradle9.8/AGP9.5 toolchain:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
python3 -B -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

Build, instrumentation compilation, Lint and existing contract coverage gates:
PASS (unchanged Gradle tasks, including JVM/coverage, were UP-TO-DATE).
Retained matching-source JVM XML: **653 PASS**, zero failures/errors/skips; no new
JVM execution is claimed. Newly executed Python: **492 PASS**, zero
failures/skips. Localization: PASS, retaining R1's 164 count. Canonical fixture
checksum/structure/render checks: **15 PASS**. Scope/whitespace: PASS.
No new production business logic or coverage scope was introduced.

Local logs: /tmp/l-m3c-r2-gradle.log, /tmp/l-m3c-r2-python.log,
/tmp/l-m3c-r2-localization.log, /tmp/l-m3c-r2-fixtures.log. Framework source/bytecode
inspection artifacts are local /tmp/l-r2-AndroidDialog.kt and
/tmp/l-r2-dialog-bytecode.txt. These are not Git inputs. Committing this report
is not another execution and does not transfer results to a changed implementation.

API26 runtime: **NOT RUN / BLOCKED**, no authorized runnable emulator and no
/dev/kvm. The two failing methods and full Android class were not rerun here.
No device operation, installation or CI access occurred. Window-policy efficacy,
focus restoration, TalkBack, large-font/landscape and physical-device acceptance
remain unverified. No PR, merge, rebase, tag or release is claimed or authorized.
