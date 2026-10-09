# CI execution and troubleshooting

The workflow in [ci.yml](../.github/workflows/ci.yml) runs for every PR targeting
`main`, every push to `main`, manual dispatch and Mondays at **04:23 UTC**.
Schedules run the default branch and become available after the workflow is
integrated; draft PRs still run PR checks. No `pull_request_target`, path-based
skips, secrets, publishing or automatic merges are used.

## Reproducing the existing toolchain

The unchanged single `:app` module uses application ID `com.abdev.partituraspdf`,
minSdk 26, compile/target SDK 37, AGP 9.4.1, Gradle Wrapper 9.8.0,
Compose compiler plugin 2.2.10 and Compose BOM 2026.02.01. Java source/target is
11; the **Gradle daemon runs on JDK 25**, a separate requirement. AGP 9 provides
built-in Kotlin; no Kotlin Android plugin is added. No application dependency
versions or Gradle configuration change in Phase 0A.

Android jobs use Ubuntu 24.04 hosted runners. The shared setup action provisions
Temurin JDK 25, validates the existing wrapper through Gradle's maintained action,
and installs command-line tools 19.0, `platforms;android-37.0`,
`build-tools;37.0.0` and platform-tools. The SDK repository names include `.0`
for API 37; the application compileSdk remains 37. SDK license acceptance occurs
on the ephemeral runner. The preinstalled SDK manager bootstraps tools 19.0.
Toolchain versions are printed in logs; clean runners must resolve real packages.
JDK/SDK patch revisions, hosted images, emulator and Python dependencies within
the existing declared ranges are not byte-for-byte locked. A green clean CI run
is required before claiming hosted reproducibility; local cached build success
alone does not prove it.

CI actions are pinned to reviewed commit SHAs of checkout v5, setup-java v5,
setup-python v6, upload-artifact v4, Gradle setup v5 and emulator-runner v2.
Official actions provide checkout/runtime/report operations; Gradle's action
provides wrapper validation/cache handling; ReactiveCircus's action is needed for
real emulator lifecycle. Review provenance, input changes and release notes in a
separate approved infrastructure update before changing pins. These are CI-only
actions, not Android dependencies. Python 3.12 and its standard library run the
new validators; PDF dependencies come from the unchanged fixture requirements.
GitHub's actions and Gradle's setup action retain their MIT licenses;
emulator-runner retains Apache-2.0. They execute only in CI and are not packaged
with the application. Root LICENSE and separately licensed CC0 fixtures remain
unchanged; general code-license suitability is still an owner/legal review risk.

## Gates and evidence

The seven exact required check names and Ruleset instructions are in
[CHANGE_CONTROL.md](CHANGE_CONTROL.md). The six baseline checks feed
`quality-gate`, which runs with `always()` and fails on failed, missing, skipped
or cancelled mandatory dependencies. A cancelled workflow never provides a
passing required gate. Scheduled/manual compatibility jobs feed the gate only
when scheduled or requested; their omission from ordinary PR runs is deliberate.
Build/lint/unit/instrumentation are independent jobs so one failure does not hide
others. No `continue-on-error` masks a required failure.

API 26 uses a hardware-accelerated `google_apis` x86_64 emulator. The runner must
have `/dev/kvm`; absence fails instead of substituting test-source compilation.
The script verifies the device API, runs connected tests, and verifies the
existing `ExampleInstrumentedTest.useAppContext` appears in successful JUnit XML.
The JVM job similarly requires the retained existing starter test to execute.
Real emulator success remains unverified until GitHub runs this workflow.

Weekly runs execute API 27 and API 36. Before phase acceptance, the owner uses
**Actions → CI → Run workflow**, selects the final development revision and
enables **compatibility**. Record both matrix results and the exact SHA in the
PR/phase evidence. If the workflow is not yet on `main`, the manual UI may not
offer dispatch; use the PR run for initial verification and record the limitation.

Failure reports include Gradle problem/manifest logs, Lint reports, JVM XML/HTML
and instrumentation XML/HTML, retained for 14 days. The job log remains available
when setup fails before reports exist. Missing artifacts never count as passing
test evidence. Only generated diagnostics are uploaded; APKs, releases, PDF
fixtures and private documents are not uploaded. Permissions are `contents: read`,
checkout does not retain credentials, PR Gradle caches are read-only, dependency
submission/Build Scan publication and PR comments are disabled.

## Local commands

Provide JDK 25 and a compatible SDK through environment variables or ignored
`local.properties`, then run:

```bash
bash ./gradlew --version
bash ./gradlew :app:assembleDebug
bash ./gradlew :app:lintDebug
bash ./gradlew :app:testDebugUnitTest
python scripts/verify_test_results.py app/build/test-results/testDebugUnitTest \
  --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
# With a running API 26 emulator/device:
bash ./gradlew :app:connectedDebugAndroidTest
python scripts/verify_test_results.py app/build/outputs/androidTest-results/connected \
  --required-test com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext
python -m unittest discover -s scripts/tests -v
python scripts/validate_localization.py
python -m pip install -r test-fixtures/requirements-fixtures.txt
python test-fixtures/scripts/verify_fixtures.py
```

For workflow syntax review, use `actionlint .github/workflows/ci.yml`; it is a
development-only tool, not an application dependency or claim of CI execution.
See [PHASE_0A_REPORT.md](PHASE_0A_REPORT.md) for actual local/remote results.

## Known blockers and corrective actions

- Missing `values-ca/strings.xml` and `values-es/strings.xml` cause localization
  and aggregate-gate failure. Application resource corrections belong to Phase
  0B. The authorized split creates an integration sequencing blocker; no check
  exemption or failing-check bypass is permitted. The owner must resolve it
  through explicit change control before PR 1 can be merge-ready.
- The Unix wrapper is non-executable; Bash runs the preserved wrapper. A tracked
  permission correction is deferred to the separately approved foundation PR.
- SDK/JDK/artifact download or proxy failures: record the exact endpoint/error,
  restore the supported environment access, and retry. Never change project
  versions or bypass TLS/network controls just to pass.
- Emulator/KVM unavailability, system-image incompatibility or test discovery
  failure: retain logs and XML, diagnose the real runner, leave acceptance
  unresolved. Do not replace execution with an echo or successful placeholder.
- Hosted Actions permissions, first-run approval, repository plan/protection
  availability or workflow billing constraints require owner action. No agents
  change protection settings or authorize integration.
