# Phase J — M3 technical closeout

Date: **2026-10-23**. Repository: `alexbonavila/PartiturasPDF`.
Branch: `test/mvp-stabilization-j` (**unmerged**).

**M3 technical verification is completed for the selected compatibility,
locale/offline/local-delivery and A1/B5/C05 coverage scopes documented below.**
The final coverage outcome is recorded from the owner's supplied verified findings;
the campaign archive is unavailable in this checkout. This is not independent raw
artifact certification, formal owner acceptance, Phase J completion or integration
into `main`. Owner review and formal acceptance remain **PENDING**.

## Scope and revision attribution

| Reference | Exact revision |
| --- | --- |
| Original Phase J main baseline, retained in the plan | `37f51baf8b34b069a0c83e93b5780a6c9f676c16` |
| M3-A1 mapping starting revision | `d4b276d565a96122086a1af86c43699cbaf6caba` |
| M3-A2 compatibility execution | `a9905d497979520cfabf59e038b51f0ed453a8a3` |
| M3-B2-B API26 execution | `fe79cf269e6efbe30bd98a030cc945eb010bc180` |
| M3-B2-B API27/36; M3-B2-C/D source | `6b2294b715abbfd8787c0878caf8d95116fa4e35` |
| M3-C1 audited source | `8453743eaef05595a43537a685338d79be473dd2` |
| M3-C2 retained producer for offline recovery | `ad08aeeb28526fa6e57195f35c32d351b9ee4692` |
| M3-C3 implementation starting revision | `25786f35d560a1d843ea25eb87022149d25e99b6` |
| Final implementation and new coverage execution | **`b2cff985ee3d9204c3d46728636afdb329d1cc22`** |

This closeout changes documentation only. It does not reattribute earlier runs to
the final source or substitute its eventual documentation revision for an execution
revision. Original main/MVP release and hosted CI records retain their own SHAs.

## M3-A — selected API compatibility

The [compatibility matrix](J_M3_A_COMPATIBILITY_MATRIX.md) defines seventeen J07
methods plus separate identity smoke. The [independent evidence review](J_M3_A2_ANDROID_COMPATIBILITY_EVIDENCE.md)
records **18/18 PASS on each API26/27/36**, **54 completed executions** of eighteen
distinct methods, zero failures/errors/skips, valid completion and ADB exit 0.
That earlier review verified thirteen payload checksums and matching recorded source
digest and installed/host APK hash comparisons; those checks were not rerun here.
No installed APK binary was retained, so independent installed-binary attestation
is unavailable. API26 uninstall instructions and acknowledged owner execution are
recorded, but separately archived approval and proof of prior-data disposability
remain absent; those limitations do not invalidate the reviewed JUnit outcomes.

Production UI/native routes coexist with injected local capabilities, test providers
and simulated grant/result failures. This establishes the selected compatibility
scope at its recorded source, not universal compatibility, physical Android 8.1
acceptance, every external provider or compatibility of the later C3 exporter on
API27/36. Historical CI results are not a new final-branch CI run.

## M3-B — selected locale, offline and local AAB delivery

The [locale/offline matrix](J_M3_B_LOCALE_OFFLINE_MATRIX.md) remains the descriptive
scope register; its original catalogue/NOT RUN projections are unchanged.

| Workstream | Recorded selected completion | Evidence boundaries retained |
| --- | --- | --- |
| [B2-A J09](J_M3_B2A_J09_ANDROID_EVIDENCE.md) | Eight methods plus smoke, 9/9 per API26/27/36; 27 executions under original en-US settings | Earlier independent review; endpoint settings are not continuous monitoring or actual language changes |
| [B2-B manual locale](J_M3_B2B_ANDROID_EVIDENCE.md) | Owner-reported 12/12 configurations and 36/36 primary JUnit executions across API26/27/36; baseline and exact original-order restoration reported PASS per API | Separate per-API runs; French-first/English-second prefix with Spanish app fallback, not an invented exact two-language-only list; no live Activity transition or exhaustive linguistic acceptance |
| [B2-C offline](J_M3_B2C_ANDROID_OFFLINE_EVIDENCE.md) | Owner-reported 15/15 primary JUnit executions, passing before/after checks and 5/5/12 during-test samples; separate later radio restoration PASS | Supplied projection was independently checked earlier; original raw logs unavailable; sampled observations are not continuous packet-level isolation or an exhaustive locale/offline cross-product |
| [B2-D AAB delivery](J_M3_B2D_AAB_LANGUAGE_DELIVERY_EVIDENCE.md) | Owner-reported local bundletool debug AAB/device-specific APK generation and installation on API26/27/36; offline es/ca/en Library UI observed on API26 | No verified owner-AAB binary link to the separate locally inspected bundle; overwritten screenshot names remain ambiguous; exact final radio/language-order restoration independently NOT VERIFIED |

B2-D does **not** prove Google Play delivery, release distribution, offline language
switching on API27/36, all-screen/error linguistic review or a live locale transition.
Its restoration gap is not filled using B2-C's different session. These selected
technical completions preserve their owner-reported or independently reviewed levels;
no single global all-dimensions M3-B PASS is fabricated.

## M3-C chronology and resolution

1. [C1 audit](J_M3_C1_COVERAGE_AUDIT.md): existing infrastructure and evidence were
   inspected without a new campaign. Missing producer provenance, an invalid retained
   Android error payload and absent worker exports prevented current coverage
   certification. Historical feature XML counters did not repair attribution.
2. C2 compatibility corrections reused existing runners/readers: `40fb0c0` separated
   standard tests from diagnostics; `ad08aee` accepted only known coverage progress
   events; `57af07b` authenticated generated AIDL class IDs/probe counts without adding
   generated classes to handwritten denominators. Earlier producer runs remain BLOCKED.
3. [C2-C offline recovery](J_M3_C2C_OFFLINE_COVERAGE.md), delivered at `25786f3`,
   preserved frozen producer evidence and separate analyzer identity. It was not a
   new Android execution. The subsequently reported authenticated B5 baseline was
   **531/658 lines and 295/452 branches**, below the existing gates; app-process
   data could not establish isolated-worker execution. Its implementation-time
   NOT MEASURED statements and the original producer BLOCKED result remain historical.
4. [C3 worker collection and behavioral tests](J_M3_C3_WORKER_COVERAGE.md), delivered
   at `b2cff985ee3d9204c3d46728636afdb329d1cc22`, added genuine debug-only worker
   exports and thirteen ordinary Android regressions. The new owner-operated API26
   campaign below reports all existing scoped gates PASS. This closes the selected
   M3-C technical coverage gap without rewriting earlier failed or blocked evidence.

The debug-only, non-exported isolated service inherits production worker behavior.
Each test-owned worker snapshots its actual JaCoCo runtime before termination into
an app-supplied fresh regular-file descriptor, capped at 4 MiB. PID, isolated UID,
caller identity, start time, epoch, nonce, session, hashes, class IDs and probe counts
are checked through the existing collectors/validators. Capture never delays or
changes production retirement; release has no exporter. The original nineteen
engine tests, diagnostic exclusions and A1/B5/C05 thresholds are preserved.

## Final API26 campaign — owner-provided verified findings

**Evidence level for every result in this section: supplied verified owner findings,
not artifacts independently inspected during this documentary task.**

Command executed by the owner: `python3 scripts/local_e2e.py run --suite coverage`.
Original run directory:
`build/local-e2e/reports/20261023T112313Z-ebebb92a62a248afa70d6f412f03c464/`.
Archive: `20261023T112313Z-ebebb92a62a248afa70d6f412f03c464.zip`.
Owner-supplied archive SHA-256:

`612458cb1166a347782d7ed7dc4082b965ce1fe74a215481d09143e80ca8b47b`

Neither the named archive nor run directory was found in the accessible workspace
or attachments. Consequently no ZIP extraction, checksum comparison, raw JUnit/log
parse, binary rehash or per-snapshot revalidation was performed here. The checksum
is copied from the authoritative supplied findings, **not independently recomputed**.
The owner reports inspection of the complete archive containing runner/coverage
summaries, JUnit, JaCoCo reports, class inventories, binary inputs and worker evidence.
Retain that archive privately; no raw ZIP, APK, binary input, screenshot or private
log is included in this documentation change.

| Campaign dimension | Supplied result |
| --- | --- |
| Source | `b2cff985ee3d9204c3d46728636afdb329d1cc22` |
| Runner / exit | PASS / 0 |
| Environment | Android API26, arm64-v8a, dedicated owned emulator |
| JVM | 631/631 PASS |
| Standard Android | 431/431 PASS |
| Total executed tests | **1,062** (631 + 431) |
| Worker snapshots | **17, VALIDATED**, separate from test count |
| Coverage | VERIFIED |
| Failures / blockers | 0 / 0 |
| Cleanup | PASS |

The final source inventory is 445 Android methods: 431 standard and fourteen
fresh-process diagnostics. The fourteen diagnostics and their 29 controlled
invocations are excluded and **NOT RUN in this campaign**, never counted as PASS.
The seventeen worker captures correspond to the six direct-worker tests' lifecycle
counts **1 + 1 + 6 + 6 + 1 + 2**. They are not seventeen extra JUnit tests.
Thirteen added ordinary tests extend the unchanged original isolation tests.
The supplied findings report exact inventory/snapshot validation; raw identities
cannot be independently re-enumerated without the archive. No source digest, APK
checksum, device serial/boot identity or unprovided per-source coverage is invented.

### Final scoped coverage

All results below are supplied for that exact campaign. Gates remain **≥90% LINE /
≥80% BRANCH**; C05 retains its existing package and per-source gates.

| Component | Covered lines | Line coverage | Covered branches | Branch coverage | Result |
| --- | --- | --- | --- | --- | --- |
| A1 contracts | 230/231 | 99.57% | 222/224 | 99.11% | PASS |
| B5 PDF adapter | 623/658 | 94.68% | 375/452 | 82.96% | PASS |
| C05 metadata | 207/210 | 98.57% | 167/177 | 94.35% | PASS |
| C05 preferences | 35/35 | 100.00% | 12/12 | 100.00% | PASS |

B5's denominator remains **658 lines / 452 branches**, including the worker.
The reported gain is 92 covered lines and 80 covered branches over its earlier
531/295 baseline; no exclusion or threshold reduction supplied the PASS.
There remain **35 missed B5 lines and 77 missed branches**. No claim of complete
source coverage, post-retirement measurement, native C/C++ Pdfium coverage or a
production-wide/independent F/G/H business-logic gate follows from this table.
Worker evidence is genuine recorded isolated-process data according to the supplied
validation, with the [C3 provenance limitations](J_M3_C3_WORKER_COVERAGE.md) retained:
source/APK-bound recorded evidence is not cryptographic OS/installed-binary attestation.

## Preserved histories and outstanding owner acceptance

- M2's initial incomplete run, M3-A1/B1 mapping NOT RUN baselines and historical
  host-only catalogue projections are unchanged.
- B2-B's API26 global run remains BLOCKED despite its valid selected API26 results;
  API27/API36 retain their separate PASS records and earlier restoration blockers.
- B2-C's original global run and initial API26/API27 restoration remain BLOCKED;
  later restoration is separate evidence. B2-D restoration uncertainty remains open.
- C1 unavailable/unverified evidence, earlier C2 BLOCKED producers, the below-gate
  post-hoc B5 baseline and C3 implementation-time NOT RUN records remain unchanged.
  New evidence supersedes current technical readiness, not historical classifications.
- Genuine external SAF/provider acceptance, unexecuted controlled diagnostics and
  unestablished actual OS process-death/deeper recovery acceptance remain open where
  not demonstrated by their own scenario-specific evidence. Standard worker-death
  tests do not certify every operational recovery scenario.
- Physical Android 8.1/tablet, TalkBack/accessibility, full linguistic/error-screen
  review, live locale transitions, actual backup/restore/D2D, Play/release delivery,
  API27/36 AAB offline language switching and B2-D exact-restoration proof remain open.

**J08 remains DEFERRED TO PHASE R.** M4–M7 and J14/J15 final consolidation/formal
Phase J acceptance are not complete. No new hosted CI or merge-readiness result is
asserted; the Phase J branch remains unmerged. Numerical gate attainment is not an
acceptance waiver and does not close the obligations above.

Next planned milestone: **M4 — memory and performance** (J03/J04 in the existing
[Phase J plan](J_PHASE_PLAN.md)). Its scope and approval boundaries are unchanged.
No benchmark, resource measurement, device action or M4 implementation is authorized
or started by this closeout. The owner must review these documents and separately
authorize subsequent work; final M3 owner acceptance remains **PENDING**.
