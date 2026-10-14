# Phase C — Development Closeout and Deferred Acceptance

**Repository:** [alexbonavila/PartiturasPDF](https://github.com/alexbonavila/PartiturasPDF)<br>
**Application:** Partituras PDF (`com.abdev.partituraspdf`)<br>
**Recorded owner decision:** 2026-10-14<br>
**Delivery PR:** [#10 — `feat/data-persistence-c` → `main`](https://github.com/alexbonavila/PartiturasPDF/pull/10)<br>
**Last verified implementation revision:** `8927e2868cf9b6ec8bc93a12948c027440199db2`

## 1. Decision and scope

The owner has decided to **stop further Phase C feature development now** and postpone real-world backup/restore, device-to-device transfer and physical tablet acceptance until a later stage, when the application has more integrated functionality.

The owner also requires that **all Phase C source, test-only acceptance tooling and documentation ultimately reside in `main`**. No additional long-lived branch is wanted. Use the **existing PR #10** to integrate the prepared files, perform the required automated checks, and then delete its development branch after the owner merges it. Do not create a separate permanent test branch or push directly to `main`.

This decision does **not** mean the deferred tests passed. The distinction is:

- **Phase C implementation and automated verification:** completed at the last verified revision, subject to checks on any further documentation/test-tooling commit.
- **Deferred end-to-end/device acceptance:** **NOT RUN / DEFERRED**; no claim of passing real backup, restore, genuine device transfer or physical Android 8.1 acceptance.
- **Integration into `main`:** pending an explicit owner merge of PR #10 and the project's applicable acceptance/change-control requirements.
- **Unconditional phase acceptance under current repository policies:** **NOT YET SATISFIED**. `AGENTS.md`, `docs/TESTING_POLICY.md` and `docs/DEFINITION_OF_DONE.md` require physical Android 8.1 acceptance for functional phases. A recorded deferral must not silently override these requirements; any formal policy exception/change must follow `docs/CHANGE_CONTROL.md` (separately approved change-control PR).

This document records the owner's product/development scheduling decision, not a policy bypass or a claim that a later release is safe without the postponed validation.

## 2. Completed implementation and verified evidence

The Phase C codebase provides the approved Room metadata and DataStore preferences persistence adapters, regression tests, coverage enforcement and **Backup Policy B**. Policy B allows only `file:datastore/app_preferences.preferences_pb` in legacy, cloud-backup and device-transfer configurations. Room databases (and SQLite sidecars) and other private files are not on the allowlist.

| Evidence | Result and reference |
| --- | --- |
| Latest automatic PR CI at `8927e28` | **PASS**, [run #38625694907](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38625694907) |
| Latest complete API 26/27/36 compatibility CI at `8927e28` | **PASS** including quality gate, [run #38626486247](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38626486247) |
| JVM tests | **128/128 PASS** in hosted CI |
| Android instrumentation | **205/205 PASS** on API 26, API 27 and API 36 in hosted CI |
| Local Windows Tablet(AVD), Android 8.0/API 26 | **205/205 PASS** according to locally executed JUnit-result verifier; **not a physical tablet** |
| Persistence coverage | Room **207/210 lines (98.57%)**, **167/177 branches (94.35%)**; DataStore **35/35 lines**, **12/12 branches**; independent package/source gates **PASS** |
| Backup policy automated tests | **8 focused Python regression tests** included in **39 Python infrastructure tests**, reported PASS before publication |
| Backup APK resource inspection | XML and merged-manifest verification reported PASS; **not** an actual restore test |
| Room schema | Version 1 preserved; no speculative/destructive migration introduced |

Evidence above applies to the stated revision(s). **A later commit containing acceptance tooling or documents needs its own required PR CI before integration.** Do not re-label an older CI run as validation of a newer commit.

## 3. Prepared, not-yet-published acceptance tooling

The following four files were prepared in the Codex working tree at the last reported `8927e28` baseline, **without staging or committing**:

1. `app/src/androidTest/java/com/abdev/partituraspdf/data/backup/BackupPolicyAcceptanceRunner.kt` — explicit test fixture and post-restore assertions.
2. `scripts/Invoke-BackupPolicyAcceptance.ps1` — guarded Windows orchestration script.
3. `scripts/backup-acceptance.init.gradle` — manual build/instrumentation setup for the acceptance APK.
4. `docs/C06_BACKUP_RESTORE_ACCEPTANCE.md` — procedure, prerequisites and expected evidence.

Reported local validation: compilation of the affected test runners, Lint, 128 JVM tests, 39 Python tests, APK policy verification and four negative PowerShell guard cases passed. **The acceptance runner itself, native Windows workflow, backup/restore and device transfer were not executed.** The four files must be reviewed before committing; this document is not evidence that their actual contents have already been audited independently.

**Integration instruction:** Preserve all four files in **the existing `feat/data-persistence-c` branch and PR #10**, alongside this document. Make sure they are test-only/manual opt-in, never run destructive operations by default, and do not alter production behavior or automatically trigger backups in ordinary CI. Once PR #10 is merged by the owner, all five files belong to `main`, with no separate branch to maintain.

## 4. Deferred acceptance register

### C-DEF-01 — Real cloud-style backup and restore of Policy B

- **Status:** **NOT RUN / DEFERRED**. **Priority when resuming:** High; required before relying on backup behavior for users.
- **When:** After a safe application integration point is available and before a release that promises backup/restore behavior.
- **Environment:** Disposable emulator, supported local test backup transport, synthetic data only. Confirm emulator serial, package and destructive-operation authorization before uninstall/clear.
- **Procedure source:** `docs/C06_BACKUP_RESTORE_ACCEPTANCE.md`, with `scripts/Invoke-BackupPolicyAcceptance.ps1` and the dedicated Android fixture.
- **Acceptance evidence:** Backup/restore succeeds; `dark`, `ca` and `width` preferences are recovered exactly; synthetic Room registration, favorite, reading position, SQLite database/sidecars and a deliberately created unrelated private file **do not** reappear. Verify the actual transport and capture before/after assertions, APK/build SHA, hashes, commands, logs and exit codes.
- **Failure handling:** If the transport is unavailable, mark **BLOCKED**, not **PASS**; never substitute static XML tests for actual restore behavior.

### C-DEF-02 — Real device-to-device transfer under Android 12+

- **Status:** **NOT RUN / DEFERRED**. **Priority when resuming:** High if D2D transfer is supported/promised.
- **When:** A genuine Android transfer path and two isolated disposable devices are available.
- **Acceptance evidence:** Same expected retained/excluded data as C-DEF-01, across a **real device-transfer mechanism**. Record source/target OS/API, vendor, transfer mechanism, app version and observed result.
- **Limit:** `bmgr`/LocalTransport cloud-style tests do **not** certify D2D behavior. If the desired mechanism is unavailable, record **BLOCKED / NOT RUN**, not fabricated equivalence.

### C-DEF-03 — Physical Android 8.1 tablet acceptance

- **Status:** **NOT RUN / DEFERRED**. **Priority when resuming:** Required by current functional-phase Definition of Done.
- **When:** An actual Android 8.1/API 27 tablet and an integrated application build are available.
- **Acceptance evidence:** Record device model, API 27, Android build/security level, app commit/APK, test date, commands, JUnit XML, exercised application paths, outcomes and any issues. Execute applicable instrumentation and realistic persistence/error/lifecycle checks without destroying user data.
- **Limit:** An Android 8.0/API 26 AVD is **not** the requested physical Android 8.1 acceptance. API 27 hosted-emulator CI, while PASS, is also not a physical-device test.

### C-DEF-04 — Other transfer mechanisms and restore/reconciliation integration

- **Status:** **NOT RUN / DEFERRED / CONDITIONAL**. **When:** A cross-platform migration, OEM/privileged clone mechanism, new target Android transfer API, or actual product-level restoration workflow is designed or supported.
- **Questions:** Does that mechanism honor the app's legacy/extraction XML rules? Could restored document IDs or provider locations become stale? Are SAF grants/provider identities revalidated before access? Are preference file contents still exclusively non-sensitive?
- **Acceptance evidence:** Mechanism-specific primary documentation and tests; no blanket assurance that standard Android XML governs all privileged or cross-platform copying.
- **Scope boundary:** A migration/reconciliation implementation is **not** part of existing Phase C production code and requires separately approved functionality when needed.

## 5. Ongoing invariants for future development

- The backup allowlist includes the **entire** `app_preferences.preferences_pb` file, not three keys selectively. It must contain **only non-sensitive settings**. Re-review Policy B before adding new keys or separate stores.
- Never copy user PDFs, provider credentials, tokens or document associations into the allowlisted DataStore file.
- A restored Room `AVAILABLE` observation would **not** prove that a provider URI permission or document revision remains valid. Never treat stale metadata as authorization to access a PDF.
- Keep `minSdk=26`, Room schema v1 migration discipline, and English/Catalan/Spanish localization obligations.
- Do not introduce unreviewed automated destructive cleanup/restore behavior. Do not lower tests or coverage gates to finalize a phase.
- New Android OS versions or app-data-transfer behaviors require policy review before a feature/release depends on them.

## 6. One-PR integration plan — no additional retained branches

1. On **existing** `feat/data-persistence-c`, inspect and stage the four prepared test-only files and `docs/PHASE_C_DEFERRED_ACCEPTANCE.md` (this document). Ensure no unrelated changes.
2. Update `docs/C06_PERSISTENCE_ACCEPTANCE_AUDIT.md` only to correct obsolete baseline/CI/coverage statements and link to this deferred register. Explicitly preserve **NOT RUN** findings. Avoid claiming that the tests were executed.
3. Validate the diff, test-only safeguards and normal build/JVM/Python/backup-policy checks; commit with the configured owner author/committer. Push to the **existing** branch and PR #10. No new PR is needed for these non-policy files.
4. Confirm the latest PR #10 revision passes all **seven mandatory jobs**, plus verify its relevant API 27/36 evidence if the code changes require renewed compatibility checks. No agent may merge the PR.
5. Resolve owner review and the policy acceptance condition from Section 1. If the applicable Definition of Done remains unmet and no compliant, separately approved change-control decision exists, describe the result as **development-delivered / acceptance-deferred**, not unconditionally **phase-accepted**.
6. Only the owner performs the final merge from PR #10 into `main`. After confirming the merge and `main` CI, delete `feat/data-persistence-c` if no longer needed. **No long-lived Phase C branch remains.**

**Do not create a second branch solely for the four acceptance files.** A policy amendment, if the owner elects to change mandatory acceptance requirements rather than complete them, is a separate matter governed by `docs/CHANGE_CONTROL.md` and cannot be silently bundled into PR #10.

## 7. Resumption checklist

When the application has enough functionality and the owner explicitly asks to resume deferred acceptance:

- [ ] Read this document, `docs/C06_PERSISTENCE_ACCEPTANCE_AUDIT.md`, and `docs/C06_BACKUP_RESTORE_ACCEPTANCE.md` on current `main`.
- [ ] Verify that the prepared runner/script still build and operate with the current SDK/toolchain/Android versions; review destructive-operation guards.
- [ ] Execute C-DEF-01 and save real backup/restore evidence.
- [ ] Execute C-DEF-02 on a supported genuine D2D path, or record the specific environmental blocker.
- [ ] Execute C-DEF-03 on a **physical Android 8.1 tablet**.
- [ ] Assess C-DEF-04 only for mechanisms actually supported or planned.
- [ ] Review future DataStore keys for backup eligibility and reconcile new product storage paths.
- [ ] Resolve defects with focused regression tests, run mandatory CI, update the acceptance report and obtain owner sign-off before claiming these checks PASS.

## 8. Reference documents

- `AGENTS.md`
- `docs/CHANGE_CONTROL.md`
- `docs/DEFINITION_OF_DONE.md`
- `docs/TESTING_POLICY.md`
- `docs/C05_PERSISTENCE_VALIDATION.md`
- `docs/C05_INFRA_PERSISTENCE_COVERAGE.md`
- `docs/C06_PERSISTENCE_ACCEPTANCE_AUDIT.md`
- `docs/C06_BACKUP_RESTORE_ACCEPTANCE.md` (prepared; to be integrated)
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

**Owner's intent:** Keep the delivered Phase C implementation and all prepared follow-up tooling in `main`, without maintaining a parallel development branch. Defer actual device/transport validation until the product is sufficiently developed; do not erase the open evidence requirements or claim that deferred tests passed.
