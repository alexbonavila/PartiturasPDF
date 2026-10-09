## Phase, task and approval

Phase/task ID:
Owner-approved requirements and approval reference:
Acceptance criteria and evidence mapping:

## Scope and implementation

Problem and resulting behavior:
Files/components created, modified and removed (exact paths):
Explicit excluded scope; confirm no unauthorized product functionality:
New/modified dependencies, versions, reasons, API 26 support and licenses
(or `None`):
Android compatibility impact: min/target/compile SDK, application identity,
source/target Java and daemon JDK; document any toolchain change:

## Test evidence at the latest revision

Commit SHA and current base:

| Exact command/check | Local or CI platform, API/device, toolchain | Result (PASS/FAIL/NOT RUN/BLOCKED/NOT APPLICABLE) | Evidence/failure |
| --- | --- | --- | --- |
| Debug build | | | |
| Lint | | | |
| JVM tests (executed count) | | | |
| API 26 instrumentation (executed count) | | | |
| Localization and infrastructure validators | | | |
| PDF fixture verifier | | | |
| Quality gate | | | |
| Feature/regression tests and manual acceptance | | | |

Coverage: affected logic, tool/version, line % / branch %, exclusions and report
link; explain NOT APPLICABLE. Critical PDF scenarios: defined/tested counts and
scenario IDs. Bug fix regression: original failing revision and test ID.
API 27/recent API and physical Android 8.1 evidence when applicable:

## Localization and visual review

- [ ] Every new/changed user-facing resource and accessibility description exists
      with identical names in English, Catalan and Spanish (attach diff/evidence).
- [ ] Argument/plural validation passed at the SHA above (or record a blocker).
- [ ] A human reviewed translation quality and relevant truncation/font scales;
      identify reviewer/evidence or explain NOT APPLICABLE for unchanged UI.

Visual changes: attach light/dark screenshots, relevant portrait/landscape and
language variants, device/API and accessibility review; otherwise state no visual
changes. Do not include private documents.

## PDF integrity and security

Fixture count/verifier result; confirm no fixture/hash/expected-result changes:
Original-document survival, output structure/annotation persistence and temporary
copy evidence when affected; explain NOT APPLICABLE otherwise:
Production APK fixture exclusion evidence when asset/source sets change:
Security review performed, dependency/license findings and secret-check scope:
Known issues, exact blockers, limitations and remaining acceptance actions:

## Final human approval (owner completes)

- [ ] Approved scope matches the final diff and all acceptance evidence is linked.
- [ ] The seven mandatory checks passed on the latest revision/current base;
      applicable coverage, compatibility and manual acceptance are complete.
- [ ] Blocking conversations and P0/P1/current-phase acceptance defects are resolved.
- [ ] I explicitly authorize integration; only I will perform the merge.

Owner decision/date and any rejection or unresolved blocker:
