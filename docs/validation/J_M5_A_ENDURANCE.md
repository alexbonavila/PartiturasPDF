# Phase J M5-A — bounded native endurance

2026-11-02. Implementation baseline `848682e5ec679d5ae6870100bad516404870a7ad`,
branch `test/mvp-stabilization-j`. **Current status (2026-11-02): authorized
three-cycle API26 campaign PASS; bounded M5-A scope CLOSED by owner approval.** Prolonged memory
stability and leak-free operation remain unproven; M5-B requires separate approval.
Earlier NOT RUN statements below describe implementation-time evidence, not this
subsequent campaign. M4 closure and all historical evidence remain unchanged.

## Scope and reuse

`python3 scripts/j_m5_endurance.py doctor|run` is one opt-in entry point, reusing
M4 Probe, ownership/API26/native-host/2048 MiB preflight, shared runner lock,
nonce-framed PID/procfs checks, asynchronous Sampler, APK inspection/hash checks,
exact JUnit parser, source attribution and atomic evidence helpers. It adds no
Android runner, production behavior, dependencies, CI gates or memory thresholds.
`scripts/j-m5.init.gradle` registers only an opt-in androidTest source directory.
Ordinary instrumentation does not contain M5EnduranceTest. M4 defaults and all
27 ordinary Reader test bodies remain unchanged.

The sole executed method is
`com.abdev.partituraspdf.app.integration.M5EnduranceTest.boundedNativeCycles`.
It reuses ReaderHostNativeIntegrationTest's real MainActivity, controllers, dedicated
fixture ViewModel stores, isolated native PDF engine, posted-pixel checks and joined
cleanup. Canonical local capabilities are injected; this is not external SAF evidence.

One invocation keeps a single application instrumentation process alive across all
cycles; it does not restart the process between cycles. Each cycle opens, navigates
and closes 02 (vector/text), 10 (scanned score), then 05 (100-page document). Fixture
05 tests opening a larger document and presenting its first two pages, not traversal
or stress rendering of all 100 pages. All canonical files are hash-verified and copied
into UUID-owned storage. No source fixture changes are allowed.

Default **3 cycles**, minimum 2, maximum 10: 9 default / 30 maximum PDF journeys.
There is no M4 warm-up/distribution or latency benchmark. The reused payload retains
monotonic endpoints solely for PSS attribution and completeness checks; no latency
medians/p95 or performance budgets are computed. Every journey checks actual first/
next page pixels, route retirement, bitmap recycling, read-descriptor opens/closes,
source-byte equality and joined host/storage cleanup. Dedicated stores and lifecycle
observers are released before the next journey. No forced GC or readiness sleeps.

## Memory and classifications

M4's nominal 250 ms sampler observes main and worker PSS with kernel start tokens;
worker epochs and per-journey cycle identities are retained in JSON. Every journey
requires actual worker samples overlapping opening/presentation and navigation.
Missing observations, process identity ambiguity, malformed/incomplete JUnit, lost
transport, uncertain termination, source/APK/boot/config drift or cleanup uncertainty
are BLOCKED. A verified exited process contributes a logged gap, never a zero.

Within each fixture, post-close main-process PSS is compared across cycles. The report
lists every value, early/late half medians and net change. Strict increases at every
successive observation set `sustained_increase=true` and `review_required=true`.
This is a descriptive trend flag, not an approved memory budget or automatic functional
failure. Flat/decreasing PSS does not prove leak freedom. Android GC, instrumentation,
debug probes, test stores, observation overhead and fixed fixture order affect results.
Sampled maxima are not absolute peaks; no physical tablet acceptance is inferred.
Read-descriptor counters do not attest every OS descriptor in the entire app; worker
termination and route/bitmap ownership checks supply separate cleanup evidence.

Exit 0 / PASS means the bounded campaign produced complete validated observations
and passed its functional/resource assertions; owner review is still required,
especially for growth flags. Exit 1 / FAIL means a genuine completed JUnit assertion
failed. Exit 2 / BLOCKED means environment/evidence incomplete. No functional retries,
missing-sample substitution or relaxed assertions are used. Existing reports are untouched.

Instrumentation has a finite host watchdog of `120 + 180 * cycles` seconds (660 s
by default, 1920 s maximum); existing UI and shell timeouts remain bounded. On return,
sampling stops and joins. On failure, partial samples/raw output remain in a fresh
`build/j-m5/reports/<UTC timestamp-uuid>/`: `summary.json`, `summary.md`, `samples.csv`,
`execution.log`. JSON is atomically finalized last with artifact hashes. Raw JUnit and
payload remain in the log; this is not a new JUnit reporting framework. Review private
paths/diagnostics before sharing; nothing uploads automatically.

## Owner execution — separate exact-device authorization required

Use only the existing verified dedicated API26 native accelerated emulator with 2 GiB
RAM and a disposable test installation; the tool never provisions, adopts, launches,
reconfigures, wipes or stops an AVD. Close concurrent build/device campaigns. The shared
home lock cannot coordinate unrelated tools. `doctor` is read-only. `run` builds matching
APKs, installs with `-r`, force-stops only the test application before the campaign and
at final cleanup, and deletes only its own UUID fixtures through the test harness.
It never clears app data, uninstalls, revokes grants, changes settings, kills workers,
performs backup/D2D or injects recovery failures. Identity/config/boot checks precede
installation; uncertain identity prevents final stop operations.

After review and transfer/publication of this exact source revision, and explicit
owner authorization of the selected device/install/force-stop operations:

```bash
python3 scripts/j_m5_endurance.py doctor --home build/local-e2e --serial emulator-5580
python3 scripts/j_m5_endurance.py run --home build/local-e2e --serial emulator-5580 --cycles 3
m5_exit=$?
printf 'M5-A exit: %s\n' "$m5_exit"
```

The serial is illustrative: it must match that home's owned device manifest. Supply
`--sdk "$ANDROID_HOME"` if necessary. Keep all four report files together, including
BLOCKED/FAIL evidence. Do not silently switch devices or rerun failed assertions.

## Implementation validation

Focused Python command:
`PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests -p 'test_j_m[45]*.py' -v`:
**44 PASS** (36 reused M4 + 8 M5, mocked/synthetic only). Coverage includes sequence/
JUnit identity, cycle bounds, preflight blocking without device writes, missing phase
samples, identity mismatch, flat versus increasing trends and opt-in source inventory.

Instrumentation compilation command:
`bash ./gradlew -I scripts/j-m5.init.gradle :app:assembleDebugAndroidTest --no-configuration-cache --console=plain`.
The initial offline attempt could not resolve the baseline AGP 9.5.0; an online
attempt uses that same approved version, without build/dependency configuration edits.
Online instrumentation compilation: **PASS** (AGP 9.5.0, build successful).
Ordinary instrumentation packaging compilation also passed; DEX inspection confirms
M5 is present only in the opt-in APK and absent from ordinary test/application APKs. No JVM application test,
full Python suite, ordinary Android campaign or emulator operation was run.

M5-A runtime stability, physical-device calibration, independently reviewed native
artifacts and owner scope closure remain pending. M5-B recovery is not implemented;
worker death, OS process death, permission loss, backup/D2D and unbounded endurance
remain excluded. J08 remains **DEFERRED TO PHASE R**.


## 2026-11-02 — native campaign evidence and scoped completion

Execution revision: `43401566a7c3944c1b4faa9256ccf235e723db10`.
Report: `20261102T090554Z-cd4efb338aa34be1a14c1ef300e4bb9a`, under
`build/j-m5/reports/`. These are verified owner-provided findings. No raw archive
was attached for this documentation update: this is not a new Codex execution or
an independent revalidation of raw JUnit, binaries or artifact hashes.

| Evidence | Owner-verified outcome |
| --- | --- |
| Overall execution / evidence completeness | PASS / true |
| Cycles / journeys | 3 / 9 successful native journeys |
| Fixture order within each cycle | 02, 10, 05 |
| JUnit | 1 test PASS (`M5EnduranceTest.boundedNativeCycles`) |
| Main process | Same PID retained across all cycles |
| Isolated workers | 9 distinct worker PIDs |
| PSS observations | 37 samples, including 24 paired observations |
| Per-journey resources and integrity | All 9: bitmaps recycled, descriptors closed, original PDF bytes unchanged |
| Host cleanup | PASS |
| Artifact SHA-256 verification | PASS, as reported by owner |
| Source checkout | Clean |

Fixture 05 remains a 100-page document with only its first two pages traversed.
One JUnit invocation executes the whole bounded campaign; nine journeys do not
mean nine independent JUnit tests. Exact PID values, APK/artifact digests and extra
measurements are not inferred from the supplied summary.

### Memory observations and unresolved review flag

Maximum **sampled** PSS: main process **121407 KiB**, isolated worker **30371 KiB**.
These are not absolute peaks or simultaneous combined maxima.

| Fixture | Cycle 1 post-close main KiB | Cycle 2 | Cycle 3 | Net change KiB |
| --- | ---: | ---: | ---: | ---: |
| 02 | 94667 | 98425 | 98535 | +3868 |
| 10 | 80665 | 95322 | 104772 | +24107 |
| 05 | 101977 | 96282 | 104617 | +2640 |

Fixtures 02 and 10 show successive post-close increases; 05 does not increase
monotonically. The automated **`review_required=true` remains unresolved** and is
preserved alongside execution PASS. It is neither a confirmed memory leak nor a
functional failure. Main PSS sampled while fixture 10 was open was comparatively
stable: **117719, 115819, 117019 KiB**. Those observations do not explain away the
post-close trend or prove that later cycles would stabilize. GC timing, debug/test
retention and workload effects require future evidence; no cause is asserted here.
“Post-close” refers to the existing route-close checkpoint before complete fixture
host teardown, not a forced-GC or steady-state heap measurement.

On 2026-11-02 the owner approved closure: **M5-A is CLOSED only within its
authorized three-cycle native API26 emulator endurance scope**. This is
not acceptance of prolonged memory stability, leak-free operation, a quantitative
memory budget, performance on physical tablets or the whole M5/Phase J milestone.
The growth observation remains open for future evaluation without an invented limit.

### Next boundary — separately authorized M5-B

M5-B should first map existing deterministic recovery tests and identify only missing
non-destructive J06/J10 recovery coverage, with explicit resource/state preservation
assertions and attributable evidence. It must not silently extend this endurance run,
reclassify its growth flag, or introduce speculative production fixes. Actual worker
termination, OS process death, SAF grant changes, backup/restore and D2D require their
own explicit scope and device authorization; none is authorized or completed here.
Longer-run evaluation of the PSS trend also requires a separately bounded plan.
J08 remains **DEFERRED TO PHASE R**. No M5-B implementation starts with this update.
