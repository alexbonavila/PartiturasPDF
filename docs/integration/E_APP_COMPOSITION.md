# E02 runtime limits and ownership decisions

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
