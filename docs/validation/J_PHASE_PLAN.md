# Phase J implementation plan — M1

Baseline main: `37f51baf8b34b069a0c83e93b5780a6c9f676c16`. Branch `test/mvp-stabilization-j`.
Owner-approved scope: Tier A evidence foundation only. No M2/device work, production corrections,
CI/Gradle/dependency/fixture changes or publication. J08 remains DEFERRED TO PHASE R.

## Reuse and interfaces

The existing local_e2e Runner is the only executor; local_test_evidence supplies evidence checks.
Default commands and original reports are unchanged. The optional profile is host-only:

```bash
python3 scripts/local_e2e.py doctor --report-profile phase-j
python3 scripts/local_e2e.py run --suite host --report-profile phase-j
```

Use existing --home/--sdk options before the command if needed. Existing toolchain prerequisites
remain necessary. Profile doctor reports source/JDK/SDK discovery only; it executes no project
checks and is not Android readiness. Legacy doctor retains its read-only ADB/AVD discovery;
profile doctor and the Tier A run never invoke ADB, emulator, installation or instrumentation.
All device suites and failure injection are rejected for this profile before execution.

The catalogue scripts/j_scenarios.json versions eight actual host checks: fresh JVM XML with
coverage finalizer, Lint, debug/release builds, fixture dependency check, Python, localization,
canonical verifier. Future package scenarios are NOT RUN/planned. J08 is DEFERRED BY OWNER.
The catalogue's schema and existing source-derived test inventory avoid frozen test counts.

## Reports and provenance

The existing unique timestamp/UUID directory remains under build/local-e2e/reports by default.
Its original raw logs, summary.json, index.html and JUnit stay compatible and local. The optional
phase-j/ child contains capture.json, summary.json, report.html and redacted artifact copies.
No second artifact-management or execution stack is introduced. Reports record source revision,
Git tree, dirty state, working source digest, build configuration hashes, environment, scenario
IDs, command timestamps/exit codes, log hashes, executed test identity and evidence origin.
Dirty source is attributable by digest; it is never described as the pristine commit alone.

Commands and JVM XML are hashed at capture, rechecked before publication-safe projection, and
JVM identities must match the source-derived expected inventory (test and testDebug source sets).
JUnit sharing copies redact values structurally and preserve parseable XML and test counts. PASS requires all eight
implemented scenarios with validated evidence and complete successful orchestration. FAIL,
BLOCKED, NOT RUN, NOT APPLICABLE and DEFERRED BY OWNER stay distinct. Partial all-green results
are BLOCKED. Missing/mismatched/corrupt/duplicate/stale evidence cannot become PASS.
Relative artifact confinement rejects traversal, absolute/Windows paths and symlink parents.
Atomic file replacement avoids partial JSON masquerading as complete results; summary is the
authority. In-progress summaries have complete=false. A final evidence error invalidates PASS.

Only phase-j output is intended as a redacted sharing projection. Raw originals are NOT safe
to publish automatically. Private document-context log lines are removed; paths/URIs and device
identifiers are normalized. Review any output before manually sharing it. No uploading occurs.
Controlled Pdfium diagnostics are NOT RUN in Tier A, never ordinary PASS. Coverage workers and
metrics are not fabricated; no empty timing/memory report is emitted by M1.

## Milestones and approvals

| Milestone / packages | Boundary and evidence |
|---|---|
| M1 J01/initial J14, supports J11 | Audit/catalogue, host-only provenance/reporting and negative tests. |
| M2 J02/J06/non-destructive J10 | Reuse real host/native integration and authentic SAF seams; distinguish injected/grant evidence. |
| M3 J07/J09/J11 | Separate26/27/36 matrices, resource parity and separately authorized real offline locale switching. |
| M4 J03/J04 | Precise first-visible/usable-Library timing, main+worker PID/epoch memory, calibration then physical baselines. |
| M5 J05/deeper J06/J10 | Capped replayable endurance/recovery; backup/D2D require distinct approval. |
| M6 J12 then conditional J13 | Reproduced defect/measurement, owner-approved minimal fix and matched before/after regression evidence. |
| M7 J14/J15 | Consolidation, open/deferred ledger and human acceptance decision. |

J07/J11 continue throughout; demonstrated blocking defects can trigger separately approved J12.
No prefetch/cache/parallel-native/limit expansion. Existing local_coverage/persistence_coverage,
run_pdfium_ci, verify_test_results, localization/backup validators and immutable corpus remain
reused unchanged. Isolated-worker coverage stays BLOCKED without authentic data and separately
approved protected collection design. No worker protocol or export bridge is implemented.

Build/Lint/JVM/Python/resources/fixtures are approved Tier A actions. Device installs/launches need
recorded identity and authorization. Physical measurements/long stress need session approval.
Force-stop, kill, grant revoke, device setting changes, clear/uninstall/restore/transfer require
operation-specific approval and test-owned data. Contracts/manifest/worker/schema/SAF semantics,
dependencies, CI, gates and policies need separate protected approval. Only the owner merges,
tags, publishes and formally accepts a phase. M1 does not reconcile release docs or start M2.

## Dated execution evidence update — 2026-10-21

The preceding text preserves its original planning/readiness context. Subsequent
owner-local API26 execution is independently reviewed in
[J_M2_C2B_ANDROID_API26_EVIDENCE.md](J_M2_C2B_ANDROID_API26_EVIDENCE.md):
first attempt BLOCKED (15/20 actual tests), second attempt PASS (19 selected ordinary
methods plus smoke, 20/20). This is subset evidence, not full M2/Phase J acceptance.
Genuine SAF and other excluded operations remain NOT RUN in this evidence set.
Historical host-only reports and catalogue projections are not relabeled.

## M3-A1 mapping update — 2026-10-21

[The compatibility matrix](J_M3_A_COMPATIBILITY_MATRIX.md) describes seventeen
existing J07 methods plus separate smoke for future API26/27/36 execution. All
new mappings remain NOT RUN; M2 historical evidence and its selection are preserved.
M3-A1 changes mapping/integrity tests only. M3-A2 device readiness and actual runtime
results require separate approval; neither M3-B nor M3-C starts here.

## M3-A2 evidence review update — 2026-10-21

[Independent compatibility evidence review](J_M3_A2_ANDROID_COMPATIBILITY_EVIDENCE.md)
records seventeen J07 methods plus smoke PASS (18/18) independently on API26/27/36
at `a9905d497979520cfabf59e038b51f0ed453a8a3`. The preceding NOT RUN text preserves
its original planning context; historical reports/catalogue are not relabeled.
The report records API26 package uninstallations and the owner's conversational
authorization clarification. Separate archived approval and independent evidence of
prior data disposability remain absent. Formal acceptance and excluded operations,
physical checks, M3-B and M3-C remain unresolved.

## M3-B1 locale/offline mapping — 2026-10-21

[Locale/offline matrix](J_M3_B_LOCALE_OFFLINE_MATRIX.md) maps eight existing Android
methods plus separate smoke, all J09 NOT RUN. Scoped resources and injected local
capabilities are distinct from real device-language, offline and AAB acceptance.
M3-B2 requires exact-device/settings/restoration approval; no device execution or
M3-C begins here. Historical M2/J07 evidence and reports remain unchanged.

## M3-B2-A J09 execution evidence update — 2026-10-21

[Independent J09 evidence](J_M3_B2A_J09_ANDROID_EVIDENCE.md) records eight selected
methods plus smoke PASS (9/9) on each API26/27/36 at
`df643959c4d6bd19f8301f972d4e00bca9636596`, under original en-US settings.
Matching before/after settings are endpoint records, not proof of continuous
configuration or offline networking. Earlier NOT RUN text retains its planning
context; catalogue and historical host reports are unchanged. Actual language
changes, visible translations, disconnected networking, AAB delivery and changed
settings restoration remain unresolved. M3-B2-B/C and M3-C are not started.

## M3-B2-B manual locale selected-scope closeout — 2026-10-22

[Manual Android locale evidence](J_M3_B2B_ANDROID_EVIDENCE.md) records owner-verified
local outcomes across API26/API27/API36: four configurations and 12 primary selected
JUnit executions per API, **12/12 configurations and 36/36 primary executions PASS**
in total, with baseline and exact original-order restoration PASS for each API.
Raw evidence is unavailable in this review checkout; the report explicitly attributes
results to the owner and does not claim an independent artifact audit.

This is accumulated per-API evidence from three separate runs, not a single successful
uninterrupted global matrix. API26 run `22ffdb4262dc4a12a77bdcf6b570ac0f` at
`fe79cf269e6efbe30bd98a030cc945eb010bc180` remains globally **BLOCKED** after an API27
ADB interruption. API27 run `a328d39d16814b1497dba051d04e7408` and API36 run
`ee42080c88c44a76910f734558e7ef63`, both at
`6b2294b715abbfd8787c0878caf8d95116fa4e35`, are independent subset PASS records.
The API36 extra-language restoration blocker and subsequent successful correction
remain part of the reported history. Auxiliary invocations are excluded from the 36.

M3-B2-B may be treated as complete for planning in its selected configuration,
localization, JUnit, baseline and restoration dimensions, subject to provenance review
and owner formal acceptance. Earlier dated NOT RUN statements, catalogue projections
and M2/M3-A/M3-B2-A evidence retain their original classifications. Live Activity locale
transition is still NOT RUN; these results establish behavior under selected configurations.

Proposed next work is M3-B2-C genuine offline validation and separately scoped AAB
language-delivery acceptance (earlier plans also call AAB work M3-B2-D). Neither is
PASS or started here. M3-C coverage attribution, open M2 SAF/recovery/diagnostic gaps,
physical Android 8.1, accessibility, linguistic review and performance/memory/endurance
remain outstanding. M4–M7 and J14/J15 final consolidation are not completed by this
report. **J08 remains DEFERRED TO PHASE R.** No broader M3-B or Phase J acceptance is granted.

## M3-B2-C selected functional offline closeout — 2026-10-22

[Android offline evidence](J_M3_B2C_ANDROID_OFFLINE_EVIDENCE.md) records **15/15 primary
selected JUnit executions PASS** (five methods per API26/API27/API36), passing recorded
before/after offline checks and 5/5/12 passing during-test monitoring samples. These
are owner-reported results from the supplied final post-run restoration projection;
original raw evidence is unavailable for independent inspection in this checkout.
Monitoring was sampled, not continuous packet-level isolation.

Execution `build/j-m3-b2c/offline-three-e7b58701943e415f9fb9d219c11dd24a` at
`6b2294b715abbfd8787c0878caf8d95116fa4e35` remains originally **BLOCKED**. API26/API27
initial radio restoration was BLOCKED; API36 initial restoration was PASS. The separate
projection confirms later restoration PASS on all three APIs to original airplane=0,
Wi-Fi=1, with matching device/API/boot flags. It does not rewrite the original summary.

M3-B2-C selected functional offline scope may be treated as completed on this
owner-reported basis, subject to formal owner acceptance and the documented provenance
limits. Earlier dated NOT RUN planning and M3-B2-B histories remain unchanged. This
closes neither all of M3-B nor Phase J. Proposed next work is **AAB language-delivery
validation**, not started here. Genuine external SAF, physical tablet, accessibility,
linguistic/live-transition, unstated recovery/performance/endurance, M3-C and remaining
Phase J acceptance remain open. **J08 remains DEFERRED TO PHASE R.**

## M3-B2-D AAB language-delivery selected closeout — 2026-10-23

[AAB language-delivery evidence](J_M3_B2D_AAB_LANGUAGE_DELIVERY_EVIDENCE.md) records
owner-reported successful debug AAB generation from
`6b2294b715abbfd8787c0878caf8d95116fa4e35`, disabled language splitting, device-specific
APK generation/installation on API26/API27/API36, and owner-observed offline Spanish,
Catalan and English Library UI on API26. The app subtree is unchanged at the newer
documentation baseline; the AAB is not reattributed to a documentation commit.

The owner-local run `build/j-m3-b2d/aab-20261023T073014` and screenshots are unavailable
here. Screenshot filenames were overwritten during capture; their final language
correspondence and hashes remain unverified. A separately inspected local AAB supports
only its own configuration, with no verified identity link to the owner artifact.
API26 original airplane=0/Wi-Fi=1 and manual final restoration are owner-reported;
no session-specific final radio output or original/final language ordering verifies
exact restoration. That limitation is not converted to restoration PASS.

Selected local bundletool delivery/API26 offline language availability may be treated
as complete for planning, subject to provenance limits and owner acceptance. Earlier
M3-B2-B and M3-B2-C results retain their dated scope and classifications. Real Play
delivery, AAB offline language changes on API27/36, all-screen linguistic/accessibility,
SAF, physical, live-transition, performance/endurance and exact restoration evidence
remain outside this selected completion. M3-C coverage-attribution readiness is the
next proposed planning topic, not started here; M4–M7 and formal Phase J acceptance
remain outstanding. **J08 remains DEFERRED TO PHASE R.**

## M3-C1 coverage/evidence audit — 2026-10-23

[Coverage audit](J_M3_C1_COVERAGE_AUDIT.md) inspects existing A1/B5/C05/local-production
infrastructure and retained evidence without tests, Gradle, collection or device work.
No current source-attributed coverage percentage is independently VERIFIED. The JVM
binary passes existing class-ID/probe validation, but its complete producer chain is
missing; A1 XML/JVM evidence remains AVAILABLE_UNVERIFIED. The retained Android `.ec`
is an error message, not JaCoCo data. C05 run/provenance and a current combined report
are absent. Historical F/G/H XML counters agree with their register but hashes do not;
partial class matches cannot establish current coverage. Earlier results are preserved.

Authentic isolated worker coverage remains **BLOCKED / NOT MEASURED**, not zero:
app runner data cannot attribute another isolated PID/UID without a separately approved
export design. Native/functional PASS evidence does not close that gap.

The current local collector also includes all 432 Android methods, whereas existing
CI separates 418 standard methods from 14 fresh-process diagnostics (29 invocations).
Before any new capture, retrieve reusable producer artifacts or separately approve a
minimal inventory compatibility correction in the existing collector/host tests.
Then, if still needed, approve one frozen JVM/API26 capture on a verified runner-owned
emulator; no automatic API27/36 rerun or worker bridge is proposed. Preserve all gates,
source/class identity checks and historical artifacts. M3-C2 execution, M3-C3 closeout,
M4 and formal Phase J acceptance remain unexecuted/unaccepted by this audit.
**J08 remains DEFERRED TO PHASE R.** No next milestone starts automatically.
