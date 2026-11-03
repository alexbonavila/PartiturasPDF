# M Block 2 — implemented source boundaries

2026-11-03; baseline `c9b2f94a6668fd8dce4e7a179e001e35b54af562`.
**Source delivery for review, NOT compiled/tested/accepted.**

- pdf.api/MModificationContracts: PageSelection(index, quarterTurns), immutable
  OrderedPagePlan; typed PdfOperationFailure and diagnostic PdfValidationReport;
  final internal-constructor ValidatedPdfArtifact(output owner, frozen plan).
  Only a future real validator may mint production authority. No production
  minting site exists; engine/validator defaults return unsupported.
- pdf.editing/PdfModificationEngine: injected transform into existing bounded
  stage; distinct PdfOperationValidator. EngineResult.Produced means unvalidated.
  No native adapter/dependency; no copy-as-validation fallback.
- storage.api/MPublicationContracts: final restricted WriteDestinationCapability
  around internal NewDestinationPort; typed PublicationResult alternatives. No
  registered output identity or metadata mutation. Single-module internal is
  reviewed trust, not isolation from malicious same-UID code.
- pdf.workflow/MTransactionCoordinator: reuses Block 1 gate, real capture, shared
  store/budget, sealed leases and journal. prepare(source, plan) returns Busy,
  Rejected or session + opaque MPickerHandoff. Token consumed once by session;
  mismatched/duplicate callbacks cannot write. Session retains ownership after
  possible launch/public uncertainty rather than claim rollback.
- storage.transaction/MPublicationJournal: schema v2 sidecar in private publication
  directory, checksum/sequence/predecessor + immutable v1-anchor and plan/source/
  output/destination fields. Existing v1 RecordCodec/appendPrivate unchanged.
  Each v2 chain validates its frozen v1 prefix; no v1 bytes rewritten. Old
  PrivateRecovery conservatively blocks unfamiliar sidecars; MReadOnlyReconciler
  reads v2 independently and distinguishes legacy-only histories.
- NewDocumentPublisher: one-attempt capability tracking; bounded observed fresh
  readback. It cannot issue final verified receipts; workflow owns final ack.
  MAndroidDestination uses existing supplied result URI/flags and live URI rights,
  plus mandatory injected provider-specific create-new/independence proof. There
  is no installed production proof strategy or launcher. URI inequality/display
  name/WRITE grant alone never suffice. Adapter uses `w` once only for proven
  new empty destinations; no mode fallback/truncation of an existing original.
- MReadOnlyReconciler: bounded private classification/rehash, existing-authorized
  reader port only. A matching read does not mint a new commit. Missing sealed
  files block even if a journal contains a claimed hash. No provider write/grant.

## State progression and durability

PREPARING → PRIVATE_INPUT_SEALED → PRIVATE_OUTPUT_SEALED →
PRIVATE_OUTPUT_VALIDATED → PREPARED_FOR_PICKER → PICKER_MAY_HAVE_LAUNCHED →
DESTINATION_OBSERVED → DESTINATION_BOUND → PUBLISHING → PUBLISHED_UNVERIFIED →
READBACK_OBSERVED → VERIFIED_COMMITTED.

Missing/canceled callback after launch permission enters DESTINATION_UNKNOWN;
no clean cancellation or automatic retry. Every transition has an allowlist and
freezes introduced evidence. Source and output files share their transaction
namespace (new internal captureForTransaction path; existing capture unchanged).
V2 records are bounded to 64 KiB and 256 entries, checksum exact encoding and
reject noncanonical round trips; URI limited to 4096 characters/modified UTF bound.
Flags only READ/WRITE/PERSISTABLE. Only private no-backup journal stores opaque
URI; no logging. A source ID, URI, report or record alone is not permission.

Write new tmp → flush/file sync/close → checked no-replace rename → directory
sync → exact reread → acknowledge. New ancestors synced. Failed sync/checkpoint
returns no durable permission; tmp/unknown artifacts retained/charged. There is
no power-loss or cross-process locking claim. Corrupt/unrecognized histories block.

Workflow issues its token only after durable may-have-launched. It binds observed
URI/flags before PUBLISHING, then opens at most one writer. Fresh readback must
match the exact pinned artifact evidence before terminal ack/receipt. Future
host must not launch a picker from plain TransactionId. No host is wired now.

## Important source-review limits

This initial source slice does NOT complete all requested fault cases or semantic
report detail. There is no real semantic validator, native transform, qualified
engine, provider-general independence proof or completed actual resolver/grant
acceptance. Token/protocol happy path requires privileged TEST-only validation
and destination adapters until the engine gate. Production defaults reject.

Uncertainty deliberately retains the admission/artifacts; no owner-resolution
release API or automatic replay is supplied. Provider cancellation may block;
finite local critical sections do not prove all devices respond promptly.
Block 1 cleanup guarantees are reused, but new session-level exceptional cleanup
and all provider/journal races still need owner review and tests before acceptance.
No public UI Save As, integration readiness or safety PASS is claimed.
