# Technical stack and foundation

This document records the implemented Phase 0 foundation and the candidates in
[PROJECT_SPECIFICATION.md](PROJECT_SPECIFICATION.md). A candidate is not an
installed dependency or authorization to implement a feature.

## Verified configuration

| Setting | Repository value |
| --- | --- |
| Application ID / namespace | `com.abdev.partituraspdf` |
| Modules / build scripts | One `:app`; Kotlin DSL |
| min / target / compile SDK | **26** / 37 / 37 |
| Android Gradle Plugin | 9.4.1, built-in Kotlin support |
| Gradle Wrapper | 9.8.0 |
| Compose compiler plugin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| Java source / target | 11 / 11 |
| Gradle daemon JVM criterion | 25 |
| Toolchain resolver | Foojay convention plugin 1.0.0 |

Versions, wrapper contents, module structure and Gradle configuration are
unchanged. The Unix wrapper's executable permission is corrected so `./gradlew`
works. Do not add the legacy Kotlin Android plugin alongside AGP 9's built-in
Kotlin. JDK 25 executes Gradle; Java 11 compatibility and Android API 26 runtime
support are independent constraints.

Use Android Studio with support for this AGP, an installed JDK 25 and the SDK
packages requested by sync. CI installs Temurin 25, `platforms;android-37.0`,
build tools 37.0.0, platform-tools and SDK command-line tools 19.0; see
[CI.md](CI.md). Local SDK paths belong in ignored `local.properties` or environment
variables. Never commit them. An API 26 runtime test remains mandatory even when
compiling against SDK 37. New platform APIs require version guards and a usable
API 26 path; dependency minSdk and transitive requirements must be checked.

## Installed dependencies

The version catalog remains authoritative. Core KTX 1.10.1, lifecycle runtime
KTX 2.6.1 and activity-compose 1.8.0 support the current starter. Compose UI,
graphics, Material 3 and tooling preview use BOM 2026.02.01; debug builds also use
tooling and the UI test manifest. Tests use JUnit 4.13.2, AndroidX test JUnit
1.1.5, Espresso 3.5.1 and BOM-managed Compose UI test JUnit4. No Phase 0B
dependency is added, upgraded or downgraded. Lint's version suggestions are
review items, not permission to change working versions.

## Theme and accessibility

`ui/theme/Color.kt` supplies light/dark Material 3 schemes. Both retain primary
`#A5D6A7` and secondary `#90CAF9` with dark foregrounds; light text on these
pastels would fail contrast. Neutral green surfaces, amber tertiary accents,
red errors, foregrounds, containers and outlines are explicitly selected.
The amber tertiary pair is available for future warning presentation; future
warnings must also have localized text/semantics rather than relying on color.
Disabled states use Material 3 component defaults derived from the scheme's
surface/foreground colors; do not imply enabled affordances or convey state
solely through reduced opacity. No custom component is added here.

Wallpaper dynamic colors are disabled on every API. The theme follows system
light/dark mode; its `darkTheme` argument supports previews and tests. XML window
backgrounds match the Compose scheme before content appears, including night
resources on API 26. Existing edge-to-edge inset handling and typography are
retained. Layouts use available space, without fixed phone widths or speculative
tablet navigation. Future adaptive components need their own approved scope.

Instrumentation tests launch the starter, render all three greetings plus an
unsupported-locale English fallback in both modes, and check brand identity,
normal-text contrast >= 4.5:1 and outline-to-base-surface contrast >= 3:1.
Individual future components must verify contrast on their actual backgrounds,
including elevation and disabled/interaction states. Automated semantics and
color checks do not replace human TalkBack, font-scale and layout review.
`GreetingPreview` offers six language/theme previews for Android Studio review.

## Localization and future manual selection

English lives in `values/strings.xml`; Catalan and Spanish in `values-ca` and
`values-es`. All three contain `app_name`, `starter_recipient` and the positional
`starter_greeting` (`%1$s`). The starter follows the system locale, with English
fallback. Android and Partituras PDF remain product names in all translations.
Compose `Text` exposes the greeting through localized text semantics; there are
no image/button descriptions in this starter. Add future accessibility labels
to all three resource sets; avoid duplicate descriptions for visible text.

The unchanged `scripts/validate_localization.py` rejects missing translations,
extra/mismatched resource names/types and inconsistent format arguments. Run it
alongside Lint and the validator's existing tests; do not waive checks for brand
names or copy strings into Kotlin. Use numbered placeholders and resource
plurals for future content.

Manual selection is **planned, not implemented**. For Android 8.0+, evaluate
AppCompat's per-app locales (`AppCompatDelegate.setApplicationLocales`) and its
required activity integration with Compose; the current `ComponentActivity`
does not provide that integration automatically. Verify recreation, resource
contexts and pre-33 locale persistence without blocking startup. On API 33+
coordinate with platform per-app language settings. A newer platform-only API
is insufficient for API 26/27. Settings persistence, migration from system
locale, return-to-system behavior and process restart need approved tests before
adding AppCompat/DataStore or a selector. No locale preference, locale-config
manifest declaration, selector screen or new dependency is installed now.

## Architecture and prospective dependencies

Keep one module. As approved features arrive, use Compose UI, ViewModels/state
holders, repositories and interfaces around SAF, PDF engines and remote services;
use coroutines for bounded background work. PDF engine types must not leak into
UI. Create packages/abstractions when used, rather than empty placeholders.

| Candidate | Intended responsibility and adoption conditions |
| --- | --- |
| Platform SAF | User-controlled documents, persisted URI permissions; test revoked access, provider errors, non-seekable streams and replacement behavior. |
| Platform `PdfRenderer` | Reading on API 26; bounded bitmap/cache memory, renderer/page closure and background execution; test protected/corrupt/complex PDFs. |
| PDFBox-Android 2.0.27.0 | Candidate editing engine only; audit maintenance, vulnerabilities, dependencies, APK/memory impact and API 26 behavior before adoption. |
| Room | Metadata only, never authoritative PDF content; require migrations and reconciliation with external file changes. |
| DataStore | Future settings; test persistence/restart; never store authorization secrets in plain preferences. |
| Kotlin coroutines | Future cancellable asynchronous operations; structured lifetimes and failure/cancellation tests. |
| Google identity/authorization and Drive REST | Optional, explicit user-authorized import/export; verify current supported SDK/API and OAuth requirements before choosing versions. |

Room, DataStore, PDFBox-Android and Drive libraries are not installed. Candidate
versions other than the specification's PDFBox starting point are intentionally
undecided until dependency/API/license evaluation accompanies an approved task.

PDFBox-Android's aging 2.0.27.0 baseline is a risk, not an endorsement. Verify
fonts, transparency, encryption, annotation dictionaries and appearance streams.
Android 8 `PdfRenderer` cannot be assumed to render every standard annotation;
future overlays must account for crop/media boxes, rotation, scale and pan in
PDF coordinates. Exported annotations must persist and render in an independent
viewer; flattening needs explicit approval. Render success alone is not proof
of structural integrity or successful saving. Use temporary working copies,
validated output and recovery; SAF providers do not all support atomic replace.

Drive adoption requires human-approved Google Cloud OAuth setup, app identity,
consent configuration and current authorization requirements. Prefer minimal
`drive.file` access with explicit selection, validate its selection semantics and
request broader access only under separate approval. Handle revoked/expired
consent, offline use and cancelled/failed uploads. Do not invent credentials,
log tokens, require cloud accounts for local use or introduce automatic sync.
Audit manifest backup rules before storing private documents or credentials.

## Test fixtures and licensing

The existing manifest and 15 immutable PDFs under `test-fixtures/` are the only
authoritative collection. The original verifier checks hashes, bytes, structure,
geometry, rotation, annotations, encryption and rendering. No generator runs in
CI. No fixture is in a production source set or asset directory.

Future JVM tests can receive the repository fixture root as a Gradle task input
and read those files directly. Future Android tests can stage the same inputs
into a **generated androidTest-only** asset directory via a declared copy task,
including the manifest; never maintain a second source-controlled collection or
add that directory to `main`/release. Test edits use per-test temporary copies.
Verify release APK contents whenever source sets/assets change. No PDF-level
application tests or fixture-staging tasks are added during Phase 0.

Root CC BY-NC-ND 4.0 and the fixture dataset's separate CC0 declaration remain
unchanged. AndroidX libraries retain Apache-2.0; JUnit retains EPL-1.0 and
Kotlin retains Apache-2.0. Prospective PDFBox-Android is Apache-2.0, but that
does not establish the licenses of every transitive component. Review actual
resolved artifacts, notices, fonts, native code and Python/tooling dependencies
before distribution; preserve required third-party notices. Python fixture
tools are test-only, including PyMuPDF's AGPL/commercial licensing considerations,
and are not Android runtime dependencies. The restrictive root CC license is
not an open-source software license; its suitability for software and third-party
distribution still requires owner/legal review before release.
