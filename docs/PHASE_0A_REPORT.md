# Phase 0A audit and verification evidence

**Date:** 2026-10-09. **Scope:** owner-authorized PR 1 only: policies, PR template,
CI/quality gates and necessary documentation. **Acceptance:** unresolved;
submitting this draft PR is not full Phase 0 acceptance.

## Repository synchronization and audit

The supplied workspace was at starter commit
`4abe014fe3afde18bedf9d78c8d8e66657b7ef43`. Default sandbox networking could not
reach the configured proxy. Running `git fetch origin` with network access
enabled succeeded, advancing `origin/main` to
`693ccf9f2503e95dbd548cc379d6edd756f1b8b8`. The working tree was clean; the
dedicated branch `chore/phase-0a-policies-quality-gates` was created directly
from that current main revision. No reset, overwritten local changes or
credential disclosure was used.

Read README, AGENTS, LICENSE, specification, fixture README/SOURCES/manifest and
the existing verifier. Inspected Gradle scripts/catalog/wrapper/daemon criteria,
manifest, starter Activity/theme/resources, backup rules, existing JVM and
instrumentation tests, ignore rules and GitHub configuration.

Already correct: original application ID, single `:app` module, minSdk 26,
Kotlin DSL, AGP built-in Kotlin setup, Material 3 dependencies, preserved wrapper
and JDK criteria, both starter tests, 15 immutable synthetic PDFs outside app
source sets, structural/rendering verifier, manifest expectations, CC0 fixture
declaration and restrictive root license.

Missing before Phase 0A: testing/change-control/Done policies, PR template,
GitHub Actions, localization validation and aggregate quality gate. Still
deferred to Phase 0B: brand theme instead of generated purple/dynamic palette,
localized starter greeting instead of hardcoded text, Catalan/Spanish resources,
tracked wrapper executable permission, remaining foundation technical
documentation, editor configuration and broader repository-hygiene review.
Backup rules must be revisited before any sensitive feature data exists.
No production fixture asset wiring or PDF product tests are added.

## Exact repository changes

Created:

- `.github/workflows/ci.yml`
- `.github/actions/setup-android/action.yml`
- `.github/PULL_REQUEST_TEMPLATE.md`
- `docs/TESTING_POLICY.md`
- `docs/CHANGE_CONTROL.md`
- `docs/DEFINITION_OF_DONE.md`
- `docs/CI.md`
- `docs/PHASE_0A_REPORT.md`
- `scripts/validate_localization.py`
- `scripts/verify_test_results.py`
- `scripts/quality_gate.py`
- `scripts/tests/test_quality_infrastructure.py`

Modified: `README.md` (policy/CI links and truthful dataset/status),
`docs/PROJECT_SPECIFICATION.md` (committed fixture status and policy links/split).
Removed: **none**. AGENTS rules, LICENSE, app code/resources/tests, Gradle
configuration and the entire fixture collection/verifier are unchanged.

## Final observed build configuration

| Setting | Preserved value |
| --- | --- |
| Application ID / namespace | `com.abdev.partituraspdf` |
| Modules / build scripts | single `:app` / Kotlin DSL |
| Minimum / target / compile SDK | 26 / 37 / 37 |
| Android Gradle Plugin | 9.4.1 |
| Gradle Wrapper | 9.8.0 |
| Compose compiler plugin / BOM | 2.2.10 / 2026.02.01 |
| Java source / target | 11 / 11 |
| Gradle daemon JVM criterion | 25, any vendor |

No versions or application dependencies were changed. Local execution used
Oracle OpenJDK 25.0.4.1, Linux x86_64, SDK platform `android-37.0` revision 2,
build tools 37.0.0 and Python 3.12. CI provisions Temurin 25 with the same daemon
major requirement. Gradle's displayed embedded Kotlin 2.4.10 is not the unchanged
Compose compiler-plugin version. Existing dependencies remain as declared in
`gradle/libs.versions.toml`; no Room, DataStore, PDFBox or Drive library is added.

## Actual local commands and results

All commands below ran in the managed Linux workspace `/workspace/PartiturasPDF`.
Android commands used `source /workspace/setup/activate.sh` to select the supplied
JDK/SDK and proxy-aware Gradle user home. No local SDK paths were committed.

| Exact command | Result | Observed evidence / limitation |
| --- | --- | --- |
| `git fetch origin` with network access enabled | PASS | Current main fetched before edits |
| `bash ./gradlew --version` | PASS | Wrapper 9.8.0; launcher 25.0.4.1; daemon criterion 25 |
| `./gradlew :app:assembleDebug` | BLOCKED | Shell exit 126: wrapper not executable |
| `./gradlew :app:lintDebug` | BLOCKED | Shell exit 126: wrapper not executable |
| `./gradlew :app:testDebugUnitTest` | BLOCKED | Shell exit 126: wrapper not executable |
| `./gradlew :app:connectedDebugAndroidTest` | BLOCKED | Shell exit 126: wrapper not executable |
| `bash ./gradlew :app:assembleDebug` | PASS | Gradle build successful; initial run used up-to-date tasks |
| `bash ./gradlew :app:lintDebug` | PASS | Initial successful task execution; no errors/fatals |
| `bash ./gradlew :app:testDebugUnitTest` | PASS | Initial successful task; reused existing results, followed by fresh run below |
| `bash ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --rerun-tasks --stacktrace` | PASS | Fresh compilation, Lint and JVM execution: 51 tasks executed; 1 JVM test passed |
| `python scripts/verify_test_results.py app/build/test-results/testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect` | PASS | 1 executed test, no failures/errors/skips |
| `bash ./gradlew :app:connectedDebugAndroidTest` | BLOCKED | Nonzero exit at ADB bridge setup; test APK compiled, tests did not execute |
| `python -m pip install -r test-fixtures/requirements-fixtures.txt` | PASS | All four declared requirements already satisfied |
| `python test-fixtures/scripts/verify_fixtures.py` | PASS | All 15 original PDFs: SHA-256, byte sizes, structure and sample rendering; expected corruption detected |
| `python -m unittest discover -s scripts/tests -v` | PASS | 18 infrastructure tests, including failure/missing/skip rejection cases |
| `python scripts/validate_localization.py` | FAIL | Missing `values-ca/strings.xml` and `values-es/strings.xml`; intentionally not repaired in PR 1 |
| `actionlint .github/workflows/ci.yml` | PASS | actionlint 1.7.7, syntax/expression validation; not hosted execution |
| `git diff --check` | PASS | No whitespace errors |

Local instrumentation's exact error: ADB aborts trying to create
`/home/agent/.android` on a read-only filesystem, causing
`Could not create ADB Bridge`. Writable Android-specific environment selectors
did not correct that ADB behavior. The SDK has no emulator installation and the
host has no `/dev/kvm`; no physical device is available. API 26/27/36 execution
and physical Android 8.1 acceptance are **NOT RUN / BLOCKED**, never PASS.

Fresh Lint reports **0 errors, 0 fatals, 12 existing warnings**: redundant label,
four dependency-version suggestions and seven unused generated colors. No
findings were suppressed. Gradle also warns of deprecated behavior incompatible
with Gradle 10; no upgrade or suppression is performed. CLI tools 19 report a
repository XML-version warning when listing the installed newer SDK; installed
API 37 is correctly reported and the local build succeeds. Hosted clean-package
installation still requires independent evidence.

Business-logic/critical PDF scenario coverage: **NOT APPLICABLE**, as no business
logic or PDF operations exist or are introduced. No new app tests or artificial
coverage numbers. Screenshot/manual UI acceptance: **NOT APPLICABLE to unchanged
UI in PR 1**; existing Phase 0B localization/theming requirements remain unmet.

## Quality and fixture evidence

The new CI has seven stable mandatory contexts and scheduled/requested API 27/36
compatibility jobs. Jobs preserve diagnostic/test reports, use read-only contents
permission, no ordinary CI secrets, pinned maintained actions and no APK/release
publication. Validators reject missing translations, incompatible formatting,
missing or skipped test XML and nonsuccessful mandatory jobs. Infrastructure
tests use temporary XML/resources and never alter application files or PDFs.

The fixture verifier passed all 15 files. A separate read-only manifest-based
SHA-256 comparison also passed for all 15. No fixture, checksum, manifest,
expected property, generation/verifier script or CC0 declaration changed.
Production source sets are unchanged and do not reference `test-fixtures`.
Future test-only access and temporary mutation copies are documented in the
testing policy, not wired into production or duplicated now.

## GitHub and remaining acceptance

Hosted checks are **NOT RUN at the time this evidence document was authored**;
the PR's Checks tab and delivery report record later runs and exact revisions.
Static validation/local execution is not a substitute for actual hosted emulator
and clean toolchain validation. Ruleset activation/status is not assumed or
modified; the owner must follow [CHANGE_CONTROL.md](CHANGE_CONTROL.md) and verify
the settings on GitHub. No repository protection change is authorized by this PR.

Current application localization is English app-name only; the visible greeting
remains hardcoded. Catalan/Spanish and branded theme corrections are reserved for
Phase 0B. Therefore `localization` and `quality-gate` cannot pass this unchanged
app baseline. PR 1 must remain blocked for integration under the unchanged
mandatory policy. The owner must explicitly resolve the split's sequencing
conflict through change control; no inferred exception or bypass is introduced.

Outstanding risks: actual hosted SDK/emulator compatibility, existing Gradle
deprecations/warnings, missing translations, unavailable local device evidence,
GitHub protection availability/activation, and existing CC BY-NC-ND code-license
suitability for future distribution. New CI actions are test-only MIT/Apache-2.0
components; no new runtime libraries are installed. There is no claim of a
comprehensive vulnerability/security audit.

## Delivery and scope

Branch: `chore/phase-0a-policies-quality-gates`, based on synchronized main
`693ccf9`. Deliver as a draft PR for human review with blocked acceptance clearly
identified. No product functionality, theme, localization resources, navigation,
PDF operations, persistence or Drive code was implemented. Nothing was pushed to
or merged into `main`. Stop after PR 1 delivery; do not start Phase 0B until the
owner explicitly approves and merges PR 1 and work resumes from updated main.
