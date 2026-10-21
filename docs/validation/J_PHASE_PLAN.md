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
