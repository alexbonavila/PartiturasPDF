# L-M3A atomic guarded page navigation — 2026-11-02

Branch: `feat/reader-advanced-l`; isolated worktree `/workspace/PartiturasPDF-L`.
Starting revision: `513a0657eed44b291c381b0e10781898e06a0155`.
This report records local execution against that base plus the exact L-M3A source
and tests committed alongside it. No execution is relabeled by committing.
Owner review is still required; this is not Phase L acceptance or merge readiness.

## Implementation and atomicity

ReaderAction adds `PageIfCurrent(index: Int, expectedIntent: ReaderOpenIntent,
expectedSession: ReadSessionInfo)`. Existing Page and Step actions are unchanged.
The new action is forwarded by the existing host contract without host changes;
it is not wired to any Compose UI in L-M3A.

ReaderController handles the action inside its existing synchronized dispatch lock:

1. Existing global retired/owner-inactive checks apply.
2. Reject inactive Reader or missing authoritative Ready.
3. Require an eligible lifecycle phase and matching published intent/session using
   the existing L-M2 context model.
4. Independently match the authoritative active session's info to expectedSession.
5. Require the index within that matching session's bounds.
6. Call the existing requestPage exactly once after those checks.

Matching covers intent sequence/request and session ID, source generation, page
count and revision metadata through existing immutable value equality. Source
generation does not certify PDF content. Stale or invalid requests return before
changing requested/presented page, failure, checkpoint, frame or scheduling state.
The existing requestPage no-op handles an already selected page.

The check and request are atomic with controller-owned state transitions under
its lock. This does not authorize concurrent external route mutation, detect
external PDF writes, or prove a native session will never fail after acceptance.
Existing host quiescence/route ownership and native failure handling still apply.
Legacy unguarded Page remains deliberately unguarded for compatibility. The
future page-jump dialog must use PageIfCurrent after current UI validation.
No native open, checkpoint or graphical acknowledgment is synthesized here.

## Deterministic regression evidence

New ReaderGuardedNavigationTest contains five executed JVM tests using existing
ReaderFixture/TestSession/ReaderPresenter doubles, not real Android rendering:

- sameIntentReplacementRejectsCapturedSessionThenAcceptsCurrentSession: retain
  intent, quiesce, reopen as B, reject captured A without side effects, accept B
  with one render and no extra opening due to guarded navigation.
- wrongExpectedFieldsAndInvalidIndicesHaveNoSideEffects: older sequence, changed
  document request, ID, generation, count, revision and negative/out-of-range
  indices preserve the exact state object and existing render/open counts.
- authorityIsCheckedEvenWhenPublishedMetadataStillMatchesExpectation: a deliberately
  mutable test adapter changes its authoritative info after publishing. Rejection
  proves that published metadata alone is insufficient; this is a defensive
  synthetic case, not a claim that production adapters mutate info this way.
- missingQuiescedRetiredAndErrorStatesCannotAcceptGuardedRequests: preserve idle,
  error, paused and retired states; the ordinary Page invalid-index error behavior
  remains unchanged.
- validGuardedJumpSchedulesOnceAndWaitsForGraphicalAcknowledgment: repeated requests
  for the selected page do nothing; no position acknowledgment occurs until the
  existing presenter draw/acknowledge and Presented action; subsequent relative
  Step actions still work.

Existing scheduling, relative navigation, lifecycle, session, frame-lease and
checkpoint regressions remained enabled and passed in the full suite.

## Commands and actual results

Linux x86_64, existing JDK25.0.4.1 / Android SDK37, Gradle 9.8.0, AGP 9.5.0:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 -B -m unittest discover -s scripts/tests -v
```

Debug build, instrumentation compilation, Lint and existing contract coverage
verification: PASS. Full JVM: **647 PASS**, zero failures/errors/skips, including
five new tests. Python: **492 PASS**. Localization: PASS. Canonical fixtures:
**15 PASS**, including structural/render checks. Scope/whitespace: PASS.

The older-intent regression was made explicit with expected sequence 0 versus
current sequence 1. The final JVM test source was then recompiled and the full
suite/Lint rerun successfully:

```bash
bash ./gradlew :app:testDebugUnitTest :app:lintDebug --console=plain --max-workers=4
```

No filtered-suite result substitutes for full-suite coverage. Source hashes in
`/tmp/l-m3a-source.json` identify the validated production/test bytes; local logs
are `/tmp/l-m3a-gradle.log`, `/tmp/l-m3a-final-jvm-lint.log`,
`/tmp/l-m3a-python.log`, `/tmp/l-m3a-fixtures.log`, and JUnit XML under
`app/build/test-results/testDebugUnitTest/`. These generated artifacts are not Git
inputs. Existing compiler deprecation warnings were not suppressed.

## Actual JaCoCo coverage

JaCoCo 0.8.14 consumed matching compiled debug classes and the final full JVM
`app/build/jacoco/testDebugUnitTest.exec`. External init-script reporting changes
no repository Gradle file, denominator policy or gate:

```bash
bash ./gradlew -I /tmp/l-m3a-coverage.init.gradle :app:lM3aVerify --offline --no-configuration-cache --console=plain --max-workers=4
```

Scope: `com/abdev/partituraspdf/reader/ReaderController*.class` and
`com/abdev/partituraspdf/reader/ReaderAction$PageIfCurrent*.class`, including nested
controller/coroutine classes with standard JaCoCo compiler filters unchanged.

| Scope | Lines | Branches |
|---|---:|---:|
| Controller plus new action | 371/375 — 98.93% | 362/435 — 83.22% |
| New pageIfCurrent guard method | 8/8 — 100% | 14/14 — 100% |

Both affected logic and the new guard exceed 90% lines / 80% branches. The existing
controller denominator was not reduced to hide misses. These are JVM measurements,
not UI or Android-process coverage. Reports: `/tmp/l-m3a-coverage/report.xml` and
HTML; final execution log `/tmp/l-m3a-final-coverage.log`.

## Scope and remaining acceptance

Only ReaderUiState.kt, ReaderController.kt, the new ReaderGuardedNavigationTest.kt
and this document changed. No Screen, PageSurface, Frame, render planning, host,
core, PDF, storage, library, resources, dependencies, Gradle or CI changes.
No presentation-owner token, toolbar, dialog, gesture adapter or L-M3B work.

Android runtime: **NOT RUN / BLOCKED**, no authorized runnable emulator and no
`/dev/kvm`. Instrumentation APK compilation does not imply device execution.
API26 and later compatibility, real-surface rendering and physical-tablet/manual
acceptance remain pending. No CI access/execution, push, PR, merge, tag or release.
L-M3B or subsequent implementation requires separate owner authorization.
