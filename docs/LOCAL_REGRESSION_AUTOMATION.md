# L02 unified local regression orchestration

This extends [the opt-in local runner](LOCAL_E2E_AUTOMATION.md), on
`test/local-e2e-automation`, from baseline `a886d847251e35ea64801de035c8bbdbc8c55e04`.
The owner reports L01 accepted on macOS Apple Silicon/API 26 ARM64, including normal
SAF PASS, deliberate FAIL, ADB recovery, retained evidence and verified cleanup.
That acceptance is preserved; it does not substitute for real L02 Android execution.
Nothing here merges a phase, changes production behavior or waives a testing policy.

## Commands and responsibilities

Run from the checkout, using its supported JDK 25, SDK 37/build-tools 37.0.0 and
Python 3.10+ environment. Use `python` in Windows PowerShell; the shown `python3`
commands apply to macOS/Linux. `gradlew.bat` is selected automatically on Windows.
These L02 Windows/macOS procedures are reviewed and host-mocked, **not natively
executed in this cloud environment**. Linux host checks actually executed here.

| Command | What executes | Device requirement |
| --- | --- | --- |
| `python3 scripts/local_e2e.py run --suite host` | JVM tests with existing contract coverage finalizer, Lint, debug/release builds, declared fixture-dependency check, all Python infrastructure tests, localization, canonical PDF verifier | No emulator or AVD provisioning; no doctor/device calls |
| `python3 scripts/local_e2e.py run --suite android` | Entire ordinary AndroidJUnitRunner test suite | Existing validated dedicated native API 26 AVD |
| `python3 scripts/local_e2e.py run --suite saf` | Existing four authentic SAF stages and owned-grant cleanup | Same dedicated native API 26 AVD and L01 safety requirements |
| `python3 scripts/local_e2e.py run --suite full` | Host, ordinary Android, then authentic SAF, each independently reported | Same dedicated AVD, connected/prepared once and reused where safe |
| `python3 scripts/local_e2e.py run --suite saf --inject-failure` | Existing deliberate after-grant assertion with live evidence | Same dedicated AVD; expected functional result is FAIL/1 |
| `python3 scripts/local_e2e.py backup-preflight --serial emulator-5600` | Read-only C06 prerequisite discovery on the explicitly selected separate emulator | Owner-selected separate disposable C06 emulator, never the SAF AVD |

`--inject-failure` also works with `full`, affecting only its SAF group. It does
**not** redefine FAIL as PASS: the ordinary application regression group remains
separate, and the deliberately failing full run returns 1. Injection on host or
ordinary Android is rejected. `reader`, `library` and `coverage` remain explicitly
BLOCKED/2. L03 and a new native adapter coverage suite are not implemented.

Global `--home` / `--sdk` options still precede the subcommand. Existing `doctor`,
`provision`, owned AVD manifests, native image/ABI selection, cold-start recovery,
force-stop/grant-loss checks and SAF fixture ownership remain in effect. No extra
AVD is created for deferred suites. Never wipe/re-provision the owner's accepted
AVD or clear application data to obtain a PASS.

## Host validation and coverage boundary

Host uses the existing commands, not replacement functional tests:

```text
:app:testDebugUnitTest --rerun-tasks
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
python -m unittest discover -s scripts/tests -v
python scripts/validate_localization.py
python test-fixtures/scripts/verify_fixtures.py
```

Gradle commands use `--console=plain --max-workers=4`. The existing
`verifyContractCoverage` finalizer must appear in the JVM task output. JVM XML is
copied into the unique report only if its timestamp changed during this command;
stale output cannot satisfy the new run. The existing `verify_test_results.py`
helper checks successful JVM and Android XML, including smoke evidence.

The dependency preflight reads `test-fixtures/requirements-fixtures.txt` and checks
installed distribution versions using Python metadata. It never installs packages,
regenerates/replaces fixtures or relaxes versions. Missing/incompatible tools are
BLOCKED; the dependent PDF verification is NOT RUN, while independent checks still
execute. Install the declared tools yourself in the chosen environment before
resuming. Python and JVM counts are distinct from the count of validator commands.

No thresholds, finalizers or CI tasks changed. JVM contract coverage is enforced;
this is not a claim of newly measured combined Android PDF/Room/DataStore coverage.
The existing hosted/native coverage obligations remain. `coverage` stays BLOCKED.

## Ordinary Android execution and APK mode separation

The ordinary source inventory currently contains **228 JUnit methods** across
application integration, SAF/provider access, Android PDF/fixtures, Room metadata,
DataStore, UI and smoke tests. No existing test was edited or excluded. The manual
`BackupPolicyAcceptanceRunner` has no `@Test` methods and is not an ordinary test.
Inventory supports the repository's plain Kotlin/Java JUnit forms; an unsupported
annotation/signature is BLOCKED rather than silently omitted.

Builds switch modes explicitly: ordinary builds omit init scripts; SAF loads only
`scripts/local-e2e.init.gradle`. Instrumentation builds use `--rerun-tasks` and
`--no-build-cache` to force regeneration across mode changes. This is deliberately
more expensive than an incremental build. It avoids reusing the most recently
produced incompatible test APK without changing project Gradle configuration.

Before installation, snapshot both APKs into the unique report, record hashes and
inspect actual DEX plus the compiled manifest using SDK `aapt2`. Require the test
package, target application and **exactly one normal AndroidJUnitRunner** declaration.
Ordinary APKs must exclude `SafLocalE2eTest` and contain every inventoried ordinary
class/method. SAF APKs must contain the opt-in class. A C06 APK selecting its custom
runner is rejected even if its filename matches an ordinary APK. The C06 class's
mere presence in ordinary DEX is harmless; it is never selected or executed.

Validate the dedicated AVD name, reserved serial/port, emulator flag, API 26, native
ABI, provisioned image and stable boot using L01. Install only serial-targeted APKs,
then verify the installed runner registration. Execute without a class/package
filter, via the explicit argv equivalent of:

```text
adb -s emulator-5580 shell am instrument -w -r com.abdev.partituraspdf.test/androidx.test.runner.AndroidJUnitRunner
```

The serial comes from the owned manifest; 5580 is only its default. Neither
`connectedDebugAndroidTest` nor first-device selection is used, so an unrelated
`emulator-5554` is not targeted. No functional method is retried. Uninstallation,
application clear/reset and C06 acceptance never run. Existing tests retain their
own isolated fixtures and cleanup; runner settings are restored through L01.

The full-run parser validates starts/results, stable declared test count, every
inventoried identity, required `ExampleInstrumentedTest.useAppContext`, completion
order, failure stacks and runner termination. Zero tests, skips, missing results,
crashes or unexpected identities are BLOCKED. A trustworthy completed JUnit failure
is FAIL even when ADB exits nonzero; an apparently successful protocol with a failed
ADB wrapper is BLOCKED. Compilation cannot become execution evidence.

## Aggregation, reports and cleanup

Reports retain `build/local-e2e/reports/<UTC>-<UUID>/`. JSON includes Git SHA and
working tree, OS/Python identity, groups, exact commands, exits/timings, measured
counts, artifacts, errors, ADB recovery and cleanup. `None`/null counts mean execution
was not established, not zero coverage. Backup acceptance is always explicitly
NOT RUN in these suites.

| Artifact | Meaning |
| --- | --- |
| `summary.json`, `index.html` | Unified group results and commands; never infer success from compilation |
| root `junit.xml` for host/android/full | Orchestration **group checks**, not an invented count of application test methods |
| `host/junit.xml`, `host/*.log` | Executed host validator checks and raw command output |
| `host/jvm-xml/TEST-*.xml` | Fresh actual Gradle JVM test cases |
| `android/inventory.json`, APKs/hashes, `*.manifest.txt` | Expected tests and inspected ordinary build provenance |
| `android/instrumentation.log`, `android/junit.xml` | Complete raw Android protocol and actual executed cases; partial runs add an infrastructure error |
| `saf-junit.xml` in full runs | Actual SAF stage cases, independently retained from the group summary |
| Existing stage logs / root JUnit in standalone SAF | Backward-compatible L01 evidence |
| `android-*`, `saf-*` diagnostics on group failure | Screenshots, hierarchy, logcat and device state captured before another group changes the screen |
| `instrumentation-failures/<event>/` | Original in-process SAF checkpoints, retained separately from host post-test snapshots |
| `adb-transport.json`, `cleanup` in JSON | Serial/port/identity checks, recovery and verified/unverified restoration |

Full continues independent groups after a host/functional failure; PASS requires
all three. If ordinary Android has an infrastructure blocker, SAF is explicitly
NOT RUN: do not reuse a device with uncertain setup/execution state. Group evidence
is checkpointed after each group. Device evidence is collected before cleanup;
cleanup failure adds an explicit blocker without overwriting earlier group results.
Existing FAIL remains FAIL/1 even if another group/cleanup blocks, with all blockers
visible. With no functional failure, incomplete groups/evidence/cleanup return 2.
All required groups plus evidence and cleanup must succeed for exit 0.

## C06 boundary: discovery is not destructive acceptance

Read [the existing C06 procedure](C06_BACKUP_RESTORE_ACCEPTANCE.md) and
[acceptance audit](C06_PERSISTENCE_ACCEPTANCE_AUDIT.md). The preflight requires an
explicit emulator serial different from the SAF reserved port; it also rejects a
SAF-owned AVD name on another port. It does not select the first connected device,
launch an emulator or build/install an APK.

Its only device commands are `get-state`, `emu avd name`, emulator/hardware/API/boot
properties, `bmgr list transports`, `bmgr enabled`, secure `backup_auto_restore`
read and argument-free `bmgr` usage discovery (API 26 prints usage to stderr).
It checks the existing local-transport allowlist and PowerShell version (5.1+ on
Windows, 7+ elsewhere, queried without a profile) and
records unavailable prerequisites as BLOCKED. It does not enable Backup Manager,
change auto-restore, select a transport, create consent markers, query/erase backups,
uninstall, seed or restore anything. A preflight PASS means discovery succeeded;
`backup_acceptance` and genuine device transfer remain **NOT RUN**.

Actual C06 execution still uses **only** `Invoke-BackupPolicyAcceptance.ps1`, its
manual runner/init script and typed `ERASE ONLY <serial> <package>` consent. It needs
separate owner authorization and an independently approved disposable-device
procedure. Never use the SAF AVD for it. `full` never calls this procedure or the
preflight. Local cloud-style transport acceptance is not proof of D2D. Existing
C06 backup/D2D/physical-device acceptance requirements remain outstanding and do
not block acceptance of the non-destructive L02 implementation by themselves.

## L02 baseline evidence and owner verification

Cloud Linux: **193 Python tests PASS** (38 new L02 regressions), **165 JVM tests
PASS**, existing scoped contract coverage gate PASS, debug/release builds and Lint
PASS (zero errors, 20 existing warnings), en/ca/es localization PASS and all 15
canonical PDF fixtures PASS. Real builds verified ordinary -> C06 -> SAF -> ordinary
mode changes, normal runner selection and all 228 ordinary class/method entries.
The C06 runner was correctly rejected as ordinary; no C06 fixture executed.
Release APK inspection confirms no opt-in class, debug harness/helpers/IDs or PDF
fixtures; normal instrumentation excludes the opt-in class.

Actual `host` ran successfully without an emulator. Actual `full` retained host
PASS / Android BLOCKED (no KVM acceleration) / SAF NOT RUN, with exit 2. Authentic
Android and successful full execution of **L02 are NOT RUN** here. Mock protocol,
identity, cleanup, mode-selection and preflight tests do not establish native
Windows/macOS behavior or Android success. L01's owner-accepted Apple Silicon
results are prior-phase evidence, not a new L02 result.

On the owner's existing dedicated native API 26 AVD, run `doctor`, `host`, `android`,
`saf` and `full` with the commands above. Preserve each exact report path and check
all 228 ordinary tests plus smoke, four authentic SAF stages, owned cleanup and
unrelated-grant/device preservation. Repeat the deliberate SAF scenario separately;
its required result is genuine FAIL/1 with evidence and cleanup, not PASS. No new
provisioning, destructive backup acceptance, PR, merge or L03 work is part of L02.

## L02C: independent identity-attempt and total deadlines

Owner-provided Windows 11 evidence at `e9b6e3b` shows host PASS (195 Python/165 JVM),
all 228 ordinary Android tests PASS, then SAF BLOCKED before APK installation or
fixture creation. Settings restoration passed. The pre-install identity check's
`getprop ro.build.version.sdk` timed out after approximately 7.93 seconds. The
ordinary Android result proves that suite completed; it does not prove subsequent
ADB reads will be prompt. No evidence here establishes the underlying emulator/
host stall, a reboot or process death. Those must not be inferred from this timeout.

The demonstrated runner defect was budget starvation: a read could use the entire
remaining eight-second window, so the advertised retry could never happen after
one stalled command. The existing recovery helper now has independent bounds:

- Normal identity/readiness: **at most 3 attempts**, **2 seconds per complete
  attempt**, **8 seconds total**, existing **250 ms** backoff only after a
  recoverable failure. A healthy attempt adds no sleep.
- Every AVD-name/property read uses the minimum of its command timeout, remaining
  attempt deadline and remaining total deadline. The attempt deadline is shared
  across all reads; it is not reset per command. A result arriving after its
  deadline cannot be marked ready. Exhausted total time can shorten the final
  attempt or prevent another one.
- A retry restarts full identity verification: reserved serial/port, expected AVD,
  emulator flag, API 26, native ABI and provisioned image; boot checks remain where
  already required. A partial or timed-out attempt is not verified identity.
- The existing cold-start window retains **60 attempts / 60 seconds / 500 ms**,
  now with the same two-second attempt cap. Owned-port connection refusal remains
  restricted to the initial window of this invocation's live child.
- There is no second transition retry loop or new fixed transition delay. SAF
  already checks identity when reusing the AVD and again before each install; the
  shared fix makes those existing checks capable of recovering from a timeout.

These are conservative requested deadlines, tested with a deterministic host clock,
not a promise about Windows scheduling or a measured latency distribution. If the
native full identity sequence remains too slow, it must be reported BLOCKED and
investigated with the new evidence, not silently accepted. This task does not
increase installation, settings-write or instrumentation timeouts. Their calls
remain outside the read-only attempt context; uncertain writes and functional
JUnit stages are never replayed. All context deadlines are restored on exit.

`adb-transport.json` retains each original timeout and partial output, effective
`timeout_seconds`, attempt number and elapsed time. Recovery events include
`attempt_timeout_seconds`, `total_budget_seconds`, remaining total time on failure,
and `exhaustion_reason` (`attempt limit` or `total deadline`). Successful recovery
records the fully verified identity. Existing rejection/process-exit events,
raw instrumentation, group results and cleanup evidence remain separate; an
executed assertion failure is still FAIL, not a recovered transport event.

Regression coverage models a first SDK-property timeout consuming its entire
per-attempt allowance, then a full successful verification at 2.25 simulated
seconds (the old behavior consumed eight seconds on the first read). It also
checks repeated timeouts, a shorter total deadline, decreasing read timeouts within
one attempt, late-result rejection, foreign/API/ABI rejection after timeout,
child termination, unchanged one-shot installation timeout, and the actual SAF
workflow reaching its first install only after pre-install identity recovery.
Existing tests still cover no functional retries, incomplete cleanup blocking
PASS, settings restoration and cold-start connection-refusal recovery. These are
host fault-injection tests, not authentic Android execution.

L02C validation: **204 Python regressions PASS**, **165 JVM tests and existing
contract coverage PASS**; actual `host` JSON and JUnit PASS; debug/release,
ordinary and opt-in instrumentation builds PASS; compiled APK mode/runner isolation
checks PASS; Lint zero errors with 20 existing warnings; English/Catalan/Spanish
localization and all 15 canonical fixtures PASS. No application source, Android
assertion, dependency, Gradle/CI policy or coverage threshold changed.

Native Windows and successful real Android/full execution of **L02C are NOT RUN**
in cloud (no usable native emulator). On the owner's existing Windows AVD:

```powershell
py -3 scripts/local_e2e.py run --suite full
```

Acceptance requires host PASS, all 228 ordinary Android tests PASS, four authentic
SAF stages PASS, owned grant/fixture cleanup and settings restoration PASS, no
unresolved diagnostics, overall PASS and exit 0. Retain the printed report path
and transport events, including any timeout/recovered attempts. The prior Windows
Android PASS is baseline evidence, not proof that this correction has executed.

## L03: separate opt-in authentic coverage

Use `python scripts/local_e2e.py run --suite coverage` (macOS/Linux: `python3`).
See [Local code coverage](LOCAL_CODE_COVERAGE.md) for serial-isolated capture,
existing A1/B5/C05 gates, evidence, informational source inventory and limitations.
`full` retains the accepted host/ordinary Android/authentic SAF semantics; it does
not automatically run coverage or manual backup acceptance. L02 at `27cef9f` was
accepted by the owner on native Windows: host 204 Python/165 JVM, ordinary Android
228, SAF four stages, cleanup/settings restoration PASS and no diagnostics. This
is prior owner-reported evidence, not an independently executed cloud L03 result.
