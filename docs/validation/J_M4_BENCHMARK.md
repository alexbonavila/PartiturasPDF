# Phase J M4 — opt-in instrumented performance and PSS observations

2026-10-23. Branch `test/mvp-stabilization-j`; implementation baseline
`3682b040a99a2c48a58b5412c38d65c7a05f2d5a`.
**Android runtime benchmark: NOT RUN.** This document describes implementation and
host validation, not measured latency, a memory budget or final M4 acceptance.
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

The owned AVD must explicitly contain one `hw.ramSize=2048` entry. `/proc/meminfo`
MemTotal must be 1536–2048 MiB (inclusive): a declared 0–25% reservation band for
AOSP/kernel allocations. Wrong/missing/duplicate configuration or incompatible
observed memory is BLOCKED. This corroborates a 2 GiB configuration; it does not
independently prove the exact reservation split or detect every small command-line
RAM override inside that band. The AVD configuration hash and stable boot identity
are checked during execution. No RAM value is silently corrected.

The shared home lock is held through cleanup. Read-only ActivityManager instrumentation
inspection must identify no active campaign; unknown dump formats BLOCK. Other
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
