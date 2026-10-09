# Partituras PDF

**A native Android app for organizing, reading, editing, and annotating sheet music in PDF format.**

Partituras PDF is a tablet-first project designed to turn an Android device into a practical digital music stand and a manageable library of musical scores. Its goal is to keep scores in ordinary PDF files, provide distraction-free reading with simple page turns, and make common changes without requiring a desktop PDF editor.

> **Project status: Phase 0 completed, merged and accepted by the project owner.** The repository contains the Android starter with its Material 3 theme, English/Catalan/Spanish resources and established quality infrastructure. No product features are implemented; the capabilities below remain **planned**. Future work requires separate owner approval.

## Planned capabilities

- **Score library:** organize PDFs in real folders and subfolders; import, export, search, sort, rename, move, copy, delete, and mark favorites.
- **Sheet-music reader:** full-screen display; tap the left or right side to turn pages; zoom, fit-to-page, portrait/landscape support, page preloading, keep-screen-awake, and resume at the last page.
- **PDF page editor:** reorder and rotate pages, delete pages, merge documents, and extract selected pages into a new score.
- **On-score annotations:** draw with a finger or stylus, highlight/underline, and add text; select colors and stroke widths, use undo/redo, and save supported annotations inside the PDF itself.
- **File exchange:** local import/export via Android's system file picker and user-authorized import/export with Google Drive.
- **Accessibility and localization:** Material Design 3 with light/dark themes and all user-facing text in **English, Catalan, and Spanish**.

The core library, reader, and editor are intended to work **offline**. Internet access is only needed for external services such as Google Drive.

## Technology direction

| Area | Selected approach |
| --- | --- |
| Platform | Native Android, Kotlin |
| Minimum Android version | Android 8.0 (API 26); Android 8.1 (API 27) tablet compatibility is essential |
| UI | Jetpack Compose, Material Design 3 |
| App structure | Initially one `app` module; MVVM/repositories as features are added |
| PDF reading | Android `PdfRenderer` plus a custom Compose reading interface |
| PDF manipulation | PDFBox-Android candidate, subject to format and annotation compatibility testing |
| Local files | Android Storage Access Framework (SAF) |
| Metadata and settings | Room and DataStore when their respective features are introduced |
| Cloud | Google Drive REST API with user consent, planned for a later phase |

The exact build-tool versions are defined by the files in this repository. Do not assume that a dependency described as *planned* has already been added.

## Visual direction

- **Primary:** light green, `#A5D6A7`.
- **Secondary:** light blue, `#90CAF9`.
- **Interface:** Material 3, accessible contrast, adaptive layouts, light and dark appearances.
- **Reading mode:** maximize visible sheet-music area; keep navigation easy and unobtrusive.

## Development

1. Open the repository in a compatible version of **Android Studio**.
2. Allow the Gradle sync to complete and install any Android SDK packages requested by the project.
3. Run the generated starter application on an emulator or device (Android 8.0 or newer).
4. From a terminal, run the available checks using the Gradle Wrapper:

```bash
# macOS / Linux
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest

# Windows (PowerShell or Command Prompt)
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

The Unix wrapper is executable; CI also invokes it through Bash. Use JDK 25
and Android SDK 37; Java source/target compatibility remains 11.

The Phase 0A workflow defines build, lint, JVM tests, actual API 26 emulator tests,
localization, PDF fixture verification and an aggregate quality gate. Weekly and
requested compatibility runs also execute API 27 and API 36 tests. See
[`docs/CI.md`](docs/CI.md) for commands and limitations. The starter now uses a
fixed brand light/dark theme and complete English/Catalan/Spanish resources.
The final [Phase 0 compatibility run](https://github.com/alexbonavila/PartiturasPDF/actions/runs/37972414539)
passed on `main` at `e87fdf9f82d79ce6d0ee0b8d65a7dbe452674a37`, including
actual API 26, API 27 and API 36 instrumentation and the aggregate quality gate.
See the CI guide for the exact evidence; passing CI does not authorize future work.

## Documentation and project rules

- [`docs/PROJECT_SPECIFICATION.md`](docs/PROJECT_SPECIFICATION.md): product requirements, UI and architecture decisions, file handling, security, testing, and implementation boundaries.
- [`AGENTS.md`](AGENTS.md): mandatory instructions for automated coding agents and contributors.
- [`docs/TESTING_POLICY.md`](docs/TESTING_POLICY.md): mandatory checks, coverage, compatibility, fixtures and evidence.
- [`docs/CHANGE_CONTROL.md`](docs/CHANGE_CONTROL.md): approval workflow and manual GitHub Ruleset instructions.
- [`docs/DEFINITION_OF_DONE.md`](docs/DEFINITION_OF_DONE.md): automated, manual and final human acceptance.
- [`docs/PHASE_0A_REPORT.md`](docs/PHASE_0A_REPORT.md): historical audit, initial verification and then-unresolved gates.
- [`docs/TECH_STACK.md`](docs/TECH_STACK.md): current toolchain, theme/locales and candidate dependency risks.
- [`docs/ROADMAP.md`](docs/ROADMAP.md): Phase 0 status and future scope requiring separate approval.
- [`LICENSE`](LICENSE): the repository's restrictive Creative Commons license text.

Development is controlled through feature branches, pull requests, automated checks, and **explicit approval by the project owner**. Automated agents may propose changes but must never merge them into `main` without authorization.

## Repository layout

```text
PartiturasPDF/
├── app/                         Android app (starter project)
├── gradle/                      Gradle wrapper and dependency catalog
├── docs/
│   ├── PROJECT_SPECIFICATION.md Product and engineering specification
│   └── *.md                     Quality policies, CI guide and verification evidence
├── .github/                     CI workflow, setup action and PR template
├── scripts/                     Quality validators and their infrastructure tests
├── test-fixtures/               15 committed synthetic PDF fixtures (separate CC0)
├── AGENTS.md                    Instructions for Codex and other agents
├── LICENSE                      CC BY-NC-ND 4.0 legal text
└── README.md                    This document
```

The PDF dataset is present and immutable. Install its declared Python dependencies
and run `python test-fixtures/scripts/verify_fixtures.py`; never regenerate it in
CI. The owner merged Phase 0A, Phase 0B and the API 27 correction (PRs #1, #2 and
#3) and accepted Phase 0. No product features are implemented. Architectural
design and shared contract definition are the next planned activities; neither
is implemented or authorized by this documentation. They require separate owner
approval. Failed mandatory checks still block integration.

## License and reuse

The project's original material is intended to be shared under **Creative Commons Attribution–NonCommercial–NoDerivatives 4.0 International (CC BY-NC-ND 4.0)**, subject to third-party rights and separately licensed components. This allows noncommercial sharing with attribution **but does not grant permission to distribute modified versions**. It is **not an open-source software license**. See [`LICENSE`](LICENSE) and the [Creative Commons license page](https://creativecommons.org/licenses/by-nc-nd/4.0/).

**Important:** Creative Commons recommends against using CC licenses for software. The project owner has deliberately requested this restrictive license; its suitability for source code, downstream builds, third-party dependencies, and outside contributions should be reviewed before any public software release. Google/Android libraries and other third-party materials retain their own licenses. A repository license cannot override those rights.

## Project

Repository: https://github.com/alexbonavila/PartiturasPDF
