# L03: authentic local JVM and Android coverage

> **Current-status note (2026-10-16):** The sections below retain original delivery-time findings and their exact test SHAs. L01–L04 was subsequently validated natively on macOS and in API 26/27/36 hosted CI; consult [PROJECT_STATUS.md](PROJECT_STATUS.md) and [L04D](LOCAL_COMPATIBILITY_VALIDATION.md). Historical `NOT RUN` and `BLOCKED` observations are not claims about the later verified run. Physical Android 8.1 acceptance remains deferred.

L03 is a separate **opt-in** consumer of the existing A1/B5/C05 JaCoCo infrastructure.
It does not change `full`, CI, production code, thresholds or release instrumentation.
L01 macOS ARM64 and L02 Windows API26 acceptance at `27cef9f` are owner-reported
prior evidence; they are not new L03 coverage execution evidence.

## Run on the existing dedicated AVD

Windows PowerShell:

```powershell
python scripts/local_e2e.py doctor
python scripts/local_e2e.py run --suite coverage
$LASTEXITCODE
```

Intel macOS, Apple Silicon macOS, Linux:

```bash
python3 scripts/local_e2e.py doctor
python3 scripts/local_e2e.py run --suite coverage
echo $?
```

Use the existing `--home` and `--sdk` global options before the subcommand when
needed. For coverage, the report/home directory must remain inside the checkout
and contain no symlink ancestors; an external home is safely BLOCKED before device
connection. Do not migrate an existing external AVD to work around that guard.
Do not provision another AVD or migrate the accepted one. Prerequisites
are the existing supported Python 3.10+, JDK25, wrapper/SDK37/build-tools37 toolchain,
ADB, emulator and an owned provisioned **native accelerated API26** image.
Apple Silicon uses `google_apis;arm64-v8a`; supported native x86_64 hosts use
`google_apis;x86_64`. API27/36 and software emulation cannot substitute.
The checkout must not be built or edited concurrently by another process.
Normal Android tests use isolated fixtures on this dedicated testing emulator;
never run this procedure on a physical device or personal application data.

The existing bounded identity recovery remains 3 attempts, 2 seconds per attempt,
8 seconds total, with the separate accepted cold-start window. Only read-only
identity checks retry. Install, instrumentation, mkdir and cleanup writes never
replay after uncertain replies. Another connected device is never selected.
A foreign device at the reserved serial blocks execution; unrelated attached
serials are ignored. No unqualified device operation, server reset, wipe, uninstall,
application-data clear, backup/restore or fabricated SAF grant is used.

## Integration design and sequence

1. Read-only `doctor` must pass before any build, connection, evidence deletion or
   device modification. Connect through the accepted L01/L02 ownership and stable
   boot checks: manifest/image ownership, reserved serial/port, exact AVD name,
   qemu identity, API26 and native ABI.
2. Load only `scripts/local-coverage.init.gradle`, never the SAF or C06 init script.
   Force normal debug/application/test builds without build-cache restoration.
   The existing debug configuration already enables Android JaCoCo coverage.
   Inspect the actual instrumentation manifest/DEX for the ordinary runner and
   full ordinary inventory; reject SAF/C06 runner selection. Inspect application
   DEX for JaCoCo probes, runtime and the EMMA compatibility bridge.
3. Export effective Gradle/AGP/JDK/JaCoCo versions and the **existing** A1/B5 class
   selections. Snapshot both APKs/hashes, native host/SDK/device identity, Git SHA,
   dirty-tree state, source hashes, compiled production and test classes, IDs,
   probe counts and executable source models. Reject excessive device clock skew;
   never rewrite timestamps to manufacture freshness.
4. Invoke existing `preparePersistenceCoverageRun` only after ownership/build
   validation. Its backward-compatible explicit-serial/local-output mode keeps
   CI defaults unchanged. It clears generated JVM exec/JUnit and this run's own
   C05 inputs/reports after prerequisite/path checks. Also clear generated A1
   reports so an old finalizer report cannot satisfy this run. Historical local
   report directories are preserved.
5. Run real `testDebugUnitTest` without build-cache restoration, retaining its
   unchanged `verifyContractCoverage` finalizer. Require the exact source inventory
   (currently **165**, including testDebug helpers), fresh XML and real `.exec`.
6. Install the exact inspected APK copies with `-s <owned-serial>` and validate the
   installed runner registration. Create, without `-p`, one app-private directory
   `files/local-coverage-<UUID>`; an existing directory is never adopted.
7. Execute the entire ordinary AndroidJUnitRunner inventory (**228** today), once:

   ```text
   adb -s <owned-serial> shell am instrument -w -r
     -e coverage true
     -e coverageFile /data/data/com.abdev.partituraspdf/files/local-coverage-<UUID>/coverage.ec
     com.abdev.partituraspdf.test/androidx.test.runner.AndroidJUnitRunner
   ```

   This is one argv command, not multiple shell lines. These are documented runner
   arguments ([AndroidJUnitRunner](https://developer.android.com/reference/androidx/test/runner/AndroidJUnitRunner)).
   The repository's resolved runner 1.5.2 bytecode was also inspected:
   `CoverageListener` returns `coverageFilePath`; its reporter uses
   `com.vladium.emma.rt.RT.dumpCoverageData(File,false,false)`, supplied by the
   AGP JaCoCo runtime bridge. Output acknowledgement alone does **not** prove
   coverage: exact-path binary retrieval and JaCoCo validation are mandatory.
8. Require complete unfiltered raw JUnit protocol with every expected case and
   smoke test, no skipped/incomplete cases, and the exact coverage-path response.
   Retrieve only that `.ec` using serial-targeted `exec-out run-as ... cat` in
   binary mode. Preserve raw output, parsed JUnit and partial/corrupt bytes.
   No AGP `connectedDebugAndroidTest` result/XML is fabricated: local parsed XML
   lives in `coverage/android-junit/`, outside the CI connected-results path.
9. Reuse `validatePersistenceCoverageInputs`, both persistence report tasks and
   `verifyPersistenceCoverage`. Use the same `PersistenceCoverageInputs.java`
   readers/IDs/probes and `persistence_coverage.py` session, freshness, provenance,
   source model, denominator and independent gate checks. Local mode additionally
   validates all application namespace class IDs and positive actual Android
   execution of native PDF document/page/buffer classes. Recheck production/test
   class and APK fingerprints across capture/analysis.
10. Generate local B5 and informational production XML/HTML using Gradle's existing
    JaCoCo toolchain. B5 reuses `pdfAdapterCoverageReport.classDirectories` and
    source directories **without its connected-test dependency**. Its existing
    aggregate 90% line / 80% branch rule is applied to the authenticated inputs.
    A1 continues to use its existing JVM-only report/finalizer. C05 remains
    independently gated per package **and each source**, 90% line / 80% branch.
11. Collect existing screenshots, UI hierarchy, logcat/ADB diagnostics and restore
    modified settings. Remove only the acknowledged UUID coverage directory.
    Interrupted instrumentation is stopped only in the test application on the
    validated owned emulator before directory cleanup. Stop an emulator only if
    this invocation launched it. Any uncertain cleanup/evidence blocks PASS.

The init script is local only and uses `--no-configuration-cache` because its
report configuration closures are not configuration-cache serializable. Ordinary
builds and CI keep their original configuration-cache behavior. No dependencies,
CI jobs, coverage exclusions or testing policies change.

## Scope and denominators

Class IDs are calculated from uninstrumented AGP9 Kotlin debug output. All JVM
and Android compiled test classes are inventoried separately; debug-only helpers
are identity-checked but excluded from handwritten **main** source denominators.
Main-source membership requires the actual SourceFile/package path beneath
`app/src/main/java`. The only explicit generated class exclusions remain C05's
`DocumentDao_Impl` and `MetadataDatabase_Impl` families. Resources/BuildConfig,
third-party dependencies, scripts and test-only code are not handwritten main
logic. Kotlin-generated companions/lambdas/continuations associated with main
sources remain included; standard JaCoCo compiler filters are unchanged.

Current compiled model, not a claim of execution or attainment:

| Approved scope | Executable source lines | Branches | Gate |
| --- | ---: | ---: | --- |
| A1 core + storage/api + pdf/api | 188 | 176 | aggregate 90% / 80%, existing JVM finalizer |
| B5 pdf/android | 218 | 72 | aggregate 90% / 80%, genuine JVM + Android probes |
| C05 Room metadata | 210 | 177 | independent package/source 90% / 80% |
| C05 DataStore preferences | 35 | 12 | independent package/source 90% / 80% |
| Entire handwritten main inventory | 1258 | 737 | informational whole-project inventory |

Inventories are derived again on every run, not frozen to this table. Source-line
unions avoid double counting shared lines among compiler classes. Zero branches
are N/A for branches, never an exemption from executable-line gates. Sources
beyond approved scopes (composition, SAF, UI/theme, activity) are measured and
explicitly **INFORMATIONAL**; no new threshold is imposed. Passing approved scopes
cannot establish adequate coverage of all production code.

JaCoCo combines compatible probes by logical OR against identical class IDs and
probe counts. It does **not** average JVM and Android percentages. XML package,
class, source, line and branch counters are checked against the compiled model.
Missed lines/partial lines/branches remain visible. Compatible repeat dumps union;
wrong formats, duplicate incompatible records, unknown application classes,
changed classes/tests/APKs, wrong builds, missing required native execution,
empty data, invalid sessions, stale/replayed evidence and incomplete JUnit block.

## Reports and classification

The printed unique root contains `coverage/` with:

- `summary.json`, orchestration `junit.xml`, navigable `index.html` and SHA256
  inventory of retained artifacts; no wrapper hashes recursively include themselves.
- `doctor.json`, `configuration.json`, immutable `capture.json`, `classes.json`,
  APK copies, all compiled production/test classes, Gradle and raw instrumentation logs.
- `inputs/jvm.exec`, `inputs/android/coverage.ec`, `jvm-junit/`, `android-junit/`.
- `contracts.xml` / `contracts.html/`, `pdf.xml` / `pdf.html/`,
  `production.xml` / `production.html/`.
- C05 `persistence/run.json`, `provenance.json`, per-scope XML/HTML and failure
  diagnostics, preserving its original source/class/package counters and input hashes.

Root JSON/JUnit and device diagnostics remain compatible with L02. Real JVM and
Android counts are separate from orchestration-check counts. Incomplete runs keep
partial evidence and unknown counts; missing Android data is never zero coverage.

- **PASS / 0**: real JVM and Android tests, authentic compatible inputs, provenance,
  all approved gates, reports, evidence and owned cleanup pass with no diagnostics.
- **FAIL / 1**: complete trustworthy assertion evidence or genuine scoped threshold
  violations; retain exact fractions, scope and missed-line/branch details.
- **BLOCKED / 2**: missing tools/acceleration, unsafe identity, uncertain writes,
  incomplete execution, corrupt/incompatible/missing inputs or incomplete evidence.
  An original functional FAIL retains precedence while additional blockers remain
  recorded. No JVM-only or synthetic fallback can produce coverage PASS.

## Audit/reproduction and existing CI boundaries

Keep the entire unique directory and printed root JSON/JUnit, not only HTML or a
hash. Verify hashes, recorded commit/source state, versions, APKs, class inventory,
raw instrumentation case inventory, both sessions/inputs and threshold failures.
Run again from the same reviewed checkout using the command above for new evidence.
Do not independently rebuild classes between capture and report analysis.

C05's offline `auditPersistenceCoverage` remains available. For a retained local
capture, retain/reconstruct its exact app/build compiled-class paths and JVM XML/
exec, use the **captured** output directory and serial with the local init script,
then invoke only `:app:auditPersistenceCoverage` with
`-PlocalCoverageOutput=<captured coverage directory>`,
`-PlocalCoverageSerial=<captured owned serial>` and
`-PlocalCoveragePython=<current Python executable>` plus
`--no-configuration-cache`. Snapshot class directories map back to the corresponding
`app/build/intermediates/<compiler>/<variant>/compile<Variant>Kotlin|JavaWithJavac/classes`
paths. This audit does not contact a device or certify a new run; its original
commit/class/input/JUnit/report hashes and gate result must reproduce. Full local
Android capture/offline reproduction is NOT RUN here.

The seven mandatory CI checks, their existing C05 hosted capture and the original
B5 tasks are untouched. Do not invoke original `verifyPdfAdapterCoverage` locally:
its dependency graph includes unqualified connected instrumentation. L03 consumes
only its scope, using separately authenticated serial-targeted input. Local C05
inputs/reports cannot satisfy the ordinary CI paths. Avoid concurrent builds in
one checkout; generated JVM exec/JUnit and A1 reports are deliberately refreshed.

## Executed evidence and remaining acceptance

Cloud Linux validation: **245 Python regressions** and **22 real JaCoCo binary
validator cases** pass (41 Python and five binary cases added). The existing 165 JVM tests and A1
coverage gate, debug/release builds, Lint, en/ca/es localization and all 15 canonical
fixtures pass. Real JVM `.exec` was parsed with the broadened existing Java reader
(78 application production records, 189 known test records). An external,
explicitly **JVM-only diagnostic** report validated the full main-source model:
522/1258 lines and 366/737 branches. This is neither combined Android coverage nor
a new whole-project gate, and cannot establish L03 PASS.

That diagnostic confirms why JVM-only substitution is unsafe: B5 has 192/218
lines (88.07%); Room 90/210 lines and 105/177 branches; DataStore 20/35 lines and
12/12 branches. These are JVM-only gaps, **not demonstrated combined-runtime
coverage failures**. SAF/UI/composition gaps remain visible and informational.

Real `run --suite coverage` returns **BLOCKED / 2** in cloud before connection or
capture: no usable KVM acceleration or owned runnable emulator. No emulator was
launched. Real Android coverage, 228-case L03 execution, cross-runtime provenance,
combined thresholds and end-to-end cleanup are **NOT RUN**. Native Windows,
Intel macOS and Apple Silicon L03 acceptance must use the owner's already
provisioned accelerated AVD and preserve the entire new report. Mocked host tests,
APK compilation and prior L01/L02 acceptance do not replace this evidence.
No backup/restore/D2D acceptance, PR, merge or L04 work is included.


Reproduced validation commands (no live Android execution):

```text
python scripts/local_e2e.py run --suite host
python -m unittest discover -s scripts/tests -v
bash ./gradlew :app:testPersistenceCoverageInputs --max-workers=4 --console=plain
python scripts/local_e2e.py run --suite coverage
```

The host command executes JVM/contract coverage, Lint, debug/release, Python,
localization and fixture checks. Separate actual normal/SAF/normal APK rebuilds
used the existing Runner Gradle helper with forced tasks/cache disabled; compiled
manifests/DEX were inspected for all 228 ordinary methods and release isolation.
The local report task dry-run contains input validation and report/verify tasks
only, with no compiler or connected-device test. Complete cross-runtime report
execution still requires the real dedicated API26 AVD.
