# Development sequence and integration boundaries — A0

## Approval and current state

The owner has authorized **A0 architecture documentation only**. This PR creates
[ARCHITECTURE.md](ARCHITECTURE.md), [CONTRACTS.md](CONTRACTS.md) and this document.
It does not authorize A1, any parallel implementation, dependencies or functional
development. Phase 0 is completed/merged/accepted as recorded in
[ROADMAP.md](../ROADMAP.md). The audited base is
`ffdb35c9d9b0b4c55c94ce4783019e875c52278f`; code remains the starter Activity and
theme. A0 design approval is distinct from implementing and verifying the design.

Apply [AGENTS.md](../../AGENTS.md),
[PROJECT_SPECIFICATION.md](../PROJECT_SPECIFICATION.md),
[TESTING_POLICY.md](../TESTING_POLICY.md),
[CHANGE_CONTROL.md](../CHANGE_CONTROL.md) and
[DEFINITION_OF_DONE.md](../DEFINITION_OF_DONE.md) unchanged. No new approved
functional phase numbers, delivery dates or automatic start conditions are
created here. Each later task needs explicit owner-approved scope, exclusions,
acceptance criteria and its own branch/PR. Agents never merge.

## A0 — architecture documentation

Allowed changes are exactly the three files in `docs/architecture/`. No code,
packages, modules, tests, resources, Gradle files, dependency versions, fixtures,
CI workflows, existing policies, README, licensing or repository settings change.

Acceptance evidence for A0 comprises:

- Agreement between component responsibilities, allowed imports, diagrams and
  the single shared contract catalog; no circular dependencies or duplicated
  owners, and no material conflict with binding requirements.
- Clear distinction between approved direction, future implementation and
  unresolved provider/engine/security/performance validations.
- Resolving relative Markdown links, valid document structure, complete diff
  review and exactly the three authorized new documentation files.
- The unchanged seven mandatory PR checks on the latest revision/current base.
  Documentation-only work has no CI exemption. No new business logic or visual
  change means coverage/new feature tests/screenshots are not applicable; it
  does not make baseline checks optional.
- Human review and explicit integration approval; opening the PR is delivery
  for review, not permission to merge or begin A1.

## A1 — minimal shared contracts, separately approved

Only after owner approval and A0 integration, scope an A1 PR from current `main`.
Use [CONTRACTS.md](CONTRACTS.md) as the authoritative design catalog, selecting
the smallest consumed slice needed for independent components. Do not implement
the whole catalog in anticipation of eventual features.

Likely initial shared prerequisites are document identity/reference/revision,
typed errors/cancellation, page indices and basic geometry, read/snapshot
ownership, minimal renderer/session and metadata/preferences boundaries. The
exact subset, constructor validation, coroutine/dependency requirements,
serialization rules, public signatures and test cases need explicit A1 approval.
Annotation writing, composition and replacement contracts can wait until there
is an approved consumer and validated engine/provider behavior.

For the first parallel batch, A1 must establish enough public API for storage
read/snapshot access, renderer input/output lifetimes, registry/metadata and
typed preferences. Pure coordinate invariants are shared once, not reimplemented
by renderer and UI agents. Private drafts/recovery/publication interfaces are
included only if an approved adapter task actually needs them; otherwise no
stub implementations or speculative recovery infrastructure are permitted.

A1 defines and tests real invariants (invalid indices/geometry, exact revisions,
typed errors, cancellation propagation and lease ownership as applicable).
Publish an owner-reviewed contract/version baseline and test-double behavior
before the parallel branches begin. Select an AGP-compatible coverage approach
before new testable business logic; no A0 dependency decision installs a tool.

## Dependency-ordered sequence

| Work | Prerequisite / approval boundary | Integration evidence |
| --- | --- | --- |
| A0 documents | Current owner authorization | Documentation review and baseline CI; owner merges |
| A1 minimal contracts | Separate approval after A0 merge | Consumers/ownership agreed, relevant invariants tested; owner merges |
| First parallel adapter/UI batch | Separate task approvals after A1 merge | Isolated PRs using the same merged contracts and fakes |
| Adapter integration in `app` | Necessary adapter PRs accepted; separately approved integration scope | Real SAF-to-renderer and metadata/preference paths, resource lifecycle/failure evidence |
| Library and Reader planning/implementation | Required storage/rendering/persistence/UI integration validated; separate owner requirements | Offline flows, identity/access/reconciliation, tablet reading and measured cache budgets |
| Page editing | Snapshot/publication/recovery primitives and engine retention validated; separately approved | SaveAs first, provenance/structure/content checks and complete critical-scenario evidence |
| Annotations | Exact geometry/reading integration, writer fidelity and recoverable draft prerequisites validated; separately approved | PDF-space mapping, gesture ownership, undo/checkpoints, persisted interoperability and safe save |
| Settings enhancements and optional Drive | Relevant approved local capabilities stable; independent explicit scope/consent approval | API 26 locale behavior or secure minimal-scope import/export without mandatory network |

Preferences primitives may precede a settings UI. Annotation safety primitives
may be validated earlier than tools; no ordering requires building an unsafe
intermediate feature. ReplaceOriginal is a later capability contingent on
provider/recovery validation, not a required shortcut for the first editing PR.
Future functional scope stays governed by the existing specification/roadmap.

## First parallel batch after A1 is merged

This is a proposed decomposition for future approved Codex tasks, **not a request
to launch agents or create files during A0**. Each task uses current `main` with
the same accepted A1 contract baseline. Public contract files and composition
root files are owned by the contract/integration tasks, not any parallel branch.

| Task | Exclusive production file ownership | Interfaces/prerequisites | Test doubles | Acceptance evidence |
| --- | --- | --- | --- | --- |
| Codex A — Storage / SAF | `storage/saf/**`, approved `storage/transaction/**` subset; own storage tests | `DocumentCatalog`, `DocumentReadAccess`, `ReadHandle`, `WorkingSnapshot`; publication/recovery only if separately included | Deterministic provider/stream/fault doubles, temp directories; actual API 26 provider tests | Seekable/non-seekable reads, grants/revocation, identity observations, bounded copies, cancellation/disk-full/cleanup; originals unchanged |
| Codex B — Android PDF reading adapter | `pdf/android/**`; own renderer tests | `PdfReadEngine`, `PdfReadSession`, display/render models and agreed storage leases | Contract-conforming snapshot/lease source, deterministic requests and lifecycle probes | Actual API 26 rendering on test-only fixtures, page-count/error/resource closure, one-open-page concurrency, stale requests, pixel/memory bounds; no editing dependency |
| Codex C — Metadata / preferences | `data/metadata/**`, `data/preferences/**`; own persistence tests | `DocumentRegistry`, `DocumentMetadataRepository`, `PreferencesRepository`, typed domain values; private drafts only if approved | Registry/catalog observations and private-storage fault doubles if needed; real test databases/preferences stores | Persistence/restart/migrations, identity/reference rebinding, inaccessible versus deleted state, reading-position revision handling, no authoritative PDF or token storage |
| Codex D — Reusable UI | `ui/components/**`; `ui/theme/**` only with explicit task scope; own Compose tests | Pure approved presentation inputs, existing theme/resources | Sample immutable state/actions, no real storage/engine/feature ViewModel | Only components required by approved consumers; accessibility, contrast, touch targets, sizing, light/dark/portrait/landscape/en/ca/es; no feature navigation or fake functionality |

Each task's test ownership mirrors its production package under `src/test` or
`src/androidTest`. Shared fakes are assigned in the A1/integration task only if
multiple actual consumers require them; keep specific fakes with their tests.
Fakes reproduce documented failure, revision, cancellation and closure semantics,
not merely happy-path output. Real adapter tests are still needed: a fake provider
cannot prove SAF capability or real renderer behavior.

There are shared-file conflicts to avoid deliberately:

- `core/**`, `storage/api/**`, `pdf/api/**` and these design documents require a
  separate approved shared-contract change; no parallel agent silently edits them.
- `app/**` composition/navigation integration has a separate approved owner.
  Parallel adapters do not wire themselves into `MainActivity` or add menus.
- Gradle catalog/build files, manifest/resources, fixture staging, shared test
  configuration and CI are not jointly writable assumptions. Preapprove and
  integrate necessary dependency/test wiring in a small dedicated change before
  affected branches, or assign one explicit owning PR with coordinated rebases.
  Required en/ca/es strings for UI components need this same coordinated scope;
  they cannot be omitted to preserve disjoint package ownership.
- No branch can edit fixtures/hashes, policies or settings to accommodate its
  implementation. Generated `androidTest`-only fixture staging may be approved
  as test wiring; it is not a second source-controlled dataset or release asset.

The owner integrates one PR at a time. Remaining branches update against the
new `main`, rerun required checks and resolve conflicts without overwriting each
other's work. Integration verifies combined lifetimes, shared types and error
behavior; independently green adapters do not prove the end-to-end path.

## Shared contract change control

1. Identify the concrete consumer need, affected invariant/owner, compatibility
   impact, existing callers and tests. Propose the smallest contract change.
2. Obtain explicit owner approval for a separate change. Notify affected tasks
   of the proposed change and pause work that depends on unresolved signatures;
   continue unrelated work only within its approved scope.
3. Update the single authoritative definition and relevant design documentation
   together with meaningful tests in the approved contract PR. Specify migration,
   serialization and error/lifecycle changes; do not introduce feature-local
   copies as a workaround.
4. Validate against all affected consumers/test doubles and the mandatory CI.
   Resolve blocking review; only the owner merges.
5. Rebase/update affected branches onto the accepted contract revision and rerun
   affected checks plus required latest-revision gates before their reviews.

A contract mismatch is not permission to widen a feature PR. Breakage, ambiguous
ownership, unsupported PDF behavior or provider guarantees must be reported for
owner resolution rather than patched by changing policy or a fixture expectation.

## Technical validation checkpoints

These are **pending validations**, not implemented capabilities or approved
dependency additions. Record exact versions/APIs/providers/documents used and
retain non-sensitive reports. Stop an affected implementation if a required
guarantee cannot be established.

| Checkpoint | Required investigation before acceptance |
| --- | --- |
| Storage access and snapshots | Persisted grant flags, revocation/offline providers, non-seekable inputs, resource/disk limits, snapshot fingerprinting and external-writer limitations on API 26/27 |
| Renderer lifecycle and performance | API 26 descriptor ownership/one-page-at-a-time behavior, unsupported/encrypted/corrupt inputs, bounds/cancellation/rapid navigation; approve numerical first-page/page-turn/cache/peak-memory budgets on the physical Android 8.1 tablet |
| Exact geometry | MediaBox/CropBox origins, rotations/UserUnit, renderer agreement and round-trip tolerance; do not enable annotations from approximate display dimensions |
| Editing dependency | PDFBox-Android candidate maintenance/security/license/transitives/API 26 footprint; preservation of fonts, transparency, links/forms/annotations/protection; reject unsupported destructive cases |
| SaveAs and recovery | Exact validated-artifact handoff, readback checks, partial outputs, full disks, permissions/provider failure, cancellation/process death, idempotent reconciliation and journal durability |
| ReplaceOriginal | Explicit owner/user confirmation, recovery-copy integrity, provider-specific replace semantics and failure tests; if guarantees are insufficient, keep it unavailable and use SaveAs |
| Annotation fidelity | PDF standard objects/appearance streams, double-render prevention, revision-bound IDs/drafts, checkpoint window, command inverses and independent-reader display; no silent flattening |
| Persistence/security | Room migrations/reconciliation, DataStore typed defaults, private draft/journal format and retention, backup exclusions, no tokens or unnecessary personal data in logs |
| UI and locale integration | Real tablet gestures/input priority, large fonts/semantics, en/ca/es/light/dark and orientation; API 26/27 locale persistence/recreation with a validated mechanism |
| Optional Drive | Human-approved OAuth setup, current consent/scope/selection behavior, secure authorization, revoked/offline/cancelled transfers; no automatic synchronization |
| Distribution licensing | Root CC BY-NC-ND suitability and contribution/distribution rights, third-party/transitive notices, fixture CC0 and test-only tool licenses; no unilateral relicensing |

Where necessary, propose an explicitly scoped technical validation task before
feature implementation. A spike's experimental result does not authorize
production dependency adoption. Unresolved fidelity or provider risk must remain
visible in the affected PR and cannot become an unverified success statement.

## Testing, CI and human acceptance

Every approved PR, including A0, retains the seven required checks:
`build`, `lint`, `unit-tests`, `instrumented-api26`, `localization`,
`pdf-fixtures`, `quality-gate`. The aggregate gate must fail closed on missing,
failed, cancelled or unexplained skipped mandatory jobs. See [CI.md](../CI.md)
for actual commands, emulator architectures, triggers and report verification.
Checks must correspond to the latest PR revision and current base. Executing
test compilation is never instrumentation evidence; successful JUnit XML must
prove the required smoke test actually ran.

Future approved feature tests cover unit, integration, Compose UI, regression,
PDF structural/content integrity, relevant errors and security. New testable
business logic requires at least 90% line and 80% branch coverage with scoped
denominators/exclusions. All defined critical PDF modification/publication
scenarios need explicit tests, including original survival, disk-full/provider
failures, cancellation/interruption and recovery. Every bug fix adds a regression
test for the original failure. Do not inflate coverage with artificial tests.

Use the [immutable fixture collection](../../test-fixtures/README.md) and
[manifest](../../test-fixtures/manifest.json) as the single source. JVM tests may
consume its root directly; Android tests may receive generated test-only assets
from that root. Use temporary copies for edits, never regenerate fixtures in CI
or package them into production APKs. Preserve CC0 and original hashes. Required
cases beyond the 15 PDFs need approved additions/deterministic reproducers.

API 26 instrumentation is mandatory per PR; API 27 and recent API 36 remain
scheduled/requested checks and latest-revision phase-close evidence. Functional
phase acceptance also requires the physical Android 8.1 tablet and approved
performance/security/manual scenarios. UI work requires relevant portrait and
landscape, accessible light/dark behavior, English/Catalan/Spanish, font-scale and
TalkBack review. Tests on one API or a pure fake do not establish another API's
compatibility. Historical Phase 0 results do not substitute for new revision checks.

Each PR records exact commands, commit/base, local versus CI environment,
device/API, counts/reports and PASS/FAIL/NOT RUN/BLOCKED/NOT APPLICABLE results.
Unavailable emulators, SDK/network/auth or unresolved validations remain explicit
blockers, never fabricated passes. No private documents, secrets, APK releases
or sensitive logs are uploaded. Resolve P0/P1 and any unmet acceptance criterion
regardless of severity; owner triage is required for nonblocking improvements.

The owner reviews scope, contract compatibility, current test evidence, manual
acceptance where applicable and blocking conversations, then personally approves
and performs integration. Passing CI does not grant approval. **Stop after
delivering A0's PR; do not begin A1 or functional development without a new
explicit request.**
