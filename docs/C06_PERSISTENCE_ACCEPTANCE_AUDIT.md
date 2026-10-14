# C06 persistence security and acceptance audit

Audit date: 2026-10-14, Europe/Madrid. Repository: alexbonavila/PartiturasPDF.
Evidence describes the baseline and local pre-publication review; the publication commit SHA
and subsequent CI execution are reported separately, without treating older runs as validation.
Owner review target: [PR #10](https://github.com/alexbonavila/PartiturasPDF/pull/10).

## 1. Executive status

**BLOCKED for final phase acceptance.** The persistence adapters and their regression tests
have no newly confirmed functional defect from this source review. Local compilation, lint,
128 JVM tests and infrastructure checks pass. The preceding infrastructure run is visibly
successful on API 26/27/36. These results do not close the following mandatory items:

- The integrated revision's seven mandatory CI jobs are now **PASS**, confirmed on the final
  public run-page snapshot; its exact captured artifacts remain unavailable to this audit.
- Exact hosted coverage XML, execution data, JUnit XML and provenance could not be downloaded.
  Numeric combined coverage and independent artifact reproduction therefore remain BLOCKED.
- API 27/36 phase-close execution on the final integrated SHA is not established.
- Backup Policy B is explicitly approved and implemented below. Static/merged/APK checks PASS;
  actual backup/restore/device-transfer execution remains NOT RUN.
- Physical Android 8.1 tablet acceptance is BLOCKED: no physical device is attached.

**Current owner decision: implement Policy B.** Only the non-sensitive preferences protobuf is
eligible for backup/transfer; Room and every other application-private file/domain are omitted
from an exact-file allowlist. Both XML resources have been changed under explicit authorization;
manifest references and allowBackup=true remain unchanged. Option C remains unsupported without
restore/reconciliation integration. The earlier recommendation of A is superseded by the owner
decision. This report is not merge authorization.

## 2. Git state and integration

Required and audited HEAD: `0f49390724eed9cce00e7eae2f2a6d6872dd8e8d`.
Working branch: `feat/data-persistence-c`. Remote branch and `refs/pull/10/head` matched it.
The initial clean checkout was on infrastructure commit
`0ee80756a9cb46c56057c4d2b6bf64c142a0d5ab`; the feature ref was fetched and a local
feature branch was created from that exact remote baseline. No reset, rebase or changes discarded.

The integrated commit is `test(infra): C05 persistence coverage verification (#11)`, with parent
`d0b1ca02890c0e68e470b5a237b7e7f1b0057d73`. Its entire tracked tree is byte-identical to
the final infrastructure commit `0ee80756...` (`git diff ... HEAD --exit-code` succeeded).
Relative to C05 parent d0b1ca0 it adds exactly the seven infrastructure files plus
`MetadataDatabaseOpenTest.kt`: eight files, 1207 additions, no deletions. No C01-C05 production
code, public contracts, dependency catalog, schema, manifest or backup resources changed in
that integration. Squash integration means the infrastructure commit is not an ancestor;
tree equality and the integration diff, rather than ancestry alone, establish inclusion.

The original report is preserved and updated. The follow-up authorization modifies only the two
backup XML resources and adds scripts/verify_backup_policy.py plus scripts/tests/test_backup_policy.py.
No production Kotlin, existing tests, schema, Gradle, CI, manifest or public contracts changed.
All five implementation/report files remain unstaged and uncommitted; no push or merge.
PR #11 was already integrated by the owner; this audit does not perform that merge.

## 3. Authority and requirement-to-test traceability

Reviewed authority: AGENTS.md; PROJECT_SPECIFICATION.md; architecture/ARCHITECTURE.md,
CONTRACTS.md and DEVELOPMENT_PLAN.md; TESTING_POLICY.md; DEFINITION_OF_DONE.md;
CHANGE_CONTROL.md; C05_PERSISTENCE_VALIDATION.md; C05_INFRA_PERSISTENCE_COVERAGE.md.
The architecture and earlier reports contain explicitly historical status statements; they
do not override this task's authorization or establish current execution results.
Compiled A1 repository contracts define the implemented slice. No catalog/UI/drafts/restore
coordinator, PDF modification or locale application is added or required to exist by this audit.

Abbreviations: MDB = MetadataDatabaseTest; OPEN = MetadataDatabaseOpenTest;
REG = RoomDocumentRegistryTest; META = RoomDocumentMetadataRepositoryTest;
PREF = DataStorePreferencesRepositoryTest; C05 = PersistenceRegressionTest.
The following is source/test traceability, not a claim to have independently read hosted XML.
Local JVM XML is verified; Android execution has workflow-status evidence on the earlier SHA,
with exact per-test XML evidence still BLOCKED.

| Contract / invariant | Reviewed tests and evidence |
| --- | --- |
| Stable logical ID; exact opaque provider/location; no identity inferred from names or digest | REG.registersCallerIdentityAndBothDurableDefaults; lookupUsesExactOpaqueNamespaceAndLocatorOnly; MetadataMappingTest.registryRoundTripsOpaqueIdentityAndAllObservations |
| Duplicate ID/location cannot replace an existing association | REG.duplicateIdentityDoesNotReplaceLocationObservationsOrMetadata; duplicateProviderLocationDoesNotCreateAnOrphanOrChangeIdentity; MDB.duplicateLogicalIdAndProviderLocationAbortWithoutReplacingRows |
| Registration and initial metadata are atomic | MDB.failureOfMetadataInsertRollsBackRegistryInsert; REG.nonUniqueConstraintFailurePropagatesAndRollsBackBothTables |
| Missing registration differs from changed registration or deleted document | REG.resolveAndLookupMissingReturnNotRegisteredWithoutCreatingRows; missingObservationAndRebindAreNotStaleRegistrationConflicts; META.missingMutationsDoNotCreateMetadataOrMisreportRevisionConflicts |
| Rebind retains identity/metadata and invalidates access/revision observations | REG.rebindReturnsPersistedUnknownStateAndPreservesHistoricalMetadata; staleObservationAndRebindCannotModifyExistingState; META.rebindPreservesHistoricalMetadataUntilExplicitClear |
| Revoked/unavailable/deleted remain observations; a row is not access proof | REG.resolvePreservesEveryAvailabilityEvenWhenAccessWasLost; observationsStoreCallerEvidenceAndCanReturnToUnknownWithoutTouchingMetadata |
| Writer concurrency is guarded by SQLite, including separate instances | REG.concurrentRegistrationAcrossTwoRoomInstancesUsesSqliteUniqueness; observationRacingRebindIsLinearizableAndNeverRestoresOldEvidence; C05.independentRoomInstancesPreserveConcurrentMetadataFields |
| Favorite updates preserve positions; concurrent fields do not overwrite each other | META.favoriteOnlyUpdatesFavoriteAndPreservesPositionAndRegistration; independentConcurrentFavoriteAndPositionUpdatesPreserveBothFields |
| Verified positions compare exact type, digest and byte count; stale writes preserve existing values | META.verifiedPositionRequiresExactRegistryTypeDigestAndLength; queuedVerifiedPositionDetectsARevisionCommittedByAnotherWriter; MDB.expectedRevisionComparisonIncludesTypeDigestAndByteCount |
| Unknown position remains a hint; clear preserves favorite and uses current revision | META.unknownHintOnVerifiedRegistryIsNotPromotedToVerifiedEvidence; clearingUsesCurrentRegistryRevisionEvenWhenHistoricalPositionIsStale; queuedClearUsesTheRevisionCommittedBeforeItsOwnTransaction; MetadataReadingPositionPolicyTest |
| Cold per-collector Loading; transactional parent/metadata observation and invalidation | META.coldFlowStartsLoadingAndEachCollectorReadsItsOwnCurrentState; missingObservationRemainsLiveAndBecomesReadyAfterRegistration; multipleLiveCollectorsObserveUpdatesAndCancelIndependently; failedMissingObservationDoesNotRetainStaleReadyAndCanRecover |
| Corruption/absent required metadata cannot be silently repaired | META.corruptMetadataNeverBecomesReadyOrGetsSilentlyRepairedByWrites; missingMetadataForAnExistingRegistrationIsCorruptionNotNotRegistered; MDB.corruptPersistedEvidenceIsRejectedAfterRealSqlRead; MetadataMappingTest |
| Operational storage errors are typed; unexpected SQL/programming failures propagate | REG.actualCantOpenDatabaseFailureMapsSafelyForAllFiveMethods; META.operationalFailureStartsLoadingThenFailedAndIsTypedForBothMutations; C05.metadataSqlDefectsPropagateThroughObservationAndBothWrites; RegistryDatabaseErrorsTest |
| Cancellation propagates; transaction rollback; downstream exception transparency | REG.cancellationExceptionEscapesAnOuterTransactionAndRollsBackRegistration; META.cancellationRollsBackBothMetadataFieldsInAnOuterTransaction; cancelledQueuedMetadataWritesDoNotReturnOutcomesOrChangeFields; C05.metadataDownstreamReadyFailurePropagatesByIdentity; MetadataDatabaseOperationTest |
| File-backed Room reopen and factory construction/default/rejection paths | MDB.fileBackedDatabaseRetainsBothIndependentRevisionsAcrossCloseAndReopen; REG.fileBackedReopenRetainsIdentityAvailabilityAndSeparateMetadataRevision; META.fileBackedCloseReopenPersistsFavoritePositionAndClearing; both OPEN tests |
| Successful absent DataStore keys alone receive stable defaults | PREF.emptyStoreReturnsExactDefaultsAfterLoading; absentIndividualKeysDefaultWithoutReplacingPresentValues; allThemesPersistTheirExplicitStableKeys; allLanguagesPersistTheirExplicitStableKeys; allPageFitsPersistTheirExplicitStableKeys; PreferencesMappingTest |
| Atomic preference updates preserve unrelated keys and other collectors | PREF.everyUpdatePreservesOtherFieldsAndUnrelatedTypedKeys; concurrentDifferentFieldUpdatesDoNotLoseChanges; independentCollectorsReceiveChangesAndCancelIndependently; repeatedUpdatesRemainIdempotent |
| Invalid strings/types/protobuf are not defaults or silent repair | PREF.unknownSerializedValuesPropagateAndCannotBeSilentlyRepaired; wrongPersistedTypesPropagateWithoutDefaultingOrRepairing; corruptProtobufPropagatesAndOriginalBytesAreNotReplaced; PreferencesStorageErrorsTest |
| DataStore I/O failures do not fabricate saved state; cancellation remains visible | PREF.realReadIoFailureStartsLoadingThenFailedRatherThanDefaults; failedWriteDoesNotFabricateUpdatedObservationOrReplacePersistedBytes; cancellationBeforeUpdateDoesNotEnterPersistence; cancellationOfQueuedUpdatePropagatesAndCallerReconcilesThroughObservation; downstreamProgrammingFailureAndCollectorCancellationAreNotTranslated |
| One production store and explicit test scope disposal/recreation | PREF.productionAccessorReusesOneApplicationContextStore; duplicateActiveFileOwnerIsAProgrammingErrorNotAStorageOutcome; completePreferencesRoundTripExactlyThroughRecreatedStore |

C-specific Android inventory: MDB 13, REG 23, META 22, PREF 21, C05 3, OPEN 2 = 84.
The complete app inventory is 205 Android tests and 128 JVM tests; C-specific JVM classes
contain 20 tests. Source inventories are not execution counts. No existing application tests
were changed; eight focused Python backup-policy tests were added in the follow-up.

## 4. Persistence correctness review

### Room

Reviewed every C-phase production file, corresponding JVM/instrumentation tests and the
checked-in v1 schema. Registry primary identity is documentId; the unique provider/locator
index enforces location ownership. Registration inserts registry and metadata within a
transaction using ABORT, not REPLACE. The metadata primary key is also a CASCADE foreign key;
no public delete API exists. Access-loss observation does not delete either row.

The registry adapter performs checks, conditional updates and readback under a writer
transaction. This avoids another writer racing missing-registration/conflict classification.
Rebinding invalidates registry evidence but preserves the independently revision-bound stored
reading position. SQL compares both expected provider and locator. Every parameter originating
in a domain value is bound, not interpolated into SQL.

Metadata reads use @Transaction plus the RegisteredMetadata relation; Room observes parent
and child invalidations. Each collection starts Loading. Missing registration is a live Failed
observation which can recover after registration; an operational exception terminates that
collection. Missing metadata for a registered parent is a defect/corruption, not defaults.
The existing raw-SQL regression explicitly refreshes invalidation rather than weakening assertions.

Favorite and reading fields are separate SQL updates. Position writes validate ownership and
compare registry revision type/digest/length in SQL. Verified mismatch returns REVISION_CHANGED
without replacing metadata; Unknown remains Unknown, guarded by the current transaction's
revision. Clearing writes null position fields and preserves favorites.

Mapping rejects invalid availability/revision keys, inconsistent null evidence, invalid IDs,
non-boolean integer favorites and out-of-range page values. SnapshotEvidence checks representation;
it is neither authentication nor proof of current provider bytes. Corrupt SQLite database types
map to PRIVATE_STORAGE_UNAVAILABLE without recreation; invalid domain rows remain visible failures.
Generic SQL errors and constraints propagate. Cancellation is checked before entering operations
and before error translation, and is never transformed into Outcome.Failure.

The caller owns MetadataDatabase.close(); adapters do not close injected databases or create
background scopes. MetadataDatabase.open uses application context and has no startup wiring,
provider access or destructive migration fallback. OPEN now uses targetContext as its base but
returns its isolated wrapper as applicationContext; its database path/open/delete operations
stay under a UUID cache directory. Real database handles close before cleanup. Existing Room
tests use in-memory databases or unique names and delete only their own files.

### DataStore

The private top-level preferencesDataStore delegate owns one process-lifetime store, obtained
through application context. There is no multiprocess guarantee and no duplicate production
factory. The adapter adds no scope, polling or unrelated background job. Tests inject unique
files and cancelAndJoin their owned scopes before reopening/deletion.

Only three stable string keys are owned: theme_selection (system/light/dark), language_selection
(system/en/ca/es), reader_page_fit (page/width). Missing keys default after a successful read.
Atomic edit validates all owned fields before changing one; unrelated keys survive. Unknown
strings, wrong types and CorruptionException propagate without replacing stored data. Ordinary
IOException is a typed private-storage failure. Cancellation and collector defects propagate.
Interrupted durable updates may have committed: consumers must observe to reconcile, not assume
rollback. Separate collectors independently start Loading and observe the same store.

**Review conclusion: PASS for source-level contract consistency; device/evidence acceptance
remains conditional.** No new functional defect is confirmed. Stress tests do not prove every
possible filesystem failure, process-death point or vendor behavior. No generic corruption
recovery, restore coordinator or automatic deletion is falsely credited as implemented.

## 5. Security findings and limits

| Finding | Classification / evidence | Consequence / action |
| --- | --- | --- |
| F1: unrestricted backup eligibility in baseline | CONFIRMED baseline gap; corrected by approved Policy B | PASS static, merged-manifest and compiled APK checks; actual transport validation NOT RUN |
| F2: restore reconciliation is not implemented | CONFIRMED integration limitation; no startup wiring/coordinator | Restored AVAILABLE observations/locations cannot establish current access; option C requires separate work |
| PDF content/tokens are not C-phase fields | PASS reviewed entities/preferences and production operations | No PDF bytes, OAuth tokens, passwords or credential field is introduced; opaque locators still require privacy treatment |
| SQL injection boundary | PASS parameterized DAO queries and Room inserts | Raw SQL with synthetic literals is confined to tests; no production dynamic SQL found |
| Corrupt data does not become valid state | PASS mapping and exception boundaries plus regression tests | No silent reset/replacement/migration fallback |
| Typed storage errors contain no identifiers or raw exceptions | PASS AppError enum outcomes and adapter source | Unexpected exceptions deliberately propagate; future callers must sanitize logs/UI, not log domain toString or SQL/file exceptions |
| C production logging | PASS source scan; no logging/printing in persistence packages | Not a complete framework/transport log audit; Android/Room diagnostics can contain private paths |
| Private storage | PASS databaseBuilder application-private database; DataStore files/datastore | Sandbox is not application-level encryption; no extra encryption requirement is invented |
| Test assets | PASS source-set separation and actual debug app APK inspection | No PDF fixture/schema assets in debug app APK; instrumentation assets are test-only; release APK was not independently built/inspected in C06 |
| CI artifacts | PASS upload scope review | Only generated execution/XML/report/scoped class evidence, 14-day retention; no database, protobuf, APK, credentials or user PDFs added to uploads |

Identifiers, provider namespace/locator, content digests/byte counts, favorites and reading
positions may reveal personal document associations or habits. A provider locator is opaque:
the adapter does not parse or sanitize a caller-supplied credential-bearing URL, so future
trusted storage composition must never supply tokens as locators. No such credential use or
actual data disclosure is demonstrated here. No authentication/token subsystem is implemented.

Framework log behavior, a comprehensive dependency-CVE audit, rooted-device resistance,
backup transport execution and encrypted-device key handling are not established by compilation.
Future consumers must recheck permissions/provider identity and current revision before using
stored state. Stale AVAILABLE is historical evidence, not permission to read/edit a document.

## 6. Android backup and restore

### Approved and implemented Policy B

The owner explicitly authorized this correction after the initial audit. Manifest remains
allowBackup=true, fullBackupContent=@xml/backup_rules, dataExtractionRules=@xml/data_extraction_rules.
There is no custom backup agent. The only rule in each transport is:

```xml
<include domain="file" path="datastore/app_preferences.preferences_pb" />
```

backup_rules.xml has one full-backup-content include for API26-30. data_extraction_rules.xml
has both explicit cloud-backup and device-transfer sections, each with that same single include.
The path is relative to the `file` domain (Context.filesDir); do not add a leading `files/`.
No database/root/sharedpref/device-protected/external domain is included. No broad directory,
wildcard, additional include or denylist-dependent default is used. A nonempty include map
selects only included paths and skips domains without includes: therefore every Room database,
-wal/-shm/-journal sidecar and all other private files are outside eligibility.

| Platform / transport | Implemented eligibility |
| --- | --- |
| API26-30 legacy full backup/restore | Only file:datastore/app_preferences.preferences_pb |
| API31+ cloud-backup | Only file:datastore/app_preferences.preferences_pb |
| API31+ device-transfer | Only file:datastore/app_preferences.preferences_pb; explicit section |

The compiled debug APK contains these exact three includes, as verified by aapt2 decoding.
The final merged and APK manifests preserve allowBackup=true and point to those same compiled
resource IDs. Room is in the database domain and cannot enter this file-domain allowlist;
no main SQLite or auxiliary file is selected. Android transport prerequisites still apply:
eligibility is not evidence that a backup has occurred, and real restore/D2D must still be tested.

DataStore location is confirmed by the production delegate name app_preferences, STORAGE_FORMAT.md,
and official AndroidX preferencesDataStoreFile documentation/source: the protobuf is beneath
applicationContext.filesDir/datastore with the .preferences_pb suffix. Private cache and other
files are not included. MainActivity still has no product persistence composition/UI wiring.

**File-level limitation:** Android backup selects the entire protobuf, not individual keys.
Today the owned values are theme_selection, language_selection and reader_page_fit. DataStore
updates preserve unrelated keys, so any future sensitive or unrelated key written into this same
file would also become eligible. Such data must go to a separate excluded file/store or require
an explicitly approved policy revision. Never put tokens, document references or secrets into
this allowlisted settings protobuf. Restored corrupt/unknown values still propagate failures;
this patch does not add silent repair or a migration.

### Android 16 and newer transfer boundary (final publication review)

Android 16's official android-16.0.0_r1 FullBackup implementation was inspected. It selects
cloud-backup for CLOUD and device-transfer for DEVICE_TRANSFER. It also has a compatibility
change that can ignore legacy fullBackupContent rules during D2D, which is why this patch
does not rely on the legacy allowlist to protect newer device transfers: device-transfer is
explicitly restricted in dataExtractionRules. Ordinary framework cloud/D2D transfers using
these destinations therefore have the appropriate exact-file section.

This verification is bounded to those framework destinations, not every mechanism that can
copy application data on Android 16 or later. Cross-platform migration (Android to another OS),
later platform-specific transfer modes, OEM privileged clone/migration utilities, application-
implemented export, and root/debug file copying are not certified by these XML tests. No
cross-platform-specific section/opt-in or custom transfer implementation is present in this app.
Do not infer that an Android cloud/device-transfer allowlist automatically governs a separate
cross-platform transport or prevents privileged copying. Their applicability, default behavior
and actual device implementation require current transport-specific evidence and owner review.
The r1 source does not establish later Android16 QPR or future-platform behavior; attempted
retrieval of the mirror's android16-qpr2-release source returned 404, and developer.android.com
documentation remains inaccessible through this environment. No speculative transfer rules
or manifest attributes are added. This is an explicit outstanding acceptance boundary, not
an observed leak or a defect in the approved ordinary cloud/D2D XML configuration.

Source: https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/app/backup/FullBackup.java.

### Restore consequences under the reviewed framework policy

A restored logical ID can persist while its opaque provider document/tree/location no longer
exists or refers to a different environment. Android/provider permissions cannot be assumed
to survive reinstall or cross-device restore; a serialized reference carries no new grant.
Stored availability and VerifiedSnapshot are historical. Neither proves present access, identity
of a newly selected file or current bytes. Restore must never infer deletion from unavailable
access, silently attach metadata to a same-named file, or promote an Unknown hint to evidence.
Favorites/positions should be retained or discarded only under an approved policy.

Contracts support preserving identity, reauthorization/rebinding and new observations, but no
implemented restore detector, grant reconciliation UI or coordinator performs them. Reconciliation
requires future separately approved composition/storage work; it cannot be credited to C06.

| Option | Security | Usability / compatibility | Acceptance requirement |
| --- | --- | --- | --- |
| A: exclude Room and DataStore | Least exposure; no cross-device replay of persisted associations/settings | New install/restore starts with no registrations and default theme/language/fit; favorites/positions are lost from automatic restore | Explicit exclusions in legacy rules and both newer transport sections, including complete SQLite domain and DataStore directory; separately approved patch and real restore tests |
| B: exclude Room, retain only non-sensitive preferences | Protects document associations; transfers three harmless enum choices | Retains theme/language/fit, but not registrations/favorites/positions; both API rule formats must cover DataStore's file path | Prefer narrowly allowlisting the actual settings protobuf; future unrelated/sensitive keys must not enter that file. Validate missing/corrupt/incompatible restored settings without silent repair |
| C: retain current behavior plus safeguards | Transfers private associations/habits; encryption does not fix stale permissions or identity | Could preserve user metadata if reauthorization and deliberate identity reconciliation exist | BLOCKED: implement approved restore/reconciliation safeguards, sanitize observations, verify grants/provider identity/revisions, test reinstall/cross-device and every transport; document consent/security conditions |

**Approved choice: B, implemented.** A remains the more restrictive alternative, but the owner
has chosen to retain the three non-sensitive settings. This decision is no longer pending.
C remains unsuitable without separately approved restore/reconciliation safeguards. The patch
changes eligibility only; it does not recover previously restored metadata or erase existing
cloud backups. Existing on-device Room rows are unchanged. Transport-specific handling of older
backup sets and enforcement on actual devices remain runtime-validation items.

Documentation verification: developer.android.com/identity/data/autobackup and
 developer.android.com/about/versions/12/behavior-changes-12#backup-restore were requested but
blocked by this environment's proxy (CONNECT 403). Instead, official AOSP BackupAgent source
was read for both android-8.0.0_r1 and current platform, confirming the nonempty include-map
filter and omission of all non-included domains. FullBackup's parser was reviewed for legacy
full-backup-content and newer transport sections. Official AndroidX PreferenceDataStoreFile
source documents the exact location/suffix. Authoritative source links:

- https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/app/backup/BackupAgent.java
- https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/backup/BackupAgent.java
- https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/backup/FullBackup.java
- https://github.com/androidx/androidx/blob/androidx-main/datastore/datastore-preferences/src/androidMain/kotlin/androidx/datastore/preferences/PreferenceDataStoreFile.android.kt

The upstream main sources are semantic references, not a claim of pinned runtime identity.
The actual resolved DataStore 1.2.1 AAR bytecode was also inspected: PreferenceDataStoreFile
appends .preferences_pb and calls DataStoreFile, which resolves applicationContext.filesDir
plus datastore/. This independently confirms the exact production file for app_preferences.

## 7. Schema and migration readiness

PASS: @Database version=1, exportSchema=true, Room plugin/KSP exports genuine schema history.
Path: app/schemas/com.abdev.partituraspdf.data.metadata.MetadataDatabase/1.json.
SHA256: `bc23cfd06a96945472317645e1b681911c213f38001ffa0e27e9f48f8b15f32e`.
Room identityHash: `c55fdabe4a043f57f99cbdbc8780c765`.
The file is byte-identical to C01 commit f4a908f9b8107b992d8b9d34b3eba2ac3cdfad04.
Entity columns/types/nullability, primary keys, unique provider/locator index and CASCADE foreign
key agree with that JSON and the DAO's table/column references. KSP compiles successfully.
No destructive migration fallback is enabled. Bad schema/open failures are not replaced silently.

No released C-phase database version is evidenced by the repository; there are no local release
tags. Distribution/install history cannot be established from this alone: the owner must confirm
whether any v1 database has shipped before migration decisions. A v1-to-v2 migration is
NOT APPLICABLE to the unchanged schema; do not invent an unshipped version or migration.

Before v2, retain v1 JSON, approve the exact change and preservation invariants, increment version,
export v2, implement an explicit migration path (auto only where appropriate and reviewed), and
use MigrationTestHelper to verify genuine v1 files preserve IDs, locations, metadata, constraints
and evidence through upgrade. Exercise failures and backup/restore compatibility. No destructive
fallback or speculative migration is added here. Preferences key/enum changes similarly need an
approved format/migration strategy rather than resetting unknown values.

## 8. Local test and artifact evidence

Environment: JDK 25.0.4.1, Gradle 9.8.0, AGP 9.4.1, JaCoCo 0.8.14, SDK 37, minSdk 26.
Tool activation: source /workspace/partituras-tools/activate.sh. No emulator launched.
The following commands completed in C06 at the integrated checkout:

| Command / check | Actual outcome |
| --- | --- |
| bash ./gradlew :app:kspDebugKotlin :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest :app:testPersistenceCoverageInputs --max-workers=4 --console=plain | PASS; compilation/APKs/lint, existing contract gate retained; cached tasks identified by Gradle |
| bash ./gradlew :app:testDebugUnitTest --rerun-tasks --no-build-cache --max-workers=4 --console=plain | PASS fresh execution: 128 JVM cases; no failures/skips; contract coverage gate passes |
| python scripts/verify_test_results.py app/build/test-results/testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect | PASS actual local XML, 128 executed tests |
| python -m unittest discover -s scripts/tests -v | PASS 31 infrastructure tests |
| :app:testPersistenceCoverageInputs | PASS 17 real JaCoCo parser/identity synthetic regression cases; temporary fixtures never enter app coverage |
| python scripts/validate_localization.py | PASS en/ca/es |
| python test-fixtures/scripts/verify_fixtures.py | PASS 15 immutable fixtures, hashes/structure/rendering |
| actionlint .github/workflows/ci.yml | PASS official actionlint 1.7.7 |
| Debug app APK ZIP inspection | PASS no fixture PDF/schema assets or preferences protobuf |
| Schema comparison and exact integration diff | PASS byte equality; no unexpected production/contracts/schema changes |
| Live Android instrumentation / physical device | NOT RUN locally; device unavailable |
| Complete offline auditPersistenceCoverage on hosted artifact | BLOCKED missing downloadable evidence |

Existing Gradle/deprecation notices and the fixture verifier's fitz deprecation remain visible;
no policy or test is suppressed. Lint retains the prior 0 errors/20 warnings. Logs and diagnostic
inventory are outside Git under /workspace/partituras-tools; generated app/build files are ignored.
No test or product dependency was added by this audit.

### Policy B follow-up implementation validation

Exactly five changed/new files: the two XML rules, this existing audit report,
scripts/verify_backup_policy.py and scripts/tests/test_backup_policy.py. All are unstaged and
uncommitted at baseline 0f49390724eed9cce00e7eae2f2a6d6872dd8e8d. The original audit is
retained, with its superseded backup recommendation and findings updated rather than recreated.

| Executed follow-up check | Actual result |
| --- | --- |
| bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --max-workers=4 --console=plain | PASS updated XML resources compiled into APK; lint 0 errors/20 existing warnings |
| bash ./gradlew :app:testDebugUnitTest --rerun-tasks --no-build-cache --max-workers=4 --console=plain | PASS fresh 128 JVM tests and existing contract coverage gate |
| python -m unittest discover -s scripts/tests -v | PASS 39 tests (31 existing + 8 backup-policy tests) |
| python -m unittest discover -s scripts/tests -p test_backup_policy.py -v | PASS 8 focused tests; no skips |
| python scripts/verify_backup_policy.py --manifest app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml --apk app/build/outputs/apk/debug/app-debug.apk --aapt2 /workspace/partituras-tools/android-sdk/build-tools/37.0.0/aapt2 | PASS source XML, final merged manifest, compiled APK manifest resource IDs and all three compiled exact-file allowlists |
| python scripts/validate_localization.py | PASS en/ca/es |
| python test-fixtures/scripts/verify_fixtures.py | PASS 15 immutable PDFs |
| Whitespace, allowed-file inventory and schema/manifest integrity | PASS |
| Live backup, restore, D2D or Android instrumentation for this patch | NOT RUN; no emulator/device execution claimed |

Tests reject empty lists, broad directories, absolute/traversal/wrong file paths, wrong domains,
extra Room/main/WAL/SHM/journal includes, missing/duplicate/new transport sections, changed or
missing manifest references, disabled allowBackup and unreviewed custom backup agents. A compiled
XML parsing regression ensures an extra unsafe include is retained and rejected. The verifier
reads aapt2's actual APK trees and resolves manifest references against the packaged resource IDs,
so source-only validation cannot substitute for the artifact check. Synthetic policy fixtures
never enter application coverage. The new Python tests are discovered by the unchanged existing
infrastructure test command; no CI configuration/quality gate/coverage threshold was edited.

The successful hosted CI discussed below predates this uncommitted Policy B patch and therefore
does not validate its new resources/tests. Any future owner-authorized publication requires new
latest-revision mandatory checks; no commit, push, dispatch or merge is performed here.

## 9. Hosted execution, coverage and compatibility evidence

### Infrastructure revision (historical relative to integrated HEAD)

[Run 38607465541](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38607465541)
is visibly **Success**, workflow_dispatch, at `0ee80756a9cb46c56057c4d2b6bf64c142a0d5ab`.
All seven mandatory jobs succeeded; the compatibility matrix also succeeded on API 27 and 36.
The API26 job succeeded with the new gate enabled, so the workflow reports no remaining threshold
failure on that revision. This is independently observed GitHub status, not independently parsed
XML/counter/provenance evidence. The full source tree matches the integrated tree, but these are
different commits and historical checks cannot replace latest-SHA phase-close validation.

The run lists instrumentation-api26-reports (1.38 MB), artifact ID 11915709468, published archive
digest `ea1af31f52e80f207ef5fd0c6cf519234686e683db9ff176f5baf3af14b64d33`.
API27/API36 artifacts are also listed. No archive was obtained or hashed locally.
`gh run download ... --name instrumentation-api26-reports` failed with API Forbidden; the
public artifact URL returned 404. The public page requires sign-in for logs. No credential or
network control was bypassed. Exact executed counts are expected to be 205 Android and 128 JVM;
the enabled API26 validator enforces at least 203/128, so a successful job alone does not prove
the exact 205 count. Obtain XML to establish it. API27/36 smoke verification does not by itself
prove each persistence test executed: inspect their class/method inventory too.

### Integrated revision

[Run 38609224921](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38609224921)
is associated with PR #10 / head `0f49390`, triggered by synchronize. It was IN PROGRESS at
the initial checks, then completed **Success** before this report was finalized. Final snapshot:

| Job | Observed GitHub status |
| --- | --- |
| build | PASS |
| lint | PASS |
| unit-tests | PASS |
| instrumented-api26 | PASS |
| localization | PASS |
| pdf-fixtures | PASS |
| quality-gate | PASS |
| compatibility | SKIPPED by the existing ordinary pull_request condition; this is expected for that run, not phase-close compatibility PASS |

Overall result is **PASS**: the mandatory CI portion of C05 integration is verified, including
the API26 job and its coverage gate. Exact counts, numerical integrated coverage and captured
provenance are still not independently inspected; overall C-phase acceptance remains blocked.
Pull-request workflows can check out a synthetic merge commit;
the run's head association is not proof of the producer's actual checkout commit. Inspect
provenance.build.commit, CI GITHUB_SHA and current base/head before attributing captured evidence.

### Exact coverage audit boundary

The current local debug bytecode inventory was analyzed with JaCoCo 0.8.14. Its denominators
are verified, not combined coverage measurements. These are all handwritten source groups:

| Scope | Source | Executable lines | Branches | Current hosted covered counts |
| --- | --- | ---: | ---: | --- |
| Room | DocumentDao.kt | 17 | 14 | BLOCKED: XML unavailable |
| Room | MetadataDatabase.kt | 5 | 4 | BLOCKED: XML unavailable |
| Room | MetadataEntities.kt | 16 | 0 | BLOCKED; branches N/A |
| Room | MetadataMapping.kt | 50 | 93 | BLOCKED: XML unavailable |
| Room | MetadataReadingPositionPolicy.kt | 5 | 14 | BLOCKED: XML unavailable |
| Room | RegisteredMetadata.kt | 3 | 0 | BLOCKED; branches N/A |
| Room | RegistryDatabaseErrors.kt | 21 | 6 | BLOCKED: XML unavailable |
| Room | RoomDocumentMetadataRepository.kt | 38 | 18 | BLOCKED: XML unavailable |
| Room | RoomDocumentRegistry.kt | 55 | 28 | BLOCKED: XML unavailable |
| **Room** | **Package** | **210** | **177** | **BLOCKED: XML unavailable** |
| DataStore | ApplicationPreferencesStore.kt | 2 | 0 | BLOCKED; branches N/A |
| DataStore | DataStorePreferencesRepository.kt | 13 | 0 | BLOCKED; branches N/A |
| DataStore | PreferencesMapping.kt | 16 | 8 | BLOCKED: XML unavailable |
| DataStore | PreferencesStorageErrors.kt | 4 | 4 | BLOCKED: XML unavailable |
| **DataStore** | **Package** | **35** | **12** | **BLOCKED: XML unavailable** |

Do not substitute earlier JVM-only Room 90/210 lines, 105/177 branches and DataStore 20/35
lines, 12/12 branches for combined measurements. Likewise, the owner-supplied first hosted
results (Room 206/210, 165/177; DataStore 35/35, 12/12) preceded the two construction tests
and are not numerical evidence for this final revision. No inferred new percentages are supplied.
Missing Android evidence is neither zero coverage nor passing coverage.

The enforced thresholds remain 90% LINE and 80% BRANCH independently by package and source;
zero-branch groups are N/A for branches only. Only documented Room/KSP implementation families
are excluded; JaCoCo's standard compiler filtering applies without excluding handwritten paths.
The validator binds compiled class IDs/hashes/probes, fresh sessions, actual adapter probes,
API26, JUnit count/smoke/hash evidence and report denominators to one producer build.

Owner evidence steps: download final run's instrumentation-api26-reports and inspect run.json,
provenance.json, metadata.xml/preferences.xml, all JVM/Android XML and actual exec/ec files;
record producer commit/toolchain/API/session/class IDs and each source/package LINE/BRANCH count.
Check out the captured commit and reproduce retained evidence with :app:auditPersistenceCoverage
as documented by C05-INFRA. Record PASS or genuine threshold FAIL; do not fabricate or use
the invalid historical .ec. Preserve archive digest and run/attempt identity. Do not rebuild
captured class files before auditing them.

For final SHA compatibility, owner uses Actions -> CI -> Run workflow, selects
feat/data-persistence-c and enables compatibility=true. Confirm the ref still points to the
approved final SHA and retain API27/API36 XML. No workflow dispatch was requested or performed
by C06; no trigger/protection changes are authorized.

## 10. Physical Android 8.1 tablet acceptance

Status: **BLOCKED / NOT RUN**. No physical tablet is available to this cloud task. This mandatory
functional-phase requirement is not downgraded to optional. No library/settings UI integration
exists in MainActivity, so clicking through a library or changing settings in UI cannot validate C.

Owner checklist, on a test installation with synthetic data only:

1. Record tablet model, Android version/security patch, API=27, ABI, build type, app/test APK
   revisions and exact SHA. Confirm adb shell getprop ro.build.version.sdk reports 27.
2. Activate the supported JDK/SDK, attach only the intended test device, run
   `bash ./gradlew :app:connectedDebugAndroidTest --max-workers=4 --console=plain` at the final
   revision. Keep XML proving all 205 cases and zero failures/errors/skips, including the smoke test.
   This executes the existing synthetic tests; do not run on a real user's installed data.
3. Review the 84 persistence cases: registration/uniqueness/rollback; rebind and unavailable
   observations; independent writers/collectors; favorite/position preservation and stale revision
   rejection; corrupt rows/protobuf and I/O boundaries; cancellation; default-name creation and
   close/reopen; DataStore enum/field preservation and scope disposal. Record actual outcomes.
4. Verify isolated factory databases use only UUID cache directories; confirm no leftover test
   files after completion and normal application databases remain untouched. Do not capture
   real identifiers, database contents or user documents in public evidence.
5. Record observed failures, lifecycle/resource problems and any OEM differences. Close/reopen
   tests prove reopen behavior, not full process-death/reboot durability. Re-running the suite
   after force-stop/reboot starts new test fixtures and must not be reported as a cross-process
   persistence test. A true restart harness, if required, needs separately authorized scope.
6. Policy B is now approved and implemented; verify actual backup/restore/transfer on
   appropriate API26-30 and API31+ test devices/transports. Confirm excluded metadata is absent
   and permitted preferences, if option B, are restored safely. Record inaccessible/revoked SAF
   situations without claiming restored URI grants. These transport checks are NOT RUN now.

Manual outcome, logs and owner signature remain outstanding. No physical-device or UI acceptance
is implied by emulator status or local APK compilation.

## 11. Confirmed defects, risks and corrective actions

No new confirmed adapter implementation defect requires a production-code correction from
this audit. The previous test-only null applicationContext defect was corrected before PR #11
integration; the corrected file is present. F1/F2 are confirmed configuration/integration facts,
not proof of data loss or disclosure. Policy B resolves F1 statically; its
security acceptance still needs real transport evidence. F2 remains a limitation, mitigated
by excluding Room rather than restoring its stale references. Current behavior cannot be
described as restore-safe merely because registry contracts are capable of later reconciliation.

Risks remain distinct: stale grants/locations after restore, future credential-bearing locators
or new preferences keys, framework exception logging, vendor transport differences, release
history, process-death failure points and full security/dependency review. No invented requirement
is implemented to address them, and no arbitrary severity downgrade closes a mandatory blocker.

## 12. Decisions and final acceptance checklist

Owner decisions required:

- Policy B is already approved and implemented. Obtain transport-validation evidence and keep
  the entire allowlisted protobuf limited to non-sensitive preferences. Choose C only with separately approved safeguards.
- Supply/review final coverage/provenance/XML artifacts and confirm actual checkout/base/head.
- Retain the successful integrated mandatory CI result and obtain compatibility at the final
  accepted revision; any subsequent correction requires new current-revision checks.
- Perform and record physical Android 8.1 acceptance; confirm whether v1 has ever shipped.
- Resolve relevant security/review findings and personally decide integration of PR #10.

| Acceptance item | Status |
| --- | --- |
| Required baseline and correct infrastructure integration | PASS |
| Scope/contract/source correctness review | PASS source audit; no new confirmed functional defect |
| Stable exported v1 schema and no destructive fallback | PASS |
| Fresh local JVM suite, host builds/lint and infrastructure checks | PASS, including Policy B follow-up |
| Integrated head-associated seven mandatory CI checks | PASS public job/run status; actual captured commit remains an artifact-audit item |
| Exact latest coverage/provenance and independent reproduction | BLOCKED artifact access |
| Final-SHA API27/API36 phase-close evidence | BLOCKED; earlier identical-tree run succeeds but is historical |
| Backup Policy B configuration | PASS implemented exact-file allowlist, final merged manifest and APK checks |
| Real backup/restore/device-transfer validation | NOT RUN / BLOCKED device/transport evidence |
| Physical Android 8.1 tablet | BLOCKED / NOT RUN |
| Product library/settings UI and PDF modification acceptance | NOT APPLICABLE to this adapter audit; not implemented by C |
| Released-version migration obligation | NOT APPLICABLE to unchanged v1; release history needs owner confirmation |
| Hosted CI for uncommitted Policy B patch | NOT RUN; previous baseline Success is historical for this delta |
| Human acceptance and merge authorization | Outstanding; never granted by this report |

Technical adapter implementation appears suitable to continue final review, but the persistence
phase is **not technically ready for unconditional final acceptance** while the mandatory evidence,
transport validation and physical-device criteria remain unresolved. Human approval is separate even
after those items pass. Stop for owner review; the five Policy B implementation/report files
remain uncommitted and unstaged.
