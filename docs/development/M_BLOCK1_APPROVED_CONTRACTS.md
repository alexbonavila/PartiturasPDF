# Phase M — Block 1 implemented contract surface

2026-11-03. Owner implementation order: `PARTITURASPDF_PHASE_M_BLOCK1_CODEX_IMPLEMENTATION.md`.
Starting revision: `ad4c0ee07497c0c49471e03f33d5440d4ab76743` on
`feat/pdf-safe-write-m`. **Implementation source, not verified build/runtime evidence.**

The owner closed S-01=A, S-02=B, S-03=B, S-04=B after the historical
[M-M2A review](M_M2_CONTRACT_FREEZE_REVIEW.md). That review remains unchanged.
Public declarations below are the selected source surface; no approval of PDF
editing/validation/publication, integration, or phase acceptance is implied.

## Public surface

Under `com.abdev.partituraspdf.storage.api`, new files only:

| File / declaration | Exact surface (nested result constructors are public values, not mint authority) |
| --- | --- |
| SnapshotContracts.kt / SnapshotId | `@JvmInline value class SnapshotId(val value: String)`; canonical UUID required |
| SnapshotSource | `data class SnapshotSource(val storage: StorageDocumentRef, val registered: DocumentRef? = null)` |
| SnapshotOrigin | sealed; `Source(val source: SnapshotSource)`, `Produced(val transactionId: TransactionId)` |
| SnapshotCaptureLimits | `data class SnapshotCaptureLimits(val maxInputBytes: Long)`; 1 through 64 MiB |
| SnapshotFailure | sealed data objects AccessDenied, AccessRevoked, SourceUnavailable, SourceReadFailure, InputLimitExceeded, InsufficientPrivateSpace, PrivateStorageUnavailable, SealFailure, Closing, Closed, CleanupFailure |
| SnapshotCaptureResult | sealed; `Captured(val snapshot: WorkingSnapshot)`, `Rejected(val reason: SnapshotFailure, val cleanup: SnapshotCleanupStatus = SnapshotCleanupStatus.COMPLETE)` |
| SnapshotCleanupStatus | enum COMPLETE / PENDING; primary rejection and cleanup outcome are independent |
| SnapshotLeaseResult | sealed; `Acquired(val lease: SealedSnapshotLease)`, `Rejected(val reason: SnapshotFailure)` |
| SnapshotReleaseResult | sealed; Released; `CleanupPending(val reason: SnapshotFailure.CleanupFailure)` |
| SnapshotCapturer | `suspend fun capture(source: SnapshotSource, limits: SnapshotCaptureLimits): SnapshotCaptureResult` |
| WorkingSnapshot | final non-data, internal constructor taking SnapshotOwnerPort; `val id: SnapshotId`, `val origin: SnapshotOrigin`, `val evidence: SnapshotEvidence`; `suspend fun acquireReadLease(): SnapshotLeaseResult`; `fun requestClose()`; `suspend fun awaitReleased(): SnapshotReleaseResult` |
| SealedSnapshotLease | final non-data, internal constructor taking SnapshotLeasePort; `val descriptor: ParcelFileDescriptor`; `fun transferOwnership(): SealedSnapshotLease`; `override fun close()` from Closeable |
| RecoveryContracts.kt / TransactionId | `@JvmInline value class TransactionId(val value: String)`; canonical UUID required |

Produced/TransactionId have real staging/journal consumers in this delivered slice;
they are not B1.1 placeholders. There is no public recovery, parser, validator,
publisher, reservation, destination grant or workflow API.

## Internal ownership and trust

- SnapshotOwnerPort: immutable identity/origin/evidence plus acquire/requestClose/
  awaitReleased. SnapshotLeasePort: descriptor and close. These ports bridge the
  public wrappers to a private physical owner, never accept arbitrary public paths.
- PrivateSnapshotStore.Writer.finish is the sole WorkingSnapshot minting site.
  It flushes/syncs/closes, checks measured SHA/length, renames without replacement
  under the root mutation mutex, removes write permission, syncs directory and
  verifies the final file again. A sealed candidate is **not validated PDF**.
- TransactionSnapshotCapturer uses existing injected openRead; never openSeekable,
  registry mutation or provider metadata as attestation. A structured cancellation
  child attempts handle closure during blocking reads. Provider responsiveness
  remains unproven; even read/close may block in a hostile provider.
- SealedSnapshotLease transfers the same port and pin, invalidates the old wrapper,
  and rejects its later close/access. Independent acquisitions use independent RO
  PFD opens and lseek, never dup. Failure to close a leased resource retains the
  backing file/charge and yields cleanup pending after owner closure.
- The injected scope owns asynchronous cleanup; composition must retain it through
  all snapshot/lease releases, await results, and join it. A canceled waiter does
  not cancel cleanup. No global job/singleton is installed. Single-module internal
  visibility is review discipline, not a hostile-code security boundary.

## Filesystem, quota and admission

PrivateRoot.forContext selects the no-backup transaction subtree. Each component
must receive the **same root and budget**; construction must happen off the main
thread. Root/file checks reject symlinks and escape. This private process owner,
not a cross-process filesystem lock, serializes journal/seal publication. A
second independently constructed owner for the same root is unsupported.

MStorageBudget reserves capacity before writing, enforces <=64 MiB/source,
<=128 MiB/output, <=256 MiB aggregate, 64 MiB free margin, and counts physical
files once. Sequential buffer is 64 KiB. Tests can lower ceilings, not the free
margin. Pending reservations are conservatively counted against free space;
this can reject some physically feasible concurrent operations. ENOSPC races
still reject. A charge is removed only after confirmed owned deletion.

Unknown/recovery/partial/checkpoint files are retained and charged, including on
restart. Scan errors block admission. Failed creation releases its reservation only after a positive NOFOLLOW absence
observation. Present or uncertain artifacts retain their charge and are reported
as cleanup pending. Invalid/overflowing observations reject with a typed storage
failure; arithmetic never wraps or escapes as an accounting ArithmeticException. Empty transaction
directories are retained, not recursively swept; the bounded discovery limit can
therefore require later explicitly reviewed housekeeping, not arbitrary deletion.

WriteAdmissionGate in pdf.workflow is injected: tryAcquire returns immediate
Busy or an idempotent Closeable admission. No queue, long-held synchronous lock,
normal Reader lock or process singleton. The future workflow must hold one
admission across its whole transaction and inject the single shared storage
owner. No production workflow/host is wired by Block 1; low-level storage APIs
are internal primitives, not a standalone authorized mutation workflow.

## Journal grammar and recovery

PrivateJournal.appendPrivate(transaction, state, source?, output?) returns Durable,
InvalidTransition, Blocked, CheckpointUnconfirmed or DurabilityUnavailable.
PrivateRecord carries canonical transaction, positive sequence, state, predecessor,
optional source/output evidence and typed DestinationState.Unknown.
RecordCodec implements schema 1 with magic, deterministic encoding, payload length,
SHA-256 trailer, strict decoding/re-encoding, 64 KiB payload and 256 checkpoint caps.

Writable transitions: genesis PREPARING → PRIVATE_INPUT_SEALED →
PRIVATE_OUTPUT_SEALED (unvalidated); each private working state may enter
FAILED_RECOVERABLE or CLEANUP_PENDING while preserving introduced evidence.
Reserved picker/public/verified/clean-abort states are represented in the enum
but **not reachable through appendPrivate**. In particular, PRIVATE_OUTPUT_SEALED
is insufficient for PREPARED_FOR_PICKER: future independent validation and an
approved host-bound durable authorization are required. Unknown picker/public
records are conservatively BLOCKED, never replayed or treated as clean abort.

Every append uses new exclusive tmp, write/flush/file sync/close, same-filesystem
no-replace-precondition rename, required directory sync, reread/verify, then ack.
New ancestors are synchronized too. Required sync failure cannot produce Durable;
previous records remain intact. Canceled calls do not return Durable, even if a
finite local critical section reached disk. No universal power-loss claim.

PrivateRecovery.scan returns Complete(classifications, chargedBytes) or Blocked.
Complete means the scan classified entries, **not successful transactions**.
Unfinished/private-output-unvalidated/cleanup-pending/blocked classifications
never authorize external effects. Unbound/unknown/partial files and malformed
journals remain retained. Every sealed artifact required by checkpoint evidence must exist and match that
evidence; checking only the files that happen to be present is insufficient.
No descriptor count is reconstructed. No external inspector is implemented.
Only the live, known snapshot/writer owner can delete its own private artifact;
recovery has no destructive sweep API. Future unknown destinations require
read-only reconciliation using existing authority; no new grants or writes.

## Binding policy mapping

| Policy | Block 1 disposition |
| --- | --- |
| H-01 / S-01 | One physical owner; no registered ID manufacture or public seal factory |
| H-02 / S-03 | Narrow failure values, cancellation/programmer defects propagate; cleanup retained |
| H-03 | Typed unknown destination and reserved states; no picker/public write possible |
| H-04 | Actual immutable file/directory sync protocol; API26 proof PENDING OWNER-RUN |
| H-05 | Host/manifest/SAF/Reader untouched; no unnecessary new test provider |
| H-06 | No engine/dependency or validated artifact; semantic validation deferred |
| H-07 | Central physical ledger/reservations, fixed upper ceilings, independent admission primitive |
| H-08 | Bounded conservative private scan, no startup wiring/provider reconciliation |
| S-02 | Transaction identity/Produced introduced with actual staging/journal consumers |
| S-04 | Independent PFD opens and transfer; parser adapter intentionally deferred |

BUILD/TESTS/LINT/CI/COVERAGE/INSTRUMENTATION: **NOT RUN**.

## Block 1 correction review — 2026-11-03

At baseline `b06fcebe7e07b2fdd0a36e319118654253184cc5`, four source defects were
confirmed: incomplete artifact enumeration, absent-file reservation retention,
hidden cleanup failures in ordinary rejections, and unchecked accounting overflow.
The correction adds Rejected.cleanup (typed COMPLETE/PENDING), retaining the
original reason; cancellation/programmer defects still propagate with a sanitized
SnapshotCleanupPendingException secondary diagnostic. No Throwable/path/message
is placed in public result values. A close whose completion is uncertain retains
the file and charge instead of claiming clean release. Shared Outcome/AppError
and all H/S decisions remain unchanged. Validation remains NOT RUN.
