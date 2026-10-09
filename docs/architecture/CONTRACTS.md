# Shared contracts and invariants — A0

## Design status and notation

This is the single catalog for shared model and contract ownership in the
approved A0 design. **No declarations below are implemented by this task.**
Operation notation describes inputs and results, not final compiled signatures.
Any Kotlin example is an illustrative design contract. A1 selects only the
minimum separately approved subset; the full catalog is not an implementation
backlog authorization.

Follow [ARCHITECTURE.md](ARCHITECTURE.md) for dependency rules,
[DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) for sequencing and the authoritative
[specification](../PROJECT_SPECIFICATION.md), [agent rules](../../AGENTS.md),
[testing policy](../TESTING_POLICY.md), [change control](../CHANGE_CONTROL.md)
and [Definition of Done](../DEFINITION_OF_DONE.md). Package names below are
relative to `com.abdev.partituraspdf`; packages are future logical boundaries
inside the existing single `:app` module.

## Authoritative ownership catalog

The declaration owner below controls the public definition. The implementation
owner implements it without redeclaring a second interface/model in a feature.
Related supporting types introduced in this document are included in each row.
Names such as provider, renderer or ViewModel refer to implementation roles,
not additional shared contracts.

| Public model / contract | Declaration owner | Implementation / state owner |
| --- | --- | --- |
| `DocumentId`, `DocumentRef`, `FolderRef`, `DocumentInfo`, `DocumentEntry`, `DocumentRevision`, `PageIndex`, `RevisionPageRef` | `core.document` | Immutable domain values; adapters supply observations |
| `DocumentRegistry`, `DocumentMetadataRepository` | `core.document` | `data.metadata` |
| `DocumentCatalog` | `core.document` | `storage.saf` |
| `PdfPoint`, `PdfRect`, `PdfRotation`, `PdfPageGeometry`, `PageViewport`, `PageCoordinateTransform` | `core.geometry` | Pure mathematics in core; adapters supply inspected geometry |
| `AnnotationId`, `Annotation`, `AnnotationChangeSet`, `AnnotationCommand`, `AnnotationDraft`, `AnnotationDraftStore` | `core.annotation` | Annotations owns commands/drafts; `data.metadata` implements private draft persistence |
| `AppPreferences`, `ReaderPreferences`, `PreferencesRepository` | `core.preferences` | `data.preferences` |
| `Outcome<T>`, `AppError` | `core.result` | Producers report errors; presentation localizes them |
| `DocumentReadAccess`, `ReadHandle`, `WorkingSnapshot`, `SnapshotId`, `DocumentPublisher`, `PublicationMode`, `PublicationReceipt`, `PublicationResult`, `OperationId`, `RecoveryArtifactStore`, `RecoveryRecord` | `storage.api` | `storage.saf` / `storage.transaction` |
| `PdfSourceSnapshot`, `PdfReadEngine`, `PdfReadSession`, `PageDisplayInfo`, `RenderRequest`, `RenderedPage`, `RenderRequestId`, `PdfGeometryInspector` | `pdf.api` | `pdf.android` reads; `pdf.geometry` inspects |
| `PagePlacement`, `PageCompositionPlan`, `PdfPageComposer`, `PdfAnnotationWriter` | `pdf.api` | `pdf.editing`; candidate engine not yet adopted |
| `PdfOutputValidator`, `ValidationExpectation`, `ValidationReport`, `ValidatedPdfArtifact`, `PdfModificationRequest`, `PdfModificationService`, `ModificationReceipt` | `pdf.api` | `pdf.geometry` / `pdf.editing` supply validation; `pdf.workflow` coordinates |

`core` contains no Android, Compose, Room, DataStore, PDFBox or SAF implementation
types. Pure coroutine Flow contracts may be selected with approved dependencies;
they do not make domain models Android-specific. Android descriptors, URI
conversion and bitmaps are technical types confined to `storage.api`/`pdf.api`
and their consumers. Do not create a generic opaque `Any` handle that hides
ownership or couples core to an engine.

## Document identity, revisions and access

### Domain values

| Model | Required meaning / fields |
| --- | --- |
| `DocumentId` | Stable app-issued logical identifier; independent of location, display name and hash |
| `DocumentRef` | Opaque provider/location reference represented by Android-free values; may be unregistered; adapter alone interprets URI syntax and permission behavior |
| `FolderRef` | Provider-scoped folder/tree reference; not a raw filesystem path or a document identity |
| `DocumentInfo` | Observed name, MIME type, byte size/time when available and explicit availability/access state; unknown fields stay unknown |
| `DocumentEntry` | One catalog result: reference, observed info, folder association and optional registered `DocumentId`; catalog membership is not registration |
| `DocumentRevision` | App-observed content version bound to a specific logical document; exact snapshot digest/byte length identifies the bytes used by an operation; provider size/time are hints only |
| `PageIndex` | Nonnegative zero-based internal index, also checked against session page count; UI formats index + 1 |
| `RevisionPageRef` | `DocumentId` + exact `DocumentRevision` + `PageIndex`; never a cross-revision stable page identifier |

Renames or verified moves can retain `DocumentId`; they update the associated
`DocumentRef`. Copies normally acquire a new ID. Equal filenames or digests
alone do not justify identity merging. Registry reassociation requires evidence
or explicit user confirmation, not a guess from a failed provider query.

Provider timestamps, lengths, notifications and document IDs may be absent,
stale or unstable. They cannot establish reliable external-change detection.
A revision used for editing must refer to a complete immutable snapshot with
an exact digest and length. A provider stream captured while an external writer
is active may be inconsistent: structure/content validation is still required,
and even successful snapshot capture cannot promise the source did not change
during or after capture. The revision proves the captured bytes, not the current
provider bytes. Unknown/changed source state blocks unsafe replacement.

### Core contracts

- `DocumentRegistry`: resolve a `DocumentId` to its registered reference and
  last-known revision/access state; register a newly selected document; rebind a
  confirmed relocation; record observations/publication and unresolved recovery.
  Missing registration, revoked permission, unavailable provider and confirmed
  deletion are distinct results. The registry does not open PDFs or acquire
  permissions. Data implements persistence; storage does not call it.
- `DocumentCatalog`: list a `FolderRef` and inspect a `DocumentRef`, returning
  `Outcome` of immutable entries/info. Queries are cancellable/background work,
  can fail or return partial observations explicitly, and do not mutate the
  registry. Library/application coordination joins catalog observations with
  registry state. Mutation capabilities for future rename/move/delete require
  their own approved minimal contracts; A0 does not invent their implementation.
- `DocumentMetadataRepository`: observe and update registered metadata such as
  favorite state and last successfully visible page, bound to a known revision.
  Reading position is validated/clamped after a page-count change only under an
  explicit reconciliation rule; annotations are never remapped by index alone.
  Durable writes and migrations must preserve identity and report failures.

### Technical access and snapshots

`DocumentReadAccess` owns acquiring read access to a reference and materializing
an immutable `WorkingSnapshot`. Its operations return `Outcome<ReadHandle>` or
`Outcome<WorkingSnapshot>`; callers provide the registered identity/observation
needed for revision binding. It does not silently copy authoritative documents
into the permanent library. Persist permission grants only when supported by
the selected SAF grant flags; handle revoked grants, unavailable volumes,
security exceptions, cancellation, size limits and disk-full copying.

`ReadHandle` is a closeable, exclusively owned technical input. State its
seekability and descriptor/stream ownership. It may wrap a platform descriptor
inside the storage adapter boundary, never inside a core document model. Opening
a stream is not proof of a complete valid PDF. A non-seekable provider stream
must be materialized into a bounded private copy before `PdfRenderer` opens it.

`WorkingSnapshot` has `SnapshotId`, document identity, exact revision, byte count
and immutable private bytes. It offers independently owned seekable read leases;
its owner cannot mutate or delete it while any lease is active. One descriptor
must not be shared concurrently between sessions/adapters. `PdfSourceSnapshot`
is the `pdf.api` view of this same snapshot, exposing PDF provenance and its
storage lease, not a second independently copied document or revision definition.
Acquisition either finishes and seals a complete snapshot or leaves a clearly
incomplete private artifact for cleanup/recovery; incomplete bytes cannot be
used as a valid source. The operation owns the snapshot; sessions own acquired
leases; closing sessions does not destroy another operation's snapshot.

For produced output, the service reserves the intended output identity (a new
ID for SaveAs, the existing ID for an approved replacement) before sealing its
`WorkingSnapshot`. The bytes determine a new exact revision; they never reuse
the input revision. Registration occurs only after verified publication.
Incomplete producer files are private working files, not `WorkingSnapshot`s.

### Public operation shapes

These shapes fix responsibilities and result semantics without requiring an A1
implementation of every operation. Observe operations use immutable `Flow`
values with explicit availability/error state; mutations and one-shot queries
are suspending `Outcome` operations unless noted. Cancellation always propagates.

| Contract | Input → output design |
| --- | --- |
| `DocumentRegistry` | Resolve ID → registered reference/observation or typed failure; register/rebind/record a confirmed identity + reference/revision → acknowledged durable update |
| `DocumentCatalog` | Folder → entries; reference → info; partial listing is explicitly marked incomplete and is not proof that omitted documents were deleted |
| `DocumentMetadataRepository` | ID/revision → observable immutable metadata; ID/expected revision + changed metadata → acknowledged update or stale-revision/storage failure |
| `DocumentReadAccess` | Reference → exclusive `ReadHandle`; ID/reference + operation limits → sealed `WorkingSnapshot` or access/copy/resource failure |
| `PdfReadSession` | Revision-bound page → `Outcome<PageDisplayInfo>`; `RenderRequest` → `Outcome<RenderedPage>`; close → release/wait for owned resources |
| `PdfGeometryInspector` | `PdfSourceSnapshot` + revision-bound page → `Outcome<PdfPageGeometry>` |
| `PdfPageComposer` | `PageCompositionPlan` + operation-owned output target/identity → `Outcome<WorkingSnapshot>` of complete, unvalidated output |
| `PdfAnnotationWriter` | `PdfSourceSnapshot` + `AnnotationChangeSet` + operation-owned output target/identity → `Outcome<WorkingSnapshot>` of complete, unvalidated output |
| `AnnotationDraftStore` | ID/base revision → recoverable draft or explicit absence; versioned draft → acknowledged durable checkpoint; acknowledged saved generation → clear only that generation |
| `PreferencesRepository` | Observe → typed `AppPreferences` containing `ReaderPreferences`; typed update → acknowledged durable preference change |
| `PdfOutputValidator` | Complete output `WorkingSnapshot` + `ValidationExpectation` → `Outcome<ValidatedPdfArtifact>` with passing report; failed checks retain a diagnostic `ValidationReport` |
| `DocumentPublisher` | Pinned storage snapshot lease + sealed digest/length evidence + destination/mode/operation → `PublicationResult`; cancellation propagates after recording any uncertain write |
| `RecoveryArtifactStore` | Operation/versioned record + owned files → acknowledged durable checkpoint; operation → recorded recovery state; confirmed disposition → bounded cleanup |
| `PdfModificationService` | `PdfModificationRequest` → `Outcome<ModificationReceipt>` only after required verification/reconciliation; partial receipts accompany typed recovery errors |

Read observations and acknowledgements must not expose Room entities or engine
objects. Their minimal value schemas, Flow failure/retry behavior, close API
and serialization formats are finalized in the separately approved contract
slice, using the owners and semantics here. A1 must not ship ambiguous generic
maps or dummy return values in place of the consumed typed model.

## PDF reading and render lifecycles

| Contract / model | Inputs, outputs and responsibilities |
| --- | --- |
| `PdfReadEngine` | Open one `PdfSourceSnapshot` and return `Outcome<PdfReadSession>`; validate readability/resource limits and acquire its own read lease |
| `PdfReadSession` | Fixed revision and page count; query `PageDisplayInfo`, render `RenderRequest`, close deterministically; no editing or publication |
| `PageDisplayInfo` | Revision-bound page, renderer display dimensions/orientation and declared annotation-rendering capability; not a substitute for exact raw page geometry |
| `RenderRequest` | `RenderRequestId`, revision-bound page, target pixel dimensions/clip and viewport generation; strictly bounded dimensions/allocation |
| `RenderedPage` | Request ID, revision/page/viewport generation, display metadata and a closeable render-buffer lease; bitmap is confined to the technical presentation boundary |

The initial adapter is Android `PdfRenderer`. On API 26, serialize session access
and permit only one native page open at a time; close the page in `finally`
before the next operation. Do not assume parallel page rendering or editing
support. A session rejects calls after close; shutdown waits for in-flight native
work to release the page/renderer/input in the correct order. Define descriptor
ownership transfer explicitly in the adapter so neither double-close nor leaks
occur. Open failure closes every resource it acquired.

Reader owns a session for one reading route/revision and closes it when that
route/session ends. Render off the main thread. The session may serialize
requests; reader limits queued work, prioritizes current/adjacent pages and
discards outdated requests after rapid navigation, zoom or revision changes.
Cache keys include revision, page and render/viewport parameters. An old completed
render cannot overwrite the new viewport. Cancellation of a non-interruptible
native call discards its eventual result and releases the buffer; it does not
promise immediate native interruption.

Reader's cache has an approved total byte budget, not merely a page-count limit.
Eviction releases ownership only after UI consumers release their leases; do not
recycle an in-use bitmap. Test rapid open/close, low memory, giant raster pages,
zoom allocation limits and failed renders. Password-protected, malformed or
unsupported documents return specific errors; API 26 password support must not
be assumed from later renderer APIs. Passwords, if subsequently supported, stay
ephemeral and are never logged or stored in ordinary preferences.

## Exact geometry and coordinate transforms

`PdfGeometryInspector` inspects the same `PdfSourceSnapshot` revision used for
reading/editing and returns `Outcome<PdfPageGeometry>` per `RevisionPageRef`.
`pdf.geometry` owns parsing/extraction; a selected editing/parser engine remains
subject to validation. Android `PdfRenderer` display widths/heights alone cannot
recover arbitrary MediaBox/CropBox origins or UserUnit. Exact geometry unavailable
means safe read-only viewing may continue, but annotation editing is rejected.

| Pure model | Required invariant |
| --- | --- |
| `PdfPoint` | Finite x/y in unrotated PDF default user space; origin/units determined by exact page geometry |
| `PdfRect` | Finite ordered min/max coordinates, nonnegative dimensions; retain nonzero/negative box origins |
| `PdfRotation` | Normalized PDF page clockwise rotation in {0, 90, 180, 270}; distinguish a composition rotation delta |
| `PdfPageGeometry` | Revision-bound page, raw MediaBox, effective CropBox, normalized rotation and positive finite UserUnit; default CropBox/rotation/UserUnit only under verified PDF specification defaults |
| `PageViewport` | Pixel bounds, fit mode, zoom, pan and generation; density is converted at the presentation boundary |
| `PageCoordinateTransform` | Immutable invertible mapping for one geometry/revision/viewport; pure forward and inverse transforms plus clipping information |

PDF coordinates use the unrotated page's bottom-left convention; viewport pixels
use a top-left origin. Transform composition accounts for crop translation,
UserUnit (one default unit is UserUnit / 72 inches), clockwise rotation with
positive output translation, y-axis inversion, fitting, zoom and pan. Do not
apply UserUnit or renderer rotation twice. The renderer's actual visible bounds
must agree with the inspected geometry; disagreement disables annotation input
until resolved. Round-trip error tolerances must be approved and tested; persist
PDF-space doubles, not rounded screen pixels. Clips/hit tests must share this
mapping, including corners and off-page points.

Tests must cover all four rotations, offset/negative boxes, differing crop/media
boxes, non-default UserUnit, mixed dimensions, portrait/landscape, zoom/pan and
degenerate/nonfinite inputs. The permanent dataset is the starting point; absent
geometry cases need separately approved deterministic cases, not modified fixture
expectations. Cache geometry by exact revision/page and invalidate on revision
change. Never reapply a draft using guessed geometry.

## Page composition

`PageCompositionPlan` is an ordered nonempty list of `PagePlacement` plus the
referenced `PdfSourceSnapshot` set. A placement contains source snapshot/revision,
source `PageIndex`, output position determined by list order, and a
`PdfRotation` delta. The resulting page rotation is the source rotation plus the
delta modulo 360. Output provenance preserves which exact source page produced
each destination page, including repeated pages.

- Rotate changes a placement's delta; reorder changes list order.
- Delete removes placements; zero-page output is invalid.
- Merge concatenates placements from multiple immutable source snapshots.
- Extract selects an explicit ordered subset into SaveAs output.

Reject out-of-range pages, missing/released snapshots, revision mismatch, invalid
rotations, excessive resource requests and ambiguous output mappings before
modification. Duplicate source pages are permitted only as intentional explicit
placements. No index operation changes an input snapshot or mutates a source
document. Page-reference remapping is provenance-based; drafts are not silently
reassigned after reordering/deletion or across a newly published identity.

`PdfPageComposer` consumes the validated plan and writes complete private output
owned by the operation. It reports `Outcome` of that private artifact; it never
publishes or updates metadata. The adapter must disclose unsupported properties
before proceeding. Preservation of fonts, raster/vector content, rotation,
boxes, annotations, transparency, links, outlines, forms, encryption and signed
documents cannot be assumed. Define applicable retention requirements for each
approved operation; reject unsafe unsupported cases. Editing may invalidate
signatures; do not promise signature preservation or silently strip protection.

## Annotations, drafts and history

`AnnotationId` identifies one logical annotation within its document/version
mapping. `Annotation` uses `RevisionPageRef`, PDF-space geometry, style and a
supported subtype: Ink (strokes), Highlight/Underline (quadrilaterals), or
FreeText (text, bounds and style). Ordered quadrilateral corner semantics,
stroke widths in PDF units and color/opacity representation must be finalized
consistently with the chosen writer in the approved annotation contract task.
No Compose offsets, pixel widths, engine dictionaries or Room entities occur
in these domain values.

`AnnotationChangeSet` declares its exact base revision and add/remove/update
operations with expected previous values/identities. Add requires an unused ID;
remove/update requires a matching prior annotation. Validate page/geometry
binding and prevent duplicate additions or stale updates. A batch is applied
to one private working copy; an invalid change does not partially publish.

`AnnotationCommand` is a reversible domain add/remove/update command. It records
enough previous/next state for inverse execution. Annotations owns the bounded
in-memory undo/redo history; a new command after undo clears redo, and an accepted
save establishes a new base. History is separate from persisted PDF objects.
Draft persistence preserves current unsaved work; persistence of the entire undo
stack requires separate approval and is not promised here.

`AnnotationDraft` contains document/base revision, revision-bound pages, current
changes and recovery/version metadata. `AnnotationDraftStore` loads, checkpoints
and clears drafts using `Outcome`; `data.metadata` implements private durable
records/files through `storage.api` as needed. Drafts must survive interruption
under explicitly tested checkpoint/durability rules; an in-memory cache or
`SavedStateHandle` alone cannot satisfy recovery. Define the permissible unsaved
checkpoint window before accepting annotations, and report checkpoint failures.
Do not promise recovery of unacknowledged input before a durable checkpoint.

`PdfAnnotationWriter` consumes one exact source snapshot and its change set,
producing private complete output. It writes supported standard Ink, Highlight,
Underline and FreeText objects with valid appearance streams and exposes any
interoperability limitation. It neither publishes nor owns undo history. Never
flatten as an implicit fallback. Unsupported pre-existing objects are preserved
or the operation is rejected under the approved retention rule.

Overlay state has three distinct sources: persisted annotations from the PDF,
unsaved draft changes, and transient pointer/tool previews. Before drawing an
overlay, the reading adapter declares whether the same persisted appearances
are already rendered. If the capability cannot be established, do not claim
duplicate-free display; disable the affected editing path or validate a supported
rendering strategy. A persisted update/removal also requires suppression or
regeneration of the baked previous appearance; simply painting a new overlay
cannot erase it. After save, rebase only against verified output and clear
acknowledged drafts without losing newer commands entered during the save.

Recovery after an external revision change never automatically applies the old
draft by page index. Preserve it separately and require an approved explicit
reconciliation decision. Independently verify annotation dictionaries,
coordinates, appearance streams and display in another PDF reader.

## Preferences and reconciliation

`AppPreferences` holds typed appearance/language and application choices;
`ReaderPreferences` holds typed fit/navigation/reading choices as part of that
preferences model. Enumerated values need stable serialized semantics/defaults
and migration handling. These models do not introduce a settings screen.
`PreferencesRepository` exposes `Flow` of typed preferences and cancellable
durable updates returning `Outcome`; `data.preferences` owns DataStore mapping.
Future manual locale selection must validate API 26/27 resource/activity
integration; newer system-only locale APIs are insufficient.

| External observation | Required response |
| --- | --- |
| Content changes or exact revision mismatch | Invalidate geometry/renders; refresh known content; preserve incompatible drafts separately; reconcile reading position/favorites without pretending content is unchanged |
| Verified rename/move | Update reference while retaining logical identity and metadata; close obsolete access and reopen as needed |
| Permission revoked / provider unavailable | Mark access unavailable with typed error; preserve metadata/drafts and offer future reauthorization; do not infer deletion |
| Confirmed deletion | Mark the confirmed absence; retain recovery/user metadata according to an approved retention rule, rather than cascading an accidental destructive delete |
| SaveAs success | Register new logical identity/reference/revision; associate only metadata intentionally copied under approved rules; original remains unchanged |
| Replacement success | Retain identity, record new revision/reference if necessary, reconcile page provenance and draft base |
| Publication complete but metadata update fails | Keep publication receipt/recovery record, report reconciliation pending and repair idempotently; never blindly repeat publication |

Recovery artifacts and drafts must be excluded from unintended backup/export,
use private access controls and bounded retention, and never expose passwords or
tokens. Backup rules must be audited in the approved storage/persistence work;
the current starter manifest is not evidence of safe sensitive-data handling.

## Errors, cancellation and concurrency

Illustrative Kotlin design only; not compiled or installed:

```kotlin
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}
```

`AppError` is a typed domain error vocabulary: access revoked/denied, unavailable
source, confirmed missing document, password required, invalid/unsupported PDF,
geometry unavailable, stale revision, invalid plan, resource limit, insufficient
space, provider I/O, conflicting operation, validation failure and unresolved
publication/recovery. Retain safe diagnostic context separately from localized
user messages. Expected failures map to this vocabulary; do not swallow arbitrary
programming defects or leak document contents/URIs/tokens into logs.

Coroutine cancellation propagates as `CancellationException`, never an ordinary
`Outcome.Failure`. Use structured concurrency and explicit dispatcher ownership;
do not create unbounded/global jobs. Close resources in `finally`; any necessary
non-cancellable journal/cleanup work is bounded and cannot silently resume the
whole operation. Observe cancellation before committing publication; after a
provider write might have occurred, record what is known for recovery before
propagating cancellation. Cancellation cannot prove rollback.

An `OperationId` identifies one workflow and its recovery artifacts. Acquire
exclusive in-app modification ownership for each affected logical document and
destination; serialize or reject conflicts. Multi-source operations acquire
locks in deterministic order to avoid deadlocks. Snapshot readers can coexist
with modifications without seeing mutable working bytes. App locks do not lock
uncooperative external processes/providers; revisions and provider capabilities
remain necessary safety checks. Draft checkpoint and save coordination must
not clear changes produced after the acknowledged save generation.

## Unified modification and publication workflow

`PdfModificationRequest` identifies an operation, exact source snapshots, either
a page-composition plan or annotation change set, validation expectations and
publication mode/destination. Combining both operations in one edit requires
explicit provenance/remapping rules and separate approval; it is not silently
assumed. `PdfModificationService` is the public orchestration contract implemented
by `pdf.workflow`; features invoke it instead of implementing their own save path.

| Step | Owner and acceptance boundary |
| --- | --- |
| 1. Prepare | Service obtains operation ownership; storage acquires/seals immutable snapshots and confirms access/capabilities; record operation/recovery identity |
| 2. Modify | Composer or writer produces changes on operation-owned private copies; no source writes |
| 3. Complete output | Producer flushes/closes writers, creating a complete private artifact; partial output remains marked incomplete |
| 4. Validate | `PdfOutputValidator` reopens output against `ValidationExpectation`: structure/readability, page count/order/provenance/geometry, retained content and applicable annotations/protection |
| 5. Bind exact artifact | A passing `ValidationReport` seals a `ValidatedPdfArtifact` with length/digest, operation and validation evidence; keep bytes immutable and pin its lease |
| 6. Publish | Service passes the exact validated artifact's pinned storage lease, sealed digest/length and `PublicationMode` to `DocumentPublisher`; transfer those bytes, never reopen a mutable unrelated path |
| 7. Reconcile | Service records publication receipt, new reference/revision and metadata through core contracts; keep durable pending work if reconciliation fails |
| 8. Confirm and release | Confirm verified publication plus reconciliation, acknowledge relevant drafts and release resources; retain required recovery evidence for uncertain outcomes |

Validation definitions live in `pdf.api`. `ValidationExpectation` specifies the
approved content/geometry/annotation retention criteria, including provenance.
`ValidationReport` records actual checks, limitations and failures. Rendering
alone or hash comparison with the input is insufficient: an edited output can
legitimately differ byte-for-byte. `ValidatedPdfArtifact` is a capability for a
specific validated immutable byte sequence, not a Boolean flag beside a mutable
file. Its constructor/sealing path must be controlled by validation; no public
setter can bypass the validation result. In the single module, API discipline
and tests must enforce that restriction; package names alone do not.

`DocumentPublisher` is declared in `storage.api`. It consumes the approved
artifact's storage-readable lease and sealed evidence without importing
`pdf.api` or parsing PDF structures. The artifact's PDF-specific wrapper lives
in `pdf.api`; its underlying immutable `WorkingSnapshot` and verified
length/digest are passed across the storage contract. This keeps storage independent
of PDF and avoids a reverse dependency. The publisher owns destination writes,
capability probing, close/durability status, readback verification when supported
and cleanup/recovery of partial destination files.

`PublicationMode` has two deliberately different modes:

- `SaveAs`: create a separate destination with explicit collision handling and
  no overwrite of the original. Prefer a temporary/incomplete destination name
  followed by provider-supported finalization where verified; if unavailable,
  report and track the partial file until fully completed/verified. Never report
  an interrupted or unverifiable output as a successful save.
- `ReplaceOriginal`: require explicit confirmation, verified source revision,
  retained recoverable original and a validated provider-specific strategy.
  If provider capabilities or concurrent-change/recovery safeguards cannot meet
  the approved acceptance criteria, reject replacement and offer SaveAs. Do not
  truncate the sole original, promise universal atomic rename/replace or claim a
  digest precheck guarantees no later external write.

`PublicationResult` distinguishes verified completion (`PublicationReceipt`),
known failure with recovery state, and uncertain/partial publication requiring
recovery. The receipt contains `OperationId`, resulting reference, exact verified
byte identity and actual durability/readback evidence. Provider completion signals
alone are not universal power-loss durability guarantees. If re-reading or
provider-supported equivalent integrity verification is unavailable, the result
remains unverified rather than ordinary success. Report supported guarantees
per provider; do not invent them.

`ModificationReceipt` identifies verified published output, page provenance/new
revision and reconciliation status. Service returns ordinary success only after
required publication and reconciliation criteria pass. Reconciliation-pending
or publication-uncertain outcomes preserve receipts in recovery state, with typed
errors/status so the UI does not claim full success or automatically retry a save.

`RecoveryArtifactStore` maintains operation-owned files and durable
`RecoveryRecord` checkpoints (prepared, producing, validated, publishing,
published/reconciliation-pending, confirmed, failed/uncertain). Storage owns raw
file/journal mechanics; the service owns PDF operation state and serializes its
recovery payload without importing storage internals. Restart reconciliation
must be idempotent, inspect actual destination state and never blindly overwrite
either original or output. Schema versions, durability boundary, backup exclusions,
quota/retention and cleanup rules require tests before adoption.

Disk-full conditions can occur during snapshotting, editing, validation, journal
updates or provider transfer. Fail closed, preserve original/recovery copies and
do not erase the last useful artifact during cleanup. Provider failure or process
death during publication can leave a visible partial output; retain its reference
and show unresolved state until cleanup/verification is confirmed. Cancellation
after publication starts requires the same reconciliation. Perfect rollback or
recovery cannot be guaranteed when the provider lacks necessary capabilities;
such limitations must block unsafe replacement rather than weaken acceptance.

## Testable invariants and pending validations

| Invariant to test in future approved implementation | Pending technical validation |
| --- | --- |
| Logical identity is independent of URI/name; unavailable is not deleted | Provider identity/relocation evidence and unreliable metadata semantics |
| Every edit/render/geometry/draft is revision-bound; stale results are rejected | Snapshot capture under external writes, fingerprint costs and cache invalidation |
| Sessions/pages/leases close once, including failures/cancellation | API 26 renderer descriptor transfer, blocking-call cancellation and concurrency |
| Transform round trips preserve PDF coordinates across rotations/boxes/UserUnit | Exact geometry inspector, renderer visible bounds and numerical tolerances |
| Plans are nonempty/in-range and output provenance/order is explicit | Engine retention of PDF properties, encrypted/signed input policies |
| Draft commands invert; checkpoint/save generations do not lose newer edits | Private persistence/checkpoint window, recovery schema and annotation ID mapping |
| No duplicated persisted/draft overlay or implicit flattening | API 26/27/36 annotation rendering and independent-viewer appearances |
| Published bytes are the exact validated artifact; originals survive failure | SAF capabilities, readback/durability, partial outputs, external races and replacement recovery |
| Cancellation propagates; conflicts serialize/reject; recovery is idempotent | Coroutine/native boundaries and disk-full/interruption fault injection |

Use the [15 immutable fixtures](../../test-fixtures/README.md) and authoritative
[manifest](../../test-fixtures/manifest.json), with temporary mutation copies.
Missing cases require approved deterministic additions, never changing existing
hashes/expected results. Product-level tests, dependency/security/license audits
and physical-tablet acceptance remain future work, not achievements of A0.
