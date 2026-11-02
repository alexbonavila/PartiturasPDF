# Phase J M6 — defect triage and conditional optimization

Recorded 2026-11-02. Repository `alexbonavila/PartiturasPDF`, branch
`test/mvp-stabilization-j`, audit baseline
`a6fdcf73f9cfbc3e362ce2e2af539f2927686bf8` (clean before this documentation update).
This records the completed repository-based read-only audit, not a new execution
campaign, measurement, complete issue inventory or guarantee of defect absence.

| Decision | Status |
| --- | --- |
| J12 | **NO CORRECTION JUSTIFIED BY CURRENT EVIDENCE** |
| J13 | **NOT AUTHORIZED / DEFERRED** |
| M6 | **CLOSED — OWNER-APPROVED EVIDENCE-TRIAGE SCOPE (2026-11-02)** |
| M7 | **NOT STARTED** |

## Evidence and provenance

| Reference | Evidence available / attribution | Boundary |
| --- | --- | --- |
| [M2 API26](J_M2_C2B_ANDROID_API26_EVIDENCE.md) | Previously independently reviewed 20/20 execution at `83e0094cea8a20a90635f82b97b84610a11d2a2e` | Earlier incomplete run remains BLOCKED; not a new current-revision execution |
| [M3-A compatibility](J_M3_A2_ANDROID_COMPATIBILITY_EVIDENCE.md) | Previously independently reviewed 18/18 per API26/27/36 at `a9905d497979520cfabf59e038b51f0ed453a8a3` | Exact selected inventory, not every recovery/manual scenario |
| [M3 closeout](J_M3_CLOSEOUT.md) | Owner-reported final 631 JVM / 431 ordinary Android PASS and scoped coverage; raw final archive unavailable in this checkout | No new per-method/raw-artifact verification or latest-head CI is inferred |
| [M4 results and corrections](J_M4_BENCHMARK.md) | Verified owner-provided six successful benchmark invocations, report `20261102T084249Z-a38be721e7404c949a05e6c30c79b64d`, execution `a782903fef1a5cc8ab3404d8c27fbc8cd45d16e3` | Raw report not attached to that documentation task; sampled/debug-instrumented observations, not approved performance targets |
| [M5-A endurance](J_M5_A_ENDURANCE.md) | Verified owner-provided 3 cycles / 9 native journeys PASS, report `20261102T090554Z-cd4efb338aa34be1a14c1ef300e4bb9a`, execution `43401566a7c3944c1b4faa9256ccf235e723db10` | Raw archive not attached to documentation update; short campaign and unresolved trend |
| [M5-B recovery](J_M5_B_RECOVERY_MATRIX.md) | Supplied independent archive-review findings: 8/8 PASS, report `e07d1f8aed8549faaef51103c2528818`, execution `d1df892e5d1f7d2c3e722b51e9617ed666ca6d0c` | Not a new Codex archive audit; test-specific teardown is not a separate host-cleanup campaign |

The owner also reports that a supplementary GitHub open-issue search returned no
matching items. That narrow search result does not prove no defects exist, cover
closed/historical issues or establish a complete historical issue inventory. The
M6 audit remains repository/evidence based; no fresh GitHub search was performed here.

## J12 findings and decision

No currently demonstrated, reproducible and attributable production defect in the
available evidence meets the threshold for a J12 correction. No current MVP failure,
OOM or failed resource cleanup is established by the selected M4/M5 results. This
is an evidence-limited decision, not a claim that PartiturasPDF contains no defects.

Historical M4 failures concerned RAM representation, unsupported API26 diagnostics,
fixture ViewModel reuse and process-discovery/sampler races. The dated M4 record
preserves those FAIL/BLOCKED executions and their corrections; the subsequent complete
native campaign passed. They do not justify new production changes or reopening M4.
M4, M5-A and M5-B retain their respective owner-approved scoped closures.

Missing coverage or acceptance evidence is an EVIDENCE GAP, not a CONFIRMED DEFECT.
Excluded/destructive scenarios are not failed product behavior merely because they
were not executed. **J12 requires no production changes on current evidence.** A
future correction requires a concrete reproduction, source/device attribution,
owner-approved minimal scope and regression evidence at the corrected revision.

## Open memory observation — not resolved

M5-A retains **`review_required=true`**, classified **UNRESOLVED OBSERVATION**:

| Fixture | Post-close main PSS, cycles 1 / 2 / 3 (KiB) | Interpretation |
| --- | --- | --- |
| 02 | 94667 / 98425 / 98535 | Successive increases |
| 10 | 80665 / 95322 / 104772 | Successive increases |
| 05 | 101977 / 96282 / 104617 | Not monotonic |

The checkpoint is after route close but before complete fixture-host teardown. These
finite PSS observations do not demonstrate retained-object growth or a memory leak.
No OOM, failed bitmap recycling, failed read-descriptor closure or changed PDF bytes
was demonstrated in the nine journeys. Fixture-10 open PSS of 117719 / 115819 /
117019 KiB is comparatively stable, but neither resolves the post-close trend nor
proves future stability. Sampled maxima are not absolute memory peaks.

Carry the observation into M7 without changing it to RESOLVED or CONFIRMED DEFECT.
Equivalent post-teardown observations, consistent workload/process attribution and
appropriate allocation/retention evidence may be considered under **separate owner
authorization**. No new measurement, forced GC, longer campaign or instrumented
collection is authorized or performed by this closeout.

## J13 conditional optimization decision

**NOT AUTHORIZED / DEFERRED.** No approved quantitative latency or memory target has
been demonstrated to be violated. The existing M4 timings are instrumented debug
observations, not an approved release/device budget. Do not invent thresholds after
measurement or optimize merely because a PSS review flag exists.

No speculative caching, prefetching, concurrency, parallel native work or resource-limit
changes are authorized. Future J13 work requires a reproduced performance problem,
an approved target and minimal change scope, matched before/after measurements and
regressions preserving isolation, resource ownership and source integrity.

## Acceptance ledger to carry into M7

These remain visible; M6 technical completion does not waive them:

- **Physical Android 8.1/API27 tablet:** actual hardware acceptance remains separate
  from API27 emulator compatibility. Quantitative budgets and physical calibration
  are unapproved. See [P05-A/P05-F](../planning/PRE_FGH_DEFERRED_ACCEPTANCE.md).
- **External SAF/provider behavior:** controlled local/injected capabilities and
  historical authentic SAF stages do not establish the outstanding external-provider
  matrix. See P05-E in the same register and M5-B limitations.
- **Genuine OS process-death and broader worker-loss recovery:** synthetic Bundle
  restoration is not OS death; historical standard worker-death coverage does not
  certify all operational recovery. Separate exact scope/device authorization is
  required for termination, worker killing or real permission revocation.
- **Backup/restore, D2D and other transfer mechanisms:** actual supported transport
  evidence and safety approval remain required; cloud-style tests do not prove D2D.
  See [C-DEF-01–04](../PHASE_C_DEFERRED_ACCEPTANCE.md).
- **Accessibility/manual language/locale:** TalkBack, full linguistic/error-screen
  review and applicable live locale-transition acceptance remain outstanding as
  described in [M3's preserved limitations](J_M3_CLOSEOUT.md).
- **Delivery/restoration:** preserve Play/release delivery, API27/36 AAB offline
  switching and B2-D exact-restoration limitations in
  [the AAB record](J_M3_B2D_AAB_LANGUAGE_DELIVERY_EVIDENCE.md). Preserve the original
  global blockers and later separate restoration results; do not rewrite history.
- **Prolonged endurance/PSS:** finite M5-A completion is not long-duration stability
  or leak-free acceptance; its review flag remains open.
- **Latest-revision CI/final acceptance:** historical passes are revision specific.
  No new hosted CI, merge readiness, physical acceptance or final Phase J approval
  follows from this audit. Required final evidence remains for consolidation.
- **J08:** **DEFERRED TO PHASE R**, unchanged.
- **Status reconciliation:** `docs/PROJECT_STATUS.md` still contains the dated M3
  snapshot describing M4 as not started. M7 should reconcile current summaries with
  later scoped closures while preserving historical dates/results. This is a
  documentation inconsistency, not a demonstrated production runtime defect.

M7 is NOT STARTED. This document supplies its future open-findings ledger, not
permission to begin final consolidation or automatically close outstanding evidence.

## Validation and closure boundary

Only this document and `J_PHASE_PLAN.md` are changed. Evidence references, named
revisions/results, relative documentation targets and status consistency were checked
statically; `git diff --check` passes. No tests, builds, Android operations, new
measurements or production changes occurred. No complete live issue audit or raw
campaign reanalysis was performed. The owner subsequently approved scoped closure and documentation publication.

**M6 is CLOSED by owner approval on 2026-11-02 only within its evidence-triage
scope, with no production changes. M7 remains NOT STARTED.**
J12's no-correction decision and J13's non-authorization do not declare final Phase J
acceptance or dismiss any unresolved/deferred obligation above.
