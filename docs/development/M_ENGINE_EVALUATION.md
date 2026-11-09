# M engine evaluation — 2026-11-03

> Current evidence update (2026-11-09): [scoped fidelity PASS and open security gates](M_PDFBOX_INDEPENDENT_VALIDATION.md#completed-owner-campaign--2026-11-09). Earlier dated records below remain historical.

**NO SUITABLE ENGINE VERIFIED. ENGINE-BACKED TRANSFORM/VALIDATION BLOCKED.**
No dependency approval, installation, compilation, native execution or semantic
validation evidence exists for Block 2. No production validator can mint a token.

## Evidence actually obtained

Read-only curl requests (no dependency installation) on 2026-11-03:

- [Maven metadata](https://repo.maven.apache.org/maven2/com/tom-roush/pdfbox-android/maven-metadata.xml): latest/release **2.0.27.0**, lastUpdated **20230102065914**.
- [Published POM](https://repo.maven.apache.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.pom): Apache-2.0 AAR; bcprov-jdk15to18, bcpkix-jdk15to18, bcutil-jdk15to18 **1.72**.
- [Current repository README](https://github.com/TomRoush/PdfBox-Android): based on PDFBox 2.0.27; API19+ claimed; application-context resource initialization required; optional JP2Android 1.0.3 historically on JCenter; **JPX images ignored with warning without it**.
- Current master library/build.gradle declares Bouncy Castle **1.73**. This is NOT evidence the published 2.0.27.0 AAR contains 1.73 or newer security fixes.
- GitHub commits API and Apache security-page requests: **HTTP 403**. Latest maintenance commit/date and complete version-specific advisory assessment remain unavailable. Initial sandbox requests failed at the proxy; read-only permitted network retry obtained only the above metadata. No CI accessed.

## Comparison and decision

| Candidate | Findings / unresolved risk | Disposition |
| --- | --- | --- |
| A: com.tom-roush:pdfbox-android:2.0.27.0 | Only pinned Android candidate verified published. Old upstream base/transitives; API19+ vendor claim, not our API26 proof. JPX silent omission violates fail-closed content preservation unless independently detected/rejected. Fonts/forms/XObjects/annotations/resource importing, stream/temp allocations and native close behavior unverified. | Evaluation candidate only; NOT approved for production installation |
| B: modern Apache PDFBox 2.x/3.x Android port | No pinned maintained Android-compatible adapter and API26 runtime/transitive evidence verified in this session. Upstream JVM release numbers do not prove Android compatibility or fix inclusion in A. | No qualifying candidate verified |
| C: another maintained Android editor | No pinned artifact/license/security/maintenance and non-destructive fidelity evidence established. No proprietary/network substitute or homegrown parser introduced. | No qualifying candidate verified |

Root project license remains CC BY-NC-ND 4.0. Apache-2.0 dependency attribution,
license/NOTICE and modification notices must remain distinct and distributed as
required; the app license cannot relicense Apache components. This is not a legal
clearance. Bouncy Castle obligations and exact bundled security advisories need
complete review. No CVE absence claim: denied advisory access is an evidence gap.
Parser recursion, compressed-object/XML/ZIP bombs, JBIG2/JPX/images/fonts, security
handlers and input-dependent heap/CPU are not bounded by a 64 KiB copy buffer.
APK footprint and API26/27/36 execution are NOT MEASURED.

## Required evidence before approval/integration

Approve a pinned engine only after closing security/maintenance/transitive/license
questions and agreeing document-feature rejection policy. Test fixture 02 reordered
and 90/180/270 rotation; 08 page boxes; 09 existing rotation composition; 10 raster;
11 vector/text/font retention; 12 annotation dictionaries/appearance; 13 complex
resources/transparency; reject 14 encrypted/protected and 15 corrupt. Add independent
reparse/page tree/count/order/geometry/resource/annotation/content expectations,
not producer-memory assertions or SHA-only checks. Deliberate mutations must fail.
Fixtures 01/03/04/05 add count/resource cases, 06/07 their manifest cases. All 15
originals immutable. Native FD/stream close, allocation peaks and CPU on API26,
API27 and recent Android remain owner-run prerequisites. No library installed
means no honest actual parser/transform/semantic validator implementation yet.

## Proposed exact build additions — NOT APPLIED / NOT APPROVED

Only if the owner explicitly approves candidate A with its unresolved risks resolved:

```toml
# gradle/libs.versions.toml
[versions]
pdfboxAndroid = "2.0.27.0"
[libraries]
pdfbox-android = { module = "com.tom-roush:pdfbox-android", version.ref = "pdfboxAndroid" }
```

```kotlin
// app/build.gradle.kts dependencies, no toolchain/repository changes
implementation(libs.pdfbox.android)
```

These are a proposal, not a recommendation to approve the old candidate unchanged.
No transitive overrides, optional JPX dependency, repository expansion or dependency
license changes are implicitly approved. The owner engine gate remains OPEN;
there is no verified safe pinned recommendation to install automatically.

## Owner-supplied research update — 2026-11-03 (not independently re-fetched here)

The approved continuation brief supplies external facts unavailable to the prior
restricted requests. Its primary references are retained below. They supplement,
not retroactively replace, the earlier recorded HTTP 403 evidence gap.

- BC Java CVE-2023-33202: <=1.72 affected, fixed 1.73, crafted PEM/ASN.1 OOM.
- CVE-2026-5598 / GHSA-p93r-85wp-75v3: bcprov-jdk15to18 1.71–<1.80.2,
  fixed 1.80.2; described FrodoKEM context, reachability in PDF operation unproven.
- CVE-2024-34447 / GHSA-4h8f-2wvx-gg5w: <1.78, TLS hostname validation;
  dependency affected does not establish exploitability in an offline PDF path.
- Apache's 2026 ExtractEmbeddedFiles path-traversal advisories concern the example
  module; do not label these as demonstrated core-parser vulnerabilities.
  Malformed PDF CPU/OOM/infinite-loop availability risks still matter to this app.
- Public PdfRenderer.write is API35, unsuitable for mandatory API26 editing;
  PdfDocument Canvas recreation/rasterization is not object-preserving editing.
  PDFium/viewer reordering alone does not establish a maintained editing/save port.

Owner research references:
https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902023%E2%80%9033202
https://github.com/advisories/GHSA-p93r-85wp-75v3
https://github.com/advisories/GHSA-4h8f-2wvx-gg5w
https://pdfbox.apache.org/security.html
https://developer.android.com/reference/android/graphics/pdf/PdfRenderer
https://developer.android.com/reference/android/graphics/pdf/PdfDocument
https://github.com/barteksc/PdfiumAndroid

## One specific owner decision: confined test-only dependency spike

Recommend continuing rejecting production engine defaults (Option B) while asking
approval for Option A: **test-only Android compatibility/security/fidelity spike**
for com.tom-roush:pdfbox-android:2.0.27.0, not production engine acceptance.
The exact catalog proposal above would be paired with ONLY:

```kotlin
androidTestImplementation(libs.pdfbox.android)
```

This supersedes `implementation(...)` for the proposed spike ONLY; production
installation/minting would need later separate approval after evidence. No build
file is changed now. No coordinated BC upgrade, exclusions, JCenter, JP2/JBIG2
codec, native PDFium, production UI or parser fallback is bundled into approval.

Bill of materials: one Android AAR 2.0.27.0 + bcprov-jdk15to18 1.72,
bcpkix-jdk15to18 1.72, bcutil-jdk15to18 1.72 as published. Resolve/record complete
transitives and hashes in the owner-run spike, audit BC license/attribution and
exact reachable algorithms, reject JPX before transformations, no silent omission.
API19+ is a vendor claim, not proof of our API26/API27 resource/fidelity behavior.
No new native ABI artifact is proposed; investigate optional image codec/native
requirements instead of installing them. Heap/CPU/input-compression limits,
source/lease/native close ownership and temp allocation charges need measurement.

Owner-run acceptance matrix: fixtures 02/08/09/10/11/12/13 plus rejection 14/15,
independent reparse/object/page/resource/annotation expectations and deliberately
mutated outputs, 50/100-page resource trials, API26/27/36 and physical Android8.1
as applicable. No operation publicly enabled; no main engine token minting until
that evidence is reviewed. Tests/builds/CI remain owner-executed, NOT RUN here.

## Test-only spike authorization and evidence ledger — 2026-11-03

**SOURCE READY FOR OWNER VALIDATION; PRODUCTION APPROVAL: NOT APPROVED.**
This dated entry supersedes earlier *test-only dependency approval pending*
statements only. The earlier production-installation proposal remains unapproved.
The owner approved the pinned catalog alias and exactly
`androidTestImplementation(libs.pdfbox.android)` on `feat/pdf-safe-write-m`, from
`32ad93fdcffc2273bc76617bc6880e0f9a5dbe6b`. No `implementation` or
`debugImplementation`, engine adapter, production validator/minting, optional codec,
repository/BC override, host wiring or public document write is introduced.
See [test inventory, retrieval/oracle protocol and gates](M_PDFBOX_ANDROID_TEST_SPIKE.md).
CI #128 still applies only to `edd372aaf6ecc73d78e67655cf8996a2569f0fdf`.

### Read-only upstream evidence obtained for this increment

Public source/metadata requests via curl and upstream `git ls-remote --tags`,
2026-11-03; no Gradle resolution, archive installation or security scanner:

| Reference | Observation / limitation |
| --- | --- |
| [Maven metadata](https://repo.maven.apache.org/maven2/com/tom-roush/pdfbox-android/maven-metadata.xml) | Latest/release 2.0.27.0; lastUpdated 20230102065914. This corroborates the 2023-01-02 metadata date, not an independent artifact publication timestamp. |
| [Versioned POM](https://repo.maven.apache.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.pom) | AAR, Apache-2.0 declaration; bcprov-jdk15to18, bcpkix-jdk15to18, bcutil-jdk15to18 all declared 1.72. Actual resolved graph/bytecode/hashes: NOT RUN / UNKNOWN. |
| [Release source tag](https://github.com/TomRoush/PdfBox-Android/tree/v2.0.27.0) | Remote tag at 45da92629dad5b3c9887eceefecc89a1423f5457. Read PDDocument/PDPage/PDResources/COSStream/COSName/COSBoolean/PDAnnotation/PDFRenderer/Splitter, README and library manifest from this tag; no source-to-AAR identity attestation. |
| [Tagged README](https://github.com/TomRoush/PdfBox-Android/blob/v2.0.27.0/README.md) | API19+ vendor statement and application-context resource-loader requirement; optional JP2Android/JCenter warning and ignored JPX images without codec. README still names 2.0.26 in examples/base: do not mistake it for a resolved 2.0.27 binary identity. |
| [PDDocument.importPage](https://github.com/TomRoush/PdfBox-Android/blob/v2.0.27.0/library/src/main/java/com/tom_roush/pdfbox/pdmodel/PDDocument.java) | Explicit inherited-resource omission warning; annotation references may retain other pages. Stock-import case uses strict unchanged fidelity expectations, no silent repair. |
| [Splitter](https://github.com/TomRoush/PdfBox-Android/blob/v2.0.27.0/library/src/main/java/com/tom_roush/pdfbox/multipdf/Splitter.java) | Page-link handling is implementation-specific, not proof that stock import preserves arbitrary annotation relationships. Spike does not adopt Splitter as a production workaround. |
| [Sonatype versions](https://central.sonatype.com/artifact/com.tom-roush/pdfbox-android/versions) | HTTP 403; page UNVERIFIED in this increment. Maven metadata/POM were accessible separately. Initial raw source URL without the `v` tag prefix returned 404; corrected tagged sources were accessible. |
| [Apache security page](https://pdfbox.apache.org/security.html) | HTTP 403; current complete advisory inventory UNVERIFIED. Owner research about example-module issues remains attributed, not proof of this fork's vulnerability or safety. |

### Security and licensing remain separate owner gates

| Advisory | Verified/source attribution, precondition and decision boundary |
| --- | --- |
| [CVE-2023-33202 BC wiki](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902023%E2%80%9033202) | Wiki fetched; PEM/ASN.1 parsing availability issue described for <=1.72, fixed 1.73. Published candidate transitives include affected version; exact use/reachability from the spike/production PDF path UNKNOWN. |
| [CVE-2026-5598 / GHSA-p93r-85wp-75v3](https://github.com/advisories/GHSA-p93r-85wp-75v3) | Fetched advisory lists bcprov-jdk15to18 >=1.71,<1.80.2, fixed 1.80.2; FrodoKEM encapsulation-decryption timing context. Artifact affected is not demonstrated invocation/exploitability here; reachability UNKNOWN. |
| [CVE-2024-34447 / GHSA-4h8f-2wvx-gg5w](https://github.com/advisories/GHSA-4h8f-2wvx-gg5w) | Fetched advisory lists bcprov-jdk15to18 >=1.61,<1.78, fixed 1.78; BCJSSE/TLS endpoint identification/hostname context. The spike adds no networking; actual reachable class paths remain unassessed. |

No complete SCA or reachability/security audit performed; further advisories may
apply. An absent demonstrated exploit is not a security clearance. Test APKs can
contain affected bytecode: evaluate only committed synthetic assets on separately
authorized disposable devices. No untrusted/private PDFs or network endpoint tests.
No automatic BC patch upgrade/exclusion, JCenter, JP2/JBIG2 or new native artifact.

The tagged upstream manifest is empty (source observation only). Actual merged
permissions, runtime/network behavior, APK size/native payload, license/NOTICE
packaging, BC attribution and resolved transitives require owner inspection. Preserve
root license and fixture CC0. Apache-2.0/POM and upstream README license statements
are metadata, not legal clearance; distribution of test APKs also needs appropriate
third-party notices. No production binary may acquire PDFBox or BC from this spike.
Source-only dependency scope is not proof of that property.

| Gate | Status at source publication |
| --- | --- |
| SOURCE READY | Test-only source prepared; compilation NOT RUN |
| CI / API26 / API27 / API36 | NOT RUN for spike; historical #128 not reused |
| TEST-APK ISOLATION / actual graph / APK size | NOT RUN / UNKNOWN |
| FUNCTIONAL / FIDELITY | NOT RUN / UNVERIFIED; saved-output and deliberate-mutation checks plus independent PDFium source; owner pypdf/PyMuPDF checks pending |
| JPX | BLOCKED: dictionary-filter negative test only; no real fixture or complete positive detection/rejection proof |
| SECURITY | OPEN: affected declared transitives, unknown full inventory/reachability; owner risk decision required |
| MEMORY | NOT MEASURED / INCONCLUSIVE; bounded cycles/phase observations do not cap parser heap/CPU or prove leak absence |
| PHYSICAL API27 | NOT RUN; 2–3 GiB hardware feasibility pending |
| PRODUCTION APPROVAL | NOT APPROVED regardless of future test successes |

No engine-backed production defaults changed. M-specific coverage remains the
separately proposed, unapplied scope. All BUILD / JVM / ANDROID / LINT / COVERAGE /
DEPENDENCY RESOLUTION / SECURITY SCAN / CI / DEVICE / VALIDATORS: **NOT RUN**.


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

## Experimental vertical slice and owner performance scope — 2026-11-09

A real working-copy engine and strict validator now exist ONLY in androidTest,
limited to exact canonical synthetic source hashes and a 2 MiB harness parser-byte
bound. Production EngineApprovalRequired defaults and test-only dependency remain
unchanged; no general engine approval. See [source/fault matrix](M_BLOCK2_TEST_AND_ACCEPTANCE_EVIDENCE.md#real-pdf--safe-save-as-test-confined-vertical-slice--2026-11-09).
The owner omitted the dedicated physical memory/performance campaign: earlier
prospective profiling requirements are superseded as scope, not marked PASS.
No new benchmark/physical profiling or quantitative threshold is scheduled. Parser
exhaustion/cancellation limits and security/JPX/packaging/legal gates remain open;
no leak-freedom or resource-safety claim. New integration/JVM tests are NOT RUN.

## Block A production implementation — 2026-11-09

**Current source disposition, superseding prospective/test-only statements above;
all historical execution evidence retains its original revision.** Starting source:
`39a0332a87323ad151bf0914403fe7a862d9cc46`. Publication revision is the commit
containing this entry (`git log -1 --format=%H -- docs/development/M_ENGINE_EVALUATION.md`).

Selected implementation: PDFBox-Android **2.0.27.0**, with explicit, strictly
constrained **1.86** versions of `bcprov-jdk15to18`, `bcpkix-jdk15to18` and
`bcutil-jdk15to18`. Maven metadata/POM source was read, not resolved/installed.
There was no newer published Android fork release in the inspected metadata.
The vendor API19+ claim and Java 1.5–1.8 BC artifact description do not prove our
API26 compatibility. Existing native PdfRenderer supplies independent visual
corroboration; no added codec, network call, repository or dependency framework.
The production dependency replaces the old test-only declaration; old absence-
from-main packaging expectations apply to their historical revisions only.

Rationale: retain the previously exercised working-copy strategy and interfaces,
replace the known affected BC 1.72 declaration, and enforce a restricted PDF
subset. No evidence justifies substituting a second unassessed parser. This is a
source implementation decision, **not security clearance or runtime acceptance**.
Complete current fork-advisory inventory and resolved-bytecode reachability remain
BLOCKED; the ordinary coordinator still defaults to `EngineApprovalRequired`.
Only the explicit `MTransactionCoordinator.production(...)` construction factory
selects the new adapters. No host calls it and no user picker is enabled.

`ProductionPdfAdapters` uses strict PDFParser (`setLenient(false)`) over a
non-owning positional `SnapshotRandomAccess` view of a duplicated leased FD.
No parser-created input file or alternate snapshot owner. Scratch buffering is
limited to 32 MiB per parsed document in memory, each parser read/decoded stream to 8 MiB, graph depth/
nodes, page count, content tokens and native verification dimensions are bounded.
These controls do **not** bound every upstream parser allocation or CPU interval.
Cancellation is checked on descriptor reads, qualification traversal, tokens and
page verification; a synchronous upstream/native call is not forcibly interruptible.
No promise of hostile-input exhaustion immunity or hard cancellation deadline.

Supported policy: single-source, unique page selection/reorder/removal/extraction;
additional 0/90/180/270-degree rotations; effective five page boxes/resources
materialized before reattaching original page objects to the working tree. This
avoids stock import's fixture12 `/P` defect. Source bytes are never rewritten.
The selection contract still rejects duplicates and multi-source merging is absent.
Qualified text/vector/image resources and Highlight/Underline/Ink/FreeText with
appearances are supported, subject to strict structure checks. Embedded fonts or
standard fourteen fonts are permitted; no arbitrary missing-font substitution is
accepted. Catalog structures outside the allowlist are unsupported.

Rejected: encrypted inputs, strict-parser failures, inconsistent/cyclic/shared page
trees, unsupported units/rotations/geometry, unavailable resource references,
Type3/nonstandard unembedded fonts, missing/foreign annotation associations or missing
appearances, widgets/forms/signatures/attachments/actions/scripts, unsupported
filters/DecodeParms, all inline images and JPX. JPX detection resolves dictionary
Filter values, filter arrays and indirect objects over the loaded COS graph,
including retained orphan objects; it is not a byte-keyword search. Unknown object
representations fail closed. Strict parser and graph limits do not prove that the
upstream parser cannot allocate while resolving a malicious graph before policy.

Validator: independently leases/hashes and strictly parses source and output;
checks exact plan, count/order/rotation, five boxes, decoded content, resource
fingerprints, supported annotations/appearance graphs and page associations,
catalog/info metadata and exact extracted text. All selected pages also require
exact native PdfRenderer raster correspondence after the requested rotation.
Bitmaps are bounded/recycled; parser/FD/lease cleanup precedes output sealing.
Structural and text checks share PDFBox and are **not wholly independent**;
native rendering supplements them and does not prove interactive semantics. Any
mismatch/unsupported/inconclusive check refuses the restricted artifact. Exact
raster comparison may reject a legitimate fractional-geometry case; no tolerance
or synthetic hash exemption manufactures success.

Status: engine/validator/integration/test source **IMPLEMENTED — NOT EXECUTED**.
Block A is **NOT ACCEPTED / NOT READY FOR HOST ACTIVATION** while the mandatory
security, resolved dependency, build, API26/27/36, packaging, coverage and legal
checks in [the gate ledger](M_PDFBOX_SECURITY_AND_PACKAGING_GATES.md) are open.
The dedicated memory/performance/physical profiling campaign remains omitted by
owner decision, not an unexecuted acceptance requirement or a PASS.
