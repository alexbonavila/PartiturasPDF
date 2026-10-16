# Roadmap and approval boundaries

**Status snapshot: 2026-10-17; verified `main` baseline `f94841f9f1eead508dd81b670d0943f3b3ae19bb`.** The Phase 0-only opening of this document is historical; the project progressed through architecture, backend adapters, persistence, integration and L01–L04 testing. For dated evidence and precise merge/acceptance boundaries see [PROJECT_STATUS.md](PROJECT_STATUS.md).

| Workstream | Implemented state and remaining boundary |
| --- | --- |
| Phase 0A/0B and API27 correction | Merged in [PR #1](https://github.com/alexbonavila/PartiturasPDF/pull/1), [PR #2](https://github.com/alexbonavila/PartiturasPDF/pull/2), [PR #3](https://github.com/alexbonavila/PartiturasPDF/pull/3); owner accepted foundation. |
| A0/A1 | Architecture catalog and Kotlin shared contracts are implemented/merged; see [architecture](architecture/ARCHITECTURE.md), [contracts](architecture/CONTRACTS.md). Historical A0 status wording refers to that earlier task. |
| B storage and PDF read | SAF access/provider adapters and native Android PDF reading/rendering backend are present; not a complete library or reader screen. |
| C persistence | Room/DataStore and Policy B configuration implemented; [real backup/restore/D2D and physical-tablet acceptance](PHASE_C_DEFERRED_ACCEPTANCE.md) are deferred, not passed. PR #10 is merged. |
| E application composition | AppGraph/factory and integration tests merged through [PR #12](https://github.com/alexbonavila/PartiturasPDF/pull/12). The [E audit](integration/E_APP_COMPOSITION.md) records historical acceptance boundaries and remaining manual steps; user-facing navigation is not implemented. |
| L01–L04 local testing | Merged through [PR #14](https://github.com/alexbonavila/PartiturasPDF/pull/14). SAF E2E, regression orchestration and scoped combined JaCoCo have revision-specific native evidence; post-merge hosted CI at `f94841f9` passed mandatory and requested API27/36 checks. Native runs remain attributed to their earlier SHAs. [L04D evidence](LOCAL_COMPATIBILITY_VALIDATION.md). |
| Pre-F/G/H readiness | P01/P02 delivered. P03 cleanup remediation on the unmerged readiness branch at `618c9aa` has owner-reported Windows/API26 combined PASS: factory 37/37 lines, 14/14 branches; app/di 53/53, 14/14. Counts/raw artifacts are not independently inspected; classification stays INFORMATIONAL. [P04 boundaries](planning/FGH_INTERFACE_BOUNDARIES.md) prepared for owner review; decisions are recommendations. P05 not started. No formal acceptance waiver or feature authorization. |
| C06 real backup/restore and D2D | **NOT RUN / DEFERRED**; XML-policy validation and automated persistence tests do not establish actual restore or D2D behavior. See the [deferred register](PHASE_C_DEFERRED_ACCEPTANCE.md). |
| Physical Android 8.1/API27 tablet | **NOT RUN / DEFERRED** until hardware purchase; later a new short-lived test-specific branch from current `main` will prepare and execute physical acceptance. |
| F/G/H/I and user-visible library/reader/settings/editor/annotation/Drive | Future implementation, not completed or authorized by this roadmap; each task requires separate owner approval. |

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

The future F/G/H/I references above do not approve a detailed phase scope,
completion dates or candidate dependencies. Pedal controls, two-page spread,
setlists, OCR, automatic cloud sync, collaboration and a backend remain uncommitted ideas requiring separate
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
