# AGENTS.md — Mandatory Instructions for Coding Agents

**Repository:** `alexbonavila/PartiturasPDF`  
**Application:** Partituras PDF (`com.abdev.partituraspdf`)  
**Implementation status (not an authorization):** Phase 0, shared architecture, SAF/PDF adapters, Room/DataStore persistence, and application composition are implemented. Phase F (Library), Phase G (Reader with isolated PDF worker), and Phase H (Settings) are implemented and merged through PRs #18, #19, and #20. These components have independent tests but are not connected to the production application flow. `MainActivity` still displays the starter greeting. Phase I will implement the production host, SAF picker, navigation, lifecycle coordination, and runtime preference/locale application. PDF editing, annotations, and Google Drive integration remain unimplemented. The separate pre-I closeout branch addresses complete localization, coverage, and documentation; verify its merge status before treating its changes as part of `main`. Only the human project owner can authorize further work and formal phase acceptance.

This file applies to **all agent work** in this repository, including Codex. Read it before analyzing, editing, proposing, testing, or committing changes.

## 1. Non-negotiable rules

1. **Never implement unapproved functionality.** Work only on the requirements and files within the expressly approved task. If the task conflicts with project policy or has essential ambiguities, ask the owner rather than assuming.
2. **Do not bypass approval.** Work in a dedicated branch and propose a pull request. Never push directly to `main`, force-push to `main`, approve your own work, or merge a PR. Only the human project owner authorizes merges and phase completion.
3. **Protect Android 8.0 support.** `minSdk = 26` is mandatory and must not be increased. Do not use APIs unavailable on API 26 without a tested compatibility implementation.
4. **Use Kotlin and Jetpack Compose/Material 3** for application development. Keep the existing Gradle Kotlin DSL configuration, catalog, and compatible toolchain unless a change is explicitly justified.
5. **No hardcoded user-visible text.** All new user-visible text and accessibility descriptions require Spanish resources. Preserve completed English/Catalan translations; staged new feature translation follows the Spanish-first procedure in section 5 and TESTING_POLICY.md. Technical documentation, identifiers, comments, commit/PR text, and Codex prompts are in English.
6. **Never risk user PDFs to save time.** Editing must operate on a temporary copy and preserve the original across failures. A partial output cannot be mistaken for a successful save.
7. **Do not fake quality.** Never disable tests, change assertions to avoid errors, hide lint findings, mark unavailable checks as passed, or reduce coverage requirements to make a PR green.
8. **Keep secrets out of Git.** Do not include tokens, signing keys, OAuth client secrets, local SDK paths, private documents, or unnecessary personally identifiable information.
9. **Do not silently add dependencies or licenses.** Explain why a dependency is needed, verify API 26 support, maintenance/security and license compatibility; use `gradle/libs.versions.toml`.
10. **Keep documentation truthful.** Distinguish planned work, code that exists, tests actually executed, and behavior that has been verified on a device.

## 2. Required reading before coding

- `README.md` — public description; cross-check implementation status with current source and dated task evidence.
- `docs/PROJECT_SPECIFICATION.md` — requirements, decision log, risks and implementation boundaries.
- `LICENSE` — restrictive project license; do not replace or relicense it.
- `docs/TESTING_POLICY.md`, `docs/CHANGE_CONTROL.md`, `docs/DEFINITION_OF_DONE.md` — established policies governing testing, change control, and acceptance.
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

- **Localization baseline:** The project follows a Spanish-default localization policy. The pre-I F/G/H closeout delivers complete Spanish, Catalan, and English resources for all currently implemented features, including the debug SAF acceptance UI. Verify that this closeout is merged before treating its resource migration as part of `main`.
- **Resource organization:** Following the completed migration, `res/values/` contains the Spanish default/fallback, while `values-es`, `values-ca`, and `values-en` contain complete corresponding translations. Resource names, types, formatting arguments, plurals, and accessibility semantics must remain consistent across supported languages.
- **Historical staging:** The `*_spanish_first.xml` mechanism was used during F/G/H development as temporary translation debt. The completed localization validator rejects remaining staged resources. The existing `TESTING_POLICY.md` documents the earlier staging policy; any future change to localization requirements or validation policy requires separate approval.
- **Automatic language selection:** The application must use the device language when Spanish, Catalan, or English is supported, and fall back to Spanish for unsupported languages. No manual language selector is permitted. Legacy `LanguageSelection` persistence remains compatible but must not control the active language. Phase I owns actual runtime locale application, configuration changes, and Activity recreation.
- **Validation and acceptance:** Maintain complete resource parity, format validation, localized accessibility text, and Android API 26 compatibility. Automated resource checks do not replace Android runtime tests, human linguistic review, visual inspection, or TalkBack acceptance. Record missing evidence explicitly.

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
- User-facing work requires Spanish-first/completed-translation checks and accessible UI verification; eventual English/Catalan translation acceptance remains mandatory.

These are project policy requirements. CI gates were established during Phase 0; their existence is not proof that they passed on the revision under review. Check real execution evidence at the latest required commit. If an environment lacks Android SDK, network access, the required JDK or an emulator, report the missing prerequisites and mark dependent tests **NOT RUN / BLOCKED**, never PASS. Do not waive the gate yourself.

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

**Historical Phase 0 scope:** The owner subsequently approved development beyond Phase 0. This section records the original Phase 0 restrictions; it neither prohibits already approved and implemented later work nor authorizes any new functionality. Sections 1, 6 and 9 continue to govern every new task.

During Phase 0, permitted work was limited to:

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
