# Phase M — Independent synthetic PDFBox output evaluation

2026-11-03. **SOURCE ONLY / OWNER EXECUTION REQUIRED.** Baseline
`3a0f4067ec671d640bf7ced9d026dec9146e9911`. Owner reports CI #132 attempt 2
[41687967334](https://github.com/alexbonavila/PartiturasPDF/actions/runs/41687967334)
passed nine jobs, 708 JVM and 466 Android tests on each API26/27/36. Those results
apply to that exact implementation, not these Python tools, and do not establish
independent fidelity. No CI access/dispatch or tool execution by Codex.

## Boundaries

Only scripts/host regressions and Phase M documents change. No Android evidence
schema, app, dependency, Gradle, CI, manifest or fixture modifications. Existing
[pinned candidate/security gates](M_ENGINE_EVALUATION.md) and
[spike inventory](M_PDFBOX_ANDROID_TEST_SPIKE.md) remain authoritative.
No production validator, engine approval, publication authority or public PDF write.

The collector accepts an explicit `emulator-N` serial, one or more canonical UUID
namespaces, a new output directory and an owner-declared full lowercase Git SHA.
It queries only `shell getprop ro.build.version.sdk` for connected API metadata.
All private-file reads use argument-array `exec-out run-as com.abdev.partituraspdf
cat no_backup/<validated-namespace>/<validated-file>`; only evidence.json and
`result-N.pdf` names explicitly in positive recipes. No shell=True, root, grants,
recursive download, delete, device mutation or arbitrary app-private path access.
No physical-device collection is supported. ADB executable is owner-selected.

Binary stdout streams to exclusively created files, at most the declared size;
worker threads drain bounded stderr and the subprocess is killed after 120 seconds.
Stderr/transfer uncertainty blocks. JSON limit 8 MiB; at most 64 namespaces/recipes
per namespace; aggregate PDF collection 256 MiB, individual output 128 MiB.
Candidate/API/PID/case/retention state, manifest hash, source hashes, recipe indices,
quarter turns, strategy and destination uniqueness are checked. Partial failures
retain diagnostics in `collection-manifest.json` with BLOCKED status; no silent
file overwrite or re-use of a partial collection. Choose a new directory to retry.
A malicious concurrent local filesystem writer is outside this single-owner tool's
security boundary; use a private directory with no concurrent edits.

The SHA is **owner-declared provenance**, not proof of executed device source or
APK identity. The JSON and hashes are not signed attestation. Record original CI,
JUnit, APK identities and tested SHA separately. Empty positive recipe lists are
retained as unverified cases and cannot prove negative test success. In particular,
`import12Limitation` contains no positive recipe and is never reported as accepted
stock-import fidelity. No existing Android format is changed to invent such proof.

## Owner-only Windows workflow (NOT EXECUTED by Codex)

Use an authorized dedicated emulator only. Synchronize the feature branch normally
and stop for divergence; record the exact tested commit and clean checkout. The
Git SHA passed below must identify the Android run, not automatically this later
host-tool revision. Never clear logs/storage or repeat retained campaigns without
reviewing remaining space. The connected task must address only the intended AVD.

```powershell
py -3 -m pip install -r test-fixtures\requirements-fixtures.txt
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.pdf.engine.spike.PdfboxAndroidSpikeTest" `
  "-Pandroid.testInstrumentationRunnerArguments.retainPdfboxSpike=true"
# Read existing logs; do not clear them. Record namespaces from this exact run.
adb -s $env:ANDROID_SERIAL logcat -d -s M_PDFBOX_SPIKE:I

py -3 scripts\collect_pdfbox_spike.py `
  --serial $env:ANDROID_SERIAL `
  --namespace m-pdfbox-spike-12345678-1234-1234-1234-123456789abc `
  --namespace m-pdfbox-spike-23456789-2345-2345-2345-23456789abcd `
  --commit 3a0f4067ec671d640bf7ced9d026dec9146e9911 `
  --output C:\PrivateEvidence\pdfbox-collection `
  --adb C:\Android\platform-tools\adb.exe

py -3 scripts\verify_pdfbox_outputs.py `
  --collection C:\PrivateEvidence\pdfbox-collection `
  --fixtures .\test-fixtures `
  --report C:\PrivateEvidence\pdfbox-report --dpi 144
# Optional: --retain-images also retains exact-match PNGs within the artifact cap.

py -3 -m unittest discover -s scripts/tests -p "test_collect_pdfbox_spike.py" -v
py -3 -m unittest discover -s scripts/tests -p "test_verify_pdfbox_outputs.py" -v
```

Replace namespace examples with exact logged values. For full positive coverage,
collect every positive case namespace from one campaign, including `import13` and
`stress0405`; repeat `--namespace` as needed. Retain original JUnit evidence for
negative methods separately. Collection is binary-safe on Windows (Python pipes,
not PowerShell redirection). Linux/macOS use the same Python CLI and adb argument
arrays. The collector uses the repository's canonical fixture manifest by default;
`--fixtures` in the verifier permits an explicit canonical fixture root.

## Offline verification

`verify_pdfbox_outputs.py` imports only existing Python-only fixture dependencies
(pypdf 5/6, PyMuPDF 1.x, Pillow 10–12). No network, ADB, JVM or Android subprocess.
Report directory must be new and disjoint from collection and fixtures; originals
and collected PDFs are read-only. Symlinks/path traversal, duplicate JSON keys,
unknown files, duplicate namespaces/recipes, changed manifest/source/output hashes,
invalid metadata or incomplete collections block. Source sizes and SHA are checked
against the canonical manifest; output sizes/hashes are rechecked against collection
and recipe records. No PDF byte equality is required for a transformed output.

Per selected page: exact composed rotation; MediaBox/CropBox origins/extents and
effective bounds (0.01 point representation tolerance); decoded contents and order;
exact complete PyMuPDF text at the original reference orientation (no stripping or
normalization); recursive resource dictionaries including fonts, XObjects/images,
graphics state/transparency; annotation count/subtype/dictionary/AP and output-page
association. `/P` identities are checked within the output, never compared as raw
IDs across documents. No arbitrary keys ignored: object numbers/stream compression
encodings are not semantics; `/P` is checked independently. Empty/multiple page
streams are handled explicitly. DCT bytes are compared conservatively as encoded
image data, with independent decoded pixels in the visual check.

Semantic traversal is capped at 100,000 nodes, depth 64, 32 MiB per decoded stream,
128 MiB resource graph. Flate and ASCII85 plus DCT image payloads are supported;
unknown objects/cycles/filters (including JPX), predictors/DecodeParms and resource
limit excess are BLOCKED, not ignored or equated. Unsupported pypdf private `_data`
representation also blocks. This intentionally conservative oracle can reject a
valid but different encoding; review instead of weakening expectations. Parsers
still allocate when opening PDFs: these limits are NOT a hard parser heap/CPU cap.
Use only the committed synthetic corpus and its privately retained results, never
adversarial/private/user PDFs. Run on a sufficiently provisioned owner workstation.

### Rotated visual reference

A separate source document is opened from canonical bytes into memory. The reference
page receives `(original rotation + 90 * extra quarter turns) % 360` in memory only;
its rotation is restored in finally and never saved. Actual output is independently
opened/rendered with PyMuPDF using identical matrix resolution, RGB, alpha false,
and annotations enabled. Every additional 90/180/270 case and complex-content case
is rendered. Long unrotated 50/100-page cases structurally check every page but
render first/middle/last only; reports label SAMPLED and list exact covered pages.
No full visual-corpus or hidden-page equivalence claim follows from sampling.

Default 144 DPI, CLI range 72–200, maximum dimension 6000 and 8 million pixels/page.
Per-page processing bounds image lifetimes; accumulated PNG budget 256 MiB. Exact
pixels PASS; any same-sized pixel mismatch is REVIEW with expected/actual/difference
PNGs, different pixels and percent, maximum channel delta, mean absolute error and
RMSE. No pixel tolerance or undocumented blur/cropping. Wrong dimensions FAIL,
with diagnostic images (a padded difference when within the pixel cap). If a
full difference cannot fit the cap it is unavailable, not evidence of equality.
Structural failure always outranks a clean render. Missing/modified content FAIL;
possible renderer disagreement remains REVIEW for owner investigation.

## Reports, coverage and exit policy

Outputs: `validation-report.json`, `validation-report.md`, `visual/`. JSON includes
collection provenance/API/serial/namespaces, canonical hash, parser versions, output
recipes/hashes, per-page structural/visual outcomes and diagnostic paths. Markdown
summarizes each output; inspect JSON for complete metrics and missing evidence.

Expected positive coverage is the exact 27 recipes generated by the current spike
(including both repeated 04 and 05 operations). Missing recipes, unexpected or
excess repeated scenarios make the scoped campaign incomplete/BLOCKED. Empty
negative-case namespaces are explicitly unverified and never supply positive coverage.
`KNOWN_UNSAFE` always records stock import of fixture 12 in a separate ledger;
a submitted positive recipe using that strategy cannot pass and returns nonzero.
The known limitation ledger itself is informational for permitted working-copy
positive evaluation; it does not disappear if all positive recipes PASS.

Aggregate precedence: FAIL, BLOCKED, REVIEW, KNOWN_UNSAFE, then PASS only if all
required positive recipes are present. Missing coverage blocks otherwise green
outputs. No outputs is BLOCKED. CLI exit 0 means this narrowly scoped positive
campaign PASS (with documented long-document visual sampling), exit 1 means report
non-PASS, exit 2 means preflight/dependency/CLI blockage. Missing optional negative
runtime proof remains outside that positive PASS and visible in empty_cases; it is
not full Phase M acceptance. Partial failures preserve a report where the output
directory can safely be created. Inputs are never repaired or deleted.

## Source delivery and remaining gates

Two new scripts and two unittest files; regressions cover namespace/path/metadata
validation, binary transfer/error/truncation/hash limits, JSON and inventories,
synthetic page order/rotation/content and annotation association, rotated visual
references, pixel mismatch metrics, unknown/cyclic resources, partial coverage,
known-unsafe classification and report/exit behavior. Tests require the existing
fixture Python dependencies where they actually parse/render synthetic PDFs;
no Android device is needed. Canonical PDFs are never rewritten.

Source/diff review and `git diff --check` only. **BUILDS / JVM / ANDROID / PYTHON
TESTS / LINT / COVERAGE / DEPENDENCY INSTALLATION / ADB / DEVICE / CI: NOT RUN.**
No Python syntax checker, validator, oracle or collector was executed. Actual runtime
correctness, independent fidelity, full resource/JPX support, security review,
physical resource acceptance, test-APK isolation and production approval remain
owner gates. No production engine approval or Phase M/Block 2 completion is claimed.
