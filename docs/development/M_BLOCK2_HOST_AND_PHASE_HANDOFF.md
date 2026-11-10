# M Block 2 host integration and phase handoff

Current source status: see [Block C robustness](#block-c-robustness-disposition--2026-11-10)
and the [final validation plan](M_FINAL_VALIDATION_AND_ACCEPTANCE.md).
The dated sections below preserve their historical authorization and evidence.

2026-11-03. Host/MainActivity/DI, K/L, core/SAF/Reader/registry remain protected.
No edits to those owners and no user-facing Save As.

Future host consumes only an opaque MPickerHandoff issued after durable
PICKER_MAY_HAVE_LAUNCHED, launches ACTION_CREATE_DOCUMENT/application/pdf once,
and returns exact operation callback token plus result URI/observed flags.
TransactionId/display name is not launch authority. No recreation relaunch.
Token mismatch/late/duplicate callback cannot write. Cancellation/missing URI is
possible orphan state, not proof no create occurred. Suggested names are display
hints; there is no identity derived from them. The present internal request lacks
full production presentation/result integration, deliberately deferred to host.

MAndroidDestination requires existing rights and provider-specific proof of new,
empty, source-independent identity. No generic proof is installed. A host cannot
simply pass arbitrary WRITE URI/tree grants; provider aliases and same-name
collisions require explicit provenance. No source original is ever overwritten.
New storage result is unregistered; favorites/reading position never inherited.

Future async startup discovery may invoke MReadOnlyReconciler using already-held
READ only, without blocking normal Reader. It never launches, writes, deletes,
regrants or resumes edits. Unknown URI/rights/schema/missing files require owner
resolution; no blind replay. A match observed on restart is not a new committed
receipt. Retained admission/uncertainty needs an explicit future resolution policy.

K continues ordinary observed-row identity/registry contracts; L retains one
ReaderRoute/session/native worker and original checkpoint lifetime. N/O/P may
consume immutable plans/approved real engine capabilities only under separate
feature authorization. No merge, annotation UI, Replace Original, rebind/move,
backup/restore or D2D work. No claim of cross-phase runtime compatibility.

Engine and coverage gates remain separate owner decisions; physical API27 tablet,
real picker/grant transitions and accepted dependency/semantic proof remain
prerequisites to actual production host integration.

## Block A progress — 2026-11-09

Production transformation/validation source is now available through the explicit
`MTransactionCoordinator.production(...)` factory, using PDFBox2.0.27.0 and BC1.86
main dependencies, no synthetic fixture access. Default coordinator construction
still returns unsupported. See [engine decision](M_ENGINE_EVALUATION.md#block-a-production-implementation--2026-11-09)
and [current gate ledger](M_PDFBOX_SECURITY_AND_PACKAGING_GATES.md#block-a-current-gates--2026-11-09).

**IMPLEMENTED — NOT EXECUTED; mandatory security/runtime/packaging gates remain
open, so Block A is not accepted and the host must not activate this factory.**
Block B must close those gates before host binding, implement the approved real
Save As picker/result capability and preserve existing independence proof, receipt,
callback, recovery and metadata boundaries above. No host or K/L changes here.
No benchmark or physical memory campaign is scheduled; owner explicitly omitted
it. This is not leak-free, hostile-parser safety, legal or physical acceptance.

## Block B Android host implementation — 2026-11-10

**IMPLEMENTED — NOT RUNTIME VERIFIED. Production publication remains UNAPPROVED.**
Starting source `1e24c95c1d694123e4f76a6f254d736c15838ac7`. This dated entry
supersedes earlier prospective/no-host statements only for the expressly authorized
Block B scope. This assignment permits gated host binding before the open engine
gates close; it does not permit qualified-engine selection. Owner deferred comprehensive validation and the six known Block A
failures to final Phase M stabilization; no acceptance/security gate is waived.

### Ownership and data flow

- `AppGraphFactory` constructs application-only `MEditingDependencies`; injected
  legacy/test graphs can omit it. No new dependency, module, manifest permission,
  schema, global service or alternate storage owner.
- `ProductionHostSession` retains one `MEditCoordinator` in `AppHostFeatures`.
  `AppHostViewModel` supplies current navigation revision/Reader intent/request,
  verifies edit entry against the active Reader and observes route invalidation.
  Editing retires/joins BEFORE Reader/repositories/graph release. Activity instances,
  Views, descriptors, native frames and Reader controllers are not feature state.
- `MEditDraft` owns only frozen page-selection data (1..10000 observed pages),
  inclusion, relative reorder and modulo-four turns. Empty output/duplicates are
  forbidden; plan freezing copies into the existing immutable `OrderedPagePlan`.
  Display uses one-based page numbers; engine selections remain zero-based.
- `MPageEditor` is a Material3 Dialog over the existing mounted Reader. It has no
  numeric input/IME, native renderer, thumbnails, source-byte mutation or second
  route. Scrollable page/header content, wrapping controls, stable original-index
  keys and >=48dp controls support narrow/large-font layouts. Entry and result
  presentation are controlled in AppHost; Reader production files are untouched.
- The retained host preserves drafts on Activity recreation; process death does
  NOT restore a draft, session, capability, callback token or automatic edit.
  Registered/transient identity is copied from the existing original Reader intent.
  Names come only from already observed library metadata, otherwise localized
  fallback. The suggested name is bounded/sanitized metadata, never URI identity.

### Explicit production authorization and provider boundary

`MProductionWriteAuthorization` unconditionally reports UNAPPROVED. There is no
preference/build-type/hidden gesture/serialized switch. UI Save As stays disabled;
feature save rejects BEFORE source resolution/capture; the Android backend also
rejects preparation/binding. Its coordinator uses existing unavailable defaults,
never `MTransactionCoordinator.production(...)`. Draft viewing is allowed.
Changing that boundary requires a separate owner-approved source change after
engine/security/distribution acceptance, not a runtime flag.

The Android destination bridge delegates ONLY to `MAndroidDestination`, requiring
observed READ+WRITE flags and existing live rights. No provider-general independence
proof was established. Production injects `NewDocumentProvenance { false }`:
**all providers remain unsupported for publication**. Test-provider authority is
not a production allowlist, and no claim is made that CREATE_DOCUMENT, an empty
size or a different URI proves nonaliasing. No direct resolver output stream,
source overwrite, grant escalation, automatic registration/favorite/checkpoint
transfer, output opening or selected-root change is installed.

### Picker and callback lifecycle

`bindMDocumentPicker` registers StartActivityForResult with an operation-specific
key inside MainActivity's lifecycle. Intent: ACTION_CREATE_DOCUMENT,
CATEGORY_OPENABLE, application/pdf, bounded localized display-name hint and
READ/WRITE request flags. Existing SAF root picker remains separate.

Only an actual `MPreparation.Ready` handoff can be claimed; claim precedes launch
and is one-shot. Recreation reconnects the same retained operation/token without
relaunching. Callback handling checks exact operation/token, launch claim, source
route and consumption before any bind/write. Current registered location is
resolved/rechecked at preparation, after Ready and before destination binding.
Background native-session retirement does not itself change Reader intent;
provider access is still checked by the existing capture/write contracts.

Accepted flag, URI and flags remain raw in-memory observations, not capability or
Bundle state. Non-OK results ignore any supplied URI for publication. Missing URI,
missing rights, unsupported proof, stale/replaced route, unknown/duplicate token or
retired owner never permits an uncontrolled write. Existing session/publisher keep
one writer and exact readback/terminal-checkpoint requirements. No retry/delete.

A null/untrusted callback destination completes the existing session with no
capability. The existing journal then records DESTINATION_UNKNOWN; it does not
persist an unproven candidate URI. In-memory observations are inspectable but die
with the process. Durable rejected-candidate attribution or a user-driven resolution
UI needs a separately reviewed contract, not a fabricated destination capability.

Cancellation before preparation has no public effect. Cancellation origins
(draft/preparing/before claim/after claim) are distinguished in presentation.
Once Ready's conservative MAY_HAVE_LAUNCHED checkpoint exists, cancellation or
launch failure is retained as uncertain even when the UI never claimed launch.
Verified public success remains verified independently of cleanup PENDING.
Uncertain sessions retain private files/quota/admission; closing the dialog or host
is not an automatic owner acknowledgement. No "clean rollback" is invented.
A cancellation return across IO dispatch cannot lose an already-created Ready:
its session is captured before return and settled without a public capability.
Primary defects/cancellation remain observable; secondary cleanup is suppressed.

### Process restoration and read-only recovery

Only a pending Boolean is saved alongside the existing host lifetime marker.
A new process has no matching live session: the feature exposes owner-resolution
state and old registry results cannot bind to a fresh random operation. Rotation
with the retained host keeps the original request. No URI, draft rows, source grant,
validated artifact or native resource is manually serialized by this feature.

On the first editing entry, one IO scan enumerates at most256 confined transaction
directories and calls the existing `MReadOnlyReconciler`. A conclusively absent root
returns empty without creating transaction storage; unknown/symlink/invalid histories
block. One runtime shares root, budget, store, journal and admission. Known destination
reads check already-held READ permission and use only `r`; no permission requests,
new receipt, replay, provider mutation, deletion or automatic cleanup. Match-observed
and verified-record-observed remain historical observations, not new publications.
Uncertain/corrupt/legacy histories block new saves in the presentation. No fully
safe user-driven owner-resolution control is installed; unresolved cases stay
visibly blocked. Synchronous provider IPC remains an interruption/latency limitation,
not a hard cancellation deadline or a newly authorized benchmarking campaign.

### Block C and final acceptance

Block C must stabilize the six Block A failures, execute all new source tests and
existing regressions, close engine/security/licensing/APK/provider gates and review
any proposed real-provider proof/resolution contract. Only then may the owner
approve production activation. Authentic picker/grant/process-death behavior,
API26/27/36, accessibility/linguistic/physical functional acceptance where required,
new-logic coverage and latest-revision CI remain pending. The separate memory/
performance profiling campaign stays omitted by owner decision. No acceptance,
merge, PR, release or permission to start Block C is inferred from this delivery.

## Block C robustness disposition — 2026-11-10

Current source supersedes Block B's initial one-shot recovery and picker-only
restore marker descriptions. See the [consolidated final campaign and open ledger](M_FINAL_VALIDATION_AND_ACCEPTANCE.md).
No production publication is activated; every provider still fails the production
independence proof, and the host does not select the production engine factory.

- Every explicit edit entry can refresh bounded read-only recovery; concurrent
  scans coalesce. Save requires fresh successful observations, and preparation
  dirties freshness. No scan during prepare/publish/drain, no polling or replay.
- Recovery uses an existing-only root opening: even a disappearance between
  preflight and open cannot create transaction storage. Confinement/symlink checks,
  shared ledger, byte quotas and existing READ-only destination inspection remain.
- Re-entering the same draft preserves modifications. Verified success with cleanup
  PENDING is retained on reopen and is never described as failed/cleaned.
- A Ready created before canceled IO return remains explicitly owned even if it
  never reached the Activity. Draining joins preparation and observes any owner
  installed by its finally. Admission/private evidence stay retained on uncertainty.
- Terminal observations are cached, not reconstructed by invoking an already-used
  token; session verified observation has precedence. No duplicate public writer,
  retry, automatic owner acknowledgement or public deletion.
- The saved Boolean marks preparation, publication and retained uncertainty/debt,
  as well as pending picker. It remains only a recovery hint, never write authority.
- Destination binding/revalidation now insists on observed/live READ+WRITE before
  a writer, still requiring independent provider proof. No protected original,
  transient/registered identity, favorite, checkpoint or root is substituted.

The six Block A FAILs have a demonstrated initial empty-transition qualification
blocker and source correction, but no owner retest result yet. Exact native raster
and other supported-subset behavior may expose additional defects. No source-only
acceptance or claim that all six runtime causes are fully known.

Block C source/review is delivered for final owner validation. All builds/tests,
real picker/grants/process interruption, API26/27/36, new M coverage, applicable
manual/physical/accessibility and current security/packaging/legal evidence remain
open. The new central plan is authoritative for current execution order; old dated
plans/results stay historical. Phase M is not accepted or authorized to merge,
release, activate writes or start another feature.
