# C05 persistence coverage and regression audit

Date: 2026-10-14 UTC. Baseline and ending HEAD: `6a596c8ab65aa7052e33d5104590ac17930e1c50`.
Local checkout `work`; review target `feat/data-persistence-c`, existing PR #10.
Local and remote matched the required baseline; no synchronization mutation was needed.
Repository-local identity: alexbonavila <alexbonavila44@gmail.com>.
No commit, staging, push, PR creation, merge or C06 work.

## Outcome and acceptance

C05 is delivered for review, **not accepted as complete persistence validation**.
Full API 26 coverage is BLOCKED: the Actions API returned HTTP 403, the host lacks `/dev/kvm`,
and the only local `.ec` is invalid JaCoCo execution data. No emulator was launched.
The owner's statement that baseline CI passed 200 Android and 128 JVM tests is recorded as
owner-supplied evidence, not an independently downloaded XML/coverage measurement.
The three added Android regression tests are compiled but NOT RUN.
No confirmed production defect was found by this source/test audit. That is not proof of
absence of runtime defects or coverage adequacy.

## Coverage method and inclusion policy

JaCoCo 0.8.14, AGP 9.4.1, Gradle 9.8.0, daemon JDK 25, SDK 37,
Room 2.8.5, DataStore 1.2.1; existing dependencies and quality rules unchanged.
External diagnostic init script: `/workspace/partituras-tools/c05-coverage.init.gradle`.
Reports use the actual AGP Kotlin debug classes at
`app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes` and the
JVM `app/build/jacoco/testDebugUnitTest.exec`.
Every class in `data/metadata/**` and `data/preferences/**` is included, including companion,
entity/relation and coroutine/lambda classes. Only Room/KSP `*_Impl*` is excluded: its generated
SQL materialization has no handwritten source. Third-party dependencies are outside the two
package scopes. No handwritten error handling, lifecycle helper or difficult branch is excluded.
JaCoCo's built-in Kotlin compiler filters apply; there are no custom coroutine exclusions.
Generated enum/string-dispatch branches that remain in the report are retained, including the
five missed bytecode branches on MetadataMapping.kt line 18 despite testing all availability keys.
Abstract DAO SQL declarations have no executable handwritten bytecode: integration assertions
verify their real generated SQL, while the handwritten DAO wrappers remain in the denominator.

The tables below are **JVM-only actual measurements**, not combined Android coverage. A zero
means no JVM execution in this scope, not proof that the reported CI Android suite missed it.
Full combined counts remain pending. Source-group LINE counts deduplicate shared source lines;
class counts may share source lines and must not be added to reproduce source-group totals.
N/A branches means no reportable branch, not an automatic coverage pass.


## Scope A: Room — JVM-only inventory

| Source group | Executable lines | Covered | Missed | Line % | Branches | Covered | Missed | Branch % | Evidence |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| DocumentDao.kt | 17 | 0 | 17 | 0.00% | 14 | 0 | 14 | 0.00% | JVM XML |
| MetadataDatabase.kt | 5 | 0 | 5 | 0.00% | 4 | 0 | 4 | 0.00% | JVM XML |
| MetadataEntities.kt | 16 | 16 | 0 | 100.00% | 0 | 0 | 0 | N/A | JVM XML |
| MetadataMapping.kt | 50 | 50 | 0 | 100.00% | 93 | 88 | 5 | 94.62% | JVM XML |
| MetadataReadingPositionPolicy.kt | 5 | 5 | 0 | 100.00% | 14 | 14 | 0 | 100.00% | JVM XML |
| RegisteredMetadata.kt | 3 | 0 | 3 | 0.00% | 0 | 0 | 0 | N/A | JVM XML |
| RegistryDatabaseErrors.kt | 21 | 19 | 2 | 90.48% | 6 | 3 | 3 | 50.00% | JVM XML |
| RoomDocumentMetadataRepository.kt | 38 | 0 | 38 | 0.00% | 18 | 0 | 18 | 0.00% | JVM XML |
| RoomDocumentRegistry.kt | 55 | 0 | 55 | 0.00% | 28 | 0 | 28 | 0.00% | JVM XML |
| **Package total** | 210 | 90 | 120 | 42.86% | 177 | 105 | 72 | 59.32% | JVM XML |

### Class-level detail (all included classes)

| Class | Lines | Covered | Missed | Line % | Branches | Covered | Missed | Branch % |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `DocumentDao` | 17 | 0 | 17 | 0.00% | 14 | 0 | 14 | 0.00% |
| `DocumentDao$register$1` | 0 | 0 | 0 | N/A | 0 | 0 | 0 | N/A |
| `MetadataDatabase` | 2 | 0 | 2 | 0.00% | 0 | 0 | 0 | N/A |
| `MetadataDatabase$Companion` | 3 | 0 | 3 | 0.00% | 4 | 0 | 4 | 0.00% |
| `MetadataEntity` | 8 | 8 | 0 | 100.00% | 0 | 0 | 0 | N/A |
| `MetadataMapping` | 50 | 50 | 0 | 100.00% | 93 | 88 | 5 | 94.62% |
| `MetadataReadingPositionPolicyKt` | 5 | 5 | 0 | 100.00% | 14 | 14 | 0 | 100.00% |
| `RegisteredMetadata` | 3 | 0 | 3 | 0.00% | 0 | 0 | 0 | N/A |
| `RegistryDatabaseErrorsKt` | 21 | 19 | 2 | 90.48% | 6 | 3 | 3 | 50.00% |
| `RegistryDatabaseErrorsKt$metadataDatabaseOperation$1` | 0 | 0 | 0 | N/A | 0 | 0 | 0 | N/A |
| `RegistryEntity` | 8 | 8 | 0 | 100.00% | 0 | 0 | 0 | N/A |
| `RoomDocumentMetadataRepository` | 14 | 0 | 14 | 0.00% | 4 | 0 | 4 | 0.00% |
| `RoomDocumentMetadataRepository$observe$1` | 3 | 0 | 3 | 0.00% | 0 | 0 | 0 | N/A |
| `RoomDocumentMetadataRepository$observe$1$1` | 6 | 0 | 6 | 0.00% | 4 | 0 | 4 | 0.00% |
| `RoomDocumentMetadataRepository$observe$2` | 2 | 0 | 2 | 0.00% | 0 | 0 | 0 | N/A |
| `RoomDocumentMetadataRepository$setFavorite$2` | 2 | 0 | 2 | 0.00% | 0 | 0 | 0 | N/A |
| `RoomDocumentMetadataRepository$setFavorite$2$1` | 4 | 0 | 4 | 0.00% | 6 | 0 | 6 | 0.00% |
| `RoomDocumentMetadataRepository$setReadingPosition$2` | 2 | 0 | 2 | 0.00% | 0 | 0 | 0 | N/A |
| `RoomDocumentMetadataRepository$setReadingPosition$2$1` | 7 | 0 | 7 | 0.00% | 4 | 0 | 4 | 0.00% |
| `RoomDocumentMetadataRepository$validatedRegistration$1` | 0 | 0 | 0 | N/A | 0 | 0 | 0 | N/A |
| `RoomDocumentRegistry` | 19 | 0 | 19 | 0.00% | 6 | 0 | 6 | 0.00% |
| `RoomDocumentRegistry$findRegistration$2` | 4 | 0 | 4 | 0.00% | 2 | 0 | 2 | 0.00% |
| `RoomDocumentRegistry$missingOrChanged$1` | 0 | 0 | 0 | N/A | 0 | 0 | 0 | N/A |
| `RoomDocumentRegistry$persisted$1` | 0 | 0 | 0 | N/A | 0 | 0 | 0 | N/A |
| `RoomDocumentRegistry$rebind$2` | 2 | 0 | 2 | 0.00% | 0 | 0 | 0 | N/A |
| `RoomDocumentRegistry$rebind$2$1` | 13 | 0 | 13 | 0.00% | 10 | 0 | 10 | 0.00% |
| `RoomDocumentRegistry$recordObservation$2` | 2 | 0 | 2 | 0.00% | 0 | 0 | 0 | N/A |
| `RoomDocumentRegistry$recordObservation$2$1` | 7 | 0 | 7 | 0.00% | 4 | 0 | 4 | 0.00% |
| `RoomDocumentRegistry$register$2` | 2 | 0 | 2 | 0.00% | 0 | 0 | 0 | N/A |
| `RoomDocumentRegistry$register$2$1` | 5 | 0 | 5 | 0.00% | 4 | 0 | 4 | 0.00% |
| `RoomDocumentRegistry$resolve$2` | 4 | 0 | 4 | 0.00% | 2 | 0 | 2 | 0.00% |

## Scope B: DataStore — JVM-only inventory

| Source group | Executable lines | Covered | Missed | Line % | Branches | Covered | Missed | Branch % | Evidence |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| ApplicationPreferencesStore.kt | 2 | 0 | 2 | 0.00% | 0 | 0 | 0 | N/A | JVM XML |
| DataStorePreferencesRepository.kt | 13 | 0 | 13 | 0.00% | 0 | 0 | 0 | N/A | JVM XML |
| PreferencesMapping.kt | 16 | 16 | 0 | 100.00% | 8 | 8 | 0 | 100.00% | JVM XML |
| PreferencesStorageErrors.kt | 4 | 4 | 0 | 100.00% | 4 | 4 | 0 | 100.00% | JVM XML |
| **Package total** | 35 | 20 | 15 | 57.14% | 12 | 12 | 0 | 100.00% | JVM XML |

### Class-level detail (all included classes)

| Class | Lines | Covered | Missed | Line % | Branches | Covered | Missed | Branch % |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `ApplicationPreferencesStoreKt` | 2 | 0 | 2 | 0.00% | 0 | 0 | 0 | N/A |
| `DataStorePreferencesRepository` | 10 | 0 | 10 | 0.00% | 0 | 0 | 0 | N/A |
| `DataStorePreferencesRepository$observe$1` | 3 | 0 | 3 | 0.00% | 0 | 0 | 0 | N/A |
| `DataStorePreferencesRepository$observe$1$1` | 1 | 0 | 1 | 0.00% | 0 | 0 | 0 | N/A |
| `DataStorePreferencesRepository$observe$2` | 2 | 0 | 2 | 0.00% | 0 | 0 | 0 | N/A |
| `DataStorePreferencesRepository$update$1` | 0 | 0 | 0 | N/A | 0 | 0 | 0 | N/A |
| `DataStorePreferencesRepository$update$2` | 1 | 0 | 1 | 0.00% | 0 | 0 | 0 | N/A |
| `PreferencesMapping` | 16 | 16 | 0 | 100.00% | 8 | 8 | 0 | 100.00% |
| `PreferencesStorageErrorsKt` | 4 | 4 | 0 | 100.00% | 4 | 4 | 0 | 100.00% |

## Execution-data validity and provenance

The JVM loader read 1,008 execution classes, including 26 persistence records. JaCoCo Analyzer
compared current class identities and found **zero incompatible persistence identities**.
Diagnostic source: `/workspace/partituras-tools/C05CoverageIdentity.java`, using the existing
JaCoCo core 0.8.14 and ASM 9.9 artifacts, no dependency addition.

The old local file at
`app/build/outputs/code_coverage/debugAndroidTest/connected/emulator-5554/coverage_data/coverage.ec`
is 49 bytes, timestamp 2026-10-14 06:13:34 UTC; its adjacent test exit-code file contains `1`.
ExecFileLoader.load rejected it with `IOException: Invalid execution data file`.
It is **not merged or used for any reported percentage**. Its existence/nonzero size is not valid
coverage evidence. GitHub GET `/repos/alexbonavila/PartiturasPDF/actions/runs?branch=feat%2Fdata-persistence-c&per_page=1`
returned HTTP 403 with injected platform authentication; no token was requested or exposed.
The current workflow retains Android XML/HTML but omits `.ec`; the JVM artifact omits `.exec`.
Thus a passed suite alone supplies no retrievable persistence coverage denominator here.

## Requirement-to-test evidence matrix

Android baseline entries below: owner reports execution PASS on baseline; independently retrieved
XML NOT AVAILABLE (403). JVM entries: actual local executed XML PASS, 128 tests. C05 entries:
NOT RUN on Android, APK compilation PASS. Each baseline test remains byte-identical.

| Requirement | Concrete test(s), class abbreviation | Environment / actual evidence | Remaining gap or action |
| --- | --- | --- | --- |
| Atomic defaults and unique identities | MDB.registrationCreatesUnknownObservationsAndDefaultMetadataAtomically; MDB.duplicateLogicalIdAndProviderLocationAbortWithoutReplacingRows | Android baseline, owner PASS | Obtain coverage |
| Rollback when metadata insert fails | MDB.failureOfMetadataInsertRollsBackRegistryInsert; REG.nonUniqueConstraintFailurePropagatesAndRollsBackBothTables | Android baseline, owner PASS | Runtime exception-path coverage |
| FK cascade and metadata PK | MDB.metadataForeignKeyAndPrimaryKeyAreEnforced | Android baseline, owner PASS | Obtain coverage |
| Concurrent registration / same-file instances | REG.concurrentRegistrationOfSameIdHasOneWinnerWithoutOverwrite; REG.concurrentRegistrationAcrossTwoRoomInstancesUsesSqliteUniqueness | Android baseline, owner PASS | Covered assertions, no full coverage evidence |
| Exact opaque lookup / stable identity | REG.lookupUsesExactOpaqueNamespaceAndLocatorOnly; REG.registersCallerIdentityAndBothDurableDefaults | Android baseline, owner PASS | No PDF/URI-derived identity |
| Missing vs conflict priority | REG.missingObservationAndRebindAreNotStaleRegistrationConflicts; META.missingMutationsDoNotCreateMetadataOrMisreportRevisionConflicts | Android baseline, owner PASS | Cross-instance missing/write races not exhaustively tested |
| Rebind uniqueness and historical metadata | REG.occupiedReplacementIsAConflictAndSameIdentityLocationIsAllowed; META.rebindPreservesHistoricalMetadataUntilExplicitClear | Android baseline, owner PASS | Obtain coverage |
| Rebind/observation concurrency | REG.observationRacingRebindIsLinearizableAndNeverRestoresOldEvidence | Android baseline, owner PASS | Further independent-instance races possible |
| Availability/revision persistence | REG.resolvePreservesEveryAvailabilityEvenWhenAccessWasLost; REG.observationsStoreCallerEvidenceAndCanReturnToUnknownWithoutTouchingMetadata | Android baseline, owner PASS | Obtain coverage |
| Favorite partial/idempotent updates | META.favoriteOnlyUpdatesFavoriteAndPreservesPositionAndRegistration | Android baseline, owner PASS | Obtain coverage |
| Exact verified revision type/hash/length | META.verifiedPositionRequiresExactRegistryTypeDigestAndLength; MDB.expectedRevisionComparisonIncludesTypeDigestAndByteCount | Android baseline, owner PASS | Obtain coverage |
| Unknown hints, no promotion | META.unknownPositionOnUnknownRegistryIsOnlyAResumeHint; META.unknownHintOnVerifiedRegistryIsNotPromotedToVerifiedEvidence | Android baseline, owner PASS | Obtain coverage |
| Stale position clear / writer ordering | META.clearingUsesCurrentRegistryRevisionEvenWhenHistoricalPositionIsStale; META.queuedClearUsesTheRevisionCommittedBeforeItsOwnTransaction; META.queuedVerifiedPositionDetectsARevisionCommittedByAnotherWriter | Android baseline, owner PASS | Independent-instance equivalent remains unmeasured |
| Ownership validation | META.foreignRevisionOwnershipPropagatesBeforePersistence; policy.foreignPositionAndRegistryRevisionAreRejectedAsProgrammingErrors | Android owner PASS + JVM actual PASS | Obtain adapter coverage |
| Cold Loading / multiple collectors | META.coldFlowStartsLoadingAndEachCollectorReadsItsOwnCurrentState; META.multipleLiveCollectorsObserveUpdatesAndCancelIndependently | Android baseline, owner PASS | Transaction relation read checked in generated DAO; actual branch coverage pending |
| Raw SQL delete and recovery | META.failedMissingObservationDoesNotRetainStaleReadyAndCanRecover | Android baseline, owner PASS | Explicit refreshAsync regression preserved |
| Room close/recreation | MDB.fileBackedDatabaseRetainsBothIndependentRevisionsAcrossCloseAndReopen; REG.fileBackedReopenRetainsIdentityAvailabilityAndSeparateMetadataRevision; META.fileBackedCloseReopenPersistsFavoritePositionAndClearing | Android baseline, owner PASS | No released migrations yet |
| Registry/metadata corruption not repaired | REG.invalidPersistedRepresentationPropagatesForReadsAndMutations; META.corruptMetadataNeverBecomesReadyOrGetsSilentlyRepairedByWrites; META.missingMetadataForAnExistingRegistrationIsCorruptionNotNotRegistered | Android baseline, owner PASS | Obtain coverage |
| Room operational failure boundary | REG.actualCantOpenDatabaseFailureMapsSafelyForAllFiveMethods; META.operationalFailureStartsLoadingThenFailedAndIsTypedForBothMutations | Android baseline, owner PASS | All eight classified SQLite subclasses only type-tested on JVM, not all induced on device |
| Unexpected SQL errors | REG.unexpectedSqlDefectPropagatesRatherThanClaimingStorageUnavailable; C05.metadataSqlDefectsPropagateThroughObservationAndBothWrites | baseline owner PASS + new Android NOT RUN | Execute new regression |
| Collector exception transparency | C05.metadataDownstreamReadyFailurePropagatesByIdentity; PREF.downstreamProgrammingFailureAndCollectorCancellationAreNotTranslated | new Android NOT RUN + baseline owner PASS | Execute new Room regression |
| Independent metadata writers on one file | C05.independentRoomInstancesPreserveConcurrentMetadataFields | new Android NOT RUN | Execute new regression |
| Room cancellation / rollback | REG.cancellationExceptionEscapesAnOuterTransactionAndRollsBackRegistration; META.cancellationRollsBackBothMetadataFieldsInAnOuterTransaction; META.cancelledQueuedMetadataWritesDoNotReturnOutcomesOrChangeFields | Android baseline, owner PASS | No general rollback claim after acknowledged commit |
| Error/cancellation policy | RegistryDatabaseErrorsTest (2 tests); MetadataDatabaseOperationTest (3 tests); PreferencesStorageErrorsTest (2 tests) | JVM actual PASS | SQLite constructor/backend paths require Android |
| DataStore defaults / missing key distinction | PREF.emptyStoreReturnsExactDefaultsAfterLoading; PREF.absentIndividualKeysDefaultWithoutReplacingPresentValues; PreferencesMappingTest.absentKeysProduceOnlyDocumentedDefaults | Android owner PASS + JVM actual PASS | Obtain adapter coverage |
| Stable enums / exact durable roundtrip | PREF.allThemesPersistTheirExplicitStableKeys; PREF.allLanguagesPersistTheirExplicitStableKeys; PREF.allPageFitsPersistTheirExplicitStableKeys; PREF.completePreferencesRoundTripExactlyThroughRecreatedStore | Android baseline, owner PASS | Obtain coverage |
| Atomic fields, unrelated keys, concurrency | PREF.everyUpdatePreservesOtherFieldsAndUnrelatedTypedKeys; PREF.concurrentDifferentFieldUpdatesDoNotLoseChanges; PREF.repeatedUpdatesRemainIdempotent | Android baseline, owner PASS | Obtain coverage |
| DataStore independent collectors | PREF.independentCollectorsReceiveChangesAndCancelIndependently; PREF.coldFlowQueriesOnlyWhenCollectedAndRestartsLoadingForEachCollector | Android baseline, owner PASS | Obtain coverage |
| Unknown / wrong type / corrupt protobuf | PREF.unknownSerializedValuesPropagateAndCannotBeSilentlyRepaired; PREF.wrongPersistedTypesPropagateWithoutDefaultingOrRepairing; PREF.corruptProtobufPropagatesAndOriginalBytesAreNotReplaced | Android baseline, owner PASS | Obtain exception-path coverage |
| Real read/write I/O, no fabricated state | PREF.realReadIoFailureStartsLoadingThenFailedRatherThanDefaults; PREF.failedWriteDoesNotFabricateUpdatedObservationOrReplacePersistedBytes | Android baseline, owner PASS | Additional disk-full/permission failure induction not established |
| Single production store / test lifetime | PREF.productionAccessorReusesOneApplicationContextStore; PREF.duplicateActiveFileOwnerIsAProgrammingErrorNotAStorageOutcome; PREF.completePreferencesRoundTripExactlyThroughRecreatedStore | Android baseline, owner PASS + source audit | Library-owned process scope; test scopes cancelAndJoin |
| DataStore cancellation | PREF.cancellationBeforeUpdateDoesNotEnterPersistence; PREF.cancellationOfQueuedUpdatePropagatesAndCallerReconcilesThroughObservation | Android baseline, owner PASS | Post-commit interruption reconciliation acknowledged, not simulated |
| Security / backup | source/schema/manifest audit, not a runtime test | Manual source review performed locally | Room opaque provider locations are private data and currently backup eligible; owner backup decision pending |

MDB = MetadataDatabaseTest; REG = RoomDocumentRegistryTest; META = RoomDocumentMetadataRepositoryTest;
PREF = DataStorePreferencesRepositoryTest; policy = MetadataReadingPositionPolicyTest;
C05 = PersistenceRegressionTest.

## Findings and acceptance blockers

**Confirmed implementation defects:** none identified. No production changes proposed or made.
**Missing tests:** three genuine gaps above now have isolated real Room tests; they require Android
execution. The first uses two file-backed Room instances, the second real DROP TABLE SQLite
failures, the third a downstream failure after Ready. No sleeps/mocks/weakened assertions.
**Missing evidence (blocking):** valid API 26 execution data, matching build provenance, combined
class/source/package coverage, the 90% LINE / 80% BRANCH results for each scope and source group,
and execution of the three new regressions. The JVM-only Room scope fails both full-scope numbers;
DataStore fails full-scope line coverage. No percentage is improved by removing runtime code.
**Infrastructure gap:** existing PDF coverage input validation checks only nonempty `.ec`, which
would not reject the local invalid file until report generation. C05 proposal must parse input,
verify identity and provenance before accepting data; do not silently change the PDF task.
**Security decision:** allowBackup=true with no database/file exclusions makes Room identities,
opaque provider locators, metadata and DataStore settings eligible for backup. No PDF contents or
tokens/passwords/credentials are stored; opaque provider locators can still be sensitive. This
is an outstanding owner decision, not permission to edit backup rules. DataStore settings are
non-sensitive; future unrelated keys must not become a secret store.
**Nonblocking recommendations / ownership limits:** reuse one caller-owned Room database and the
single private DataStore delegate. Cross-instance Room write serialization is tested, but automatic
cross-instance Flow invalidation is not configured by MetadataDatabase.open (no
 enableMultiInstanceInvalidation). That is not a demonstrated violation of the current one-shared-
database ownership contract. If future composition requires separate writer instances and live
observers, define that requirement and add a real cross-instance invalidation regression first.
No released v1 migration exists; schema history remains intact and destructive fallback absent.
Future version migrations, API27/physical-device checks and final owner acceptance remain required
when their applicable phase criteria are reached.


## Concrete shared-infrastructure proposal — NOT applied / requires separate owner authorization

No new dependency is required. Request authorization specifically for additive changes to
`app/build.gradle.kts` and `.github/workflows/ci.yml`, plus the input-validation/threshold tool.
Leave all seven job names, triggers, smoke checks, policies and existing limits unchanged.

1. Add two JacocoReport tasks over the same debug classes used above, with includes
   `com/abdev/partituraspdf/data/metadata/**` and `com/abdev/partituraspdf/data/preferences/**`,
   excluding only `**/*_Impl*`. XML/HTML outputs should be
   `app/build/reports/persistence/metadata.{xml,html}` and `preferences.{xml,html}`.
   Use BOTH `app/build/jacoco/testDebugUnitTest.exec` and the recursive
   `app/build/outputs/code_coverage/debugAndroidTest/connected/**/*.ec`.
2. Run the JVM suite in the **same API26 job/build** as the connected suite. This avoids trusting
   bytecode from independently built job caches. Keep the existing connected-test and smoke XML
   verification lines; append JVM verification, input validation and report verification there.
3. Before merging data, use JaCoCo ExecFileLoader for each individual input (not size checks):
   require parse success, valid sessions and actual persistence records from Android; require
   Android scope A and B runtime records (RoomDocumentRegistry / RoomDocumentMetadataRepository /
   DataStorePreferencesRepository), and actual successful API26 XML. Reject each scope record
   whose JaCoCo class ID differs from Analyzer's current debug classes. Reject incompatible IDs
   between JVM and Android for the same name. No fallback to JVM-only data or discarded files.
   The C05CoverageIdentity diagnostic demonstrates JVM identity comparison and rejects the local
   invalid .ec; extending it with these full-run preconditions needs approved infrastructure.
4. Write a provenance manifest with git SHA, Android API, Gradle/AGP/JDK/JaCoCo versions, input
   SHA256 hashes, debug-class SHA256 inventory, sessions, class IDs and XML test counts. Preserve
   it with XML/HTML, `.exec`, `.ec` and the matching handwritten class files. Retain no user DBs,
   stores, PDFs, APK releases or private data.
5. Fail closed at LINE <90% or BRANCH <80%, **separately for both packages and each handwritten
   source group with executable lines/branches**. Print class-level counts too. Zero-branch groups
   are N/A, not waived line checks. Keep all continuations included; source groups avoid assigning
   impossible standalone source-line targets to compiler-only classes. No package/project average
   may hide a below-threshold handwritten file. The gate must reject missing groups, zero executed
   persistence classes, missing counters or unavailable evidence. Kotlin's built-in compiler
   filtering must be documented and reviewed against source branches.

Proposed appended emulator script (requires the tasks/tools above; NOT executed):

```bash
bash ./gradlew :app:testDebugUnitTest --stacktrace
python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
bash ./gradlew :app:metadataPersistenceCoverageReport :app:preferencesPersistenceCoverageReport :app:verifyPersistenceCoverage --stacktrace
```

Proposed additional paths to the existing instrumentation-api26-reports upload (NOT applied):

```yaml
app/build/outputs/code_coverage/debugAndroidTest/connected/
app/build/jacoco/testDebugUnitTest.exec
app/build/reports/persistence/
app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/com/abdev/partituraspdf/data/
```

The existing debug `enableAndroidTestCoverage=true` needs no change. Binary compatibility is
not inferred from matching JaCoCo versions alone: the class identity/input checks above are
mandatory. Do not independently rebuild after capturing Android data; verify exact identities.
The same instrumented-api26 job would fail on insufficient persistence coverage, propagating
through the unchanged quality-gate. This is an additive enforcement change and explicitly
requires owner approval; it has not been implemented or represented as executed.

### Reproducible report task template

This is the exact report task body proposed for both scopes, adapted from the successfully run
external JVM diagnostic. Input validation and provenance enforcement must precede it as above.

```kotlin
listOf("metadata", "preferences").forEach { scope ->
    tasks.register<JacocoReport>("${scope}PersistenceCoverageReport") {
        dependsOn("testDebugUnitTest", "validatePersistenceCoverageInputs")
        classDirectories.setFrom(fileTree(layout.buildDirectory.dir(
            "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
        )) {
            include("com/abdev/partituraspdf/data/$scope/**")
            exclude("**/*_Impl*")
        })
        sourceDirectories.setFrom(files("src/main/java"))
        executionData.setFrom(
            layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"),
            fileTree(layout.buildDirectory.dir("outputs/code_coverage/debugAndroidTest/connected")) {
                include("**/*.ec")
            }
        )
        reports {
            xml.required.set(true)
            html.required.set(true)
            xml.outputLocation.set(layout.buildDirectory.file("reports/persistence/$scope.xml"))
            html.outputLocation.set(layout.buildDirectory.dir("reports/persistence/$scope.html"))
        }
    }
}
```

## Executed validation and commands

Activation: `source /workspace/partituras-tools/activate.sh`; no toolchain/dependency change.

PASS, final BUILD SUCCESSFUL in 11s (after adding regressions):

```bash
bash ./gradlew :app:kspDebugKotlin :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --max-workers=4 --console=plain
```

JVM task reused unchanged passing results; XML independently checked: **128 executed tests,
zero failures/errors/skips**. No JVM source changes. PASS:

```bash
python scripts/verify_test_results.py app/build/test-results/testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
python -m unittest discover -s scripts/tests -v
python scripts/validate_localization.py
python test-fixtures/scripts/verify_fixtures.py
```

18 infrastructure tests PASS; en/ca/es PASS; all 15 fixture checksums/structure/rendering PASS.
Lint: 0 errors, 20 pre-existing warnings, no new test/production findings.
PASS: three new compiled JUnit4 test methods are public final void; existing 200 methods are
unchanged (total source inventory 203). API26 new tests NOT RUN; APK compilation is not execution.
Room v1 JSON compared byte-for-byte to HEAD; all pre-existing tracked files unchanged.
PASS: git diff --check and no-index whitespace checks on each new file.

Actual diagnostic report generation PASS:

```bash
bash ./gradlew -I /workspace/partituras-tools/c05-coverage.init.gradle :app:c05MetadataJvmCoverage :app:c05PreferencesJvmCoverage --max-workers=4 --console=plain
```

JVM identity analyzer PASS (zero mismatches); old Android .ec parser **FAIL** (invalid input).
Full combined coverage **BLOCKED**, thresholds **NOT DEMONSTRATED**. This expected rejected input
is an evidence finding, not an unexplained application test failure.
External reports/logs (not staged/committed): c05-metadata-jvm.xml, c05-preferences-jvm.xml,
c05-validation.log, c05-coverage.log under `/workspace/partituras-tools`.
No proposed CI task, report upload or API26 run is claimed as executed.

## Changed files and final working-tree boundary

Created only:

- `app/src/androidTest/java/com/abdev/partituraspdf/data/metadata/PersistenceRegressionTest.kt`
- `docs/C05_PERSISTENCE_VALIDATION.md` (this report).

No production or pre-existing test changes, schema edits, policy changes, staged files, commits,
pushes or merges. Ending HEAD remains the required baseline. Owner review and separate
shared-infrastructure authorization are the next prerequisites for full coverage acceptance.

## Complete persistence test inventory

Each row identifies all test methods, not a coverage claim. JVM rows have actual local PASS
XML; baseline Android rows have owner-supplied PASS for the baseline and no downloaded XML;
new C05 Android rows remain NOT RUN. See the matrix for gaps and behavior mapping.


### MetadataDatabaseOperationTest (test, 3 tests)

- `operationReturnsTypedResultsWithoutChangingTheirMeaning`
- `programmingErrorsAndCancellationAreNeverTranslatedToStorageFailure`
- `cancelledCoroutineCannotEnterOperationOrTurnFailureIntoAnOutcome`

### MetadataMappingTest (test, 6 tests)

- `availabilityKeysAreExplicitAndRoundTrip`
- `registryRoundTripsOpaqueIdentityAndAllObservations`
- `inconsistentOrUnrecognizedRevisionsCannotBecomeEvidence`
- `invalidIdentityLocationAndAvailabilityAreRejected`
- `metadataRoundTripsAbsentUnknownAndVerifiedPositionsWithoutRegistryRevision`
- `corruptMetadataNeverSilentlyBecomesDefaultsOrEvidence`

### MetadataReadingPositionPolicyTest (test, 4 tests)

- `verifiedPositionSuppliesExactExpectationWithoutBeingRewritten`
- `unknownPositionGuardsCurrentRegistryWhileRemainingOnlyAHint`
- `clearingUsesCurrentRevisionWithoutAHistoricalExpectedRevision`
- `foreignPositionAndRegistryRevisionAreRejectedAsProgrammingErrors`

### RegistryDatabaseErrorsTest (test, 2 tests)

- `operationalDatabaseFailuresArePrivateStorageFailures`
- `constraintsGenericSqlAndProgrammingFailuresAreNotAvailabilityProof`

### PreferencesMappingTest (test, 3 tests)

- `absentKeysProduceOnlyDocumentedDefaults`
- `everyStableValueRoundTripsExactlyAndUpdatesPreserveAllOtherKeys`
- `unknownStringsAndWrongTypesNeverDefaultOrGetRepairedByUpdates`

### PreferencesStorageErrorsTest (test, 2 tests)

- `fileIoErrorsAreClassifiedButCorruptionAndUnexpectedErrorsPropagate`
- `cancellationTakesPrecedenceOverIoClassification`

### MetadataDatabaseTest (androidTest, 13 tests)

- `registrationCreatesUnknownObservationsAndDefaultMetadataAtomically`
- `duplicateLogicalIdAndProviderLocationAbortWithoutReplacingRows`
- `concurrentRegistrationOfOneLocationCreatesOnlyOneCompleteIdentity`
- `failureOfMetadataInsertRollsBackRegistryInsert`
- `metadataForeignKeyAndPrimaryKeyAreEnforced`
- `rebindComparesLocationProtectsUniquenessAndInvalidatesOnlyRegistryObservations`
- `concurrentExpectedLocationRebindHasExactlyOneWinner`
- `independentConcurrentMetadataWritesAndSeparateRevisionsSurviveObservations`
- `expectedRevisionComparisonIncludesTypeDigestAndByteCount`
- `unknownPositionsRemainHintsAndMismatchedDocumentsAreRejected`
- `flowsEmitMissingRegistrationAndCommittedUpdates`
- `corruptPersistedEvidenceIsRejectedAfterRealSqlRead`
- `fileBackedDatabaseRetainsBothIndependentRevisionsAcrossCloseAndReopen`

### PersistenceRegressionTest (androidTest, 3 tests)

- `independentRoomInstancesPreserveConcurrentMetadataFields`
- `metadataSqlDefectsPropagateThroughObservationAndBothWrites`
- `metadataDownstreamReadyFailurePropagatesByIdentity`

### RoomDocumentMetadataRepositoryTest (androidTest, 22 tests)

- `coldFlowStartsLoadingAndEachCollectorReadsItsOwnCurrentState`
- `missingObservationRemainsLiveAndBecomesReadyAfterRegistration`
- `missingMutationsDoNotCreateMetadataOrMisreportRevisionConflicts`
- `favoriteOnlyUpdatesFavoriteAndPreservesPositionAndRegistration`
- `unknownPositionOnUnknownRegistryIsOnlyAResumeHint`
- `unknownHintOnVerifiedRegistryIsNotPromotedToVerifiedEvidence`
- `verifiedPositionRequiresExactRegistryTypeDigestAndLength`
- `foreignRevisionOwnershipPropagatesBeforePersistence`
- `clearingUsesCurrentRegistryRevisionEvenWhenHistoricalPositionIsStale`
- `rebindPreservesHistoricalMetadataUntilExplicitClear`
- `independentConcurrentFavoriteAndPositionUpdatesPreserveBothFields`
- `queuedVerifiedPositionDetectsARevisionCommittedByAnotherWriter`
- `queuedClearUsesTheRevisionCommittedBeforeItsOwnTransaction`
- `multipleLiveCollectorsObserveUpdatesAndCancelIndependently`
- `failedMissingObservationDoesNotRetainStaleReadyAndCanRecover`
- `fileBackedCloseReopenPersistsFavoritePositionAndClearing`
- `corruptMetadataNeverBecomesReadyOrGetsSilentlyRepairedByWrites`
- `missingMetadataForAnExistingRegistrationIsCorruptionNotNotRegistered`
- `operationalFailureStartsLoadingThenFailedAndIsTypedForBothMutations`
- `cancelledObservationDoesNotEmitATypedFailureOrCancelOtherCollectors`
- `cancellationRollsBackBothMetadataFieldsInAnOuterTransaction`
- `cancelledQueuedMetadataWritesDoNotReturnOutcomesOrChangeFields`

### RoomDocumentRegistryTest (androidTest, 23 tests)

- `registersCallerIdentityAndBothDurableDefaults`
- `duplicateIdentityDoesNotReplaceLocationObservationsOrMetadata`
- `duplicateProviderLocationDoesNotCreateAnOrphanOrChangeIdentity`
- `nonUniqueConstraintFailurePropagatesAndRollsBackBothTables`
- `resolveAndLookupMissingReturnNotRegisteredWithoutCreatingRows`
- `resolvePreservesEveryAvailabilityEvenWhenAccessWasLost`
- `lookupUsesExactOpaqueNamespaceAndLocatorOnly`
- `observationsStoreCallerEvidenceAndCanReturnToUnknownWithoutTouchingMetadata`
- `mismatchedRevisionOwnershipIsAProgrammingErrorAndLeavesStateUntouched`
- `staleObservationAndRebindCannotModifyExistingState`
- `missingObservationAndRebindAreNotStaleRegistrationConflicts`
- `rebindReturnsPersistedUnknownStateAndPreservesHistoricalMetadata`
- `occupiedReplacementIsAConflictAndSameIdentityLocationIsAllowed`
- `concurrentRegistrationOfSameIdHasOneWinnerWithoutOverwrite`
- `concurrentRebindWithOneExpectedLocationHasOneWinner`
- `observationRacingRebindIsLinearizableAndNeverRestoresOldEvidence`
- `concurrentRegistrationAcrossTwoRoomInstancesUsesSqliteUniqueness`
- `fileBackedReopenRetainsIdentityAvailabilityAndSeparateMetadataRevision`
- `actualCantOpenDatabaseFailureMapsSafelyForAllFiveMethods`
- `invalidPersistedRepresentationPropagatesForReadsAndMutations`
- `unexpectedSqlDefectPropagatesRatherThanClaimingStorageUnavailable`
- `cancellationExceptionEscapesAnOuterTransactionAndRollsBackRegistration`
- `cancellationWhileQueuedForWriterDoesNotCommitOrReturnAnOutcome`

### DataStorePreferencesRepositoryTest (androidTest, 21 tests)

- `emptyStoreReturnsExactDefaultsAfterLoading`
- `coldFlowQueriesOnlyWhenCollectedAndRestartsLoadingForEachCollector`
- `independentCollectorsReceiveChangesAndCancelIndependently`
- `allThemesPersistTheirExplicitStableKeys`
- `allLanguagesPersistTheirExplicitStableKeys`
- `allPageFitsPersistTheirExplicitStableKeys`
- `completePreferencesRoundTripExactlyThroughRecreatedStore`
- `everyUpdatePreservesOtherFieldsAndUnrelatedTypedKeys`
- `concurrentDifferentFieldUpdatesDoNotLoseChanges`
- `repeatedUpdatesRemainIdempotent`
- `absentIndividualKeysDefaultWithoutReplacingPresentValues`
- `unknownSerializedValuesPropagateAndCannotBeSilentlyRepaired`
- `wrongPersistedTypesPropagateWithoutDefaultingOrRepairing`
- `corruptProtobufPropagatesAndOriginalBytesAreNotReplaced`
- `realReadIoFailureStartsLoadingThenFailedRatherThanDefaults`
- `failedWriteDoesNotFabricateUpdatedObservationOrReplacePersistedBytes`
- `duplicateActiveFileOwnerIsAProgrammingErrorNotAStorageOutcome`
- `cancellationBeforeUpdateDoesNotEnterPersistence`
- `cancellationOfQueuedUpdatePropagatesAndCallerReconcilesThroughObservation`
- `downstreamProgrammingFailureAndCollectorCancellationAreNotTranslated`
- `productionAccessorReusesOneApplicationContextStore`
