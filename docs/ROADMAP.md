# Roadmap and approval boundaries

**Status snapshot: 2026-10-16.** The Phase 0-only opening of this document is historical; the project progressed through architecture, backend adapters, persistence, integration and L01–L04 testing. For dated evidence and precise merge/acceptance boundaries see [PROJECT_STATUS.md](PROJECT_STATUS.md).

| Workstream | Implemented state and remaining boundary |
| --- | --- |
| Phase 0A/0B and API27 correction | Merged in [PR #1](https://github.com/alexbonavila/PartiturasPDF/pull/1), [PR #2](https://github.com/alexbonavila/PartiturasPDF/pull/2), [PR #3](https://github.com/alexbonavila/PartiturasPDF/pull/3); owner accepted foundation. |
| A0/A1 | Architecture catalog and Kotlin shared contracts are implemented/merged; see [architecture](architecture/ARCHITECTURE.md), [contracts](architecture/CONTRACTS.md). Historical A0 status wording refers to that earlier task. |
| B storage and PDF read | SAF access/provider adapters and native Android PDF reading/rendering backend are present; not a complete library or reader screen. |
| C persistence | Room/DataStore and Policy B configuration implemented; [real backup/restore/D2D and physical-tablet acceptance](PHASE_C_DEFERRED_ACCEPTANCE.md) are deferred, not passed. PR #10 is merged. |
| E application composition | AppGraph/factory and integration tests implemented. The [E audit](integration/E_APP_COMPOSITION.md) records historical acceptance boundaries and remaining manual steps; user-facing navigation is not implemented. |
| L01–L04 local testing | SAF E2E, full regression orchestration, combined JaCoCo and cross-host/API compatibility validated at recorded SHAs; awaiting PR/owner-controlled integration of the dedicated testing branch. [L04D evidence](LOCAL_COMPATIBILITY_VALIDATION.md). |
| Physical Android 8.1/API27 tablet | **NOT RUN / DEFERRED** until hardware purchase; later a new short-lived test-specific branch from current `main` will prepare and execute physical acceptance. |
| User-visible library/reader/editor/annotation/Drive | Still planned and subject to separate owner-approved feature tasks. |

**Do not equate implementation, automated evidence, merge and formal phase acceptance.** New commits require the seven checks on their latest PR SHA/current `main`, owner review and explicit merge authorization. Android 8.1 physical acceptance remains required for applicable functional phases under current policy.

## Future functional areas (not approved phases)

- Local documents and library: SAF folder/file access, import/export, organization,
  search and favorites; permissions/provider failures and metadata reconciliation.
- Reader: API 26 PDF rendering, fitting/zoom, edge page turns, reading position,
  portrait/landscape and tablet use; memory, fonts, rotation and annotation display.
- Page editing: rotate/reorder/delete, merge/extract; transactional save, recovery
  and structural/content regression tests preserving originals.
- Annotations: finger/stylus ink, highlights/underlines and text; PDF coordinates,
  undo/recovery, persistence and independent-viewer interoperability.
- Settings and language choice: backwards-compatible locale selection, appearance
  and reading preferences; API 26/27 behavior and restart/recreation tests.
- Optional Drive exchange: minimal-consent import/export, secure authorization,
  offline/cancellation/failure handling; no automatic two-way synchronization.
- Stabilization and release readiness: performance budgets, security/dependency
  and licensing review, API compatibility, accessibility and device acceptance.

These areas have no assigned phase numbers, dates or installed candidate
dependencies. Pedal controls, two-page spread, setlists, OCR, automatic cloud
sync, collaboration and a backend remain uncommitted ideas requiring separate
owner decisions.

Before implementing any future task, the owner must approve its requirements,
scope/exclusions, error cases, acceptance criteria, tests/fixtures, coverage,
localization and compatibility evidence. Start from updated `main` on a dedicated
branch and submit a PR; agents never merge. API 26 checks, API 27 phase-close
verification and applicable physical Android 8.1 tablet acceptance remain
mandatory. Resolve every unmet acceptance criterion before declaring the phase
complete, regardless of severity label. Phase 0 acceptance does not authorize
architectural design, shared contract definition or subsequent development;
nothing in this roadmap grants permission to proceed.
