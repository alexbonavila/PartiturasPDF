# Isolated PDF worker coverage feasibility — 2026-10-19

C04 investigation only. Baseline `adcca2bd07693e0d985eee1c611bc931be1dfed1`.
The handwritten worker/client sources, AIDL, service manifest and coverage instrumentation
are unchanged by this branch. No emulator was used and no worker execution or performance
measurement was attempted. **Authentic worker-process coverage: BLOCKED / NOT MEASURED**.
Missing process data is neither zero coverage nor a passing percentage.

## Source and process attribution

Paths are under `app/src/main/java/com/abdev/partituraspdf/pdf/android/isolated/`.

| Handwritten source | Production execution owner |
| --- | --- |
| IsolatedPdfReadEngine.kt | Application client; source handoff/open serialization |
| IsolatedPdfReadSession.kt | Application client; requests, bitmap ownership, retirement |
| PdfWorkerConnection.kt | Application client; binding, identity, Binder death, retained source |
| PdfWorkerLifecycle.kt | Client state machine; also pure JVM tests |
| PdfWorkerCall.kt | Client interrupt/cancellation boundary; also pure JVM tests |
| IsolatedRenderLimits.kt | Client-side limits; also pure JVM tests |
| PdfRemoteErrors.kt | Worker encoding and client decoding; JVM tests do not identify process execution |
| PdfWorkerProtocol.kt | Both processes; Bundle protocol validation |
| PdfPixels.kt | Worker writes rows; client reads rows; pure JVM wire tests exercise neither PID |
| PdfWorkerService.kt | Isolated worker; native engine, serialized work, self-retirement |

Generated `IPdfWorker` AIDL Stub/Proxy is not handwritten business logic. The underlying
`pdf/android/AndroidPdfReadEngine` executes inside the worker on this production path but
also in direct diagnostic tests; aggregate class probes cannot identify which process ran it.
Do not certify service execution from client/JVM/direct-renderer coverage.

## What the existing infrastructure proves

- `app/build.gradle.kts` enables `debug.enableAndroidTestCoverage` and JaCoCo 0.8.14.
- An actual debug build produced
  `app/build/intermediates/classes/debug/jacocoDebug/dirs/com/abdev/partituraspdf/pdf/android/isolated/PdfWorkerService.class`.
  `javap -private` confirms its generated `$jacocoInit(...)`. Instrumentation exists at the
  bytecode stage; this is **not** evidence that a worker loaded it or exported execution data.
- The service is nonexported, `android:isolatedProcess="true"`, `android:process=":pdf"`.
  `onCreate` checks a distinct UID. Identity reports PID/start time/epoch. `retireSelf` schedules
  `Process.killProcess(Process.myPid())`, independent of the native executor. onDestroy also
  retires; hard native crashes cannot be expected to run orderly shutdown hooks.
- `IPdfWorker.aidl` exposes identity/open/info/render/closeSession/oneway retire only. There is
  no coverage flush/snapshot command or output capability. Source review found no agent dump.
- `scripts/local_coverage.py:CoverageRun.android/retrieve_android` asks AndroidJUnitRunner to
  dump one coverage file in an app-owned per-run directory, then retrieves it using app run-as.
  That process-local dump cannot read a different isolated UID's in-memory probe arrays.
- `scripts/local-coverage.init.gradle` consumes genuine JVM `.exec` plus Android `.ec` inputs,
  preserving provenance validation. It cannot create absent process data. The controlled
  Pdfium runner preserves test/diagnostic evidence, but supplies no worker probe exporter.
- `doctor`: no acceleration; dedicated AVD UNPROVISIONED. No authentic PID/UID runtime evidence
  could be collected here. Hosted API access returned Forbidden; no worker artifact was obtained.

## Recommended separately approvable collection design (not implemented)

1. Use the existing debug Android coverage build and exact original pre-instrumentation class
   files; hash source, bytecode, APKs, JaCoCo version and test inventory before installation.
   Verify that the offline runtime actually initializes in the isolated process on API26.
2. In a **debug/test-only**, reviewed bridge, provide the worker one explicit bounded output
   capability (ParcelFileDescriptor/pipe, API26 compatible). The app owns the destination file;
   the worker must never gain general app-private path access, storage permission or a shared UID.
   Prefer a separate reviewed debug bridge over extending the release protocol accidentally.
3. Snapshot JaCoCo execution data from inside the identified worker PID using the bundled runtime
   API, not the application runner's agent. Bind the payload to run nonce, PID, UID, start time,
   worker epoch, request sequence, APK/class/source hashes and agent version. Bound payload length,
   checksum it, reject truncation and duplicate/late/mismatched identities. Never send probe bytes
   as an unbounded Binder Bundle. The exact maximum must be agreed from the actual instrumented
   class inventory, not silently chosen as a product limit.
4. Serialize snapshot with native work and normal retirement, with explicit descriptor ownership,
   IO cancellation and bounded waits. This must not delay emergency cancellation, quarantine or
   hard retirement, force worker reuse, or turn a failed PDF into success. A native crash may
   lose probes after the last acknowledged snapshot: mark that interval incomplete/BLOCKED,
   preserve raw failures, and do not infer its uncovered or covered paths.
5. Collect positive/negative baselines and recovery using genuine worker replacement on API26,
   then API27/36. Validate independent per-worker streams before merging compatible data with
   JVM/client data. Keep worker and client attribution in reports even if class IDs coincide.
6. Add fail-closed tests for missing, stale, wrong PID/UID/epoch, incompatible class IDs, source
   mismatch, duplicate, truncated and crash-before-flush data. Preserve standard JUnit inventory,
   controlled diagnostic assignments/classifications and current quality thresholds.
7. Confirm release packaging contains no bridge, agent-control surface or coverage export path.
   Review Binder access checks and descriptor confinement; keep service nonexported/isolated.

This design requires specific owner approval for any worker/AIDL/test bridge, Gradle or CI change.
No such implementation is authorized or included here. The minimal follow-up is a separately scoped
process-coverage collection task with those acceptance criteria; not a renderer rewrite or benchmark.
Full worker coverage remains unproven even when F/G/H logic coverage and functional CI pass.
