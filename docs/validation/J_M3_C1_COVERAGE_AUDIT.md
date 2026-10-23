# Phase J — M3-C1 existing coverage and evidence audit

2026-10-23. Branch: `test/mvp-stabilization-j`.
Audited revision: `8453743eaef05595a43537a685338d79be473dd2` (clean pre-audit checkout).

## Objective and method

Read-only source/artifact inspection followed by this document and a dated plan update.
No Gradle task, test suite, coverage collection, package installation, ADB/emulator
operation, evidence cleanup or worker change was executed. Existing artifacts were
not moved, regenerated or rewritten. No new `.exec`/`.ec` or coverage report was created.

Inspected the module Gradle configuration; local_coverage/local-coverage.init.gradle;
persistence_coverage/PersistenceCoverageInputs.java; local_e2e/local_test_evidence;
verify_test_results; CI workflow; worker sources/AIDL/manifest; policies and historical
coverage/acceptance documents. Read-only XML/JSON, file hashes and source inventories
were examined. The existing Java reader's `validate` entry point was invoked directly
against existing compiled classes and existing binary files, without its Python/Gradle
wrappers or ADB. Its probe model is calculated in memory; no instrumented app was run.

**No current source-attributed coverage percentage is VERIFIED by this audit.**
A compatible binary is not, by itself, a complete execution/source provenance chain.
Functional JUnit success from M2/M3-A/M3-B does not supply missing coverage probes.

Status terminology: VERIFIED means compatible authenticated measurement; AVAILABLE_UNVERIFIED
means retained evidence lacks complete attribution/integrity validation; NOT MEASURED
means no usable authentic measurement; BLOCKED identifies a missing prerequisite.
No absent worker data is represented as zero coverage.

## Existing mechanisms, scopes and gates

All use the existing JaCoCo **0.8.14**, configured in the version catalogue/module.
Policy remains at least **90% LINE / 80% BRANCH** for testable new business logic.
A configured or historical gate is not proof of current attainment.

### A1 contracts — JVM

- `:app:contractCoverageReport` depends on `validateContractCoverageInputs`, which
  depends on `testDebugUnitTest`. `:app:verifyContractCoverage` depends on that report;
  `testDebugUnitTest` finalizes with the verifier. Calling a report/verifier can run tests.
- Uninstrumented Kotlin class root:
  `app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`.
- Includes all `com/abdev/partituraspdf/core/**`, `storage/api/**`, `pdf/api/**`;
  not SAF implementations, Compose UI or the Android PDF adapter. No explicit
  handwritten-class exclusions in this scope; standard JaCoCo filters still apply.
- Input: `app/build/jacoco/testDebugUnitTest.exec` only. Aggregate 90/80 gate for
  the combined scope, not independent per-package/source gates.
- XML: `app/build/reports/tests/testDebugUnitTest/contract-coverage.xml`;
  HTML directory: `contract-coverage` beside it.
- Plain Gradle input validation checks nonempty class selection and an existing exec,
  not a frozen Git/APK manifest. Exact source attribution requires the stronger
  capture/class/session/JUnit checks from the existing local evidence infrastructure.

### B5 PDF Android adapter — JVM + Android

- `:app:pdfAdapterCoverageReport` depends on `testDebugUnitTest` and
  `validatePdfAdapterAndroidExecution`. The latter **depends on
  `connectedDebugAndroidTest`**. `:app:verifyPdfAdapterCoverage` depends on this report.
  These are not safe read-only audit commands and were not run.
- Includes **all `pdf/android/**`, including `pdf/android/isolated/**`**, not just
  the historical direct renderer. This selection uses the Kotlin class root above;
  generated AIDL Java classes live in the separate javac output and are outside that
  selected root. Preserve the existing Kotlin denominator and disclose this boundary.
- Inputs: the JVM exec plus
  `app/build/outputs/code_coverage/debugAndroidTest/connected/**/*.ec`.
- Aggregate 90/80 gate. XML/HTML: `pdf-adapter-coverage.xml` and
  `pdf-adapter-coverage` under `app/build/reports/tests/testDebugUnitTest`.
- Its default Android check establishes nonempty data, not PID/UID attribution or a
  same-build capture. Stronger binary checks are needed. The local init script's
  `localPdfCoverageReport` reuses this class/source selection while avoiding the
  connected-test dependency, after persistence input validation.

### C05 Room/DataStore persistence — same-build combined inputs

- `:app:preparePersistenceCoverageRun` depends on debug/test APK compilation and
  `testPersistenceCoverageInputs` (real Java reader infrastructure regressions).
  It reads an actual API26 device before preparing a manifest; then **deletes/replaces
  generated exec, connected coverage/JUnit and its report output**. It is not an audit.
- `:app:validatePersistenceCoverageInputs` has no test/compile dependency, but calls
  ADB for API identity and writes a validation stamp. It is not device-free/read-only.
- `:app:metadataPersistenceCoverageReport` and `:app:preferencesPersistenceCoverageReport`
  depend on that validator. `:app:verifyPersistenceCoverage` depends on both reports,
  repeats provenance checks and writes the verified provenance/result.
- Scope: `data/metadata/**`, `data/preferences/**`. Explicit generated exclusions are
  only `DocumentDao_Impl` / `MetadataDatabase_Impl` and their `$...` families.
  Handwritten nested/coroutine classes remain included, with standard JaCoCo filters.
- Inputs: JVM exec, all selected real Android `.ec`, original compiled/test class
  inventories, APK/build/source identity and fresh exact-enough JUnit evidence.
  Baseline C05 enforces smoke plus minimum historical counts; the local coverage
  wrapper strengthens this to exact current JVM and Android method inventories.
- Positive actual Android execution of RoomDocumentRegistry,
  RoomDocumentMetadataRepository and DataStorePreferencesRepository is mandatory.
  Local full-namespace mode also requires native document/page/buffer execution.
- Java reader validates format, sessions, CRC64 class IDs, probe counts and duplicate/
  unexpected records. Python validates freshness, source/build/class provenance,
  XML class/source/line/branch denominators, hashes and counters. Each package **and
  each handwritten source group** has independent 90/80 gates, not an averaged waiver.
- Default output: `app/build/reports/persistence/{run,provenance,failure}.json`,
  `{metadata,preferences}.xml` and corresponding `.html` directories.
- `:app:auditPersistenceCoverage` reparses a completed retained producer run offline,
  without tests or ADB. It requires its exact recorded commit, compiled/test classes,
  execution/JUnit/XML bytes and hashes. It is not a new Android run. The wrapper can
  write failure.json on error; use a copy of retained evidence, never the sole original.
  No such Gradle command was run here.

### Local production coverage and per-feature reuse

Existing entry point: `python3 scripts/local_e2e.py run --suite coverage`.
It uses only `scripts/local-coverage.init.gradle`, inside the existing Runner.
It is a full **execution** workflow, not a report-only command:

1. Dedicated AVD ownership/native acceleration/API26 preflight and connection.
2. Forced matching debug/test compilation, actual APK manifest/DEX/runtime inspection,
   frozen source/build/APK/test/class identities and a clock-skew check.
3. C05 preparation and fresh full JVM execution with A1 finalizer; retain exec/JUnit.
4. Install inspected APKs, run instrumentation with an explicit app-private UUID
   `coverageFile`, require exact raw completion/JUnit inventory and coverage path,
   retrieve real binary data via `exec-out run-as`.
5. Reuse C05 reports/verifier, `localPdfCoverageReport` and
   `localProductionCoverageReport`; independently validate report class/source models.
6. Preserve partial inputs/logs before cleanup; remove only acknowledged test-owned
   output. Interrupted instrumentation can force-stop the test app; an emulator may
   be stopped only if this invocation launched it. These operations require approval.

Reports live under `build/local-e2e/reports/<timestamp-UUID>/coverage/` by default:
configuration/capture/classes/summary JSON, retained classes and APKs, `inputs/jvm.exec`,
`inputs/android/*.ec`, JVM/Android JUnit, `contracts.xml`, `pdf.xml`, `production.xml`,
HTML and `persistence/` reports/provenance. The local output must be confined inside
the checkout; existing `--home`/`--sdk` options select the already owned environment.

The selected compiled root is Kotlin-only; generated AIDL Java output is outside it.
Full-namespace membership is based on an actual main source path matching the class
SourceFile/package. Debug helpers/test code/resources/dependencies are not handwritten
main-source denominators. Main-associated companions/coroutines remain included;
standard Kotlin filters and the explicit C05 generated exclusions are preserved.
Production package/source measurements outside A1/B5/C05 are **informational** in this
runner, not a new all-production gate. In particular, its library/reader/settings
package outputs do not independently enforce the feature business-logic minimums.

Existing [F/G/H coverage reproduction](../acceptance/FGH_COVERAGE.md) supplies an
external init script with All and Logic feature projections and independent 90/80
logic verifiers. Only Android presentation/preview sources are excluded from Logic;
All retains them. This is reusable report design, not evidence that today's Reader
passes. Its tasks depend on the JVM task and must only consume matching fresh inputs.

### CI and evidence checks

The API26 CI job prepares C05, runs JVM tests/A1 finalization, executes the standard
plus controlled PDFium workflow, then verifies C05. Artifacts retain exec, `.ec`,
JUnit and persistence provenance. The workflow does **not** explicitly invoke the
B5 verifier or a production-wide threshold gate. A successful quality-gate status
is not a full-app or worker coverage result.

`verify_test_results.py` rejects absent/empty/failed/skipped JUnit with optional smoke;
it does not inspect coverage binaries, process IDs or source revision. The local
wrapper additionally compares exact source-derived method identities. Phase J
`j_capture` hashes source/build files and records revision/tree/dirty state/inventory;
`j_report` verifies attributed host evidence. Those host projections do not import
or manufacture Android coverage from an instrumentation PASS count.

## Current evidence and component matrix

| Component | Infrastructure / denominator | Existing evidence | Measurement status for current source | Remaining work |
|---|---|---|---|---|
| Core architecture contracts | A1 `core/**`, JVM aggregate | A1 XML + compatible JVM binary; no complete producer manifest | AVAILABLE_UNVERIFIED | Authenticate retained run/source/report chain or fresh captured JVM run |
| Storage/access contracts | A1 `storage/api/**` | Same combined A1 evidence; not SAF implementation coverage | AVAILABLE_UNVERIFIED | Package attribution within validated A1 report |
| PDF API contracts | A1 `pdf/api/**` | Same combined A1 evidence | AVAILABLE_UNVERIFIED | Package attribution within validated A1 report |
| PDF Android adapter | B5 `pdf/android/**`, JVM + real Android | PDF XML absent; retained `.ec` invalid | BLOCKED / NOT MEASURED for combined Android measurement | Compatible real Android capture, correct inventory and explicit B5 verification |
| Room metadata | C05 metadata package/source gates | No completed run/provenance/XML; failure record only | BLOCKED / NOT MEASURED for combined coverage | Obtain complete producer artifact or capture same-build JVM/API26 data |
| DataStore preferences | C05 preferences package/source gates | Same missing C05 provenance; JVM tests alone insufficient | BLOCKED / NOT MEASURED for combined coverage | Same capture; require positive Android adapter records |
| Library/Reader application logic | F/G/H external All/Logic reports; production informational model | Historical XML/provenance and D04 report; not current authenticated results | AVAILABLE_UNVERIFIED | Revalidate or regenerate matching scoped reports; Reader source changed |
| Settings logic | Same feature report design | Historical XML/classes; no original matching exec retained | AVAILABLE_UNVERIFIED | Reuse only after evidence chain validation; no automatic current PASS |
| UI/integration/SAF implementation | Main-source production inventory + Android probes | Executed selected JUnit documented; no valid current Android coverage | NOT MEASURED in combined current report | Attribute app-process `.ec`; keep business logic and UI scopes distinct |
| PDF worker client | B5/production model; JVM client/protocol tests + Android paths | JVM records exist but no authenticated component report | AVAILABLE_UNVERIFIED for JVM portion; Android NOT MEASURED | Validate client report separately from worker origin |
| Isolated PDF worker service | Instrumented bytecode possible; separate PID/UID | No worker-owned exported stream or provenance | BLOCKED / NOT MEASURED | Separately approved process-attributed collection design |
| Full handwritten production aggregate | `localProductionCoverageReport`, informational | No retained production.xml/current full run | NOT MEASURED; Android/process prerequisites BLOCKED | One compatible capture plus explicit worker limitation; no global 90/80 claim |

Zero-branch groups are NOT APPLICABLE for branch percentage, not automatically fully
covered. Native PDFium C/C++ internals are outside JaCoCo JVM bytecode measurement;
functional native diagnostics are not native code coverage percentages.

## Retained artifacts inspected without regeneration

- `app/build/jacoco/testDebugUnitTest.exec`: 239,468 bytes, modification time
  2026-10-21 12:33:54 UTC. SHA-256
  `f6086d338363002d98e2e4b220fdf60ea3ed1d8cf58f8fdf99a7fecf553171c6`.
  Existing Java reader accepts its real sessions/class IDs/probe counts against the
  current compiled app/test inventory. This is binary compatibility validation,
  **not complete source/run attribution**. No frozen producer capture binds it to a
  verified Git revision and the XML report; no new coverage percentage is certified.
- A1 XML at the path above exists and shares session start/dump timestamps with
  that binary. SHA-256
  `a69395698562e4c54497e10270f7dbf71fb9b4cbea9d9e24038baf92d440c476`.
  Its counters can be read, but no full immutable producer manifest/reanalysis chain
  was verified. Do not promote an XML file or mtime alone to current VERIFIED coverage.
- Current JVM JUnit XML contains **631 unique completed methods**, no failures/errors/
  skips; the source inventory is also 631. This is existing test evidence, not tests
  executed by this audit and not proof of Android/process coverage.
- The retained `app/build/outputs/code_coverage/debugAndroidTest/connected/**/*.ec`
  is **49 bytes of a run-as unknown-package error message**, not a coverage stream.
  SHA-256 `1e2b938a8158c1d36848916af67b7d5099e862d2875874b3cbd97b791c6604f0`.
  The existing Java validator rejects it with missing JaCoCo header. Preserve it as
  failed collection evidence; never merge it with the valid JVM exec or call it 0%.
- `app/build/reports/persistence/failure.json` records validate failure because run.json
  is missing. Both run.json and provenance.json are absent; metadata/preferences XML
  and B5 XML are unavailable. There is no completed C05 run to offline-audit here.
- Three 2026-10-15 `build/local-e2e/reports/*/coverage/summary.json` records are BLOCKED
  at acceleration preflight with coverage NOT RUN and empty measured components.
  A separate configuration.json advertises tool versions/classes, not execution PASS.
- `build/diagnostics/d04/controller-coverage.xml` and `app/build/jacoco/d04ReaderTests.exec`
  are retained narrow D04 evidence. They cannot replace a complete current Reader/
  production report; no matching immutable current producer chain was established.
- `/tmp/pre-i-coverage/` contains six historical All/Logic XML files and provenance.json,
  attributed to `46c1b117dcee26902e675426755de551bbb68e01` by the historical register.
  Their LINE/BRANCH totals agree with that register, but **all six current XML hashes
  differ from its captured hashes**. Current compiled class hashes match only 103/106
  Library and 43/65 Reader entries; all 30 Settings entries match. The original exec
  hash in that record differs from the current JVM exec. Partial matches do not repair
  provenance. Do not rewrite the manifest or promote those reports to current PASS.
  Git confirms ReaderController changed since that source revision; Library/Settings
  source equality alone does not prove a compatible complete execution artifact.

## Historical measurements — retained at their original revisions

[Local compatibility validation](../LOCAL_COMPATIBILITY_VALIDATION.md) records an
owner macOS coverage run at `87b864de296563649a4f80e15b55e30f75e26836` with four scoped
A1/B5/C05 results. Its original report directory is absent here. That document explicitly
attributes terminal excerpts to the owner and requires retaining original binary data.
Its old denominators and percentages are not copied as current Phase J measurements.

[Pre-I F/G/H coverage](../acceptance/FGH_COVERAGE.md), its
[closeout](../acceptance/FGH_PRE_I_CLOSEOUT.md), and the
[Phase I acceptance record](../acceptance/I_APP_MVP_ACCEPTANCE.md) contain historical
JVM logic/whole-feature counters with their own source context. They document prior
measured work, not missing Android or isolated-worker attribution today. None is
retroactively relabeled. Their scoped/classification rationale should be reused.

M3-A/M3-B runtime reports, including the latest local AAB/offline results, contain
functional evidence, not a reusable authenticated coverage stream. No evidence indicates
that the later selected runs produced a compatible real `.ec` retained here.

## Isolated PDF worker attribution

[Worker feasibility](../acceptance/PDF_WORKER_COVERAGE_FEASIBILITY.md) remains applicable:

- Manifest declares nonexported `:pdf` service with `isolatedProcess=true`.
  PdfWorkerService checks its UID differs from the application UID and can kill its
  own process on retirement/quarantine; graceful destruction is not guaranteed.
- IsolatedPdfReadEngine/Session and PdfWorkerConnection are app-client owners. Lifecycle,
  call cancellation and limit policies have JVM tests. Their execution is not service
  execution. Protocol, errors and pixel-wire helpers are shared; class identity does
  not say which process executed a probe.
- PdfWorkerService owns AndroidPdfReadEngine/native rendering on the production route.
  Direct renderer instrumentation can execute those same adapter classes in the app
  test process. Passing direct or client tests cannot attribute them to the worker.
- IPdfWorker exposes identity/open/info/render/closeSession/retire, with no coverage
  snapshot/export capability. Source review finds no worker agent dump. The app
  runner's EMMA/JaCoCo dump plus app run-as cannot export the other isolated UID's
  in-memory probes. Class-ID compatibility does not establish PID/UID origin.

Authentic worker coverage is **BLOCKED / NOT MEASURED today**. A debug/test-only bounded
capability export, worker-internal snapshot, PID/UID/start/epoch/run/APK/class attribution,
crash/truncation handling and release-isolation tests require separate approval. Do not
change AIDL/Binder, manifest, Gradle, isolation or lifecycle as an automatic M3-C2 step.
A production aggregate may display missed service lines because no probes were exported;
that is not an authentic measurement of zero worker coverage. Similarly, a direct-native
record cannot certify worker execution merely because a class name matches.

## Collection readiness defect: ordinary versus controlled diagnostic inventory

The current source contains **432 Android methods**: **418 standard** and **14 assigned
fresh-process diagnostics**, derived read-only using the existing diagnostic inventory
helper. That helper also defines **29 controlled invocations**. The production isolation
classes' existing 24 standard tests remain standard acceptance.

CI's `scripts/run_pdfium_ci.sh` excludes the FreshPdfiumProcess annotation from the
standard Gradle run and invokes controlled diagnostics separately. By contrast,
`CoverageRun.prepare()` uses the complete android_inventory and `android()` launches
unfiltered AndroidJUnitRunner with coverage arguments, without that annotation exclusion.
Thus the historical L03 command cannot be recommended unchanged for today's source:
it would mix process-sensitive diagnostics into one process and lacks their required
fresh-process evidence. This is a confirmed source-level compatibility gap, not a new
runtime failure or authorization to suppress diagnostic acceptance.

## Minimum recommended M3-C2 — approval required, not executed

### Required first: reuse and repair readiness, before any recollection

1. Ask the owner for the complete existing source-attributed API26 CI/local coverage
   producer bundle, if available: run/provenance JSON, exact producer commit, original
   pre-instrumentation main/test classes, both APK hashes/bytes, JVM exec, Android ec,
   raw and exact JUnit, XML/HTML and build/tool versions. Prefer existing evidence over
   running tests. Validate it in an isolated copy at its recorded commit; never edit
   provenance to match this documentation SHA or combine builds. If it reproduces the
   required scopes, reuse it and skip corresponding collection.
2. If recollection remains necessary, separately authorize a **minimal compatibility
   correction** to the existing local coverage runner and its host regressions:
   reuse `pdfium_diagnostic_evidence.test_inventory`, select the 418 current standard
   methods/exclude FreshPdfiumProcess exactly as existing CI does, and bind that exact
   selection to capture, raw/JUnit and APK inventory. Keep all 14 diagnostics and their
   29 controlled invocations separate; no fake execution, removed tests or relaxed gate.
   Candidate files: `scripts/local_coverage.py` and
   `scripts/tests/test_local_coverage.py`; reuse existing helpers, no parallel framework.
   Preserve whole production denominators and existing A1/B5/C05 thresholds.
3. Explicitly approve exact dedicated disposable API26 AVD/home/serial/ABI and build,
   installation, ordinary test execution, possible bounded owned-worker loss tests,
   test-owned cleanup/force-stop and start/stop behavior. The locale-run Tablet2 or a
   discovered device is not automatically a runner-owned AVD. Require the existing
   provisioning manifest and config/index ownership checks; never adopt/reprovision
   an unknown device. No physical tablet or destructive backup/grant operations.

### Conditional minimum execution: one API26 capture, not three compatibility reruns

Only after steps above, using the existing approved local home/SDK configuration:

```bash
# FUTURE OWNER-AUTHORIZED commands; NOT executed by M3-C1.
python3 scripts/local_e2e.py doctor
python3 scripts/local_e2e.py run --suite coverage
```

Do **not** run the second command on the unchanged collector identified above.
If the accepted home/SDK differs, use its existing `--home`/`--sdk` global options
before the command, with output inside the checkout; no new AVD is implied.
This is one justified frozen JVM/API26 capture: currently 631 JVM and 418 standard
Android methods after the compatibility correction, one owned API26 device, actual
counts re-derived from the then-approved source. No API27/36 repetition is required
merely for app-process coverage. Real worker cross-API validation is a separate proposal.

This single capture can feed A1, both persistence scopes, B5 client/direct adapter and
the informational production/UI/SAF/F/G/H inventory. Existing feature All/Logic reports
can consume that same frozen JVM data where independent business-logic gates are
needed. Reuse the exact external script documented in FGH_COVERAGE.md (already locally
present), rather than invent a second runner. Its report/verify task names are:

```text
:app:libraryAllCloseoutReport :app:readerAllCloseoutReport :app:settingsAllCloseoutReport
:app:libraryLogicCloseoutReport :app:readerLogicCloseoutReport :app:settingsLogicCloseoutReport
:app:libraryLogicCloseoutVerify :app:readerLogicCloseoutVerify :app:settingsLogicCloseoutVerify
```

Those tasks are registered only via that external init script and depend on JVM tests;
confirm matching inputs and unchanged/up-to-date task state before report reuse. If
invoking them would rerun tests, approve that separately rather than assume report-only.
Do not create new coverage inputs solely to increase a project-wide percentage.

### Existing validation/analysis commands and side effects

| Future command | Test/device consequences |
|---|---|
| `bash ./gradlew :app:auditPersistenceCoverage --console=plain` | Completed producer artifact only, exact recorded commit; no tests/ADB. May write failure.json; use a copy. |
| `bash ./gradlew :app:validatePersistenceCoverageInputs --console=plain` | No test dependency, but live API26 ADB identity and validation output writes; requires captured run. |
| `bash ./gradlew :app:verifyPersistenceCoverage --console=plain` | Captured inputs -> XML/HTML and package/source gates; live identity checks; does not collect tests itself. |
| `bash ./gradlew :app:verifyContractCoverage --console=plain` | Can invoke full JVM suite and finalizer. |
| `bash ./gradlew :app:verifyPdfAdapterCoverage --console=plain` | Can invoke JVM and connected Android instrumentation; never use as a read-only shortcut. |

The local init script also exposes `localPdfCoverageReport` / `localProductionCoverageReport`
without connected-test dependencies. Let the existing coverage runner supply their
confined output/serial/Python properties and captured classes; do not handcraft an
unattributed invocation that bypasses validation.

### Pass boundaries, preservation and optional work

- Gates that fail must remain FAIL. In particular B5 now includes isolated sources:
  app-process collection cannot prove service coverage and may fail its aggregate gate.
  Do not exclude the service or count JVM/shared/direct probes as worker execution.
  Missing worker attribution remains a formal M3-C/Phase J issue requiring owner
  resolution, not an automatic waiver or promise that one run will pass everything.
- Preserve current generated inputs and failed captures before any prepare step;
  it deletes generated paths. Use immutable unique run outputs, do not clean historic
  reports, edit concurrently, reset branches, rewrite timestamps or mix class IDs.
  Preserve partial raw/JUnit/binary/failure evidence; stop after a functional failure,
  do not automatically rerun assertions. Restore only explicitly approved settings.
- M3-C3 can document verified app-process scopes and remaining blockers; it cannot
  certify complete worker/full-app coverage or formal Phase J acceptance while those
  requirements are unverified. No thresholds or policies may be relaxed for closeout.
- Optional, separately authorized improvements: worker export bridge under the existing
  feasibility design; API27/36 coverage only for a demonstrated attribution/compatibility
  need; additional meaningful tests only after a validated report identifies a real gap.
  Memory/performance/endurance work and policy changes are outside this audit.

## Recommendation and audit validation

Proceed first with owner review of this audit and retrieval of any retained current
producer artifacts. If unavailable, approve the narrowly scoped collector-inventory
compatibility fix and one API26 capture plan. Do not execute the legacy broad command
unchanged, repeat all compatibility/locale/offline campaigns, or start a worker bridge.

Only this file and the dated J_PHASE_PLAN.md addendum change. Documentation links,
artifact/source references, privacy and whitespace are validated. Historical coverage
percentages are retained in their original documents; this audit generates none.
J08 remains **DEFERRED TO PHASE R**. M3-C2, M3-C3, M4 and Phase J are not marked PASS.
No commit, push, branch rewrite or merge is authorized or performed by this task.
