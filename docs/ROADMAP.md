# Roadmap and approval boundaries

Current phase: **Phase 0 — Project Foundation**. This roadmap records approved
foundation scope and product direction from
[PROJECT_SPECIFICATION.md](PROJECT_SPECIFICATION.md); it does not authorize any
functional development phase.

| Work | State and boundary |
| --- | --- |
| Phase 0A — Policies and Quality Gates | Owner merged PR #1 into `main`; policies, validators, PR template and CI exist. Historical execution evidence is in PHASE_0A_REPORT.md. |
| Phase 0B — Project Foundation | Owner authorized this work after the merge. Brand light/dark themes, en/ca/es starter resources, preserved toolchain and technical documentation are submitted for review. Acceptance depends on current automated evidence, applicable manual review and explicit human approval. |

The app remains a starter greeting screen. Foundation code and a passing workflow
do not imply the product capabilities below are implemented. No Phase 1 is
defined or authorized here. See [TECH_STACK.md](TECH_STACK.md) for installed
technology versus candidates, and [DEFINITION_OF_DONE.md](DEFINITION_OF_DONE.md)
for acceptance conditions.

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
complete, regardless of severity label. Stop after Phase 0B delivery for human
review; nothing in this roadmap grants permission to proceed.
