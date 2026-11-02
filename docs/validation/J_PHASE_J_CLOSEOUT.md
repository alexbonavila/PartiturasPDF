# Phase J technical consolidation — NOT final acceptance

2026-11-02. Repository `alexbonavila/PartiturasPDF`, branch
`test/mvp-stabilization-j`, audited revision
`6d0bbe412237065ce4759d5f11d143250e9608bf` (clean before documentation edits).
Despite this filename, this is an in-progress consolidation report, not formal closeout.
**NOT MERGE-READY / NOT FORMALLY ACCEPTED. J14 IN PROGRESS; J15 PENDING.**

## Integration observations

Read-only remote inspection on 2026-11-02 confirms Phase J HEAD at the audited
revision and `main` at `37f51baf8b34b069a0c83e93b5780a6c9f676c16`.
The merge base equals that main revision: 0 main-only / 48 Phase-J-only commits.
The cumulative change includes 66 files, tooling, Android/JVM tests, debug coverage
export, the production worker subclassability change and AGP 9.5.0—not only recent
documentation. Complete review of that cumulative diff remains necessary.

The M7-A audit's GitHub PR, current-head check-runs and branch-protection requests
returned Forbidden. PR existence, current CI conclusions and enforced protection
settings are therefore UNKNOWN, not absent or passing. The checked-in CI defines
seven mandatory jobs; feature pushes do not trigger it (push is main-only).
No new workflow was dispatched. These observations must be refreshed for the final PR.

## Requirement-to-evidence index

All execution revisions below are historical, not this documentation revision.
Independent review means the previously recorded artifact review, not a new audit here.

| Requirement | Technical scope / milestone | Best evidence and exact revision | Current boundary |
| --- | --- | --- | --- |
| J01 audit | M1 complete | [J01](J01_AUDIT.md), main `37f51baf8b34b069a0c83e93b5780a6c9f676c16`; repository inspection | Final integration/status reconciliation pending |
| J02 functional regression | M2 scoped complete | [M2](J_M2_C2B_ANDROID_API26_EVIDENCE.md), `83e0094cea8a20a90635f82b97b84610a11d2a2e`; independently reviewed 20/20 API26 incl. smoke | Not latest-head CI or external-provider acceptance |
| J03 latency | M4 measurement scope CLOSED | [M4](J_M4_BENCHMARK.md), `a782903fef1a5cc8ab3404d8c27fbc8cd45d16e3`; owner-verified six invocations | Instrumented emulator observations; no approved quantitative/physical budget |
| J04 resources/memory | M4/M5-A bounded scopes CLOSED | M4 above; [M5-A](J_M5_A_ENDURANCE.md), `43401566a7c3944c1b4faa9256ccf235e723db10`; owner-verified | PSS review flag unresolved; no retained-heap/leak-free proof |
| J05 endurance | M5-A CLOSED | Same M5-A revision, 3 cycles/9 journeys PASS | Not prolonged endurance; 100-page fixture traverses only first two pages |
| J06 recovery | M2/M5-B bounded scopes CLOSED | [M5-B](J_M5_B_RECOVERY_MATRIX.md), `d1df892e5d1f7d2c3e722b51e9617ed666ca6d0c`; supplied independent review, 8/8 PASS | Non-destructive selected scenarios only |
| J07 compatibility | M3-A scoped complete | [M3-A](J_M3_A2_ANDROID_COMPATIBILITY_EVIDENCE.md), `a9905d497979520cfabf59e038b51f0ed453a8a3`; independently reviewed 18/18 on each API26/27/36 | Latest consolidated-revision compatibility pending |
| J08 | DEFERRED TO PHASE R | Owner decision retained in plan/catalogue | Not PASS or an inferred policy waiver |
| J09 locale/offline/delivery | M3-B selected technical scopes complete | [J09](J_M3_B2A_J09_ANDROID_EVIDENCE.md), `df643959c4d6bd19f8301f972d4e00bca9636596`; independently reviewed 9/9 per API; later B2-B/C/D provenance retained in [M3](J_M3_CLOSEOUT.md) | Manual/live-transition/delivery/restoration limits remain |
| J10 persistence/restoration | M2/M5-B selected scopes CLOSED | M2/M5-B revisions above | Synthetic reconstruction is not OS death; backup/D2D remain separate |
| J11 regression/coverage | M1–M3 infrastructure/scoped evidence complete | [M3](J_M3_CLOSEOUT.md), `b2cff985ee3d9204c3d46728636afdb329d1cc22`; owner-reported 631 JVM/431 Android PASS, 17 worker snapshots | Raw final archive unavailable here; no latest-head or universal coverage claim |
| J12 correction decision | M6 CLOSED | [M6](J_M6_DEFECT_TRIAGE.md), audit `a6fdcf73f9cfbc3e362ce2e2af539f2927686bf8`, closure at audited HEAD | NO CORRECTION JUSTIFIED BY CURRENT EVIDENCE; not proof of defect absence |
| J13 optimization | M6 decision complete | Same M6 triage | NOT AUTHORIZED / DEFERRED; reproduction, target and approval required |
| J14 consolidation | M1 reporting foundation complete; M7-A IN PROGRESS | This source-based index and [ledger](J_ACCEPTANCE_LEDGER.md) | Final evidence/review integration pending |
| J15 acceptance | M7-C PENDING | DoD and owner decision process | No final Phase J acceptance |

M3's reported A1/B5/C05 gates retain exact scope: B5 623/658 lines (94.68%),
375/452 branches (82.96%); metadata 207/210 lines and 167/177 branches; preferences
35/35 lines and 12/12 branches. No thresholds/denominators changed for consolidation.
The final M3 archive is unavailable here. Earlier below-gate/unmeasured/blocked
captures remain historical outcomes, not overwritten by those later reported passes.
Historical CI #99/#101 in the [MVP record](../releases/MVP_RELEASE.md) concerns earlier
MVP revisions, not the Phase J PR. No current-revision success is claimed.

M4 and M5-A raw archives were not attached to their documentation updates; findings
are owner-verified. M5-B records supplied independent review, not an additional
Codex archive audit. Its test-specific teardown is not a separate host-cleanup campaign.
All dated FAIL/BLOCKED/NOT RUN records and distinct restoration histories remain intact.
M5-A `review_required=true` is UNRESOLVED, not a confirmed leak or resolved concern.

## Minimum M7-B verification plan — not executed or authorized here

Use the final reviewed/committed consolidation SHA and then-current main. Prefer
trustworthy equivalent CI evidence over duplicate local execution.

| Check / existing command | Required evidence and boundary |
| --- | --- |
| Authorized PR targeting main: `.github/workflows/ci.yml` | All seven jobs: build, lint, unit-tests, instrumented-api26, localization, pdf-fixtures, quality-gate; actual conclusions/XML/logs at final PR revision/base |
| Separately authorized `gh workflow run ci.yml --repo alexbonavila/PartiturasPDF --ref test/mvp-stabilization-j -f compatibility=true` | Actual final-SHA API27 and API36 jobs, independently checked; skipped is not compatibility PASS |
| Complete `git diff origin/main...HEAD` and commit inventory | Review all tooling/test/AGP/coverage/debug and production changes, licenses/privacy, owner-approved scope; resolve conversations |
| `bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug :app:testDebugUnitTest --stacktrace` | Local preflight only where CI lacks equivalent evidence; actual JVM XML and coverage finalizer, Lint/APKs. Compilation is not instrumentation execution |
| `python3 -m unittest discover -s scripts/tests -v` | Final infrastructure regression results; covered by localization CI when successful |
| `python3 scripts/validate_localization.py`; `python3 test-fixtures/scripts/verify_fixtures.py` | Complete resources and canonical hashes; prefer CI equivalents |
| Existing `python3 scripts/local_e2e.py run --suite coverage`, only if needed for final scoped attribution not supplied by CI | Separate authorization of exact owned device; source/APK/classes/JUnit/worker identities, genuine scoped probes and unchanged gates. Not an M4/M5 repeat |
| Existing APK/DEX/source-set inspection | Final production APKs contain no canonical PDFs, opt-in M4/M5 tests or debug worker exporter; ordinary instrumentation excludes opt-in adapters |
| Applicable manual/physical acceptance procedures in ledger | Exact device/scenario approval and actual evidence; no emulator substitution for physical tablet |
| GitHub PR/check/protection review | Restore read access or obtain attributable owner evidence; do not infer required checks or review approval from workflow YAML |

The CI native script includes controlled diagnostics/device operations; dispatch
requires separate approval, not an inference from this plan. No M4 or M5 campaign is
proposed merely to repeat its accepted scope. PASS requires complete attributable
results and applicable gate success; executed assertion/gate violations are FAIL;
missing/aborted/skipped-required/uncertain evidence is BLOCKED, never fabricated PASS.

## M7-C decision boundary

Technical consolidation requires an accurate final mapping, reviewed cumulative diff,
resolved documentation discrepancies and an explicit open-item ledger. Formal owner
acceptance additionally requires applicable automated/manual/physical evidence and
resolution of policy conflicts. Scheduling deferral is not a waiver. PR readiness
requires current checks/base, review/protection information and no unresolved applicable
blocking requirement. Only the owner may authorize and perform a merge. After an
owner merge, inspect actual main-push CI results. Any tag/release requires separate
authorization; no version or distribution decision is made here.

Current decision: **NOT MERGE-READY / NOT FORMALLY ACCEPTED**. M7-A documentation
consolidation remains IN PROGRESS for owner review; M7-B verification and M7-C acceptance
are PENDING. No files outside the approved documentation scope, tests, workflows,
devices, releases or repository settings are changed by this task.
