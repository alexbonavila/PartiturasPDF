# Change control and human approval

This policy implements [AGENTS.md](../AGENTS.md) and the
[project specification](PROJECT_SPECIFICATION.md).

## Required workflow

1. Obtain explicit owner approval for requirements, acceptance criteria and
   excluded scope before implementation. A roadmap is not authorization.
2. Synchronize with current `main` without overwriting local changes, then use
   a dedicated development branch for the approved task.
3. Implement only approved scope and submit all changes through a PR describing
   requirements, files, behavior, dependencies, compatibility and risks.
4. Record relevant automated and manual evidence using the testing policy.
   Every mandatory check must pass on the latest PR revision and current base.
5. Resolve blocking review conversations and acceptance defects. Re-run affected
   checks after changes; evidence from an older SHA cannot authorize integration.
6. Only the human owner can approve integration and perform the final merge.
   Automated agents never merge their changes, enable auto-merge, push to `main`,
   waive failures or claim phase approval.

Changes to testing policy, AGENTS.md, licensing, repository protection or quality
gates require an **explicitly approved standalone PR**. Never weaken policy to
make an implementation pass. Changes outside the approved task require new
explicit approval.

## Authorized Phase 0 split

- **Phase 0A / PR 1:** policies, PR template, CI/quality-gate infrastructure and
  strictly necessary documentation. Application code, theme and resources stay
  unchanged.
- **Phase 0B / PR 2:** theme, en/ca/es localization foundation, remaining build
  verification and foundation documentation. It starts only after the owner
  explicitly approves and merges PR 1, from the updated `main`.

This split does not exempt PR 1 from mandatory gates. Its existing untranslated
starter resources can make `localization` and `quality-gate` fail. It must remain
blocked for integration until the owner resolves the sequencing conflict through
explicit approved change control. An agent cannot add Phase 0B resources, disable
the check, create an exception or merge PR 1 to get past this condition.

## Manual GitHub configuration for main

These are expected settings, **not a claim that protection is active**. Do not
change them without explicit owner authorization. The owner should configure:

1. Repository **Settings → General → Pull Requests**: allow squash merging;
   disable merge commits and rebase merges if squash-only history is desired.
2. **Settings → Rules → Rulesets → New ruleset → New branch ruleset**. Name it
   `main-quality-control`, set enforcement to **Active**, target branch pattern
   `main`, and leave the bypass list empty (including administrators/apps).
3. Enable **Restrict deletions**, **Block force pushes** and **Require linear
   history**. Enable **Require a pull request before merging**, which blocks
   ordinary direct pushes to `main` without a PR.
4. Set **Required approvals = 0** for this single-human-maintainer repository.
   Enable **Require conversation resolution before merging**. Owner-authored
   PRs cannot receive the owner's GitHub review approval; owner review and final
   merge remain explicit policy requirements. Do not impose a second reviewer.
5. Enable **Require status checks to pass** and **Require branches to be up to
   date before merging** (strict current-base validation). Add each exact check
   below, choosing the GitHub Actions app as source. Obtain names from the PR's
   actual Checks tab after CI has run; do not select another same-named provider.
6. Do not permit bypassing failed checks or enable a merge queue unless an
   approved workflow change adds `merge_group` support. Save and verify the
   active ruleset. Keep write/admin roles limited to authorized people and apps;
   the owner remains responsible for final integration.

| Required check context | Workflow job ID |
| --- | --- |
| `build` | `build` |
| `lint` | `lint` |
| `unit-tests` | `unit-tests` |
| `instrumented-api26` | `instrumented-api26` |
| `localization` | `localization` |
| `pdf-fixtures` | `pdf-fixtures` |
| `quality-gate` | `quality-gate` |

The workflow is named `CI`; the Actions UI may display `CI / build`, etc.
The status contexts are the job names above. Scheduled/manual compatibility
contexts `compatibility-api27` and `compatibility-api36` are not per-PR required
contexts; when requested or scheduled they are mandatory inputs to that run's
`quality-gate`. Their latest passing evidence is required at phase close.

If GitHub does not offer these rules on the repository's current plan, record
the limitation and obtain owner resolution; documentation is not enforcement.
Do not claim protection is enabled without verifying saved settings. Before
merging, the owner checks the latest head/base, seven successful mandatory checks,
resolved conversations, manual acceptance and absence of blockers.

## Severity and blockers

| Severity | Definition | Approval impact |
| --- | --- | --- |
| P0 — Critical | Data loss, PDF corruption, critical security defect | Always blocks approval; contain and preserve recovery evidence |
| P1 — High | Broken core functionality or serious Android compatibility problem | Always blocks approval |
| P2 — Medium | Defect affecting requirements of the current phase | Blocks the affected acceptance criterion/phase |
| P3 — Low | Nonblocking improvement requiring explicit owner triage | Track owner decision; no automatic scope expansion |

Any defect preventing an approved acceptance criterion blocks the affected phase
regardless of its severity label. Missing tests or manual evidence cannot become
P3 merely to approve a phase. Log failures, reproduction, affected revisions,
regression tests and corrective actions; no secret/private-document evidence.
