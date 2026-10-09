# Partituras PDF — Product & Engineering Specification

**Document version:** 1.0 (initial design baseline)  
**Project:** `alexbonavila/PartiturasPDF`  
**Application name:** Partituras PDF  
**Application ID / namespace:** `com.abdev.partituraspdf`  
**Status:** decisions and requirements documented **before functional implementation**. Do not interpret this document as evidence that a feature is already implemented.

## 1. Purpose and product principles

Partituras PDF is a native Android application for musicians who need a reliable library of sheet-music PDFs, quick and quiet page turning during practice/performance, and non-destructive editing and annotation. Tablet usability and Android 8.0 compatibility are primary requirements.

Fundamental principles:

1. **PDF-first:** a user's scores remain ordinary, portable PDF files, not proprietary documents locked into the app.
2. **Offline-first:** library, reading, local editing, and annotation must work without an account or network connection.
3. **Data safety:** prevent corruption and accidental loss; never use the only copy of a score as temporary processing storage.
4. **Low-friction reading:** the score takes precedence over toolbars and decorative interface elements.
5. **Compatibility:** Android API 26 is the absolute minimum; Android 8.1 tablets must be tested as first-class devices.
6. **Localization:** English, Catalan, and Spanish are mandatory for all visible copy and accessibility descriptions.
7. **Incremental delivery:** build by approved phases, tests, and pull requests; no speculative features or broad rewrites.
8. **No silent compromise:** unsolved format, performance, permissions, or compatibility risks must be documented and tested before acceptance.

### 1.1 Defined scope vs. implementation choices

**Committed product scope:** organization, full-screen reading with edge taps, basic PDF page editing, merge/extract, ink/highlight/text annotations saved in PDF, local import/export, and Google Drive import/export, with three-language support.

**Engineering baseline (subject to verification):** Kotlin, Jetpack Compose/Material 3, Room, DataStore, SAF, Android PdfRenderer, PDFBox-Android, Drive REST API, MVVM and repositories.

**Not yet committed for the initial release:** pedal-based page turning, two-page spread, concert setlists, automatic Drive sync, shared/collaborative libraries, OCR, printed-sheet recognition, online user accounts, and a general-purpose cloud backend. These may be considered only in separately approved future phases.

## 2. Platform and repository baseline

The following configuration was observed in the starter repository (not necessarily a recommendation to upgrade any component):

| Setting | Observed baseline |
| --- | --- |
| Android minSdk | **26** (Android 8.0, mandatory) |
| targetSdk | 37 |
| compileSdk | 37 |
| Android Gradle Plugin | 9.4.1 |
| Gradle Wrapper | 9.8.0 |
| Kotlin Compose Compiler plugin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| Java source/target compatibility | 11 |
| Gradle daemon JVM toolchain | 25 (as configured by the repo) |
| Build language | Gradle Kotlin DSL (`.kts`) |
| Modules | A single `:app` module |

The starter app was reported to compile on the developer's PC; this is not a substitute for CI, physical-device, or all-API verification. Gradle's daemon JDK, source/target bytecode level, and Android minSdk are separate concepts.

**Version management rules:** use the repository's `../gradle/libs.versions.toml`; verify release compatibility; do not upgrade/downgrade the generated project merely to match a sample. Android Gradle Plugin 9 includes built-in Kotlin support: do not add a redundant legacy Kotlin Android plugin. Add dependencies only when the relevant module is implemented, except dependencies required to establish testing or infrastructure.

## 3. Visual and interaction design

### 3.1 Brand and theme

| Role | Light theme decision |
| --- | --- |
| Primary brand | `#A5D6A7` light green |
| Secondary brand | `#90CAF9` light blue |
| Suggested light background | `#F5F8F4` |
| Suggested primary text | `#263238` |

Only the first two brand colors are locked; other colors are adaptable for contrast and dark mode. Material 3 color schemes must include accessible `on*` colors and state colors. Do not assume light green/blue with white text is accessible: compute contrast. Target WCAG AA where applicable (4.5:1 normal text; 3:1 large text and meaningful interface boundaries). Provide light/dark schemes; avoid unrestricted Android dynamic colors overriding core branding by default.

### 3.2 Tablet-adaptive design

- Build flexible layouts rather than hard-coded widths for one device.
- Support portrait and landscape orientations and screen rotation without losing reading position or unsaved work.
- Library: list/grid of scores, folder navigation, clear search/filter/sort actions, selection mode for bulk file actions.
- Reader: near-full-screen PDF, high-contrast score, minimal overlays, user-initiated menu for page number/zoom/edit tools.
- Editor: page thumbnails, selected-page actions, reorder via drag-and-drop, explicit save/export action.
- Touch targets should remain accessible to fingers; stylus input must not require tiny controls.
- Expose meaningful semantics/content descriptions for accessibility and automated Compose tests.

### 3.3 Reader tap behavior

- Default interaction zones: leftmost 25% goes to the previous page, rightmost 25% to the next page, middle 50% shows/hides reading controls.
- Boundaries: on first/last pages, remain on the current page without an error or wraparound.
- Taps must not unexpectedly trigger a page turn while the user is drawing, selecting, or dragging in annotation/edit mode.
- With zoom enabled, distinguish intentional panning, tapping, and double-tapping; gestures and priorities must be verified on tablets.
- Cache adjacent pages at a memory-safe resolution, cancel stale rendering jobs after rapid navigation, and close renderer resources correctly.
- Remember the last successfully visible page in metadata so opening the document can resume there.

## 4. Internationalization

Mandatory languages:

- **English (`en`)** — base strings and fallback.
- **Catalan (`ca`)**.
- **Spanish (`es`)**.

Resources:

```text
app/src/main/res/values/strings.xml       # English fallback
app/src/main/res/values-ca/strings.xml    # Catalan
app/src/main/res/values-es/strings.xml    # Spanish
```

Use Android resources for all user-facing strings, including navigation, confirmations, permissions explanations, accessibility labels, empty/error states, notifications, and PDF operation progress. Avoid concatenation that breaks translation; use plurals and formatted string parameters.

At first, follow the system locale with English fallback. A manual language selector is part of the planned settings. Because API 26 and API 27 must work, manual selection requires a backwards-compatible locale mechanism; evaluate AppCompat's per-app language support and its activity integration with Compose before implementing it. Never assume a newer system-only per-app locale API is sufficient.

Tests must detect missing translations and invalid format parameters; UI review must check truncation and RTL-independent layout resilience (RTL localization itself is not an initial language requirement).

## 5. PDF library and file ownership

### 5.1 Storage model

The user chooses a local storage location or folder through the **Storage Access Framework (SAF)**. Files and directories are real, user-accessible documents. Use persisted URI permissions where allowed; do not rely on raw filesystem paths or broad all-files access. Files may live on local/internal storage or a supported document provider.

Operations: browse/create subfolders; import one/multiple PDFs; copy/move/rename/delete; export/share; search and sort; favorites; remember last page. Confirm destructive actions and handle revoked permissions, provider errors, duplicate names, unavailable media, and insufficient storage.

Do not import every document into private app storage by default. Private cache files may be created for rendering or transactional editing and removed safely. In particular, `PdfRenderer` may require seekable input: materialize inaccessible provider streams into **temporary private copies** without moving the authoritative document.

### 5.2 Application metadata

Plan for Room to index document URIs, display names, folder identifiers, favorite flags, timestamps, last page, and optional cached thumbnails. Database information must **not** replace PDF content. Reconcile deleted/renamed/moved externally managed files; do not treat a stale Room row as proof a file exists. Migrations must preserve user data.

Use DataStore for settings such as light/dark mode, preferred reading behavior, last chosen storage root, and (when implemented) manual locale choice. Credentials and tokens do not belong in plain preference files.

## 6. PDF reader — rendering

The primary built-in renderer candidate is Android **PdfRenderer** (available on minSdk 26). Render on a background dispatcher, present bitmaps via Compose, and limit memory usage on older devices.

- Support single-page reading, arbitrary portrait/landscape page dimensions, differing page sizes, and page rotation.
- Fit page into available viewport; preserve legibility when zooming; use appropriate resampling to avoid poor staff-line clarity.
- Cache only nearby pages or thumbnails with bounded memory; release bitmaps when no longer needed.
- A PDF can be password-protected, malformed, image-heavy, or use complex fonts/transparencies: failures must produce a helpful localized message, never data loss.
- Some standard PDF annotations are not reliably rendered on all Android versions via PdfRenderer. The app must render its own annotation overlay where necessary and verify interoperability after saving. Do not claim universal PDF annotation support until proven in tests.

## 7. PDF page editor

Planned actions:

1. **Rotate:** rotate individual pages by 90°, 180°, or 270°; preserve page content and correct presentation.
2. **Reorder:** display thumbnails, move selected pages into a new order, validate complete order mapping.
3. **Delete:** remove pages explicitly, preventing invalid empty documents or offering a clear error when unsupported.
4. **Merge:** import multiple PDFs, define document and page order, preserve final page count and content.
5. **Extract:** choose nonconsecutive pages and/or ranges, create a separate PDF with the selected pages.

**PDFBox-Android 2.0.27.0** is the initial edit-engine candidate. Its age and transitive dependencies are known risks: verify operations against fixed fixtures, inspect licenses, and keep it behind an interface so it can be replaced if it cannot safely support required documents and annotations. PDFBox is a prospective dependency, not necessarily installed today.

### 7.1 Safe save transaction

1. Obtain read permission and (when required) write permission to the destination.
2. Verify input readability and any password restrictions.
3. Create a **temporary private working copy** of the source(s).
4. Apply transformations to that copy, never directly to the authoritative PDF.
5. Write a complete temporary result, then reopen and validate its structure, page count, page content/rotations and supported annotations.
6. For **Save as**, write a new output; preserve the original regardless of failure.
7. For **Replace original**, explicitly confirm, ensure that a rollback/recovery copy exists, then use the strongest provider-supported safe replacement strategy. Do not claim every SAF provider supports atomic replacement.
8. On any interruption, exception, cancellation or disk-full event, keep the original accessible; clean or retain recoverable temporary artifacts appropriately.

A green PDF-rendering result alone does not establish document integrity. Automatic regression tests must check structural invariants, page order, annotation persistence and safety on failure.

## 8. Annotation system

### 8.1 User tools

- Freehand ink via finger or stylus; strokes with selectable color and width.
- Text insertion and editing (font size, color, text content and position).
- Highlight and underline tools with selectable color and thickness/opacity as applicable.
- Eraser, select/move, undo/redo; an unsaved-changes indicator and explicit save/export flow.
- Prevent conflicting gestures between writing, zoom/pan and edge page turns. Stylus pressure/tilt support may be enhanced where hardware exposes useful data; no hardware-specific feature may be required for basic operation.

### 8.2 Coordinate system and persistence

Annotations must be stored in **PDF page coordinates**, not screen pixels. Translate between the document's crop/media box, rotation, viewport scale, pan position and Compose pointer coordinates. Reopening, rotating, resizing or zooming must preserve geometry.

Preferred mapping: standard PDF annotation objects (`Ink`, `Highlight`, `Underline`, `FreeText`) with usable appearance streams when the engine supports them. Verify final rendering in the app and at least one independent PDF reader. Do not promise that every exported annotation remains editable in all PDF viewers: record format limitations and do not silently flatten unless the user explicitly accepts a documented fallback.

A local in-memory command/history model will support undo/redo until save. A supported PDF edit must be saved to a complete validated output, following the same transactional strategy as structural page editing. The user must not lose unsaved annotations if the app is interrupted unexpectedly; implement draft recovery when the annotation phase is developed.

## 9. Google Drive import/export

Cloud access is **optional** and limited to user-authorized files. Planned integration uses Google authorization/identity services and Google Drive REST API. Prefer narrowly scoped `drive.file` access plus explicit file selection, subject to current Google platform requirements and review. Do not request whole-Drive access without a separately approved need.

Initial cloud operations:

- Connect/disconnect an account with user consent.
- Select a PDF from Drive and import it into the local library.
- Upload/export a selected score or edited copy to Drive.
- Show progress, cancellation, errors, and success confirmations.
- Handle token expiry, revoked consent, offline operation, duplicate names, and upload failures.

**No automatic two-way synchronization** is planned in the initial scope. Do not assume local file names identify Drive objects; use Drive file IDs and explicit operation semantics. Provide no made-up OAuth client IDs, tokens, API keys, or credentials. The Google Cloud OAuth setup is a separate human-approved preparation step.

## 10. Architecture and package plan

Initially keep **one Android Gradle `:app` module**. Gradually separate code by feature/responsibility, for example:

```text
com.abdev.partituraspdf/
  ui/                     shared Material 3 theme and components
  library/                browse/import/favorites/search
  reader/                 rendering and page navigation
  editor/                 page operations and transactional output
  annotations/            ink/text/highlight and persistence
  storage/                SAF, file operations, temporary documents
  drive/                  user-authorized Google Drive exchange
  data/                   metadata persistence, settings
  core/                   shared abstractions, error types
```

This is a **future package plan**, not an instruction to create empty packages/classes in Phase 0.

Architecture rule: UI via Compose; state holders/ViewModels coordinate work; repositories/interfaces isolate storage, PDF engines, and remote services. Use Kotlin Coroutines for asynchronous operations. Keep UI independent of PDFBox concrete types. Store user-visible copy in string resources; convert expected exceptions into typed, localized errors.

## 11. Security, privacy, reliability

- No mandatory account, telemetry, analytics or backend for basic use.
- Treat PDFs as untrusted inputs. Enforce sensible resource limits and avoid excessive memory/CPU usage for malicious or huge documents.
- Avoid unrestricted external storage permissions; use SAF-granted user access.
- Never commit secrets. OAuth authorization requires consent and secure token handling.
- Do not log private document contents, authorization tokens or user file paths unnecessarily.
- Validate file types based on content where practical, not only filename extension.
- Review dependencies for known vulnerabilities and license compatibility prior to introduction.
- Backups must not expose sensitive cache, Google authorization information or temporary user documents unintentionally. Audit the existing manifest's `allowBackup` and backup rules before storing sensitive data.
- Preserve a recoverable original during edits and after interruptions.

## 12. Test-fixture dataset

Maintain a repository-controlled, repeatable fixture library at:

```text
test-fixtures/
  pdf/basic/
  pdf/page-sizes/
  pdf/complex/
  pdf/annotations/
  pdf/invalid/
  manifest.json
  README.md
```

Target suite includes single-page, 2-page, 10-page, 50-page, 100-page, A3 landscape, A5 portrait, mixed sizes, rotated pages, image/scanned scores, vector text/music, standard annotations, complex fonts/transparency, password-protected, and deliberately corrupt PDFs.

- Fixtures are immutable inputs. Test code must use temporary copies for edits.
- Each fixture's checksum, page count, size/rotation features and expected validity are declared in `manifest.json`.
- The fixture manifest and verification script should fail CI if fixtures are missing or unexpectedly altered.
- PDF fixtures belong only to test classpaths and instrumentation test assets; never package them in the release APK.
- Large stress cases should be deterministically generated rather than stored as enormous Git binaries.
- Fixes for previously unseen PDF bugs must add a permanent regression fixture or a reproducible generator.

## 13. Mandatory test and approval policy

### 13.1 Branches and reviews

- `main` is protected; direct pushes, force pushes and deletion are prohibited after protection is enabled.
- Each approved task goes into its own branch and PR. Prefer conventional branch names such as `chore/phase-0-foundation` or `feat/library-import`.
- A PR describes requirement IDs, files modified, risks, tests and exact results. No silent additions beyond approved scope.
- CI must pass on the latest PR revision; review conversations must be resolved.
- **Only the human project owner may authorize a merge.** Codex cannot approve or merge its own work.
- Use squash merges and keep a linear `main` history.
- GitHub does not permit the PR author to approve their own PR; for a single-maintainer repository, rely on explicit owner merge control rather than imposing an impossible second-person approval rule.
- Changes to testing or merge policies require their own approved PR; an agent cannot weaken controls to pass a failing change.

### 13.2 Automated checks

Every PR must be subject to:

- Debug build (`:app:assembleDebug`).
- Android Lint (`:app:lintDebug`), with no new lint errors.
- JVM unit tests (`:app:testDebugUnitTest`), including regression tests.
- Android API 26 instrumentation tests once CI supports them; establish a working emulator gate during the foundation phase.
- Localization/resource completeness checks.
- Applicable integration, Compose UI, and data-integrity tests.
- Dependency/secrets checks where configured; vulnerabilities with critical/high severity block acceptance.

Do not mark skipped, failed-to-run, or network-blocked jobs as **passed**. Non-applicable future-feature coverage checks must be reported as not applicable, not fabricated. If a required gate is unavailable, the PR remains unapproved until the gate runs successfully.

### 13.3 Coverage and compatibility

- New **testable business logic**: >=90% line coverage and >=80% branch coverage; exclude generated code and resources only with recorded justification.
- Critical PDF-edit/save cases: **100% of defined critical scenarios** must have explicit tests (not a claim of 100% code coverage).
- Every bug fix includes a test demonstrating the previous failure.
- Instrumentation on **API 26** is required per PR.
- **API 27** scheduled CI plus phase-close checks; manual acceptance on a physical Android 8.1 tablet before closing a functional phase.
- Periodically test a recent Android API (35/36 or later compatible runner).
- Regressions, untranslated user strings, missing permissions handling, or undetected loss of original PDFs block acceptance.
- Test memory/performance with representative files before approving the reader, editor, or annotations.

### 13.4 Severity and release blockers

- **P0 (critical):** irreversible PDF loss/corruption, serious security flaw. Immediate block.
- **P1 (high):** core feature broken, reproducible API-26 crash, substantial data-integrity defect. Block.
- **P2 (medium):** material issue in a phase being delivered. Resolve before phase acceptance.
- **P3 (low):** nonblocking enhancement, tracked with owner approval; it cannot represent an unmet acceptance criterion.

No phase is complete without requirement fulfillment, applicable passing tests, Android 8.1 device verification where required, three-language UI review, current docs, and explicit owner acceptance.

### 13.5 Verification and evidence

Store testing policies in `docs/TESTING_POLICY.md`, change control in `docs/CHANGE_CONTROL.md`, completion criteria in `docs/DEFINITION_OF_DONE.md` and the PR template at `.github/PULL_REQUEST_TEMPLATE.md` during Phase 0. Such files are **planned outputs** and may not exist when this document is first committed.

## 14. Roadmap and milestone constraints

**Phase 0 (preparation only):** verify toolchain, documentation, agent rules, theming/localization groundwork, initial CI, test-policy files and fixture validation. No feature implementation.

Subsequent development phases will be defined in separate, owner-approved tasks. A possible ordering (not a committed phase plan) is: local files/library, reader, page editing, annotations, Drive integration, then stabilization/release readiness. No stage may begin merely because the previous one appears in this example.

Each phase must declare: scope, exclusions, behavior, failure cases, tests/fixtures, Android compatibility and localization acceptance. Do not expand scope without explicit approval.

## 15. Risks and open validations

| Risk | Required resolution |
| --- | --- |
| PDFBox-Android maintenance and security | Audit capabilities, transitive libraries and licenses; test against deterministic PDFs before adopting it as final. |
| Viewing standard annotations on Android 8 | Verify overlay handling, exported PDF appearance streams and third-party viewer interoperability. |
| Stylus and gesture priority | Test actual tablets, pen/finger combinations and zoom/annotation conflicts. |
| SAF file providers | Test non-seekable streams, rename/replace support, revoked permissions and failed writes; preserve originals. |
| Older-tablet memory pressure | Define measurable bitmap/thumbnail budgets and test 100-page, scanned and high-resolution inputs. |
| Drive authorization policies | Confirm current OAuth setup, scopes, Google consent screen and storage/security implications before code. |
| API 26 CI emulator | Configure and demonstrate reliable instrumented tests before requiring green checks for merge. |
| Restrictive Creative Commons license on code | CC advises against using its licenses on software; confirm compatibility with third-party code and intended distribution before release. |

## 16. Licensing and attribution

The project owner requested **CC BY-NC-ND 4.0** for original project material (see repository root `../LICENSE`). Attribution is required; commercial reuse and distribution of derivatives are not granted by that license. Creative Commons explicitly advises against applying its licenses to software; licensing of the code and external contributions should receive a deliberate legal review before public software distribution. Third-party source files, Android libraries, externally sourced sheet music and fonts maintain their own licenses; repository ownership cannot override them. The fixed PDF fixtures must identify provenance in their own documentation.

## 17. Decision log

| ID | Decision | Status |
| --- | --- | --- |
| D-001 | Native Android app in Kotlin | Confirmed |
| D-002 | Minimum Android API 26 | Confirmed, non-negotiable |
| D-003 | Jetpack Compose + Material 3 | Confirmed |
| D-004 | Green `#A5D6A7`, blue `#90CAF9` | Confirmed |
| D-005 | English, Catalan and Spanish user-facing UI | Confirmed |
| D-006 | Local physical folders/PDF files via SAF | Confirmed approach |
| D-007 | Edge taps to navigate PDF pages | Confirmed |
| D-008 | Rotate/reorder/delete, merge/extract | Confirmed scope |
| D-009 | Finger/stylus, highlight/underline, text annotations saved inside PDF | Confirmed scope; interoperability verification pending |
| D-010 | Optional Drive import/export, not mandatory cloud sync | Confirmed scope |
| D-011 | PdfRenderer for reading, PDFBox-Android as candidate edit engine | Proposed technical implementation; validation pending |
| D-012 | Single app module at start, gradual internal separation | Confirmed |
| D-013 | Strict CI, test coverage, data-integrity fixtures, PR approval by owner | Confirmed policy |
| D-014 | No feature implementation during Phase 0 | Confirmed |
| D-015 | Requested CC BY-NC-ND 4.0 restrictive license | Requested; legal suitability for software pending review |

**Change control:** Amend this specification only through a scope-explicit PR approved by the human project owner. Keep status (planned, implemented, verified) separate in subsequent documentation and never describe future features as delivered.
