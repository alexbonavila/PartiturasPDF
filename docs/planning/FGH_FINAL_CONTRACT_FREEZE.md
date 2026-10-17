# F/G/H/I shared foundation contract freeze

Recorded 2026-10-17. Independent implementation branch: `feat/fgh-shared-foundation`, from
merged main `121090a56ff6d2fdb98655eaf7104ee67d79caa4` (PR #15). The separate
`chore/spanish-first-localization-policy` branch is **not merged into this branch**.
The owner-approved master task supersedes the historical P04 recommendations. D01–D11 are
**OWNER APPROVED IN PRINCIPLE**; implemented behavior below is the concrete shared baseline.
No feature screen, production Activity host/navigation or PR is implemented by this task.

Paths below are relative to `app/src/main/java/com/abdev/partituraspdf/`. The following code
blocks reproduce the actual compiled shared source, not proposed stubs. Implementations and
consumer responsibilities follow. Existing core document/result/preferences, SAF read and PDF
session APIs remain compatible. No Room schema, backup XML or dependency changes.

## Exact shared Kotlin surface

### `core/foundation/FoundationContracts.kt`

```kotlin
package com.abdev.partituraspdf.core.foundation

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.Outcome

/** Android authorization stays in I; this only queries already persisted READ capability. */
fun interface PersistedRootAccess { fun hasRead(root: FolderRef): Outcome<Boolean> }

/** Only authorized selections are durable. Restoration rechecks permission, never deletes metadata. */
interface SelectedRootRepository {
    suspend fun select(root: FolderRef): Outcome<Unit>
    suspend fun restore(): Outcome<FolderRef?>
    suspend fun clear(): Outcome<Unit>
}

sealed interface DocumentOpenRequest {
    data class Registered(val document: DocumentRef) : DocumentOpenRequest
    /** This capability must be reobtained after process death; never serialize it as a permanent grant. */
    data class Transient(val storage: StorageDocumentRef) : DocumentOpenRequest
}

interface RegistrationCommands {
    suspend fun register(storage: StorageDocumentRef): Outcome<RegisteredDocument>
    /** Caller has confirmed relocation; no name/hash inference occurs here. */
    suspend fun relocate(document: DocumentRef, expected: StorageDocumentRef, confirmed: StorageDocumentRef): Outcome<RegisteredDocument>
    suspend fun observe(document: DocumentRef, expected: StorageDocumentRef, revision: DocumentRevision, confirmed: DocumentAvailability): Outcome<Unit>
}

/** Route tokens are issued by the one repository. A newer route invalidates older writers. */
data class PositionWriter(val document: DocumentRef, val epoch: Long)
interface ReaderPositionRepository {
    suspend fun writer(document: DocumentRef): PositionWriter
    suspend fun restore(document: DocumentRef, pageCount: Int): Outcome<PageIndex?>
    /** Sequence increases only for successfully displayed pages, never merely requested renders. */
    suspend fun saveVisible(writer: PositionWriter, sequence: Long, page: PageIndex): Outcome<Unit>
}

/** Immutable navigation descriptions only; no native or Android objects are navigation arguments. */
data class ReaderRouteState(val request: DocumentOpenRequest, val visiblePageHint: PageIndex? = null) {
    val restorableAfterProcessDeath: Boolean get() = request is DocumentOpenRequest.Registered
}

sealed interface LibraryEvent {
    data object ChooseRoot : LibraryEvent
    data class Enter(val entry: DocumentEntry, val version: Long) : LibraryEvent
    data class Back(val version: Long) : LibraryEvent
    data class Open(val request: DocumentOpenRequest) : LibraryEvent
    data class Register(val storage: StorageDocumentRef) : LibraryEvent
}

/** H persists only these active settings; LanguageSelection remains legacy storage compatibility. */
sealed interface SettingsEffect {
    data class Theme(val selection: ThemeSelection) : SettingsEffect
    data class Fit(val selection: PageFit) : SettingsEffect
}
interface SettingsCommands { suspend fun apply(effect: SettingsEffect): Outcome<Unit> }

/** I applies locale/resources and theme; no Activity, picker result or AppGraph leaks into features. */
interface FeatureHost {
    suspend fun selectedRoot(): Outcome<FolderRef?>
    suspend fun acceptPersistedRoot(root: FolderRef): Outcome<Unit>
    suspend fun library(event: LibraryEvent): Outcome<Unit>
}
```

### `core/foundation/ReaderPolicies.kt`

```kotlin
package com.abdev.partituraspdf.core.foundation

import com.abdev.partituraspdf.core.document.PageIndex
import java.util.Locale

/** Enabled translations are a delivery decision, not the legacy persisted language selection. */
data class DeliveredLanguages(val english: Boolean = false, val catalan: Boolean = false)
object DeviceLocalePolicy {
    fun resolve(deviceLanguages: List<String>, delivered: DeliveredLanguages = DeliveredLanguages()): String {
        val language = deviceLanguages.firstOrNull()?.substringBefore('-')?.substringBefore('_')?.lowercase(Locale.ROOT)
        return when {
            language == "en" && delivered.english -> "en"
            language == "ca" && delivered.catalan -> "ca"
            else -> "es"
        }
    }
}

fun restoredPage(saved: PageIndex?, pageCount: Int): PageIndex {
    require(pageCount > 0)
    return PageIndex((saved?.value ?: 0).coerceAtMost(pageCount - 1))
}

data class HeapAvailability(val maximumBytes: Long, val usedBytes: Long) {
    init { require(maximumBytes > 0 && usedBytes in 0..maximumBytes) }
}
data class ReaderResourceBudget(val bitmapBytes: Long) {
    init { require(bitmapBytes >= 0) }
    val activeSessions: Int get() = 1
    val scheduledRenders: Int get() = 1
    val retainedPageLeases: Int get() = 1
    val prefetchPages: Int get() = 0
    val maxArgbPixels: Long get() = bitmapBytes / 4
}
enum class ReaderTrim { LOW_MEMORY, BACKGROUND, CRITICAL }
data class ReaderTrimResponse(val cancelPending: Boolean, val releasePage: Boolean, val closeSession: Boolean)
object ReaderMemoryPolicy {
    /** Reserve most heap for native/Compose/Room: <=1/16 max and <=1/4 currently free, capped 16MiB. */
    fun budget(heap: HeapAvailability): ReaderResourceBudget = ReaderResourceBudget(
        minOf(16L * 1024 * 1024, heap.maximumBytes / 16, (heap.maximumBytes - heap.usedBytes) / 4),
    )
    fun trim(level: ReaderTrim): ReaderTrimResponse = ReaderTrimResponse(true, true, level != ReaderTrim.LOW_MEMORY)
}
```

### `storage/api/FolderNavigation.kt`

```kotlin
package com.abdev.partituraspdf.storage.api

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.Outcome
import java.util.Collections

class FolderNavigationState(root: FolderRef, breadcrumbs: List<FolderRef>, val version: Long) {
    val root = root
    val breadcrumbs: List<FolderRef> = Collections.unmodifiableList(breadcrumbs.toList())
    val current: FolderRef get() = breadcrumbs.last()
    init { require(breadcrumbs.isNotEmpty() && breadcrumbs.first() == root && version >= 0) }
}
interface FolderNavigation {
    suspend fun select(root: FolderRef): Outcome<FolderNavigationState>
    suspend fun enter(entry: DocumentEntry, expectedVersion: Long): Outcome<FolderNavigationState>
    suspend fun back(expectedVersion: Long): Outcome<FolderNavigationState>
    suspend fun state(): FolderNavigationState?
}
```

### `pdf/api/ReaderRoute.kt`

```kotlin
package com.abdev.partituraspdf.pdf.api

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.core.result.Outcome

/** Session is borrowed from the route owner. G separately owns every published bitmap lease. */
data class ReaderReady(val request: DocumentOpenRequest, val session: PdfReadSession, val page: PageIndex)
interface ReaderRoute {
    suspend fun current(): ReaderReady?
    suspend fun open(request: DocumentOpenRequest): Outcome<ReaderReady>
    /** Call only after successful display; requests and completed-but-discarded renders do not count. */
    suspend fun displayed(session: ReadSessionId, page: PageIndex): Outcome<Unit>
    suspend fun background(): Outcome<Unit>
    suspend fun close(): Outcome<Unit>
}
```

### `pdf/api/ReaderBudget.kt`

```kotlin
package com.abdev.partituraspdf.pdf.api

import com.abdev.partituraspdf.core.foundation.ReaderResourceBudget
import com.abdev.partituraspdf.core.result.*

/** G consumes both the existing native ceiling and current heap budget; zero headroom means no allocation. */
fun boundedRenderLimits(budget: ReaderResourceBudget, ceiling: RenderLimits): Outcome<RenderLimits> =
    if (budget.maxArgbPixels < 1) Outcome.Failure(AppError.Resource.RENDER_LIMIT)
    else Outcome.Success(RenderLimits(ceiling.maxDimension, minOf(ceiling.maxPixels, budget.maxArgbPixels)))
```

## Implementations, ownership and errors

| Implemented path / exact entry | Responsibility / consumers |
| --- | --- |
| `data/foundation/PrivateSelectedRootRepository.kt`: `PrivateSelectedRootRepository(context: Context, grants: PersistedRootAccess)`; test constructor `(file: File, grants: PersistedRootAccess)` | Implements SelectedRootRepository for I/F. AtomicFile `files/foundation/selected-root.bin`, version1 integer + UTF provider/value. No DataStore key or Room schema. Serialized IO, atomic replace/recovery; missing file -> Success(null), invalid representation -> INVALID_DOCUMENT, inaccessible storage -> PRIVATE_STORAGE_UNAVAILABLE. Selection checks persisted READ before writing; restore rechecks and yields PERMISSION_REVOKED without deleting stored root/metadata. Clear removes only selection, never grants. |
| `storage/saf/SafPersistedRootAccess.kt`: `SafPersistedRootAccess(resolver: ContentResolver)` | Implements PersistedRootAccess using existing SafTreeGrantAccess; only validated content tree roots accepted. Actual OS persisted READ query, no arbitrary grant selection, path fallback, write permission or fabricated authorization. |
| `storage/saf/SafRootSelection.kt`: `SafRootSelection(grants: SafTreeGrantAccess, roots: SelectedRootRepository)`; `suspend fun acceptResult(uri: Uri?, returnedFlags: Int): Outcome<FolderRef>` | I passes actual Activity Result values. Existing grant helper persists READ only, verifies it, then root repository persists selection. Storage failure does not release pre-existing/new grants automatically. A result grants capability, not document identity or deletion proof. |
| `storage/saf/SafFolderNavigation.kt`: `SafFolderNavigation(catalog: DocumentCatalog, grants: PersistedRootAccess)` | Implements FolderNavigation. Root/child/grandchild/back confined to explicit selected tree; maximum64 breadcrumb entries including root. Entry parent/MIME checked, fresh actual catalog membership confirmed, authority/tree IDs compared through SafUriCodec/DocumentsContract. No URI-prefix/filesystem ancestry assumptions. Cycles, ambiguous/malformed/foreign entries and stale versions fail; no failed transition mutates trail. Provider errors propagate, missing known grant -> revoked, cancellation propagates. State copies/unmodifiable breadcrumbs protect ownership. |
| `app/coordination/RegistrationCoordinator.kt`: `RegistrationCoordinator(registry: DocumentRegistry, issueId: () -> DocumentId = { DocumentId(UUID.randomUUID().toString()) })` | Implements RegistrationCommands for F/G through I. Explicit registration only: lookup existing association; independently issue ID only for missing association; register atomically through Room contract; reconcile concurrent location conflict by lookup, preserve stored ID. No automatic listing registration, filename/hash-based move inference, metadata deletion or provider implementation. relocate/observe require caller-confirmed evidence and expected-location guard. |
| `data/foundation/SharedPreferencesReaderPositions.kt`: `SharedPreferencesReaderPositions(context: Context)` or `(preferences: SharedPreferences)` | Sole active new reader position store, `shared_prefs/reader_positions.xml`. Stable DocumentId keys and Int pages; no locations/credentials. Off-main commit; commit false -> PRIVATE_STORAGE_UNAVAILABLE. Corrupt/non-Int/negative values ignored without deleting them/unrelated keys. Restore clamps to actual positive page count. Shared in-process state per Android prefs owner issues route epochs; old route/sequence writes -> STALE_SESSION, even across repository recreation. Failed commit reserves ordering because in-memory state may already change; a fresh-sequence checkpoint/observation reconciles uncertainty. No multiprocess access guarantee. |
| `app/coordination/ReadingCheckpoint.kt`: `ReadingCheckpoint(scope: CoroutineScope, repository: ReaderPositionRepository, writer: PositionWriter, pageCount: Int, delayMillis: Long = 1000)`; `visible(page: PageIndex)`, `flush()`, `close()` all suspend -> Outcome<Unit>; `lastWrite: StateFlow<Outcome<Unit>?>` | I/G route-local owner. Own child SupervisorJob, never cancels supplied parent. Valid visible-page acknowledgement only; coalesces rapid changes with default1000ms delay, configurable0..1000ms. Flush/close cancel/join pending job and save latest with a fresh sequence off main via adapter. Exposes actual durable result, including failure. Process death before checkpoint may lose at most the pending work; scheduling under system pressure is not a real-time guarantee. Caller flushes background/navigation. No global jobs. |
| `app/coordination/ReaderRouteOwner.kt`: `ReaderRouteOwner(scope: CoroutineScope, registry: DocumentRegistry, access: DocumentReadAccess, engine: PdfReadEngine, positions: ReaderPositionRepository, readLimits: ReadAccessLimits)` | Implements ReaderRoute. I retains across configuration changes; process death creates new owner. Registered opens resolve current location and restore/clamp from SharedPreferences only. Transient opens use no DocumentId/writer or durable position. One active session; serialized replacement flushes/closes previous resources. Open generation rejects/closes superseded output; cancellation/unused input cleanup preserved. G calls displayed only after actual display, background flushes, close quiesces and closes native work off main. Session borrowed from owner, published bitmap leases remain exclusively G-owned and independent. |
| `app/coordination/RegistrationCoordinator.kt`: `RepositorySettingsCommands(repository: PreferencesRepository)` | Implements SettingsCommands; only Theme/Fit map to existing atomic PreferenceChange variants. No manual Language action. H still observes the injected existing PreferencesRepository and ignores legacy language/position fields for active behavior. I applies theme/locale and passes fit to G. |
| `app/coordination/FoundationHost.kt`: `FoundationHost(roots: SelectedRootRepository, folders: FolderNavigation, registration: RegistrationCommands, chooseRoot: suspend () -> Outcome<Unit>, open: suspend (DocumentOpenRequest) -> Outcome<Unit>)` | Implements FeatureHost without Activity/global navigation. Callbacks are required real integration functions, no default no-op. Restore initializes the same injected folder navigator; acceptPersistedRoot rechecks authorization through root adapter. Library events dispatch to callbacks/navigation/explicit coordinator. Typed registration completion remains available via RegistrationCommands; LibraryEvent.Register only acknowledges its command through Outcome<Unit>. |

Errors use existing Outcome/AppError. Cancellation and programming defects are not translated to
benign access failures. Storage/provider permissions and observed availability remain distinct;
a failed read never confirms deletion. No native object is a navigation argument. Transient
ReaderRouteState is in-memory only: `restorableAfterProcessDeath=false`; I must reacquire access,
not serialize a temporary grant as durable authorization. Registered route restoration stores
only approved logical IDs/small page hints, then reopens and revalidates access/page count.

## AppGraph and I handoff

`app/di/AppGraph.kt` adds `foundation: FoundationDependencies? = null` solely to preserve existing
explicitly injected test graph construction. The real Application constructor always builds it
inside the existing production-used cleanup helper. No production path returns an unimplemented
foundation. Legacy isolated test graphs can omit platform adapters; they are not feature wiring.

`app/di/FoundationDependencies.kt` owns the shared adapter instances:

- `val roots: SelectedRootRepository`
- `val positions: ReaderPositionRepository`
- `val registration: RegistrationCommands`
- `val settings: SettingsCommands`
- `val rootPicker: SafRootSelection`
- `fun folders(): FolderNavigation` creates an independent route-owned breadcrumb controller.
- `fun reader(scope: CoroutineScope): ReaderRoute` creates a route owner, using the same registry,
  read access/engine/position repository and immutable read limits.
- `fun host(folders: FolderNavigation, chooseRoot: suspend () -> Outcome<Unit>, open: suspend (DocumentOpenRequest) -> Outcome<Unit>): FeatureHost`
  uses the **same** navigator supplied to F/I, not a second competing trail.

I obtains the production foundation once, injects narrow contracts into features, supplies the
actual Activity Result callback and real navigation handlers, and retains route scopes across
configuration changes. No global service locator or Application startup is installed. The factory
still owns one shared Room lifetime and closes it on partial construction failure; DataStore is
borrowed from the existing application-context singleton and is not closed by the factory.
New file/prefs adapters create no permanent coroutine scope; checkpoint jobs belong to routes.

## Position compatibility and backup

The new ReaderRoute implementation has no DocumentMetadataRepository parameter and never reads
or writes its Room position API. New positions have **one authority**, SharedPreferences. F uses
Room for registration/favorites; legacy Room APIs/columns/schema v1 and all C tests remain intact.
There is **no implicit migration or fallback** from legacy Room position: a missing new position
starts at zero even if Room has an old position. No unverifiable migration is claimed. Future
explicit migration would require a separately approved source/provenance design, not dual writes.
The Android compatibility test preserves a legacy page7/favorite while new position becomes page2.

Selected root is `file:foundation/selected-root.bin`; positions are in the `sharedpref` domain,
`reader_positions.xml`. Neither is in Policy B's only eligible path
`file:datastore/app_preferences.preferences_pb`. Static policy regression and compiled-XML Android
test assert legacy/cloud/D2D includes remain exact. No backup XML or schema changes. Entire settings
proto remains non-sensitive theme/legacy language/page-fit; no provider values or reader positions
are added. Real restore/D2D acceptance remains separate, deferred and never executed here.

## Provisional 2GB-device-oriented reader budgets

Use actual `Runtime.maxMemory()` and used heap (`totalMemory - freeMemory`) as HeapAvailability
inputs; total physical RAM is not a heap budget. I supplies a fresh snapshot before allocation.
Shared policy caps one ARGB bitmap at min(16MiB, maxHeap/16, currentlyFreeHeap/4), then
`boundedRenderLimits` intersects pixel count with the existing native render ceiling. Zero room
returns RENDER_LIMIT, not a zero-sized allocation. A64MiB heap with32MiB used allows4MiB, not the
16MiB ceiling typical of a larger heap. 2GB RAM devices are the primary target, up to3GB expected.

One route session, one scheduled native render, at most one retained page lease, no prefetch.
G must detach display consumers and release the previous retained lease before replacing it;
rejected/late output is closed. No full cache is implemented here. LOW_MEMORY cancels pending work
and releases page; BACKGROUND/CRITICAL also request session closure. I/G apply trim response and
reopen as needed. Existing64MiB per temporary copy,4096-axis/4,194,304-pixel native ceilings remain.
These are provisional testable constraints, not measured tablet latency/native peak memory PASS.

## Locale and branch independence

DeviceLocalePolicy is pure: first functional stage always es; after I explicitly enables delivered
complete English/Catalan translations, primary device en/ca selects them, es/unsupported selects es.
It ignores legacy DataStore LanguageSelection; no manual selector or AppCompat is introduced.
I alone applies resources/configuration/system-language changes on API26+. Existing translations
remain in this branch and current multilingual validation runs unchanged. The separate policy PR
must be independently reviewed/merged before Spanish-only feature copy relies on its staging rule.
I/localization owns the later centralized Spanish-default migration and complete English/Catalan
translation phase. No resource migration or false translation completeness claim is made here.

## Ownership and dependency freeze

| Owner | Files | Injected inputs / outputs |
| --- | --- | --- |
| F | `library/**`, mirrored tests, owner-agreed `library_` resource files | SelectedRootRepository/FeatureHost, FolderNavigation, RegistrationCommands, existing catalog/registry/metadata; LibraryEvent and typed DocumentOpenRequest. No URI parsing or position writes. |
| G | `reader/**`, mirrored tests, `reader_` resource files | ReaderRoute, ReaderReady, ReaderResourceBudget/boundedRenderLimits, existing PDF/bitmap contracts and page-fit value; displayed/background/close acknowledgements. No concrete adapters, legacy Room position access, fake IDs or full-copy duplication. |
| H | `settings/**`, mirrored tests, `settings_` resource files | PreferencesRepository observation, SettingsCommands/SettingsEffect.Theme/Fit. No language selector, DataStore construction or locale application. |
| I | MainActivity/startup, navigation, Activity Results, app/coordination and approved shared resource wiring | AppGraphFactory/FoundationDependencies, actual picker results, narrow feature inputs/events, locale/theme effects and lifecycle/trim. Owns shared dependency/resource queue under approval. |

Dependency direction: F/H -> core contracts; G -> core + storage.api/pdf.api; I -> feature public
entry points + app composition; data -> core; SAF -> core/storage.api; PDF -> core/storage.api/pdf.api.
No feature imports another's concrete implementation or obtains AppGraph. No feature independently
edits these frozen contracts, adapters, MainActivity, schema, Gradle or shared strings. New shared
needs require a separately approved task. Existing feature-branch names remain F/G/H/I's approved
ownership plan; no feature implementation branch is created by this assignment.

## Decision closure and verification

| Decision | Implemented prerequisite / tests | Left exclusively to later features/I |
| --- | --- | --- |
| D01 | PrivateSelectedRootRepository, SafRootSelection; Android fresh/replacement/recreation/revocation/error/recovery tests | I forwards real picker results; F root display. |
| D02 | Separate private paths; Python backup exclusion and Android compiled-rule test | Genuine runtime backup/restore/D2D acceptance. |
| D03 | RegistrationCoordinator; JVM conflict/error/cancellation and Android real Room concurrent registration | I host command delivery/confirmed evidence; F explicit action UI. |
| D04 | DocumentOpenRequest, ReaderRouteState; contract/route registered/transient tests | I destination encoding and transient capability reacquisition. |
| D05 | FolderNavigation/SafFolderNavigation; Android child/grandchild/back/hostile/provider/stale/cancel tests | F breadcrumbs UI; I supplies authentic selected root. |
| D06 | ReaderRoute/ReaderRouteOwner; bounds/reopen/supersession/ownership JVM tests | I retains route owner/configuration lifecycle and reopens after process death; G display. |
| D07 | ReaderMemoryPolicy/ReaderResourceBudget/boundedRenderLimits; JVM heap/trim/bounds tests | G scheduling/display lease enforcement; I trim delivery; real hardware measurement. |
| D08 | ReaderPositionRepository, SharedPreferences adapter, ReadingCheckpoint; JVM coalesce/durable-failure tests, Android reload/corruption/concurrency/legacy compatibility | G acknowledges actual visible page; I background/exit events. |
| D09 | DeviceLocalePolicy; JVM language/delivery/fallback tests | I API26 resource application, no manual UI. |
| D10 | Separate Branch A governance/validator and focused tests; no policy copy in B | Independent owner merge of A, I/localization shared resource migration and later translations. |
| D11 | Frozen compileable events/commands/route/settings/host interfaces and real implementations; JVM consumer tests | Actual feature entry composables and final I navigation/Activity host, which are intentionally out of scope. |

All rows: OWNER APPROVED IN PRINCIPLE; shared code delivered for review, not unconditional feature
or phase acceptance. Actual executed counts and available coverage are recorded in delivery.
Android APK compilation is not execution; this cloud lacks KVM/connected devices. Native Windows
verification must run `python scripts/local_e2e.py doctor` then
`python scripts/local_e2e.py run --suite coverage` on the existing owned accelerated API26 AVD.
Retain actual JUnit inventory, original classes/provenance and full new source counters; no new
coverage percentage is predicted. Existing A1/B5/C05 gates, API27/recent and physical/manual/C06
requirements remain unchanged. Never run destructive backup acceptance as part of this suite.

Unsupported: file mutation/editing/annotations, global registry enumeration, multiprocess preference
writers, inferred moves/deletion, durable transient grants, snapshots from Unknown revisions,
unbounded caches, automatic feature startup and actual locale application. These are not TODO
implementations hidden behind the frozen interfaces; they are explicitly separate capability scopes.


## Executed delivery validation — 2026-10-17 (Europe/Madrid)

Branch B was validated independently on merged main `121090a56ff6d2fdb98655eaf7104ee67d79caa4`.

- `python -m unittest discover -s scripts/tests -v`: 248 tests PASS (two new backup-exclusion regressions).
- `python scripts/validate_localization.py`: PASS under the unchanged main localization policy.
- `python test-fixtures/scripts/verify_fixtures.py`: all 15 canonical PDFs PASS, including expected corruption.
- `bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --rerun-tasks --no-build-cache --max-workers=4 --console=plain`: PASS.
- Actual JVM JUnit XML: 192 tests, zero failures/errors/skips (24 new foundation tests).
- Lint: zero errors; 21 warnings, no suppression or policy changes.
- Existing A1 `verifyContractCoverage` finalizer PASS: 230 covered/231 lines, 222 covered/224 branches.
  These counters describe that existing contract gate, **not** combined coverage of the new foundation.
- Android source inventory: 242 ordinary tests (229 existing + 13 new). `javap` confirms all 13 new
  JUnit methods return void. APK compilation PASS; instrumentation execution NOT RUN.
- Local documentation links, exact embedded public-source signatures, `git diff --check`, and
  unchanged schema/backup/build/CI/normative-policy/fixture checks: PASS.
- Real Android/SAF, API26/27/recent execution and combined new-source coverage: NOT RUN/BLOCKED
  here (no connected device or KVM). Native Windows owner verification remains required using
  the commands above. Physical-device performance, genuine backup/restore and D2D acceptance
  remain pending; compiled tests and synthetic grant doubles do not establish these results.

Independent Branch A has 254 Python tests and 168 JVM tests PASS, with localization, all 15 PDFs,
debug build and Lint PASS. Its separate policy changes are not included in Branch B.
