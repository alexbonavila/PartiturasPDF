# AGENTS.md — Mandatory Instructions for Coding Agents

**Repository:** `alexbonavila/PartiturasPDF`  
**Application:** Partituras PDF (`com.abdev.partituraspdf`)  
**Current stage:** Phase 0 / foundation, unless the human project owner explicitly approves a later phase.

This file applies to **all agent work** in this repository, including Codex. Read it before analyzing, editing, proposing, testing, or committing changes.

## 1. Non-negotiable rules

1. **Never implement unapproved functionality.** Work only on the requirements and files within the expressly approved task. If the task conflicts with project policy or has essential ambiguities, ask the owner rather than assuming.
2. **Do not bypass approval.** Work in a dedicated branch and propose a pull request. Never push directly to `main`, force-push to `main`, approve your own work, or merge a PR. Only the human project owner authorizes merges and phase completion.
3. **Protect Android 8.0 support.** `minSdk = 26` is mandatory and must not be increased. Do not use APIs unavailable on API 26 without a tested compatibility implementation.
4. **Use Kotlin and Jetpack Compose/Material 3** for application development. Keep the existing Gradle Kotlin DSL configuration, catalog, and compatible toolchain unless a change is explicitly justified.
5. **No hardcoded user-visible text.** All text visible to users (including errors, dialogs, notifications and accessibility descriptions) must be localized in English (`en`), Catalan (`ca`) and Spanish (`es`). Technical documentation, identifiers, comments, commit/PR text, and Codex prompts are in English.
6. **Never risk user PDFs to save time.** Editing must operate on a temporary copy and preserve the original across failures. A partial output cannot be mistaken for a successful save.
7. **Do not fake quality.** Never disable tests, change assertions to avoid errors, hide lint findings, mark unavailable checks as passed, or reduce coverage requirements to make a PR green.
8. **Keep secrets out of Git.** Do not include tokens, signing keys, OAuth client secrets, local SDK paths, private documents, or unnecessary personally identifiable information.
9. **Do not silently add dependencies or licenses.** Explain why a dependency is needed, verify API 26 support, maintenance/security and license compatibility; use `gradle/libs.versions.toml`.
10. **Keep documentation truthful.** Distinguish planned work, code that exists, tests actually executed, and behavior that has been verified on a device.

## 2. Required reading before coding

- `README.md` — public description and current status.
- `docs/PROJECT_SPECIFICATION.md` — requirements, decision log, risks and implementation boundaries.
- `LICENSE` — restrictive project license; do not replace or relicense it.
- `docs/TESTING_POLICY.md`, `docs/CHANGE_CONTROL.md`, `docs/DEFINITION_OF_DONE.md` — when created during Phase 0, these govern the detailed testing and change-gate criteria.
- `test-fixtures/README.md` and `test-fixtures/manifest.json` — when the PDF fixture suite is present, these define immutable test inputs.

If approved task instructions and repository documentation appear inconsistent, **stop and request clarification**. Do not invent permission to relax any safety or approval condition.

## 3. Baseline project constraints

Observed starter configuration (inspect actual files before acting):

| Item | Baseline |
| --- | --- |
| Application ID / namespace | `com.abdev.partituraspdf` |
| minSdk | **26** |
| targetSdk / compileSdk | 37 / 37 |
| Android Gradle Plugin | 9.4.1 |
| Gradle Wrapper | 9.8.0 |
| Kotlin Compose plugin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| Java source/target | 11 |
| Gradle daemon JVM | 25 |
| Project structure | Single `app` module |

Gradle daemon JDK and source/target compatibility are not Android platform requirements. Avoid unnecessary toolchain changes. AGP 9 incorporates Kotlin support; do not apply a redundant legacy `org.jetbrains.kotlin.android` plugin. Validate any build-system changes using the actual wrapper and the project's supported environment.

## 4. Design and architectural rules

- Keep a **single Gradle `:app` module** until an approved task demonstrates a real need to split it.
- Prefer clear packages based on feature and responsibility; do not create placeholder classes, empty modules, unused abstractions, or speculative infrastructure.
- UI: Compose + Material 3, adaptive tablet layout, light/dark colors, accessible contrast and semantics.
- Brand: primary `#A5D6A7` and secondary `#90CAF9`; choose accessible foreground and supplementary colors.
- Core reading and local edits must work offline and must not depend on Google Drive.
- Use Storage Access Framework and persisted URI permissions appropriately; do not demand broad all-files access as a shortcut.
- The PDF reader is expected to use Android `PdfRenderer`, with an appropriate annotation overlay where needed.
- PDFBox-Android is a **candidate**, not an unquestionable final dependency. It must be assessed for security, format support, annotation fidelity, license and API compatibility before production use.
- Use repositories/interfaces to keep storage, PDF renderer/editor and optional Drive services independent from the presentation layer as implementation evolves.
- Google Drive requires explicit user authorization and minimal scopes. Do not implement automatic whole-library synchronization without separate approval.
- For PDF annotation placement, use PDF page coordinates (including rotation/crop boxes), not saved screen pixels. Save standard annotation objects where technically supported and validated.

## 5. Localization requirements

- English is the base/fallback language in `res/values/strings.xml`.
- Catalan translations go in `res/values-ca/strings.xml`.
- Spanish translations go in `res/values-es/strings.xml`.
- Resource arguments, plurals and formatting must behave correctly in all three languages.
- Manual language selection on Android 8 must use a confirmed backward-compatible mechanism when the settings feature is implemented. Do not assume Android 13's per-app language APIs are available on API 26.
- No code review is complete without verifying translations for every new or changed user-facing string.

## 6. Workflow for every approved change

**Before editing:**

1. Identify the approved phase, exact acceptance criteria, excluded scope, affected files and risks.
2. Inspect the existing implementation and tests first; minimize the diff.
3. State a concise implementation/test plan for nontrivial changes.
4. Create or use a task-specific development branch. Never write to `main`.

**While editing:**

1. Implement only the approved behavior.
2. Add meaningful tests for any changed functionality; bug fixes require a regression test that would have failed before the fix.
3. Keep code idiomatic, maintainable, localized and API-26-compatible.
4. Keep docs, dependency metadata and fixtures synchronized when necessary.
5. Treat source PDF files and test fixtures as immutable. Use copies for mutations.

**Before delivery:**

1. Run available build, lint, unit tests, instrumentation/CI checks, and fixture validation relevant to the task.
2. Run the broader regression suite required by policy, not merely a single favorable test.
3. Review the full diff for out-of-scope changes, secrets, accidental binaries and untranslated strings.
4. Prepare a PR with requirement references, test evidence, risks, screenshots where applicable, and explicit unanswered issues.
5. Stop. Wait for explicit human review and merge authorization.

## 7. Mandatory testing and quality gates

At minimum, attempt:

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

Use `gradlew.bat` on Windows. All PRs must eventually pass the **API 26 Android instrumentation gate** and localization checks as established by Phase 0 CI. Apply feature-specific integration, Compose UI, PDF structural-integrity and regression tests. Build success by itself is insufficient.

Strict thresholds:

- **>=90% line coverage and >=80% branch coverage** for new, testable business logic.
- **100% of defined critical PDF save/edit scenarios** must be covered by explicit tests.
- **0 required test failures; 0 unexplained skipped checks**.
- No new Android Lint errors or critical/high unresolved security findings.
- Test on Android API 26 for PRs; API 27 in scheduled/phase-close automation and on a physical Android 8.1 tablet before accepting functional phases.
- User-facing work requires English/Catalan/Spanish translation checks and accessible UI verification.

These are project policy requirements, **not claims that their pipelines currently exist**. Phase 0 must establish honest, working initial gates. If an environment lacks Android SDK, network access, the required JDK or an emulator, report the missing prerequisites and mark dependent tests **NOT RUN / BLOCKED**, never PASS. Do not waive the gate yourself.

## 8. PDF test fixtures and safety

The authoritative fixture suite belongs under `test-fixtures/`, with metadata and SHA-256 hashes in `manifest.json` and a verification script. All test code must refer to that dataset or deterministic generated cases, and never overwrite fixture originals. Maintain cases for page sizes/counts, rotation, images, vectors, annotations, encryption and corrupted PDFs. Keep fixture binaries **out of production APKs**.

For edited PDF results, verify requested page order, count, rotations and retained content, annotation persistence where relevant, readability, and original-document survival after errors. Simulate permission denial, cancellation, invalid input and interrupted/disk-full writes.

## 9. Pull requests, approvals and incidents

- Branch per approved task; PR required. Prefer **squash merge** and linear history.
- Required CI jobs must pass on the **latest commit**, and all blocking review conversations must be resolved.
- Human project owner has the sole authority to approve integration. Automated agents cannot merge, enable bypass rules, weaken protections or rewrite policy in the same PR simply to avoid a failing gate.
- P0/P1 defects (data loss, serious vulnerability, broken core behavior/API-26 compatibility) block immediately. Material phase-scope P2 defects also block phase acceptance; P3 items require explicit owner triage.
- All required tests, supported-device checks, documentation and acceptance criteria must pass before a phase is declared complete.
- A change to testing policy, AGENTS.md, licensing, branch protection or quality gates requires an **explicitly approved standalone PR**.

For a single-maintainer repository, do **not** enable a GitHub reviewer-count requirement that makes a self-authored PR impossible to merge. Instead enforce CI/branch rules and require the owner personally to perform the final merge. Do not claim GitHub settings are enabled without verifying the actual ruleset.

## 10. Special restriction — Phase 0 only

Until the owner explicitly authorizes a later phase, permitted work is limited to:

- Toolchain verification without unnecessary changes.
- Material 3 theme and base string-resource groundwork.
- README/AGENTS/specification, policies and engineering documentation.
- GitHub Actions and test/fixture verification infrastructure.
- Existing starter app corrections strictly necessary for a clean build/test setup.

**Do not implement** library screens or repositories, PDF reader, PDF editor, annotations, Drive sign-in, a database schema, app navigation, or the language picker during Phase 0. Future behavior can be documented, not built.

## 11. Required final report from any agent

Every task report must contain:

1. Approved objective and scope.
2. Files created, edited and deleted.
3. Build/dependency/toolchain changes and why.
4. Exact commands executed and observed results (**PASS / FAIL / NOT RUN**).
5. Tests added and coverage impact, if applicable.
6. Platform/device and localization checks actually performed.
7. Outstanding failures, security concerns, uncertainties and out-of-scope requests.
8. PR/branch information and an explicit statement that **nothing was merged**.

Never describe the task as complete when a mandatory acceptance gate remains blocked. Do not start the next phase without a new approved request.
