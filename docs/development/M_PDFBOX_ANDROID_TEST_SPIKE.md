# Phase M — PDFBox-Android instrumentation-only candidate spike

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
`m-pdfbox-spike-<UUID>` child. `PDFBoxResourceLoader.init(targetContext.applicationContext)`
precedes candidate calls. Only flat, test-owned files are eligible for deletion;
no original or provider document is writable. Each source copy AND original asset
is rehashed against the manifest in teardown. Cleanup failures fail the test;
uncertain owned handles retain the namespace. Original failures retain secondary
cleanup failures as suppressed exceptions. No global cleanup or automatic retry.

## Exact source test inventory

Class: `com.abdev.partituraspdf.pdf.engine.spike.PdfboxAndroidSpikeTest`.
All **19** block-bodied JUnit4 methods are enabled; none have been executed here.
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
| `stockImportComparedWithWorkingCopyWithoutSilentRepairs` | 02/08/10/11/12/13: stock import/reverse checked against the same expectations; no resource/annotation repair or silent drop |
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
