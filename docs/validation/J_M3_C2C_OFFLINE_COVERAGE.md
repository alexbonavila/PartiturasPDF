# M3-C2-C — Retained API26 coverage reanalysis

2026-10-23. Implementation baseline: `57af07b8e88dfb34d8aa13ee36b9e0e8b9f11409`.
Original producer: `ad08aeeb28526fa6e57195f35c32d351b9ee4692`.

This is a host-only recovery tool, not a collector or new Android/JVM test execution.
The owner's real capture is unavailable in Codex. Real metrics remain **NOT MEASURED
by this task**. Synthetic infrastructure tests do not establish application coverage.
The original producer must remain **BLOCKED**, even if a separate reanalysis passes.
Worker-process attribution remains **BLOCKED / NOT MEASURED**.

## Entry point and prerequisites

Run from the repository root only after reviewing this implementation:

```bash
python3 -B scripts/offline_coverage.py \
  --producer-run build/local-e2e/reports/20261023T071450Z-283df3ade7304b19a1c303079ada60c3 \
  --output "build/offline-verify-$(date -u +%Y%m%dT%H%M%SZ)-$(uuidgen)" \
  --java "$JAVA_HOME/bin/java" \
  --verify-only
```

If verification succeeds, request a **new** directory for report generation:

```bash
python3 -B scripts/offline_coverage.py \
  --producer-run build/local-e2e/reports/20261023T071450Z-283df3ade7304b19a1c303079ada60c3 \
  --output "build/offline-analysis-$(date -u +%Y%m%dT%H%M%SZ)-$(uuidgen)" \
  --java "$JAVA_HOME/bin/java"
```

These are proposed owner commands; the real capture was not executed here. Set
JAVA_HOME to an existing supported JDK (the project uses JDK25). Paths must not traverse
symlinks. If JAVA_HOME is an alias, provide the canonical absolute Java executable.
The output parent (`build` above) must already exist; the chosen output must not exist.
No directory is reused, cleaned or replaced.

Existing JaCoCo 0.8.14 core/report and ASM 9.9 core/commons/tree jars are required.
Their SHA-256 hashes are pinned in the tool. It uses the original configuration's
classpath locations if still present. For moved caches, supply `--jacoco-classpath`
with the existing local jar paths separated by `:` on macOS. Other captured jars are
not loaded. Missing libraries return BLOCKED; nothing is downloaded or installed.
Git must contain the original producer object. The tool reads it without checkout.

## Authentication and isolation

- Reject symlink evidence/output paths, traversal, special evidence files, overlapping
  input/output directories and preexisting output directories.
- Hash the original run, copy it with `copy2` into `producer-copy/`, and use that copy.
  Compare both original and copy hashes again at finalization. Do not rewrite mtimes.
- Verify every artifact listed in the captured coverage summary; reject missing,
  changed or uninventoried payload files. Wrapper summary/index/JUnit files are
  preserved separately; these local inventories are not cryptographic signatures.
- Pin the owner-supplied binary hashes and sizes: JVM 239586 bytes / SHA-256
  `3d3f470690be04adc9005c42cc7984091e77c2993cf5ac8e68aeeac20efde961`;
  Android 260764 bytes / SHA-256
  `4de7636926a8cc391336d9187fa5dec2e48ad23cf6f3190ec3513f2eea59effa`.
- Require the original producer revision, clean-source identity, API26 verified emulator
  record, normal instrumentation identity, PASS execution/cleanup records and original
  BLOCKED classification without functional failures. No live device inspection occurs.
- Recover tracked `app/src` bytes through read-only Git and verify all recorded source
  hashes, including AIDL declarations. Require a complete tracked source inventory.
  Recorded ignored files (such as Python cache bytes) must still exist with the exact
  recorded hash in the local checkout; they are copied, never recreated. Missing
  original bytes are a precise BLOCKED prerequisite, not permission to ignore hashes.
- Verify recorded original build/tool file hashes against the producer Git revision.
  Keep those hashes unchanged. Current analyzer tool hashes, revision, dirty-state
  information, command, library hashes, Java/Python versions and timestamp are separate.
- Reconstruct Kotlin/javac production and test compilation layouts from retained files,
  never from current build output. Match original class hashes/IDs/probe models and
  authenticate generated AIDL separately. Unknown or incompatible records are rejected.
- Reuse the existing source inventory helper, annotation/diagnostic partition audit,
  exact JUnit inventory checks and strict coverage progress-event parser. The producer
  source implies 631 JVM methods and 418 standard Android methods; derive those sets
  rather than manufacturing successful results from the numbers. All 14 assigned
  diagnostics remain NOT RUN; their 29 controlled invocations are not replayed.
- Check raw instrumentation completion, zero assertion failures, recorded exit 0,
  exact annotation exclusion, selected serial and requested private coverage output.
- Match APK hashes, original successful manifest-dump command/output, normal runner,
  required DEX methods, opt-in isolation and application coverage-runtime markers.
  This preserves recorded binary inspection; it is not fresh installed-device attestation.
- Use the existing Java reader and C05 checks for real binary formats, sessions,
  timestamp policy, exact class IDs/probe counts and positive persistence/native adapter
  execution. Keep generated identities out of handwritten report denominators.

No captured command is executed. Subprocesses are limited to read-only Git, the explicitly
selected host Java executable and the two reviewed Java analysis helpers. Java parses
retained bytecode; it does not load application classes or run application tests.

## Reports and result meanings

All new outputs are confined to the new analysis directory:

- `summary.json`, `provenance.json`, `inputs-manifest.json`,
  `junit-verification.json`, `binary-verification.json`, `index.html`.
- `producer-copy/` (untouched evidence copy) and `retained-layout/` (reconstructed inputs).
- Contracts, PDF, metadata, preferences and production XML/HTML, plus selected class lists.
- Package/source counters and report hashes. These are retrospective measurements.

A1 consumes **JVM only** and uses its existing aggregate LINE90/BRANCH80 gate.
B5 consumes compatible JVM+Android data and retains its full Kotlin adapter/isolated
scope and aggregate gate. C05 retains metadata/preferences package **and source** gates.
The whole handwritten production projection remains informational. Generated AIDL is
validated but not added to these denominators. No service implementation is excluded.

`--verify-only` checks evidence and bytecode but generates no execution coverage metrics.
Its PASS means input verification passed. Full-analysis PASS means the existing app-stream
scope gates passed; it does not close missing worker attribution or formal Phase J acceptance.
Genuine threshold violations are FAIL. Missing/incompatible provenance or evidence is
BLOCKED. A verified gate failure survives a later report-generation blocker, with both
recorded. Original/copy integrity changes invalidate the analysis. Exit codes are 0/1/2.

Original JSON, JUnit, logs, APKs, binary inputs and source identities are never relabeled.
Do not add raw copies, private paths or reports to Git. Keep private analysis outputs local.
A missing file, stale timestamp, absent dependency or hash mismatch must be resolved with
original retained evidence; do not patch manifests or rebuild substitute bytecode.

The implementation must be reviewed before owner execution. No actual API26 coverage
result or percentage has been calculated in this implementation task.

## Implementation validation

Host-only validation on 2026-10-23: 25 offline-tool tests, 14 existing persistence-policy
Python tests and 52 existing coverage-runner Python tests PASS. The offline suite includes
real JaCoCo XML/HTML generation and forged-ID/probe-count rejection using a temporary
synthetic Java class and synthetic execution data. It compiles no application source and
executes none of the 631 JVM or 418 Android application tests. Actual Mac capture replay
remains NOT RUN. No coverage percentages from the owner's capture are claimed.
