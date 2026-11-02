# Phase K acceptance ledger — 2026-11-02

Implementation reviewed: `089eab7f12102059e6decb22219aabca47358186`.
Original baseline: `7f00a793a06e988859dbefc3e21814c21ec727dd`.
See [K-M6 audit](K_M6_FINAL_AUDIT.md) and [K-M4 evidence](K_M4_REGRESSION_EVIDENCE.md).

## D1–D6 acceptance mapping

All six decisions are implemented. Test existence, local execution, owner-reported
CI and formal owner acceptance are distinct categories.

| Decision | Implementation / test mapping | Current evidence and remaining acceptance |
|---|---|---|
| D1 local observed-name search | LibraryProjectionTest Unicode, locale, whitespace, punctuation, unnamed and URI-rejection cases; LibraryScreenTest searchClearsAccentsWithoutChangingDisplayedNamesOrDispatching; large-list no-I/O regression | Local JVM PASS; owner-supplied exact-SHA API26 PASS. Linguistic/manual device review still applicable. |
| D2 five exclusive combined filters | allFiveFiltersCombineWithSearchAndBothSorts; fiveFiltersAreExclusiveAndPreserveSearch; mixedLoadMatrixHasExactMembershipStableOrderingAndNoRetainedResults | Local JVM and owner-supplied API26 PASS. No global search or persistence implemented. |
| D3 confirmed/unresolved truth | favoritesRequireReadyTrueAndIdleAndNeverDesiredOrFailedValues; authoritativeTransitionSequenceNeverKeepsStaleMembershipOrConfirmsDesiredFavorite; allUncertainFavoriteStatesKeepTheirExistingStatusInUnresolvedSection; unchanged registration/favorite suites | Local JVM and owner-supplied API26 PASS. Synthetic failure tests are not real external-provider acceptance. |
| D4 deterministic folders-first sorts | ascendingExactlyMatchesExistingListingPreparationIncludingNullBlankAndCaseTies; descendingKeepsFoldersFirstAndNullLastWithinEachGroup; duplicateNamesAndNullTiesAreIndependentOfInputOrderInBothSections; duplicateNamesRetainDistinctRegisterAndFavoriteKeysAfterSorting | Local JVM and owner-supplied API26 PASS. No size/date sort. |
| D5 local state lifetime | savedControlsRestoreOnlyWithinSameFolderScope; savedControlsFromOldFolderAreRejectedDuringRestorationIntoNewFolder; newLoadGenerationPreservesAllControlsAndStillRestoresThem; refresh/cancel/leave/parent/root screen cases | Owner-supplied API26 PASS covers these methods at exact SHA. Static host review supports composition disposal. Real Activity rotation, host round-trip controls and process recovery remain separate evidence, not inferred from StateRestorationTester. |
| D6 excluded features | Cumulative diff/import/API review, unchanged storage/persistence/host contracts, projection no-I/O regression | No multiselection, new write operations, persistent search defaults or cross-phase implementation. No changes required. |

K-M1–K-M4 technical work is implemented and supported by the evidence above.
K-M5 is **DEFERRED / NOT VERIFIED**. K-M6 documents readiness for owner review.
No full Phase K formal acceptance, integration acceptance or merge readiness is
recorded by this ledger. Existing approval to continue or publish milestones is
not final phase acceptance.

## Deferred K-M5 integration register

L currently has a Reader UX design contract, not new functional implementation;
M remains at its original main baseline without Phase M implementation. These
are owner-supplied status facts; no branch was modified or integrated by K-M6.

| Boundary | Statically checkable now | Deferred implementation-dependent evidence / coordination |
|---|---|---|
| Library → Reader opening and route lifetime | Existing Open/RetryOpen retain original observed key; confirmed Registered uses DocumentRef, Unregistered uses transient StorageDocumentRef. Host owns route acceptance/revision and a single Reader route. | Revalidate L implementation's ownership, rejection/retry, asynchronous completion, cleanup and registered/transient semantics. Owner-controlled host integration after L is available. |
| Returning Reader → Library | Current host refreshes existing Library and conditionally remounts its screen; local controls reset on leaving. Existing K tests and pre-L host/Reader regressions exist. | Actual L back/lifecycle transitions, presentation disposal, refresh generations and saved controls must be tested on an approved combined revision. |
| Registration/metadata ownership | K consumes existing registry confirmation and metadata observations; favorite pending/reconciliation and registered-only reading position ownership remain unchanged. | Coordinate L checkpoints and any M publication/reconciliation; verify favorite/position preservation and logical identity. No alternate registry or speculative API. |
| PDF writing/editing | K introduces no destination writes, WRITE grants, editing engine, copy/move/rename/delete or source mutations. | M must first supply approved capability, revision, publication, failure/cancellation and reconciliation evidence. K must not expose mutation UI before owner-authorized contracts and implementation exist. |
| SAF/identity | Folder/storage locations remain opaque READ capabilities; no URI-derived names or inferred deletion. Existing controller/host stale guards remain authoritative. | Verify real provider grants, relocated/replaced identities, unavailable media and any explicitly approved WRITE authority. Reading does not authorize writing; matching names/bytes do not establish identity. |
| Parallel source integration | K production diff stays in Library plus its resources; original public LibraryScreen signature and shared models are unchanged. | K's approved Reader instrumentation helper exception can textually conflict with L test changes. Localization resource-count assertion can conflict with other features adding strings. Review actual diffs and combined test inventories later; do not cherry-pick or pre-install shared APIs now. |

Every row remains **DEFERRED / NOT VERIFIED** for future L/M compatibility.
No conflict with an unavailable implementation can be declared resolved. Complete
L/M work → owner-approved contract review → controlled host integration → combined
current-revision regression/compatibility evidence → explicit acceptance.

## Outstanding acceptance and evidence ledger

| Item | Classification / next evidence |
|---|---|
| K-M5 cross-feature integration | DEFERRED BY OWNER / NOT VERIFIED; not completed or PASS. Revisit only after L/M implementation and separate authorization. |
| API27 and recent API36 compatibility | VERIFIED AT PUBLISHED K-M6 SHA: owner-triggered run `41462058074` executed 445 instrumentation tests on API27 and 445 on API36, all PASS; API26 also 445 PASS. Re-evaluate against a future combined/integration revision. |
| Physical Android 8.1 tablet | REQUIRED BEFORE FORMAL ACCEPTANCE; device/API/revision and per-scenario UI results missing. General Android manual success is insufficient. |
| Real Activity rotation, host transitions and actual process recovery | EVIDENCE GAP; separate current-source device evidence where applicable. Compose emulation and synthetic reconstruction are not actual OS process death. No worker killing or destructive recovery is authorized here. |
| TalkBack, focus/keyboard, large-font/contrast/orientation and human es/ca/en review | REQUIRED UI ACCEPTANCE; automated semantics/resource checks do not replace manual/device review. Preserve J08 full audit deferral to Phase R without waiving K control accessibility. |
| Actual external SAF/provider behavior | PENDING applicable acceptance; injected/synthetic tests do not prove real authorization, revocation or unavailable-provider journeys. |
| Performance / memory | Descriptive JVM observations only; no approved numerical K budget or physical calibration. No claim of leak-free operation, measured Android memory or proven performance regression. |
| CI Python skip | EXPLAINED / NOT EXECUTED: `test_offline_coverage.RealHostReportSmokeTests.test_real_jacoco_report_and_identity_reader_without_application_build` conditionally skips when host `java`/`javac` or cached JaCoCo report libraries are missing. CI log: `Existing host JDK/JaCoCo report libraries unavailable; no downloads`. Python: 492 executed, 1 skipped, 0 failed. This is not PASS for that individual smoke test or a waiver of mandatory policy; local K-M4 had no skip. |
| K-M6 published revision | CI VERIFIED at documentation SHA `23624a1c3c087e545da68243a1a42875f48f7caf` in run `41462058074`. This follow-up evidence-only documentation amendment is not separately CI-verified. Any later PR/integration requires checks at its actual revision. |
| Human approval / merge | NOT GRANTED by this task. No PR or merge; only owner may authorize integration after applicable gates and acceptance. |

Latest independently checked owner-triggered CI:
[run 41462058074](https://github.com/alexbonavila/PartiturasPDF/actions/runs/41462058074),
at published K-M6 SHA `23624a1c3c087e545da68243a1a42875f48f7caf`,
completed SUCCESS: 653 JVM PASS; 445 Android instrumentation PASS on **each**
of API26, API27 and API36; build, Lint, localization, 15 PDF fixtures and
quality gate PASS. Python: 492 executed, 0 failures, 1 explained conditional
skip (see above). The source implementation remains unchanged from the
previously audited SHA. This follow-up amendment itself is documentation-only
and has no distinct CI run. General manual K-M3 success is owner-reported
without physical Android 8.1 or per-scenario attribution.

Standalone K-M1–K-M4 implementation and K-M6 technical audit are closed for
owner review with the above limits. K-M5 remains **DEFERRED / NOT VERIFIED**;
formal whole-Phase-K acceptance, integration and merge readiness are pending.
No mandatory policy requirement is waived by this documentary closeout.
