# Phase J — M3-B2-D AAB Language Delivery Evidence

Execution date: **2026-10-23** (owner-local session).
Branch: `test/mvp-stabilization-j`.
AAB source revision: `6b2294b715abbfd8787c0878caf8d95116fa4e35`.
Documentation baseline: `342d3db977de34b5690095afa3c252c04dcb3d08`.

## Objective, scope and evidence level

This report closes out the selected **local bundletool-based language-delivery** scope:
debug AAB generation, device-specific APK-set generation/installation on API26/27/36,
and owner-observed offline Spanish/Catalan/English Library UI on API26. It does not
establish Google Play delivery, all-screen language acceptance or full Phase J completion.

The owner supplied the execution and visual observations in the closeout request.
The owner-local evidence directory is:
`build/j-m3-b2d/aab-20261023T073014`.
That directory, its APK sets, configuration/resource dumps and screenshots are not
available in this review checkout. Corresponding results below are **OWNER-REPORTED**,
not independently inspected owner artifacts. A separate local AAB was inspected as
described below; it has no verified link to the owner's run and is not substituted
for that evidence. No Android campaign, Gradle task or device operation ran during review.

## Result summary

| Dimension | Result / evidence level |
|---|---|
| Debug AAB build | PASS — OWNER-REPORTED |
| Language splitting disabled | PASS — repository configuration verified; owner AAB configuration reported |
| Device-specific APK generation API26/27/36 | PASS — OWNER-REPORTED |
| APK installation API26/27/36 | PASS — OWNER-REPORTED |
| Language-specific splits absent from generated/installed inventories | PASS — OWNER-REPORTED |
| API26 Spanish Library UI offline | PASS — owner-observed |
| API26 Catalan Library UI offline | PASS — owner-observed |
| API26 English Library UI offline | PASS — owner-observed |
| API26 post-test restoration | OWNER-REPORTED; independently unverified |
| Exact original system-language order restoration | NOT VERIFIED |
| AAB offline language switching on API27/36 | NOT RUN |
| Real Google Play delivery | NOT RUN |

These are separate dimensions, not a fabricated global PASS. Earlier failed attempts
and outstanding restoration verification remain distinct from successful generation,
installation and visible-language observations.

## Source provenance independently checked

The two published commits after the build source are the M3-B2-B documentation closeout
`0e99f93e5a14145f332d9c1af07151dae52d76ac` and M3-B2-C documentation closeout
`342d3db977de34b5690095afa3c252c04dcb3d08`.
A repository comparison from the build source to the documentation baseline contains
only Phase J Markdown changes. Both commits have the same complete `app` Git subtree
as the build source: `d285b85c3ccee02a53eb81ae6b80872830161a85`.

This independently establishes unchanged repository app source/build configuration,
not identical build outputs, installed bytes or environments. The owner's AAB remains
attributed to **6b2294b715abbfd8787c0878caf8d95116fa4e35**, never the newer documentation
revision. Source equivalence is not independent binary attestation.

## Debug AAB build and configuration

The owner reports executing `./gradlew :app:bundleDebug`, with BUILD SUCCESSFUL,
producing `app/build/outputs/bundle/debug/app-debug.aab`, approximately 14 MB, using
bundletool **1.18.3**. No new build was performed in this closeout.

Reported retained files under the owner-local directory are `revision.txt`,
`aab.sha256`, `bundle-config.txt` and `bundle-resources.txt`. Their bytes are unavailable;
no owner AAB hash, dump hash or source digest is invented.

The owner reports this generated bundle configuration:

```json
{
  "optimizations": {
    "splitsConfig": {
      "splitDimension": [
        { "value": "LANGUAGE", "negate": true }
      ]
    }
  }
}
```

The inspected `app/build.gradle.kts` independently contains:

```kotlin
bundle {
    language {
        enableSplit = false
    }
}
```

Both describe **disabled language-based APK splitting**. They do not prove that all
screens/strings were reviewed or that Google Play delivered a particular installed set.

## Device-specific generation and installation

The owner generated these sets from the actual debug AAB with bundletool:

| API / Android | Owner-selected AVD | APK-set path | Approximate reported size | Generation / installation |
|---|---|---|---|---|
| 26 / 8.0 | Tablet2 | `build/j-m3-b2d/aab-20261023T073014/api26.apks` | 15 MB | PASS / PASS, OWNER-REPORTED |
| 27 / 8.1 | Tablet_27_Edited | `build/j-m3-b2d/aab-20261023T073014/api27.apks` | 15 MB | PASS / PASS, OWNER-REPORTED |
| 36 / 16 | Tablet_36 | `build/j-m3-b2d/aab-20261023T073014/api36.apks` | 40 MB | PASS / PASS, OWNER-REPORTED |

The owner reports all three inspected APK sets contained:

```text
splits/base-arm64_v8a.apk
splits/base-hdpi.apk
splits/base-master.apk
```

No language-specific split was reported. APK-set sizes alone are not language-delivery
evidence. No archive content or set hashes could be independently checked here.

The owner used bundletool `install-apks` with each corresponding `.apks` and selected
device. All installations completed without reported errors. Subsequent Package Manager
inspection reported these installed components on each target:

```text
base.apk
split_config.arm64_v8a.apk
split_config.hdpi.apk
```

No installed language-specific APK path was reported. This is owner-observed successful
installation, not an independent package inspection, signing assessment or installed
APK checksum. Device serials and private installation paths are not reproduced here.

Earlier generation attempts encountered unavailable ADB devices, and an installation
was attempted before its `.apks` existed. These infrastructure/procedure attempts were
not successful installations. The owner reports resolving them before the successful
sets and installations above; their history is not reclassified as PASS.

## API26 offline language observations and screenshots

After AAB-derived installation, the owner reports ADB observations of airplane mode
`1`, Wi-Fi `0` and `Active default network: none`. The owner selected real Android
system languages through Settings and reopened PartiturasPDF. The following visible
Library content was reported:

| Language | Owner-observed Library resources | Result |
|---|---|---|
| Spanish (`es`) | Biblioteca de partituras; Seleccionar carpeta; Selecciona una carpeta para explorar sus PDF.; Ajustes | PASS — owner-observed offline |
| Catalan (`ca`) | Biblioteca de partitures; Selecciona una carpeta; Selecciona una carpeta per explorar-ne els PDF.; Configuració | PASS — owner-observed offline |
| English (`en`) | Score library; Select folder; Select a folder to browse its PDFs.; Settings | PASS — owner-observed offline |

The owner describes three screenshots at these expected local paths:

- `build/j-m3-b2d/aab-20261023T073014/language-api26/es.png`
- `build/j-m3-b2d/aab-20261023T073014/language-api26/ca.png`
- `build/j-m3-b2d/aab-20261023T073014/language-api26/en.png`

Some filenames were overwritten during capture attempts. **No image bytes are available
here**, so filename-to-language correspondence, actual pixels and screenshot hashes
could not be independently verified. The visual descriptions above are attributed to
the owner, not inferred from filenames. Retain and inspect the final image contents
before treating filenames as definitive evidence. No screenshot is added to Git.

The observed screen is Library. Visible navigation labels do not prove that Settings,
Reader, every contextual error or accessibility state received visual/linguistic review.
The owner-reported disconnected observations are not continuous packet-level monitoring
or proof of every transport at every instant. No offline language switching on API27/36
was performed in this evidence scope. Reopening the app after a language change does
not establish a live transition of an already active Activity.

## Original state and restoration limitation

The owner reports original captured radio settings of airplane mode `0`, Wi-Fi `1`,
and manual restoration after testing. No separate post-restoration ADB output was
supplied for this AAB session, and its private raw evidence is unavailable here.
Therefore restoration is **OWNER-REPORTED**, with exact radio restoration independently
**NOT VERIFIED**. Exact original language-order restoration is also **NOT VERIFIED**:
no verified initial/final ordered lists are available for this session.

No restoration PASS, identity continuity or final connectivity measurement is inferred
from a prior milestone. The [M3-B2-C later restoration projection](J_M3_B2C_ANDROID_OFFLINE_EVIDENCE.md)
belongs to a different execution; it cannot fill this session's gap. No additional
device operations were performed to supply missing evidence.

## Independently inspected local artifact — not owner-run attribution

A pre-existing file at `app/build/outputs/bundle/debug/app-debug.aab` is present in
this checkout. Its size is **14,984,186 bytes** and its independently computed SHA-256 is:

`c8f2bba65dc69443bcc3d109a1389c9451a4a9bdcc3248476f53c1658542adff`.

Its ZIP contains `BundleConfig.pb`, `base/resources.pb` and the base manifest. A read-only
decode of BundleConfig.pb using the cached bundletool 1.18.3 protobuf classes showed
bundletool version 1.18.3 and LANGUAGE with `negate: true`. This independently corroborates
configuration in **this local file only**. No artifact identity record links these
bytes to the owner's AAB or execution directory; matching approximate size/version and
unchanged repository source do not establish that link. This hash must not be used as
the owner's AAB hash. No APK sets were generated or binaries installed during inspection.

## Selected-scope assessment and remaining acceptance

The owner-supplied evidence supports successful **local bundletool-based language
delivery and manual offline language availability on API26**, with successful
device-specific generation and installation on API27/API36. This selected scope may be
treated as complete for planning, subject to owner formal acceptance, owner-reported
provenance, screenshot ambiguity and the explicit restoration verification gap.
It does not establish a fully independently verified execution or formal M3-B/Phase J PASS.

Excluded or outstanding:

- Genuine Google Play installation/delivery and release/distribution acceptance.
- AAB offline language switching on API27 and API36.
- All-screen/contextual-error linguistic and accessibility/TalkBack acceptance.
- Genuine external SAF/provider access and revocation.
- Physical Android tablet acceptance, including Android 8.1.
- Live language transitions of an already active Activity.
- Memory/performance measurements, endurance and deeper recovery validation.
- M3-C authentic Android/native/isolated-worker coverage and remaining M4–M7/J14/J15 work.
- Independently verified post-test radio and original language-order restoration here.

**J08 remains DEFERRED TO PHASE R.** Prior locale/offline/compatibility results and their
historical BLOCKED/NOT RUN classifications are not changed or repeated by this report.

The recommended next planning milestone is **M3-C coverage-attribution readiness**,
with these provenance/restoration limitations retained in the acceptance register and
separate owner authorization required before implementation or Android execution.
No M3-C work, new campaign, PR, merge, tag or release is started by this closeout.
