# M-M1 contract and approval matrix — 2026-11-02

**PROPOSED — OWNER APPROVAL REQUIRED** for every new contract/file/sequence below.
M-D01–M-D14 are approved design, not approved Kotlin signatures or implementation.
Source: `7f00a793a06e988859dbefc3e21814c21ec727dd`, feat/pdf-safe-write-m.
TEST IMPLEMENTATION: NOT STARTED. TEST EXECUTION: NOT RUN (documentation-only).

## Ground truth and dependency boundary

See [capability/threat audit](M_M1_CAPABILITY_THREAT_AUDIT.md) for numbered source
references and fetch/branch provenance. A0's contract map is
`docs/architecture/CONTRACTS.md:37–40,119–194,449–528`. Installed declarations are
DocumentRef/StorageDocumentRef/SnapshotEvidence/DocumentRevision, DocumentRegistry,
ReadHandle/ReadProvenance, DocumentReadAccess, PdfReadSource, PdfReadEngine/session
and ReaderRoute. Planned WorkingSnapshot, SnapshotId, PdfSourceSnapshot,
DocumentPublisher, PublicationMode/Receipt/Result, OperationId, RecoveryArtifactStore/
RecoveryRecord, PdfPageComposer, PdfAnnotationWriter, ValidationExpectation,
PdfOutputValidator, ValidationReport, ValidatedPdfArtifact, PdfModificationService/
Request/Receipt have **no installed production Kotlin declarations/implementations**
in this source inventory. A0 prose is not an implemented API.

Future direction: UI/host -> pdf.workflow orchestration -> pdf.api typed engine/
validation and storage.api capabilities; implementations in pdf.editing and
storage.transaction. storage.transaction must not import pdf.api, UI or Room;
pdf.editing must not acquire grants, publish or update registry. Workflow combines
capabilities, not mutable paths. Existing storage.saf and isolated reader remain
protected. No reverse dependency from storage to PDF parser is proposed.

## Approved decision traceability

| Decision | Actual contract/source relation | Required slice, owner and approval boundary |
|---|---|---|
| M-D01 registered/transient | DocumentModels.kt:10–18,62–82; CONTRACTS:119–169 | Operation-local source/byte identity under storage.api; H-01 must resolve registered-only snapshot and PdfReadSource/ReadProvenance bridge |
| M-D02 temporary snapshots/durable uncertainty | SafReadCopyRecovery.kt:7–35 is only cache cleanup | storage.transaction owns operation artifact lifetime; workflow recovery never resumes editing automatically |
| M-D03 budgets | AppRuntimeLimits.kt:17–20 is read/render only | M-local injected limits/admission accounting; preserve 64MiB input,128MiB output,256MiB aggregate,64MiB margin,64KiB buffer; H-07 |
| M-D04 one owner/read leases | ReadHandle.kt:15–63 transfers a single descriptor, not multiple leases | New sealed owner/lease contract in storage.api, implementation storage.transaction; no mutation after seal, close joining/cleanup pending |
| M-D05 durable journal/reconciliation | A0 RecoveryArtifactStore at CONTRACTS:512–528 only | storage.api narrow durable store, storage.transaction mechanics, workflow state/reconciliation; schema/durability H-04, startup H-08 |
| M-D06 explicit Save As | SafTreeGrantAccess.kt:27–49 only READ tree; A0 publication broader | Narrow destination capability; owner-controlled host bridge later; H-03 pre-picker durable ordering, no root WRITE or replacement |
| M-D07 strict modular validation | PdfReadEngine.kt:7–35 is read/render, not output validator | pdf.api expectation/report/validated capability; pdf.editing checks; workflow chooses operation checks; no caller-forged validation authorization |
| M-D08 replaceable engine | libs.versions.toml contains no PDFBox dependency | H-06 engine/security/license/API26/fidelity evaluation before build changes; no library chosen here |
| M-D09 typed specialized engines | A0 composer/writer plans, no Kotlin implementation | New approved pdf.api files only when consumed; pdf.editing adapters; defer annotation/N/O/P-only placeholders |
| M-D10 exclusive reservation/BUSY | Outcome.kt:10–20 lacks BUSY; existing Reader locks unrelated | Workflow single process coordinator, immediate typed rejection, no queue/long I/O locks; H-02 failure contract |
| M-D11 private/no-backup/retention | backup_rules.xml:2–6 and extraction:2–11 exact settings allowlist | storage.transaction transaction-owned noBackupFilesDir subtree; journal-aware cleanup/quota; backup verifier reused by owner, no policy edits |
| M-D12 independent Save As identity | Registry/rebind exist; A0 ModificationReceipt:505–509 expects logical output ref | Receipt must permit verified unregistered storage result, no auto register/rebind/favorite/position; H-01/H-02 extension decision |
| M-D13 layered tests | Existing test ContentProviders are not DocumentsProviders | New instrumentation-only DocumentsProvider/control plane proposed, manifest approval required; API26 actual evidence, all20 cases,90%/80% |
| M-D14 ownership | A0 storage.api/pdf.api ownership map vs protected files | New files need exact owner/signature approval; existing core/API/SAF/worker/host/build/status/policies untouched |

## Smallest candidate contract slice (NOT final names/signatures)

Each candidate shared name is a responsibility label, not authorization to create
that filename. Exact filenames/signatures and consumed vertical slice await M-M2.
No Kotlin pseudocode is necessary to freeze premature interfaces.

| Candidate responsibility | Declaration / implementation | Input/output shape; ownership and lifetime | Failures/test seam and prerequisites |
|---|---|---|---|
| Source/operation identity | storage.api new files / transaction acquisition, workflow coordination | Registered logical association optional, opaque provider input, random operation-local capture identity; no fabricated DocumentRef | Stale association/denied input; registered and transient doubles; H-01 |
| SnapshotSource / trusted factory | storage.api / storage.transaction | Authorized read acquisition + limits -> completely copied, closed, hashed, sealed owner with captured length/digest | Unknown length, partial input, cancellation, ENOSPC; byte stream/file seam; H-01/07 |
| Sealed snapshot owner/read lease | storage.api / storage.transaction | Immutable evidence + independent read-only acquisitions; close rejects new leases and joins existing lease closure before deletion | Closing/cleanup pending; deterministic race seam; no shared descriptor; H-02 |
| Staging writer/artifact manager | Prefer M-private storage.transaction, expose only needed new storage.api boundary | Transaction-confined writable .part -> closed immutable artifact, never authority to publish | Write/close/cancel failure and quota charge; file-operation seam; H-02/07 |
| ValidationExpectation/Report | pdf.api / operation validation in pdf.editing | Typed operation constraints and source provenance -> byte/structure/content results with explicit unsupported checks | Invalid/unsupported/protected/incomplete report; approved real engine required; H-06 |
| ValidatedPdfArtifact | pdf.api / validator only creates trusted capability | Passing operation-specific report + sealed output evidence/pinned read lease, no path reopening or mutable alias | Caller cannot self-certify; transformation double is not independent validator; H-02/06 |
| Narrow destination capability | storage.api / later authorized host/storage adapter | Result-grant evidence, operation binding, allowed write/readback/close operations for newly chosen destination | Denied/expired/readback unavailable/independence unknown; fake capability now, genuine picker later; H-03/05 |
| Publisher/result/receipt | storage.api / storage.transaction | Pinned validated bytes supplied in storage-only form -> storage result, length/digest/readback/durability evidence and uncertainty | Typed partial/uncertain/unverified, never success on close alone; fault provider; H-02/03 |
| TransactionId/state/journal | Minimal storage.api durable record / storage.transaction filesystem; workflow owns PDF payload | Versioned operation intent, unknown-or-known destination, expected output hash/length, acknowledged durable transitions | Torn/stale/unknown schema, journal flush failure; filesystem checkpoints and restart seam; H-04 |
| Exclusive reservation/BUSY | Prefer pdf.workflow private owner unless consumed publicly | Acquire -> one closeable reservation or immediate typed BUSY; not held synchronous lock across I/O/picker | Concurrent admission and cancellation; deterministic scheduler seam; H-02 |
| Read-only reconciliation | Workflow state machine / storage.transaction supplies reads/journal | Durable record + valid existing read authority -> observed match/mismatch/unavailable/unknown; no new publication | Corrupt record or lost authority remains unresolved; repeat idempotently; H-04/08 |
| PdfModificationService | pdf.api / pdf.workflow | Typed source(s), plan, expectations and injected destination -> independent verified storage receipt or explicit unresolved result | Source/journal/validation/publication failures remain distinct; no metadata side effects; H-01/02 |
| PdfPageComposer | pdf.api only if first approved operation consumes it / pdf.editing | Immutable input(s) + typed page plan + owned staging output -> unvalidated complete output | Fidelity/structure failure, cancellation; engine gate; H-06/07 |
| PdfAnnotationWriter | Future pdf.api/pdf.editing, DEFER | No class until an approved annotation operation actually consumes it | Later annotation phase, not a placeholder in M-M2 |

AppError is protected. Do not map BUSY to PROVIDER_UNAVAILABLE, SESSION_CLOSED or
another misleading existing enum. H-02 must choose an approved new result/error
boundary (possibly a specialized M result) or separately authorize a core change.
Specialized result design must still preserve cancellation and programming errors,
not conceal failures behind a generic map or success with flags.

## Owner hold points before M-M2

**H-01 — identity, revisions and receipts (blocking contract freeze).**
A0 WorkingSnapshot requires registered DocumentRef/exact registered revision;
M-D01 forbids manufacturing those for transient input. ReadProvenance.SnapshotLease
and PdfReadSource currently require registered revision matching. M-D12 also
conflicts with A0's output-identity reservation/ModificationReceipt logical ref.
Technically viable alternatives, all **PROPOSED — OWNER APPROVAL REQUIRED**:

- New technical captured-byte identity/evidence and source-association union, with
  a narrow registered projection into the existing revision model only when
  legitimate. Requires approved new storage.api/pdf.api bridge and explicit
  decision about how a transient immutable read lease reaches existing renderer.
- A new M-local transient artifact owner separate from registered WorkingSnapshot,
  sharing a byte-evidence/lease abstraction only via approved new contracts.
  Avoid two independent snapshot implementations; specify one technical owner.
- Separately amend the historical shared snapshot/provenance model to distinguish
  operation-local from registered revision evidence. Existing core/API edits would
  require explicit additional authorization and downstream compatibility tests.

No alternative is selected. Treating transient input as unknown registered revision,
a fake DocumentId, URI-derived identity or auto registration is not an option.
New Save As receipts must not imply a DocumentRef before explicit registration.

**H-02 — exact public surface and typed errors.** Approve exact filenames, owners,
input/output shapes, lease close/cleanup semantics, result distinctions, BUSY,
validation capability construction and journal versions. No final signatures here.

**H-03 — picker creation precedes URI observability (blocking publication).**
ACTION_CREATE_DOCUMENT can create a public provider object before the result is
returned; cancellation/result loss cannot prove no document exists. M alone does
not own picker launch and cannot journal a not-yet-known URI. Proposals:

- Durable operation intent with explicitly unknown destination must be acknowledged
  before the later host launches the picker; after result, durably associate URI/
  returned permissions before writing. Lost callback remains unresolved even with
  zero application bytes written. Exact host handshake/schema needs approval.
- Retain the strict requirement but defer real picker publication; test M using
  injected capabilities plus a host-boundary simulator until the handshake is
  approved and separately integrated. This is not real Save As acceptance.

Accepting an already-created injected destination as if M had journaled before
its creation does not meet the current requirement. Any narrower definition of
transaction boundary would need an explicit owner decision, not a silent waiver.
No universal orphan enumeration, provider rollback, name uniqueness or deletion
right can be inferred from a URI. If destination independence from the source
cannot be established, reject writing rather than risk the original.

**H-04 — durability and corrupted state.** Approve API26 checkpoint strategy,
file/data/directory sync and atomic-replacement assumptions, acknowledgment
boundary, torn/unknown-schema behavior, and crash versus power-loss evidence.
A successfully invoked fsync/rename is not a proved universal durability guarantee.

**H-05 — host and cross-phase ownership.** Approve later host picker handshake,
result/grant transfer, source/capability cancellation, test-only provider/manifest
and integration order. No K/L/host/SAF/isolated-worker edits in independent M.
No new root WRITE grant, production provider or implicit grant persistence.

**H-06 — engine/validation.** No editing dependency is approved. Request candidate
license/security/maintenance/transitive/API26/27/fidelity/resource evaluation and
an independent structural/content validation strategy. Render success or identical
copy bytes is insufficient. Engine-backed tests stay blocked until approval.

**H-07 — budget interpretation.** Clarify whether 64MiB is per source or total
source set for multi-input operations; both interpretations must still fit the
256MiB aggregate including retained uncertainty, journal and staging. Define
reservation accounting and free-space checks without raising ceilings. Margin
checks race with other disk users; ENOSPC must remain safe at every stage.

**H-08 — reconciliation without UI.** Approve who discovers pending records,
when read-only reconciliation is invoked, what existing read grants suffice and
how unresolved status reaches later host UI. Startup wiring/editor resume/new
grant acquisition are not implicitly authorized.

## Proposed sequence and approvals (M-M2 → M-M8)

This ordering is a proposal because the original handoff's full milestone schedule
was not attached. Do not mistake it for a new approved phase plan.

1. **M-M2:** resolve H-01–H-08 as applicable; approve a minimal exact contract slice,
   owners and consuming tests. No public definitions before approval.
2. **M-M3:** operation-local snapshot/lease/quota primitives and mirrored tests;
   no provider write. Owner executes JVM/API26 evidence.
3. **M-M4:** private staging, exclusive reservation, durable pre-publication journal
   and selective retention/recovery primitives; no public mutation until proven.
4. **M-M5:** separately approved engine evaluation and strict operation-specific
   validation. Without H-06, engine work remains BLOCKED, not replaced by fakes.
5. **M-M6:** capability-injected Save As transfer, close/readback and read-only
   reconciliation, dependent on H-03/04 and earlier durable journal/validator.
6. **M-M7:** owner-run full fault-window/test-provider campaign, genuine API26,
   crash/restart and privacy/packaging evidence. No personal-document writes.
7. **M-M8:** owner acceptance and separately authorized host/K/L integration review;
   actual picker integration is not installed by independent M. Compatibility,
   physical-device and publication limits must be explicit before formal acceptance.

The next narrowly scoped request is owner resolution of H-01/H-03 plus approval
of the first exact M-M2 contract slice. This audit does not authorize M-M2.
