# E02 runtime limits and ownership decisions

> **Current-state addendum (2026-10-16):** This file is a chronological E-phase implementation and acceptance audit; original `NOT RUN` statements refer to the environment/commit of that delivery, not every subsequent revision. Follow-up L01–L04 real API26 regression, SAF and combined coverage and hosted API26/27/36 results are documented at [current project status](../PROJECT_STATUS.md) and [L04D](../LOCAL_COMPATIBILITY_VALIDATION.md). The original E acceptance limits (notably manual external SAF grants and physical tablet) must not be silently reclassified as passed. The user-facing library/reader navigation remains unimplemented.

Scope: E02 only, based on E01 b017c44a94e25384b419ab8c3006cb181f147991 on
feat/app-composition-e. No MainActivity wiring, reader feature, ViewModel, global graph provider,
new adapter or dependency is introduced. E03-E06 require separate owner authorization.

## Immutable configuration

AppRuntimeLimits contains existing validated ReadAccessLimits and RenderLimits values. It adds
no duplicate positivity checks, platform context, filesystem access, mutable state or background
scope. Both nested contracts contain immutable primitive properties. copy() produces another
configuration; it cannot mutate an existing graph. Provisional values are obtained by an explicit
AppRuntimeLimits.provisional() call, not an implicit factory default or global configuration.

AppGraphFactory now takes Application and AppRuntimeLimits instead of the E01 RenderLimits
argument. There is no unused compatibility overload. The isolated test construction callback
receives the exact same configuration object. The production path places that object on AppGraph
and passes its render member directly to AndroidPdfReadEngine. Future consumers receive
DocumentReadAccess and graph.runtimeLimits.read through their constructors, then pass that
ReadAccessLimits to the existing openSeekable(document, limits) operation. No SAF API or behavior
is wrapped, replaced or changed. There is no reader consumer to wire during E02.

One application-level owner must be retained across Activity recreation/recomposition. Repeated
get() calls reuse its graph, configuration, shared Room database and both Room repositories.
Application preferences still come from the one existing AndroidX delegate/file. No mutable
runtime setter, configuration watcher or graph rebuilding mechanism exists. Separate explicit
test owners can select separate configurations without sharing graph resources.

## Provisional initial ceilings

These are conservative starting safety ceilings, **not measured performance guarantees** on an
API26 emulator or a constrained Android8.1 tablet. Values occur once in production configuration.

| Input | Exact value / unit | Reason and consequence |
| --- | --- | --- |
| read.maxTemporaryBytes | 67,108,864 bytes = 64 MiB | Bounds a single complete non-seekable SAF materialization, allowing moderate inputs without unbounded cache writes; excessively large streams fail with existing INPUT_LIMIT |
| render.maxDimension | 4096 pixels per axis | Prevents pathological single-axis allocations even with low height/width; permits portrait/landscape and wide aspect requests, subject also to the area ceiling |
| render.maxPixels | 4,194,304 pixels per bitmap | Approximately 16,777,216 bytes = 16 MiB of ARGB_8888 pixel payload per full-page output; permits 2048x2048 or 4096x1024, rejects 4096x4096 |

For example, a 1448x2048 portrait bitmap uses 2,965,504 pixels (~11.31 MiB payload) and is within
these ceilings. This is arithmetic, not a measured render or a prescribed display resolution.
Future callers can request smaller images; E02 does not choose fit/zoom/render dimensions.

The actual SAF implementation copies in 8192-byte chunks and checks cumulative bytes before
writing, including unknown-size streams; it does not allocate a 64-MiB byte array or trust
provider size hints. A direct seekable provider descriptor bypasses materialization, so this is
**not a universal PDF filesize restriction**. Partial copies are discarded by existing cleanup.
Disk-full can fail earlier with the existing typed storage/resource error. Multiple concurrent
copies can consume a multiple of this ceiling; no global disk quota/cache budget is invented.

AndroidPdfReadEngine uses Bitmap.Config.ARGB_8888 and validates RenderRequest with its limits
before allocating. The existing contract uses Long multiplication, avoiding Int area overflow.
The four-bytes/pixel estimate excludes row/layout overhead, native PdfRenderer memory, decoded
PDF resources, descriptors and other live bitmaps. N retained full-size bitmap leases can use
roughly N*16 MiB pixel payload alone. Native rendering/heap pressure can still fail below these
limits; only bitmap-allocation OutOfMemoryError is mapped by the existing adapter. This is not
a total renderer peak-memory guarantee or a future reader-cache/concurrency budget.

## Evidence supporting selection and limits of that evidence

The immutable canonical dataset has 15 fixtures. Its largest file is
test-fixtures/pdf/basic/05-hundred-pages.pdf at 464,958 bytes (~0.443 MiB); all fixtures together
are 1,495,415 bytes. It covers vector, image/scanned, multiple page sizes/rotations, annotation,
protected and malformed cases. Manifest SHA256/structural/render checks pass unchanged.
Every fixture is below the proposed copy cap, but the small synthetic corpus does not represent
large scanned scores or establish copy throughput, latency, native memory, or tablet suitability.
The 64-MiB ceiling deliberately leaves room above that corpus while restricting per-copy storage;
it is a provisional policy choice, not an experimentally established optimum.

Required later measurements: real non-seekable input near/over 64 MiB and disk-full/cancellation;
source-byte integrity; render allocation boundaries; native/Java peak memory with representative
image-heavy PDFs; repeated open/render/close and multiple outstanding leases; portrait/landscape
legibility/latency on API26 and the physical Android8.1 tablet. Decide any total cache/concurrency
budget with actual future consumers and owner approval. No E03 native integration is claimed here.

## Lifecycle retained from E01

- The application host owns the factory; Activity recreation must neither close nor recreate it.
- Explicit off-main close(), after repository work is quiescent, releases only factory-owned Room.
  It is idempotent, rejects future get(), and does not depend on Application.onTerminate().
- Production DataStore and its process-lifetime scope remain AndroidX-owned and are never closed
  by the factory. Each operation owns its SAF ReadHandle; callers own PdfReadSession and rendered
  bitmap/page leases. The graph stores no reader sessions/bitmaps and has no close operation.
- Existing typed failures and cancellation behavior are unchanged. Application-only production
  construction does not accept an Activity Context. No locale/theme/UI initialization is added.

## Actual E02 validation

Host toolchain: JDK25.0.4.1, wrapper9.8.0, AGP9.4.1, SDK37, JaCoCo0.8.14, minSdk26 unchanged.

| Check | Result |
| --- | --- |
| bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --max-workers=4 --console=plain | PASS app/test APK compilation, lint and JVM; existing contract gate retained |
| JVM XML verifier with original smoke test | PASS 146 cases, zero failures/skips: 138 previous + 8 E02 |
| AppRuntimeLimitsTest | PASS 6 tests: typed construction, provisional units, invalid bounds, immutable copies, positive extremes and configured render boundaries |
| AppGraphFactoryTest | PASS 12 tests: 10 preserved E01 regressions + configuration identity/propagation and independent-owner selections/cleanup |
| python -m unittest discover -s scripts/tests -v | PASS 39 infrastructure tests |
| python scripts/validate_localization.py | PASS English/Catalan/Spanish |
| python test-fixtures/scripts/verify_fixtures.py | PASS 15 immutable fixtures |
| Source/scope, whitespace, schema and dependency integrity | PASS; only authorized app/di tests/production and this document |
| Android instrumentation, native composition and tablet performance | NOT RUN; no emulator/device execution in this task |

JVM JaCoCo diagnostic report, generated using an external uncommitted Gradle init script, includes
the entire app/di package without new exclusions. No tracked build/CI/coverage configuration changed.

| Measured scope | LINE | BRANCH |
| --- | --- | --- |
| New AppRuntimeLimits.kt | 7/7 (100%) | N/A, no reportable branches |
| AppGraph.kt | 9/9 (100%) | N/A |
| Factory get() lifecycle method | 3/3 (100%) | 6/6 (100%) |
| Factory close() lifecycle method | 6/6 (100%) | 6/6 (100%) |
| Entire AppGraphFactory.kt, including Android construction/error cleanup | 13/36 (36.11%) | 12/14 (85.71%) |

The configuration and JVM-testable lifecycle paths are covered, but full-source factory line
coverage does **not** meet 90%: the Application constructor/Android adapter creation and native
construction-failure cleanup are not executed by fake-injection JVM tests. Do not present this
as complete native factory verification or exclude those lines to manufacture a passing scope.
Those existing E01 platform paths still need authorized device integration evidence. E02 adds no
business algorithm, duplicate adapter validation or threshold exception. Existing mandatory gates
and 90% LINE / 80% BRANCH policy remain unchanged. Reports/logs are local diagnostics under
/workspace/partituras-tools/e02-*; they are not committed artifacts or hosted CI results.

No PR creation, merge, E03-E06 implementation, new device defaults based on unmeasured claims,
or changes to deferred Phase C acceptance are authorized by this E02 delivery.

## E03 — real SAF-to-PdfRenderer integration tests

E03 is separately owner-authorized on feat/app-composition-e, based on approved E02
7f4efc7dba5e4fa9e43c220d1e5e599500d86912. The earlier E01/E02 evidence above is historical
and remains intact. This delivery adds test code and documentation only. **E03's principal
acceptance criterion, real Android execution, is BLOCKED / NOT RUN in this cloud environment.**
Compilation does not establish that the integration or its cleanup assertions pass at runtime.

### Architecture and isolation

CanonicalPdfContentProvider is a Java, test-APK-only ContentProvider registered under
com.abdev.partituraspdf.test.integration.pdf. Java avoids target Kotlin runtime dependencies
during early provider initialization, as in the existing SAF fixture provider. Stable document
paths are /document/file-ID and /document/pipe-ID; a per-open UUID query token isolates completion
evidence. Only IDs 01, 02, 08, 10 and 11 map to fixed canonical assets. No arbitrary asset/file
path, storage permission, DocumentsProvider, picker, grant orchestration or write API is added.
The provider is exported so the target resolver can access the separate test package, but exposes
only these synthetic test assets. It is absent from the production APK.

The file path copies an asset to a unique provider-cache file, opens it read-only, then unlinks
the pathname before returning the descriptor. The descriptor keeps the bytes alive until its
owner closes it. The pipe path uses createReliablePipe and a separate producer thread; openFile
never fills a pipe synchronously. Asset streams and producer descriptors close in try-with-resources.
A per-request CountDownLatch completion handshake reports delivered bytes, errors and unlinking;
the test waits for each producer to finish, then removes its report. The 10-second wait is a
failure watchdog, not an arbitrary scheduling delay or performance assertion. Early reader
closure may produce a recorded broken-pipe error in limit/cancellation cases. A separate broken
delivery mode reports an intentional I/O error after an initial canonical chunk, without feeding
truncated bytes to native Pdfium.

Each ordinary test uses a UUID target-cache directory supplied through a ContextWrapper to the
real SafDocumentReadAccess constructor. The resolver is real; no hand-built ReadHandle, fake PDF,
fake native renderer or new copy algorithm substitutes for the integration under test:

```text
test APK canonical asset -> test provider -> target ContentResolver
  -> SafDocumentReadAccess.openSeekable(document, AppRuntimeLimits.provisional().read)
  -> ReadHandle -> PdfReadSource -> AndroidPdfReadEngine -> native PdfRenderer
  -> page information -> bounded RenderRequest -> real caller-owned Bitmap
```

The real factory test obtains the actual target Application and calls AppGraphFactory(application,
limits). It refuses non-emulator hardware and installations with an existing default Room database,
preferences file or SAF read-copy contents. Use a fresh disposable google_apis emulator (ranchu or
goldfish). It never clears/deletes application persistence and never invokes registry, metadata or
preferences operations. Room is constructed and closed lazily without opening/querying database
rows; this is not E04 persistence acceptance. Only a direct provider input is used for this graph,
avoiding materialization into its normal application cache. Repeated get(), selected-limit identity,
actual adapter types, renderer use, idempotent factory close and rejection of get() after close
are asserted. Factory close occurs with no active repository work and must leave the caller's
open PDF session and bitmap alive. No Application subclass, singleton or MainActivity wiring exists.

### Canonical inputs and integrity

The tests read status, path, byte count, hash and page count from the existing manifest in test
assets. They check exact resolver-delivered bytes with pread (without moving the native input
offset), SHA-256 and equality to the canonical asset. Temporary materializations additionally
check their file length/hash. These checks do not fabricate VerifiedSnapshot provenance: the
test association remains DocumentRevision.Unknown and the native session has no verified revision.

All five entries have manifest status valid; the following hashes were verified on the host and
against packaged test assets, **not yet through Android provider execution**:

| Fixture | Bytes | Pages | SHA-256 |
| --- | ---: | ---: | --- |
| pdf/basic/01-single-a4.pdf | 42,384 | 1 | 301340714954d3821e94558c524a44ab92b08b3e3db91e463b38c3985506c642 |
| pdf/basic/02-two-pages.pdf | 46,034 | 2 | 50f6447d3893b3822ed902fa370ecdfe2c116bbdaeb09afaa9e735cdc21fd512 |
| pdf/page-sizes/08-mixed-sizes.pdf | 58,505 | 5 | 1e04cdf19cefc7fa04388a1998822607b69229e4d8dff0421adedfbe70be3b61 |
| pdf/complex/10-scanned-score.pdf | 221,317 | 2 | c049743af7075819d49550342bcc4a55b4f5dc29b504d1236b22102a24700dd0 |
| pdf/complex/11-vector-score.pdf | 47,215 | 2 | 2ce44d7c54cfa6c494a4757dba695d03a11565468477b3f248a12ed0e2cc4b91 |

### Android test inventory and expected evidence

All methods belong to com.abdev.partituraspdf.app.integration.SafPdfIntegrationTest. Each uses
a block body returning JVM void; javap confirms all 12 signatures. Every row below is compiled
but **NOT RUN** on Android; no PASS, FAIL or resource-lifecycle observation is inferred from that.

| Test | Assertions awaiting device execution | Result |
| --- | --- | --- |
| directA4UsesProviderDescriptorAndRendersCanonicalPage | Seekable Provider provenance, no SAF copy, canonical bytes, 1 page, actual information/bitmap, descriptor closure | NOT RUN |
| pipeIsReallyNonSeekableThenMaterializesTwoCanonicalPages | Raw resolver pipe reports ESPIPE, exact bytes; separate openSeekable yields TemporaryReadCopy, 2 pages, renders both, deletes copy on session close | NOT RUN |
| mixedSizesExposeDifferentRealPageDimensions | Canonical 5 pages, differing observed sizes, all pages render | NOT RUN |
| scannedScoreRendersThroughAsynchronousPipe | Image-heavy 221,317-byte asset exceeds ordinary pipe buffer; bounded copy, both pages render | NOT RUN |
| vectorScoreRendersThroughResolverAndNativeEngine | Canonical vector/font asset, both pages inspect/render through direct input | NOT RUN |
| inputLimitRejectsCopyAndLeavesNoIncompleteFile | Positive test-selected limit one byte below fixture length yields INPUT_LIMIT, no partial copy, producer terminates | NOT RUN |
| failedProviderPipeIsIoFailureRatherThanDeletionOrInvalidPdf | Reliable-pipe failure yields PROVIDER_IO_FAILURE, not deletion/corruption; partial copy removed | NOT RUN |
| cancellationDuringRealCopyPropagatesAndDiscardsIncompleteFile | Cancel independent Job on actual production copy-progress callback; CancellationException propagates and partial copy disappears | NOT RUN |
| invalidPageAndE02RenderBoundsDoNotPoisonSessionAndClosedCallsFail | INVALID_PAGE, STALE_SESSION, axis/area RENDER_LIMIT before oversized allocation; subsequent valid renders; SESSION_CLOSED after closure | NOT RUN |
| sessionCloseReleasesCopyWhileCallerRetainsBitmapUntilLeaseClose | Input copy removed at session close; separate bitmap stays usable until its lease closes, then recycled | NOT RUN |
| realApplicationFactoryReusesGraphAndDoesNotOwnPdfLeases | Real Application constructor and production adapters, graph/configuration reuse, safe factory closure and independent session/bitmap lifetime | NOT RUN |
| providerRejectsWritesMutationsAndUnlistedFixturePaths | rw open, insert, update, delete and unlisted asset requests rejected | NOT RUN |

Shared integration assertions inspect every page and render small 128x160 outputs within E02
limits. They check dimensions, display/request identity, non-recycled pixels, explicit lease
closure, idempotence, recycling and use-after-close rejection. The original ReadHandle becomes
TRANSFERRED on engine entry, denies descriptor access/another transfer, and is never closed by
the old owner. The session owns native/input cleanup; retained descriptor wrappers must be closed
and temporary paths absent after session disposal. Before closure an owned copy must exist.
Teardown joins producers and checks for leaked complete/incomplete copies before deleting only
the test's own cache directory. These are implemented assertions, not observed Android outcomes.

### Available validation and coverage

Host validation on 2026-10-15: JDK25.0.4.1, Gradle9.8.0, AGP9.4.1, SDK37;
minSdk26 and all dependencies/gates unchanged.
No emulator/device was used: /dev/kvm is absent and adb devices -l lists no device. API26,
API27, API36 and physical-tablet execution are NOT RUN for this revision.

| Command/check actually run | Result |
| --- | --- |
| bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --max-workers=4 --console=plain | PASS; production/test APK compile, Lint and JVM checks |
| bash ./gradlew :app:testDebugUnitTest --rerun :app:assembleDebugAndroidTest :app:lintDebug --max-workers=4 --console=plain | PASS; explicitly re-executed JVM task after test cleanup review |
| python scripts/verify_test_results.py app/build/test-results/testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect | PASS 146 executed JVM tests, zero failures/skips |
| python -m unittest discover -s scripts/tests -v | PASS 39 infrastructure tests |
| python scripts/validate_localization.py | PASS en/ca/es |
| python test-fixtures/scripts/verify_fixtures.py | PASS all 15 immutable hashes, structure and host rendering checks |
| javap on compiled SafPdfIntegrationTest | PASS 12 public void test methods; not JUnit execution |
| APK ZIP/dex and aapt2 decoded manifests | PASS all 15 test-asset PDFs match manifest; no fixture PDFs or new provider class/registration in production APK; provider/authority appear in test APK |
| Lint report | PASS 0 errors, 20 existing warnings; no new production logic |
| git diff --check and scope review | PASS; production adapters, Room v1 schema, fixtures, Gradle and CI unchanged |
| Real Android instrumentation | BLOCKED / NOT RUN; 12 E03 tests compiled, 217 total expected (205 preserved + 12), 0 executed here |

The existing approved contract coverage gate passes as part of JVM validation. The same external,
uncommitted JaCoCo diagnostic used in E02 was rerun successfully with
`bash ./gradlew -I /workspace/partituras-tools/e02-coverage.init.gradle :app:e02CompositionJvmCoverage --max-workers=4 --console=plain`.
It reports the whole app/di package without exclusions: AppRuntimeLimits 7/7 lines, AppGraph 9/9,
and AppGraphFactory 13/36 lines and 12/14 branches. No production line changed and no Android
execution data exists. JVM-only coverage still cannot
prove production factory construction or its native construction-failure cleanup. E02's full-source
factory limitation (13/36 lines, 12/14 branches) remains unresolved; adding unexecuted Android tests
does not improve measured coverage. Updated combined Android/JVM coverage is BLOCKED / NOT RUN.
No denominator, exclusion, threshold or coverage infrastructure change is included.

### Reproduce on a real API26 emulator

Start a fresh disposable API26 google_apis x86_64 AVD using the existing SDK/test procedure.
Select its serial explicitly; do not use a physical device or an installation with user data.
On Bash (Windows uses gradlew.bat and $env:ANDROID_SERIAL in PowerShell):

```bash
export ANDROID_SERIAL=emulator-5554 # replace with the deliberately selected disposable AVD
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk # must be 26
adb -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu        # must be 1
bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.app.integration.SafPdfIntegrationTest
# Preserve XML/HTML under app/build/outputs/androidTest-results/connected and
# app/build/reports/androidTests/connected before running the separate full suite.
python scripts/verify_test_results.py app/build/outputs/androidTest-results/connected \
  --required-test com.abdev.partituraspdf.app.integration.SafPdfIntegrationTest.realApplicationFactoryReusesGraphAndDoesNotOwnPdfLeases
bash ./gradlew :app:connectedDebugAndroidTest
python scripts/verify_test_results.py app/build/outputs/androidTest-results/connected \
  --required-test com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext
```

Record exact SHA, selected AVD/serial/API, all 12 E03 outcomes, full-suite count and reports.
Run E03 as its own instrumentation invocation before the existing native negative-fixture class:
Android8 Pdfium can retain failure state after encrypted/corrupt opens. E03 introduces no such
native negative opens and does not depend on the existing class's method ordering. Existing
tests remain unchanged. A separate filtered PASS would not replace the mandatory full-suite gate;
any cross-class native failure-state interaction still needs diagnosis rather than hidden skips.

Deferred to separately authorized E05: real picker/persisted grants, permission loss/provider
death orchestration, external document changes and consumer-level lifecycle mediation. Native
mid-render cancellation latency, near-64MiB/disk-full stress and physical-tablet performance are
not established by the deterministic copy-cancellation test or small fixture renders. E04 Room/
DataStore acceptance and E06 phase closure remain unimplemented. No production defect has been
demonstrated by these unexecuted tests. Publication is delivery for review, not E03 acceptance;
no PR creation, merge or next-phase work is included.

## E04 — persistence through application composition

E04 is owner-authorized on feat/app-composition-e, based on E03
e6911b044991d36436feacc710c25b5ed378b343. Only AppPersistenceIntegrationTest.kt and this
documentation are added/updated. Production composition, adapters, contracts, Room schema,
manifests, dependencies, E03 provider/tests, CI and coverage policies remain unchanged.

### E03 device evidence supplied by the owner

The owner reports successful local execution on October 15, 2026, using a disposable
Android8.0 / API26 AVD at the E03 baseline:

| Owner-reported execution | Result |
| --- | --- |
| Filtered SafPdfIntegrationTest | PASS 12/12, zero failures/skips, Gradle successful |
| Complete Android instrumentation suite | PASS 217/217, zero failures/skips, Gradle successful |

This supplements the earlier historical cloud NOT RUN entries; it is local owner-reported
evidence, not an execution performed by Codex, a GitHub Actions result, a measured coverage
report, or physical Android8.1 tablet acceptance. No AVD identifier or raw reports were supplied
with E04. Those E03 test/provider files remain byte-for-byte unchanged. Their regression execution
at the E04 revision, including the expanded full suite, remains pending.

### Real adapter composition and isolation

All four E04 methods use the existing AppGraphFactory(limits, createResources) injection seam
and the existing AppGraph constructor. They do **not** invoke the production Application-based
factory constructor; that separate path is the subject of E03's
realApplicationFactoryReusesGraphAndDoesNotOwnPdfLeases test. This distinction prevents E04
writes from reaching the production default database or applicationPreferencesStore delegate.
No fake registry, metadata repository, preferences repository or in-memory persistence replaces
the real Android adapters.

The test-only Composition helper constructs one real, on-disk MetadataDatabase for each graph
lifetime and injects that exact instance into RoomDocumentRegistry and
RoomDocumentMetadataRepository. The default SQLite backend and production schema are retained.
Its name is e04-composition-UUID.db; the test checks the actual SQLite path against its intended
test-owned file. Closing the factory closes its owned Room instance, rejects subsequent get(),
and is idempotent. A reopened lifetime uses a genuinely new Room instance and new graph on the
same unique test database, with no destructive fallback or migration.

One real PreferenceDataStoreFactory store uses cache/e04-composition-UUID/preferences/
e04.preferences_pb and a test-owned SupervisorJob/IO scope. Both graph repositories and additional
repository objects borrow that one store. The factory releases only Room; it must leave this
separately owned store usable. Before opening a replacement store on the same file, the old
scope is cancelled and joined; construction checks that every earlier scope has completed.
The production app_preferences delegate, file and process lifetime are never used or modified.

The other graph slots contain the existing SAF catalog/read/grant and Android PDF adapters.
They perform no provider calls, rendering or grant mutation in E04. SAF read cache would be
contained in the test UUID directory. Registry constructors receive only Room; successful
registration of an opaque, deliberately unresolved provider/location records the exact strings
without decoding a URI, opening a provider or claiming current access.

Tests issue UUID DocumentIds independently from storage strings, filenames, bytes and hashes.
Before explicit registration, findRegistration and resolve return NOT_REGISTERED, metadata
observation yields Loading then Failed(NOT_REGISTERED), and metadata writes cannot create an
identity. Duplicate ID/location writes produce REGISTRATION_CHANGED and preserve the association.
Recorded availability remains a historical observation, independent from permission or existence.

Finite observation helpers require Loading then Ready and end their collection before disposal.
The live Room collector uses a channel handshake and distinctUntilChanged, receives each expected
transaction result, and is cancelled/joined in finally. Timeouts are failure watchdogs; there are
no arbitrary delays, polling loops or global mutable test state. Teardown closes all factories and
tracked databases even after an assertion/construction failure, joins store jobs, then deletes only
the exact UUID-named database and its SQLite auxiliary files plus the UUID cache directory.
No production name, real user data, external PDF, SAF grant or preference is cleared/deleted.

### Focused test inventory

The class is com.abdev.partituraspdf.app.integration.AppPersistenceIntegrationTest. These are
composition sequences across real adapters, not another exhaustive adapter/concurrency/corruption
suite. Existing C and E03 tests are retained. All four JUnit methods use block bodies returning void.

| Test | Implemented assertions | Android result in Codex |
| --- | --- | --- |
| graphConsumersShareExplicitRegistrationWithoutInventingStorageIdentity | Same graph/contracts/configuration for two consumers; real persistence adapters; missing registration/metadata; explicit UUID identity and exact opaque association; duplicate conflicts; actual on-disk SQLite path; factory closes database once | BLOCKED / NOT RUN |
| sharedRoomObservationAndMetadataSurviveACompletelyNewGraphAndDatabase | Live Loading/Failed -> registration/default Ready -> favorite -> Unknown position -> favorite toggle without losing position; joined collector; new graph/database recovers the same registration and both metadata fields | BLOCKED / NOT RUN |
| recordedAvailabilityRevisionAndRebindRemainSeparateFromHistoricalMetadata | UNKNOWN/AVAILABLE/PERMISSION_REVOKED/PROVIDER_UNAVAILABLE preserve association and metadata; stale expected location and foreign ownership reject mutation; exact revision matching; rebind resets registration revision/access while preserving historical favorite/position; reopen retains them | BLOCKED / NOT RUN |
| graphPreferencesAreIndependentAndPersistAfterStoreAndRoomOwnersReopen | Successful-read defaults; theme, page fit, English/Catalan/Spanish changes preserve other fields and Room metadata; another repository shares the same store; factory closure leaves store alive; cancelled/joined old scope and new store/graph recover all preferences and Room data | BLOCKED / NOT RUN |

Unknown reading positions remain resume hints after reopening. The revision/rebind test uses
explicitly synthetic SnapshotEvidence (constant test digest/byte count), solely to exercise existing
contract persistence and comparison. It is not evidence captured by a snapshot adapter, not a
canonical PDF hash, and not proof that any current provider bytes match. A digest mismatch or
rebind-invalidated verified position returns REVISION_CHANGED without destroying historical metadata;
revision.document mismatches propagate IllegalArgumentException rather than storage failures.

### E04 validation and remaining acceptance

Cloud validation date: October 15, 2026. Existing JDK25.0.4.1, Gradle9.8.0, AGP9.4.1, SDK37
toolchain and minSdk26 are unchanged. No Android emulator/device is available in Codex;
adb devices -l is empty and /dev/kvm is unavailable. No E04 persistence or cleanup assertion has
been observed on Android yet. APK compilation and host PDF rendering do not establish those results.

| Available check | Result |
| --- | --- |
| bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --rerun --max-workers=4 --console=plain | PASS; app/test APK compilation, Lint, explicitly rerun JVM task and existing contract coverage gate |
| JVM XML verifier retaining ExampleUnitTest.addition_isCorrect | PASS 146 executed tests, no failures/skips |
| python3 -m unittest discover -s scripts/tests -v | PASS 39 infrastructure tests |
| python3 scripts/validate_localization.py | PASS English/Catalan/Spanish |
| python3 test-fixtures/scripts/verify_fixtures.py | PASS all 15 immutable PDFs: hashes, structure and host rendering |
| Compiled JUnit signatures, source inventory and scope/whitespace review | PASS four void methods, 221 expected Android tests (217 unchanged + 4), no unauthorized files |
| Filtered E04 / E03 regression / complete Android suite at E04 revision | BLOCKED / NOT RUN; no device execution in Codex |

There is no new production business logic or coverage exclusion. Existing 90% line / 80% branch
requirements and all quality gates remain unchanged. No new Android execution data or combined
coverage report was generated. The last JVM-only app/di diagnostic remains AppRuntimeLimits 7/7
lines, AppGraph 9/9, full AppGraphFactory 13/36 lines and 12/14 branches; those figures do not
measure native constructor coverage. The owner's E03 PASS report does not supply coverage counts.
Do not claim an updated factory/persistence coverage percentage or threshold achievement from
compiled E04 tests. Existing C coverage evidence is historical and does not replace E04 execution.

Use a deliberately selected disposable API26 AVD for local acceptance (PowerShell uses
$env:ANDROID_SERIAL and gradlew.bat):

```bash
export ANDROID_SERIAL=emulator-5554 # replace with the selected disposable API26 emulator
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk # must be 26
bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.app.integration.AppPersistenceIntegrationTest
python3 scripts/verify_test_results.py app/build/outputs/androidTest-results/connected \
  --required-test com.abdev.partituraspdf.app.integration.AppPersistenceIntegrationTest.sharedRoomObservationAndMetadataSurviveACompletelyNewGraphAndDatabase
# Preserve each run's XML/HTML before the next invocation.
bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.app.integration.SafPdfIntegrationTest
bash ./gradlew :app:connectedDebugAndroidTest
python3 scripts/verify_test_results.py app/build/outputs/androidTest-results/connected \
  --required-test com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext
```

Expect four E04 tests, 12 E03 tests and 221 full-suite tests, all without failures/skips; these are
expected counts, not execution claims. Record revision, API/AVD/serial, reports and actual counts.
Keep E03's separate-process guidance for Android8 Pdfium negative-fixture state. No CI dispatch,
workflow change, PR creation or merge is included in this task. E04 remains pending device evidence
and human review. API27/36 final-revision compatibility, physical Android8.1 acceptance, deferred
Phase C backup/restore/D2D acceptance and Phase E closure are not established here. E05/E06 remain
unimplemented; no production defect has been demonstrated by these unexecuted Android tests.

## E05 — host SAF grants, error boundaries and reader lifetimes

E05 is separately owner-authorized on feat/app-composition-e, based on E04
f0a89782f630467f6f2f5e948ffc49908050cea8. Changes are limited to the new
AppSafLifecycleIntegrationTest.kt and this appended documentation. No production mediator,
contract, adapter, UI, manifest, dependency, schema or policy change is necessary or included.
E03/E04 tests and CanonicalPdfContentProvider remain unchanged.

### Evidence carried forward

On October 15, 2026 the owner reports local disposable Android8.0 / API26 AVD results:
E03 12/12 PASS, E04 4/4 PASS, and the full E04 suite 221/221 PASS, zero failures/skips.
These supplement the historical cloud NOT RUN entries above. They are owner-reported local
executions, not Codex/GitHub Actions executions, coverage measurements or physical Android8.1
tablet acceptance. No new device execution is implied for the E05 revision.

### Existing host boundary is sufficient

AppGraph.treeGrants already exposes the existing SafTreeGrantAccess to a controlled application
host. Future features receive their narrow catalog/read/repository contracts from that host;
they do not need Android permission mechanics or a global graph/provider. No production reader
ViewModel, application-host picker flow or orchestration service is authorized for wiring in E05.
A second mediator would duplicate existing operations without owning a concrete missing duty.
The tests therefore exercise the current API through AppGraph/AppGraphFactory's isolated seam.
They do not create a permission repository, background monitor or automatic reconciliation.
Application-based production construction remains covered by E03, not the E05 injection fixture.

treePickerIntent returns ACTION_OPEN_DOCUMENT_TREE without requesting WRITE. Picker **result**
flags supply capabilities: absent READ is rejected, invalid result URIs are rejected before any
take callback, and only FLAG_GRANT_READ_URI_PERMISSION is persisted/released even if WRITE was
offered. A successful take callback alone is insufficient: the reported persisted READ list must
contain the selected tree. Nested folders use that selected-tree root. No broad storage permission
or picker UI/launcher is added.

An unconfirmed tree yields Success(false) from hasPersistedReadGrant, not revocation or deletion.
Only a host which previously confirmed persistence may use requirePreviouslyPersistedReadGrant;
the helper stores no history itself. Known disappearance yields PERMISSION_REVOKED. Security
rejection while taking/querying a grant yields PERMISSION_DENIED; query failure cannot establish
revocation. An expired/invalid platform take is already covered by SafTreeGrantAccessTest.
Grant queries and changes never edit registry/metadata automatically.

### Tests, evidence levels and isolation

All seven tests use an isolated real Room database e05-UUID.db shared by the production registry
and metadata adapters in one graph. An independently owned real test DataStore supplies the graph's
preferences slot but is never read/written; the production delegate is not invoked. Its SupervisorJob
is cancelled/joined at teardown. Resource names are checked absent before ownership is claimed.
The factory owns only the test Room lifetime; teardown closes owners, joins provider completions and
store jobs, checks for temporary leaks, then removes only the owned UUID database/sidecars and cache.
No existing grant, document, production database or preference is cleared or replaced.

ControlledGrants uses instance-local callbacks for deterministic take/release/report states;
these callbacks never call Android takePersistableUriPermission or releasePersistableUriPermission.
Even after those tests execute, they will prove simulated transitions, **not authentic picker
authorization/revocation**. The one real OS grant-query test only reads persistedUriPermissions;
it compares URI/capability sets before/after without printing their potentially private contents.
Its fresh UUID tree is ungranted and no real grant is acquired or released.

Actual file reads always use the target ContentResolver and the unchanged E03 provider. Fixture01
(direct file) and fixture02 (asynchronous pipe) come from the canonical manifest/assets. The live-copy
test checks complete copy bytes against the canonical asset and its manifest length/page count.
The provider's request-token completion report verifies successful delivery or producer termination
after cancellation/failure. The exported test provider does not enforce picker grants; controlled
grant decisions and actual resolver readability are intentionally distinct levels of evidence.

| Test in AppSafLifecycleIntegrationTest | New composition assertion / evidence level | E05 Android result |
| --- | --- | --- |
| hostPersistsOnlyReturnedReadCapabilityAndRequiresReportedConfirmation | Existing host grant object, picker flags, invalid/missing READ rejection, take without confirmation, platform-denial seam, nested grant root, READ-only take/release and unchanged real metadata; simulated grants | BLOCKED / NOT RUN |
| realPersistedGrantQueryDoesNotTurnRegistrationIntoAuthorization | Actual OS persisted-grant query through graph, unknown tree remains false despite real Room registration; existing OS grant set unchanged | BLOCKED / NOT RUN |
| knownGrantLossAndRestorationPreserveMetadataUntilExplicitHostObservation | Simulated known grant loss -> revoked; no automatic unregister/metadata loss; explicit test-host observation; restored grant alone does not imply AVAILABLE; subsequent real resolver open supports a new historical AVAILABLE observation, revision remains Unknown | BLOCKED / NOT RUN |
| graphReadFailuresKeepTypedBoundariesAndNeverReconcileRegistration | Fault-injected provider boundary preserves INVALID_DOCUMENT, PERMISSION_DENIED, PROVIDER_UNAVAILABLE and PROVIDER_IO_FAILURE; unexpected programming exception propagates; association/favorite/hint unchanged | BLOCKED / NOT RUN |
| boundedAndPrivateCopyFailuresLeaveHistoricalMetadataAndNoPartialFile | Actual resolver pipes through graph: INPUT_LIMIT and test-owned directory obstruction -> PRIVATE_STORAGE_UNAVAILABLE, no partial copy or metadata mutation | BLOCKED / NOT RUN |
| cancellationAtPendingResolverHandoffReleasesAcquiredDescriptorWithoutMutation | Actual resolver descriptor paused by an injected handoff gate; cancel/join, CancellationException propagation, descriptor closed, no copy or metadata mutation | BLOCKED / NOT RUN |
| revocationAndFactoryCloseDoNotOwnTheReadersCopiedSessionOrBitmap | Actual resolver materialization/native PdfRenderer, simulated grant loss, retained registration, stale/render-limit rejection; factory closes opened Room but not reader-owned copy/session/bitmap; session close deletes copy and bitmap lease closes independently | BLOCKED / NOT RUN |

Injected SecurityException models denial, DeadObjectException models explicit Binder death,
FileNotFoundException/IOException model ambiguous provider I/O, and IllegalStateException remains
a programming defect. No actual Binder process is killed. Expected values are the existing typed
AppError enums, never exception text or paths. No failed open is translated to DOCUMENT_DELETED
or proof of revocation, and no private-copy failure becomes a malformed-PDF classification.

The test host, not a new production coordinator, explicitly records PERMISSION_REVOKED only after
its controlled previously confirmed grant disappears. It records AVAILABLE only after a real read
opens successfully; that remains an observation, not a guarantee of later access. Restoring the
simulated grant does not itself update availability, registration or revision. Favorites and Unknown
reading hints survive every tested transition. No VerifiedSnapshot evidence is constructed in E05.

The native lifetime test never closes the transferred original ReadHandle. A copied session remains
usable after simulated grant loss/factory shutdown because its owned bytes are already local; this
does not authorize new provider access or prescribe future reader UI policy. Explicit session close
is idempotent and removes its copy. The caller's live bitmap remains usable until its own lease closes,
then recycles; calls against the closed session reject with SESSION_CLOSED. Factory reuse represents
another host consumer, not a new Activity/UI lifecycle implementation. Cancellation uses latch
handshakes and a joined worker, with failure watchdogs and no arbitrary sleeps.

### Existing coverage reused rather than duplicated

- SafTreeGrantAccessTest covers the full grant validation/release matrix, including expired takes,
  invalid URI variants, nested roots and real unknown-tree queries. SafFinalHardeningTest covers
  proven prior loss versus unknown grant and failed lookup, idle-pipe cancellation and Binder death.
- SafReadRobustnessTest and SafReadCopyRecoveryTest cover mid-copy cancellation, interrupted input,
  orphan/active-copy recovery and cleanup failures. E03 additionally cancels after actual copy progress.
  E05 adds cancellation at the graph's pending real-descriptor handoff, not another copy algorithm.
- SafPdfIntegrationTest preserves invalid page, stale generation, axis/area limits, closed sessions,
  real bitmap leases and native fixture coverage. AndroidPdfFixturesInstrumentedTest and JVM PDF tests
  retain their native/ownership/error matrices. E05 adds copied-session ownership across grant loss and
  closing a factory with an actually opened Room database. No corrupt/encrypted native opens are added.
- AppPersistenceIntegrationTest and C-phase suites retain durable persistence, revision, transaction,
  concurrent-writer and corruption checks; E05 checks that access failures do not reconcile that state.

### Available validation

Host validation: October 15, 2026, JDK25.0.4.1, Gradle9.8.0, AGP9.4.1, SDK37; minSdk26 unchanged.
No emulator/device is connected (adb devices -l empty; /dev/kvm absent). No E05 Android test,
genuine grant flow or new native/cleanup assertion has executed in Codex.

| Check actually run | Result |
| --- | --- |
| bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --rerun --max-workers=4 --console=plain | PASS both APKs compile, Lint and explicitly rerun JVM task; existing contract coverage gate passes |
| python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect | PASS 146 executed JVM tests, zero failures/skips |
| python3 -m unittest discover -s scripts/tests -v | PASS 39 infrastructure tests |
| python3 scripts/validate_localization.py | PASS English/Catalan/Spanish |
| python3 test-fixtures/scripts/verify_fixtures.py | PASS all 15 fixture hashes, structure and host rendering |
| Compiled signatures / Android source inventory | PASS seven void JUnit methods; 228 expected tests = 221 unchanged + 7 E05, not executed here |
| Lint report | PASS 0 errors, 20 existing warnings |
| Scope, unchanged E03/E04/production and git diff --check | PASS; no dependency/build/manifest/schema/CI/policy changes |
| E05, E03/E04 regression and full Android suite at this revision | BLOCKED / NOT RUN; APK compilation is not instrumentation execution |

No new production business logic or JVM tests are introduced. The 90% line / 80% branch policy,
coverage inputs, denominators and exclusions are unchanged. No new Android execution/combined
coverage report exists. The known full-factory JVM-only diagnostic remains 13/36 lines (36.11%)
and 12/14 branches (85.71%); Android constructor/error-cleanup paths are not proved by that report.
Owner-reported E03/E04 PASS results do not supply measured coverage. Do not claim this gap is closed
by compiled E05 tests or manufacture updated persistence/native percentages.

### Local API26 execution and deferred authentic grant acceptance

Use a deliberately selected disposable API26 AVD and preserve each run's XML/HTML before the next:

```bash
export ANDROID_SERIAL=emulator-5554 # replace with the selected disposable AVD serial
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk # must be 26
bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.app.integration.AppSafLifecycleIntegrationTest
python3 scripts/verify_test_results.py app/build/outputs/androidTest-results/connected \
  --required-test com.abdev.partituraspdf.app.integration.AppSafLifecycleIntegrationTest.revocationAndFactoryCloseDoNotOwnTheReadersCopiedSessionOrBitmap
bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.app.integration.SafPdfIntegrationTest
bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.app.integration.AppPersistenceIntegrationTest
bash ./gradlew :app:connectedDebugAndroidTest
python3 scripts/verify_test_results.py app/build/outputs/androidTest-results/connected \
  --required-test com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext
```

Expect 7 E05, 12 E03, 4 E04 and 228 full-suite tests; record actual SHA, API/AVD/serial, counts,
failures/skips and reports. Windows uses gradlew.bat and $env:ANDROID_SERIAL. Keep E03's separate
instrumentation-invocation guidance around Android8 Pdfium failure state. No CI dispatch or new PR
is requested by this delivery.

Authentic picker grant acquisition, process-restart retention, nested real-provider access,
OS/provider-driven revocation and recovery remain manual/device acceptance, NOT RUN here.
The pre-existing debug-only SafGrantAcceptanceActivity can support owner-operated selection/check/
release on a disposable installation with only a synthetic test folder. It is unchanged and is not
launched automatically. Confirm the emulator identity and that the selected folder/grant belongs
only to this test; never release any pre-existing user grant. Capture picker result capabilities,
OS-reported persisted READ/WRITE bits, restart/access results and release results. Distinguish removal
of the test app's READ grant from document deletion; test genuine external revocation separately
where the platform/provider supports it. Do not extrapolate simulated state or an exported test
provider's access to a successful authentic permission flow. This task adds no launcher, background
permission monitoring, reconciliation, reader UI or global session registry. E06, final API27/36 and
physical Android8.1 acceptance, plus deferred Phase C backup/restore/D2D acceptance, remain pending.
No production adapter defect has been demonstrated by these unexecuted tests; any such defect
requires a separately authorized correction. Stop for owner review after publishing E05.

## E06 — final integration audit and PR readiness

Audit date: October 15, 2026. Repository alexbonavila/PartiturasPDF, branch
feat/app-composition-e. Approved code/test baseline: 3131d65a6ba5d93e2074419d51fde30be388951c.
Fetched main remains 89075908b236f8342df46f9621a79136653c1c9c, matching authorization; no
merge/rebase/reset is needed or performed. The worktree was clean at audit start. E06 changes
only this document; the final documentation commit SHA is recorded in the delivery report/PR.
No newer Android execution is inferred from this documentation commit.

**Overall Phase E acceptance: BLOCKED.** Implementation review and local functional evidence
are distinct from coverage, final hosted/compatibility checks, manual acceptance and owner approval.
This audit does not waive any criterion or authorize the next development phase or merge.

### Complete branch inventory and architecture audit

The full main...feature comparison contains exactly these 11 paths, with no unexpected change:

```text
app/src/main/java/com/abdev/partituraspdf/app/di/AppGraph.kt
app/src/main/java/com/abdev/partituraspdf/app/di/AppGraphFactory.kt
app/src/main/java/com/abdev/partituraspdf/app/di/AppRuntimeLimits.kt
app/src/test/java/com/abdev/partituraspdf/app/di/AppGraphFactoryTest.kt
app/src/test/java/com/abdev/partituraspdf/app/di/AppRuntimeLimitsTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/app/integration/CanonicalPdfContentProvider.java
app/src/androidTest/java/com/abdev/partituraspdf/app/integration/SafPdfIntegrationTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/app/integration/AppPersistenceIntegrationTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/app/integration/AppSafLifecycleIntegrationTest.kt
app/src/androidTest/AndroidManifest.xml
docs/integration/E_APP_COMPOSITION.md
```

The source-only changes are three composition files, two JVM test classes, four Android test/provider
files and the test manifest. There are no deletions or changes to A1 contracts, existing SAF/PDF/data
adapters, Room schema, DataStore delegate, MainActivity, navigation/UI, localization, production/debug
manifests, Gradle/catalog, CI, policies or licenses. Canonical PDFs/manifest remain immutable.
Historical architecture documents describe earlier design stages; actual compiled A1 contracts are
the implementation authority. No new public contract is needed to compose existing adapters.

AppGraph is an internal, explicitly passed typed dependency collection. It stores DocumentCatalog,
DocumentReadAccess, DocumentRegistry, DocumentMetadataRepository, PreferencesRepository and
PdfReadEngine contracts, immutable AppRuntimeLimits, and controlled host-only SafTreeGrantAccess.
There is no global accessor, mutable singleton, startup hook, Activity/Compose reference or navigation
dependency. Future features receive individual contracts rather than locating a global graph.

AppGraphFactory's production constructor accepts Application and explicit limits, retains one shared
Room instance for both metadata adapters, reuses the existing process-owned DataStore delegate and
constructs existing SAF/PDF adapters. Synchronized get() lazily caches a successful Resources value;
failed creation is not cached. Synchronized close() permanently rejects future get(), releases its
Room owner once and does not close caller-owned readers or the process DataStore. Construction
failure attempts Room cleanup while preserving the original exception and suppressing a distinct
cleanup exception. That native failure-cleanup branch remains an explicit test/coverage gap below.

KISS/DRY review: one module, one configuration value object, existing interfaces and delegate reused;
no DI framework, duplicate permission manager, extra repository or production copy/render algorithm.
Small isolated persistence/graph helpers repeat test assembly deliberately to avoid production file
access; they do not create another production composition root. No speculative refactoring is justified.

### Ownership matrix

| Resource | Creator / retainer | Release / boundary |
| --- | --- | --- |
| AppGraphFactory | Future application host, one owner per application lifetime | Host explicitly closes off main after repository work is quiescent; not Activity recreation/onTerminate |
| AppGraph | Factory, returned to host consumers | Borrowed typed references, no close; no global graph provider |
| Room database | Production factory | Same instance injected into registry/metadata; factory releases once; tests own UUID-named on-disk files |
| Production DataStore | Existing applicationPreferencesStore AndroidX delegate | Process-owned scope; factory borrows it and never creates a second delegate or cancels it |
| Test DataStore | Isolated test factory and scope | Test cancels/joins scope before reopen/delete; no production preferences access |
| SAF ReadHandle | DocumentReadAccess for each operation | One owner; unused handle closes locally; PdfReadEngine entry transfers ownership, invalidating old wrapper |
| PdfReadSession | Engine after adopting input/native renderer | Reader caller closes off main; session closes renderer/input idempotently, independently of graph |
| RenderedPage bitmap | Engine render then caller/cache lease | Caller closes after use; session/factory closure does not recycle a live caller lease |
| Temporary SAF copy | Existing bounded non-seekable materialization | Copy resource owns deletion; failures/cancellation discard incomplete data; session release deletes completed copy |
| Persisted SAF READ grant | Android following authentic picker result and READ-only persistence request | OS retains it; controlled host checks prior-known status/release, never touches unrelated user grants |

Runtime limits remain explicit 64MiB per non-seekable copy, 4096 pixels per axis and 4,194,304 pixels
per bitmap. Direct seekable input need not be copied; limits are not a universal file-size, concurrent
cache/disk quota, native peak-memory or physical-tablet performance guarantee. Those observations
do not certify a provider's current content revision.

### Requirement-to-test and evidence matrix

The owner reports real local Android8.0/API26 execution on **Tablet2 AVD, emulator-5554**, October
15, 2026: E03 12/12, E04 4/4, E05 7/7, full E05 suite 228/228, zero failures/skips. The reported
code revision is 3131d65a6ba5d93e2074419d51fde30be388951c. This is owner-provided emulator evidence,
not Codex or GitHub Actions execution, not raw coverage evidence, and not a physical tablet result.
E06 changes no code/tests. Latest-documentation-SHA hosted execution remains pending nonetheless.

| Requirement | Implementation / supporting test | Execution evidence | Current criterion status |
| --- | --- | --- | --- |
| E01 typed dependencies and constructor boundary | AppGraph/Factory; exposesInjectedContractsWithoutReplacingOrInvokingThem, productionEntryAcceptsApplicationAndExplicitLimitsRatherThanActivityContext | Codex JVM AppGraphFactoryTest 12/12; owner E03 realApplicationFactoryReusesGraphAndDoesNotOwnPdfLeases | PASS functional evidence; full-source coverage unresolved |
| E01 lazy/thread-safe reuse and isolated lifetime | Factory get/close; concurrentConsumersShareOneConstructionAndOneRelease, closingUnusedFactoryDoesNotAllocateAnyResources, separateOwnersHaveIndependentLimitSelectionsAndCleanup | Codex JVM PASS; owner E03/E04/E05 reuse/lifecycle PASS | PASS lifecycle evidence; native constructor-failure cleanup unmeasured |
| E01 failure/cancellation propagation | Factory; constructionFailuresPropagateAndDoNotCacheAnIncompleteGraph, releaseFailurePropagatesWithoutAllowingReuseOrDoubleRelease, factoryDoesNotTranslateCancellationDuringConstruction | Codex JVM PASS through injection seam | BLOCKED full Android-constructor negative-path evidence |
| E02 limits and ownership selection | AppRuntimeLimits and factory; AppRuntimeLimitsTest 6 methods, factory configuration propagation tests | Codex JVM PASS; owner E03 render/read limits PASS | PASS configuration evidence, not performance acceptance |
| E03 real seekable/pipe input, canonical bytes, native pages/bitmaps | Existing SAF/PDF adapters via test resolver/provider; SafPdfIntegrationTest 12 methods | Owner local API26 12/12 PASS | PASS reported native integration at code baseline |
| E04 explicit identity, shared Room, metadata/revision/rebind and persistence | Existing C adapters via isolated graph; AppPersistenceIntegrationTest 4 methods | Owner local API26 4/4 PASS | PASS reported persistence composition at code baseline |
| E04 preferences fields, independence and store reopen | graphPreferencesAreIndependentAndPersistAfterStoreAndRoomOwnersReopen | Owner local API26 PASS within 4/4 | PASS real isolated DataStore evidence, not production-file mutation |
| E05 READ-only host grant contract and unknown versus known loss | Existing SafTreeGrantAccess; hostPersistsOnlyReturnedReadCapabilityAndRequiresReportedConfirmation, realPersistedGrantQueryDoesNotTurnRegistrationIntoAuthorization | Owner local API26 PASS within 7/7; acquisition/revocation callbacks simulated, query real | PASS contract/query evidence; authentic grant acceptance NOT RUN |
| E05 metadata survives current access failures | knownGrantLossAndRestorationPreserveMetadataUntilExplicitHostObservation, graphReadFailuresKeepTypedBoundariesAndNeverReconcileRegistration | Owner local API26 PASS within 7/7; grant/fault injection explicitly scoped | PASS simulated transition/error boundary evidence |
| E05 copy/cancellation and independent graph/reader/bitmap owners | Remaining three AppSafLifecycleIntegrationTest methods; existing SAF hardening and E03 ownership tests | Owner local API26 PASS within 7/7 | PASS reported local resource assertions; no new physical/native stress measurements |
| E06 complete comparison, security/assets and evidence consolidation | This audit; unchanged policies/workflow, APK/class/schema inspection | Codex host checks below | PASS available audit; publication/hosted/manual gates remain BLOCKED |

### Error and recovery matrix

Expected failures remain existing Outcome/AppError values; cancellation/programming defects propagate.
This table describes contracts and evidence, not a newly implemented retry/reconciliation service.

| Error/state | Evidence needed / existing producer | Supported interpretation and host boundary |
| --- | --- | --- |
| NOT_REGISTERED | Room lookup/metadata operation has no logical registration | Not document deletion; explicit ID registration is a separate authorized operation |
| PERMISSION_DENIED | SecurityException or rejected/missing READ picker result | Denial does not establish prior revocation, PDF corruption or deletion |
| PERMISSION_REVOKED | requirePreviouslyPersistedReadGrant used only after prior confirmation; known grant disappears | No automatic unregister/metadata erase; unknown tree's hasPersistedReadGrant=false alone is insufficient |
| PROVIDER_UNAVAILABLE | Explicit Binder death/provider unavailability evidence | Distinct from permission rejection or confirmed deletion; no automatic metadata rewrite |
| PROVIDER_IO_FAILURE | Ambiguous FileNotFoundException, IOException, invalid provider result/read failure | Not proof of deletion or revocation; preserve historical association and allow separately approved recheck |
| DOCUMENT_DELETED | Requires independently confirmed deletion under existing core semantics | E03/E05 do not confirm deletion; current read adapter does not infer it from failed open |
| INPUT_LIMIT | Actual materialized bytes exceed positive selected copy bound | No partial copy returned; future host may select an approved limit/input, never silently remove the bound |
| PRIVATE_STORAGE_UNAVAILABLE | Private materialization/storage cannot be used | Distinct from provider I/O/malformed PDF; failed copy cleanup is attempted before returning failure |
| REVISION_CHANGED | Verified expected metadata revision differs from registered revision | No silent overwrite; Unknown positions stay hints, historical metadata may remain after rebind |
| STALE_SESSION | Wrong session/source generation supplied to PdfReadSession | Reject before native work; caller uses the correct session/generation rather than relabelling stale output |
| SESSION_CLOSED | Session retired/closed | Reject requests; reopening and separate ownership are explicit caller work |

SafReadCopyRecovery protects active copies within this process and retries abandoned managed-file
cleanup on a subsequent read. It is not a cross-process durable transaction journal or a universal
disk quota. Direct provider input can change externally; no VerifiedSnapshot is fabricated from
provider metadata or ordinary read-copy integrity checks. Exact editing/snapshot operations remain
outside Phase E. Native malformed/encrypted test ordering guidance remains unchanged for Android8.

### Security, permission and package audit

Scope: cumulative handwritten diff, compiled debug/release/instrumentation APKs, decoded manifests,
fixture hashes, schema bytes and dependency/configuration identity. No new broad-storage permission,
WRITE acquisition, network client/INTERNET permission, private user path, credential, PDF mutation or
production export was introduced. This is a scoped review, not a penetration/dependency security audit.
MainActivity remains the existing exported launcher. The existing AndroidX ProfileInstallReceiver is
exported with android.permission.DUMP; startup provider/Room service remain non-exported. No Phase E
production provider exists. The existing exported debug-only manual grant Activity remains debug-only.

The CanonicalPdfContentProvider is exported only in the test APK, under a distinct authority, exposing
fixed synthetic assets read-only. File delivery opens then unlinks unique cache copies; pipes produce
asynchronously. No storage permission, picker protocol, arbitrary path or write operation is added.
Completion records are per UUID request and consumed by tests. This is not an adversarial provider/
authentic Android grant test. Do not install instrumentation tooling as a production distribution.

APK ZIP/dex and aapt2 inspection: debug and release contain zero PDFs and none of the canonical
provider/E03/E04/E05 integration classes or provider registration. The test APK contains all 15 PDFs
with exact manifest hashes/lengths and the expected AndroidJUnitRunner/provider registrations.
Release also excludes SafGrantAcceptanceActivity. Assets are not duplicated in source control.
Room v1 schema is byte-identical to main; SHA256:
bc23cfd06a96945472317645e1b681911c213f38001ffa0e27e9f48f8b15f32e.
Root license and independent CC0 fixtures remain unchanged. No license/dependency update is proposed.

### Host verification performed in E06

Environment: managed Linux cloud, JDK25.0.4.1, wrapper9.8.0, AGP9.4.1, SDK37, JaCoCo0.8.14;
source/target Java11 and minSdk26 unchanged. No /dev/kvm; adb devices -l has no connected device.

| Exact command/check | Result / actual evidence |
| --- | --- |
| bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --rerun --max-workers=4 --console=plain | PASS both APKs compile, Lint and explicitly re-executed JVM task; approved contract coverage gate passes |
| python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect | PASS 146 executed JVM tests, zero failures/skips; includes 12 factory and 6 limits methods |
| python3 -m unittest discover -s scripts/tests -v | PASS 39 infrastructure tests |
| python3 scripts/validate_localization.py | PASS en/ca/es, no resource changes |
| python3 test-fixtures/scripts/verify_fixtures.py | PASS all 15 immutable hashes, structure and host rendering |
| bash ./gradlew :app:assembleRelease --max-workers=4 --console=plain | PASS unsigned release APK construction, including release vital lint |
| Lint report | PASS 0 errors, 20 existing warnings; no new high/critical finding demonstrated by this review |
| Compiled test discovery | PASS 23 integration void JUnit signatures: E03=12, E04=4, E05=7; total Android source inventory 228 |
| Full branch scope/schema/license/asset/manifest/whitespace review | PASS exact 11-file accumulated scope, E06 documentation only, no binary or unrelated change |
| Android execution in Codex | BLOCKED / NOT RUN; no local device run, no new Android count claimed |
| UI screenshots/accessibility, PDF save/edit scenarios | NOT APPLICABLE to this unchanged-UI read-only composition task; future applicable requirements are not waived |

Local logs, decoded manifests and diagnostic reports remain outside Git under
/workspace/partituras-tools/e06-*. No generated coverage/test/build artifacts are published as source.

### Coverage investigation and unresolved acceptance

Existing automatic scopes are A1 core/storage.api/pdf.api JVM contracts, optional PDF-adapter
combined reporting, and independently validated Room/DataStore package/source combined reporting.
None of those report scopes includes app/di. enableAndroidTestCoverage is enabled, so a genuine
Android run can generate app/di probes in the raw .ec, but a passing persistence gate is not a Phase E
composition report. CI retains .ec/JVM .exec and persistence classes; app/di original class files are
not included in that artifact's explicit retained class paths. Retain exact matching app/di bytecode
from the producer build for a reproducible additional report; do not reconstruct mismatched classes
and assume identical IDs. No workflow/artifact/configuration change is made in E06.

The external uncommitted E02 diagnostic was rerun in E06 with all app/di handwritten source included,
no new class/source exclusions:

```bash
bash ./gradlew -I /workspace/partituras-tools/e02-coverage.init.gradle \
  :app:e02CompositionJvmCoverage --max-workers=4 --console=plain
```

| Measured JVM-only source | LINE | BRANCH |
| --- | --- | --- |
| AppGraph.kt | 9/9, 100% | NOT APPLICABLE, no reportable branches |
| AppRuntimeLimits.kt | 7/7, 100% | NOT APPLICABLE |
| Entire AppGraphFactory.kt | 13/36, 36.11% | 12/14, 85.71% |

Factory get()/close() remain fully covered by JVM tests. The full source misses Android-constructor
normal assembly/release binding at lines30-45 and constructor-failure cleanup at lines47-55; both
branches at line51 (distinct cleanup exception versus same exception) are unexecuted in this report.
The injection-seam failure tests do not exercise that actual production constructor catch block.
E03 supplies positive real-constructor functional evidence, but no supplied test/report establishes
coverage of its negative cleanup/suppression paths. Combined coverage must be measured; do not
predict a passing percentage from the positive constructor test alone.

An external JaCoCo input inspection found five matching app/di class identities in current JVM
execution data. The existing ignored local coverage.ec was only 49 bytes, dated October14, and
ExecFileLoader rejected it with IOException. It has no fresh Android JUnit/provenance evidence;
it was preserved and **not** merged, labelled zero coverage, or presented as an emulator run.
No trustworthy matching Android .ec was available from the owner's local reports. Consequently
combined Phase E coverage is BLOCKED / NOT RUN. This is invalid/stale generated evidence, not a
demonstrated production adapter defect. The 90% line / 80% branch criterion remains unresolved;
no denominator, threshold, policy or exclusion is changed to hide it.

For fresh reproducible API26 input, use the existing approved sequence on the exact final checkout:
preparePersistenceCoverageRun, testDebugUnitTest --no-build-cache, connectedDebugAndroidTest,
verifyPersistenceCoverage. Preserve raw JVM .exec, Android .ec, successful XML, original app/di class
files, run SHA, versions, timestamps and class hashes before rebuilding/cleaning. The existing
persistence validator checks API26, same-build provenance, class IDs, probes and real test evidence
for its own scopes. An additional temporary app/di JacocoReport can consume those compatible inputs
and all five app/di classes; validate their IDs and report full source counters/missed branches.
That would be a diagnostic, not an automatic new policy gate. Any needed production test seam,
negative-path test or permanent coverage/CI change requires separately approved scope.

### Hosted CI, Draft PR and compatibility

Open-PR API queries (GraphQL and REST) currently fail with Forbidden. The public GitHub search
is:pr is:open head:feat/app-composition-e shows no matching PR at audit time. Avoid duplication
if one appears before publication. A Draft PR targeting main is authorized only after this audit
is committed; PR creation/verification and workflow dispatch remain BLOCKED until the API is usable.
No PR number, Draft flag or CI run is invented. The prepared PR body follows the repository template.
The final delivery report records the actual publication attempts and refs.

The running cloud allowlist does not include api.github.com. A minimal addition was saved in the
environment configuration draft for review; saving that draft has not activated or published the
network change. Existing Git HTTPS access works. Review/save/publish the environment settings to
activate API access, or execute the prepared PR/dispatch commands from an authorized local session.
No token value was read, printed, replaced or requested. No repository CI configuration was changed.

| Required hosted check at final PR revision/current main | Audit-time status |
| --- | --- |
| build | BLOCKED / NOT RUN verified for final PR |
| lint | BLOCKED / NOT RUN verified for final PR |
| unit-tests | BLOCKED / NOT RUN verified for final PR |
| instrumented-api26 | BLOCKED / NOT RUN verified for final PR |
| localization | BLOCKED / NOT RUN verified for final PR |
| pdf-fixtures | BLOCKED / NOT RUN verified for final PR |
| quality-gate | BLOCKED / NOT RUN verified for final PR |

The workflow triggers PRs to main, pushes to main, manual dispatch and schedule, not feature-only
pushes. Local PASS does not imply hosted success. All seven checks must pass at the latest PR SHA
against current main, without skipped/failed mandatory jobs or bypasses. Branch-protection state was
not independently verified through the blocked API; this audit does not change it.

For final compatibility, GitHub Actions -> CI -> Run workflow -> select feat/app-composition-e ->
enable compatibility -> Run. First confirm its tip equals the final published documentation SHA.
Equivalent authorized local command:

```bash
gh workflow run ci.yml --repo alexbonavila/PartiturasPDF \
  --ref feat/app-composition-e -f compatibility=true
gh run list --repo alexbonavila/PartiturasPDF --workflow ci.yml \
  --branch feat/app-composition-e \
  --json databaseId,headSha,url,status,conclusion
```

Record the matching final headSha, run ID/URL, all seven jobs, compatibility-api27 and
compatibility-api36 results, executed XML counts and reports. Images stay API27 google_apis x86
and API36 google_apis x86_64. Both final-revision compatibility executions are NOT RUN here;
historical Phase0 success is not Phase E evidence. No dispatch/run ID is claimed before success.

### Authentic SAF grant and physical-device acceptance

Manual SAF status: NOT RUN. On a deliberately selected disposable installation/AVD, create a
synthetic folder containing only canonical test copies. Verify serial/API and absence of user data.
Manually open the existing debug-only SafGrantAcceptanceActivity; no automatic launch is performed.
Select that test folder through the genuine Android picker, capture result READ capability and OS
persisted READ/WRITE bits, then check listing/read access. Restart the process and re-open the manual
Activity to check grant retention and a nested synthetic document. Release only the grant acquired
for this test, check the OS list/access result and retain the known-prior-grant distinction. Repeat
genuine selection/restoration; test OS/provider-driven revocation separately where supported.
Never release any pre-existing grant or treat an access error as deletion. The debug harness has
existing dedicated manual SharedPreferences; it does not exercise production AppGraph reconciliation.
Record final SHA, AVD/provider, synthetic folder, flags, before/after/restart results and limitations.
E05's callback states and exported fixture provider do not replace this authentic authorization flow.

Physical Android8.1 status: NOT RUN; Tablet2 is an AVD, not a physical tablet. Outstanding applicable
items include installation/compatibility, real SAF grant/restart/revocation, source survival, repeated
open/render/close and constrained-device resource behavior. No latency, memory or tablet suitability
measurement is invented. UI/reader performance budgets and feature interaction tests require their
own approved consumers; physical acceptance still blocks functional phase closure under current policy.

### Outstanding work and final status

1. **Demonstrated acceptance defects:** none newly reproduced in production or tests by this audit.
   No P0/P1 or concrete current-phase implementation defect was found in the scoped review; this is
   not proof of absence. A future failure needs reproduction, owner severity assessment and separately
   authorized correction, not a silent E06 refactor.
2. **Blocking missing evidence/delivery:** reliable full-source app/di combined coverage, including
   native constructor cleanup paths; Draft PR creation/verification; seven latest-revision hosted
   checks; final API27/36; authentic grants; applicable physical Android8.1 acceptance and explicit
   owner decision. These are not relabelled implementation bugs or waived because API26 passed.
3. **Feature work outside this delivery:** library/reader/settings UI, navigation, application startup,
   editing/annotations/Drive and future integration F/G/H/I remain unimplemented and require their
   own explicit task authorization. Deferred Phase C backup/restore/D2D acceptance remains separate.
4. **Nonblocking future considerations:** test-helper sharing only if a concrete maintenance need
   arises; additional measured cache/concurrency/performance policy with approved consumers. None
   is implemented here or used to defer a mandatory acceptance gate.

| Stage | Explicit evidence/acceptance status |
| --- | --- |
| E01 | BLOCKED final full-factory coverage/negative native cleanup evidence; JVM lifecycle and positive API26 functional evidence PASS |
| E02 | PASS configuration/JVM evidence within scope; provisional values are not device performance acceptance |
| E03 | PASS owner-reported API26 functional evidence at code baseline; final hosted/compatibility gates pending |
| E04 | PASS owner-reported API26 functional evidence at code baseline; final hosted/compatibility gates pending |
| E05 | BLOCKED authentic grant/manual acceptance; seven owner-reported API26 composition tests PASS |
| E06 | BLOCKED PR/hosted/coverage/compatibility/manual evidence; available audit checks PASS |
| Overall Phase E | BLOCKED; not fully accepted, not merge-ready, no subsequent phase authorized |

Only the human owner may make the final approval and merge decision after applicable evidence is
complete. No merge, auto-merge, main push, protection change or further implementation is authorized.


---

## Post-Merge Phase E Evidence Reconciliation — 2026-10-16

This dated reconciliation supplements the earlier current-state note and chronological E01–E06
findings above; the complete original text, including historical commands, SHAs and BLOCKED /
NOT RUN results, is preserved. It records later integration/evidence, not retroactive execution
or unconditional phase acceptance. The verified `main` baseline is
`f94841f9f1eead508dd81b670d0943f3b3ae19bb`; P01/P02 are documentation-only work on
`chore/pre-fgh-readiness` from that baseline.

### Delivery and subsequent automated evidence

- [PR #12](https://github.com/alexbonavila/PartiturasPDF/pull/12) merged Phase E at
  `c5db5b4cce4c08bdf3ead92b21d3972e96843392`.
- [PR #13](https://github.com/alexbonavila/PartiturasPDF/pull/13) merged the governance/status
  correction at `f56b6193c9b059cf647502a6b75dcbf762afd70c`.
- [PR #14](https://github.com/alexbonavila/PartiturasPDF/pull/14) merged L01–L04 automation at
  `f94841f9f1eead508dd81b670d0943f3b3ae19bb`. The old lack of PR publication/integration is
  resolved; its historical audit-time BLOCKED statement is retained above.
- [Main push CI #58](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38926693866),
  `f94841f9`: build, lint, unit-tests, instrumented-api26, localization, pdf-fixtures and
  quality-gate all PASS. API27/36 were not requested in this push run.
- [Manual compatibility CI #59](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38926745562),
  `f94841f9`: all seven mandatory jobs plus compatibility-api27 and compatibility-api36 PASS.
  The earlier missing final hosted/compatibility evidence has subsequent main-baseline evidence;
  emulator execution does not satisfy physical Android 8.1 acceptance.
- [PR #14 CI #57](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38925687779)
  passed the seven mandatory jobs at its pre-merge revision `36a52fb`; it is not a run on the
  later `f94841f9` merge commit.

The merged PRs and public run/job result pages were rechecked on 2026-10-16. Raw logs, XML and
execution artifacts were not inspected in this review (GitHub API access remains unavailable).
The recorded inventory of **165 JVM / 228 Android tests** therefore is not a new independently
measured count for #58/#59. No new run or branch-protection verification is claimed here.

[L04D](../LOCAL_COMPATIBILITY_VALIDATION.md) separately records owner-reported native macOS
Apple Silicon API26 ARM64 `full` and `coverage` PASS at
`87b864de296563649a4f80e15b55e30f75e26836`: 246 Python / 165 JVM / 228 Android / four authentic
SAF stages for `full`, and genuine merged JVM+Android data for the approved coverage scopes.
Its earlier Windows evidence is at `27cef9f` (L02 full) and `9d10739d` (L03 coverage), not at the
latest main SHA. The historical hosted compatibility run
[38857215346](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38857215346) is also
at `87b864de`. These report-attributed results were not rerun on native hosts here. In particular,
the genuine picker grants in the controlled synthetic-folder SAF suite supply evidence beyond
E05's simulated callbacks, but do not prove every external provider or a production host
reconciliation flow. The user-facing library, reader and settings screens remain unimplemented.

### Requirement-to-evidence reconciliation

Rows below distinguish implementation/evidence already present from the precise evidence still
needed. Formal blockers remain governed by the existing Definition of Done; none is waived.
P03/P04/P05 and additional execution require their own owner-approved scope.

| Requirement | Implementation state | Test / evidence state | Remaining limitation, closure evidence and responsibility |
| --- | --- | --- | --- |
| E01 composition contracts and lifetime | AppGraph/Factory implemented and merged in #12 | Historical JVM factory lifecycle/failure tests and positive real-constructor integration; subsequent full-suite hosted PASS at `f94841f9` | Native constructor failure/cleanup and full-source coverage remain unestablished. Matching combined data, original classes and source/branch report must resolve the existing criterion; formal coverage blocker, P03 / owner. Injection-seam tests alone do not close it. |
| E02 runtime limits | Immutable explicit read/render limits and owner lifetime implemented | Historical limits/configuration JVM tests; subsequent hosted unit/instrumentation PASS | Provisional ceilings are not measured performance budgets. Closing applicable device/resource acceptance needs repeated open/render/close and memory/latency observations on the selected constrained device; owner-approved physical/performance task. Future UI budgets require approved consumers, not speculative P02 changes. |
| E03 SAF-to-PDF integration | Production adapters composed; real resolver/provider/native-render integration tests merged | Historical 12-test API26 owner report; full-suite hosted API26/27/36 PASS at `f94841f9` | Controlled providers/canonical inputs do not cover all external providers or physical conditions. Retained real-provider and physical-device scenario results at an exact SHA are needed for applicable formal acceptance; owner-led device/provider task. |
| E04 Room/DataStore integration | Shared Room observation, metadata and preferences integration implemented | Historical four-test API26 owner report; subsequent full-suite hosted PASS | Real backup/reinstall/restore and D2D exclusion evidence still missing; C06 guarded acceptance with restored preferences and absent Room/private files closes the relevant persistence-security criteria. Owner / C06 deferred register; formal C acceptance blocker, not an E04 implementation failure. |
| E05 lifecycle/error boundaries | Error/cancellation and independent graph/reader/bitmap ownership tests merged | Historical seven-test owner report; hosted full-suite PASS. Native L01 SAF evidence at `87b864de` uses authentic Android authorization | Additional external-provider revocation/restart conditions, physical stress and production-host reconciliation remain unverified. Close applicable manual E criteria with provider/device identity, grant flags, exact revision and retained before/after results; owner-approved external SAF / physical task. Controlled E2E is not universal provider acceptance. |
| E06 delivery and acceptance reconciliation | Chronological audit retained; #12 integrated | PR publication, hosted mandatory checks and API27/36 now evidenced by merges and #58/#59 result pages | Coverage/manual obligations below and explicit final owner acceptance remain outstanding; formal phase closure cannot be inferred from merge or green CI. Owner reviews complete requirement evidence after approved follow-up tasks. |
| L01–L04 automation | Merged in #14; opt-in and ordinary suites remain distinct | Native reports at their recorded SHAs; hosted main-baseline PASS | No new native Windows/macOS run at `f94841f9` or raw current-run count inspection is claimed. Fresh exact-revision host reports/XML would establish those specific claims if required by owner; attribution limitation, not an assertion that hosted CI failed. |
| Full `app/di` combined coverage | Composition instrumentation exists; approved scoped coverage tooling exists | Current full-package JVM+Android report unresolved; A1/B5/C05 scoped PASS is not composition coverage | Missing matching Android/JVM data, original bytecode/provenance and all-source counters including native cleanup branches. P03 must measure and report against existing policy; formal coverage verification blocker. No new gate/threshold/exclusion is authorized. |
| Physical Android 8.1 tablet | Backend implementation exists; device not yet accepted | **DEFERRED / NOT RUN**; API27 emulator PASS is separate | Acquire/select the physical tablet, record model/API/SHA and relevant compatibility, SAF, source-survival and resource results. Owner-approved physical acceptance after hardware purchase; blocks applicable functional-phase acceptance. |
| Real backup/restore and D2D | Policy B exact-file allowlist and guarded manual tooling merged | XML-policy/static tests and persistence suites provide automated evidence; actual restore/D2D **DEFERRED / NOT RUN** | Execute independently supported cloud-transport restore and D2D procedures only in explicitly selected disposable environments. Retain exact preference survival, Room/sidecar/private-file absence and transfer-mode limitations. Owner / C06 deferred register; formal persistence acceptance blocker. |

### Coverage interpretation and formal status

The earlier factory JVM-only `13/36` line and `12/14` branch diagnostic remains a historical
JVM-only measurement of the full source, not zero Android coverage or a combined percentage.
L04D's combined A1/B5/C05 scope results do not supply the missing full `app/di` denominator or
establish coverage of the production constructor's native failure/cleanup paths. P03 is pending;
no coverage infrastructure, policy or tests are changed by this reconciliation.

- **Technical integration:** Phase E is merged with backend integration tests and subsequent
  hosted main-baseline mandatory/API27/API36 PASS evidence. The historical “not merge-ready”
  verdict describes its earlier delivery, not the current fact of owner-controlled integration.
- **Coverage verification:** complete combined JVM + Android `app/di` coverage remains unresolved
  in the evidence available to R0/P02. No unconditional policy-threshold achievement is claimed.
- **Physical/manual acceptance:** external/provider/device scenarios and controlled C06 restore/D2D
  evidence remain outstanding, as detailed above and in the
  [Phase C deferred register](../PHASE_C_DEFERRED_ACCEPTANCE.md).
- **Formal acceptance:** this documentation task does not declare unconditional Phase E acceptance,
  waive any [Definition of Done](../DEFINITION_OF_DONE.md) requirement or substitute integration
  for explicit owner approval. P02 reconciliation can be delivered while those separate obligations
  remain open. F/G/H/I implementation is future owner-approved work; P03/P04/P05 are not started here.

Use [PROJECT_STATUS](../PROJECT_STATUS.md) and [ROADMAP](../ROADMAP.md) for the current index.
Future revisions still require applicable latest-head CI/current base and human review; evidence
at the baseline above is not automatically certification of this new documentation commit.
