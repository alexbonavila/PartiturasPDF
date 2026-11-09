# Phase M — PDFBox-Android instrumentation-only candidate spike

> Current evidence update (2026-11-09): [scoped fidelity PASS and open security gates](M_PDFBOX_INDEPENDENT_VALIDATION.md#completed-owner-campaign--2026-11-09). Earlier dated records below remain historical.

2026-11-03. **SOURCE READY FOR OWNER VALIDATION; PRODUCTION NOT APPROVED.**
Starting revision: `32ad93fdcffc2273bc76617bc6880e0f9a5dbe6b` (documentation).
CI #128 tested `edd372aaf6ecc73d78e67655cf8996a2569f0fdf`, not this spike or
its documentation baseline. Its 708 JVM / 445 Android tests per API and nine
successful jobs remain historical evidence only. See the
[engine ledger](M_ENGINE_EVALUATION.md) and
[Block 2 ledger](M_BLOCK2_TEST_AND_ACCEPTANCE_EVIDENCE.md).

## Scope and isolation

The authorized catalog additions are `pdfboxAndroid = "2.0.27.0"` and
`pdfbox-android = { module = "com.tom-roush:pdfbox-android", version.ref = "pdfboxAndroid" }`.
The only app build change is `androidTestImplementation(libs.pdfbox.android)`.
No new repository, optional JP2 codec, BC override, plugin or SDK/toolchain change.
Candidate imports are confined to `src/androidTest/.../pdf/engine/spike/`.
No provider registration, UI, production engine/validator, artifact minting,
transaction/journal/host/SAF/registry change or public destination operation.
Source organization is not proof of final binary isolation.

Fixtures come from the existing test APK assets and unchanged canonical manifest.
Every operation copies its source to a unique target-app `noBackupFilesDir`
`m-pdfbox-spike-<UUID>` child. Fixture assets use `instrumentation.context.assets`;
`PDFBoxResourceLoader.init(pdfboxAssetContext)` uses a private `ContextWrapper`
based on `testContext`, with `getApplicationContext()` returning the wrapper itself.
Inherited asset access delegates to the instrumentation APK. Private storage uses
`instrumentation.targetContext.applicationContext`. Before
creating the namespace, a two-asset, one-byte-per-asset preflight checks nonempty
`glyphlist/additional.txt` and `glyphlist/glyphlist.txt` under the PDFBox resource
prefix using `pdfboxAssetContext.applicationContext.assets`, exactly matching the loader.
Missing/empty assets fail explicitly with asset and context/package diagnostics;
no target-storage copy or replacement glyph lists. Candidate calls follow init.
Only flat, test-owned files are eligible for deletion;
no original or provider document is writable. Each source copy AND original asset
is rehashed against the manifest in teardown. Cleanup failures fail the test;
uncertain owned handles retain the namespace. Original failures retain secondary
cleanup failures as suppressed exceptions. No global cleanup or automatic retry.

## Exact source test inventory

Class: `com.abdev.partituraspdf.pdf.engine.spike.PdfboxAndroidSpikeTest`.
Current source contains **21** enabled block-bodied JUnit4 methods; none executed
by Codex. CI #131 evaluated the previous 19-method inventory at its exact SHA.
Helper files: `PdfboxSpikeSandbox.kt`, `SpikeSemantics.kt`.

| Method | Canonical fixtures / actual planned assertions |
| --- | --- |
| `identitySinglePageSavedAndIndependentlyRendered` | 01: real save/reopen, manifest, semantic snapshot, PDFium raster |
| `reversedAndSelectedPagesRetainDistinctPageLabels` | 02: [1,0] and [1]; distinct source PAGE-ID text, streams and raster/order |
| `eachQuarterTurnIsPersistedForEachSelectedPage` | 02: each source page alone with 1/2/3 additional clockwise quarter turns, six outputs |
| `tenPageSelectionRetainsOrderAndContent` | 03: [9,4,0], label and raster/order |
| `a3AndA5GeometryRemainsIntact` | 06/07: manifest geometry plus persisted media/crop origins and extents |
| `mixedBoxesSurviveSelectionAndAdditionalRotation` | 08: [4,1,2], +90 degrees, media/crop and resource comparison |
| `additionalQuarterTurnComposesWithAllOriginalRotations` | 09: [3,2,1,0], +90 composed with source 0/90/180/270 |
| `scannedImageXObjectsAndPixelsSurvive` | 10: reverse; image XObjects, decoded resource bytes, PDFium raster |
| `vectorContentEmbeddedFontsAndGlyphPixelsSurvive` | 11: reverse; text, decoded streams/resources, embedded font presence, raster |
| `annotationTypesAssociationsAndAppearancesSurvive` | 12: reverse; all five annotations, four subtypes, full dictionaries/AP data and containing-page associations |
| `complexResourcesTransparencyAndPixelsSurvive` | 13: [2,0,1]; text/fonts, resource graph including ExtGState, raster |
| `stockImportComparedWithWorkingCopyWithoutSilentRepairs` | 02/08/10/11: positive stock import/reverse checks; no repair or silent drop |
| `stockImportComplexFixtureIsEvaluatedIndependently` | 13: independent positive stock import/reverse semantics and PDFium |
| `stockImportPersistedAnnotationsRetainInvalidExternalPageAssociation` | 12: expected-negative persisted external /P relationship; NOT fidelity acceptance |
| `passwordProtectedAndCorruptedInputsCannotProduceOutputs` | 14/15: expected parser rejection, no save/output; no password/decrypted output |
| `fiftyAndHundredPagesHaveBoundedRepeatedResourceObservations` | 04/05: exactly two full identity operations each; all pages inspected/rendered; no warmup or timing acceptance threshold |
| `limitedOutputAndTruncatedCopyCloseWithoutSuccessfulResult` | 02: 64-byte output fault and separately truncated 64-byte input; required rejection, closed owned handles |
| `semanticChecksDetectMissingPageAndWrongRotation` | 02/09: save/reopen mutations; exact semantic equality must fail |
| `semanticChecksDetectMissingAnnotationAndAppearance` | 12: separate missing annotation/AP mutations; equality must fail |
| `semanticChecksDetectDroppedFontsImagesAndTransparency` | 11/10/13: remove Font/XObject/ExtGState; persisted resource equality must fail |
| `syntheticJpxFilterIsRejectedBeforeResourceDecoding` | Synthetic COS dictionary, no new fixture: rejects JPXDecode; NOT proof of real JPX detection/fidelity |

A method containing several operations stops at its first genuine failure. Retain
that case's evidence and classify downstream cases as not completed, not passed.
Stock import has an independent method so its failure does not rewrite the
working-copy result. Full case-level results must be inspected, not just totals.

## What the oracles do and do not prove

`SpikeSemantics` captures each source page before transformation and compares
actual saved/reopened output against zero-based selections and
`(sourceRotation + 90 * quarterTurns) mod 360`, matching `PageSelection`.
It checks effective media/crop coordinates, decoded content bytes, text, resource
graphs (fonts/images/forms/graphics state where present), annotation dictionaries,
AP streams, subtypes and page associations. Fingerprints ignore indirect object
numbers and stream compression encodings only; annotation `/P` is checked as an
association separately. Cycles/unknown COS types, excessive recursion/nodes,
nonfinite numbers or decode limits fail rather than become equal. This conservative
oracle can reject a valid reserialization: inspect such a failure, do not relax
it blindly. Same-PDFBox reparsing is **not independent semantic validation**.

Android `PdfRenderer` (PDFium) supplies separate fixed-resolution source/output
pixel hashes for zero-extra-rotation cases, including fonts, raster images and
transparency. Blank renders fail. It is additional rendering evidence, not a
complete semantic/annotation oracle. Extra-rotation cases use persisted geometry
and require the separate owner oracle below for independent validation. Corpus
coverage for inherited resources, nondefault crop origins, links/forms, signatures,
complex annotation relationships and real JPX is incomplete. The manifest gives
page sizes/rotation/counts, not independent nonzero crop-origin expectations. No generated content replaces originals.
JPX detection traverses inspected resource/AP streams, not every possible PDF
object or inline image; real positive detection/fail-closed acceptance remains
**BLOCKED**. No optional codec is installed.

## Bounded observations and evidence

Per namespace: 64 created file slots maximum; source 64 MiB, output 128 MiB,
aggregate observed file bytes 256 MiB, 64 MiB free-space margin, 64 KiB streams.
Output writes stop before crossing their limit; failed output is never recorded
as a successful operation. No public publication occurs. File margins are local
observations, not storage reservation against external writers. Default PDFBox
in-memory buffering is deliberately measured, not claimed to have a heap cap.

Each parse, inspect, transform, save, reopen, close and PDFium render records
monotonic **nanoseconds**, before/after Java heap used bytes, native heap allocated
bytes, process PSS **KiB**, observed process FD count, tracked owned documents/
descriptors/bitmaps/save streams and temporary bytes. Output records include size,
SHA-256, fixture hash and selection/rotation/approach recipe. The instrumentation
process PID/API is recorded; no isolated editing process is claimed. Harness-owned
counters do not enumerate hidden parser/native handles; `/proc/self/fd` count can
include unrelated instrumentation. Zero owned handles and successful private
cleanup are required. No forced GC, sleeps, parser retries or artificial latency
budgets. Phase endpoints are observations, **not true or sampled continuous peaks**.
PSS is process-wide and includes instrumentation; it is not isolated engine usage.

The bounded operation count does not bound parser heap/CPU on arbitrary inputs.
No OOM/bomb stress test is authorized. Hangs/crashes/OOM or incomplete metrics are
resource FAIL/INCONCLUSIVE, never proof of feasibility. API26/27/36 and relevant
physical Android 8.1 memory/latency assessment on 2–3 GiB devices remain owner gates.
Finite observations cannot prove leak absence.

## Owner-only build, dependency and runtime commands — NOT RUN by Codex

From the published spike revision, retain exact Git SHA/clean status, JDK/Gradle,
actual device API/ABI/RAM and runner output. Use the existing CI API26/27/36 jobs;
no workflow changes or dispatch by Codex. No private/untrusted PDFs or network
endpoint testing with this candidate.

```bash
bash ./gradlew :app:dependencies --configuration debugAndroidTestRuntimeClasspath
bash ./gradlew :app:dependencies --configuration debugRuntimeClasspath
bash ./gradlew :app:dependencies --configuration releaseRuntimeClasspath
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
bash ./gradlew :app:testDebugUnitTest :app:lintDebug
```

Inspect each produced main debug/release APK and the instrumentation APK with APK
Analyzer, including DEX/classes/assets/licenses/native payloads. Confirm PDFBox and
BC absence in BOTH production runtime graphs and binaries, and exact presence in
test graph/APK; graph names/debug compilation alone are insufficient. Record artifact
hashes, versions and size deltas against a matched baseline build. Check merged
permissions; the upstream tagged library manifest is empty, but the actual resolved
and packaged graph has not been inspected. No release configuration change allowed.

Focused execution on a separately authorized disposable emulator, using existing
runner and optional explicit retention of **synthetic generated copies only**:

```bash
ANDROID_SERIAL='<authorized-emulator-serial>' bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.pdf.engine.spike.PdfboxAndroidSpikeTest \
  -Pandroid.testInstrumentationRunnerArguments.retainPdfboxSpike=true
```

Default execution deletes generated copies; retention is off unless the argument
is exactly `true`. Do not run repeated retained campaigns without checking remaining
private space. Retained `evidence.json` recipes are observations, not validation
receipts; an output is recorded before subsequent semantic assertions and may fail.
Negative/fault outputs are not included in that recipe list; no recipe is an acceptance receipt. Preserve JUnit
failures/logs separately. No automatic PDF upload to GitHub Actions artifacts.

For independent review, owner alone may retrieve a named generated output and
`evidence.json` from the logged `retained_namespace` on that emulator. Use an explicit
serial, verify the namespace matches `m-pdfbox-spike-<UUID>` and allow only basenames
listed in evidence or explicitly reviewed fault files. Example (replace placeholders,
never point at another directory):

```bash
adb -s '<authorized-emulator-serial>' exec-out run-as com.abdev.partituraspdf \
  cat 'no_backup/m-pdfbox-spike-<UUID>/evidence.json' > '<private-local-case-dir>/evidence.json'
adb -s '<authorized-emulator-serial>' exec-out run-as com.abdev.partituraspdf \
  cat 'no_backup/m-pdfbox-spike-<UUID>/result-2.pdf' > '<private-local-case-dir>/result-2.pdf'
```

Inspect exit status and hashes; retrieve only this run's synthetic files. Never
archive all app-private storage. Owner separately removes only verified test-owned
namespaces after review; Codex runs no retrieval or deletion. Record execution SHA,
API and output hashes alongside retrieved evidence; its JSON alone is not source/APK
attestation. The normal suite remains required, not replaced by focused execution.

## Separate Python-only semantic oracle — owner-run, NOT RUN here

Use the already approved `test-fixtures/requirements-fixtures.txt` toolchain on the
workstation, not Android dependencies. The canonical fixture verifier validates
originals only; it does not accept transformed outputs. The following read-only
example checks retrieved positive recipes against canonical source with **pypdf
and PyMuPDF**, independent of PDFBox. Run once per private case directory from repo
root. A mismatch fails, unsupported/cyclic structure or unavailable oracle is
BLOCKED/UNVERIFIED; do not reinterpret as preservation. Do not use `python -O`.

```bash
python3 - '<private-local-case-dir>' <<'PY'
import hashlib, json, math, sys
from pathlib import Path
import fitz
from pypdf import PdfReader
from pypdf.generic import (ArrayObject, DictionaryObject, StreamObject,
    IndirectObject, NullObject, BooleanObject, NameObject, ByteStringObject,
    TextStringObject, NumberObject, FloatObject)
root = Path(sys.argv[1]).resolve(strict=True)
manifest_bytes = Path('test-fixtures/manifest.json').read_bytes()
manifest = json.loads(manifest_bytes)
entries = {x['id']: x for x in manifest['files']}
evidence = json.loads((root / 'evidence.json').read_text())
assert hashlib.sha256(manifest_bytes).hexdigest() == evidence['manifest_sha256']
assert evidence['outputs'], 'No completed saved-output recipes'
def canonical(raw, active=None, depth=0, ignore=frozenset()):
    active = set() if active is None else active
    assert depth <= 64, 'BLOCKED: graph depth'
    value = raw.get_object() if isinstance(raw, IndirectObject) else raw
    if value is None or isinstance(value, NullObject): return None
    ident = id(value)
    assert ident not in active, 'BLOCKED: cyclic semantic graph'
    active.add(ident)
    try:
        if isinstance(value, DictionaryObject):
            skipped = ignore | ({'/Length','/Filter','/DecodeParms'} if isinstance(value, StreamObject) else set())
            result = {str(k): canonical(v, active, depth+1) for k,v in sorted(value.items()) if k not in skipped}
            if isinstance(value, StreamObject):
                result['decoded-sha256'] = hashlib.sha256(value.get_data()).hexdigest()
            return result
        if isinstance(value, ArrayObject): return [canonical(v, active, depth+1) for v in value]
        if isinstance(value, BooleanObject): return ('bool', value.value)
        if isinstance(value, (NumberObject, FloatObject)):
            n = float(value); assert math.isfinite(n); return ('number', n)
        if isinstance(value, ByteStringObject): return ('bytes', bytes(value).hex())
        if isinstance(value, TextStringObject): return ('text', str(value))
        if isinstance(value, NameObject): return ('name', str(value))
        raise AssertionError('BLOCKED: unsupported semantic value')
    finally: active.remove(ident)
for recipe in evidence['outputs']:
    name = recipe['file']; assert Path(name).name == name and not (root/name).is_symlink()
    output = (root/name).resolve(strict=True); assert output.parent == root
    entry = entries[recipe['fixture']]; source = Path('test-fixtures') / entry['path']
    assert hashlib.sha256(source.read_bytes()).hexdigest() == entry['sha256'] == recipe['source_sha256']
    assert hashlib.sha256(output.read_bytes()).hexdigest() == recipe['sha256']
    assert output.stat().st_size == recipe['bytes']
    src, dst = PdfReader(source), PdfReader(output)
    assert not src.is_encrypted and not dst.is_encrypted
    assert len(dst.pages) == recipe['pages'] == len(recipe['indices'])
    with fitz.open(source) as fs, fitz.open(output) as fd:
        for j, i in enumerate(recipe['indices']):
            a, b = src.pages[i], dst.pages[j]
            assert b.rotation % 360 == (a.rotation + 90*recipe['extra_quarter_turns']) % 360
            for attr in ('mediabox','cropbox'):
                assert all(abs(float(x)-float(y)) < .01 for x,y in zip(getattr(a,attr),getattr(b,attr)))
            assert a.get_contents().get_data() == b.get_contents().get_data()
            assert canonical(a['/Resources']) == canonical(b['/Resources'])
            aa, bb = a.get('/Annots',[]), b.get('/Annots',[])
            assert len(aa) == len(bb)
            for old,new in zip(aa,bb):
                assert canonical(old, ignore={'/P'}) == canonical(new, ignore={'/P'})
                p = new.get_object().get('/P')
                if p is not None: assert p == b.indirect_reference, 'Wrong annotation page'
            assert fs[i].get_text() == fd[j].get_text()
            # Pixel comparison is valid here only without an added orthogonal rotation.
            if recipe['extra_quarter_turns'] == 0:
                x,y = fs[i].get_pixmap(matrix=fitz.Matrix(.3,.3),annots=True), fd[j].get_pixmap(matrix=fitz.Matrix(.3,.3),annots=True)
                assert (x.width,x.height,x.samples) == (y.width,y.height,y.samples)
    print('INDEPENDENT CHECKS PASS', entry['id'], recipe['approach'], name)
PY
```

This oracle example does not establish full equivalence, adversarial-input safety,
JPX detection or resource feasibility. It does not independently raster-compare
extra rotations, prove hidden native closure, or validate every possible feature.
Review rotated-page appearances manually/with a separately approved oracle.
Negative saved-output cases are exercised by Android semantic assertions; retrieve
and apply expected-failure comparisons separately if independent mutation evidence
is required. Do not treat absence of that evidence as full fidelity acceptance.

## Gate ledger and next owner decision

| Gate | Current status / required evidence |
| --- | --- |
| Source | SOURCE READY for compilation/review only |
| Gradle/test-APK isolation | NOT RUN: actual resolution, main/test binary inspection and size/permission comparison |
| API26 / API27 / API36 | NOT RUN on this increment; exact JUnit inventory and zero failures/skips required per API |
| Functional PDF | NOT RUN: saved/reopened selection, persisted rotation and structural case results |
| Semantic fidelity | UNVERIFIED: same-parser and PDFium source ready; separate pypdf/PyMuPDF + manual results pending |
| JPX and absent feature oracles | BLOCKED / NOT TESTED with real JPX, inherited-resource/link/form fixtures |
| Security | OPEN: affected pinned transitives, unknown reachability/completeness; separate owner risk decision |
| Memory/resource | NOT MEASURED / INCONCLUSIVE until exact phase observations and device review |
| M-specific production coverage | Existing proposal remains NOT CONFIGURED / NOT MEASURED; unchanged gates |
| Production approval | NOT APPROVED, regardless of candidate test outcome |

Codex ran source/diff inspection and `git diff --check` only. All BUILD / JVM /
ANDROID / LINT / COVERAGE / DEPENDENCY RESOLUTION / SECURITY SCAN / CI / DEVICE /
PROJECT VALIDATORS: **NOT RUN**. Publication is not acceptance. Owner must classify
resolution, packaging, SDK, runtime parser, fidelity, resource and unrelated failures
separately; no automatic dependency override, feature expansion or production fallback.

## CI #129 resource-loader correction — 2026-11-03

Owner-provided evidence for exact spike commit
`648c80c9b28de0eb4f1be3570f609af9e06e9859`:
https://github.com/alexbonavila/PartiturasPDF/actions/runs/41672861285.
Build, JVM (708 PASS), Lint, localization and PDF fixtures succeeded; API26,
API27, API36 and quality-gate failed. Each Android API ran 464 tests: 449 PASS,
15 FAIL. All 445 existing tests and four candidate tests passed; 15 of the 19
candidate tests failed. Codex did not access CI/logs or execute these checks.

Primary error: `ExceptionInInitializerError` caused by missing
`com/tom_roush/pdfbox/resources/glyphlist/additional.txt` in
`PDFBoxResourceLoader.getStream` / `LegacyPDFStreamEngine.<clinit>`.
Subsequent `NoClassDefFoundError: PDFTextStripper` is consistent with failed
static initialization, not demonstrated absent bytecode or semantic incompatibility.

Source inspection of the pinned v2.0.27.0
[loader](https://github.com/TomRoush/PdfBox-Android/blob/v2.0.27.0/library/src/main/java/com/tom_roush/pdfbox/android/PDFBoxResourceLoader.java)
confirms `init(context)` stores `context.getApplicationContext().getAssets()`.
The original target-app context selected resources outside the instrumentation-only
candidate packaging boundary. The correction passes `testContext` and separately
retains target-app private storage. Fixtures continue using the test APK manager.
Before directory creation and before candidate text initialization, both required
glyph lists are opened and checked nonempty through the exact application-context
manager used by the loader. Streams close deterministically after at most one byte.
If that manager cannot expose the assets, diagnostics identify the required path,
actual application package and instrumentation package: this remains a packaging/
context BLOCKER, with no fallback, synthetic resource or Gradle change.

Preflight failure creates no directory. If subsequent loader init fails after
successful mkdir, existing cleanup deletes only the owned empty namespace and
attaches secondary cleanup failure to the original exception. No filesystem or
other provider context is changed. No attempt to recover a previously poisoned
static class in the same process: owner validation needs a fresh test process.

All 19 methods, semantic/negative expectations, PDFium comparisons, source hashes,
annotation checks and resource constraints are unchanged. Only the sandbox and
two authorized documents change; dependency/catalog/build/manifests, production
and other tests remain unchanged. No new compatibility/fidelity/safety/production
acceptance is inferred. Further genuine candidate failures must remain visible.

Full diff/source-scope inspection and `git diff --check` only. BUILD / JVM / ANDROID
INSTRUMENTATION / LINT / COVERAGE / CI / DEVICE / PROJECT VALIDATORS: **NOT RUN**
for this correction. Owner must validate packaging/preflight and the complete
API26/27/36 suite on the new publication SHA. All prior gate limitations remain.

## CI #130 instrumentation application-context correction — 2026-11-03

Owner-provided run: https://github.com/alexbonavila/PartiturasPDF/actions/runs/41677282609
at `ddb3b90e6072abbdbd424beabcc90514144fb44c`. Build, JVM (708 PASS), Lint,
localization and fixtures PASS; API26/27/36 and quality-gate FAIL. Each Android API
executed 464 tests: 445 existing tests PASS, all 19 candidate tests FAIL during
sandbox construction. No new transformation/fidelity failure is established.
These results are owner evidence, not CI/log access or execution by Codex.

The exact failure was `IllegalStateException: PDFBox resource application context
unavailable for com.abdev.partituraspdf.test`. The instrumentation context's
applicationContext was null on all three APIs; the CI #129 correction therefore
failed before checking resource availability. Passing testContext directly cannot
satisfy upstream init's `context.getApplicationContext().getAssets()` contract.

The private harness now uses this test-only adapter:

```kotlin
private val pdfboxAssetContext: Context = object : ContextWrapper(testContext) {
    override fun getApplicationContext(): Context = this
}
```

Inherited getAssets delegates to testContext. Both the two-glyph-list preflight
and `PDFBoxResourceLoader.init(pdfboxAssetContext)` select that same instrumentation
asset manager, without accessing testContext.applicationContext or changing any
global context. Missing/empty assets retain explicit path/instrumentation-package
diagnostics and the original IOException cause. Streams still close after the
bounded nonempty check. Preflight precedes mkdir; post-mkdir init failure still
preserves its primary exception and suppressed cleanup failures.

Fixture access remains testContext.assets; targetContext.noBackupFilesDir, quotas,
namespace ownership and teardown are unchanged. All 19 test methods, assertions,
negative cases and semantic expectations remain enabled and unmodified. No
production, dependency/build, manifest, permission, fixture or CI change.

Full diff/source review and `git diff --check` only. BUILD / JVM / ANDROID /
INSTRUMENTATION / LINT / COVERAGE / CI / DEVICE / VALIDATORS: **NOT RUN** for this
correction. Actual glyph-list presence in the built test APK remains unverified;
owner must run complete CI at the new SHA. No compatibility, security, fidelity,
production-engine approval or Block 2 completion is claimed. Prior evidence and
all remaining gates are preserved.


## CI #131 functional evaluation correction — 2026-11-03

Owner-provided CI https://github.com/alexbonavila/PartiturasPDF/actions/runs/41681088740
at `90fd7debf5428911cd0c3440140ea3a302cd9eb3`: build/JVM (708 PASS)/Lint/
localization/fixtures PASS; API26/27/36 and quality-gate FAIL. Each API: 464 tests,
460 PASS / 4 FAIL; all 445 existing tests PASS, candidate 15 PASS / 4 FAIL.
No CI/log access or runtime execution by Codex for this correction.

The three rotation methods (eachQuarterTurnIsPersistedForEachSelectedPage,
mixedBoxesSurviveSelectionAndAdditionalRotation,
additionalQuarterTurnComposesWithAllOriginalRotations) differed in PDFTextStripper
layout while observed structural fields matched. Reopened persisted rotations are
now asserted against the composed expectation before extraction. Only additional-
rotation cases extract complete text temporarily at the source reference rotation,
restore the actual saved rotation in finally, and compare text exactly. No text,
whitespace, punctuation or glyph removal. Unrotated snapshot behavior and exact
comparison remain unchanged. No temporary orientation is saved; wrong-rotation
mutation detection and all geometry/content/resource/annotation checks remain.
Independent rotated raster/visual acceptance remains pending, not implied by this
same-parser text comparison. Zero-extra-rotation PDFium assertions are unchanged.

Stock import fixture 12 exhibited an annotation /P outside its containing page:
a real candidate limitation, not an oracle issue. The negative regression
stockImportPersistedAnnotationsRetainInvalidExternalPageAssociation performs real
import/save/reopen, checks retained annotation dictionaries excluding /P, requires
explicit associations, and requires an incorrect /P outside the output page tree
whose content matches its original source page. At least one such relationship
must be observed. No broad exception is accepted; unrelated failures propagate.
No annotation is repaired, removed or accepted as valid. A passing negative test
only detects the limitation; stock import remains unsuitable for this scenario.

The original positive import method retains 02/08/10/11. New independent method
stockImportComplexFixtureIsEvaluatedIndependently checks 13 (previously never
reached after 12 failed) with the same positive semantic/PDFium assertions.
Source inventory is now 21 methods, not an executed count. All prior scenarios,
negative mutations, fixture hashes, resource limits and cleanup are retained.
Sandbox, production, dependencies, manifests, original fixtures and non-spike tests
are unchanged. BUILD / JVM / ANDROID / LINT / COVERAGE / CI / VALIDATORS: **NOT RUN**.
Full source/diff review and git diff --check only; owner CI must validate the new
SHA. Fidelity, independent rotated visuals, security, JPX, physical resources and
production approval remain open; Phase M/Block 2 completion is not claimed.


## Independent Python fidelity tooling — source delivery, 2026-11-03

Baseline `3a0f4067ec671d640bf7ced9d026dec9146e9911`. Owner reports CI #132 attempt 2
[41687967334](https://github.com/alexbonavila/PartiturasPDF/actions/runs/41687967334)
PASS: nine jobs, 708 JVM, 466 Android on each API26/27/36. This is historical owner
evidence, not independent Python fidelity or CI evidence for the new host tools.

Added bounded explicit-namespace binary collector, offline pypdf/PyMuPDF/Pillow
structural and rotated raster verifier, and synthetic/mock regression tests.
See [owner workflow, limits, classifications and PowerShell commands](M_PDFBOX_INDEPENDENT_VALIDATION.md).
The known fixture 12 stock-import association remains KNOWN_UNSAFE; positive
working-copy results do not clear it. Source tools have not been executed, and
empty negative recipes cannot prove negative success. Missing positive scenarios
block complete campaign acceptance; long-document visual sampling is explicit.
Android code/evidence schema, dependencies, CI and immutable fixtures unchanged.

Source/full diff review and git diff --check only. BUILDS / JVM / ANDROID / PYTHON
TESTS / LINT / COVERAGE / DEPENDENCY INSTALLATION / ADB / DEVICE / CI: **NOT RUN**.
No compatibility/fidelity/security/production approval or Phase M completion claim.


## Independent positive fidelity acceptance — 2026-11-09

**PASS / COMPLETE within the specified synthetic positive-transformation scope**,
owner-verified at `134fd0f8d0599c70a4e1de5a06b83478b49c422d` on Windows 10,
dedicated API26 emulator-5554. Full CI #135 was previously verified by the owner:
https://github.com/alexbonavila/PartiturasPDF/actions/runs/41720613841.
No CI/log access, build, test or device execution by Codex for this documentation.

Owner manual debug/test APK install and `am instrument -w` of the focused class
with retainPdfboxSpike=true produced OK (21 tests), with app installed and run-as
available. Collector COLLECTED; independent verifier exit 0, Overall PASS,
Missing scenarios 0. Positive results: 27/27 recipes (22 WORKING_COPY, five
STOCK_IMPORT), 345 structural pages PASS, 57 visual comparisons at 144 DPI with
exact pixel equality and zero different pixels. Four long outputs sampled first/
middle/last: 04 indices 0/25/49 and 05 indices 0/50/99, each repeated twice.
The 288 other pages were structurally verified, NOT individually raster-compared.
Additional rotated-page positive comparisons are complete within that scoped
campaign, not independent visual acceptance of arbitrary PDFs.

Python 3.15.0 / PyMuPDF 1.28.2 / pypdf 6.20.1 / candidate 2.0.27.0;
report timestamp 2026-11-09T18:13:08.859917+00:00. Raw reports remain with the
owner and are unavailable in this workspace. Their supplied hashes, exact tool/
manifest identities, successful Windows ADB command and source CRLF/LF byte
comparison are registered in the [canonical campaign addendum](M_PDFBOX_INDEPENDENT_VALIDATION.md#completed-owner-campaign--2026-11-09).
No claim that Codex independently rehashed those unavailable reports or attested
installed APK identity; the tested Git SHA is owner-supplied provenance.

Stock import fixture 12 remains **KNOWN_UNSAFE** (external annotation /P), while
its WORKING_COPY case passed. Eleven empty negative namespaces still lack
standalone independent oracle proof; absence of output is not negative acceptance.
No complete JPX/JPEG2000, full long-document raster coverage, unusual forms/
inherited resources/filters, malformed-input safety or unrestricted PDF equivalence.

Next owner gate: [security/dependency/packaging/licensing audit and hardware handoff](M_PDFBOX_SECURITY_AND_PACKAGING_GATES.md).
Security and production packaging remain OPEN; candidate production approval is
NOT GRANTED and Phase M Block 2 is not complete. Prior dated FAIL/NOT RUN evidence
remains unchanged; this scoped PASS supersedes only the matching pending positive
fidelity/rotated visual evidence. Builds/tests/validators/resolution/install/ADB/
devices/scanners/CI: **NOT RUN by Codex** for this documentation increment.
