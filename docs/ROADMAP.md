# Roadmap and approval boundaries

## Current milestone — merged functional MVP

PR [#24](https://github.com/alexbonavila/PartiturasPDF/pull/24) integrated Phase I and all D04 corrections into `main` at `f06197a75a2e420fb87153c0e64e3fe2a28841ea`. [CI #99](https://github.com/alexbonavila/PartiturasPDF/actions/runs/39424923611) passed all nine jobs with 407 ordinary Android tests on each API26/27/36; [CI #101](https://github.com/alexbonavila/PartiturasPDF/actions/runs/39456085172) passed the mandatory post-merge checks. Owner-reported tablet/emulator usability is positive; individual manual acceptance observations have not been supplied.

`docs/mvp-release-closeout` prepares the documentation-only milestone. After owner review and merge, a proposed annotated `v1.0.0-mvp` tag may be created on the resulting `main` commit. No tag exists yet. Future editing, annotation and Google Drive development requires separate authorization. See [MVP release record](releases/MVP_RELEASE.md).

**The following sections are archived snapshots from before PR #24; their 'not merged' and 'unimplemented I' statements are no longer current.**

## Historical phase I snapshot before merge (2026-10-20)

I01–I11 are implemented locally on `feat/app-mvp-integration-i`: retained application composition,
Library/Reader/Settings navigation, real picker wiring, registered/transient opening, lifecycle and
saved-state coordination, authoritative settings, automatic locale/window effects and recovery.
I12 completes the available source/artifact audit and documentation. This branch is **not merged**.

The tested source is `f51cc42df996390cf7e6aacf6cff85e004c9ca17`; I12 changes documentation only.
Latest fetched main and merge base are `cd86db5c4f70201cfbe6970de20cdb3308a970d0`, without drift.
Resolve the final audit commit with `git log -1 --format=%H -- docs/acceptance/I_APP_MVP_ACCEPTANCE.md`.
Fresh builds,620 JVM,289 Python, localization,15 fixtures and scoped JVM logic coverage pass.
See [current status](PROJECT_STATUS.md) and the [I-T01–I-T17 register](acceptance/I_APP_MVP_ACCEPTANCE.md)
for exact commands, file counters and the complete pending-acceptance inventory.

The next milestone is **owner review and missing acceptance**, not another development phase:
current-revision mandatory CI, actual API26/27/36, genuine production SAF/native/Surface/process-
death behavior, physical Android8.1 and manual/accessibility/linguistic validation remain open.
AAB language-split delivery requires a separately approved packaging decision and runtime evidence;
Lint remains0 errors/26 classified warnings. No Gradle/policy/worker change or waiver is made.

A [PR description](integration/I_APP_MVP_PR_DESCRIPTION.md) is prepared locally. Publication,
PR creation, synchronization if main later moves, merge and formal acceptance require their
respective owner decisions. No push/PR/merge or next phase occurred. Earlier snapshots below retain
their original date/revision; their former unimplemented-I statements are historical.

## Historical merged F/G/H snapshot — 2026-10-19

Verified Git baseline: `adcca2bd07693e0d985eee1c611bc931be1dfed1`. F merged in [PR #18](https://github.com/alexbonavila/PartiturasPDF/pull/18), G plus isolated native renderer in [PR #19](https://github.com/alexbonavila/PartiturasPDF/pull/19), H in [PR #20](https://github.com/alexbonavila/PartiturasPDF/pull/20). This section supersedes earlier *current* claims below that these components or their foundation/policy PRs remain unimplemented/unmerged. Dated historical results remain unchanged.

- **F:** folder browsing, bounded registration lookup, explicit registration, metadata/favorites and registered/transient open requests, with Compose presentation. No global search or file mutations.
- **G:** bounded one-page reading, page navigation, PAGE/WIDTH fitting, visual-only zoom, lifecycle/frame ownership and presented-page checkpoint scheduling. Production native work is isolated in a nonexported `:pdf` service. No editing, annotations or adjacent-page prefetch.
- **H:** theme SYSTEM/LIGHT/DARK and page-fit PAGE/WIDTH settings, typed commands, authoritative observation, explicit retries and uncertain-write reconciliation. No manual language selector.
- **I remains unimplemented:** starter `MainActivity` has no real F/G/H navigation, picker Activity Results, retained host lifecycle or global theme/fit/locale effect wiring. Implemented components do not prove a navigable app.

The owner-supplied handoff attributes [post-merge CI 39251879166](https://github.com/alexbonavila/PartiturasPDF/actions/runs/39251879166) to this exact baseline: nine jobs PASS (seven mandatory plus API27/36), 420 JVM and 357 ordinary Android tests on each API26/27/36, plus separately validated controlled PDFium diagnostics. This is attributed baseline evidence; raw hosted artifacts have not been independently downloaded in this closeout. Known raw diagnostic failures must remain visible under the existing classification policy. Neither historical CI nor a merge certifies the changed closeout revision.

Complete main es/ca/en translation bundles and Spanish default are delivered in the pre-I closeout (147 main IDs plus20 debug SAF IDs); runtime host locale application remains I.

See [FGH_PRE_I_CLOSEOUT](acceptance/FGH_PRE_I_CLOSEOUT.md) for current translation/coverage results, source attribution, Phase-I ownership and remaining acceptance. Physical Android8.1, backup/restore/D2D, external providers and manual accessibility remain open. Performance/memory measurement is deferred until after a functional Phase I; none is authorized here. The specification's 25% edge zones/center toggle versus G's 15%-minimum-48dp bands is a later owner product-fit decision, not changed here.

## Historical snapshots (retain their original revision and date)

**Status snapshot: 2026-10-17; verified `main` baseline `121090a56ff6d2fdb98655eaf7104ee67d79caa4`.** The Phase 0-only opening of this document is historical; the project progressed through architecture, backend adapters, persistence, integration and L01–L04 testing. For dated evidence and precise merge/acceptance boundaries see [PROJECT_STATUS.md](PROJECT_STATUS.md).

> **Current owner-approved foundation update (2026-10-17):** Readiness PR #15 is merged at `121090a56ff6d2fdb98655eaf7104ee67d79caa4` (seven mandatory jobs owner-reported PASS). Historical baseline/P04-pending wording below records the readiness snapshot. D01–D11 are now OWNER APPROVED IN PRINCIPLE and implemented centrally on `feat/fgh-shared-foundation`; separate `chore/spanish-first-localization-policy` changes governance/validation independently. See [actual frozen contracts](planning/FGH_FINAL_CONTRACT_FREEZE.md). Native validation, physical/manual acceptance and independent owner integration remain required; F/G/H/I screens/final host are not implemented by this task.

| Workstream | Implemented state and remaining boundary |
| --- | --- |
| Phase 0A/0B and API27 correction | Merged in [PR #1](https://github.com/alexbonavila/PartiturasPDF/pull/1), [PR #2](https://github.com/alexbonavila/PartiturasPDF/pull/2), [PR #3](https://github.com/alexbonavila/PartiturasPDF/pull/3); owner accepted foundation. |
| A0/A1 | Architecture catalog and Kotlin shared contracts are implemented/merged; see [architecture](architecture/ARCHITECTURE.md), [contracts](architecture/CONTRACTS.md). Historical A0 status wording refers to that earlier task. |
| B storage and PDF read | SAF access/provider adapters and native Android PDF reading/rendering backend are present; not a complete library or reader screen. |
| C persistence | Room/DataStore and Policy B configuration implemented; [real backup/restore/D2D and physical-tablet acceptance](PHASE_C_DEFERRED_ACCEPTANCE.md) are deferred, not passed. PR #10 is merged. |
| E application composition | AppGraph/factory and integration tests merged through [PR #12](https://github.com/alexbonavila/PartiturasPDF/pull/12). The [E audit](integration/E_APP_COMPOSITION.md) records historical acceptance boundaries and remaining manual steps; user-facing navigation is not implemented. |
| L01–L04 local testing | Merged through [PR #14](https://github.com/alexbonavila/PartiturasPDF/pull/14). SAF E2E, regression orchestration and scoped combined JaCoCo have revision-specific native evidence; post-merge hosted CI at `f94841f9` passed mandatory and requested API27/36 checks. Native runs remain attributed to their earlier SHAs. [L04D evidence](LOCAL_COMPATIBILITY_VALIDATION.md). |
| Pre-F/G/H readiness and shared foundation | R0/P01–P05 merged via PR #15. D01–D11 OWNER APPROVED IN PRINCIPLE; actual central contracts/adapters on independent `feat/fgh-shared-foundation`, documented in [contract freeze](planning/FGH_FINAL_CONTRACT_FREEZE.md). Separate localization-policy branch awaits its own owner review. Native verification and owner integration remain required; no new phase acceptance or F/G/H/I screen authorization. |
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
