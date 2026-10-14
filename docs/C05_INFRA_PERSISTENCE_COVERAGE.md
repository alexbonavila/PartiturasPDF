# C05-INFRA persistence coverage implementation and review

Status: prepared locally for separate owner review; not published or merged.
Baseline and ending HEAD: `d0b1ca02890c0e68e470b5a237b7e7f1b0057d73`.
Local branch: `feat/c05-persistence-coverage-infra`.
The remote `feat/data-persistence-c` matched the baseline before branch creation.
No commit, push, PR creation, C06 implementation or application changes.
Repository-local future author/committer: alexbonavila <alexbonavila44@gmail.com>.

## Changed-file inventory

| File | Change / purpose |
| --- | --- |
| `app/build.gradle.kts` | Add isolated preparation, binary infrastructure tests, input validation, Room/DataStore reports, threshold verification and offline audit tasks |
| `.github/workflows/ci.yml` | Add preparation, JVM execution/verification and persistence verification inside existing API26 emulator; extend scoped evidence upload |
| `scripts/PersistenceCoverageInputs.java` | Use real JaCoCo parsing, class identities, probe counts, session records and compiled-class inventory |
| `scripts/persistence_coverage.py` | Capture provenance, bind fresh JVM/Android inputs and JUnit evidence, validate XML denominators and independent thresholds, reproduce retained evidence offline |
| `scripts/tests/PersistenceCoverageInputsTest.java` | Real JaCoCo writer/reader regression cases on temporary synthetic inputs |
| `scripts/tests/test_persistence_coverage.py` | Pure infrastructure policy tests for provenance, XML, counters, thresholds and missing/stale evidence |
| `docs/C05_INFRA_PERSISTENCE_COVERAGE.md` | This design, validation and publication-strategy report |

Existing contracts, C01-C05 production/tests, Room schema, dependencies, quality policies,
contract coverage and PDF adapter tasks remain unchanged. This separately authorized
infrastructure adds enforcement; it does not declare feature or phase acceptance.

## Coverage method and exclusions

The actual AGP 9.4.1 Kotlin debug output is
`app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`.
JVM data comes from `app/build/jacoco/testDebugUnitTest.exec`; Android data comes from
`app/build/outputs/code_coverage/debugAndroidTest/connected/**/*.ec`.
These paths were checked against the existing filesystem and C05 reports.
The real historical emulator path ends in `emulator-5554/coverage_data/coverage.ec`.

Two reports include every class in `com/abdev/partituraspdf/data/metadata/**` and
`com/abdev/partituraspdf/data/preferences/**`, respectively. Only the known generated
`DocumentDao_Impl.class` / `DocumentDao_Impl$*.class` and `MetadataDatabase_Impl.class` / `MetadataDatabase_Impl$*.class` families are excluded.
They originate from Room/KSP, not handwritten DAO wrappers. No coroutine/lambda class,
error boundary, lifecycle helper, entity or mapping is excluded. External dependencies
are outside these package denominators. Standard JaCoCo 0.8.14 compiler filters remain;
no new filters or thresholds are introduced to hide branches. Its supplied filter omits the
compiler-only `MetadataMapping$WhenMappings` enum-dispatch table from reports. That class is
still inventoried and its execution ID/probes verified with JaCoCo's actual CRC64 identity;
the handwritten `when` remains in MetadataMapping.kt's full 93-branch denominator.

The Java tool uses `ExecutionDataReader`, `Analyzer`, `Instrumenter` and the actual
JaCoCo class ID/probe model. It checks each file's header, format, sessions and persistence
records. It rejects mismatched class IDs/probe counts, unexpected persistence classes,
one ID with different names and incompatible duplicate names across or within inputs.
Compatible repeat dumps are legal and their probes are unioned. No file-name/tool-version
shortcut establishes binary compatibility. Third-party/test execution records are not
report denominators; incompatible duplicate records are still rejected. Scoped test records
are identified by actual compiled JVM/Android test-class inventories and have their IDs/probes
validated, not ignored through a `*Test` naming heuristic. Unexpected production records still fail.
Test-class fingerprints are captured and rechecked alongside production fingerprints.

The debug inventory records class-file SHA256, JaCoCo ID, probe count, source file and
executable line/branch model. Before and after capture, that complete inventory must match.
The producer run/attempt/job/SHA is also bound when running in GitHub Actions, rejecting
replayed provenance from another CI build even when its compiled bytes are identical.
Each input must be freshly generated after preparation, have a current session and contain
execution records. Device clock drift has an explicit five-second tolerance; freshness also
requires file timestamps, run provenance and exact class-file hashes. Inputs are cleared
before execution and cached test results cannot satisfy the capture.
Android must have positive probes for all three real adapter classes:
RoomDocumentRegistry, RoomDocumentMetadataRepository and DataStorePreferencesRepository.
An unrelated file, nonempty garbage file or all-false probe record cannot satisfy that proof.

JUnit XML must exist, be fresh, have no failures/errors/skips/disabled tests and include the
unchanged smoke tests. Duplicate/unnamed cases are rejected. Minimum execution counts are
128 JVM and 203 Android tests; additional future tests are allowed, not used to lower this baseline.

Both scopes and every handwritten source group independently require LINE >=90% and
BRANCH >=80%. A zero-branch group is N/A for branches only; executable lines still gate.
Class-level counts are recorded for audit. Source groups avoid imposing standalone source-line
targets on compiler-only continuation classes while retaining their lines/branches in the scope.
Expected source/class sets and denominators are derived from the compiled model, not from
convenient report totals. Missing counters/groups, inconsistent per-line evidence, altered
counters and a passing package average hiding an insufficient source group fail closed.
The provenance includes missed lines, partial lines and missed branches for insufficient groups.

## Exact CI order and task dependencies

Only the existing `instrumented-api26` emulator script is extended:

1. Retain the existing actual-device API26 assertion.
2. `:app:preparePersistenceCoverageRun` builds debug APK/test APK/JVM test classes and runs
   `:app:testPersistenceCoverageInputs`; it checks the real device API and inventories classes.
   After prerequisites succeed, it clears only generated coverage/JUnit/report paths, then
   writes `app/build/reports/persistence/run.json` with a run UUID/start time and build inventory.
3. Run `:app:testDebugUnitTest --stacktrace --no-build-cache` and the unchanged JVM XML smoke
   verifier. Deleted output files force execution; `--no-build-cache` prevents restoration of an
   older exec/XML pair. Contract coverage verification remains active.
4. Retain `:app:connectedDebugAndroidTest --stacktrace` and its existing XML smoke verifier.
5. Run `:app:verifyPersistenceCoverage`. Its dependencies are input validation followed by
   `metadataPersistenceCoverageReport` and `preferencesPersistenceCoverageReport`, then the
   Python independent package/source gate. These report/validation tasks have **no compilation
   or connected-test dependency**: they consume the captured build, never launch an emulator or
   silently rebuild incompatible classes. Reports always regenerate after fresh input validation.

Argument providers defer JaCoCo classpath resolution and are compatible with configuration cache.
Input validators are execution tasks without cached outputs. One failed validation or threshold
returns nonzero, failing instrumented-api26 and the unchanged aggregate quality-gate.
All original job names, triggers, PR/main push behavior, pins, smoke commands, localization,
PDF fixtures, API27/36 configuration and existing seven checks are preserved.

## Provenance, artifacts and independent reproduction

Outputs beneath `app/build/reports/persistence/`:

- `run.json`: capture UUID/time, commit, API, Gradle/AGP/JDK/JaCoCo versions, non-secret CI
  run/attempt/job/SHA identity and production/test class inventories.
- `provenance.json`: validated inputs with original and relative paths/hashes, sessions/probes,
  JVM/Android XML hashes/counts, captured classes, class/source/package coverage and PASS/FAIL.
- `metadata.xml`, `preferences.xml` and HTML directories `metadata.html/`, `preferences.html/`.
- `failure.json` when preparation, input validation or verification fails.

The existing always-run API26 artifact step retains these files, JVM `.exec`, Android `.ec`,
JUnit XML/HTML and only scoped production and test debug-class directories (including their generated
files for independent reconstruction; those generated files are excluded from measurement).
Retention remains 14 days. No entire build directory, APK, application database, preference
protobuf, credential, PDF or unrelated device data is uploaded. No new application dependency
or Java/Python package is needed; the Java helper uses the existing JaCoCo/ASM runtime and the
Python tool uses the standard library and existing JUnit verifier.

To independently audit a completed run, check out its recorded commit, download its artifact
and unpack its build-relative paths underneath `app/build/`. Then run:

```bash
bash ./gradlew :app:auditPersistenceCoverage --console=plain
```

This task does not compile source or require a live emulator. It reparses retained inputs,
checks class IDs/hashes, recorded adapter execution/sessions, JUnit hashes/counts, XML counters
and the original gate result. Relative input paths allow a different checkout location.
It uses the existing JDK/SDK/Gradle toolchain and JaCoCo dependency resolution, but does not
call adb. An original below-threshold result is reproduced as FAIL. Offline auditing verifies
retained evidence; it does not certify a new Android test run. A full valid Android artifact
is not currently available, so complete offline reproduction remains NOT RUN here.

## Actual host validation

Environment: JDK 25.0.4.1, Gradle 9.8.0, AGP 9.4.1, JaCoCo 0.8.14, SDK37;
existing activation `/workspace/partituras-tools/activate.sh`.
No `/dev/kvm` and `adb devices` lists no attached device. No emulator was launched.

| Executed check | Actual result |
| --- | --- |
| `bash ./gradlew :app:kspDebugKotlin :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --max-workers=4 --console=plain` | PASS; application/test APK compiled, existing JVM results valid, existing contract gate retained |
| `python scripts/verify_test_results.py app/build/test-results/testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect` | PASS: 128 executed JVM cases, zero failures/skips |
| `python -m unittest discover -s scripts/tests -v` | PASS: 31 cases (18 existing +13 new policy tests) |
| `bash ./gradlew :app:testPersistenceCoverageInputs --max-workers=4 --console=plain` | PASS: 17 binary/identity cases using real JaCoCo writer/reader; configuration cache stored/reused |
| Actual JVM `.exec` reparsed | PASS: 9 production and 17 known scoped test records; all IDs/probe counts match |
| Actual C05 JVM reports revalidated against current compiled inventory | PASS counter/model consistency; same JVM-only 90/210 lines, 105/177 branches Room; 20/35 lines, 12/12 branches DataStore; gates correctly reject these JVM-only results |
| Historical 49-byte `.ec` passed to Java validator | Expected FAIL: missing JaCoCo header; never combined with real reports |
| `bash ./gradlew :app:verifyPersistenceCoverage --dry-run --console=plain` | PASS task graph; validation -> two reports -> verifier; no compiler/device-test task in graph |
| Existing PDF report/verification and offline audit task dry-runs | PASS registration; no device execution |
| `bash ./gradlew :app:verifyPersistenceCoverage --console=plain` without prepared run | Expected FAIL: missing run provenance; no fallback to JVM-only data |
| `bash ./gradlew :app:preparePersistenceCoverageRun --max-workers=4 --console=plain` without a device | BLOCKED / expected FAIL at actual API probe, before clearing prior evidence |
| `python scripts/validate_localization.py` | PASS en/ca/es |
| `python test-fixtures/scripts/verify_fixtures.py` | PASS 15 immutable fixtures |
| `actionlint .github/workflows/ci.yml` | PASS using official actionlint 1.7.7 binary with release checksum verified; tool kept outside checkout |
| `git diff --check`, per-new-file no-index whitespace, schema/source integrity | PASS |

Initial local API/configuration and fixture issues were corrected; final checks above supersede
those intermediate failures. Existing deprecation notices remain; no application/toolchain policy
was changed to conceal them. Tool logs remain outside the checkout in `/workspace/partituras-tools/`.

Infrastructure synthetic fixtures test the validator only: valid binary pairs, missing/truncated
files, class-ID/probe mismatches, incompatible duplicates and unknown classes, missing sessions,
stale build/commit/class provenance, missing Android probes/XML, duplicate/stale/failed/skipped
XML, exact thresholds, insufficient line/branch/source coverage, missing/altered counters and
zero-branch handling. Java fixtures live in temporary directories and are deleted; their
sessions are deliberately test-only and would not satisfy current real-run provenance.
Python policy bytes are not JaCoCo files and cannot pass the binary validator.
No synthetic execution data is written to or combined with application coverage directories.

## Hosted-only validation still pending

Real combined API26 capture, cross-runtime identity/probe compatibility, fresh session/XML
binding, complete report generation, actual 90%/80% attainment and offline artifact reproduction
remain NOT RUN/BLOCKED until the hosted hardware emulator runs this exact reviewed infrastructure.
A passing JVM/tool suite or APK compile does not establish Android execution or adapter coverage.
If real coverage is below thresholds, the new task must fail and retain the missing lines/branches.
Production/test changes to close those gaps require a separately scoped owner decision; none
have been added to manufacture a passing percentage.
Existing 203 Android test sources and 128 JVM tests remain intact. The seven required checks must
all pass on the eventual published revision. API27/36 phase-close and backup/security acceptance
remain separate pending decisions; C06 has not started.

## Standalone PR strategy — owner approval required before publication

Recommended smallest compliant sequence:

1. Owner authorizes publishing this isolated infrastructure branch and a **separate stacked PR**
   targeting `feat/data-persistence-c`, with only these infrastructure files in its diff.
2. Because normal PR triggers target only `main`, owner runs the existing **workflow_dispatch**
   CI on the infrastructure branch's exact reviewed SHA. The workflow already exists on main;
   dispatch can select the infrastructure ref and execute its additive workflow without changing
   triggers. Record the SHA, all seven job results, API26 XML and retained coverage artifacts in
   that standalone PR. Optional compatibility=true adds the existing API27/36 jobs if requested.
3. A manual dispatch does not create a new branch-protection rule or grant merge permission.
   Feature-branch protection may not require these contexts; the owner must verify the exact
   reviewed SHA's successful checks and resolve blockers before approving the standalone PR.
   If existing protection does not associate dispatch checks with the PR as required, stop for
   owner configuration/integration guidance rather than bypassing it.
4. Only the owner merges the approved infrastructure PR into the feature branch. Existing PR #10,
   whose base is main, then triggers ordinary PR CI at the new integrated feature SHA. Any merge
   or squash creates a new revision: prior dispatch evidence cannot replace that fresh run.
   Final main integration remains the owner's separate decision after all acceptance gates.

Alternatives: a standalone infrastructure PR based directly on current main is not equivalent;
this branch contains C00-C05 ancestry and would bundle persistence changes into its main diff.
Cherry-picking infrastructure onto main without the persistence implementation would fail closed
on absent scopes and cannot yield valid coverage. A main-first sequence therefore needs separate
owner approval of prerequisite feature integration and gate timing. Broadening PR triggers to
feature bases would be another separately authorized workflow change, not implemented here.
No CI or protection bypass and no direct main push is recommended.

Publication, standalone PR creation, manual dispatch and merge require subsequent owner approval.
This task stops with seven files changed, unstaged and uncommitted on the isolated local branch.
