# Definition of Done

Applies to tasks, PRs and development phases together with
[TESTING_POLICY.md](TESTING_POLICY.md), [CHANGE_CONTROL.md](CHANGE_CONTROL.md),
[AGENTS.md](../AGENTS.md) and the [specification](PROJECT_SPECIFICATION.md).
Submitting a PR is delivery for review; it is not integration or phase acceptance.

## Automated acceptance

- All approved requirements have evidence; no unauthorized functionality or
  architectural/dependency expansion is present.
- The app compiles with the original wrapper/toolchain and `minSdk = 26`;
  identity and single-module constraints remain intact.
- Every mandatory CI check passes at the latest PR revision against current
  `main`; required API 26 instrumentation actually executes.
- Applicable feature tests pass; bug fixes include original-failure regression
  tests. Existing tests remain enabled and no blocking regressions remain.
- New business logic meets 90% line / 80% branch coverage with scoped reports;
  all defined critical PDF edit/save scenarios have explicit tests. Mark coverage
  NOT APPLICABLE where no business logic exists, with justification.
- English, Catalan and Spanish resources and argument contracts are complete;
  immutable PDF fixtures pass the authoritative verifier and remain outside
  production assets. Exceptions cannot be inferred from the phase split.
- No unresolved critical/high security findings or leaked secrets, credentials,
  keystores, private documents or local device/SDK configuration are in the diff.
  Record the security review scope and limitations; build success is not a
  comprehensive security audit.

## Manual verification

- Review the complete diff for approved scope, licenses, dependency risks and
  implementation/documentation consistency. Preserve root LICENSE and fixture CC0.
- Update documentation and record test commands, environment, revision and actual
  results. All blocked, failed or unexecuted checks are explicit unresolved items.
- For UI changes, review accessibility, translations, light/dark appearances,
  relevant portrait/landscape behavior and screenshots on supported devices.
- At phase close, obtain latest-revision API 27 and recent-API compatibility
  evidence. For functional phases, perform physical Android 8.1 tablet acceptance
  and relevant performance/security/error-handling scenarios.
- Resolve all P0/P1 defects, affected-phase acceptance defects at any severity,
  and blocking review conversations. Record owner triage for nonblocking P3 items.

No Phase 0A application/UI behavior changes are authorized. UI/manual feature and
business coverage criteria can be NOT APPLICABLE for that PR; mandatory baseline
CI and localization completeness cannot be declared not applicable.

## Final human approval

The human owner reviews the requirement-to-evidence mapping, latest CI results,
manual acceptance and known limitations, then explicitly authorizes integration.
Only the owner performs the merge. An agent's report, submitted PR, passing build
or checklist tick does not authorize merging or the next phase.

A task is Done only when its applicable criteria and human approval are recorded.
A PR is merge-ready only when all applicable automated/manual criteria and review
requirements are met. A phase is complete only after all its accepted tasks,
compatibility/device evidence and final owner acceptance are satisfied. PR 2
must wait for explicit owner approval and merge of PR 1 and a fresh current-main
checkout. Failed/missing gates leave Phase 0 acceptance unresolved.
