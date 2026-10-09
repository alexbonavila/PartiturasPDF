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
Phase 0A demonstrated real API 26 smoke-test execution. Phase 0B additionally
executes foundation Compose/localization/contrast tests; its PR records results
for the latest revision rather than relying on earlier runs.

Weekly runs execute API 27 and API 36. Before phase acceptance, the owner uses
**Actions → CI → Run workflow**, selects the final development revision and
enables **compatibility**. Record both matrix results and the exact SHA in the
PR/phase evidence. If the workflow is not yet on `main`, the manual UI may not
offer dispatch; use the PR run for initial verification and record the limitation.

### API 27 system-image availability

Compatibility keeps `google_apis` for both APIs, using **x86 for API 27** and
**x86_64 for API 36**. API 26 remains `google_apis` x86_64. The architecture
expression changes only API 27; job names, triggers, device API assertions,
connected tests, XML smoke-test verification and aggregate-gate requirements
are unchanged.

[Run 37969432746](https://github.com/alexbonavila/PartiturasPDF/actions/runs/37969432746)
failed before API 27 boot because `system-images;android-27;google_apis;x86_64`
does not exist in the official repository. No API 27 test ran. Its API 26 and
API 36 instrumentation passed, but the requested compatibility failure correctly
failed `quality-gate`.

Official metadata checked on 2026-10-09 lists:

| API 27 target | x86 | x86_64 |
| --- | --- | --- |
| `google_apis` | Available, revision 11 | Not listed |
| `default` | Available, revision 1 | Available, revision 1 |

Sources: Google's
[Google APIs image index](https://dl.google.com/android/repository/sys-img/google_apis/sys-img2-3.xml)
and [default image index](https://dl.google.com/android/repository/sys-img/android/sys-img2-3.xml).
The Google APIs version-4 index agrees. The selected stable package is
`system-images;android-27;google_apis;x86`; its
[x86-27_r11.zip](https://dl.google.com/android/repository/sys-img/google_apis/x86-27_r11.zip)
archive HEAD request returned HTTP 200 and Content-Length 807,298,593. Selecting
x86 retains the existing target instead of changing the installed Android services. Package
availability is provisioning evidence, **not a passing instrumentation result**.

The correction was merged in [PR #3](https://github.com/alexbonavila/PartiturasPDF/pull/3).
The owner then ran compatibility on `main`; the earlier API 27 execution blocker
is resolved. Phase 0 is merged and accepted by the owner. Its final compatibility
evidence is [run 37972414539](https://github.com/alexbonavila/PartiturasPDF/actions/runs/37972414539),
at `e87fdf9f82d79ce6d0ee0b8d65a7dbe452674a37` on 2026-10-09:

| Check | Actual result and evidence |
| --- | --- |
| API 26, `google_apis` x86_64 | PASS: 4 executed tests, no failures/skips; [job 113962134021](https://github.com/alexbonavila/PartiturasPDF/actions/runs/37972414539/job/113962134021) |
| API 27, `google_apis` x86 | PASS: 4 executed tests, no failures/skips; [job 113962133941](https://github.com/alexbonavila/PartiturasPDF/actions/runs/37972414539/job/113962133941) |
| API 36, `google_apis` x86_64 | PASS: 4 executed tests, no failures/skips; [job 113962133780](https://github.com/alexbonavila/PartiturasPDF/actions/runs/37972414539/job/113962133780) |
| Aggregate quality gate | PASS, including required compatibility; [job 113963783178](https://github.com/alexbonavila/PartiturasPDF/actions/runs/37972414539/job/113963783178) |

Build, Lint, JVM tests, localization and all 15 fixture checks also passed in
that run. These are actual emulator results, not image-availability or
compilation-only claims. Historical failed runs remain evidence of earlier
failures rather than the current Phase 0 status. Future phase-close runs must
still record the exact revision, API 27/36 results and passing JUnit XML with
`ExampleInstrumentedTest.useAppContext`; all existing gates remain mandatory.

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

- Phase 0B adds the previously missing Catalan/Spanish resources and corrects
  the Unix wrapper executable permission. The existing validators and gate are
  unchanged; new missing translations still fail CI. See TECH_STACK.md and the
  Phase 0B PR for current execution evidence; Phase 0A's report is historical.
- SDK/JDK/artifact download or proxy failures: record the exact endpoint/error,
  restore the supported environment access, and retry. Never change project
  versions or bypass TLS/network controls just to pass.
- Emulator/KVM unavailability, system-image incompatibility or test discovery
  failure: retain logs and XML, diagnose the real runner, leave acceptance
  unresolved. Do not replace execution with an echo or successful placeholder.
- Hosted Actions permissions, first-run approval, repository plan/protection
  availability or workflow billing constraints require owner action. No agents
  change protection settings or authorize integration.
