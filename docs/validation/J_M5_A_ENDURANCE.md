# Phase J M5-A — bounded native endurance

2026-11-02. Implementation baseline `848682e5ec679d5ae6870100bad516404870a7ad`,
branch `test/mvp-stabilization-j`. Native execution **NOT RUN**; owner evaluation
required before M5-B. M4 closure and all historical evidence remain unchanged.

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
