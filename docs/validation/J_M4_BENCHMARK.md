# Phase J M4 — opt-in instrumented performance and PSS observations

2026-10-23. Branch `test/mvp-stabilization-j`; implementation baseline
`3682b040a99a2c48a58b5412c38d65c7a05f2d5a`.
**Current status (2026-11-02): M4 emulator measurement scope CLOSED by owner approval; native benchmark
execution PASS**, from the owner-provided successful campaign recorded below.
Final performance acceptance remains pending; no quantitative budgets or physical
tablet calibration are approved. Earlier dated NOT RUN/FAIL/BLOCKED entries below
remain historical records of their original revisions and executions.
M3 evidence remains unchanged. J08 remains **DEFERRED TO PHASE R**; M5 is not started.

## Entry point and safety

One entry point: `scripts/j_m4_benchmark.py` (`list`, `doctor`, `run`). It reuses
`local_e2e` SDK/native-host selection, `doctor`, `load_device`, Runner identity checks,
bounded subprocess execution and the same exclusive runner-home lock. It never
provisions, starts, stops, wipes, adopts or edits an AVD. `list` is read-only ADB/AVD
discovery, not ownership approval. Physical serials cannot be selected for `run`.

`doctor` checks the selected home and, optionally, serial without device writes.
It requires the existing owned booted API26 emulator, the host's native compatible
image/ABI and available hardware acceleration, and rejects a foreign reserved port.
It records existing host APK hashes where available; readiness is not a claim about
installed binaries. `run` builds and verifies its own matching APK identities later.
Only an explicitly selected `--serial` matching that home is accepted. Keep the
approved emulator hardware accelerated; host `-accel-check` establishes availability,
not independent attestation of the running emulator's launch flags. No software
emulation fallback is implemented or accepted as a measured hardware baseline.

The owned AVD must explicitly contain exactly one RAM entry, either
`hw.ramSize=2048` or the equivalent `hw.ramSize=2G`. Both represent 2048 MiB;
other representations and duplicate entries remain rejected. No AVD is edited.
`/proc/meminfo`
MemTotal must be 1536–2048 MiB (inclusive): a declared 0–25% reservation band for
AOSP/kernel allocations. Wrong/missing/duplicate configuration or incompatible
observed memory is BLOCKED. This corroborates a 2 GiB configuration; it does not
independently prove the exact reservation split or detect every small command-line
RAM override inside that band. The AVD configuration hash and stable boot identity
are checked during execution. No RAM value is silently corrected.

The shared home lock is held through cleanup. Read-only
`dumpsys activity processes` inspection requires the exact API26
`ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)` header and a later
`Total persistent processes:` marker from the full dump. The unsupported API26
`dumpsys activity instrumentation` command is not used. An `Active instrumentation:`
section, `Instrumentation #` record or instrumentation record object blocks execution,
even if incomplete. Missing/duplicate headers, truncated/unrecognized output and
reported dump errors BLOCK; an empty grep result never establishes inactivity.
These structures were checked against Android 8.0 AOSP
[`ActivityManagerService.dumpProcessesLocked`](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/services/core/java/com/android/server/am/ActivityManagerService.java).
This remains a point-in-time check, not a guarantee against a later external start. Other
manual tools using different locks cannot be coordinated perfectly: the owner must
ensure there is no competing build/device campaign. A pre-existing `:pdf` worker
blocks instead of being adopted or killed. Identity is rechecked before mutations.

`run` performs `install -r` of the matching debug/test APKs, then app-specific
force-stop before each fresh instrumentation iteration and at final cleanup.
These operations require separate owner authorization for the exact disposable,
test-owned installation. It never clears data, uninstalls packages, changes radios/
locales/SAF grants, restores backups or kills arbitrary PIDs. Existing test-only
UUID storage and descriptors are cleaned through the normal integration harness.
A failing build/preflight does not authorize device cleanup. An uncertain install
reply is not retried. Boot/identity uncertainty prevents cleanup operations.

## Reuse, packaging and exact execution scope

`scripts/j-m4.init.gradle` adds only the local adapter source set. It changes no
module Gradle configuration, dependency, CI task, threshold or production source.
The existing `ReaderHostNativeIntegrationTest` exposes a non-JUnit helper reusing
its canonical copies, test-owned Room/DataStore, production controllers/route/Reader
UI, real isolated native engine, PixelCopy checks and joined cleanup. Its ordinary
methods and default 512-pixel test ceiling retain their existing behavior. Only M4
uses the same provisional render ceiling as production (4096 dimension / 4 Mi pixels).

The sole opt-in JUnit method is:

`com.abdev.partituraspdf.app.integration.M4BenchmarkTest.coldProcessAndTwoLocalDocuments`

The adapter is absent from ordinary instrumentation and application/release APKs;
its helper is test-only and has no `@Test` annotation. Standard inventory remains
445 methods, including 431 standard and 14 controlled diagnostics; none of those
suites is executed by M4. An opt-in APK additionally contains this one method.
The exact explicit selector invokes **only that method**. Smoke/full instrumentation,
JVM/Python suites, stress/endurance and controlled diagnostics are not launched by
`run`. Build tasks compile/package without running tests.

Default execution is **one warm-up + five measured fresh-process iterations**.
`--runs` accepts 1–10; there is exactly one warm-up, excluded from medians/maxima.
Every iteration runs vector/text **02** followed by raster/image **10**, each two
pages, copied from the immutable manifest and hashed before and after. Their hashes:

| ID | Path | SHA-256 |
| --- | --- | --- |
| 02 | `pdf/basic/02-two-pages.pdf` | `50f6447d3893b3822ed902fa370ecdfe2c116bbdaeb09afaa9e735cdc21fd512` |
| 10 | `pdf/complex/10-scanned-score.pdf` | `c049743af7075819d49550342bcc4a55b4f5dc29b504d1236b22102a24700dd0` |

The raster journey follows the vector journey in an already warmed main process;
this fixed order is not a controlled vector-versus-raster comparison. Each native
route owns its worker. Local capabilities are injected: no genuine external SAF,
DocumentsUI or network/provider performance acceptance is implied.

## Measurement points and limitations

- Host verifies app absence after force-stop; a new instrumentation process launches
  actual MainActivity. Device `Process.getStartElapsedRealtime` to observed Library
  pixels is **instrumented cold-process startup**, including runner/class-loading/
  Compose-rule overhead. It is not an uninstrumented launcher or release startup.
- A monotonic timestamp before the Activity rule launches to displayed Library
  semantics plus a successful window PixelCopy is the cold-Activity/visible metric.
  This observes valid presentation after the event; it is an upper bound, not exact
  first scan-out. `am start -W` is neither used nor substituted for UI readiness.
- Readiness requires authoritative host/preferences/Library readiness, an enabled
  folder-selection control and a real Settings/Back interaction returning to Library.
  The metric includes that interaction and harness observation cost.
- For each PDF: UI-open to real posted first page, native engine `open()` interval,
  and Next-click to page 2 presentation. The existing Reader VISIBLE/Presented checks
  and PixelCopy ink/paper assertions remain required. Native completion alone fails
  to establish presentation. All latency boundaries use device monotonic clocks.
- Test-owned bitmap recycling, descriptor opens/closes, route retirement, source-byte
  equality and joined host/store cleanup are asserted. The original production host
  is disposed before fixture injection; retained Activity/test objects still add
  memory overhead. There is no production factory or lifecycle override.
- Main-process idle/presented/post-close PSS is measured using Debug.MemoryInfo;
  worker checkpoints use bounded `dumpsys meminfo` after verified presentations.
  Each checkpoint records its own device-time window, separately from latency endpoints.
- A host thread additionally samples main/`:pdf` PSS at a nominal **250 ms** cadence
  (`--sample-ms` 100–1000). Actual period includes ADB/dumpsys overhead; query-window
  duration is reported. PIDs, exact process names and kernel start ticks are checked
  around queries. They are matched to instrumentation observations and worker epochs,
  including replacements at page navigation. Unattributed identities block success.
  Termination races discard a sample rather than assigning it to a replacement.
- Every measured fixture needs worker samples overlapping its opening/presentation
  and navigation windows. These may straddle boundaries and do not prove a sample
  inside the narrower blocking native-open call. Missing phase observations BLOCK;
  no artificial pause is inserted to manufacture samples. A very fast journey or
  slow host may therefore need review even though its JUnit assertions passed.
- Reported maxima include asynchronous and explicit checkpoint observations. Combined
  PSS sums are sequential paired observations, not simultaneous instantaneous peaks.
  Queries, PixelCopy, test stores and the JaCoCo-instrumented debug build perturb
  timing, GC and memory. No forced GC, arbitrary readiness sleep or assertion retry.
- p95 is **nearest rank**, so for five measured iterations it is the maximum. It is
  not a statistically robust population tail. There are no new pass/fail budgets.

An uninstrumented production cold-start metric, exact display timing, instantaneous
peak PSS, unbiased release performance and physical constrained-tablet calibration
remain unavailable from this harness. These limitations must accompany any results.
Do not mistake complete instrumented observations for full J03/J04 acceptance.

## Reports and classification

Each invocation of `run` creates a fresh confined directory:
`build/j-m4/reports/<timestamp-uuid>/`, retaining exactly:

| File | Schema / role |
| --- | --- |
| `summary.json` | schema 1; status/complete; Git/source digest/cleanliness; host/device/boot/RAM; fixture/APK identities and installed hash comparisons; iterations with warm-up marker, monotonic endpoints, observed worker identities, cleanup and individual samples; medians/p95, observed maxima, missing metrics/errors and artifact hashes |
| `summary.md` | Automatically generated review report: source, environment, RAM, APK hashes, per-scenario median/p95, PSS maxima, post-close memory/cleanup, overhead and acceptance limitations |
| `samples.csv` | Iteration/warm-up, observation kind/fixture, begin/end nanoseconds, PID/name/kernel start token/worker epoch, value and unit. Warm-up, checkpoints and partial samples remain identifiable |
| `execution.log` | Original Gradle/instrumentation output including genuine JUnit and the `m4` result bundle, PSS diagnostics, discarded-sample gaps and errors. Private local evidence: review before sharing |

There is no second JUnit parser: exact JUnit completion/identity/failure checks reuse
`local_test_evidence.parse_android`. The adapter returns its JSON via API26's
`Instrumentation.addResults`; it is not a forged JUnit event. No additional runner,
custom Android runner or diagnostic classification is introduced.

JSON is finalized atomically **last**, after Markdown/CSV; it is authoritative.
Partial progress remains BLOCKED/complete=false. Missing, malformed, mismatched,
nonmonotonic or incomplete evidence never becomes zero/PASS. Actual completed JUnit
assertion failures remain FAIL even with nonzero ADB exit; sampler/cleanup blockers
are retained without hiding a primary functional failure. Per-iteration instrumentation
has a 240 s host watchdog; individual memory commands have bounded timeouts and the
sampler is joined on exit. No automatic functional or installation replay.

Exit **0** means complete valid observations only; **1** actual functional failure;
**2** environment/evidence BLOCKED. Final performance acceptance remains with the owner
against reviewed criteria and physical hardware. No automatic acceptance report is
written to `docs/validation/` by the script, and no old report is overwritten.

## Owner's Mac procedure — future explicit device authorization

First review and publish this implementation separately; do not substitute an older
APK. Use the existing owned native accelerated API26 AVD whose configured RAM is
already 2048 MiB. Do not edit/reprovision another AVD to get past doctor. Start that
known AVD manually outside this tool, confirm its installation/data are test-owned,
and close concurrent testing/build tools. Use the same verified runner home.

```bash
python3 scripts/j_m4_benchmark.py list
python3 scripts/j_m4_benchmark.py doctor --home build/local-e2e --serial emulator-5580
# Only after owner authorization of that exact emulator and install/force-stop operations:
python3 scripts/j_m4_benchmark.py run --home build/local-e2e --serial emulator-5580 --runs 5
m4_exit=$?
printf 'M4 exit: %s\n' "$m4_exit"
```

`emulator-5580` is illustrative, never a default/adopted target: use the serial matching
that home's verified `device.json`. Supply `--sdk "$ANDROID_HOME"` if SDK discovery
needs it. The commands accept their options after the subcommand. `doctor` never
changes RAM, packages, settings or device state. Unknown ActivityManager/proc/PSS
formats, permissions, installed hashes or ownership return BLOCKED for review.
Do not silently weaken a check to run on a different API/device.

Open the printed run directory's `summary.md` and inspect JSON/CSV/raw log. Retain all
four files together; failures and incomplete runs are useful evidence. Nothing uploads
automatically. Share a reviewed/redacted copy with Codex rather than committing private
logs. No measured values or acceptance are asserted until an authorized Mac run exists.

## Implementation validation

Focused host regressions use synthetic fixtures and mocked commands only: selector/
foreign-device/concurrency/RAM blocking, PSS formats and process identity/replacement,
JUnit failure versus incomplete evidence, timestamp/nonce/fixture/cleanup integrity,
warm-up exclusion and p95, atomic reports and exact four-file output, normal process
exit races and opt-in inventory isolation. These tests are not device observations.

Observed host validation on the uncommitted implementation:

- `PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests -p test_j_m4_benchmark.py -v`: **17 PASS** (mocked/synthetic only).
- `bash ./gradlew -I scripts/j-m4.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-configuration-cache --console=plain`: **PASS**, JDK25/SDK37.
- `bash ./gradlew :app:assembleDebugAndroidTest --offline --no-configuration-cache --console=plain`: **PASS**. DEX inspection confirms the adapter exists only in the opt-in test APK, and is absent from the ordinary test APK and application APK. Its source is registered only in the opt-in androidTest source set, never release source sets; a fresh release build was not run.
- `python3 scripts/validate_localization.py`: **PASS**.
- Both selected canonical fixture hashes: **PASS**. Existing 27 Reader JUnit method names/order: unchanged.
- `git diff --check` and new-file trailing-whitespace checks: **PASS**.

An initial direct unittest module invocation failed to import the existing sibling
`test_local_regression` helper; the supported discovery invocation above resolves
that import and executed all seventeen tests. This was a host invocation error,
not Android execution.

No full JVM/Python/Android campaign or emulator operation was executed. M4
performance and memory, runtime cleanup, and API26 compatibility: **NOT RUN**.

## 2026-11-02 — sequential fixture ownership correction

The owner reports that execution `20261023T125516Z-f2174104a00241b092351e5e16bc2aa5`
on revision `76d8aca5292d7189263872624a2703a7697740e6` failed while mounting
fixture 10, after fixture 02 completed its presentation/navigation/cleanup assertions.
That run remains **FAIL**, with zero complete measured iterations; acceptance remains
pending. No raw archive was supplied with this correction request, so those runtime
details are owner-reported, not an independent artifact review.

Source inspection and a focused JVM reproduction confirm that `dispose()` closes an
AppHostViewModel but does not evict it from its ViewModelStore. Reusing the Activity's
fixed `i05.test.host` key therefore returned a Closed host on the second mount.

Only benchmark mounts now use a fresh dedicated ViewModelStore and a local factory.
Ordinary Reader tests retain the Activity-owned key/factory and their recreation
semantics. Benchmark cleanup detaches the presentation, joins host disposal, removes
its Activity lifecycle observer, clears the dedicated store, deletes its UUID-owned
storage and releases the harness's host/session references. The next fixture cannot
mount over an uncleared store. Both fixture mounts assert actual factory creation;
each completed cleanup asserts that the owning references were released. This
prevents Activity/store/observer retention of the earlier fixture rather than merely
using a different key and leaving disposed hosts cached. No forced GC is introduced;
ordinary garbage collection and the documented instrumentation overhead remain part
of observed PSS. No production ownership behavior or report schema changed.

Validation on this uncommitted correction:

- Focused Python discovery for `test_j_m4_benchmark.py`: **23 PASS**, including a
  guard for dedicated benchmark ownership, joined disposal and observer removal.
- `:app:testDebugUnitTest --tests com.abdev.partituraspdf.app.host.AppHostViewModelTest`:
  **20 executed, 20 PASS, zero failures/errors/skips**, including cached-disposed-host
  reproduction and two sequential fresh-store sessions with exactly-once closure and
  zero remaining Settings collectors.
- In the same opt-in Gradle invocation, Android instrumentation Kotlin compilation
  and `:app:assembleDebugAndroidTest` packaging succeeded. The **overall command
  failed** at the unchanged `:app:verifyContractCoverage` finalizer: the filtered class
  produced 10% A1 lines / 4% branches against the 90%/80% whole-scope gate. This is
  not a passing coverage campaign, nor a replacement for previous whole-suite evidence.
- Whitespace/scope checks: **PASS**. Existing 27 Reader Android test bodies unchanged.

No emulator operations, native rerun, full JVM/Android campaign, commits or publication
were performed for this correction. A new single authorized native M4 attempt must
demonstrate completion of both fixtures; runtime cleanup and measurements remain
**NOT RUN** on the corrected source until then.

## 2026-11-02 — sampler process-exit evidence correction

The owner reports run `20261102T081409Z-df3366fcb40542ab846450c329aaba5b`
at revision `fd303fcc1801f6d034c363ab5cd4fb88ec01bc18`: the sole benchmark JUnit
method passed, both fixtures completed, and the payload was COMPLETE/cleanup PASS.
The overall run remained **BLOCKED**, with zero accepted iterations and 13 partial
PSS observations, after a failed read of instrumentation PID 4161's cmdline. Host
cleanup passed; M4 acceptance remains pending. These details are owner-reported;
the raw failed-read stderr and follow-up pidof response were not supplied for this
correction. No historical report or classification is changed.

The previous code used `checked()` for procfs reads, losing the distinction between
ADB exit and remote cat exit in its control flow, then accepted disappearance only
for a silent pidof exit 1 or a different single PID. It also stopped the sampler only
after result parsing. Source inspection confirms these limitations; the exact native
fallback response that caused the supplied run's BLOCKED status is not independently
established from the summary alone.

Procfs reads now use a fresh nonce-framed remote shell status. ADB nonzero exit,
timeout, missing/ambiguous framing or malformed identities remain BLOCKED, even after
instrumentation completes. A remote read failure or empty cmdline is discarded only
after one independent successful shell probe establishes that procfs is accessible
and the original PID is absent, or validates its PID/start-token stat in a terminal
zombie/dead state. A live, unreadable or unverifiable PID remains BLOCKED. This is a
bounded independent termination check, not a retry of the failed read; it records an
explicit sample-gap reason and never substitutes zero or replacement-process PSS.
PID/start-token comparisons around memory queries and all phase-sampling requirements
remain unchanged. Unusable missing phase observations still block a complete report.

Instrumentation return now requests sampler stop before payload parsing. New process
queries are not started after that signal; in-flight reads must still pass the same
verification. `finish()` joins the thread and preserves any transport/evidence errors;
it does not suppress them merely because the test finished. Report schema, warm-up
exclusion, statistics, worker attribution and FAIL/BLOCKED rules are unchanged.

Focused validation: **32 Python tests PASS**, including exit during cmdline/stat,
positive absence/zombie proof, live-PID and failed-proof rejection, ADB failures and
unframed output, PID replacement/start-token reuse, and shutdown with an in-flight
verified exit. Whitespace/scope checks: **PASS**. No Android/Gradle execution or device
operation was performed. The clean checkout was fast-forwarded to the owner's
execution revision before editing; its pre-existing AGP update is not part of this fix.
Real sampler compatibility and M4 observations remain **NOT RUN** on the correction;
one new authorized native attempt is required after review/publication.

## 2026-11-02 — framed PID discovery compatibility correction

The owner reports run `20261102T083033Z-e33d4afab2f34c218db59465f2fad350`
at baseline `26476eab7089c66078b892e7bffc2aa5f154c9a5`: preflight/APK checks,
both JUnit invocations and both PDF journeys passed. One warm-up was accepted, but
zero measured iterations were accepted; the campaign remained **BLOCKED** on empty
unframed worker pidof output. Host cleanup passed. This remains owner-provided evidence,
not a fresh execution or independent raw-artifact review; historical results are unchanged.

The remaining discovery path still used raw `adb shell pidof`, accepting absence only
when the combined command returned exit 1 without output/errors. Unlike procfs reads,
it did not separate a remote pidof status from an ADB transport result. Discovery now
reuses the same nonce-framed `process_shell()` wrapper, with strict stderr rejection:

- Remote 0 plus exactly one positive decimal PID proceeds to unchanged cmdline/stat
  identity and kernel-start-token verification.
- Remote 1 plus empty output and no stderr establishes absence, returning no sample.
- ADB failure, missing/duplicate/malformed framing, multiple/invalid PIDs, unexpected
  status, error text or contradictory output remains BLOCKED.

The allowed process targets remain the application and its `:pdf` worker. Procfs exit
proof, sampler stop/join behavior, explicit gap records, phase-sampling requirements,
statistics, timeouts and report format are unchanged. No zero or replacement memory
observations are fabricated. This addresses the demonstrated unframed discovery path;
the summary alone cannot prove which underlying status caused the original empty
output. No other implementation issue is demonstrated by the supplied evidence.

Focused Python validation: **36 tests PASS**, including framed success/absence,
contradictory status/output, stderr, transport errors and invalid framing, plus retained
procfs race and worker-attribution regressions. Scope/whitespace: **PASS**. No Gradle,
Android instrumentation, emulator operations or full regression suite was run.
Runtime compatibility remains **NOT RUN** on this correction. After owner review and
publication, one authorized native M4 rerun must establish real sampler completeness;
benchmark completion and final M4 acceptance are still pending.

## 2026-11-02 — successful native API26 benchmark execution

**M4 emulator measurement scope: CLOSED by owner approval (2026-11-02). Benchmark execution: PASS. Evidence
complete: true. Final performance acceptance: PENDING.**

Source revision: `a782903fef1a5cc8ab3404d8c27fbc8cd45d16e3`, branch
`test/mvp-stabilization-j`. Report:
`20261102T084249Z-a38be721e7404c949a05e6c30c79b64d` under
`build/j-m4/reports/`. The following are verified owner-provided results, not a new
Codex execution or an independent audit of raw report files/APK binaries. The raw
four-file report was not attached to this documentation task; no hashes or additional
measurements are inferred.

The owner reports a native API26 emulator with 2048 MiB configured RAM, a clean
execution source tree and matching installed/host APK hashes. The campaign completed
one warm-up plus five measured fresh-process iterations, with **six successful Android
JUnit executions** of `M4BenchmarkTest.coldProcessAndTwoLocalDocuments`. Both canonical
fixtures (02 vector/text and 10 raster/image, identities above) completed in every
execution. There were **60 measured PSS samples, excluding warm-up**. Host cleanup
was **PASS**. All **12 PDF journeys** (two warm-up and ten measured) reported successful
bitmap recycling, descriptor closure and unchanged PDF sources.

### Observed latency — measured iterations only

All values are milliseconds; median and nearest-rank p95 use five measured iterations,
excluding the warm-up. At this sample count, p95 is the observed maximum, not a robust
population-tail estimate.

| Metric | Median ms | p95 ms |
| --- | ---: | ---: |
| Instrumented cold process to visible Library | 3065.160 | 3334.925 |
| Library interaction ready | 2215.309 | 2401.245 |
| PDF 02 native open | 425.719 | 488.749 |
| PDF 02 open to visible | 1942.948 | 2000.258 |
| PDF 02 next page visible | 1528.746 | 1604.809 |
| PDF 10 native open | 331.855 | 406.599 |
| PDF 10 open to visible | 1774.282 | 1870.339 |
| PDF 10 next page visible | 1489.296 | 1513.871 |

The cold-process metric begins at process start; Library interaction-ready begins
at the pre-Activity-rule timestamp. Their different origins explain why the reported
ready duration can be smaller; they must not be treated as successive intervals
with the same origin. The separate cold-Activity-to-visible metric is not supplied
in this summary and is not invented here. Presentation endpoints, PixelCopy/harness
overhead and injected local-capability limitations remain as defined above.

### Observed PSS maxima — measured iterations only

| Observation | Maximum KiB |
| --- | ---: |
| Main process | 122898 |
| Isolated PDF worker | 29664 |
| Combined observation | 145677 |

These are **maximum observed PSS**, not absolute memory peaks. Main/worker maxima
need not occur in the same observation; the combined value is its own sequential
paired observation, not the sum of the two independent maxima. The sampling and
debug/instrumentation perturbations described above still apply.

### Scoped closure and outstanding acceptance

The owner approved closure of the minimal automated API26 emulator measurement
scope on 2026-11-02. **M4 is CLOSED only within that scope.** Execution completeness
PASS does **not** establish compliance
with quantitative latency/memory budgets: those budgets and physical tablet
calibration are not yet approved. Physical Android tablet performance remains
unverified by this campaign; no release-build or broad device performance acceptance
is inferred. No new hosted CI evidence is asserted.

Long-duration memory stability, cumulative retention and endurance are **M5 work**,
not proven by these six short executions. M5 has not started. M4 formal performance
acceptance and Phase J completion remain pending; J08 remains **DEFERRED TO PHASE R**.
All earlier FAIL/BLOCKED campaigns and their corrective-action history above retain
their original classifications. No tests or devices were operated for this update.
