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
