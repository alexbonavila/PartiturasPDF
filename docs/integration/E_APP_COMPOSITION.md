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
