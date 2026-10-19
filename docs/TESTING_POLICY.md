# Mandatory testing policy

This policy applies to every approved task, PR and future development phase.
It supplements [AGENTS.md](../AGENTS.md) and the
[project specification](PROJECT_SPECIFICATION.md); it does not authorize features.
Tests must remain enabled. Never weaken assertions, remove meaningful tests,
suppress unexplained Lint errors or change fixture expectations to obtain a pass.

## Baseline checks for every PR

| Check name | Required execution |
| --- | --- |
| `build` | `bash ./gradlew :app:assembleDebug` |
| `lint` | `bash ./gradlew :app:lintDebug`; no new Lint errors |
| `unit-tests` | `bash ./gradlew :app:testDebugUnitTest`; executed, passing JUnit XML evidence |
| `instrumented-api26` | `bash ./gradlew :app:connectedDebugAndroidTest` on a real API 26 emulator/device; executed smoke test evidence |
| `localization` | Infrastructure validator tests and `python scripts/validate_localization.py` |
| `pdf-fixtures` | Install `test-fixtures/requirements-fixtures.txt`, then run the existing `python test-fixtures/scripts/verify_fixtures.py` |
| `quality-gate` | All six mandatory job results must be `success` |

Use `./gradlew` once its executable permission is corrected in an approved
foundation change. The initial Git-tracked wrapper has mode `100644`, so CI
invokes the unchanged wrapper with Bash. Windows uses `gradlew.bat`.
No path filters exempt documentation-only PRs. Compilation of test sources
does not count as execution. XML evidence must contain at least one actual
test and no failures, errors, disabled or skipped tests. The existing
`ExampleInstrumentedTest.useAppContext` must execute; it verifies application
identity only and is not evidence of feature or UI correctness.

Every feature requires applicable unit, integration, Compose UI, regression,
PDF structural integrity, error-handling and security tests. Requirement IDs
must map to test cases and manual evidence; explain any category that is not
applicable. Every bug fix includes a regression test demonstrating the original
failure against the earlier implementation.

## Coverage and critical scenarios

For new, testable business logic, enforce at least **90% line coverage and 80%
branch coverage** over the affected logic. Select and configure an
AGP-compatible coverage tool before introducing that logic; record the tool,
version, scope, denominator, exclusions and report in the PR. Do not use a
project-wide average to hide an under-tested change.

Critical PDF editing/saving requires explicit tests for **100% of defined
critical scenarios**, including original survival, readable output, page/content
and annotation preservation, permission denial, cancellation, interrupted writes,
disk-full errors and recovery. This is scenario coverage, not a promise of
100% code coverage. Define the scenario inventory before implementation.

Generated code and resources may be excluded only with documented justification.
No business logic exists in the starter application; business-logic coverage is
currently **NOT APPLICABLE**. Keep existing sample tests, but do not invent
arithmetic tests or application-wide metrics to increase coverage. Phase 0A's
Python tests exercise the validators' failure behavior and are infrastructure
tests, not application business-logic coverage.

## Compatibility and manual verification

- API 26: instrumentation on every PR. `minSdk = 26` is non-negotiable.
- API 27: weekly scheduled instrumentation and a manual workflow run with
  compatibility enabled against the phase's latest revision before phase close.
- Physical Android 8.1 tablet: manual acceptance before approving a functional
  phase. Record model, API, application revision, actions, results and issues.
- Recent Android: weekly API 36 emulator tests initially; update the chosen
  recent API through approved infrastructure change as runner support evolves.
- UI changes: portrait and landscape when affected, light and dark themes,
  Spanish and completed translations, large font scales, contrast, touch targets and
  accessibility semantics. Record screenshots and TalkBack/device observations
  where relevant. Automated checks do not replace these reviews.

The initial CI executes the existing identity smoke test on API 26/27/36.
Future feature tests must extend its scope. No physical-device or application
launch acceptance is implied by that test.

## Localization validation

### Complete Spanish-default localization (pre-Phase-I baseline)

As delivered in the merged pre-I F/G/H closeout (PR #21), the Android default
`res/values/` resources are Spanish. `values-es`, `values-ca`, and `values-en`
contain complete matching Spanish, Catalan, and English translations for the
currently implemented application and debug SAF acceptance UI. This is resource
delivery, not evidence that Phase I's production Activity locale handling exists.

All new or changed user-visible and accessibility text must be resource-backed
and complete in all four directories before the change is accepted. Preserve
resource name/type parity, format argument contracts and indices, plurals,
string arrays, markup, semantics and readable translated copy. Default Spanish
and `values-es` must match. No hardcoded user-visible strings, placeholders
masquerading as translations, missing keys, blanket translation suppressions,
or unreviewed format changes are permitted.

`*_spanish_first.xml` staging was an approved temporary mechanism used during
earlier F/G/H development. It is historical translation debt, not a permitted
exception for newly delivered resources: the current production localization
validator requires complete es/ca/en bundles and rejects remaining staging.
Historical staging-validator regression tests may remain, but cannot exempt
production resources from complete-delivery checks. Do not reduce validator
strictness, remove negative tests or change CI gates to bypass missing resources.

Phase I owns automatic runtime language application using the existing
`DeviceLocalePolicy`: the device's primary supported language (es, ca, en)
is used; unsupported primary languages fall back to Spanish on API 26+.
There is no manual language selector, and legacy persisted
`LanguageSelection` data must not become an active override. Verify real
Activity recreation and device-language changes separately; resource-context
tests and compiled bundles do not establish production navigation or runtime
locale behavior.

Run the localization validator and its negative/control tests on each PR.
Preserve human review of Spanish, Catalan and English wording, accessibility
semantics, formatting, layouts, TalkBack and relevant API 26 behavior. Treat
missing linguistic/manual/device evidence as pending rather than PASS.
Technical documentation, commit messages and agent instructions remain in
English. All non-localization CI, code coverage, PDF safety, compatibility and
physical-device acceptance requirements remain unchanged.

## Permanent PDF dataset

`test-fixtures/manifest.json` is authoritative for the 15 immutable synthetic
PDFs. Preserve their separate CC0 declaration and the repository root license.
The existing verifier checks the exact file collection, SHA-256, byte size,
page count, geometry, rotations, text, annotations, protection, expected corrupt
input failure, and sample rendering. Do not substitute hash-only validation,
run the generator in CI, or alter expectations to pass a test.

Future JVM tests should receive an absolute path derived from the Gradle root
project directory through a test-only system property. Future instrumentation
tests should expose the same root `test-fixtures/` collection through the
`androidTest` asset source set and read it from the instrumentation context
(the test APK), not the target application context. Configure these paths only
when approved tests need them; do not commit a second dataset. Copy inputs to
unique temporary directories before every mutation and clean those copies.
Never add fixture paths to production `main`/`release` assets or resources.
Review source sets and inspect release APK ZIP entries before approving any
future fixture wiring; fail if test PDFs appear there.

## Future performance policy

Before reader/editor/annotation implementation, approve numerical budgets and
reproducible scenarios for first-page latency, page-turn latency, peak/steady
memory, bitmap/cache bounds, cancellation and resource cleanup on the physical
Android 8.1 tablet and a recent device. Record median and tail latency, cold/warm
runs, device/OS, build type, fixture IDs and measurement method. Test the 100-page
fixture, raster-heavy pages, mixed dimensions, rapid navigation and repeated
open/close/edit cycles. Large 100 MB+ stress documents require a separately
documented repeatable workflow, not new committed private PDFs or generator runs
in ordinary CI. Agreed budget regressions block the affected phase; avoid arbitrary
wall-clock thresholds on shared hosted runners.

## Evidence and unavailable tests

Record the exact command, commit, environment/toolchain, API/device, local or CI
execution, date, result and relevant failures. Use **PASS**, **FAIL**, **NOT RUN**,
**BLOCKED**, or **NOT APPLICABLE**. A blocked command may return a nonzero exit;
record that exit and the environment blocker, never call it passed. Missing SDK,
JDK, network, emulator or CI authorization leaves dependent acceptance unresolved.
CI logs and retained test reports are useful evidence; no secrets or private
documents may be uploaded. Before phase close, run the manual compatibility
workflow at the final revision and record required human acceptance.

Policy/gate changes require an explicitly approved standalone PR. Failed or
missing mandatory checks cannot be bypassed by an agent or declared accepted.
