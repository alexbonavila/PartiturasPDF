# Restartable native PDF renderer — owner review handoff

Date: 2026-10-19. Base: `c654d9c2d4841375146b544234d48fa201624702`
(`origin/feat/reader-g`). Delivery branch: `fix/pdfium-process-isolation`.
No merge, Phase I navigation, policy change or fixture modification is included.

## Defect and selected architecture

The owner supplied confirmed API 26 results from
`20261019T054650Z-c3c88050f53c4dc192b0e1417df4d9ef.zip`: 26 independent
instrumentation invocations, 18 passing and 8 reproducing contamination. Valid
fixture 01 opens in a clean process; corrupt 15 or protected 14 can poison later
valid opens through both PdfRenderer and AndroidPdfReadEngine. Replacing that
process recovers. This archive is owner evidence, not a new execution here.

Production now injects `IsolatedPdfReadEngine` through `AppGraphFactory`.
`ReaderRouteOwner`, G's controller, screen, surface, checkpoint and public PDF
contracts are unchanged. The native implementation remains
`AndroidPdfReadEngine`, invoked only in `PdfWorkerService` on this production path.
Direct low-level tests remain direct intentionally.

```text
Application: SAF authorized source -> original ReadHandle retained by client
  -> IsolatedPdfReadEngine -> client session / independent published bitmap
         | explicit Binder: small Bundle controls + PFD capabilities
         | reliable pipe: bounded ARGB rows, no bitmap arrays in Binder
Isolated UID, :pdf process: PdfWorkerService -> AndroidPdfReadEngine -> PdfRenderer
```

The service is `exported=false`, `isolatedProcess=true`, `process=:pdf`.
It receives a seekable read-only descriptor and opaque per-open identifiers.
Read-only capability acquisition remains the existing trusted ReadHandle/SAF
contract; no hidden FD flag APIs or read/write reopening is introduced. It
receives no URI, user path, document registry, revision evidence, credentials or
provider authority. It constructs no Room/DataStore/app graph, acquires no SAF
grant and opens no application-private backing path. There are no new permissions
or foreground/persistent jobs. Construction of the client is lazy and does not bind.
The worker checks that its UID differs from the application's UID before any
self-termination. It can kill only its own PID; there is no caller-supplied kill PID.

The only Gradle change enables the existing Android AIDL build feature. No new
library, PDF engine, dependency version or module is added. SharedMemory was
rejected because it is unavailable on API 26. Full-page Binder payloads, hidden
ashmem APIs and a permanent non-restartable second process were rejected.

## Lifecycle, identity and recovery

`STOPPED -> BINDING -> READY -> SESSION_OPEN -> RETIRING -> DEAD` belongs to the
PDF transport, not G. The engine serializes opens; the session serializes info
and rendering; the worker serializes native calls on one owned executor. One
healthy native document/worker serves successive pages with zero prefetch.
A second open while a live session remains owned returns `STALE_SESSION` and
closes only the newly acquired source. It cannot steal the active session.

Identity contains PID, process-start elapsed realtime and a random worker epoch.
Every new connection checks isolated execution and rejects reuse of the previous
PID/start pair. Session UUIDs, request UUIDs, source generation, exact verified
revision (client-side), page and viewport generation form validation boundaries.
Public requests are checked against `ReadSessionInfo` before IPC. A fresh bounded
wire request UUID avoids passing arbitrarily long caller cache keys to Binder.

All native-open failures quarantine the worker on **every supported API**,
including 26/27/36. Native info/render failures also quarantine it. Caller
validation failures do not poison a healthy renderer. The worker returns the
original typed error before the client requests retirement; it never retries the
same input. Unexpected worker death returns a recoverable storage/session failure
and invalidates the old session. An explicit later open requests recovery.

`retire()` is an oneway control call independent of the native executor. It marks
the worker quarantined, then schedules self-termination on its main looper. The
client observes Binder death (`DeathRecipient` or a non-live observed Binder).
Neither unbinding, a disconnect callback nor an expired deadline proves death.
Framework reconnection is rejected rather than silently replacing an old Binder.
Late callbacks cannot retire a different worker now owned by a fresh binding.
Two focused Android regressions cover close-during-bind and delayed replacement
after observed death.
Only observed death permits another binding after a known worker was exposed.
A new identity is checked again before use.

Binding and death-observation deadlines are 10 seconds, not performance acceptance
thresholds. A death timeout leaves the connection quarantined; subsequent opens
return `PROVIDER_UNAVAILABLE` until death is observed. The source backing owner is
retained until that event and released by one bounded IO cleanup. A timeout never
authorizes releasing a possibly live backing copy. No infinite recovery loop runs.
If the OS never terminates the worker, this intentionally retains that one backing
owner and blocks reuse; it does not create more workers.

## Ownership and cancellation

| Resource | Owner and release |
|---|---|
| Original source | `takeHandle()` transfers exactly once to the client adapter. Retained through session lifetime; failure/cancellation or session close releases it. |
| Main PFD/backing copy | Remains with that original owner. IPC duplication alone never releases a temporary copy/snapshot owner. |
| IPC input PFD | Binder duplicates into the isolated worker. The worker ReadHandle transfers to the existing native engine; unsuccessful acquisition closes it. |
| PdfRenderer/native page | Existing engine/session owns renderer; existing per-operation `use` closes each native page. Graceful session close precedes normal retirement. Process death releases remaining isolated resources. |
| Worker bitmap | Existing native RenderedPage lease, held only while streaming; `use` closes it before successful reply publication. |
| Pipe | Client owns local ends; Binder duplicates the write capability. Stream/operation finalizers close ends idempotently, including aborts and RPC exceptions. No raw FD is closed twice/reused manually. |
| Client bitmap | Unpublished until complete stream plus successful matching reply. Failure/cancellation joins the actual IO work before recycling it. |
| Published lease | Client-only, independent of service/session lifetime; synchronized idempotent close recycles once. Getter after close rejects access. |
| Client session/service | Caller closes session off main. Busy close invalidates output and requests isolated termination, joins the operation, then resolves source ownership and unbinds. |

Binder calls run on IO and cannot be interrupted simply by cancelling a coroutine.
`PdfWorkerCall` requests retirement on cancellation and joins the actual blocking
call under `NonCancellable` before returning. Already-cancelled callers do not
start another call. The pixel consumer runs concurrently with the producer to
avoid pipe-buffer deadlock. Cancellation closes pipe capabilities and retires the
worker, then joins both paths before disposing of unpublished pixels. Late replies
cannot publish a frame. Cleanup errors remain suppressed on the primary cancellation.

Expected errors preserve all existing AppError categories over a finite enum
encoding. Worker programming defects produce a distinct protocol defect and are
re-thrown, not classified as malformed PDFs. A death before a failure reply arrives
cannot preserve an unavailable parser diagnosis; it yields a recoverable connection
failure instead. There is no fabricated successful frame or displayed-page ACK.

## Pixel protocol and total-memory considerations

A reliable pipe is available from API 19 and is used uniformly on API 26/27/36.
The version-1 big-endian stream contains magic/version, worker epoch, session UUID,
wire request UUID, viewport generation, width/height, expected 64-bit byte count,
row-major ARGB_8888 integer pixels, CRC32 and mandatory EOF. Pixel format and byte
order are fixed by version 1. Dimensions and overflow-safe pixel counts are checked
before bitmap/row allocation. The consumer rejects wrong identities/generation,
wrong dimensions/length, corrupt payloads, missing footer, premature EOF and trailing
bytes. Bitmap `getPixels`/`setPixels` transfers logical rows without assuming native
row stride. Native rendering retains the existing opaque white sheet.

No page-sized byte array is allocated in production. Each endpoint uses one int
row and a 32 KiB buffered stream, plus the bounded kernel pipe. At the protocol's
absolute 16384 dimension cap, row plus stream buffers total at most 192 KiB across
both endpoints, excluding the kernel pipe and small controls.

The isolated session advertises at most half the caller's pixel ceiling, capped
at 4,194,304 pixels and dimension 16384. This reserves the caller's logical pixel
allowance for the overlapping worker/client planes (minimum one pixel per plane).
With the current production ceiling of 4,194,304 pixels, each plane is capped at
2,097,152 pixels (~8 MiB ARGB payload); the temporary pair is ~16 MiB. The lower
single-page resolution is an intentional conservative tradeoff. The one-pixel
minimum may exceed a caller's one-pixel total by one pixel. Pixel limits are not
claims about native parser heap, bitmap allocation padding, surface buffers,
process overhead or total device RAM.

G already retires and releases its old frame before its next render. The adapter
retains no cache. Other callers must honor the existing exclusive lease contract
and bound their own retained leases. Moving allocations to another process does
not reduce combined device RAM. Low-memory worker death is recoverable; client
bitmap allocation OOM returns `RENDER_LIMIT` without poisoning a healthy worker.

## Test inventory and actual local evidence

Added JVM tests cover protocol headers/ARGB/endian/rows, every truncated prefix,
CRC/trailing bytes, size/overflow/generation bounds, producer/consumer exceptions,
lifecycle transitions and reuse rejection, all finite error mappings, memory
ceilings, actual blocking-call cancellation/draining and late reply rejection.

Added Android tests use the real production adapter and immutable canonical test
assets copied only into unique test-owned cache files. They cover:

- Valid -> corrupt -> valid; valid -> protected -> valid; both two-invalid orders.
- PID/start replacement and unchanged main PID, real page info/ink/opaque pixels.
- Healthy worker reuse, multiple pages, session exclusivity, stale identities,
  invalid pages, oversized rendering, idempotent closure and backing lifetime.
- A published bitmap remaining valid after worker/session retirement.
- Failed binding, cancellation before/during binding, binding timeout, manifest isolation.
- A native 3 MiB page payload and actual Bitmap ARGB row roundtrip.
- Out-of-band termination of the real worker through its own retirement protocol.
- Deterministic pending-RPC and successful-late-reply cancellation barriers around
  the real remote Binder. These barriers are test-only, not fake PDF output.
- Small Bundle validation and distinction between typed failures and worker defects.

The worker-death test simulates unexpected loss using a second test-owned binding;
it does not claim to reproduce a native SIGSEGV. Pending-RPC barriers do not prove
termination at a particular instruction inside Pdfium. The real native crash/
low-memory/render-interruption scenarios still require Android execution and logs.

The official `ReaderNativeIntegrationTest` route now uses the isolated engine.
Its differential direct-engine experiment remains direct. Existing Compose/native
assertions, checkpoint race tests, `ReaderPdfiumProcessStateTest` and its manual
runner/documents remain intact. No test is disabled, reordered or relabelled.

Local validation (final source, 2026-10-19):

| Check | Observed result |
|---|---|
| `:app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug :app:testDebugUnitTest --max-workers=4 --console=plain` | PASS; debug/release and instrumentation APK compiled; 379 JVM tests, zero failures/errors/skips. |
| Lint | PASS, zero errors; 21 pre-existing warnings, no new warning in the isolated package. |
| `python scripts/verify_test_results.py app/build/test-results/testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect` | PASS, 379 executed tests. |
| `python -m unittest discover -s scripts/tests -v` | PASS, 256 host infrastructure tests. |
| `python -m unittest discover -s app/src/androidTest/java/com/abdev/partituraspdf/reader -p test_pdfium_process_runner.py -v` | PASS, 12 experiment-runner host tests. |
| `python scripts/validate_localization.py` | PASS; no resource/translation change. |
| `python test-fixtures/scripts/verify_fixtures.py` | PASS, all 15 canonical fixture checksums/structure/host rendering; not Android rendering. |
| Policy B verifier, merged manifest plus compiled debug APK | PASS; backup policy unchanged. |
| APK/manifest inspection | Service remains private/isolated in debug and release; production APKs contain zero PDFs, instrumentation APK contains the 15 canonical PDFs. |
| New JUnit 4 signatures | Compiled methods return void, inspected using `javap`. |
| `git diff --check` | PASS. |
| `:app:verifyPdfAdapterCoverage` | BLOCKED: prerequisite `connectedDebugAndroidTest` fails with `No connected devices!`. No gate/threshold bypass. |
| API 26 / 27 / 36 | NOT RUN — ANDROID ENVIRONMENT UNAVAILABLE. 24 new tests are compiled, not executed. |

There are 22 new JVM tests and 24 new instrumentation tests (347 standard Android
source test methods in total, not an executed count). A scoped JaCoCo analysis of
the actual JVM execution data reports **80/80 lines (100%) and 116/118 branches
(98.31%)** for the five pure files: `PdfPixels`, `PdfWorkerLifecycle`,
`PdfRemoteErrors`, `PdfWorkerCall`, `IsolatedRenderLimits`. This is explicitly a
pure-logic subset, not a passing combined adapter gate. Bundle/Binder/service/
bitmap/session execution is still pending. No Android-dependent class was excluded
from the existing coverage denominator.

Android compilation is **not** runtime evidence.

## Reproduce on an owner-selected disposable macOS emulator

No command below creates/wipes an AVD, changes SAF grants or clears application data.
Use a dedicated disposable emulator; do not select a physical/user device. Select
its actual serial explicitly, inspect its identity, and confirm ownership before
installing these APKs. Repeat independently for genuine API 26, 27 and 36 images.
Native ARM64 acceleration/image availability must be checked on Apple Silicon.

```bash
git fetch origin fix/pdfium-process-isolation
git switch fix/pdfium-process-isolation
git status --short --branch
git rev-parse HEAD
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=4 --console=plain
adb devices -l
SERIAL=emulator-5554  # replace with the explicitly selected disposable emulator
adb -s "$SERIAL" shell getprop ro.kernel.qemu
adb -s "$SERIAL" shell getprop ro.build.version.sdk
adb -s "$SERIAL" emu avd name
# Owner confirms this exact serial/AVD is disposable before the following installs.
adb -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
REPORT="$(mktemp -d "${TMPDIR:-/tmp}/isolated-pdf-evidence.XXXXXX")"
git rev-parse HEAD > "$REPORT/revision.txt"
adb -s "$SERIAL" shell pm list instrumentation > "$REPORT/instrumentation.txt"
# Verify the installed component/target from instrumentation.txt, rather than assuming success.
adb -s "$SERIAL" shell am instrument -w -r \
  -e class com.abdev.partituraspdf.pdf.android.isolated.IsolatedPdfReadEngineTest,com.abdev.partituraspdf.pdf.android.isolated.PdfWorkerProtocolTest \
  com.abdev.partituraspdf.test/androidx.test.runner.AndroidJUnitRunner \
  > "$REPORT/isolated-instrumentation.txt" 2>&1
adb -s "$SERIAL" logcat -d -v threadtime > "$REPORT/logcat.txt"
adb -s "$SERIAL" shell dumpsys activity processes > "$REPORT/processes.txt"
```

Expected targeted inventory: 24 tests, zero failures/errors/skips. Inspect actual
`INSTRUMENTATION_STATUS` and final result; an adb exit code alone is insufficient.
`IsolatedPdfAcceptance` logs/status Bundles contain the main PID, worker PID/start/
epoch, sequence and elapsed time. Check actual replacement after each invalid
input, both mixed invalid orders, and successful rendered ink after recovery.
For machine-readable Gradle JUnit and coverage run the same class selection:

```bash
ANDROID_SERIAL="$SERIAL" ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.pdf.android.isolated.IsolatedPdfReadEngineTest,com.abdev.partituraspdf.pdf.android.isolated.PdfWorkerProtocolTest \
  --max-workers=4 --console=plain
# Preserve app/build/outputs/androidTest-results and app/build/reports/androidTests.
```

This targeted execution is diagnostic acceptance, not a replacement for the full
mandatory suite. Run G's native/Compose acceptance and the full suite separately;
retain failing direct native sentinel results as well. The original controlled
process experiment procedure remains in
`app/src/androidTest/java/com/abdev/partituraspdf/reader/PDFIUM_PROCESS_EXPERIMENTS.md`.
No CI selector, gate or known-failure suppression is changed here.

## Performance, coverage and remaining integration blockers

No accelerated Android runtime is available in this cloud checkout: adb lists no
devices; `emulator -accel-check` exits 3, CPU has no vmx/svm KVM capability.
**NOT RUN — ANDROID ENVIRONMENT UNAVAILABLE**, for API 26, 27 and 36.
Consequently there are no new actual application/worker PID values, latency,
combined PSS/RSS, native leak, low-memory or visual compatibility measurements.

On the selected emulator, record cold bind/open, first/subsequent render, page
navigation, invalid-open retirement and next-valid recovery using monotonic clocks.
Use the canonical fixture benchmark test (01/05/08/09/10/11), repeat cold and warm
runs, and collect Perfetto/profiler plus `adb shell dumpsys meminfo <app-pid>` and
`<worker-pid>` while both are alive, including during pixel transfer. Record both
processes and their sum; a post-retirement worker sample cannot represent peak RAM.
Compare with the direct adapter in a separate verified clean process using the
same fixture, dimensions and device. Do not compare isolated reduced resolution
with a larger direct bitmap or claim a latency/PSS improvement without measurements.
The current benchmark logs total open/two-render time, not separate latency phases.

The combined PDF-adapter coverage gate still requires real Android execution data.
JVM-only measurements of pure transport/state logic are not coverage certification
of Android binding, native worker, bitmap or session code. Verify separately that
the secondary **isolated process's** JaCoCo runtime data is collected: a runner dump
from the main process alone cannot establish worker coverage. Cross-process coverage
collection, if the existing runner omits it, remains an explicit infrastructure
blocker requiring reviewed implementation; no missing worker execution is called
passing coverage and no exclusion/threshold change is made.

The original direct Pdfium sentinels may continue failing on API 26/27. Existing CI
cannot automatically treat that platform result as production acceptance; retain
both results and obtain separate owner approval for an execution/reporting
arrangement before changing any CI policy. The production-isolation tests must
pass independently. Current quality-gate success is not claimed.

Future Phase I must retain one application graph/client engine, close route-owned
sessions off main, preserve existing displayed-page acknowledgements and avoid
initializing the Room/DataStore graph from the isolated service's Application
startup. No new navigation/host wiring is implemented. Physical tablet, UI/manual,
TalkBack, external providers and backup/restore acceptance remain separate.

Integration recommendation: **not ready for merge** until real API 26/27/36 recovery,
G regressions, complete coverage and combined-memory/performance evidence pass.


## Changed files in this correction

```text
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/aidl/com/abdev/partituraspdf/pdf/android/isolated/IPdfWorker.aidl
app/src/main/java/com/abdev/partituraspdf/app/di/AppGraphFactory.kt
app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/IsolatedPdfReadEngine.kt
app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/IsolatedPdfReadSession.kt
app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/IsolatedRenderLimits.kt
app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/PdfPixels.kt
app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/PdfRemoteErrors.kt
app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/PdfWorkerCall.kt
app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/PdfWorkerConnection.kt
app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/PdfWorkerLifecycle.kt
app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/PdfWorkerProtocol.kt
app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/PdfWorkerService.kt
app/src/test/java/com/abdev/partituraspdf/pdf/android/isolated/PdfPixelsTest.kt
app/src/test/java/com/abdev/partituraspdf/pdf/android/isolated/PdfRemoteErrorsTest.kt
app/src/test/java/com/abdev/partituraspdf/pdf/android/isolated/PdfWorkerCallTest.kt
app/src/test/java/com/abdev/partituraspdf/pdf/android/isolated/PdfWorkerLifecycleTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/pdf/android/isolated/IsolatedPdfReadEngineTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/pdf/android/isolated/PdfWorkerProtocolTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/reader/ReaderNativeIntegrationTest.kt
docs/integration/PDFIUM_ISOLATED_RENDERER_HANDOFF.md
```

No file is deleted. Original direct engine, fixture corpus, process experiments,
Room schemas, DataStore, backup rules, CI, thresholds and public contracts are unchanged.

## Hosted validation status

The managed proxy currently returns `Forbidden` for GitHub Actions API requests
(`gh run list --repo alexbonavila/PartiturasPDF --branch feat/reader-g --limit 1`).
Native Git read/write uses its existing authorized Git proxy; API access is separate.
After publication the requested existing workflow dispatch is attempted on the new
branch with `compatibility=true`. A forbidden dispatch/list operation supplies no
run URL and cannot certify a pending or completed run. The owner can dispatch:

```bash
gh workflow run ci.yml --repo alexbonavila/PartiturasPDF \
  --ref fix/pdfium-process-isolation -f compatibility=true
```

For the exact final published revision, all nine checks below remain **NOT VERIFIED**
until accessible hosted evidence exists:
`build`, `lint`, `unit-tests`, `localization`, `pdf-fixtures`,
`instrumented-api26`, `compatibility-api27`, `compatibility-api36`, `quality-gate`.
Local passes above do not stand in for these hosted jobs. Preserve actual JUnit XML,
logcat, current revision and process identity in the acceptance report.
