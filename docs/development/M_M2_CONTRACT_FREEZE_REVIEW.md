# M-M2A — Contract-freeze review

Date: 2026-11-02. Status: **REVIEW COMPLETE; EXACT SIGNATURES NOT APPROVED**.
Branch: `feat/pdf-safe-write-m`. Reviewed HEAD:
`cf3a3364bec0f6950d62b5cad1bcf8f189b58bfb`.
Production source baseline: `7f00a793a06e988859dbefc3e21814c21ec727dd`;
the intervening two commits change only M-M1 documentation. Initial index and
working tree were clean. This document adds no implementation or test evidence.

## Authority and provenance

The supplied `PHASE_M_M2_CODEX_REVIEW_PROMPT.md` authorizes this review and its
publication only. `PHASE_M_H01_H08_APPROVED_DECISIONS.md` supplies binding H policy;
`PHASE_M_M2_CONTRACT_FREEZE_PROPOSAL.md` supplies candidate declarations, not
approved APIs. All three were read. The original
`PHASE_M_SAFE_PDF_WRITING_CODEX_HANDOFF(1).md` is available and was reviewed,
including its M01–M12, M-T01–M-T20 and milestone sequence. Its initial picker
cancellation simplification is qualified by newer H-03, not silently retained.
M-D01–M-D14 remain binding, as recorded in the accepted M-M1 documents.

Internal references: [capability/threat audit](M_M1_CAPABILITY_THREAT_AUDIT.md),
[contract/approval matrix](M_M1_CONTRACT_AND_APPROVAL_MATRIX.md),
[critical test plan](M_M1_CRITICAL_TEST_PLAN.md),
[A0 contracts](../architecture/CONTRACTS.md),
[architecture](../architecture/ARCHITECTURE.md),
[specification](../PROJECT_SPECIFICATION.md),
[testing policy](../TESTING_POLICY.md), [change control](../CHANGE_CONTROL.md).
Their historical status paragraphs are not current implementation evidence.
The task's explicit execution prohibition applies here; eventual acceptance
checks remain required and have not been waived.

## 1. Source evidence inventory

Paths below are relative to the repository. `src/` abbreviates
`app/src/main/java/com/abdev/partituraspdf/`, never a proposed new source root.

| Source location / symbol | Observed fact and contract consequence |
| --- | --- |
| `src/core/document/DocumentModels.kt:5–19,62–83` | DocumentRef is registered identity; StorageDocumentRef is opaque location. SnapshotEvidence checks hash/length representation, not authenticity. VerifiedSnapshot requires registered identity. |
| `src/core/document/DocumentRepositories.kt`, DocumentRegistry | register/rebind are metadata coordination, not creation/move/publication. No implicit registration of output is allowed. |
| `src/core/result/Outcome.kt:3–20` | Cancellation/programming defects propagate. Existing conflicts do not include BUSY. Preserve these declarations. |
| `src/storage/api/DocumentReadAccess.kt:6–27` | openRead/openSeekable only. Seekable fallback is not trusted immutable capture. |
| `src/storage/api/ReadHandle.kt:8–23,26–63` | SnapshotLease requires VerifiedSnapshot; handle transfers exclusive ownership. Native closure must be tolerated, backing-owner cleanup must still happen once. |
| `src/pdf/api/PdfReadSource.kt:9–28` | seekable/OWNED preconditions; snapshot revision must match document; takeHandle transfers once. No truthful registered provenance for a transient M snapshot. |
| `src/pdf/api/PdfReadEngine.kt`, open/session contracts | Engine owns source on entry even on failure/cancellation. Session and native descriptor lifetimes are not M parser ownership. |
| `src/storage/saf/SafDocumentReadAccess.kt`, openSeekable/copy path | Bounded cache copy exists, but lacks M seal/evidence/journal semantics. Its 8192-byte buffer is not H-07's future 64 KiB M buffer. Do not modify it. |
| `src/storage/saf/SafReadCopyRecovery.kt`, active copy recovery | Process-local cache cleanup, not durable transaction recovery or authority to delete uncertain outputs. |
| `src/storage/saf/SafTreeGrantAccess.kt`, treePickerIntent/persist | Existing library tree READ authorization, not a Save As WRITE bridge. |
| `src/MainActivity.kt`, onCreate/onSaveInstanceState; `src/app/host/RootPickerBinding.kt`, bindRootPicker | Existing root-picker lifecycle cannot be repurposed silently. Host integration is later and protected. |
| `app/src/androidTest/AndroidManifest.xml:3–13`; SafFixtureContentProvider / CanonicalPdfContentProvider | Existing test providers are ContentProviders, not a fault-controllable DocumentsProvider. H-05 exception needs an explicit future scope. |
| `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Exact settings allowlist; no M artifacts should be included. No policy edits proposed. |
| `app/build.gradle.kts`, defaultConfig; `gradle/libs.versions.toml` | minSdk 26, AGP 9.5.0; no installed production PDFBox editing engine. No build changes authorized. |
| `docs/architecture/CONTRACTS.md:119–169,183–194,449–528` | Planned snapshot, publishing and recovery prose, not installed writing APIs. Conflicts are reconciled below. |
| `test-fixtures/manifest.json` | Fifteen immutable fixtures; identity is manifest hash/length, not filename alone. No fixture validation was executed here. |

No proposed writing contract is installed by this review. Existing source owners
and Reader/SAF/registry/backup behavior remain unchanged.

## 2. Declaration ownership and first-use review

All package names have prefix `com.abdev.partituraspdf`. Filename abbreviations:
S = new `storage/api/SnapshotContracts.kt`; R = new
`storage/api/RecoveryContracts.kt`; P = new `storage/api/PublicationContracts.kt`;
V = new `pdf/api/PdfValidationContracts.kt`; W = new
`pdf/api/PdfModificationContracts.kt`. These are candidate groupings only.

**IMPLEMENT NOW** means recommended for a separately authorized M-M2B declaration
slice, not permission to implement during M-M2A. No actual M consumer exists yet;
first consumers below are planned, not a claim of current use.

| Public declaration(s), candidate file/package | Dependencies; first consumer / target | Lifetime, cancellation and test seam | Risk; recommendation |
| --- | --- | --- | --- |
| SnapshotId — S/storage.api | String; M-M3 capture owner | Immutable technical identity; generator collision/uniqueness seam | Public constructor is not authority. OWNER DECISION S-01; then IMPLEMENT NOW if consumed. |
| SnapshotSource — S/storage.api | StorageDocumentRef, optional genuine DocumentRef; M-M3 capturer | Request value, not read permission; registered/transient doubles | Caller association is not registry confirmation. IMPLEMENT NOW subject to exact approval. |
| SnapshotOrigin and Source/Produced — S/storage.api | SnapshotSource / TransactionId; M-M3 source/staging | Immutable origin assertion bound by trusted owner; lineage seam | Produced pulls in R before staging consumes it. OWNER DECISION S-02. |
| SnapshotCaptureLimits — S/storage.api | Long; M-M3 capture | Per-operation positive ceiling; boundary/overflow seam | Does not enforce H-07 aggregate/free space alone. IMPLEMENT NOW. |
| SnapshotFailure and Existing/CaptureIncomplete/SealFailed/Closing/CleanupFailed — S/storage.api | AppError; M-M3 capture/lease/cleanup | Expected failures only; injected I/O, seal and closure faults | Too broad Existing; missing closed distinction and secondary detail. OWNER DECISION S-03. |
| SnapshotCaptureResult and Captured/Rejected — S/storage.api | WorkingSnapshot / SnapshotFailure; M-M3 caller | Successful ownership handoff; cancellation at return seam | Public Captured plus forgeable owner does not attest bytes. OWNER DECISION S-01/S-03. |
| SnapshotLeaseResult and Acquired/Rejected — S/storage.api | SealedSnapshotLease / SnapshotFailure; M-M3 reader/validator | Acquired lease owns independent read-only access; acquire/close race seam | Cancellation during handoff must release unreturned lease. OWNER DECISION S-01/S-04. |
| SnapshotReleaseResult and Released/CleanupPending — S/storage.api | SnapshotFailure; M-M3 operation cleanup | Joined release versus tracked pending cleanup; deletion fault seam | No claim of release while live descriptors remain. OWNER DECISION S-03. |
| SealedSnapshotLease — S/storage.api | ParcelFileDescriptor, Closeable; M-M3 hashing / M-M4 parser | One independent descriptor per lease; idempotent terminal close; parser-close seam | Public implementability/raw FD aliases; OWNER DECISION S-01/S-04. |
| WorkingSnapshot — S/storage.api | Identity/origin/evidence/results; M-M3 capture/staging consumers | requestClose rejects acquisitions atomically; awaitReleased joins closure; race seam | Public implementation can fabricate seal/evidence; OWNER DECISION S-01/S-03. |
| SnapshotCapturer — S/storage.api | Source, limits, result; M-M3 capture consumer | Background cancellable capture, close/seal before return; stream/file fault seam | Interface implementation is a trusted injected dependency, not arbitrary caller authority. IMPLEMENT NOW only with approved result/owner slice. |
| TransactionId — R/storage.api | String; Produced or M-M5 journal | Unique operation-local identity; generator/restart collision seam | Not permission or proof of journal existence. OWNER DECISION S-02; otherwise DEFER. |
| PublicationResult and VerifiedCommitted/FailedBeforePublicEffect/PublishedUnverified/UncertainPublication — P/storage.api | Receipt, failure, recovery reference; M-M5 workflow | Public-effect state outlives cancellation; provider/journal fault seam | Not compilable standalone without support types; DEFER. |
| PublicationReceipt, PublicationFailure, RecoveryReference — P/R storage.api (placement not frozen) | Transaction/evidence/destination/checkpoint; M-M5 workflow | Receipt only after full readback + durable terminal checkpoint; restart seam | Constructor trust, privacy and schema unresolved; DEFER exact signatures. |
| WriteDestinationCapability / DocumentPublisher — P/storage.api (no exact signatures supplied) | Bound authority and pinned storage bytes; M-M5 injected publisher | Single authorized destination; write/readback/close under journal control; provider seam | No PDF dependency or ambient grant acquisition. DEFER. |
| RecoveryRecord / recovery-store operations — R/storage.api (logical grammar only) | Versioned checksummed predecessor chain; M-M5 journal, later reconciler | Durable lifetime independent of coroutine/Activity; filesystem crash seam | Directory-sync/API26 proof outstanding; DEFER. |
| PdfValidationResult and Accepted/Rejected/Unsupported — V/pdf.api | Artifact/report; M-M4 validator caller | Cancellation propagates; parser fault/unsupported seam | Grammar is useful but not a complete compiling contract; DEFER to M-M4. |
| ValidatedPdfArtifact, ValidationExpectation, ValidationReport / PdfOutputValidator — V/pdf.api (support signatures absent) | Pinned storage owner, operation-specific expectations; M-M4 | Independent validation and lease lifetime; canonical/fault engine seam | Caller cannot mint acceptance. Engine gate unresolved; DEFER. |
| TransactionAdmission and Acquired/Busy — W/pdf.api | WriteReservation; M-M5 workflow admission | Immediate BUSY, no queue; deterministic competing admission seam | Public member cannot expose private reservation type. DEFER; prefer private admission. |
| WriteReservation / PdfModificationService — W or private pdf.workflow (not frozen) | Capture/transform/validate/publish; M-M5, M-M7 integration | Admission spans uncertainty control; cancel releases only safe ownership; workflow fault seam | No long-held monitor across I/O or picker. DEFER exact signatures. |
| PdfPageComposer / PdfAnnotationWriter and request/plan/receipt types — future pdf.api | Approved operation and engine; later actual consumer | Per-operation immutable inputs/output staging; fidelity seam | Mentioned responsibilities, not candidate declarations ready to freeze. DEFER; no placeholders. |

Direction: future host → pdf.workflow → pdf.api and storage.api;
pdf.editing → storage.api capabilities; storage.transaction → storage.api/core,
**never pdf.api**. No demonstrated cycle in the snapshot candidates. A storage
publisher importing ValidatedPdfArtifact would introduce the forbidden reverse
edge; workflow must unwrap a trusted pinned storage capability instead.

## 3. Static Kotlin / API26 review — not compilation

1. The snapshot block depends on TransactionId in the second file. Omitting that
   file while retaining Produced gives an unresolved reference. Keeping both
   removes that particular dependency error; no build result is implied.
2. Later grammar blocks deliberately omit support declarations/imports/package
   context. PublicationReceipt/Failure/RecoveryReference, validation support types
   and WriteReservation are unresolved if copied as standalone Kotlin. They are
   incomplete designs, not evidence that existing source fails compilation.
3. A public Acquired constructor/property exposing a private WriteReservation is
   not a valid public API visibility boundary. Make admission private or expose
   only the eventual workflow's typed BUSY result; owner approval is required.
4. Public interfaces WorkingSnapshot/SealedSnapshotLease can be implemented by
   callers. Public data-result constructors can wrap such fakes. Valid hash syntax,
   random IDs and sealed result alternatives do not establish trusted capture.
   The same construction issue must be addressed for validated artifacts/receipts.
5. Internal visibility is module-wide, **not package-private**. This is one :app
   module: an internal constructor does not isolate M from all other production
   packages. Trust requires narrow minting sites, code review and no public
   path/boolean-based factories; do not promise cryptographic module isolation.
6. PFD/Closeable are available on API26; Android types remain in storage.api,
   consistent with its current native bridge, not Android-free core. Kotlin value
   classes/data objects are language/toolchain features, not newer Android APIs.
   No obvious API>26 call is present in the candidate signatures. Actual FD modes,
   seekability, independent offsets, sync behavior and close races are NOT VERIFIED.
7. CaptureLimits is positive but permits ceilings above H-07. Enforce the effective
   per-source maximum in the implementation; limits cannot override global quota.
   SnapshotEvidence rejects zero bytes: empty input must reject, not fabricate a
   positive length. Byte arithmetic must be overflow-safe and based on observations.
8. requestClose/awaitReleased do not yet specify lease-close error propagation,
   closed versus closing, cancellation of a waiter, or ownership when success races
   cancellation. CleanupFailed alone loses primary/secondary details. Freeze these
   semantics before source declarations, not after capture is implemented.
9. A nullable registered association does not prove that the supplied location is
   still the registry's current location. Preserve captured-byte truth only, and
   require authoritative confirmation before any registered revision projection.
10. Never label an M transient lease as Provider/TemporaryReadCopy to imply trusted
    revision, or invent a registered SnapshotLease. Keep independent M parser
    consumption. Do not modify ReadHandle/PdfReadSource/native worker.
11. A parser may close a supplied FD or retain it beyond open. A raw descriptor
    getter does not define transfer. A duplicated FD can share an offset: dup alone
    is not proof of independent seek position. Parser integration must explicitly
    own its descriptor and release the snapshot pin once after parser completion.

These are design blockers/limitations, not discovered deployed production defects.

## 4. S-01–S-04 alternatives

Every alternative below is **PROPOSED — OWNER APPROVAL REQUIRED**. Recommendations
are not selections or approved signatures.

| Sign-off | Alternatives and recommended direction |
| --- | --- |
| S-01 construction | A: final public non-data WorkingSnapshot wrapper with internal constructor/internal owner port, minting only after sealed-file verification; lease likewise final, no copy()/public factory. B: nonpublic physical owner and a final public read-only handle with a restricted minting bridge. Both share one physical owner. Recommend A for the smallest surface, subject to exact constructor/port review and explicit single-module trust limitation. Public interfaces may remain injection seams, but arbitrary implementations must not authorize trusted publication. IDs are values, not capabilities. |
| S-02 initial slice | A: snapshot source/owner/lease/results plus origin Produced and TransactionId in R. B: initial source-only origin, defer Produced and R until M-M3 staging has an actual consumer. Recommend B to avoid an unused journal file. If M-M2B has no approved test/consumer seam, defer declarations rather than create skeleton infrastructure. No P/V/W files now. |
| S-03 typed errors | A: retain Existing but whitelist applicable AppError categories and add explicit Closed, typed cleanup diagnostics and primary/secondary rules. B: narrow M-specific access/resource/seal/closing/closed/cleanup causes with a reviewed adapter from existing errors. Recommend B, avoiding misleading unrelated rendering/registration errors. CancellationException and programmer defects propagate. Record cleanup failures without replacing primary failure/cancellation; never return a partial success. Canceling awaitReleased must not cancel the owner cleanup job. Terminal cleanup pending retains quota/ownership and is discoverable; repeated close is idempotent, not an unsafe repeated native close. Exact declarations remain unapproved. |
| S-04 lease/parser | A: future parser receives a scoped borrowed read-only descriptor and is forbidden to close/retain it, only if its documented behavior supports this. B: a restricted adapter takes an owned independent read-only descriptor plus pin, closes parser first, releases pin once even on error/cancel; descriptor ownership is explicitly transferred and the old wrapper invalidated. Recommend B where parsers own descriptors. Test borrowed/owned adapters against actual engine behavior; no generic close-tolerance assumption. No renderer bridge now. |

Owner closure must cover cancellation at each acquisition/return boundary, worker
cleanup job ownership, diagnostic privacy and whether resource close errors are
thrown/suppressed or returned. No unbounded wait or arbitrary timeout value is
approved here; M-M3 must demonstrate joined cleanup and explicitly report pending
release when completion cannot be established.

## 5. Binding H reconciliation and A0 differences

| Policy | Review consequence / preserved boundary |
| --- | --- |
| H-01 A | One technical owner for registered/transient/produced bytes. A0 CONTRACTS:119–169 registered-only snapshot/output identity is not applied to transient M input. No alternate physical owner, fabricated DocumentRef or silent core amendment. |
| H-02 A | Specialized results, explicit BUSY and publication uncertainty; no Outcome/AppError changes. Grammar approval is separate from exact Kotlin approval. Closing/closed/pending cleanup must be specified. |
| H-03 A | Durable pre-picker intent before launch, durable URI/flag binding before write. Original handoff's canceled-picker “no mutation” row is not generally provable. Missing result may mean an existing unknown provider object. |
| H-04 A | Immutable checkpoint chain, no overwrite of last valid checkpoint; full durability acknowledgement sequence below. A0's general durable store prose is insufficient implementation proof. |
| H-05 A | Host, SAF, MainActivity/DI and isolated reader protected. Existing root-picker not reused as Save As. Test DocumentsProvider/manifest exception only in separately authorized scope. |
| H-06 A | PDFBox candidate only. Independent structure/operation validation required; no-op hash equality is not semantic validation or engine approval. |
| H-07 B | 64 MiB each source; 128 MiB output; 256 MiB all private M artifacts, including partials/journals/uncertainty; 64 MiB physically free margin; 64 KiB stream buffer; one active write. Central reservations and actual-byte accounting, one disk charge per physical snapshot despite multiple leases. CPU/heap separately bounded/evidenced. |
| H-08 A | Later host invokes async read-only reconciliation after initialization; Reader is not blocked. Existing READ only; local checkpoints may be written. No provider writes, picker, new grants, automatic edit resume or blind retry. |

A0 CONTRACTS:482–509 ReplaceOriginal and registered ModificationReceipt are not
independent M scope. Save As returns actual opaque storage identity, not fabricated
logical output identity; never copy favorite/checkpoint or call rebind to simulate
physical movement. A0 planned service names are not installed methods and do not
require speculative source declarations. M-M1's unresolved H alternatives remain
historical; the newer approved H register controls this review without editing
those earlier records.

## 6. Deferred picker/journal/recovery model

**All state names, record schemas and later method signatures here are DEFERRED;
logical invariants only, not declarations approved for M-M2B.**

1. Prepare authorized inputs, private snapshots/staging and independently validated
   output. Snapshot lifecycle CREATING → SEALED → CLOSING → RELEASED or
   CLEANUP_PENDING is separate from the transaction lifecycle.
2. Persist pre-picker intent with unknown destination. Stage a new version-1,
   sequence-numbered, checksummed, predecessor-bound record; flush/sync, publish
   without replacing prior valid checkpoint, directory sync where supported,
   re-read and verify before acknowledging durability.
3. Once durable launch authorization is issued, treat destination as possibly
   created even if launch acknowledgement/result is lost. Never auto-relaunch
   after recreation. A durable intent by itself cannot prove launch occurred or
   did not occur; retain that ambiguity across crash windows.
4. Observe URI/result flags; until a verified durable binding checkpoint exists,
   do not write content. Unknown/missing URI remains DESTINATION_UNKNOWN, including
   a canceled picker with no proof that no public effect occurred.
5. Revalidate operation-bound destination authority/independence and reserve quota;
   record publishing intent before the single write. No overwrite-original path.
6. Write/close may leave PUBLISHED_UNVERIFIED or UNCERTAIN_PUBLICATION. Read back
   full exact bytes, SHA-256 and length, then acknowledge a durable terminal
   checkpoint before VerifiedCommitted. Match of bytes without that checkpoint
   is not completed commit. Cancellation never implies rollback.
7. FAILED_BEFORE_PUBLIC_EFFECT/ABORTED_CLEAN requires positive absence-of-effect
   proof; no picker-result heuristic. Recoverable/uncertain state retains safe
   artifacts, quota charge and necessary destination evidence. Cleanup pending is
   explicit, not success. Age alone cannot authorize deletion.
8. Later read-only reconciliation validates owned records/schema/chain, checks
   known authorized destinations and returns typed match/mismatch/unavailable/
   unknown states. It may append local recovery checkpoints; it cannot write,
   delete, rediscover an unknown URI by guess, request grants or resume editing.
   Unsupported schema, corrupt chain and revoked READ remain unresolved/blocked.

Records contain only needed transaction/state/source/output evidence, sequence,
checksums, known-or-unknown destination/grant evidence and retention disposition;
no PDF bytes or private URI/path logging. Operationally necessary opaque destination
identity stays in private no-backup records, not UI logs. API26 filesystem and
provider tests must establish exact supported durability; no universal hardware
power-loss guarantee, cross-process lock or provider atomicity is claimed.

## 7. Contract invariants and critical-test traceability

Every row is a **future test plan, NOT RUN**. M-M2B can test approved pure invariant
shapes only; it cannot establish actual copying, filesystem durability, parser
fidelity or genuine provider behavior with a fake. Existing M-M1 test details and
canonical fixture identities remain authoritative; this table does not replace them.

| Case | First contract invariant / test seam | Real proof required / target |
| --- | --- | --- |
| M-T01 | Trusted capture only; exact digest/length; transient identity; independent leases | Fixture 02 immutable capture/reopen, original intact; M-M3/API26 |
| M-T02 | Observed-byte limits, overflow-safe accounting; hints not authority | False/unknown size stream + aggregate/reservation boundaries; M-M3 |
| M-T03 | No partial Captured, cancellation propagates, acquired input closes | Throw/deny/revoke/cancel at acquisition and return barriers; M-M3/M-M6 |
| M-T04 | Evidence refers to captured bytes, not current provider | Concurrent source change seam, no false revision assertion; M-M3/API26 |
| M-T05 | Open/failed staging cannot mint sealed output/publication | Write/close/ENOSPC faults and suppressed cleanup; M-M3 |
| M-T06 | Rejected/Unsupported cannot mint validated artifact | Corrupt 15/protected 14; engine gate BLOCKED, M-M4 |
| M-T07 | Expectation bound to exact inputs/permitted operation | Geometry/order/rotation/content/annotations 08/09/12/13; engine gate BLOCKED, M-M4 |
| M-T08 | VerifiedCommitted requires destination proof + terminal durability | Test-owned Save As full readback/original SHA and access; M-M5/M-M6 |
| M-T09 | Source READ never creates destination WRITE authority | Missing grant/refused create; capability/provider seam; M-M5/M-M6 |
| M-T10 | No silent overwrite; same-name behavior explicit | Provider conflict policy and independent destination proof; M-M5/M-M6 |
| M-T11 | Partial output/uncertainty not success | Mid-write disk full/provider death/network-disconnect simulation; M-M5/M-M6 |
| M-T12 | Cancellation preserves public-effect uncertainty | Before/after commit acknowledgement, no rollback/replay inference; M-M5/M-M6 |
| M-T13 | Durable ordered checkpoints, idempotent bounded reconciliation | Interrupted every transition, unknown URI/schema, backup-safe retention; M-M5/M-M6 |
| M-T14 | Capability revalidated at use, not just picker time | Permission race/revocation before write/readback; M-M5/M-M6 |
| M-T15 | Storage result not logical rebind/move | Source registry/favorite/checkpoint unchanged; later explicit registration separate; M-M5/M-M7 |
| M-T16 | H-07 budgets include partial/uncertain data; no unbounded engine claim | Large/hostile input, disk pressure, engine CPU/heap; M-M3/M-M4/M-M6 |
| M-T17 | Actual read-only/seekable lease and provider-specific semantics | API26 seekable/nonseekable create/write/readback; M-M6, provider exception approval needed |
| M-T18 | Test-only capabilities/fixtures never production assets | Release/package inspection by owner at relevant implementation SHA; M-M6/M-M8 |
| M-T19 | Primary exception/cancellation survives cleanup failure | Deterministic close/unlink/parser faults, suppression and pending ownership; M-M3 onward |
| M-T20 | No user-facing overwrite/delete or ambient provider writes | Static/negative integration checks, injected owned destinations only; M-M5/M-M6/M-M7 |

Original order is unchanged: **M-M2 contracts → M-M3 snapshots AND staging →
M-M4 validation/engine → M-M5 Save As → M-M6 faults/API26 → M-M7 integration
contracts → M-M8 PR/owner acceptance**. M01 inventory/M02 freeze feed contracts;
M03/M04 feed M-M3; M05/M06 feed M-M4; M07 coordinator feeds M-M5; M08/M10 faults
and providers feed M-M6; M09 identity and M12 integration feed M-M7/M-M8;
M11 privacy/backup is cross-cutting, never postponed until after publication.
Journal prerequisites must be proved before M-M5 public effects, not delayed to
M-M6. Future new business logic still needs >=90% lines/>=80% branches and all
critical scenarios; none of those gates is satisfied by this document.

## 8. Proposed exact-declaration sign-off register

**OWNER APPROVAL REQUIRED; all entries OPEN.** The candidate's literal declarations
are design inputs, not approved source. Each name below needs a reviewed exact
visibility, constructor and signature diff before M-M2B. A changed wrapper design
requires a new explicit declaration listing; a blanket file approval is insufficient.
No implementation source or placeholder has been created.

| Declaration / exact candidate member surface | Required owner decision before M-M2B |
| --- | --- |
| `SnapshotId(value: String)` value class, `value`, init | S-01 minting/visibility and validation; identifier is not authority |
| `SnapshotSource(storage: StorageDocumentRef, registered: DocumentRef? = null)` | Approve fields/default and confirmed-association semantics |
| `SnapshotOrigin`; `Source(source: SnapshotSource)`; `Produced(transactionId: TransactionId)` | S-02 retain/defer Produced; exact origin construction restriction |
| `SnapshotCaptureLimits(maxInputBytes: Long)` with positive init | Effective maximum and separate global accounting semantics |
| `SnapshotFailure`; `Existing(error: AppError)`; `CaptureIncomplete`; `SealFailed`; `Closing`; `CleanupFailed` | S-03 accept/revise each alternative, explicit Closed and secondary diagnostics; no silent catch-all |
| `SnapshotCaptureResult`; `Captured(snapshot: WorkingSnapshot)`; `Rejected(reason: SnapshotFailure)` | S-01 trusted constructor chain and S-03 cancellation handoff |
| `SnapshotLeaseResult`; `Acquired(lease: SealedSnapshotLease)`; `Rejected(reason: SnapshotFailure)` | S-01/S-04 authenticity, return/close/cancel race |
| `SnapshotReleaseResult`; `Released`; `CleanupPending(reason: SnapshotFailure)` | S-03 terminal/join semantics and retained cleanup diagnostics |
| `SealedSnapshotLease : Closeable`; `val descriptor: ParcelFileDescriptor`; inherited `close(): Unit` | S-01 wrapper visibility; S-04 borrowed/transferred FD, independent offsets and close behavior |
| `WorkingSnapshot`; `val id: SnapshotId`; `val origin: SnapshotOrigin`; `val evidence: SnapshotEvidence` | S-01 final restricted wrapper vs interface, exact internal owner port signature not supplied yet |
| `suspend WorkingSnapshot.acquireReadLease(): SnapshotLeaseResult` | S-03/S-04 acquisition atomicity and canceled delivery cleanup |
| `WorkingSnapshot.requestClose(): Unit` | Atomic immediate rejection of new leases, idempotence |
| `suspend WorkingSnapshot.awaitReleased(): SnapshotReleaseResult` | Joined cleanup, cancel-safe waiter, explicit pending/closed semantics |
| `SnapshotCapturer`; `suspend capture(source: SnapshotSource, limits: SnapshotCaptureLimits): SnapshotCaptureResult` | Trusted injected implementation boundary, no input ownership ambiguity or partial result |
| `TransactionId(value: String)` value class, `value`, init | Only if S-02 keeps Produced; generation/uniqueness, no authorization implication |

The proposed internal owner port/factory and revised failure declarations are
**not exact-signature-ready**: request their complete signature/construction diff
as part of S-01/S-03 approval. They cannot be invented silently in M-M2B.
All PublicationResult alternatives/supports, WriteDestinationCapability, recovery
operations/records, PdfValidationResult/supports and TransactionAdmission/reservation/
workflow declarations are **DEFERRED, excluded from M-M2B**. Their exact signatures
are not complete in the supplied proposal and need first-consumer approval later.

## 9. Findings, blockers and execution boundary

- **Blocking contract freeze, not a production defect:** S-01 authenticity and
  S-04 parser ownership are unresolved; a public read-only view alone is not proof.
- **Blocking exact initial source slice:** S-02 dependency choice and S-03 complete
  error/cleanup/cancellation declarations need owner sign-off. No five-file
  implementation authorization follows from the proposal's grouping.
- **Later acceptance blockers:** real independent validator/engine approval,
  API26 durability/FD/provider evidence, test-provider scoped authorization and
  actual host Save As bridge. Current read renderer cannot truthfully attest a
  transient snapshot; no bridge is needed for this documentation-only milestone.
- The old A0 identity/receipt/picker assumptions cannot override H policy. They
  are disclosed here; protected source and historical M-M1 records are untouched.

Execution ledger: read-only git status/branch/revision/history/remote inspection,
source/policy/reference text reads and searches; scoped Markdown diff review.
One authorized Markdown file written, staged and committed for publication on the
existing M branch. No other tracked file is changed. No new test, dependency,
public declaration, host integration or production operation is implemented.

**BUILD: NOT RUN. TESTS: NOT RUN. CI: NOT RUN.** Compilation, JVM/Python tests,
Lint, instrumentation, localization/backup/fixture validators and device operations
were prohibited and not executed. Git whitespace/diff inspection is not a test
suite or compile check. No runtime/API26 safety PASS, coverage percentage, PDF
validation success or phase acceptance is claimed. M-M2B/M-M3 have not started.
No PR, merge, rebase, tag or release is authorized or performed.
