# Current project status — PartiturasPDF

**Snapshot date:** 2026-10-16. **Scope:** repository state and evidence review; this page is an index, not a new development authorization or an acceptance waiver.

- Repository: [alexbonavila/PartiturasPDF](https://github.com/alexbonavila/PartiturasPDF).
- At audit, `main` was `c5db5b4cce4c08bdf3ead92b21d3972e96843392`. The opt-in L01–L04 test infrastructure is on `test/local-e2e-automation` awaiting a PR and owner-controlled integration.
- Last application/test revision with both native macOS and hosted API 26/27/36 evidence: `87b864de296563649a4f80e15b55e30f75e26836`; later documentation-only commits must still pass the required latest-PR-revision CI. See [L04D](LOCAL_COMPATIBILITY_VALIDATION.md).

## Implemented capabilities versus user-facing features

| Area | Code currently present | Limit / acceptance status |
| --- | --- | --- |
| Phase 0 | Material 3 theme, three-language resources, CI policies, immutable PDF fixtures | Merged; historical phase acceptance recorded |
| A0 / A1 | Architectural specifications, core document/revision/geometry/result/preferences contracts and associated tests | Code exists, not merely a planned future phase; A0 catalogs also describe unimplemented designs |
| SAF (B storage) | SAF catalog, URI codecs, grants, seekable/non-seekable bounded read access, provider/recovery handling | Integration tests exist; synthetic L01 local SAF E2E passed; separate genuine manual external-grant acceptance remains |
| PDF (B5) | Android PdfRenderer-backed read/render engine and API contracts | Native rendering tests passed; this is **not** a user-facing reading screen or a PDF editor |
| C persistence | Room schema v1, registry/metadata repositories, DataStore preferences, Policy B backup XML and tests | Backend implemented and integrated into composition; real C06 backup/restore and D2D acceptance **NOT RUN / DEFERRED** |
| E integration | AppGraph/AppGraphFactory and SAF-to-PDF/persistence Android integration tests | Backend composition exists; E's historical acceptance audit documents remaining manual/evidence limits; `MainActivity` is still a starter greeting |
| L01–L04 | Opt-in dedicated API26 AVD SAF E2E, host/android/full orchestration, real JaCoCo combined coverage, multi-host testing | Implemented on the unmerged `test/local-e2e-automation` branch; see evidence below |
| User-facing score library, reader UI, editing, annotations and Google Drive | Planned product scope, **not shipped** | Require separately approved feature tasks and relevant tests |

Do not mistake repositories, adapters or test harnesses for a finished library/reader/editor UX. This page does not claim that every production class has >=90% coverage or that all deferred external/physical scenarios passed.

## Confirmed test evidence

- **Native macOS Apple Silicon / API26 ARM64, SHA `87b864de`:** `full` PASS/0: 246 Python infrastructure tests + 165 JVM + 228 Android + 4 synthetic SAF E2E stages = **643 heterogeneous checks/stages**; cleanup and diagnostics PASS. `coverage` PASS/0: 165 JVM, 228 Android, genuine JaCoCo JVM+Android.
- **Earlier native Windows / API26 x86_64:** L02 `full` PASS at `27cef9f`; L03 `coverage` PASS at `9d10739d`. These are earlier revisions, not a claim of final-SHA native Windows execution.
- **GitHub Actions [CI #38857215346](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38857215346), SHA `87b864de`:** all seven required jobs and API27/API36 compatibility jobs PASS; 165 JVM and 228 Android tests on **each** API 26/27/36 (zero failures/skips), 246 Python infrastructure tests, all 15 immutable PDF fixtures.
- **Scoped combined JaCoCo line/branch coverage:** A1 99.47%/99.43%; B5 PDF 97.71%/87.50%; C05 Room 98.57%/94.35%; DataStore 100%/100%. Approved gate is >=90% lines and >=80% branches **for each applicable scoped area**, not an unqualified project-wide metric. See [L04D](LOCAL_COMPATIBILITY_VALIDATION.md), [L03](LOCAL_CODE_COVERAGE.md).
- These are evidence claims for their **identified SHAs**; after further commits a new PR must run current-head CI against current `main`.

## Outstanding acceptance and next integration step

1. Open the L01–L04 PR from `test/local-e2e-automation` to `main`; review diff and security/dependency scope; obtain all seven required checks on the **latest PR head** and current base; resolve conversations. Only the human owner decides and performs any squash merge. See [change control](CHANGE_CONTROL.md).
2. **Physical Android 8.1 (API27) tablet:** **DEFERRED / NOT RUN** until purchased. Owner's planned next step is a new, short-lived task-specific branch from the then-current `main` (suggested `test/physical-android81-acceptance`) for safe physical-device tests. Do not open that branch now or waive the [formal functional-phase acceptance rule](DEFINITION_OF_DONE.md).
3. Real C06 backup/reinstall/restore and Android device-to-device transfer: **DEFERRED / NOT RUN**; separate guarded, potentially destructive acceptance on an expressly approved disposable test environment; do not run on an owner's normal device. See [Phase C deferred register](PHASE_C_DEFERRED_ACCEPTANCE.md).
4. Manual external SAF permission/restart/revocation acceptance and genuine resource/performance measurements remain distinct from synthetic CI E2E, as applicable. See [E integration audit](integration/E_APP_COMPOSITION.md).
5. Library/reader UI, editing, annotation and Drive remain separate future work requiring owner-approved requirements and tests.

## Documentation authority and historical records

Use this page and [ROADMAP.md](ROADMAP.md) for **current status**. Use [PROJECT_SPECIFICATION.md](PROJECT_SPECIFICATION.md), [AGENTS.md](../AGENTS.md), [TESTING_POLICY.md](TESTING_POLICY.md), [CHANGE_CONTROL.md](CHANGE_CONTROL.md) and [DEFINITION_OF_DONE.md](DEFINITION_OF_DONE.md) for normative requirements. Historical A0, C05/C06, E and L01–L03 reports intentionally preserve what was known **at the date and SHA of each delivery**. A historical `NOT RUN` cannot erase a later verified PASS, and a later PASS cannot retroactively certify the earlier run.

**No policy, Android product behavior, quality gate or feature implementation is changed by this status document.**
