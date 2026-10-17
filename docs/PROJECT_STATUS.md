# Current project status — PartiturasPDF

**Snapshot date:** 2026-10-17. **Scope:** repository state and evidence review; this page is an index, not a new development authorization or an acceptance waiver.

- Repository: [alexbonavila/PartiturasPDF](https://github.com/alexbonavila/PartiturasPDF).
- Historical readiness baseline: `f94841f9f1eead508dd81b670d0943f3b3ae19bb` (2026-10-16); current main is `121090a56ff6d2fdb98655eaf7104ee67d79caa4` after PR #15. Phase E merged through [PR #12](https://github.com/alexbonavila/PartiturasPDF/pull/12) (`c5db5b4c`); the governance/status correction through [PR #13](https://github.com/alexbonavila/PartiturasPDF/pull/13) (`f56b6193`); L01–L04 automation through [PR #14](https://github.com/alexbonavila/PartiturasPDF/pull/14) (`f94841f9`). Integration is complete for those PRs; formal acceptance remains separate.
- Latest documented native macOS application/test evidence is at `87b864de296563649a4f80e15b55e30f75e26836`, before PR #14's merge. Post-merge hosted evidence below is at `f94841f9`; it does not establish a new native Windows/macOS run. See [L04D](LOCAL_COMPATIBILITY_VALIDATION.md).
- Readiness branch `chore/pre-fgh-readiness` includes the P03 cleanup remediation at `618c9aa753e9597835b80c4862876e8d34e2fc85`, subsequently merged through PR #15. The owner reports native Windows/API26 combined coverage PASS at that exact revision; details below. [P04 interface boundaries](planning/FGH_INTERFACE_BOUNDARIES.md) are a planning deliverable awaiting owner review, not feature implementation authorization.

## Current shared-foundation delivery — 2026-10-17

PR [#15](https://github.com/alexbonavila/PartiturasPDF/pull/15) merged R0/P01–P05 into `main` at **`121090a56ff6d2fdb98655eaf7104ee67d79caa4`**; its seven mandatory CI jobs are owner-reported PASS (no new run ID/raw artifact inspection is asserted). This supersedes earlier readiness-branch/unmerged wording in the historical evidence below.

The owner approved D01–D11 in principle in the master shared-foundation task. Two independent branches start from that exact main: `chore/spanish-first-localization-policy` (only localization governance/validator/tests, no production resource migration) and `feat/fgh-shared-foundation` (actual Kotlin prerequisites and tests, no policy copied from A). See [the final contract freeze](planning/FGH_FINAL_CONTRACT_FREEZE.md) for current source signatures and ownership. These branches are delivered for independent owner PR review; no PR/merge or feature implementation is authorized in this assignment.

Implemented foundation: separately excluded durable selected root, validated folder navigation, central registration, registered/transient route lifetimes, authoritative SharedPreferences reader positions, heap-derived provisional resource budgets, automatic device locale policy and typed settings/host handoffs. Legacy Room position APIs/schema and DataStore language values remain compatible but inactive for new reader position/manual locale behavior. No automatic Room position migration/fallback is claimed. F/G/H screens and final I Activity/navigation/locale application are still unimplemented; native Android/combined coverage must validate the new code before acceptance. Historical P03 100% coverage is not a new measurement of this expanded foundation.

The eleven P04 decisions are no longer unapproved proposals; their earlier wording below is historical. The [P05 register](planning/PRE_FGH_DEFERRED_ACCEPTANCE.md) still governs open physical Android8.1, actual backup/restore/D2D, external-provider and performance acceptance. Spanish-first UI depends on independent approval/integration of Branch A; Branch B passes the unchanged existing localization rules and adds no visible strings.

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

### P03 native Windows/API26 coverage — recorded 2026-10-17

**Owner-provided evidence:** `python scripts/local_e2e.py run --suite coverage` returned PASS using the existing dedicated native accelerated API26 emulator at **`618c9aa753e9597835b80c4862876e8d34e2fc85`**. Report identifier: **`20261016T230454Z-941f1f3e04d0482ba9ea5063aeed730a`**. The owner extracted these counters from `coverage/summary.json`:

| Source | Covered lines | Covered branches |
| --- | --- | --- |
| AppGraph.kt | 9/9 — 100% | N/A |
| AppGraphFactory.kt | 37/37 — 100% | 14/14 — 100% |
| AppRuntimeLimits.kt | 7/7 — 100% | N/A |
| Entire app/di package | 53/53 — 100% | 14/14 — 100% |

The supplied source inventory reports zero missed source lines/branches. The factory's production-used internal helper is exercised for successful cleanup, original exception propagation, distinct suppressed cleanup failure and same-instance suppression avoidance. These measured counters meet the P03 remediation's numerical targets; they replace the earlier current claim that combined composition coverage is unmeasured.

**Attribution limits:** Codex has not inspected this Windows run's raw `.exec`, `.ec`, XML or class files. Its actual JVM/Android test counts were not supplied and are **not asserted** here. The prior cloud execution of 168 JVM tests at the remediation code is separate evidence, not this Windows run's count. Fresh bytecode/source mappings explain the changed denominator from the owner's earlier 31/36 factory and 47/52 package lines; percentages were not extrapolated from that baseline. `app/di` remains **INFORMATIONAL** in the runner; no permanent gate, threshold or exclusion changes. Coverage PASS is neither blanket production correctness nor formal Phase E, physical-device or backup/restore acceptance. See the [dated E addendum](integration/E_APP_COMPOSITION.md).

## Historical readiness review before the master foundation task

1. P01/P02 status reconciliation is delivered; PR #14 is already merged. P03 cleanup implementation and owner-reported numerical coverage evidence are recorded above. [P04 architectural planning](planning/FGH_INTERFACE_BOUNDARIES.md) is prepared for owner review; its recommendations and handoff decisions are not approved implementation contracts. [P05 deferred-acceptance register](planning/PRE_FGH_DEFERRED_ACCEPTANCE.md) is delivered for owner review, with P05-A–G, resumption/safety conditions and all eleven P04 decisions still pending approval. Any eventual readiness PR still requires current-head CI/current base, owner review and an explicit merge decision under [change control](CHANGE_CONTROL.md).
2. **Physical Android 8.1 (API27) tablet:** **DEFERRED / NOT RUN** until purchased. Owner's planned next step is a separately approved short-lived test branch from then-current `main`; no such branch is created by this documentation task. Physical-device evidence remains required by the [formal functional-phase acceptance rule](DEFINITION_OF_DONE.md), independently of hosted API27 PASS.
3. Real C06 backup/reinstall/restore and device-to-device transfer: **DEFERRED / NOT RUN**. Guarded acceptance on an expressly approved disposable environment must record exact restored preferences and excluded Room/private files; cloud transport results cannot prove D2D. See the [Phase C deferred register](PHASE_C_DEFERRED_ACCEPTANCE.md). No waiver or destructive execution is authorized here.
4. External SAF provider/grant/restart/revocation acceptance beyond the controlled local E2E flow, and real constrained-device resource/performance measurements, remain unverified as applicable. Owner-approved scenarios, provider/device identities, exact revision and retained results are needed to close these E acceptance limits; provisional runtime ceilings are not measured budgets.
5. F/G/H/I and library/reader/settings UI, editing, annotations and Drive remain future work requiring separately approved requirements and tests. Backend implementation and technical merges do not establish unconditional formal Phase E acceptance.

## Historical P05 delivery assessment (superseded decision status)

R0 and P01–P05 are technically delivered for owner review: status/evidence reconciliation, cleanup remediation and owner-reported native coverage, P04 architectural planning, and the [P05 consolidated register](planning/PRE_FGH_DEFERRED_ACCEPTANCE.md). P04 recommendations are not approved contracts. Missing root persistence, nested-folder handoffs, locale application and aggregate resource budgets are documented prerequisites, not feature authorization.

The cumulative readiness diff includes the three scoped P03 Kotlin/test files in addition to documentation; P05 itself is documentation-only. The branch can enter a formal PR review process after explicit owner authorization, but **is not merge-approved**. No PR is created here and no latest-readiness-head CI PASS is asserted. All seven mandatory checks at the eventual latest PR SHA/current main, applicable final-revision compatibility, review resolution and owner decision remain required. See the register's three separate questions: technical delivery, PR review/integration readiness and formal C/E acceptance.

Unconditional formal C/E acceptance remains unestablished: physical Android8.1, real C06 backup/restore and genuine D2D, plus applicable external-provider/manual/resource evidence, are still open. Conditional transfer modes need mechanism-specific assessment. Deferral is not a waiver and historical main CI/native coverage cannot close unrelated acceptance obligations. F/G/H/I remain unimplemented and require separate owner-approved tasks and prerequisite decisions.

## Documentation authority and historical records

Use this page and [ROADMAP.md](ROADMAP.md) for **current status**. Use [PROJECT_SPECIFICATION.md](PROJECT_SPECIFICATION.md), [AGENTS.md](../AGENTS.md), [TESTING_POLICY.md](TESTING_POLICY.md), [CHANGE_CONTROL.md](CHANGE_CONTROL.md) and [DEFINITION_OF_DONE.md](DEFINITION_OF_DONE.md) for normative requirements. Historical A0, C05/C06, E and L01–L03 reports intentionally preserve what was known **at the date and SHA of each delivery**. A historical `NOT RUN` cannot erase a later verified PASS, and a later PASS cannot retroactively certify the earlier run.

**No policy, Android product behavior, quality gate or feature implementation is changed by this status document.**
