# M3-C3 — Isolated-worker coverage preparation

Date: 2026-10-23. Branch: `test/mvp-stabilization-j`.
Implementation baseline: `25786f35d560a1d843ea25eb87022149d25e99b6`.
This records implementation and pre-publication host validation, not Android execution evidence.

## Problem and collection design

The retained authenticated report measured B5 at **531/658 lines (80.70%)** and
**295/452 branches (65.27%)**. All 88 lines and 86 branches in `PdfWorkerService.kt`
were missing. AndroidJUnitRunner exports its application-process runtime; that
cannot establish execution in the separate isolated UID. Worker termination does
not flush that process into the application's dump. Historical reports remain unchanged.

The new **debug-only** `PdfCoverageWorkerService` subclasses the actual worker.
The sole main-source change is `class` → `open class` on `PdfWorkerService`.
No native method, dispatch, cancellation, close, AIDL, isolation or retirement
implementation changes. In particular, retirement never waits for coverage.
The existing 19 engine tests and five protocol tests remain unchanged.

The debug manifest adds a non-exported, isolated `:pdfCoverage` service. Its Binder
passes native transactions through unchanged. One reserved transaction accepts
only the app UID, a 32-hex capture nonce and an explicitly supplied descriptor.
The descriptor must identify a fresh, empty regular file at position zero (not a
pipe, device, directory or arbitrary path). No file paths, permissions or network
capabilities are granted to the isolated UID. Export is capped at 4 MiB per worker.
The test owns the destination and both sides close their descriptor copies.

The worker calls the existing instrumented APK's public JaCoCo runtime by reflection:
`RT.getAgent()`, `IAgent.setSessionId()` and `IAgent.getExecutionData(false)`.
These APIs were found in the actual debug APK. No dependency was added. If export
is unavailable or incompatible, collection cannot pass; no synthetic fallback exists.

After the behavioral assertions and **before** oneway retirement, each test-owned
worker writes its actual execution bytes and returns PID, isolated UID, application
caller PID/UID, process start time, real worker epoch, nonce, byte count, SHA-256 and
JaCoCo session identity. The application checks the acknowledgment against the
previous real Binder identity and its own PID/UID, then finalizes matching JSON.
The session is `worker:<nonce>:<pid>:<uid>:<epoch>`.

The existing collector requests these exports only for its explicit coverage run.
Ordinary instrumentation still executes all functional assertions without export.
The collector reads only paired UUID `.ec`/`.json` files inside its newly owned
`files/local-coverage-<run>/workers` directory. It requires **17 distinct worker
snapshots**, attributed to all six worker tests, with their exact per-test counts.
Missing, duplicate, unexpected, oversized, symlinked, wrongly attributed or
hash/session/process-incompatible inputs are rejected. Completed failing runs
retain available dumps and metadata without rerunning any test.

The existing Java validator authenticates every scoped class ID and probe count,
including generated AIDL classes, against the captured compiled inventories. The
new Python check additionally requires real probes for both the native service and
the debug exporter. The existing C05 validation repeats the worker checks before
allowing the same Gradle report tasks to merge execution streams. Application and
worker inputs retain separate paths and provenance. This is source/APK-bound
recorded process evidence, **not cryptographic OS or installed-binary attestation**.

## Behavioral additions

All identities below use package `com.abdev.partituraspdf.pdf.android.isolated`.
All 13 methods compile to JUnit 4 `void` methods.

| Class / method | Observable contract / branches targeted |
| --- | --- |
| `PdfWorkerCoverageTest.wrongTokensAndPageBoundsDoNotPoisonHealthyWorker` | Actual identity/open/info/render/close; wrong epoch and session independently; negative and upper-bound page requests; successful close followed by stale info/render/close; fresh native session on the still-healthy worker. |
| `PdfWorkerCoverageTest.duplicateSessionIsProgrammingDefectAndQuarantinesWorker` | Duplicate open is an explicit `IllegalStateException` defect, not an invalid-PDF error; all subsequent operations reject the quarantined worker. |
| `PdfWorkerCoverageTest.invalidOpenBoundsAreDefectsRatherThanInvalidDocuments` | Empty/overlong token, lower/upper dimension and pixel violations; unadopted descriptor cleanup; defect/quarantine behavior. Six workers. |
| `PdfWorkerCoverageTest.invalidRenderParametersAreDefectsAndOversizeIsTypedRecoverableFailure` | Oversized bounded requests remain typed and permit a later valid render; empty/overlong request, zero dimensions and negative generation quarantine as programming defects. Six workers. |
| `PdfWorkerCoverageTest.invalidNativeOpenQuarantinesBeforeAnyLaterNativeCall` | Canonical corrupted fixture 15 fails natively, has no programming-defect marker, and subsequent open/info cannot enter native work. The same existing invalid-PDF error set (INVALID_DOCUMENT, DOCUMENT_FORMAT, PROVIDER_IO_FAILURE) preserves platform parser classification. |
| `PdfWorkerCoverageTest.brokenPipeIsTypedIoFailureAndNewWorkerRecovers` | A genuinely closed pipe reader causes worker write I/O failure, typed poisoned provider error and later rejection; a new isolated epoch opens/renders/closes successfully. Two workers. |
| `PdfWorkerCallbackTest.disconnectedCallbackRetiresOnlyOwnedWorkerAndUnbindsExactlyOnce` | Injected framework disconnection around a real worker; actual Binder death and exactly-once unbind. |
| `PdfWorkerCallbackTest.bindingDiedCallbackAndRepeatedClosurePreserveExactlyOnceUnbind` | Binding-death/disconnection/repeated close sequence; no repeated unbind. |
| `PdfWorkerCallbackTest.replacementCallbackNeverRetiresOrCallsIncomingForeignBinder` | A late replacement callback retires the previously owned real worker; incoming Binder is never called. |
| `PdfWorkerCallbackTest.nullAndLateBindingCannotAdoptAnUnidentifiedWorker` | Null binding followed by late unidentified Binder cannot become a usable native connection. |
| `PdfWorkerReplyTest.staleOpenReplyReleasesAdoptedBackingAndPropagatesDefect` | Real native open, then deliberately corrupted reply epoch; exact protocol error propagates and adopted source closes once. |
| `PdfWorkerReplyTest.staleInfoReplyRetiresSessionRatherThanReusingNativeOwner` | Real native info, then deliberately corrupted epoch; session retires and backing closes once. |
| `PdfWorkerReplyTest.staleRenderReplyCannotPublishBitmapAndPoisonedReplyRetiresOwner` | Real native pixel transfer, then injected wrong request token or poisoned typed reply; no successful lease escapes, backing closes once, later requests return SESSION_CLOSED. |

Callback/reply injection is explicitly **simulated failure evidence around real
Binder/native execution**, not proof of OS-induced death, corruption or genuine
provider failure. The broken-pipe test is an actual transport failure. No source
PDF asset is changed: tests copy and verify canonical hashes and delete only their
own temporary files. Client tests assert exactly-once source release; native-worker
tests close descriptors, retire only their worker and await its death before unbind.
No sleeps, unbounded retries, process-ID killing or fabricated successful renders.

The existing `AndroidPdfReadEngineTest.pageCountReadFailureIsTypedAndClosesBothOwners`
already covers I/O, permission and illegal-state metadata failures. Existing
`AndroidPdfCoverageEdgeCasesTest`, `AndroidPdfCoverageFinalGapsTest`, concurrency and
`PdfWorkerCallTest` seams already cover native/error/cleanup/cancellation cases.
Those tests are reused, not duplicated. No artificial resource exhaustion is added.

## Inventory, denominator and acceptance

- Source inventory: **445** Android methods = **431 standard + 14 diagnostics**.
- The original **19 + 5 = 24** isolation methods remain standard; 13 new methods
  extend them. The **29 controlled diagnostic invocations remain NOT RUN**.
- Expected worker exports: **17** from the six direct-worker methods.
- JVM inventory: **631**, unchanged.
- Before/after independent JaCoCo compiled-class inventory comparison found identical
  B5 class names, executable line sets and branch totals: **658 lines / 452 branches**.
- Debug helper classes have `production=false`; they never enter handwritten B5,
  A1 or C05 denominators. No source exclusion, threshold or Gradle scope changed.
- Actual new Android worker export, merged B5/C05 metrics and gates: **NOT RUN**.
- A1's existing JVM finalizer: **PASS**. New C05/B5 real combined gates remain pending.
- At least 62 additional lines and 67 branches are needed relative to the baseline.
  These are required gains, **not measured gains**. No new percentage is asserted.

Residual paths must be read from the real capture's `coverage/pdf.xml` source lines
and branch counters. Hard-kill/onDestroy/retire probes after the final pre-retirement
snapshot cannot be claimed. Native Binder/platform races, reliable-pipe creation
failure and bitmap allocation OOM must not be manufactured by unsafe descriptor or
memory exhaustion. The nullable cancellation-cause cleanup branch in `PdfWorkerCall`
may not be reachable through ordinary coroutine Job cancellation; no fake execution
is introduced. Exact remaining missed line numbers cannot be established without
that real report. Failure to reach either unchanged threshold remains **FAIL**;
missing/incompatible inputs remain unverified and cannot certify coverage.

## Local validation (no device execution)

Commands run using the existing JDK25 / SDK37 / Gradle9.8 / JaCoCo0.8.14 toolchain:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug :app:testDebugUnitTest --offline --console=plain
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

- Debug/release builds, instrumentation compilation, Lint: PASS.
- Full JVM suite: **631 PASS**, zero failures; existing A1 finalizer PASS.
- Python infrastructure: **448 PASS**, including worker identity/session/hash,
  missing/duplicate/symlink input, exact snapshot multiplicity, command opt-in,
  retrieval confinement and invalid-bytecode rejection tests. Synthetic host-policy
  fixtures do not establish coverage.
- Localization: PASS. Immutable canonical fixtures: **15 PASS**.
- After later test/debug-only refinements, debug/instrumentation compilation and
  Lint were rerun; JVM application behavior and tests remained unchanged. The final
  focused coverage-policy suite also passed **63 tests**.
- Compiled manifests/APKs: debug service non-exported and isolated; exporter absent
  from release manifest and DEX; all new Android test classes absent from application
  APKs. JaCoCo line/branch model comparison and `javap` void-signature inspection PASS.
- Initial sandbox Gradle startup was blocked by local socket/network-interface
  restrictions; the authorized host compilation succeeded outside that sandbox.
- Intermediate Python runs exposed the obsolete original-24 namespace assertion and
  an over-strict synthetic retrieval fixture setup. Both were corrected; the final
  complete suite passed. Final source review also corrected the new reply-fault
  helper to preserve generated AIDL's typed-object presence flag (distinct from
  the custom snapshot transaction's Bundle encoding); compilation/Lint were rerun. No failing application behavior was changed.
- Android execution/real worker `.ec` export: **NOT RUN**. No emulator was operated.

## One controlled owner capture after review

Use the same existing **verified dedicated API26** native-accelerated AVD and runner
home that produced the earlier retained coverage capture. Do not adopt Tablet2 or
another AVD merely because ADB sees it. Existing ownership/image/boot identity checks
must pass. Use a reviewed/committed source tree, matching toolchain and no concurrent
build/edit. No provisioning, wipe, uninstall, SAF-grant injection or data clearing.

For the default accepted runner home, on macOS:

```bash
python3 scripts/local_e2e.py doctor
python3 scripts/local_e2e.py run --suite coverage
coverage_exit=$?
printf 'Coverage exit: %s\n' "$coverage_exit"
```

If the accepted home is non-default, supply the **same** `--home <accepted-relative-home>`
(and existing `--sdk` if used) before both subcommands. The home must be confined to
the checkout without symlink ancestors; do not migrate or create an AVD to bypass this.
The owner must authorize that specific test-owned emulator before running.

The collector installs its matching debug/test APKs, runs the 631 JVM and exactly
431 ordinary Android methods, obtains app plus 17 worker dumps, validates identities,
then applies unchanged A1/B5/C05 gates. Exit **0 PASS / 1 FAIL / 2 BLOCKED**.
Do not automatically retry functional failures or run repeated coverage campaigns.

Retain the entire new `build/local-e2e/reports/<timestamp-UUID>/` directory, especially
`summary.json`, `coverage/capture.json`, `classes.json`, retained classes/APKs,
`instrumentation.log`, both JUnit directories, `inputs/android/workers/*`,
`coverage/summary.json`, persistence provenance and `coverage/pdf.xml`/HTML.
Review raw inventories and separate worker provenance before accepting any result.
Do not modify, merge or relabel older BLOCKED/post-hoc evidence. Publication follows the separately authorized final review; the real capture remains pending.


## Final publication review

The direct-worker cleanup helper now waits solely for its Binder death recipient;
`onServiceDisconnected` cannot authorize cleanup as if death had been proven.
The Python test file's direct-execution entry point was moved after all test classes
so direct execution includes the worker-evidence regressions as discovery already did.
Neither correction changes the 13 Android methods or 17-worker expectation.
Previously collected full-suite evidence is retained as pre-review validation, not
relabeled as a new execution. Affected host suites and instrumentation compilation
were rerun for these corrections: **63 coverage-runner, 14 persistence-policy and
18 inventory/diagnostic host tests PASS**, plus instrumentation APK compilation PASS.
Read-only artifact review reconfirmed all 13 void signatures, release isolation and
the unchanged 658-line/452-branch compiled B5 model. No Android runtime execution
was performed.

## Post-execution result — 2026-10-23

The owner reports the new API26/arm64-v8a campaign at
`b2cff985ee3d9204c3d46728636afdb329d1cc22` completed **PASS / exit 0**:
**631/631 JVM and 431/431 standard Android tests PASS** (1,062 tests),
**17 isolated-worker snapshots VALIDATED**, coverage VERIFIED, zero failures/blockers
and cleanup PASS. The fourteen fresh-process diagnostics remain excluded and NOT RUN
in this campaign; snapshots are not additional tests.

Reported scoped gates: A1 230/231 lines (99.57%), 222/224 branches (99.11%);
**B5 623/658 lines (94.68%), 375/452 branches (82.96%)**; C05 metadata 207/210
(98.57%), 167/177 (94.35%); preferences 35/35 and 12/12 (both 100%). All existing
90% line / 80% branch gates passed. B5's 658/452 denominator, including the worker,
is unchanged. No full source coverage or post-retirement probes are claimed.

Original directory:
`build/local-e2e/reports/20261023T112313Z-ebebb92a62a248afa70d6f412f03c464/`.
Archive: `20261023T112313Z-ebebb92a62a248afa70d6f412f03c464.zip`.
Supplied SHA-256: `612458cb1166a347782d7ed7dc4082b965ce1fe74a215481d09143e80ca8b47b`.
The archive/run directory is unavailable here. These are **owner-provided verified
findings**, not newly parsed or rehashed artifacts; this task performed no test rerun.
Recorded worker provenance retains the limitations above and is not cryptographic
OS or installed-binary attestation.

The preceding NOT RUN/pending statements preserve implementation-time history.
The [M3 technical closeout](J_M3_CLOSEOUT.md) records the subsequent selected-scope
completion and remaining limitations. Formal owner acceptance, Phase J integration
and M4–M7 remain separate; M4 is planned only. **J08 stays DEFERRED TO PHASE R.**
