# K-M4 regression and scalability evidence — 2026-11-02

## Scope and attribution

Base: `2820376276804f5a8cc74d9e42b4cbd9d6df458e`, branch
`feat/library-advanced-k`. K-M4 adds tests and this document only. No production
correction was justified; D1–D6, shared contracts, fixtures and gates are unchanged.
Execution used the base plus the K-M4 test additions committed with this document.
The resulting commit's JVM test source is the measured source; no historical run
is relabeled as a new execution.

Owner-provided baseline evidence: manually dispatched run `41454053321` at the
base passed build, Lint, 650 JVM tests, 442 API26 tests, localization, 15 fixtures
and quality gate. Its Python summary reports 492 executed and one skipped;
compatibility was SKIPPED. These statements are owner-supplied, not independently
retrieved CI artifacts and not results for K-M4. Owner-reported macOS/manual
K-M3 usability is likewise historical evidence. No CI access was attempted.

## Regression inventory and reuse

Names below are method names within the stated Library suites. Existing tests
remain enabled; new Android scenarios have compilation evidence only.

| Requirement | Existing evidence reused | Focused K-M4 addition |
|---|---|---|
| R01 search | LibraryProjectionTest: caseAccentsCanonicalFormsAndEnyeMatchWithoutChangingNames; matchingAndOrderingDoNotUseJvmDefaultLocale; outerWhitespaceIsTrimmedButInternalWhitespaceAndPunctuationAreLiteral; emptyAndWhitespaceQueriesRetainUnnamedRowsButTextDoesNotMatchPlaceholdersOrUris. LibraryScreenTest: searchClearsAccentsWithoutChangingDisplayedNamesOrDispatching | Mixed load matrix repeatedly changes queries against each source size; failedRefreshRetryAndParentOrRootChangesKeepTruthfulControlLifetime supplies new source snapshots |
| R02 filter/sort | LibraryProjectionTest: allFiveFiltersCombineWithSearchAndBothSorts; ascendingExactlyMatchesExistingListingPreparationIncludingNullBlankAndCaseTies; duplicateNamesAndNullTiesAreIndependentOfInputOrderInBothSections | mixedLoadMatrixHasExactMembershipStableOrderingAndNoRetainedResults tests independent expected memberships for all five filters/both sorts, multiple providers, null/duplicate Unicode names |
| R03 observations | LibraryFavoritesTest and LibraryRegistrationTest cover actual controller observation/write/reconciliation contracts | authoritativeTransitionSequenceNeverKeepsStaleMembershipOrConfirmsDesiredFavorite tests lookup, registration, metadata, pending/reconciling/failure and recovery sequences; old projections remain immutable |
| R04 listing | LibraryControllerTest: completeAndIncompleteEmptyAndNonemptyListingsRemainDistinct; listingFailureHasNarrowRetryAndKeepsRoot. LibraryScreenTest: completeEmptyAndEmptyPartialResultsHaveDifferentMessages; partialRowsAndLoadingRemainExplicit | Failed/loading/partial-recovered UI source snapshots preserve query and contextual truth |
| R05 navigation/lifetime | LibraryConcurrencyTest stale generations/navigation; LibraryScreenStateRestorationTest's three saveable-state tests; LibraryScreenTest refresh/canceled-selection/child/leave tests | Explicit parent and replacement-root snapshots reset controls; failed refresh/loading/recovery preserves them |
| R06 lazy identity | K-M3 corrected ReaderHostNativeIntegrationTest lazy action helper; original typed-action and duplicate-key JVM regressions | moderatelyLargeDuplicateNameListingKeepsOffscreenActionsBoundToOriginalKeys (150 rows); duplicateNamesRetainDistinctRegisterAndFavoriteKeysAfterSorting |
| R07 safety | LibraryOpeningTest: staleAndDirectoryActionsAreRejectedWithoutHostOrPersistenceCalls; LibraryFavoritesTest uncertainty cases; LibraryScreenTest providerUnavailableAndIoErrorsAreNotReportedAsDeletion, unknownNamesNeverFallBackToProviderLocations, allUncertainFavoriteStatesKeepTheirExistingStatusInUnresolvedSection | New load/transition assertions retain original rows/keys and disjoint sections; no new storage orchestration |

No extra provider/registry/write calls are already asserted by
LibraryProjectionTest.largeListingAndRapidQueriesDoNotCauseCatalogRegistryOrWriteOperations
(2,000 rows, 80 operations). That executed test is reused, rather than duplicated.
The new load matrix contains 150 query/filter/sort combinations across 100,
1,000 and 5,000 rows, also reprojects reversed inputs and checks exact identity
sets, deterministic ordering, immutable source keys, disjoint sections and final
empty results. UI lifecycle snapshots are synthetic observations, not actual
provider failures or real host navigation. Existing controller/host suites cover
the corresponding contracts; no shared host tests were edited.

## Descriptive JVM timings

Linux x86_64 shared cloud host, JDK 25.0.4.1, Gradle 9.8.0, AGP 9.5.0.
LibraryAdvancedRegressionTest.projectionTimingObservationsHaveWarmupAndNoWallClockAcceptanceThreshold
uses System.nanoTime around a batch of ten projections: text `aria`, five filters
and both sorts. One initial correctness batch and three explicit warm-up batches
are unmeasured; nine batches are measured. Median is sorted observation five;
p95 uses nearest rank (ceil(0.95 * 9)), observation nine. Times include allocation
and sorting of both result sections, but exclude the correctness assertion after
the timer. Tests assert batch result counts, never elapsed-time limits.

| Rows | Median batch ms | p95 batch ms |
|---:|---:|---:|
| 100 | 0.898374 | 21.919174 |
| 1,000 | 12.550174 | 34.350300 |
| 5,000 | 66.035048 | 92.313637 |

These are ten-query batch timings, not per-query or Android render timings.
GC, JIT compilation, CPU scheduling and concurrent build/Lint effects were not
controlled; warm-up does not eliminate them. Nine observations give a coarse tail
estimate. No numerical budget or matched historical comparison exists, so these
numbers establish neither a performance regression nor performance acceptance.
No benchmark dependency, plugin, runner or CI timing gate was introduced.

## Source-level memory/lifecycle review

LibraryScreen memoizes projection by Ready reference/query. Query changes do not
call the controller. Its key map retains only current observed keys after a Ready
replacement; while loading, at most the last observed map remains until the next
Ready or composition disposal. Values are ordinals and keys, not cached row/bitmap
objects. The generation-aware presentation keys contain no raw provider values.
Query/saved-state ownership remains screen-local. LibraryController.replacePdf
still copies/maps authoritative rows for metadata updates: a scaling consideration,
not a demonstrated defect. No production optimization was justified.

This review and finite projection tests do not measure Android heap/PSS, prove
absence of leaks, or establish prolonged lifecycle stability. No physical tablet,
UI latency budget, macrobenchmark or M4/M5 Phase J campaign was rerun.

## Validation and reproduction

Using the existing toolchain activation in the local execution environment:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
python3 -B -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

- Full JVM execution: **653 PASS**, zero failures/errors/skips; three new K-M4 methods PASS.
- Python: **492 PASS**, zero failures/skips in this local execution.
- Localization validator: **PASS**; resources unchanged.
- Canonical PDF verifier: **15 PASS**, structural/render checks included.
- Debug APK, instrumentation APK compilation, Lint: **PASS**.
- Diff/scope/whitespace: **PASS**.
- Android runtime/API26: **BLOCKED / NOT RUN**; `/dev/kvm` absent and no authorized runnable emulator. No device was adopted or operated.
- API27/API36 and physical-tablet acceptance: **NOT RUN** for K-M4.

JaCoCo 0.8.14 reports were recalculated from this JVM execution using the existing
external feature-scope init-script approach (no repository Gradle/gate edits):
projection **54/54 lines and 47/47 branches (100% / 100%)**; projection plus
LibraryPresentation **84/84 lines and 83/83 branches (100% / 100%)**. These are
scoped JVM counters, not Compose, whole-Library or Android coverage. No new
production business logic was added. External report tasks retained the existing
`kM2ProjectionVerify`/`kM2ChangedLogicVerify` names; task naming is not historical
execution attribution.

Local artifacts: `/tmp/k-m4-gradle.log`, `/tmp/k-m4-final-gradle.log`,
`/tmp/k-m4-python.log`, `/tmp/k-m4-fixtures.log`, `/tmp/k-m4-coverage.log`,
`/tmp/k-m4-coverage/{projection,changedLogic}.xml`; JUnit XML is under
`app/build/test-results/testDebugUnitTest/`. Timing output is the new suite XML's
system-out. These generated artifacts are not committed.

## Remaining acceptance

The LibraryScreenTest inventory is 40 methods; the independent restoration suite
has three. Three new Compose tests compile but are NOT RUN here. Owner-authorized
API26 execution and current-source CI must verify these and the unchanged Reader
regressions. Compose saveable-state emulation is distinct from real Activity,
process-death and host-destination restoration. Physical API27/tablet usability,
TalkBack, linguistic and other policy acceptance remain separate obligations.
No full K-M4/Phase K acceptance, K-M5 integration work or publication is implied.
