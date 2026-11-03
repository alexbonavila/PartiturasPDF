# M Block 2 initial implementation handoff — 2026-11-03

Baseline `c9b2f94a6668fd8dce4e7a179e001e35b54af562`, feat/pdf-safe-write-m.
**PARTIAL SOURCE IMPLEMENTATION; BLOCK 2 ACCEPTANCE BLOCKED.** This is not a
completed safe-writing product or full fault-matrix delivery. Read the limitations
below before considering any test-owned publication. No engine/dependency/build
change or production host integration has been approved/installed.

Owner-provided historical CI #124 at the baseline: nine jobs SUCCESS, JVM logs
674 executed, instrumentation 436 per API26/27/36. Not independently re-read here;
none of these results applies to this new Block 2 code. No current PASS claimed.

## Source inventory

New production files:
- pdf/api/MModificationContracts.kt — immutable plans, narrow failures/reports,
  restricted artifact wrapper (no production minting site).
- pdf/editing/PdfModificationEngine.kt — injectable distinct transform/validation
  boundaries, unsupported defaults; no real engine installed.
- storage/api/MPublicationContracts.kt — restricted capability and typed outcomes.
- storage/transaction/MPublicationJournal.kt — actual v2 sidecar codec/checkpoint
  I/O/state allowlist anchored to unchanged v1 history.
- storage/transaction/MPublisher.kt — one-attempt streaming/fresh readback.
- storage/transaction/MAndroidDestination.kt — confined resolver adapter; no
  installed provider-general create-new/independence proof, no picker or grants.
- storage/transaction/MReadOnlyReconciler.kt — private/hash/read-only observation.
- pdf/workflow/MTransactionCoordinator.kt — admission, private capture/stage,
  engine gate, checkpoints, opaque callback token/session, conditional publication.

Existing M files changed: PrivateSnapshotStore and TransactionSnapshotCapturer
add optional internal transaction-confined capture. Public/default Block 1 capture
semantics unchanged; source/output can now share the journal-owned directory.
No v1 codec/appendPrivate changes. Block 1 tests/resources/fixtures not edited.

New test source: MOperationPlanTest (2 JVM methods), MJournalStateTest (3 JVM),
MPublisherTest (2 JVM), MDocumentsProviderTest (1 Android). Test-only provider
MNewDocumentProvider and exactly its test-manifest declaration added. No production
manifest/resources/assets changed. Five new M documentation files; one Block 1
ledger addendum explains the necessary internal capture namespace adaptation.

## Exact new test methods — all NOT RUN

| Class | Methods / actual capability |
| --- | --- |
| pdf.editing.MOperationPlanTest | planCopiesInputAndPreservesOrderAndQuarterTurns; emptyDuplicateNegativeAndInvalidTurnsReject — pure syntax/immutability, not PDF page existence |
| storage.transaction.MJournalStateTest | versionTwoRoundTripAndTamperRefusal; cannotSkipValidationOrBindUnknownDestination; planSourceDestinationAndSequenceCannotBeRewritten — pure v2 grammar/invariants, not filesystem durability |
| storage.transaction.MPublisherTest | oneAttemptAndFreshExactReadbackWithoutRetry; corruptReadbackAndDeniedRightsNeverMatch — injected in-memory disposable destination, not Android grants or semantic PDF validation |
| storage.transaction.MDocumentsProviderTest | newIdsSameNameNeverOverwriteAndWriteIsOneShot — direct test provider contract, real PFD bytes, NOT ContentResolver/system-picker/OS grant acceptance |

The provider is protected with MANAGE_DOCUMENTS + URI grants in the **test APK**.
Its direct test does not bypass that protection and claim real OS-grant coverage;
it deliberately tests provider implementation without claiming full integration.
The initial provider supports distinct same-name IDs, one write-open and readback,
16-file namespace, teardown of its own disposable namespace only. It does not
implement the complete requested ENOSPC/pipe/close/grant-expiry/fault control matrix.
That remains an explicit unfinished test deliverable, not a skipped or passed test.
No new test uses private/user PDFs. Canonical 15-fixture corpus unchanged.

## Literal M-T01–M-T20 ledger

All execution at this revision is NOT RUN. IMPLEMENTED TEST SOURCE never means PASS.
Block 1 exact method inventory remains in its ledger; this table names specific
examples rather than treating existence as full scenario acceptance.

| Case | Status / exact source and remaining proof |
| --- | --- |
| M-T01 | IMPLEMENTED TEST SOURCE — Block1PrivateStorageTest.canonicalFixtureCapturePreservesOriginalAndRegisteredAssociationIsOptional; transaction-confined capture unexecuted |
| M-T02 | IMPLEMENTED TEST SOURCE — Block1PrivateStorageTest.observedLimitNotProviderMetadataControlsPipeCopy; full-size permutations pending |
| M-T03 | IMPLEMENTED TEST SOURCE — PrivateSnapshotStoreTest.cancellationAfterSealingSurvivesExceptionalOwnerCleanup; genuine provider revocation pending |
| M-T04 | BLOCKED — captured-byte boundary documented, no new externally mutating source runtime campaign |
| M-T05 | IMPLEMENTED TEST SOURCE — PrivateSnapshotStoreTest.writeCloseAndRereadFailuresNeverEscapeAsCaptured; no public write on rejected preparation |
| M-T06 | BLOCKED — no approved semantic parser/validator for fixtures 14/15 |
| M-T07 | BLOCKED — no real independent order/rotation/resources/annotations checks for 08/09/10/11/12/13 |
| M-T08 | BLOCKED — MPublisherTest.oneAttemptAndFreshExactReadbackWithoutRetry proves only injected byte adapter; full validated coordinator happy path not yet tested |
| M-T09 | IMPLEMENTED TEST SOURCE — MPublisherTest.corruptReadbackAndDeniedRightsNeverMatch; real READ/WRITE grant/revocation matrix unfinished |
| M-T10 | IMPLEMENTED TEST SOURCE — MDocumentsProviderTest.newIdsSameNameNeverOverwriteAndWriteIsOneShot; provider-general alias proof/host acceptance deferred |
| M-T11 | BLOCKED — partial/short/ENOSPC/provider-loss/close fault matrix incomplete |
| M-T12 | IMPLEMENTED TEST SOURCE — Block 1 cancellation identity tests; new every-public-boundary cancellation tests still incomplete |
| M-T13 | IMPLEMENTED TEST SOURCE — MJournalStateTest.versionTwoRoundTripAndTamperRefusal and Block 1 journal tests; full v1/v2 migration/restart/fsync fault tests unfinished |
| M-T14 | DEFERRED TO HOST — actual permission/result flow; confined Android adapter source exists, not runtime verified |
| M-T15 | IMPLEMENTED TEST SOURCE — no registry writes in new source; full metadata-invariance integration pending |
| M-T16 | IMPLEMENTED TEST SOURCE — existing MStorageBudgetTest extreme/boundary cases; actual engine heap/CPU BLOCKED by dependency gate |
| M-T17 | BLOCKED — test DocumentsProvider direct source exists; genuine resolver/pipe/grant/fault acceptance not completed |
| M-T18 | BLOCKED — no-backup placement/manifest boundaries source-reviewed, release APK and backup verification NOT RUN |
| M-T19 | IMPLEMENTED TEST SOURCE — existing exceptional owner/cleanup regressions; new coordinator exceptional-cleanup/cancel matrix incomplete |
| M-T20 | DEFERRED TO HOST — no production UI/overwrite/delete/WRITE tree path; source prohibition preserved, no claim of host acceptance |

## Fixtures / resource / privacy boundaries

All 15 canonical originals/manifest remain unchanged, instrumentation-only assets.
Future engine matrix: 01 basic, 02 select/reorder, 03/04/05 increasing pages,
06/07 exact manifest cases, 08 boxes, 09 rotations, 10 scans, 11 vectors/fonts,
12 annotations, 13 complex, 14 protected rejection, 15 corruption rejection.
No semantic operation is established for any fixture by source-only plan tests.
Private URI identity is journal-only, bounded; errors/logs contain no document
text/private path/token logs. No backup/restore/package execution evidence added.
64/128/256 MiB pool, 64 MiB free margin and existing cleanup/accounting are reused;
parser memory remains unmeasured. Caller supplies one shared root/ledger/gate.

## Residual defects / incomplete work requiring review before acceptance

- Native transform/independent semantic checks/report completeness BLOCKED by
  the engine gate. Defaults reject; no production token mint, copy fallback or
  installed create-new provenance verifier. No user-facing Save As.
- Initial new coordinator/session source needs comprehensive exception/cancellation,
  callback race, alias/permission and final-checkpoint fault tests. Do not call this
  a complete validated vertical slice. A retained unknown session holds admission;
  no owner-resolution release API/automatic write replay is supplied.
- V2 anchoring is sidecar-compatible with v1 decoding but legacy PrivateRecovery
  intentionally blocks sidecars; new reconciler handles v2 observation separately.
  Safe migration/retention lifecycle and cleanup of terminal verified artifacts
  (currently missing artifacts make a restart scan BLOCKED) require further review.
- Operation plan syntactic construction uses require for invalid input. Public
  validation reports are initial diagnostic grammar, not full operation-specific
  semantic reports. No claim that out-of-range PDF indices are validated without
  parsing. These remain engine/acceptance work, not permissions to fake support.
- New adapter tests do not exhaust provider-specific w-mode behavior, close/read
  blockage, native resources or identity aliasing. No universal Android/provider
  atomicity, bounded blocking-response, power-loss or same-UID security promise.

## Owner commands — WRITE ONLY, NOT EXECUTED

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
bash ./gradlew :app:testDebugUnitTest
python scripts/validate_localization.py
python test-fixtures/scripts/verify_fixtures.py
# Only after selecting and separately authorizing the exact owned API26 device:
ANDROID_SERIAL='<owner-authorized-serial>' bash ./gradlew :app:connectedDebugAndroidTest
```

Require exact new identities in XML/raw instrumentation with no failures/errors/
unexplained skips, attributable source/APK/device/API and retained incomplete logs.
API27/API36 and applicable physical Android8.1 are owner-run, not inferred from
prior CI. Build success is not runtime semantic validation.

BUILD / JVM TESTS / INSTRUMENTATION / LINT / COVERAGE / CI / DEVICE-MANUAL:
**NOT RUN**. Coverage additionally BLOCKED pending separately approved exact
M-scoped build configuration; see M_BLOCK2_COVERAGE_PROPOSAL.md.
No tests, SDK tooling or GitHub Actions were executed or dispatched by Codex.

## Approved continuation increment — 2026-11-03

Baseline 61951a84a1da6e221e9e80d89149ed4b7075504e. Prior partial-scope statements
remain historical. R1–R4 now have explicit source corrections: owner-controlled
uncertainty disposition; durable cleanup authorization/completion; verified
publication + independent cleanup status; late construction of truthful preparation
rejections. R6 has additional strict legacy/v2 recovery and journal fault source.
R5 now includes a real negative resolver boundary, not claimed positive OS grants.

Changed implementation: MTransactionCoordinator, MPublicationContracts,
MPublicationJournal, MPublisher, MReadOnlyReconciler, TransactionSnapshotCapturer.
The capturer implements internal MSourceCapturer so the actual coordinator can
be exercised without Android FD stubs in JVM tests. Production still uses actual
Block 1 openRead/capture; no replacement runtime host wiring. Native lease copying
remains the production default, with internal MLeaseCopier used by explicit tests.
No production semantic token mint was added. Test validator minting is TEST ONLY
byte-plumbing and is NOT evidence that any PDF is semantically preserved.

New test files/helpers: MTransactionLifecycleTest, MJournalRecoveryTest, MTestRig.
Updated MDocumentsProviderTest and testprovider/MNewDocumentProvider. No other
phase/core/SAF/Reader/host/DI/Gradle/CI/policy/fixture edits. No manifest change
in this increment: prior test-only registration retains MANAGE_DOCUMENTS.
Documentation changed: this ledger, contracts/state machine, engine evaluation.

### New JVM methods (26; all NOT RUN)

MTransactionLifecycleTest (14):
- verifiedCleanupIsDurableAndAdmissionReopens
- verifiedWithPendingCleanupStillReleasesAdmissionAndRetainsCharge
- expectedPreparationRejectionReportsActualCleanupDebt
- unexpectedPreparationCleanupIsVisibleWithoutReplacingReason
- missingCallbackNeedsExplicitDurableOwnerAcknowledgement
- staleAndDuplicateTokensNeverWriteTwice
- resolutionWaitsForActiveWriterAndCannotEnableReplay
- cancellationKeepsExactIdentityAndRequiresResolutionAfterWriterStops
- publicFaultsNeverReturnVerifiedOrRetryAndCanBeExplicitlyResolved
- failedFinalAckStaysUnverifiedAndExplicitResolutionDisclosesUnconfirmedRecord
- simultaneousDuplicateCallbacksHaveExactlyOneWrite
- productionEngineGateRejectsWithoutPickerAndReturnsAdmission
- primaryValidatorExceptionSurvivesAllCleanupDefects
- mismatchedOperationSourceAliasAndRevocationBeforeOpenNeverWrite

MJournalRecoveryTest (12):
- everyStateReconstructsConservativelyWithoutWriteOrReplay
- preterminalMissingFilesBlockButAuthorizedPartialCleanupRemainsVerified
- legacyChainRemainsReadableAndV2AnchorTamperingBlocks
- syncRenameCloseAndWriteFaultsNeverAcknowledgeAndRetainOldRecord
- tornTemporaryGapReorderedAndChecksumChangedRecordsBlockWithoutDeletion
- metadataLimitsUnknownVersionAndPredecessorMutationReject
- rereadTamperingNeverAcknowledgesNewRecord
- legacyScannerCannotTreatV2TransactionAsOrdinaryPrivateSuccess
- cancellationDuringCheckpointNeverReturnsDurableAcknowledgement
- absentRightsAndMismatchedReadbackNeverUpgradeVerifiedHistory
- authenticatedPredecessorRewriteStillFailsChainBinding
- unexpectedSidecarAndSymbolicLinkBlockBeforeProviderRead

Loops exercise each state and multiple faults explicitly; method count is not a
coverage percentage. Filesystem fault source covers write/fsync/close/rename/
directory/reread, sequence/anchor/torn/temp/metadata tampering and original record
survival. Object reconstruction is NOT real OS process death or power-loss proof.
New helpers use actual bounded private files and hashes with injected filesystem
barriers and native-descriptor seam. Real PFD/Android fsync remain instrumentation.

### Android provider methods / exact capability

MDocumentsProviderTest retains its same-name one-shot direct provider method and
adds three NOT RUN methods:
- providerRefusalAndCorruptReadbackAreExplicitNotOsGrantClaims
- reliablePipePartialAndCloseErrorHaveDeterministicErrorSignal
- realResolverWithoutManageDocumentsOrUriGrantCannotWriteTestProvider

Direct attachInfo metadata now matches framework DocumentsProvider requirements:
exported/grant-enabled and MANAGE_DOCUMENTS read/write permission. This corrects
the initial source's unsupported exported=false/missing-permission metadata; no
claim the prior test had executed. Provider fault modes include create/write/read
refusal, corruption, reliable pipe, partial write and error-on-peer-close. The
owned worker reports bounded signals and closes/joins its own resources; provider
namespace is disposable/test APK only, never a user's existing document.

The resolver test genuinely attempts an OS-permission-protected open without
adopting shell identity or acquiring a grant. Positive resolver create/write,
true URI grant/revocation and real system picker flow require separately authorized
host/system-owned grant execution. They remain DEFERRED TO HOST, not simulated as
PASS. Android provider errors represent controlled failure, not real network/OS
death. Unresponsive third-party provider blocking remains a platform limitation.

Current Block 2 inventory: 33 JVM methods plus four Android methods written. All
are NOT RUN; prior Block 1 test counts/CI remain separate. None proves coverage.

### Updated M-T01–M-T20 source allocation (no executed PASS)

| ID | Continuation source/proof status |
| --- | --- |
| M-T01 | IMPLEMENTED TEST SOURCE — lifecycle verified/cleanup tests reuse sealed real byte hashes; Android canonical source test remains Block 1 |
| M-T02 | IMPLEMENTED TEST SOURCE — existing observed-byte and budget boundary tests unchanged; native ceiling execution pending |
| M-T03 | IMPLEMENTED TEST SOURCE — cancellationKeepsExactIdentityAndRequiresResolutionAfterWriterStops; true provider rights deferred |
| M-T04 | BLOCKED semantic-current-provider/engine proof; captured snapshot remains byte-only identity |
| M-T05 | IMPLEMENTED TEST SOURCE — expected/unexpected preparation cleanup tests, existing staging faults |
| M-T06 | BLOCKED — genuine corrupt/protected parser decision requires approved engine |
| M-T07 | BLOCKED — genuine page/rotation/content/annotation fidelity requires independent semantic validator |
| M-T08 | IMPLEMENTED TEST SOURCE — verifiedCleanupIsDurableAndAdmissionReopens uses privileged TEST-ONLY validator/destination/copier; not real PDF/grant acceptance |
| M-T09 | IMPLEMENTED TEST SOURCE — publicFaults... denied/revalidate/open; real negative resolver test; positive OS rights deferred |
| M-T10 | IMPLEMENTED TEST SOURCE — existing distinct IDs/same name and duplicate callback tests; provider-general alias proof remains gated |
| M-T11 | IMPLEMENTED TEST SOURCE — partial/close/readback corrupt/short faults, reliable pipe error source; not real network loss |
| M-T12 | IMPLEMENTED TEST SOURCE — cancellation at active writer and checkpoint, duplicate/late owner disposition; no rollback/retry |
| M-T13 | IMPLEMENTED TEST SOURCE — everyStateReconstructs..., legacy/v2 anchor, every durability seam, cleaned terminal and mandatory missing files |
| M-T14 | IMPLEMENTED TEST SOURCE / DEFERRED TO HOST — simulated revalidation/refusal and negative resolver permission, genuine grant transitions not run |
| M-T15 | IMPLEMENTED TEST SOURCE — no registry calls, source registration/checkpoint APIs unchanged; no new logical output ID |
| M-T16 | IMPLEMENTED TEST SOURCE — retained quota, cleanup debt/admission separation, prior extreme sizes; engine heap/CPU still BLOCKED |
| M-T17 | IMPLEMENTED TEST SOURCE / DEFERRED TO HOST — test provider + real resolver denial; positive resolver/picker permission acceptance not fabricated |
| M-T18 | BLOCKED execution — no-backup/private/test-only placement preserved; release packaging/backup validators NOT RUN |
| M-T19 | IMPLEMENTED TEST SOURCE — primary/cancel identity and cleanup fault paths, repeat owner resolution, no false COMPLETE |
| M-T20 | IMPLEMENTED TEST SOURCE — explicit tests never retry callback/public writes; no host/UI/root-WRITE/registry/delete production path |

All 15 original PDF fixtures/manifest remain unchanged. The supplied research is
recorded with its owner-provided provenance in M_ENGINE_EVALUATION.md, including
BC CVEs and reachability limitations; no automatic transitive override or engine
approval inferred. One test-only engine-spike decision sheet is ready for owner
review; production engine/semantic fidelity remains BLOCKED.

Coverage measurement for M implementations remains NOT CONFIGURED / BLOCKED.
The existing proposal lists source packages/public class names; an ACTUAL COMPILED
JaCoCo class denominator cannot be asserted without owner compilation and separate
build-only approval. No 90%/80% PASS or arbitrary generated exclusions are claimed.

Full diff/whitespace/scope inspection only. BUILD / JVM TESTS / INSTRUMENTATION /
LINT / COVERAGE / CI / DEVICE-MANUAL / VALIDATORS: **NOT RUN**. Owner should first
run the existing build/lint/full JVM/CI against the new exact SHA, then separately
owned API26/27/36 and relevant physical acceptance. The prior SHA's CI does not
substitute for this revision. No PR, merge, tag, release or production host wiring.

## Publication-result / reliable-descriptor correction — 2026-11-03

Baseline: e7bf0512bada0126f47d24fc971d53bbbaf4ad1b. Exact scope: coordinator,
MAndroidDestination, their existing JVM/Android test files and this ledger only.

After durable VERIFIED_COMMITTED, the session stores its verified identity before
any private cleanup. Unexpected cleanup failures return that same verified public
identity with cleanup PENDING, release admission in finally, retain uncertain files
and charges, and remain observable through cleanupFailure(). verifiedPublication()
retains the public outcome even if caller cancellation propagates after commit.
Diagnostics are internal in-memory Throwables, never serialized/logged or exposed
as user-facing text. releaseSnapshots still attempts all owners and preserves
primary/suppressed failures; no worker failure becomes false Released/COMPLETE.

JVM regression added (NOT RUN):
MTransactionLifecycleTest.exceptionalCleanupCannotEraseDurablyVerifiedPublication
asserts verified identity/evidence, pending cleanup, observable defect, durable
cleanup-authorized state, retained files/charge and immediately reusable admission.

### API26 source evidence, not runtime reproduction

Read-only inspection of AOSP android-8.0.0_r1 ParcelFileDescriptor.java:
https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/core/java/android/os/ParcelFileDescriptor.java
Relevant symbols: AutoCloseOutputStream.close (lines 953–968), closeWithStatus /
writeCommStatusAndClose (737–812), checkError (873–894). The stream closes the PFD
and superclass stream but does not checkError. PFD close may cache a remote status
without throwing it. checkError is nonblocking: no status yet is not peer success.
No Android runtime, SDK execution, Gradle or CI was used to reach this conclusion.

The adapter now uses internal writeNewDescriptor: write/flush, checkError before
closure, owned stream closure, then checkError again for cached peer errors.
Copy/cancellation/provider primary errors survive; close and additional peer errors
are suppressed. If no earlier primary exists, an observed peer error takes priority
over a secondary close failure. A reliable writer lacking affirmative completion
proof is rejected as unconfirmed (even if currently silent); it cannot enter a
readback-match/verified flow. This intentionally limits reliable pipe publication;
regular descriptors retain ordinary fresh-readback + durable-checkpoint semantics.
No polling, sleeps, detach/dup, undocumented flags, reflection, permission escalation
or provider-write retry is introduced. There is no universal late-provider or
hardware-durability guarantee for ordinary descriptors.

Five Android regression methods added (all NOT RUN):
- autoCloseAloneCanLeaveReliablePeerErrorUnthrown — owner-run reproduction using
  the existing disposable provider's reliable close-error descriptor.
- checkedReliableProviderErrorCannotReachReadbackMatch — same provider/helper
  used by the real adapter; publisher remains Uncertain, readback not attempted.
- primaryCopyAndSecondaryCloseFailuresSurviveReliablePeerError — exact primary
  identity, injected close failure AFTER real closure, real peer error retained.
- reliableWriterWithoutPeerCompletionNeverAssumesSuccess — open reliable pair
  without an acknowledged peer, no timeout/racy retry as proof.
- checkedRegularDescriptorStillWritesAndClosesSuccessfully — regular provider FD
  remains usable and exact bytes survive helper closure.

These direct provider/descriptor tests do not claim system-picker, positive OS
URI-grant or actual resolver success. They establish only test SOURCE until the
owner executes them. Current Block 2 source inventory: 34 JVM / nine Android
methods, not executed test counts. Existing methods/assertions remain enabled.

Full diff and git diff --check reviewed. BUILD / JVM TESTS / INSTRUMENTATION /
LINT / COVERAGE / CI / DEVICE-MANUAL: NOT RUN. Runtime reproduction and compilation
remain pending owner execution. No engine installed, no Gradle/CI/policy/core/SAF/
Reader/host/registry/fixture changes. Publication is for review, not acceptance.

## CI #125 test compilation / provider startup correction — 2026-11-03

Owner-reported failure at 0cf23c05b97a6b3e3fb486f567cbf71f3d8b8c7e:
https://github.com/alexbonavila/PartiturasPDF/actions/runs/41621001737
No CI/log access or execution was performed by Codex for this correction.

1. MJournalRecoveryTest.chain is an MTestRig extension. Its receiver's OrderedPagePlan
   named plan shadows the test class's String fingerprint at the journal call.
   Rename that String planFingerprint and explicitly qualify every use as
   this@MJournalRecoveryTest.planFingerprint. No journal production behavior,
   assertion or regression method is changed.
2. Logcat supplied by the owner identifies Fault enum initialization as the cause
   of NoClassDefFoundError: kotlin/enums/EnumEntriesKt during ActivityThread provider
   installation on API26/27/36, before the relevant tests execute. This is an early
   test-provider class-loading boundary, not proof of a PDF/publication defect.
   Replace the enum with nested integer constants (same eight named modes, values
   0–7). Existing Fault.NAME test call sites compile against constants without enum
   entries initialization. No Kotlin/library/Gradle/packaging workaround is added.

Provider initializer inspection: fault is a primitive Int; pending/pipes use Java
HashSet/ArrayList constructors; directory is initialized by onCreate; failure and
coroutine backing fields start null. CompletableDeferred and CoroutineScope/
SupervisorJob/Dispatchers creation occurs only through synchronized accessors when
fault tests actually need them, not during provider construction/onCreate. This
avoids an eager coroutine/helper startup dependency, without weakening fault modes.
Shutdown does not create a worker scope merely to cancel it; any existing scope is
still canceled/joined. Pipe-list writes and shutdown snapshots use the same lock.
The compiler's actual DEX/class-loader result remains unverified until owner runs.

Exact scope: MJournalRecoveryTest.kt, testprovider/MNewDocumentProvider.kt, this
ledger. No production logic, manifest/authority/permission/provider isolation,
reliable error injection, existing method or assertion changes. Original and M
provider authorities stay distinct and test-APK-only. All earlier PASS/FAIL/
BLOCKED/NOT RUN evidence retains its original revision/provenance.

Full diff reviewed and git diff --check performed. BUILD / JVM TESTS / ANDROID
INSTRUMENTATION / LINT / COVERAGE / CI / DEVICE-MANUAL: **NOT RUN**. No claim that
CI #125 is now passing or that provider startup is runtime verified. Owner must
compile/run the new exact SHA and retain API26/27/36 test/process evidence.
