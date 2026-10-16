# Current project status — PartiturasPDF

**Snapshot date:** 2026-10-16. **Scope:** repository state and evidence review; this page is an index, not a new development authorization or an acceptance waiver.

- Repository: [alexbonavila/PartiturasPDF](https://github.com/alexbonavila/PartiturasPDF).
- Verified `main` baseline: `f94841f9f1eead508dd81b670d0943f3b3ae19bb` (2026-10-16). Phase E merged through [PR #12](https://github.com/alexbonavila/PartiturasPDF/pull/12) (`c5db5b4c`); the governance/status correction through [PR #13](https://github.com/alexbonavila/PartiturasPDF/pull/13) (`f56b6193`); L01–L04 automation through [PR #14](https://github.com/alexbonavila/PartiturasPDF/pull/14) (`f94841f9`). Integration is complete for those PRs; formal acceptance remains separate.
- Latest documented native macOS application/test evidence is at `87b864de296563649a4f80e15b55e30f75e26836`, before PR #14's merge. Post-merge hosted evidence below is at `f94841f9`; it does not establish a new native Windows/macOS run. See [L04D](LOCAL_COMPATIBILITY_VALIDATION.md).

## Implemented capabilities versus user-facing features

| Area | Code currently present | Limit / acceptance status |
| --- | --- | --- |
| Phase 0 | Material 3 theme, three-language resources, CI policies, immutable PDF fixtures | Merged; historical phase acceptance recorded |
| A0 / A1 | Architectural specifications, core document/revision/geometry/result/preferences contracts and associated tests | Code exists, not merely a planned future phase; A0 catalogs also describe unimplemented designs |
| SAF (B storage) | SAF catalog, URI codecs, grants, seekable/non-seekable bounded read access, provider/recovery handling | Integration tests exist; synthetic L01 local SAF E2E passed; separate genuine manual external-grant acceptance remains |
| PDF (B5) | Android PdfRenderer-backed read/render engine and API contracts | Native rendering tests passed; this is **not** a user-facing reading screen or a PDF editor |
| C persistence | Room schema v1, registry/metadata repositories, DataStore preferences, Policy B backup XML and tests | Backend implemented and integrated into composition; real C06 backup/restore and D2D acceptance **NOT RUN / DEFERRED** |
| E integration | AppGraph/AppGraphFactory and SAF-to-PDF/persistence Android integration tests | Merged in PR #12; the [E reconciliation](integration/E_APP_COMPOSITION.md) retains coverage/manual limitations; `MainActivity` is still a starter greeting |
| L01–L04 | Opt-in dedicated API26 AVD SAF E2E, host/android/full orchestration, real JaCoCo combined coverage, multi-host testing | Integrated into `main` through PR #14; see revision-specific evidence below |
| Future F/G/H/I and user-facing score library, reader/settings UI, editing, annotations and Google Drive | Planned product scope, **not shipped** | Require separately approved feature tasks and relevant tests |

Do not mistake repositories, adapters or test harnesses for a finished library/reader/editor UX. This page does not claim that every production class has >=90% coverage or that all deferred external/physical scenarios passed.

## Confirmed test evidence

### Hosted CI: result pages rechecked on 2026-10-16

| Execution | Revision / event | Visible result |
| --- | --- | --- |
| [Main push CI #58](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38926693866) | `f94841f9`, push to `main` | PASS: build, lint, unit-tests, instrumented-api26, localization, pdf-fixtures, quality-gate. Optional API27/36 jobs were not requested; no compatibility PASS is attributed to this run. |
| [Compatibility CI #59](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38926745562) | `f94841f9`, workflow_dispatch on `main` | PASS: all seven mandatory jobs and compatibility-api27 / compatibility-api36. These are emulator results, not physical-tablet acceptance. |
| [PR #14 CI #57](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38925687779) | PR #14 pre-merge revision `36a52fb`, not the subsequent `main` merge SHA | PASS: all seven mandatory jobs; historical pre-merge evidence, superseded for current-main status by #58/#59. |

The public GitHub run/job pages establish these conclusions and revision associations. Raw job logs/JUnit XML and downloadable execution artifacts were **not inspected in this review**; API access remains unavailable. Consequently the previously recorded **165 JVM / 228 Android** counts are not independently remeasured counts for #58/#59. No GitHub settings or branch-protection verification is implied.

### Previously documented native and historical hosted evidence

- **Owner-reported native macOS Apple Silicon / API26 ARM64, SHA `87b864de`:** [L04D](LOCAL_COMPATIBILITY_VALIDATION.md) records `full` PASS/0: 246 Python infrastructure tests + 165 JVM + 228 Android + 4 authentic Android SAF stages using synthetic test folders = **643 heterogeneous checks/stages**; cleanup and diagnostics PASS. Its `coverage` run records PASS/0: 165 JVM, 228 Android, genuine JaCoCo JVM+Android. Those local artifacts were not independently rerun or newly parsed here.
- **Earlier owner-reported native Windows / API26 x86_64:** L02 `full` PASS at `27cef9f`; L03 `coverage` PASS at `9d10739d`. These are earlier revisions, not final-SHA native Windows execution.
- **Historical hosted [CI #38857215346](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38857215346), SHA `87b864de`:** L04D records all seven required jobs and API27/API36 PASS, 165 JVM and 228 Android tests on each API, 246 Python tests and 15 immutable PDF fixtures. Counts are attributed to that existing report, not a new artifact inspection.
- **Scoped combined JaCoCo line/branch coverage recorded in L04D:** A1 99.47%/99.43%; B5 PDF 97.71%/87.50%; C05 Room 98.57%/94.35%; DataStore 100%/100%. These are historical scope-specific measurements, not new measurements at `f94841f9`. See [L03](LOCAL_CODE_COVERAGE.md) for the approved scopes; no project-wide coverage claim follows.
- **Full `app/di` JVM + Android coverage remains unresolved.** Passing A1/B5/C05 gates does not establish composition-package coverage. The historical factory JVM-only diagnostic is not a combined report. P03 must examine authentic matching execution data, original bytecode, provenance and full-source counters, including constructor cleanup paths, before determining whether the existing 90% line / 80% branch criterion is satisfied. No new threshold, exclusion or gate is introduced.

## Outstanding acceptance and readiness work

1. P01/P02 reconcile current status and the [historical E audit](integration/E_APP_COMPOSITION.md). PR #14 is already merged; opening or merging it again is not a next step. P03 combined `app/di` coverage is **pending**; P04 boundaries and P05 deferred-acceptance consolidation are **not started** and need separate authorization. Any eventual readiness PR still requires current-head CI/current base, owner review and an explicit owner merge decision under [change control](CHANGE_CONTROL.md).
2. **Physical Android 8.1 (API27) tablet:** **DEFERRED / NOT RUN** until purchased. Owner's planned next step is a separately approved short-lived test branch from then-current `main`; no such branch is created by this documentation task. Physical-device evidence remains required by the [formal functional-phase acceptance rule](DEFINITION_OF_DONE.md), independently of hosted API27 PASS.
3. Real C06 backup/reinstall/restore and device-to-device transfer: **DEFERRED / NOT RUN**. Guarded acceptance on an expressly approved disposable environment must record exact restored preferences and excluded Room/private files; cloud transport results cannot prove D2D. See the [Phase C deferred register](PHASE_C_DEFERRED_ACCEPTANCE.md). No waiver or destructive execution is authorized here.
4. External SAF provider/grant/restart/revocation acceptance beyond the controlled local E2E flow, and real constrained-device resource/performance measurements, remain unverified as applicable. Owner-approved scenarios, provider/device identities, exact revision and retained results are needed to close these E acceptance limits; provisional runtime ceilings are not measured budgets.
5. F/G/H/I and library/reader/settings UI, editing, annotations and Drive remain future work requiring separately approved requirements and tests. Backend implementation and technical merges do not establish unconditional formal Phase E acceptance.

## Documentation authority and historical records

Use this page and [ROADMAP.md](ROADMAP.md) for **current status**. Use [PROJECT_SPECIFICATION.md](PROJECT_SPECIFICATION.md), [AGENTS.md](../AGENTS.md), [TESTING_POLICY.md](TESTING_POLICY.md), [CHANGE_CONTROL.md](CHANGE_CONTROL.md) and [DEFINITION_OF_DONE.md](DEFINITION_OF_DONE.md) for normative requirements. Historical A0, C05/C06, E and L01–L03 reports intentionally preserve what was known **at the date and SHA of each delivery**. A historical `NOT RUN` cannot erase a later verified PASS, and a later PASS cannot retroactively certify the earlier run.

**No policy, Android product behavior, quality gate or feature implementation is changed by this status document.**
