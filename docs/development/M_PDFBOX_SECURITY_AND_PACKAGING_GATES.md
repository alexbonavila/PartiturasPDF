# Phase M — PDFBox security and packaging acceptance gates

2026-11-09; source baseline `134fd0f8d0599c70a4e1de5a06b83478b49c422d`.
**Audit preparation, not completed security or production approval.** Owner runs
all commands below; Codex performed read-only source/public-document inspection
and Git whitespace checks only. No new tooling, dependency or production integration.
[Independent positive fidelity campaign](M_PDFBOX_INDEPENDENT_VALIDATION.md#completed-owner-campaign--2026-11-09)
is complete within its stated synthetic scope. It does not close the gates below.

## Evidence layers and inventory

| Layer | Established evidence | Still required |
| --- | --- | --- |
| Repository | gradle/libs.versions.toml pins com.tom-roush:pdfbox-android:2.0.27.0; app/build.gradle.kts adds only androidTestImplementation(libs.pdfbox.android). minSdk 26; compile/target 37; Java source/target 11; AGP 9.5.0. Existing Android test assets expose test-fixtures and schemas only to instrumentation. | Actual resolution, bytecode and merged variant evidence; source scope alone is not APK isolation. |
| Published artifact metadata | [Pinned POM](https://repo.maven.apache.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.pom) re-read 2026-11-09: AAR, Apache-2.0, bcprov-jdk15to18/bcpkix-jdk15to18/bcutil-jdk15to18 each 1.72; no other direct POM dependencies. | Full resolved transitives, conflict substitutions and actual archive hashes. No assumption that POM equals packaged dependency graph. |
| Upstream relationship | Earlier tagged-source review at v2.0.27.0 / 45da92629dad5b3c9887eceefecc89a1423f5457; port derives from Apache PDFBox 2.x. Tagged README still labels base/example 2.0.26, while published artifact is 2.0.27.0. API19+ is a vendor claim. | Diff fork against exact upstream commit, enumerate backports; prove AAR/tag relationship. JVM upstream releases/CVEs do not automatically establish fork applicability or fixes. |
| Maintenance | Prior Maven metadata reported release/latest 2.0.27.0, lastUpdated 20230102065914. Earlier master build declared BC 1.73; irrelevant to pinned AAR inclusion. | Current latest release/commit, supported security policy and maintainer response: UNVERIFIED on Nov 9. Old release age is risk, not proof of abandonment. |
| Optional components | Prior upstream README: JPX images can be ignored with warning without optional JP2Android historically on JCenter; no codec, JCenter or new native component was approved. | Actual native payload and positive complete JPX detection/rejection proof; no silent omission. Existing synthetic dictionary test is insufficient. |
| Resolved / packaged | No new Gradle graph or APK inspected in this task. | Exact selected BC/other modules, DEX, assets, native ABIs, merged permissions, sizes, hashes and license files. |
| Executed evidence | Owner full CI #135 at source baseline and API26 independent synthetic fidelity PASS. | No transfer of fidelity PASS to security, graph, packaging, licensing or physical memory acceptance. |

No automatic BC upgrade, exclusion, version constraint, repository, codec or
production dependency is authorized. Material findings require a separately scoped
owner decision; do not force the candidate through acceptance.

## Version-specific advisory and attack-path ledger

Read-only public research on Nov 9: pinned POM and
[BC CVE-2023-33202 wiki](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902023%E2%80%9033202)
were accessible. [Apache security page](https://pdfbox.apache.org/security.html)
returned HTTP 403: current complete inventory UNVERIFIED. Prior Nov 3 advisory
reads remain dated evidence, not a fresh exhaustive Nov 9 security scan.

| Finding | Version/precondition | Presence and reachability | Evidence/action before disposition |
| --- | --- | --- | --- |
| CVE-2023-33202 | BC Java <=1.72; fixed 1.73. Wiki describes crafted PEM/ASN.1 SET parsing causing OOM; PEMParser is one entry point. | POM declares affected 1.72; actual selected/packaged modules UNKNOWN. PEM is not established as a PDF operation path; shared ASN.1/security-handler use needs tracing. No exploit or absence of reachability demonstrated. | Owner records actual graph, class inclusion and engine parse/decrypt/security-handler call paths; inspect exact fixed diff. Document whether attacker-controlled PDF data reaches affected ASN.1 routines. An affected version requires decision, not speculative exploit claims. |
| CVE-2026-5598 / [GHSA-p93r-85wp-75v3](https://github.com/advisories/GHSA-p93r-85wp-75v3) | Nov 3 read: bcprov-jdk15to18 >=1.71,<1.80.2; fixed 1.80.2; FrodoKEM encapsulation-decryption timing context. | Declared 1.72 in range; actual inclusion/calls UNKNOWN. No demonstrated FrodoKEM use by this PDF workflow. | Re-fetch advisory/version conditions, record class/call-path analysis and owner risk decision. No claim fixed by unrelated BC 1.73 master declaration. |
| CVE-2024-34447 / [GHSA-4h8f-2wvx-gg5w](https://github.com/advisories/GHSA-4h8f-2wvx-gg5w) | Nov 3 read: bcprov-jdk15to18 >=1.61,<1.78; fixed 1.78; BCJSSE TLS hostname-verification context. | Declared affected version, no test-spike networking added; actual reachable BCJSSE/TLS path UNKNOWN. | Verify current advisory and bundled classes. Offline design narrows intended paths but does not prove absence of network/reflection/other consumers. |
| Apache PDFBox / fork advisories | Current list unavailable (403). Earlier owner research concerned example-module extraction traversal as distinct from core parsing. | Fork inclusion and core/example exposure unverified; do not mechanically transfer CVE labels. | Owner obtains authoritative current advisory list, affected versions/modules, fork patch correspondence and exact bytecode; retain source URLs/date. |
| Parser CPU/heap/decompression/recursion | Compressed object/image/font streams, recursive resources, malformed cross-references, security handlers and adversarial sizes can exhaust process resources. | A real parser runs in instrumentation process; finite synthetic successes do not cap allocations, time or protect user PDFs. File quotas/64 KiB copy buffers are not heap/CPU limits. | Separately authorize controlled safe limits/cancellation experiments and implementation-level review. No decompression bombs on routine CI. Unbounded or unreliable termination remains BLOCKED. |
| Encryption / unsupported formats | Product excludes protected PDFs; current negative fixture rejects without password. JPX, unusual forms/inheritance/filters remain unsupported/unproven. | Negative synthetic success does not prove comprehensive pre-parser detection or all encryption variants; JPX warning/omission is unsafe for preservation. | Define qualified rejection policy and positive detection oracle BEFORE future engine integration. No fallback flattening, decryption or dropping objects. |
| Stock import annotation defect | Fixture 12 /P can point outside output tree. | KNOWN_UNSAFE in owner execution; working-copy positive PASS does not repair this strategy. | Keep excluded. Any replacement strategy needs separate approval and validation. No production repair in this assignment. |

Owner audit must enumerate ALL resolved modules and all applicable current
advisories, not just these three BC examples. Required record per advisory: ID,
source/date, affected/fixed conditions, selected artifact hash/version, packaged
class, input entry point, call graph/reachability evidence, disposition, residual
risk and decision owner. UNKNOWN is not NOT AFFECTED. No scanner run or exploit
simulation occurred here. Dependency installation, resolution or passing tests
alone cannot establish security clearance.

## Exact owner-run Windows packaging protocol

Use an authenticated owner workstation at the frozen source SHA with clean checkout.
Run in repository root. These commands use the single app module's debug/release
variants and AndroidJUnitRunner test variant already present in source; AGP-derived
configuration availability and actual artifact existence remain runtime checks.
No signing, R8, SDK, repository or manifest workaround is authorized. If a command
or expected artifact is unavailable, record BLOCKED and obtain the actual supported
variant path without altering configuration.

```powershell
$sha = git rev-parse HEAD
if ($sha -ne "134fd0f8d0599c70a4e1de5a06b83478b49c422d") { throw "Wrong audited source" }
if (git status --porcelain) { throw "Dirty source" }
$audit = Join-Path $env:TEMP ("PartiturasPDF-M-audit-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $audit | Out-Null
$variants = @("debugRuntimeClasspath", "releaseRuntimeClasspath", "debugAndroidTestRuntimeClasspath")
foreach ($variant in $variants) {
  .\gradlew.bat :app:dependencies --configuration $variant 2>&1 |
    Tee-Object -FilePath (Join-Path $audit "$variant.txt")
  if ($LASTEXITCODE -ne 0) { throw "Dependency graph failed: $variant" }
  foreach ($module in @("pdfbox-android", "bcprov-jdk15to18", "bcpkix-jdk15to18", "bcutil-jdk15to18")) {
    .\gradlew.bat :app:dependencyInsight --configuration $variant --dependency $module 2>&1 |
      Tee-Object -FilePath (Join-Path $audit "$variant-$module.txt")
    if ($LASTEXITCODE -ne 0) { throw "Dependency insight failed" }
  }
}
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
if ($LASTEXITCODE -ne 0) { throw "Build failed; no packaging acceptance" }

$apks = @("app\build\outputs\apk\debug\app-debug.apk",
          "app\build\outputs\apk\release\app-release-unsigned.apk",
          "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk")
foreach ($apk in $apks) {
  if (!(Test-Path $apk)) { throw "Expected APK absent: $apk" }
  Get-Item $apk | Select-Object Name, Length
  Get-FileHash $apk -Algorithm SHA256
}
# Owner supplies the installed SDK location; no installation/configuration edit.
$analyzer = Join-Path $env:ANDROID_HOME "cmdline-tools\latest\bin\apkanalyzer.bat"
if (!(Test-Path $analyzer)) { throw "Use existing APK Analyzer or obtain separate tool setup approval" }
foreach ($apk in $apks) {
  $name = Split-Path $apk -Leaf
  & $analyzer dex packages --defined-only $apk |
    Tee-Object -FilePath (Join-Path $audit "$name-dex.txt")
  if ($LASTEXITCODE -ne 0) { throw "DEX inspection failed" }
  & $analyzer files list $apk |
    Tee-Object -FilePath (Join-Path $audit "$name-files.txt")
  if ($LASTEXITCODE -ne 0) { throw "Archive inspection failed" }
  & $analyzer manifest print $apk |
    Tee-Object -FilePath (Join-Path $audit "$name-manifest.xml")
  if ($LASTEXITCODE -ne 0) { throw "Manifest inspection failed" }
}
Select-String -Path (Join-Path $audit "*-dex.txt") -Pattern "com.tom_roush", "org.bouncycastle"
Select-String -Path (Join-Path $audit "*-files.txt") -Pattern "com/tom_roush", "/lib/", "NOTICE", "LICENSE"
```

Retain exact tool versions, stdout/exit codes, graph selections, all file-size/hash
results and full archive listings in the private audit record. Hash actual resolved
AAR/JAR files identified by the owner's Gradle cache/IDE external-library resolution;
record exact paths privately, module coordinates and SHA-256. Do not infer actual
artifact hashes from POM hashes or guess cache filenames. No new resolver script.

Acceptance: no PDFBox/fontbox/com.tom_roush or BC classes/resources contributed by
this spike in EITHER main debug/release graph AND binary. Confirm exact test APK
versions/classes, glyph lists and expected permissions. Package-name grep is only
an index: inspect complete DEX listings, embedded archives/relocated names, resources
and merged manifests with APK Analyzer; a zero-match grep alone is not proof.
If production legitimately already contains a matching component, isolation is
not established: identify its source/version and seek owner disposition, never
claim absence. Unexpected main inclusion FAILS the exclusive-test requirement.
No inference from debug compilation to release isolation.

List every native library/ABI; attribute existing PDFium separately from candidate
or optional codecs. No claim that an empty upstream library manifest proves empty
merged permissions. Compare main/test manifests and a retained same-toolchain
pre-spike baseline APK, if available, for permissions, native payload, package and
size deltas. Record sizes even when baseline absent; delta then BLOCKED. Never
attribute the whole test APK growth to PDFBox without matched artifact comparison.
No production/test assets or APKs committed; no automatic GitHub uploads.

## License and distribution ledger

Root LICENSE remains CC BY-NC-ND 4.0; synthetic fixtures remain separately CC0.
The pinned PDFBox POM declares Apache-2.0. Preserve relevant license text,
copyright/attribution and NOTICE content if shipped/distributed, plus modification
notices where applicable; the project license does not relicense these components.
The [published bcprov-jdk15to18 1.72 POM](https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk15to18/1.72/bcprov-jdk15to18-1.72.pom),
read Nov 9, declares "Bouncy Castle Licence" with a vendor licence.html link.
BC is commonly described as permissive BC/MIT-style licensing, but the exact text
and included notices of all three selected 1.72 module archives have not been
independently inspected here: obligations/subcomponents remain UNVERIFIED. Resolve each
selected transitive and inspect its actual LICENSE/NOTICE files (including any
bundled third-party code), even for a distributed instrumentation APK. Owner/legal
review must determine project-license compatibility and distribution obligations;
this is not legal clearance. Do not modify root licensing, NOTICE packaging or
release configuration in this task. Missing required attribution is FAIL for
intended distribution; unresolved obligations are BLOCKED / OWNER DECISION REQUIRED.

## Gate acceptance matrix

| Gate / requirement | Evidence and exact owner action | Acceptance criterion | Current status / risk |
| --- | --- | --- | --- |
| Scoped synthetic fidelity | Owner campaign/reports identified in linked evidence | 27 positive recipes, 345 structural pages, 57 exact renders, no missing cases; retain sampling limits | PASS (owner-provided scoped evidence only) |
| Full CI at executed source | Owner verified CI #135 link and exact SHA | Existing required jobs at that source, no attribution to future docs SHA | PASS (owner-provided; not fetched here) |
| Selected modules / hashes | Run dependency/dependencyInsight protocol; hash actual AAR/JARs | Complete selected graph and version/hash attribution, no unexplained substitutions | NOT RUN; POM alone insufficient |
| Main/test binary isolation | Build and inspect all three APKs with protocol above | Candidate/transitives only in test graph AND payload; main debug/release absent | NOT RUN; production packaging OPEN |
| Permissions/native/resources/size | Inspect listings/manifests; retain matched baseline and sizes | All additions attributed/authorized, glyph resources present, no new unapproved permission/native payload | NOT RUN; unknown binary effects |
| Advisory inventory and reachability | Obtain current vendor/advisory sources; inspect selected bytecode/call paths | Every applicable advisory disposition supported by evidence; no unexplained high/critical risk | BLOCKED; current complete inventory and actual graph unavailable |
| Pinned BC risk / maintenance strategy | Review actual artifacts, reachability, fork maintenance and alternatives | Explicit evidence-based owner decision; no speculative dependency override | OWNER DECISION REQUIRED; affected declared versions |
| JPX/forms/inheritance/filter rejection | Separately authorize positive feature detection/fail-closed cases | Unsupported data never silently dropped | BLOCKED; no complete positive detection oracle |
| Stock import fixture 12 | Preserve negative-regression evidence | Strategy remains excluded; cannot count negative PASS as positive fidelity | FAIL for that strategy (KNOWN_UNSAFE); no repair authorized |
| Negative-case independent proof | Obtain separate raw/JUnit/negative-oracle evidence | Each expected negative behavior attributable, not inferred from empty lists | BLOCKED; 11 empty namespaces lack standalone proof |
| Legal/NOTICE distribution | Inspect exact archive license inventory with owner/legal | Required text/notices and distribution decision recorded | OWNER DECISION REQUIRED; no legal clearance |
| Physical resource/cancellation safety | Separately authorized measurement plan below | Complete attributed evidence and agreed decision criteria | NOT RUN; no peak/leak/latency conclusion |
| Production engine integration | Separate owner gate after all required evidence | Approved strategy, supported/rejected features, security/legal/resource/coverage and host contracts | OWNER DECISION REQUIRED / NOT APPROVED; Block 2 incomplete |

PASS does not propagate to adjacent gates. FAIL denotes demonstrated criterion
violation; BLOCKED denotes missing/uncertain prerequisite; NOT RUN denotes an
unexecuted action; OWNER DECISION REQUIRED is not permission. Known limitations
must not be waived implicitly. No broad security or engine acceptance is asserted.

## Subsequent hardware/memory handoff (design only)

After graph/binary/security triage, separately authorize representative API26/27/36
and physical Android 8.1 / 2–3 GB tablet trials. Freeze source/APK/artifact hashes,
API/ABI/device RAM, parser strategy and exact fixture plans. Reuse existing phase
observations without inventing measurements or reopening completed scopes.
Measure parse/transform/save/reopen/close monotonic durations and CPU time; Java
heap and allocation timeline, native heap, attributable process PSS, sampled maxima
versus true profiler peaks; GC frequency/pause behavior; repeated 50/100-page
post-close baselines, owned/native descriptors and document/bitmap release;
private source/output/temp storage and free-space margins; application availability
following repeated operations. Preapprove repetition limits and safe stop criteria.
Do not claim absence of retained-memory growth from finite PSS observations.

Operation cancellation needs bounded owner-authorized tests of parser interruption,
worker completion/join, independent cleanup and retained private accounting, with
originals intact. Current synchronous test-only parser does not establish a
production cancellation bound or isolated-worker kill policy. No destructive device
operation, adversarial OOM trial, worker kill or new test implementation is authorized
here. Agree quantitative budgets and physical acceptance criteria BEFORE calling
results acceptable; file quotas and 64 KiB buffers do not bound parser heap/CPU.

## Owner scope adjustment — 2026-11-09, subsequent vertical slice

Owner explicitly omits the separate physical memory/performance benchmarking
campaign. The prospective hardware/profiling plan and corresponding matrix row
above are historical proposals, superseded for this increment: DEFERRED/OMITTED
BY OWNER, NOT PASS. Do not schedule a benchmark suite, physical tablet profiling,
heap/PSS/CPU/latency campaign or invent thresholds. This does not establish leak
freedom or solve hostile-parser exhaustion. Keep byte quotas and deterministic
ownership/cleanup/cancellation requirements. General physical functional policy
remains distinct; no protected policy was changed or silently waived.

The new engine/validator remain instrumentation-only and limited to exact synthetic
source hashes, with a 2 MiB experimental parser-input bound. Production defaults
still require engine approval. Security/advisory/JPX, packaging and legal decisions
above remain open. See [vertical-slice source evidence](M_BLOCK2_TEST_AND_ACCEPTANCE_EVIDENCE.md#real-pdf--safe-save-as-test-confined-vertical-slice--2026-11-09).
No builds/tests/measurements/resolution/CI executed by Codex.
