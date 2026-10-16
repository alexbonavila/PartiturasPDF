# F/G/H interface boundaries and I integration ownership

**Planning snapshot: 2026-10-17.** Source baseline:
`618c9aa753e9597835b80c4862876e8d34e2fc85` on `chore/pre-fgh-readiness`;
verified `main` remains `f94841f9f1eead508dd81b670d0943f3b3ae19bb`.
This document records P04 design recommendations for owner review. It authorizes no F/G/H/I
implementation, branch creation, dependency, API, persistence change or acceptance waiver.

Follow [AGENTS](../../AGENTS.md), [specification](../PROJECT_SPECIFICATION.md),
[architecture](../architecture/ARCHITECTURE.md), [contract catalog](../architecture/CONTRACTS.md),
[development plan](../architecture/DEVELOPMENT_PLAN.md), [testing policy](../TESTING_POLICY.md),
[Definition of Done](../DEFINITION_OF_DONE.md) and [change control](../CHANGE_CONTROL.md).
The A0 documents contain historical designs, not an inventory of installed APIs. The Kotlin
sources below determine available behavior. One `:app` module, minSdk 26, Kotlin/Compose/Material 3
and English/Catalan/Spanish remain binding. Proposed MVP boundaries narrow a future task, not the
full product specification; any omitted product requirement needs explicit owner scope approval.

> **P05 cross-reference (2026-10-17):** The [consolidated deferred acceptance register](PRE_FGH_DEFERRED_ACCEPTANCE.md) is now delivered for owner review. “P05 not started” below records this P04 document's earlier snapshot. D01–D11 remain PENDING OWNER APPROVAL; their recommendations are unchanged.

## 1. Existing implementation inventory

All source paths here are relative to
`app/src/main/java/com/abdev/partituraspdf/`. These contracts exist and compile at the baseline;
this planning task adds none.

| Existing source / API | Available capability and consumer boundary |
| --- | --- |
| `core/document/DocumentModels.kt` | `DocumentId`, logical `DocumentRef`, opaque `StorageDocumentRef` / `FolderRef`, `DocumentEntry`, immutable `DocumentListing(complete)`, observed `DocumentInfo`, `RegisteredDocument`, `DocumentAvailability`, `ReadingPosition`, `DocumentMetadata`; `Unknown` versus `VerifiedSnapshot` revisions. |
| `core/document/DocumentRepositories.kt` | `DocumentCatalog.list/inspect`; registry `register/resolve/findRegistration/recordObservation/rebind`; metadata `observe/setFavorite/setReadingPosition`. No registry enumeration, global favorites query, root store, file mutation or automatic provider reconciliation API. |
| `core/preferences/Preferences.kt` | `PreferencesRepository.observe/update`, `AppPreferences`, `ReaderPreferences`, and exactly `PreferenceChange.Theme/Language/Fit`; stable enum keys listed in section 5. |
| `core/result/Outcome.kt` | `Outcome.Success/Failure`, `Observation.Loading/Ready/Failed`, typed `AppError` categories. Cancellation/programming defects propagate; not every exception is a user-facing failure value. |
| `core/geometry/PdfGeometry.kt` | Validated `PdfPoint`, `PdfRect` and `PdfRotation` values; their existence does not establish an implemented exact PDF geometry inspector. |
| `storage/api/DocumentReadAccess.kt`, `ReadHandle.kt` | `openRead`, bounded `openSeekable`, `ReadAccessLimits`, exclusive `ReadHandle`, `ReadOwnership`, provenance and closure rules. No compiled `snapshot` operation. |
| `pdf/api/PdfReadSource.kt`, `PdfReadEngine.kt`, `PdfReadModels.kt` | Source ownership transfer; engine `open`; session `info/displayInfo/render/close`; owned `RenderedPage` bitmap leases; session/page/request IDs, validation, display info and render ceilings. Full-page rendering, no tile/clip operation. |
| `storage/saf/**` | Implemented read-only `SafDocumentCatalog`, `SafDocumentReadAccess`, URI codecs, provider error/recovery behavior; concrete `SafTreeGrantAccess` has real picker/persisted READ grant mechanics. Only I may use the latter Android integration surface. F/G consume injected contracts, not these internals. |
| `pdf/android/AndroidPdfReadEngine.kt` | Real Android PdfRenderer implementation, serialized native page access and owned resource cleanup. G must not instantiate it or duplicate its renderer. |
| `data/metadata/**` | Room v1 registry/metadata adapters, atomic conditional updates and transactional observation. Storage locations are opaque associations, not PDF content. F/G do not import DAOs/entities/Room implementations. |
| `data/preferences/**` | One application-context DataStore delegate, stable mapping and atomic updates; defaults only after a successful read. Corruption/invalid values are not silently repaired. H/I borrow the same repository; no second production DataStore. |
| `app/di/AppGraph.kt`, `AppGraphFactory.kt`, `AppRuntimeLimits.kt` | Explicit composition and immutable limits. Graph/factory are internal host infrastructure, not feature service locators. No production startup wiring or navigation is installed. |
| `MainActivity.kt`, `ui/theme/Theme.kt` | Starter greeting and branded theme with `darkTheme` input; no library/reader/settings UI or locale-application controller. |

`SnapshotEvidence` / `VerifiedSnapshot` model representation and `SnapshotLease` provenance exist,
but no trusted production snapshot creator is implemented. A0's `WorkingSnapshot`,
`PdfSourceSnapshot`, `PdfGeometryInspector`, editing/annotation/publishing/draft services are
**design-only**, not APIs available to these branches. Do not create substitutes in features.
P03 coverage evidence is recorded separately in [current status](../PROJECT_STATUS.md); it does
not certify any future UI or host behavior.

## 2. Source ownership

The following is the proposed exclusive ownership allocation for later approved tasks, not
permission to edit these paths now. `MAIN` denotes `app/src/main/java/com/abdev/partituraspdf`;
`TEST` and `ANDROID_TEST` denote the matching packages under `src/test/java` and
`src/androidTest/java`.

| Future branch | Exclusive production ownership | Tests and handoff |
| --- | --- | --- |
| `feat/library-f` | `MAIN/library/**`: library presentation/state, browsing/navigation state, catalog/metadata orchestration within F | `TEST/library/**`, `ANDROID_TEST/library/**`; public feature entry point, immutable state/actions, host events and error inventory |
| `feat/reader-g` | `MAIN/reader/**`: reader presentation/state, route-owned sessions, render scheduling/cache and reading-position scheduling | `TEST/reader/**`, `ANDROID_TEST/reader/**`; entry point, registered/transient inputs, close/cancel contract and render ownership evidence |
| `feat/settings-h` | `MAIN/settings/**`: settings presentation/state and typed preference commands | `TEST/settings/**`, `ANDROID_TEST/settings/**`; entry point and loading/error/update semantics, no host application side effects |
| `feat/app-mvp-integration-i` | `MAIN/MainActivity.kt` (existing root package), `MAIN/app/navigation/**`, startup/host/coordinator files under `MAIN/app/**`, and explicitly approved edits to `MAIN/app/di/**` | `TEST/app/**`, `ANDROID_TEST/app/**`, and existing Activity tests only in I's approved scope; wires feature public entry points and verifies end-to-end host lifetimes |

**One integration owner (I)** owns the shared edit queue: `app/src/main/AndroidManifest.xml`,
any future Application registration/class, existing `ui/theme/**`/shared `ui/components/**`,
shared `values*/strings.xml`, resource IDs/navigation destinations, build files/version catalog,
shared test wiring and existing Activity tests. Ownership does not authorize dependencies,
manifest/backup policy changes, CI edits or unrelated shared component work: obtain explicit
approval and integrate prerequisites before dependent feature work.

For disjoint feature strings, recommend dedicated files
`app/src/main/res/values{,-ca,-es}/library_strings.xml`, `reader_strings.xml`,
`settings_strings.xml`, respectively; F/G/H own only their agreed file in each language.
Names must begin `library_`, `reader_`, `settings_`; I owns `app_` cross-feature copy in an agreed
integration file. Agree filenames and plural/format/semantics keys before parallel tasks.
Resources remain module-global despite separate files; duplicate names are conflicts.

`core/**`, `storage/api/**`, `pdf/api/**`, adapter/data implementations, schemas, fixtures,
policies and licenses are outside F/G/H ownership. Any necessary shared change is a separately
owner-approved prerequisite, not an integration-owner blanket exception.

## 3. Phase F — Library

Receive `DocumentCatalog`, `DocumentRegistry` and `DocumentMetadataRepository` explicitly.
Recommended handoff behavior, subject to decisions D01–D05 below:

1. Receive an accepted selected-root `FolderRef` from I after authentic picker/READ authorization.
   F owns the active folder and breadcrumb UI state, not permission acquisition. Request a new
   root through a host callback. Changing root must not release pre-existing grants implicitly.
2. Call `catalog.list(folder)` off the presentation thread through its suspend contract. The SAF
   adapter already filters entries to PDF MIME or directory MIME. A directory entry's `folder`
   is its **parent listing folder**, not a ready-made child `FolderRef`. F must not parse URI
   strings, build filesystem paths or perform a second provider query implementation.
3. Show PDFs and directories distinctly, observed nullable names/sizes without inventing values,
   explicit loading/error/empty states and incomplete-listing status where `complete` is false.
   Folder entry and Back actions use a bounded root/breadcrumb stack. Root boundaries and the
   host conversion of a confirmed directory entry to a child `FolderRef` need D05 approval.
4. Join visible locations with `registry.findRegistration(storage)`. Only confirmed associations
   attach a logical document. A missing row is `NOT_REGISTERED`, not a deleted PDF. Observe
   metadata only for registered entries; cancel observers leaving the active listing. Current
   contracts support favorites for known entries, not an all-library favorites index/query.
5. Explicit “add/register” intent goes to the one I-owned registration coordinator (recommended
   allocation, D03). It uses the actual `register(DocumentRef, StorageDocumentRef)` contract,
   issues an independent app ID, and confirms concurrent `REGISTRATION_CHANGED` outcomes through
   lookup rather than manufacturing another identity or matching filenames/hashes. F can request
   registration before a favorite action; it must not silently register every listing item.
6. `setFavorite` changes only the favorite field. Represent `Observation.Failed` and durable-write
   failures; do not silently replace failed observations with a cached/default success. No Room
   queries or parallel metadata format belongs in F.
7. Opening a registered document sends its logical ref to I/G for fresh resolution. A transient
   open sends its opaque storage ref without creating identity. Registry availability is a
   historical observation, not a gate proving current access. I coordinates known-grant checks
   and confirmed `recordObservation`; G's actual access result supplies evidence. A failed or
   partial listing cannot prove deletion. Confirmed relocation may use I's explicit `rebind`
   flow preserving identity; never infer a move or auto-rebind by display name/content hash.

**Recommended read-only MVP:** browse, navigate folders, open, explicitly register and favorite
within agreed scope; no create/rename/move/copy/delete/import/export/share implementation or fake
menu action. Only `register`, metadata updates and approved observation/rebind coordination change
app metadata; none mutate PDFs. Full-spec mutations, global search/favorites and relocation UI
need separately approved scope/contracts. Local sorting/filtering of loaded entries is not a
complete provider-wide recursive search. Do not claim those product requirements delivered.

## 4. Phase G — Reader

Receive `DocumentReadAccess`, `PdfReadEngine`, `DocumentMetadataRepository`, the necessary registry
contract and immutable read/render limit values supplied by I from `AppRuntimeLimits`; optional
typed page-fit input comes from I. I passes the existing `ReadAccessLimits` / `RenderLimits`
members so G need not import composition implementation types.
No import of F, Room, SAF implementation classes or AppGraph.

| Boundary | Proposed responsibility / invariant |
| --- | --- |
| Registered input | Receive `DocumentRef`, resolve its current `RegisteredDocument.storage` through the injected registry before each new open. Do not navigate using a cached URI as logical identity. Preserve metadata on unavailable/missing registrations. |
| Transient input | Receive `StorageDocumentRef` without assigning an ID; use `PdfReadSource(handle, document = null)`. No metadata writes/fake logical document. I retains grant provenance for transient versus persisted access. |
| Open and transfer | A G route/session owner calls `openSeekable(storage, readLimits)`. Close an unused acquired handle if source construction fails or engine entry never occurs. `PdfReadSource` construction alone does not transfer ownership; engine entry consumes it. After transfer do not close the old wrapper. Engine failure/cancellation owns cleanup; success gives G a `PdfReadSession`. |
| Sessions | One explicit route owner retains the active session across recomposition, not a composable body. On replacement/exit, reject stale output, cancel and join work, close sessions off main after native work, then release remaining leases. Configuration recreation may retain the owner; process death requires a fresh open. I owns navigation/lifecycle signals, G owns the resources. |
| Bitmap leases | Each successful render yields an exclusive `RenderedPage` owned by G's display/cache. Close rejected/evicted leases exactly once after Compose/display consumers stop reading them. Session closure alone does not release published bitmaps. Never recycle an in-use buffer or cache raw bitmaps without their owning lease. |
| Page/display state | Use `session.info.pageCount`, zero-based `PageIndex`, session/generation/revision-bound `ReadPageRef`, `displayInfo` and full-page `RenderRequest`. Display page numbers as index + 1; no wrapping at endpoints. Viewport changes invalidate incompatible requests; no guessed rotation/exact geometry or universal annotation-rendering claim. |
| Cancel/stale results | Route/open generation and viewport/request identity reject obsolete opens/renders after rapid navigation, resize, provider refresh or grant loss. Cancel work, but treat native cancellation as potentially delayed; close any successful late session or unpublished lease. Do not swallow cancellation as `Outcome.Failure`. Respect `STALE_SESSION`, `SESSION_CLOSED`, `REVISION_CHANGED` and validation errors. |
| Temporary input | Direct seekable SAF input is reused; non-seekable input gets a bounded private copy managed by the storage lease/session. G does not duplicate copies, promote them to a permanent library or delete arbitrary cache paths. Close session/handle ownership to trigger adapter cleanup; no external original changes. |
| Availability/revision | Report typed access results to I for confirmed observations using expected location. No metadata deletion on permission/provider failure. Provider time/size and ordinary copies yield no verified snapshot; do not fabricate `SnapshotEvidence` or infer absence from a generic I/O error. |

For registered resume, observe metadata with initial Loading, reconcile the hint only after a
new session supplies valid bounds, and schedule `setReadingPosition` for the **successfully visible**
page (D06/D08). A position with `Unknown(document)` remains a hint; revalidate/clamp or reset under
the approved rule, not an attestation of unchanged bytes. The current Room implementation accepts
an Unknown hint against the transaction's current registry revision without upgrading the hint.
A verified position must carry the exact matching document/revision; on `REVISION_CHANGED`,
retain existing metadata and reconcile explicitly, never overwrite the mismatch or downgrade it
silently. `setReadingPosition(document, null)` clears position while preserving favorites.
Cancelled writes may have committed; reconcile by observing rather than claiming rollback.

`ReadSessionInfo.sourceGeneration` is session-local (the Android engine currently starts at zero);
it is not a cross-open monotonic revision detector. G's own request generation is a separate stale
result guard. Current render API is full-page only: UI scaling/panning may be proposed, but tile or
clip APIs require approval. Default tap zones and gesture arbitration follow the specification;
no editing/annotation tools are implemented as part of this reader boundary.

Provisional limits are 64 MiB **per non-seekable copy**, 4096 pixels per bitmap axis and 4,194,304
pixels **per bitmap**. They do not bound concurrent copies, total bitmap/cache/native peak memory
or establish performance. D07 must approve aggregate memory, cache eviction, prefetch and concurrent
work budgets; recommend one active session and one scheduled native render initially, adding
adjacent-page caching only within measured budgets. Do not invent numerical performance success.

## 5. Phase H — Settings

Inject `PreferencesRepository`; use its cold `Observation` flow and atomic typed updates:

| Existing selection | Values / stable keys | H writes; host effect |
| --- | --- | --- |
| ThemeSelection | SYSTEM `system`, LIGHT `light`, DARK `dark` | `PreferenceChange.Theme`; I applies resolved light/dark state to existing branded `PartiturasPDFTheme(darkTheme=...)`. |
| LanguageSelection | SYSTEM `system`, ENGLISH `en`, CATALAN `ca`, SPANISH `es` | `PreferenceChange.Language`; I applies resources/Activity configuration via an approved API26 mechanism. |
| PageFit | PAGE `page`, WIDTH `width` | `PreferenceChange.Fit`; I supplies the observed preference to G; G recomputes the viewport safely. |

H owns selection/loading/error/pending presentation, not DataStore construction, serialization,
theme globals, Activity recreation or locale configuration. A persisted change and its visible
host effect are separate outcomes. Never show a failed read as system defaults or rewrite corrupt
values; surface expected typed failures, preserve cancellation and escalate programming/corruption
exceptions through an explicit host error boundary without swallowing them. Observe to reconcile
updates after cancellation. Keep unrelated preference fields intact.

API26 cannot rely on Android 13-only per-app locale APIs. The specification calls for evaluating
backwards-compatible locale handling (including Activity/Compose integration); AppCompat is not
currently a direct installed dependency and MainActivity is a ComponentActivity. I must validate
and obtain approval for the smallest mechanism, activity/dependency implications, system-following
behavior, locale reset, resource recreation and process restart on API26/27. H must not silently add
AppCompat, navigation or its own competing locale helper. Preserve English fallback and en/ca/es
strings/semantics in all features. Theme application similarly belongs to I, using existing fixed
brand palettes rather than unapproved dynamic-color changes.

## 6. Phase I — Host and integration

I exclusively owns MainActivity, approved startup/navigation wiring and application-lifetime
factory retention. Construct with the real Application and explicit limits, retain one owner
across Activity recreation, inject **individual borrowed contracts**, and close Room only during
an explicit quiescent shutdown off main. No feature obtains a global AppGraph, initializes its own
Room/DataStore, calls `Application.onTerminate` for correctness or closes shared repositories.
Process death supplies no reliable shutdown callback.

I launches real SAF Activity Results and passes actual returned flags/URIs to the existing
`SafTreeGrantAccess`; persist READ only, even if WRITE is offered. Cancellation/nonpersistable
results remain distinct from accepted roots; verify actual persisted READ capability. Remembered
locations and OS grants are different state. Check a previously known grant before classifying
its disappearance as `PERMISSION_REVOKED`; a first-time/transient missing grant is not that evidence.
Release only an explicitly chosen app grant under approved user intent, never all pre-existing
grants. Selecting another root does not automatically release the old one. One-off document
selection/persistability beyond the current tree helper requires an approved I scope, not a fake
callback or manual URI-grant injection.

I mediates F open/register/folder/picker events, G access/availability results and H setting effects.
It owns registration coordination and confirmed observation/rebind policy if D03 is approved,
application-level error-to-localized-message mapping/recovery actions, destination/back behavior,
root/session restoration and locale/theme application. Feature-specific error states remain local;
I must not catch arbitrary defects and convert them to benign access errors. Navigation carries
small reconstructible registered or transient references, not sessions, descriptors, bitmaps,
entities, AppGraph, provider exceptions or PDF bytes. Private provider locations must not leak into
external routes, analytics or diagnostic reports. Transient routing/restoration is D04/D06 work.

I's integration tests verify one graph/Room owner and DataStore singleton, genuine Activity Result
READ flow, F-to-G registered/transient opens, missing/revoked/provider cases, metadata preservation,
H effects across recreation/restart, session/lease shutdown and stale-result handling. Keep fixtures
in test assets; existing debug acceptance harnesses are not a production navigation implementation.

## 7. Dependency direction and communication

| Consumer | Allowed injected/domain dependencies | Forbidden coupling | Host handoff (proposed behavior, not installed APIs) |
| --- | --- | --- | --- |
| F `library` | `core.document`, `core.result`, approved shared `ui`; catalog/registry/metadata contracts | G/H concrete code, SAF/Room/DataStore implementations, AppGraph/MainActivity | Request root/folder selection, explicit registration, open logical or transient entry; receive accepted root/folder and command results. |
| G `reader` | `core` document/result/geometry/preferences values, `storage.api`, `pdf.api`, approved `ui`, immutable read/render limit inputs | F/H implementations, concrete SAF/PDF/data adapters, `app/di` or global graph lookup | Receive one agreed open description and limits; report access observations, route-close and recovery intent to I. I unwraps `AppRuntimeLimits` into its existing `ReadAccessLimits` / `RenderLimits` members, preserving configuration without a reverse dependency on composition. |
| H `settings` | `core.preferences`, `core.result`, approved shared `ui` | F/G code, concrete DataStore, Android host/locale implementation | Repository updates carry durable preference changes; I independently observes and applies them. No direct H-to-G call or global event bus. |
| I `app` / MainActivity | Feature public entry points, core and technical contracts; concrete adapters only for composition; AppGraphFactory/limits; existing theme | Feature-internal ViewModels/state mutation or duplicated backend algorithms | Explicit callbacks/immutable inputs and repository observations coordinate features. Navigation and lifecycle stay in I. |

Producer adapters retain their existing dependency direction; storage never calls registry/data,
core stays Android-free, PDF uses read contracts, data owns persistence. Features can be tested
against contract doubles without other features being merged. Kotlin `internal` in one module
is not package isolation: review imports and fully-qualified references; no new architecture-test
framework is authorized. Entry-point signatures, optional inputs, callbacks and ownership semantics
must be frozen in an owner-reviewed handoff before parallel implementation; no placeholder API is
created here. New shared cross-feature types require a separately approved contract change, not
copies under F/G/H or an accidental dependency on I's navigation implementation.

## 8. Unresolved design decisions — owner approval register

Every row is **PROPOSED / awaiting explicit owner approval**. Recommendations are not approved
choices. The owner resolves affected rows before dependent code; independent approved work may
continue. Existing policies remain binding.

| ID / decision | Current implementation | Options | Recommended default and consequence | Required approval / owner |
| --- | --- | --- | --- | --- |
| D01 Selected SAF root across process death | No selected-root repository/key; OS retains grants, not the user's selected-root order. | Session-only root with reselection; separately persisted location store; approved extension of preferences. | Keep root selection in host memory until a dedicated persistence design is approved. This does not meet full-product root restoration; approve explicit MVP scope or implement the prerequisite first. Never silently choose an arbitrary OS grant. | Owner approves UX/retention and storage contract; I coordinates, F consumes. |
| D02 Backup of provider locations | Policy B allows only the entire `file:datastore/app_preferences.preferences_pb`; Room is excluded. Existing proto holds only theme/language/fit. | Separate non-backed-up location state; separately approved expanded backup/restore policy; no durable root state. | Prefer a distinct location store excluded by the existing exact allowlist if persistence is approved. Do **not** add a root/URI key to the allowlisted proto: backup is whole-file, not per key, and restored references do not restore permission. Requires persistence/security/restore tests. | Owner explicitly approves persistence and backup treatment before any field/file change; separate adapter task. |
| D03 Logical registration/observation owner | Registry operations exist; no production registration/reconciliation coordinator. | F-owned coordinator; I-owned shared host coordination; registration-on-open versus explicit command. | One I-owned coordinator issues independent IDs for explicit user intent, confirms lookup/conflicts and records evidence with expected-location comparison. F/G request/report rather than invent competing identities. Relocation requires confirmed identity; no automatic URI/name/hash equivalence. | Owner approves coordinator scope, command semantics and reconciliation/error tests before F/G handoff. |
| D04 Registered versus transient navigation | Both reference models/read paths exist; no navigation input type or routes. | Separate explicit input variants; registration required for all opens; transient reads without metadata. | Distinguish registered and transient requests; preserve transient read ability without fake IDs. Only registered inputs persist reading state. Exact entry signatures/shared type location need approval; no provider string interpreted as DocumentId. | Owner/I freeze handoff with F/G; any core/API addition is a separate approved change. |
| D05 Nested folders | SAF lists child storage refs; entry.folder is parent. Codec accepts tree-scoped child document URIs as FolderRef; no public directory-conversion operation. | Host adapter retypes validated directory location under existing SAF semantics; separate general contract operation; root-only temporary MVP. | Host-mediated conversion after confirming directory MIME/root provenance, using existing opaque values without F URI parsing; maintain breadcrumb/back-to-root boundary and cancel stale listing. Do not invent a parent lookup from URI path segments. Generalization or missing guarantees require approved contract work. | Owner approves concrete conversion and nested-folder acceptance before F navigation implementation; I supplies it. |
| D06 Reader restoration | Metadata position exists; no route saved-state or production selected-root store. Direct-session revision remains Unknown; no restorable native objects. | Reopen from logical ID and revalidated bounds; transient process-death reselection; retaining only small state. | Retain G owner across configuration change, save only approved small IDs/page/viewport hints; after process death resolve/reopen and revalidate access/bounds. Unknown hints clamp/reset only under approved rule; transient access may require reselection. Reusing obsolete sessions/bitmaps is forbidden. | Owner approves page reconciliation, transient/private-location state and lifecycle scenarios; G/I. |
| D07 Cache/concurrency budgets | Per-copy/per-bitmap ceilings only; native session serializes page work. | Single scheduled render; bounded adjacent prefetch/cache; multiple sessions with explicit budget. | Start with one active session/one scheduled render and bounded retained leases; approve exact byte/count/eviction/prefetch limits and measure physical-device peak memory/latency before performance acceptance. Per-bitmap limits cannot justify aggregate memory claims. | Owner approves measured numerical budgets and performance scenarios; G plus I lifecycle tests. |
| D08 Position update timing | Atomic `setReadingPosition`, exact-revision comparison, Unknown hints and null clear exist. | Every visible page; debounced/coalesced updates; exit-only checkpoint. | Persist only successfully visible pages with serialized/coalesced writes and approved checkpoint/loss window; exit-only is insufficient across process death. Keep favorite writes independent; observe after cancelled updates. Exact timing remains undecided. | Owner approves durability/loss window, expected-revision handling and cancellation tests; G/I. |
| D09 API26 language application | Typed setting persisted; host follows system resources; no manual locale application mechanism installed. | Validated compatibility mechanism, possibly AppCompat after evaluation; approved context/resource integration. | I validates smallest API26/27 solution across Activity/Compose/restart/system reset. H only persists typed choice. No Android13-only fallback or unapproved dependency/activity migration. | Owner approves technical validation and any dependency/manifest/activity changes before manual-language acceptance; H/I coordination. |
| D10 Shared localization ownership | Base en/ca/es strings exist; resources have module-global names. | Feature files/prefixes plus I shared queue; one serialized shared strings editor. | Separate feature-prefixed files in every language; I owns shared copy and existing shared files. Agree resource inventory before work, validate all bundles and accessibility labels. | Owner approves exact resource-file allocations with F/G/H/I scopes; no missing translation exemption. |
| D11 Cross-feature handoffs/dependencies | No installed feature routes; lifecycle runtime/activity Compose present; navigation/AppCompat and other additions are not preapproved. | Existing dependencies with explicit host state; separately justified navigation/lifecycle additions; shared prerequisite PR. | Freeze minimal entry inputs/events/lifetimes first. One I-owned dependency queue; approve and merge necessary shared contracts/configuration before affected features. No feature-to-feature concrete imports or independent versions. | Owner approves signatures, dependency/license/API26 assessment and integration sequence; I coordinates, feature owners consume. |

## 9. Parallel implementation protocol

1. Owner first approves feature-specific scopes, unresolved prerequisite decisions and handoff
   signatures, plus the common source/contract baseline from then-current `main`. This planning
   branch is not itself permission to start feature branches. Necessary shared prerequisites must
   be reviewed and owner-merged before dependent parallel work; do not branch features from an
   unreviewed private contract fork.
2. F/G/H may then work independently on their exact exclusive packages, mirrored tests and agreed
   prefixed resource files. No feature edits MainActivity, manifest, graph lifetime, core contracts,
   Gradle dependencies, existing shared resources or the other feature's implementation.
3. Supply immutable feature state/events and narrow composition entry points using approved
   existing contracts. Keep feature test doubles local; I integrates shared fakes only if an actual
   multi-consumer need is approved. Do not implement duplicate SAF reads, renderer, persistence,
   locale application, error vocabulary or competing registration ownership.
4. Propose a shared change with consumer need, error/lifetime/serialization impact and migration
   plan; pause only dependent work until owner approval. No feature-local copy of a missing shared
   interface, silent dependency upgrade or excluded coverage source is an acceptable workaround.
5. Each feature handoff includes exact SHA, approved inputs/events, resource ownership/lifetime,
   localized loading/error/retry states, tests/fakes and retained reports, unresolved decisions and
   operational limitations. Independently green tests do not prove I's composed flow.
6. Owner integrates reviewed PRs one at a time. Other branches synchronize safely with accepted
   current `main`, preserve work, resolve conflicts by ownership and rerun latest-head checks.
   I alone wires navigation/Activity Results/host effects and runs cross-feature regression. No
   automatic merge, policy waiver, destructive acceptance or new agents are authorized here.

## 10. Acceptance and sequencing

| Stage | Prerequisite / deliverable | Evidence and acceptance boundary |
| --- | --- | --- |
| P03 evidence / P04 planning | Attributed native coverage evidence and this interface/decision register delivered | Owner reviews evidence/proposals. No code authorization follows from percentages or this document. |
| P05 | Separately authorized consolidation of deferred acceptance | Not started here. Preserve existing physical/C06/provider requirements; no waiver. |
| Shared prerequisites | Owner approves relevant D01–D11 decisions, entry points and any missing shared capability | Separate scoped contract/configuration work when needed, including backward compatibility, security/backup implications and tests; owner merges before dependent implementation. |
| Parallel F / G / H | Independent explicit task approvals and agreed baseline/ownership | F: folder/partial/error/identity/favorite flows. G: resource transfer, close/cancel/stale results, bounds/resume/render lifecycle. H: typed choices, defaults versus failed reads, atomic update/reconciliation. Independent JVM/Compose/API26 tests and scope-specific coverage, no fake Android grant acceptance. |
| I composition after reviewed feature handoffs | Reuse accepted F/G/H entry points; own shared host wiring | Genuine picker-to-library-to-reader flows, singleton/lifetime verification, settings effects, locale/theme recreation and cross-feature recovery tests on current combined revision. Feature PRs may be integrated sequentially after review; the owner selects order and acceptance boundaries. |
| Formal functional acceptance | All applicable criteria and human approval recorded | Latest PR/current-base mandatory CI; phase-close API27/recent-API evidence; physical Android8.1, applicable external-provider/security/performance/manual acceptance. Backend coverage and emulator PASS cannot substitute. |

Every eventual PR retains build, Lint, JVM, real API26 instrumentation, localization, canonical PDF
fixtures and quality-gate checks on the latest revision. New testable business logic must meet
existing >=90% lines / >=80% branches over its approved affected scope; no per-file gate is added
by this plan. Keep immutable canonical fixture hashes/test-only packaging. Future save/edit work
is out of scope and retains its complete critical data-survival obligations.

UI testing must cover English/Catalan/Spanish, light/dark, relevant portrait/landscape, font scaling,
accessible semantics/touch targets and applicable TalkBack/device observations. Use authentic SAF
permission acquisition in integration acceptance, never fabricated grants. Feature doubles isolate
logic; actual provider/PdfRenderer/SQLite/DataStore tests establish platform behavior.

**Readiness is not formal acceptance:** a feature may be ready for I review with documented APIs,
passing scoped checks and explicit open integration items; it is not an accepted functional phase
until all applicable policy/manual criteria and owner approval are recorded. Physical Android 8.1,
real C06 backup/restore/D2D, additional external-provider scenarios and measured reader performance
remain pending as recorded in [current status](../PROJECT_STATUS.md), the
[Phase E audit](../integration/E_APP_COMPOSITION.md) and
[Phase C deferred register](../PHASE_C_DEFERRED_ACCEPTANCE.md). This document closes none of them.
