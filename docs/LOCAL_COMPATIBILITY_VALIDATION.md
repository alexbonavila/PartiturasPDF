# PartiturasPDF — L04D Compatibility Validation Report

**Status:** EVIDENCE CONSOLIDATED — L04A macOS and L04B hosted compatibility PASS; earlier Windows evidence PASS; physical Android 8.1 acceptance DEFERRED / NOT RUN; final owner sign-off not recorded.  
**Report date:** 2026-10-16 (Europe/Madrid)  
**Repository:** https://github.com/alexbonavila/PartiturasPDF  
**Branch:** `test/local-e2e-automation`  
**Latest validated code/test revision (evidence baseline, before subsequent documentation-only commits):** `87b864de296563649a4f80e15b55e30f75e26836`  
**Production changes in latest portability correction:** None; only `scripts/tests/test_local_coverage.py` and `scripts/tests/test_local_e2e.py` changed relative to `9d10739d21fc718b7dcec7d2941b6e346f3aa1b6`.

## 1. Scope and boundaries

This report consolidates existing L01 authentic SAF E2E, L02 full local regression, L03 genuine JVM/Android JaCoCo coverage, L04A operating-system architecture portability, and L04B Android API compatibility. It neither substitutes for physical tablet testing nor authorizes implementation changes, changes to CI policy/thresholds, a PR, or merging.

A result is attributable only to the SHA and environment actually exercised. PASS for emulator tests does not establish physical API 27 tablet acceptance, real backup/restore, Android D2D transfer, reader/editor functionality not implemented, or arbitrary future OS compatibility.

## 2. Verified hosted GitHub Actions — L04B

- **Workflow:** CI, run [38857215346](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38857215346).
- **Trigger:** `workflow_dispatch`.
- **Revision:** `87b864de296563649a4f80e15b55e30f75e26836`.
- **Created:** 2026-10-16T05:42:57Z.
- **Conclusion:** `success` (completed, first attempt).
- **Required seven jobs:** `build`, `lint`, `unit-tests`, `instrumented-api26`, `localization`, `pdf-fixtures`, `quality-gate` — each `success`.
- **Compatibility jobs:** `compatibility-api27`, `compatibility-api36` — each `success`.

| Execution | Real tests verified from GitHub job logs | Result | Source |
| --- | ---: | --- | --- |
| JVM in `instrumented-api26` | 165 | PASS, zero failures/skips | [API26 job](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38857215346/job/116831286398) |
| Android API 26 | 228 | PASS, zero failures/skips | [API26 job](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38857215346/job/116831286398) |
| Android API 27, `google_apis`, x86 | 228 | PASS, zero failures/skips | [API27 job](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38857215346/job/116831286380) |
| Android API 36, `google_apis`, x86_64 | 228 | PASS, zero failures/skips | [API36 job](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38857215346/job/116831286282) |
| Aggregate quality gate | Job completed successfully | PASS | [Quality gate](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38857215346/job/116833155457) |

Android executions total 684 individual test-method executions across the three API levels (not 684 distinct tests). The workflow checks the actual API level before running AndroidJUnitRunner and verifies JUnit XML after execution.

**GitHub artifacts:** `instrumentation-api26-reports` (ID `12027429229`), `instrumentation-api27-reports` (ID `12028165177`), `instrumentation-api36-reports` (ID `12027956895`). They were present and unexpired at review time. Workflow retention is 14 days; preserve separately if needed for long-term audit.

## 3. Native host execution — L04A

### 3.1 Windows 11 / x86_64 / Android API 26 x86_64

**Owner-supplied local results on earlier revision `9d10739d21fc718b7dcec7d2941b6e346f3aa1b6`:**

- `run --suite coverage`: overall PASS/0; `coverage=VERIFIED`, 165 JVM PASS, 228 Android PASS; no reported failures/blockers/diagnostics.
- Combined JaCoCo gates: A1 187/188 lines, 175/176 branches; B5 PDF 213/218 lines, 63/72 branches; C05 Room 207/210 lines, 167/177 branches; DataStore 35/35 lines, 12/12 branches.
- Owned temporary coverage output cleanup and all six managed emulator settings restored PASS.
- L02 `full` PASS previously recorded at commit `27cef9f214e1d254d7e2183a649df8f65d1c27c9`: host 369 (204 Python + 165 JVM), Android 228, four SAF stages, cleanup PASS.

**Boundary:** these Windows results predate `87b864de`; they are not evidence that the final two Python-only portability test changes were executed natively on Windows.

### 3.2 macOS Apple Silicon ARM64 / Android API 26 ARM64

**Owner-executed latest-revision evidence: `87b864de296563649a4f80e15b55e30f75e26836`, 2026-10-16.**

- The preliminary `doctor` passed on macOS ARM64 with JDK 25, SDK 37, native Hypervisor.Framework acceleration and the **existing** dedicated API 26 ARM64 AVD (`PartiturasPDF_E2E_API26_f635ff9eba00`, `emulator-5580`). No reprovisioning was needed.
- The Python fixture-dependency preflight passed with an isolated environment: PyMuPDF 1.28.2, ReportLab 4.5.1, Pillow 12.3.0, pypdf 6.20.1.
- `python3 scripts/local_e2e.py run --suite full` **PASS / exit 0**.
  - Report: `build/local-e2e/reports/20261016T053524Z-47938fb8c46142afafc63e2a9ea3a3af`.
  - **Host:** PASS, **411 checks** = 246 Python regressions + 165 JVM tests.
  - **Android:** PASS, **228 ordinary tests**.
  - **SAF E2E:** PASS, **4 stages** (`acquire`, `restartAndRelease`, `lossAndRestore`, `restoredAfterRestart`).
  - **Total reported in the full groups:** 643 checks/stages (411 + 228 + 4; heterogeneous test categories, not 643 distinct test methods).
  - **Cleanup:** four recorded entries PASS: `cleanupOwnedGrant` (process exit 0), `fixture`, `setting system/accelerometer_rotation` (already at original value), and `setting system/screen_off_timeout` (restored and read back).
  - **Diagnostics:** empty (`[]`).
- `python3 scripts/local_e2e.py run --suite coverage` **PASS / exit 0**.
  - Report: `build/local-e2e/reports/20261016T053854Z-4e6d36ba8e1048f2b9843c6151b847d8`.
  - **JVM:** 165 tests; **Android:** 228 tests.
  - **Coverage:** genuine JaCoCo JVM+Android, all four approved component scopes above their existing thresholds; see Section 4.
  - **Coverage failures:** `[]`; **blockers:** `[]`.

The quantitative figures above were taken from the owner-provided `full/summary.json` and `coverage/summary.json` terminal extracts at these exact report paths, not inferred from the previous revision. The excerpts establish status, group counts, four reported full-suite cleanup items, absence of full-suite diagnostics, and scoped coverage counters. Preserve the original directories/JSON and binary evidence for independent forensic reproduction; the excerpts alone do not substitute for every raw artifact.

## 4. Latest-revision authentic coverage — macOS ARM64

The following fractions were recorded from native macOS `coverage/summary.json` for **`87b864de296563649a4f80e15b55e30f75e26836`**. They are numerically identical to the earlier Windows evidence at `9d10739d`, but belong independently to the latest macOS execution.

| Approved scope | Lines covered | Branches covered | Existing threshold | Verdict |
| --- | ---: | ---: | --- | --- |
| A1 contracts | 187/188 = **99.47%** | 175/176 = **99.43%** | 90% lines / 80% branches (aggregate) | PASS |
| B5 Android PDF adapter | 213/218 = **97.71%** | 63/72 = **87.50%** | 90% / 80% (aggregate) | PASS |
| C05 Room metadata | 207/210 = **98.57%** | 167/177 = **94.35%** | 90% / 80%, per existing package/source rules | PASS |
| C05 DataStore preferences | 35/35 = **100%** | 12/12 = **100%** | 90% / 80%, per existing package/source rules | PASS |

The approved-scope denominators are A1 188 lines/176 branches, B5 218/72, Room 210/177, and preferences 35/12. The `coverage` wrapper reported `status=PASS`, `jvm_tests=165`, `android_tests=228`, `failures=[]`, `blockers=[]`. The independent **entire handwritten production** inventory (historically 1,258 lines and 737 branches on the earlier compiled model) remains informational and is not represented as achieving the scoped-gate percentages. Success of the approved scopes must not be interpreted as a project-wide 90% coverage claim.

## 5. Deferred and out-of-scope requirements

- **L04C: Physical Android 8.1 / API 27 tablet:** **DEFERRED / NOT RUN** at owner's direction until a physical device is purchased. This is not replaced by the passing Android API27 emulator. `docs/TESTING_POLICY.md` and `docs/DEFINITION_OF_DONE.md` still require applicable real-device acceptance for a functional phase's unconditional formal closure. Deferral does not waive those policies.
- **Future physical-tablet workstream — recorded owner plan (2026-10-16):** After the physical Android 8.1 (API 27) tablet is purchased, create a **new, short-lived, task-specific branch** from the then-current `main` (suggested name: `test/physical-android81-acceptance`) for preparing the physical-device test procedure and any approved test-only instrumentation. Do **not** create it now or keep the L01–L04 automation branch open solely to wait for hardware. First approve precise scope, safety boundaries and acceptance criteria; run actual physical-device checks for applicable SAF, PDF, persistence, lifecycle, locale, UI and performance scenarios using synthetic documents; record device model/build, test/app commit, results and evidence. Never wipe, uninstall, clear data or attempt real backup/restore/D2D on the owner's tablet without separately authorized safeguards and an appropriate disposable environment. Any tracked changes go through the ordinary PR, latest-head CI and owner approval. This is a **future plan, not executed acceptance or a waiver**; C06 backup/D2D remains independently deferred.
- **C06 real backup/uninstall/restore and device-to-device transfer:** **DEFERRED / NOT RUN**, separately controlled and potentially destructive. Not part of L04 and not authorized by a local regression PASS.
- **Authentic external SAF grant manual acceptance:** separately documented, and not treated as performed merely because normal instrumentation/SAF synthetic E2E passed.
- **Unimplemented reader/library/editor/annotation/Drive E2E:** not applicable to this infrastructure-only cross-platform report; design their tests when the associated features are approved and exist.

## 6. Preservation and final decision record

The major local and hosted execution results are now quantitatively consolidated. Remaining recordkeeping, not missing L04A/L04B test execution:

1. **Preserve the native full and coverage report directories** exactly as identified in Section 3.2, including raw JaCoCo `.exec`/`.ec`, JUnit XML, class/APK hashes, logs and cleanup reports. The owner-provided terminal extracts document the summary fields; the raw reports should remain available for any independent audit.
2. **Preserve the API 26/27/36 CI artifacts** for long-term retention where needed. GitHub's workflow config specifies a 14-day retention period; the exact artifacts/IDs are in Section 2.
3. **Keep the SHA boundary explicit.** Current reviewed SHA is `87b864de`; Windows local evidence predates the two test-only portability changes, while latest-revision macOS and hosted CI evidence is exact-SHA. Do not falsely state that latest-SHA Windows native execution has occurred.
4. **Physical tablet deferral** is an explicit owner scheduling decision, not physical acceptance or a policy waiver. When an Android 8.1 physical tablet becomes available, execute and separately document the required applicable acceptance; formal functional-phase closure remains subject to the applicable `TESTING_POLICY.md`, `DEFINITION_OF_DONE.md`, and change-control requirements.
5. **Owner approval for integration remains independent.** No PR or merge is created or authorized by these test results or this document. Any later documentation-only commit will have a different HEAD; evaluate impact on evidence-to-code mappings and required CI before integrating.

### Owner review / decision fields

- Evidence reviewer: **PENDING OWNER REVIEW**.
- L04A native validation acceptance: **EVIDENCE PASS; OWNER SIGN-OFF PENDING**.
- L04B latest-SHA CI API compatibility acceptance: **EVIDENCE PASS; OWNER SIGN-OFF PENDING**.
- L04C physical Android 8.1: **DEFERRED / NOT RUN by owner's direction; no waiver**.
- Authorization to create PR or merge: **NOT GRANTED**.

## 7. Final assessment — evidence consolidated

| Workstream | Status | Basis |
| --- | --- | --- |
| **L04A — macOS ARM64** | **PASS / 0** | Latest SHA `87b864de`: `full` = 411 host + 228 Android + 4 SAF, clean; `coverage` = 165 JVM + 228 Android, approved scopes PASS, no failures/blockers |
| **L04A — Windows x86_64** | **PASS on earlier revisions** | L02 at `27cef9f`, L03 at `9d10739d`; does **not** assert Windows native execution of SHA `87b864de` |
| **L04B — hosted Android API 26, 27, 36** | **PASS** | GitHub CI [38857215346](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38857215346), exact latest SHA; 228 Android tests per API, all nine workflow jobs PASS |
| **L04C — physical Android 8.1 / API 27** | **DEFERRED / NOT RUN** | Physical device not yet available; applicable formal functional acceptance remains outstanding |
| **L04D — compatibility report** | **PREPARED / evidence complete** | Source links, local test counts and coverage, residual risks and exact revision boundaries documented; owner sign-off remains pending |

**Conclusion:** The available cross-host local test infrastructure and hosted Android API 26/27/36 compatibility have passed at their respective evidenced revisions. L04 documentation is ready for owner review, with physical-device acceptance explicitly deferred and no automatic approval of PR/merge or waiver of repository policy.