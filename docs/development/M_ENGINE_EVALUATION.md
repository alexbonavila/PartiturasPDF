# M engine evaluation — 2026-11-03

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
