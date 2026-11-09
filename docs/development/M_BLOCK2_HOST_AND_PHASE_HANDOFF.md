# M Block 2 future-host handoff — integration NOT IMPLEMENTED

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
