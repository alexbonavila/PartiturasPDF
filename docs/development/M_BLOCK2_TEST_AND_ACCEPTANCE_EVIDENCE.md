# M Block 2 initial implementation handoff — 2026-11-03

> Current evidence update (2026-11-09): [scoped fidelity PASS and open security gates](M_PDFBOX_INDEPENDENT_VALIDATION.md#completed-owner-campaign--2026-11-09). Earlier dated records below remain historical.

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

## CI #126 targeted test corrections — 2026-11-03

Owner-reported run https://github.com/alexbonavila/PartiturasPDF/actions/runs/41625091669
at baseline 137fa2d00e51ed04f262c5792316d5b63d000585. Build/Lint/localization/
fixtures succeeded; JVM reported 708 executed/one failure; API26 was blocked by
the JVM task; API27/API36 reported instrumentation failures. These are historical
owner-supplied results, not execution by Codex or results for this new revision.

A — MTransactionLifecycleTest's cancellationKeepsExactIdentityAndRequiresResolutionAfterWriterStops
cancels the writer job with Job.cancel(cancellation), then observes coroutine
cancellation through suspend/dispatch boundaries. That API does not promise the
same Throwable object (coroutine recovery/wrapping may preserve reason instead).
Replace only this unsupported identity check with expected CancellationException
message, an explicit observed flag, canceled job state, no verified publication,
retained quota/two sealed artifacts, blocked admission, explicit owner resolution
and one write. Original direct-throw/propagation identity tests elsewhere remain
unchanged. The historical method identity is retained to preserve inventory.

B — MDocumentsProviderTest.autoCloseAloneCanLeaveReliablePeerErrorUnthrown used
expression-bodied runBlocking ending in assertThrows, which returns a Throwable;
its JVM return was non-void, so JUnit4 rejected the class before its tests ran.
All nine @Test declarations were inspected. Convert all six expression-bodied
runBlocking test methods into block-bodied Unit functions. The three existing
block-bodied methods remain unchanged. Every assertion, descriptor/fault/permission
scenario, provider cleanup and method name is retained; no provider/production/
manifest/dependency changes or synthetic successes.

C — ReaderScreenTest.renderAndAttachmentWithoutVisibleScreenNeverAcknowledge
had a source-demonstrable synchronization gap: ReaderController.nextWork publishes
VISIBLE/presentedPage with checkpoint Scheduling before returning Work.Ack;
execute(Work.Ack) invokes perform(), whose distinct scope.async task calls
route.displayed(). The fake route adds to a CopyOnWriteArrayList. visible() only
polls phase/page; it does not wait for the route observation. This test inherits
Main.immediate, not an assumed IO dispatcher. Another test thread can observe
state publication before the Ack task has appended its observation.
ReaderPageSurface signals presentation after draw/unlock/post with identity/visible
checks; merely attaching/detaching does not invoke route.displayed.

Only the affected Reader test now waits for the thread-safe observed collection
to become nonempty, with the existing 10,000 ms Compose wait limit, then performs
the unchanged exact listOf(PageIndex(0)) assertion in runOnIdle. Waiting for
nonempty does NOT accept at-least-once: wrong or duplicate observations fail the
exact assertion rather than being filtered out. Pre-visibility empty-list checks
and frame/bitmap checks are unchanged. No synthetic acknowledgements, sleeps,
timeout increases, retries or changes to the shared visible() helper/other tests.
No production Reader defect is demonstrated by this specific ordering gap; runtime
behavior of the correction remains to be verified by the owner.

Exact four-file scope: MTransactionLifecycleTest.kt, MDocumentsProviderTest.kt,
conditionally authorized ReaderScreenTest.kt (one method only), this ledger.
No test disabled or skipped, no production/protected behavior changed. Full diff
and whitespace review only. BUILD / JVM TESTS / ANDROID INSTRUMENTATION / LINT /
COVERAGE / CI / DEVICE-MANUAL: **NOT RUN**. Owner must execute CI at the published
SHA; no passing-CI, engine-acceptance or Block 2 completion claim is made.

## CI #127 manual-provider storage context correction — 2026-11-03

Owner-reported run https://github.com/alexbonavila/PartiturasPDF/actions/runs/41641563085
at d2692a365821a1c9eb80048254d3622e793d903e: build/JVM/Lint/localization/fixtures
succeeded. Each API26/27/36 run reported seven MDocumentsProviderTest failures
out of 445 tests, with 438 passing, including the prior Reader regression.
Logcat showed no_backup mkdir EACCES/permission denied on API26/27 and ENOENT on
API36. These are owner-provided historical results, not new Codex execution.

Both manually attached providers received instrumentation.context (test APK
package com.abdev.partituraspdf.test), while the test executes in the target
application process. Private no-backup access through that context can address
a different package/UID filesystem namespace. Replace it with targetContext
(com.abdev.partituraspdf) in newIdsSameNameNeverOverwriteAndWriteIsOneShot and the
shared provider() helper. This does not change the actual manifest-registered
provider's context or grant any permissions; it fixes only manual test instances.

Conditional provider change: onCreate previously assigned its directory and
returned mkdir()'s Boolean. The attachInfo call sites have no returned Boolean
to validate, so a failed initialization could be followed by createDocument and
an indirect FileNotFoundException. Now require successful mkdir explicitly, then
assign the owned directory and return true. Failure throws a sanitized initialization
error before document operations; shutdown cannot own a failed candidate. Same
noBackupFilesDir, unpredictable UUID child, bounded canonical document IDs and
no public/external fallback. No runtime/external storage permission is requested.

Shutdown still closes its owned descriptors, stops/joins its existing workers,
preserves workerFailure and deletes only files in the uniquely owned test child,
then that child. No new recursive deletion, retries, sleeps or suppressed cleanup
errors. All fault modes and real descriptor/error behavior remain unchanged.
Provider authority, MANAGE_DOCUMENTS, exported/grantUriPermissions and the entire
manifest are untouched; all providers/fixtures remain test-APK-only.

Exactly three files changed: MDocumentsProviderTest.kt, testprovider/
MNewDocumentProvider.kt and this ledger. All nine enabled Android methods and all
assertions retained:

- newIdsSameNameNeverOverwriteAndWriteIsOneShot
- providerRefusalAndCorruptReadbackAreExplicitNotOsGrantClaims
- reliablePipePartialAndCloseErrorHaveDeterministicErrorSignal
- realResolverWithoutManageDocumentsOrUriGrantCannotWriteTestProvider
- autoCloseAloneCanLeaveReliablePeerErrorUnthrown
- checkedReliableProviderErrorCannotReachReadbackMatch
- primaryCopyAndSecondaryCloseFailuresSurviveReliablePeerError
- reliableWriterWithoutPeerCompletionNeverAssumesSuccess
- checkedRegularDescriptorStillWritesAndClosesSuccessfully

Full diff and git diff --check reviewed. BUILD / JVM TESTS / ANDROID
INSTRUMENTATION / LINT / COVERAGE / CI / DEVICE-MANUAL: **NOT RUN**. Directory
creation, provider startup and API26/27/36 runtime outcomes require owner CI on
the published SHA. No production, Reader, SAF, host, registry, dependency, Gradle,
manifest, fixture or quality-gate changes. Block 2 completion is not claimed.

## CI #128 owner-executed acceptance evidence — 2026-11-03

Verified GitHub Actions run: https://github.com/alexbonavila/PartiturasPDF/actions/runs/41647494002
(run ID `41647494002`, attempt 1, workflow `CI`).
Exact tested branch: `feat/pdf-safe-write-m`.
Exact tested commit: `edd372aaf6ecc73d78e67655cf8996a2569f0fdf`.
GitHub run status: **completed**; conclusion: **success** (2026-11-03 13:56:40 UTC).
These findings were independently checked against GitHub's run/job results and
the relevant job logs; they replace the *pending owner validation* status of
the preceding source-only addendum **for this exact tested SHA**, not for
future commits or unimplemented Block 2 capabilities.

| Mandatory CI job | Verified result | Specific job evidence |
| --- | --- | --- |
| `build` | **PASS** | GitHub job conclusion success |
| `lint` | **PASS** | GitHub job conclusion success |
| `unit-tests` | **PASS** | `PASS 708 executed test(s); no failures or skips` |
| `instrumented-api26` | **PASS** | Android 8.0 / API 26: `PASS 445 executed test(s); no failures or skips` |
| `compatibility-api27` | **PASS** | Android 8.1 / API 27: `PASS 445 executed test(s); no failures or skips` |
| `compatibility-api36` | **PASS** | Android 16 / API 36: `PASS 445 executed test(s); no failures or skips` |
| `localization` | **PASS** | GitHub job conclusion success |
| `pdf-fixtures` | **PASS** | GitHub job conclusion success |
| `quality-gate` | **PASS** | GitHub job conclusion success; mandatory result checks success |

The source-level CI #127 repair is now **runtime-validated on CI**: both
manual `MDocumentsProviderTest` attachments use `targetContext`, and
disposable directory creation fails explicitly before any document operation.
All nine enabled provider-test methods are included in the successful
instrumentation suite; no separate positive system-picker/OS-grant acceptance
is thereby established. The earlier Reader synchronization and JVM
cancellation regressions also executed within the passing suites.

**Acceptance interpretation:** All nine mandatory jobs PASSED on the tested
commit; this validates the current implemented and CI-covered Phase M Block 1/2
source increment. It does **not** constitute Phase M Block 2 final acceptance.
No approved production editing engine/semantic transform-and-validator,
no independent full PDF fidelity acceptance, no positive host picker/URI-grant
integration, and no physical-device/manual acceptance were established.
The M-specific `storage/transaction`, `pdf/editing` and `pdf/workflow`
coverage proposal remains **NOT CONFIGURED / NOT MEASURED**; the existing
quality-gate success must not be represented as a >=90% line / >=80% branch
result for those new implementation packages. Engine and coverage approvals
remain separate owner decisions.

This addendum is documentation-only and was committed *after* the tested
`edd372a` commit. CI #128 is **not** CI evidence for the new documentation
commit SHA. No tests or workflows were executed as part of this addendum.

## Isolated PDFBox-Android test spike — source delivery, 2026-11-03

Owner-approved baseline: `32ad93fdcffc2273bc76617bc6880e0f9a5dbe6b`.
The preceding CI #128 addendum remains unchanged and applies solely to
`edd372aaf6ecc73d78e67655cf8996a2569f0fdf`, not to this new dependency/test source.

Pinned `com.tom-roush:pdfbox-android:2.0.27.0` is added ONLY through the catalog
and `androidTestImplementation`. Three new files in the Android-only
`pdf/engine/spike` package define 19 enabled candidate test methods and two
helpers. No existing test, manifest, production package, engine default, artifact
minting, provider permission, host contract or fixture is changed. No JCenter,
optional codec, BC override, toolchain/repository/coverage/CI change.

See [spike protocol and exact test matrix](M_PDFBOX_ANDROID_TEST_SPIKE.md) and
[dependency/security source ledger](M_ENGINE_EVALUATION.md#test-only-spike-authorization-and-evidence-ledger--2026-11-03).
All 15 fixtures participate through the existing manifest/assets; only private
disposable copies are transformed. Working-copy and stock-import approaches are
compared with saved/reopened page/resource/annotation oracles; a failure is not
silently repaired. Independent PDFium raster comparisons are additional evidence;
separate owner Python checks, actual binary isolation, real JPX rejection proof,
security/risk review and physical resource acceptance remain unresolved.

Exactly two operations each for the 50/100-page cases, phase endpoint timing/heap/
native/PSS observations, tracked handle closure, source rehashing, output/incomplete
input faults and deliberately corrupted semantic outputs are source-only tests.
No invented measurements, parser heap cap, finite-run leak-freedom or production
readiness claim. Optional explicit retention concerns synthetic copies only; no
automatic PDF artifact upload. Default teardown removes its own namespace.

Read-only upstream metadata/source/advisory inspection, full diff review and
`git diff --check` only. BUILD / JVM / ANDROID / LINT / COVERAGE / DEPENDENCY
RESOLUTION / SECURITY SCAN / CI / DEVICE / PROJECT VALIDATORS: **NOT RUN**.
Owner executes existing CI/API26/27/36 and packaging/dependency/oracle checks at
the new exact publication SHA. Test source readiness is not test PASS, engine
approval, M-scoped coverage acceptance or Block 2 completion.

## CI #129 PDFBox spike asset context correction — 2026-11-03

Owner-reported run https://github.com/alexbonavila/PartiturasPDF/actions/runs/41672861285
at exact tested SHA `648c80c9b28de0eb4f1be3570f609af9e06e9859`:
build, JVM (708 PASS), Lint, localization and PDF fixtures PASS (five jobs);
API26/27/36 and quality-gate FAIL (four jobs). Each Android run: 464 executed,
449 PASS / 15 FAIL; all 445 existing tests plus four new candidate tests passed,
15 of the 19 PDFBox tests failed. This is owner-provided historical evidence,
not new execution or independent CI access by Codex.

`ExceptionInInitializerError` originates from `PDFBoxResourceLoader.getStream`
missing `com/tom_roush/pdfbox/resources/glyphlist/additional.txt` during
`LegacyPDFStreamEngine` static initialization. Later `NoClassDefFoundError` for
`PDFTextStripper` is consistent with that poisoned initialization, not proof of
a missing dependency or a transformation/fidelity defect.

The sandbox passed the target application context to the loader despite candidate
resources being instrumentation-only. Pinned upstream init reads the supplied
context's application-context assets. Now fixture access remains on
`testContext.assets`, loader init receives `testContext`, and filesystem storage
remains under `targetContext.noBackupFilesDir`. Before namespace creation or text
initialization, a bounded preflight opens both additional.txt and glyphlist.txt
from `testContext.applicationContext.assets`, checks one nonempty byte and closes
each stream. Missing/empty assets fail with exact asset/package diagnostics;
unavailable application context also fails explicitly. No copying, replacement,
permission, dependency or production fallback. Existing post-mkdir init cleanup
preserves the primary and any suppressed directory-cleanup failure.

See [correction details and remaining gates](M_PDFBOX_ANDROID_TEST_SPIKE.md#ci-129-resource-loader-correction--2026-11-03).
Exactly PdfboxSpikeSandbox.kt and the two authorized spike/Block 2 documents change.
All 19 enabled tests and assertions remain intact. androidTestImplementation scope,
version, manifest, fixtures, production, Reader/SAF/provider contexts and quality
gates are unchanged. True future candidate failures must not be masked.

Full diff and git diff --check reviewed. BUILD / JVM / ANDROID INSTRUMENTATION /
LINT / COVERAGE / CI / DEVICE / PROJECT VALIDATORS: **NOT RUN**. Owner runs complete
CI at the new exact SHA; actual test-APK resource availability and API26/27/36
compatibility remain pending. No Block 2 completion or production engine approval.

## CI #130 PDFBox instrumentation asset wrapper — 2026-11-03

Owner-provided run https://github.com/alexbonavila/PartiturasPDF/actions/runs/41677282609
at `ddb3b90e6072abbdbd424beabcc90514144fb44c`: build, JVM (708 PASS), Lint,
localization and fixtures PASS; API26/27/36 and quality-gate FAIL. Each API executed
464 tests: all 445 existing tests PASS; all 19 candidate tests FAIL before functional
execution. No independent CI access or runtime execution by Codex.

Confirmed cause: instrumentation testContext.applicationContext is null on all
three APIs. CI #129's checkNotNull failed before glyph-list access, with
`PDFBox resource application context unavailable for com.abdev.partituraspdf.test`.
Upstream init dereferences getApplicationContext().getAssets(), so direct
init(testContext) is insufficient. This is not yet a PDF semantic incompatibility.

The private sandbox now wraps testContext in `object : ContextWrapper(testContext)`
whose `override fun getApplicationContext(): Context = this`. Inherited assets
delegate to testContext. Preflight uses pdfboxAssetContext.applicationContext.assets;
PDFBox init receives that same wrapper. Missing/empty resources fail with their
path and instrumentation package, preserving the original exception cause.
No fallback, synthetic glyph lists or global context mutation. Preflight remains
before directory creation; existing primary/suppressed cleanup handling is unchanged.
Target-private noBackupFilesDir ownership, fixture access and quotas stay intact.

Exactly PdfboxSpikeSandbox.kt and the two authorized documents change. All 19 tests
and assertions, instrumentation-only dependency, manifests, production and existing
tests remain unchanged. See [correction details](M_PDFBOX_ANDROID_TEST_SPIKE.md#ci-130-instrumentation-application-context-correction--2026-11-03).

Full diff and git diff --check reviewed. BUILD / JVM / ANDROID INSTRUMENTATION /
LINT / COVERAGE / CI / DEVICE / VALIDATORS: **NOT RUN**. Actual test-APK glyph assets
and API26/27/36 behavior require owner CI at the published correction SHA. No
production approval, engine evaluation PASS or Block 2 completion is inferred.


## CI #131 functional evaluation correction — 2026-11-03

Owner-provided CI https://github.com/alexbonavila/PartiturasPDF/actions/runs/41681088740
at `90fd7debf5428911cd0c3440140ea3a302cd9eb3`: build/JVM (708 PASS)/Lint/
localization/fixtures PASS; API26/27/36 and quality-gate FAIL. Each API: 464 tests,
460 PASS / 4 FAIL; all 445 existing tests PASS, candidate 15 PASS / 4 FAIL.
No CI/log access or runtime execution by Codex for this correction.

The three rotation methods (eachQuarterTurnIsPersistedForEachSelectedPage,
mixedBoxesSurviveSelectionAndAdditionalRotation,
additionalQuarterTurnComposesWithAllOriginalRotations) differed in PDFTextStripper
layout while observed structural fields matched. Reopened persisted rotations are
now asserted against the composed expectation before extraction. Only additional-
rotation cases extract complete text temporarily at the source reference rotation,
restore the actual saved rotation in finally, and compare text exactly. No text,
whitespace, punctuation or glyph removal. Unrotated snapshot behavior and exact
comparison remain unchanged. No temporary orientation is saved; wrong-rotation
mutation detection and all geometry/content/resource/annotation checks remain.
Independent rotated raster/visual acceptance remains pending, not implied by this
same-parser text comparison. Zero-extra-rotation PDFium assertions are unchanged.

Stock import fixture 12 exhibited an annotation /P outside its containing page:
a real candidate limitation, not an oracle issue. The negative regression
stockImportPersistedAnnotationsRetainInvalidExternalPageAssociation performs real
import/save/reopen, checks retained annotation dictionaries excluding /P, requires
explicit associations, and requires an incorrect /P outside the output page tree
whose content matches its original source page. At least one such relationship
must be observed. No broad exception is accepted; unrelated failures propagate.
No annotation is repaired, removed or accepted as valid. A passing negative test
only detects the limitation; stock import remains unsuitable for this scenario.

The original positive import method retains 02/08/10/11. New independent method
stockImportComplexFixtureIsEvaluatedIndependently checks 13 (previously never
reached after 12 failed) with the same positive semantic/PDFium assertions.
Source inventory is now 21 methods, not an executed count. All prior scenarios,
negative mutations, fixture hashes, resource limits and cleanup are retained.
Sandbox, production, dependencies, manifests, original fixtures and non-spike tests
are unchanged. BUILD / JVM / ANDROID / LINT / COVERAGE / CI / VALIDATORS: **NOT RUN**.
Full source/diff review and git diff --check only; owner CI must validate the new
SHA. Fidelity, independent rotated visuals, security, JPX, physical resources and
production approval remain open; Phase M/Block 2 completion is not claimed.


## Independent Python fidelity tooling — source delivery, 2026-11-03

Baseline `3a0f4067ec671d640bf7ced9d026dec9146e9911`. Owner reports CI #132 attempt 2
[41687967334](https://github.com/alexbonavila/PartiturasPDF/actions/runs/41687967334)
PASS: nine jobs, 708 JVM, 466 Android on each API26/27/36. This is historical owner
evidence, not independent Python fidelity or CI evidence for the new host tools.

Added bounded explicit-namespace binary collector, offline pypdf/PyMuPDF/Pillow
structural and rotated raster verifier, and synthetic/mock regression tests.
See [owner workflow, limits, classifications and PowerShell commands](M_PDFBOX_INDEPENDENT_VALIDATION.md).
The known fixture 12 stock-import association remains KNOWN_UNSAFE; positive
working-copy results do not clear it. Source tools have not been executed, and
empty negative recipes cannot prove negative success. Missing positive scenarios
block complete campaign acceptance; long-document visual sampling is explicit.
Android code/evidence schema, dependencies, CI and immutable fixtures unchanged.

Source/full diff review and git diff --check only. BUILDS / JVM / ANDROID / PYTHON
TESTS / LINT / COVERAGE / DEPENDENCY INSTALLATION / ADB / DEVICE / CI: **NOT RUN**.
No compatibility/fidelity/security/production approval or Phase M completion claim.


## CI #133 localization-job Python dependency setup — 2026-11-03

Owner-verified run https://github.com/alexbonavila/PartiturasPDF/actions/runs/41708721201
at baseline `27bb2caf9d1470f724a6a108e24f6e155cf67bcd`: both attempts failed in
localization's `python -m unittest discover -s scripts/tests -v`. Discovery found
516 tests with 12 errors caused by unavailable fitz (PyMuPDF), pypdf and PIL
(Pillow). Android, JVM, build, Lint and PDF-fixture jobs passed, per owner evidence.
These are historical results supplied by the owner, not new Codex CI/log access.

The pdf-fixtures job already installs the existing Python-only fixture requirements;
localization did not, despite discovering the new independent-PDF verifier tests.
Only localization setup now adds pip caching keyed by
`test-fixtures/requirements-fixtures.txt` and installs that same requirements file
before unchanged unittest discovery and localization validation. Python 3.12,
pinned actions, all test assertions, strict failures, mandatory jobs and quality
gates are unchanged. No test skipping, collector/verifier workaround, new dependency
specification, Android/production dependency or other workflow-job change.

Exact scope: `.github/workflows/ci.yml` and this ledger. Complete diff/source review
and `git diff --check` only. BUILDS / PYTHON TESTS / ANDROID TESTS / LINT / COVERAGE /
DEPENDENCY INSTALLATION / CI: **NOT RUN** for this correction. Owner must run CI at
the new published SHA. Passing dependency setup or CI alone does not establish
verifier acceptance or independent PDF fidelity; separate owner-run collection,
structural/rotated visual evidence, and remaining engine gates stay outstanding.


## CI #134 API27 Reader test synchronization — 2026-11-03

Owner-provided run https://github.com/alexbonavila/PartiturasPDF/actions/runs/41712008963
at `1f4eb0ac591ea6d624f04e016971e0d8e6c43886`, two attempts: build, JVM (708 PASS),
Lint, fixtures, localization/Python (516 executed, 515 PASS, one skipped), API26
(466 PASS) and API36 (466 PASS) succeeded. Python dependency setup is verified
by that owner evidence. API27 had one distinct pre-existing Reader failure in each
attempt; quality-gate failed. All 21 PDFBox spike methods passed in both API27
attempts. Codex did not access CI/logs or execute these checks.

- ReaderScreenTest.replacementRecyclesOldBitmapAndNeverKeepsTwoLeases expected
  acknowledgements [PageIndex(0), PageIndex(1)], observed [PageIndex(0)]. Source:
  ReaderController.nextWork publishes VISIBLE/presentedPage and Scheduling before
  returning Work.Ack; its separate perform callback invokes route.displayed.
  The fake records acknowledgements in CopyOnWriteArrayList. visible(1) does not
  imply that callback has completed. The test now waits (existing 10,000 ms bound)
  for the actual PageIndex(1) observation, then retains the exact ordered-list
  assertion. Duplicates, reordering or unexpected values still fail. Bitmap
  recycling/current bitmap and maximum-one-lease assertions are unchanged.
- ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition
  threw ClassCastException casting LibraryContent.Loading to Ready. LibraryController
  beginLoad publishes Loading before its asynchronous catalog result. Destination
  LIBRARY is therefore insufficient readiness. Only this scenario now has a nullable
  snapshot helper for waits: safely read Ready host/content, require the single PDF
  with the expected fixture storage identity. Loading/missing/wrong rows return null;
  the Idle wait keeps its 15,000 ms bound. Registration/metadata and favorite waits
  in this same scenario use the same safe lookup. Strict pdf() and other scenarios
  remain unchanged. Related mount/back predicates were inspected; no broad helper
  or unrelated scenario rewrite was introduced.

These source orderings support test-synchronization corrections; no production
Reader/Library defect is demonstrated by the supplied failures. Native retirement,
route closure, registration, persisted page, settings/favorite metadata, original
bytes and SAF grants assertions remain unchanged. No sleeps, timeout increases,
catch-all polling, retries, relaxed assertions, production, PDFBox, Python, Gradle,
manifest, fixture, CI or quality-gate changes. Scope is the two named test files
and this append-only ledger.

Full diff/source review and git diff --check only. BUILDS / JVM / ANDROID / PYTHON
TESTS / VALIDATORS / LINT / COVERAGE / DEPENDENCY INSTALLATION / CI: **NOT RUN**.
Owner must validate the new exact SHA on API26/27/36; runtime reliability remains
unverified for this correction. No Phase M completion, independent fidelity or
PDFBox production approval is inferred.


## Independent positive fidelity acceptance — 2026-11-09

**PASS / COMPLETE within the specified synthetic positive-transformation scope**,
owner-verified at `134fd0f8d0599c70a4e1de5a06b83478b49c422d` on Windows 10,
dedicated API26 emulator-5554. Full CI #135 was previously verified by the owner:
https://github.com/alexbonavila/PartiturasPDF/actions/runs/41720613841.
No CI/log access, build, test or device execution by Codex for this documentation.

Owner manual debug/test APK install and `am instrument -w` of the focused class
with retainPdfboxSpike=true produced OK (21 tests), with app installed and run-as
available. Collector COLLECTED; independent verifier exit 0, Overall PASS,
Missing scenarios 0. Positive results: 27/27 recipes (22 WORKING_COPY, five
STOCK_IMPORT), 345 structural pages PASS, 57 visual comparisons at 144 DPI with
exact pixel equality and zero different pixels. Four long outputs sampled first/
middle/last: 04 indices 0/25/49 and 05 indices 0/50/99, each repeated twice.
The 288 other pages were structurally verified, NOT individually raster-compared.
Additional rotated-page positive comparisons are complete within that scoped
campaign, not independent visual acceptance of arbitrary PDFs.

Python 3.15.0 / PyMuPDF 1.28.2 / pypdf 6.20.1 / candidate 2.0.27.0;
report timestamp 2026-11-09T18:13:08.859917+00:00. Raw reports remain with the
owner and are unavailable in this workspace. Their supplied hashes, exact tool/
manifest identities, successful Windows ADB command and source CRLF/LF byte
comparison are registered in the [canonical campaign addendum](M_PDFBOX_INDEPENDENT_VALIDATION.md#completed-owner-campaign--2026-11-09).
No claim that Codex independently rehashed those unavailable reports or attested
installed APK identity; the tested Git SHA is owner-supplied provenance.

Stock import fixture 12 remains **KNOWN_UNSAFE** (external annotation /P), while
its WORKING_COPY case passed. Eleven empty negative namespaces still lack
standalone independent oracle proof; absence of output is not negative acceptance.
No complete JPX/JPEG2000, full long-document raster coverage, unusual forms/
inherited resources/filters, malformed-input safety or unrestricted PDF equivalence.

Next owner gate: [security/dependency/packaging/licensing audit and hardware handoff](M_PDFBOX_SECURITY_AND_PACKAGING_GATES.md).
Security and production packaging remain OPEN; candidate production approval is
NOT GRANTED and Phase M Block 2 is not complete. Prior dated FAIL/NOT RUN evidence
remains unchanged; this scoped PASS supersedes only the matching pending positive
fidelity/rotated visual evidence. Builds/tests/validators/resolution/install/ADB/
devices/scanners/CI: **NOT RUN by Codex** for this documentation increment.

## Real PDF / safe Save As test-confined vertical slice — 2026-11-09

Approved baseline `5465a9b14e9f5f1acaa7b558a7e785370de98b06`. Implementation
SOURCE, not compilation/execution evidence. Existing owner fidelity campaign and
CI #135 retain their exact historical source attribution; neither verifies this
new increment. No new framework, coordinator, journal, owner, dependency or UI.

### Implemented components

- `pdf/engine/spike/TestPdfboxModificationAdapters` (androidTest ONLY) implements
  existing PdfModificationEngine/PdfOperationValidator. It qualifies exact source
  hash/length against the canonical fixture manifest, rejects unknown/protected/
  corrupt inputs and admits only the synthetic valid corpus. Additional harness
  bound: 2 MiB per parsed sealed input/output (not a changed production quota or
  proof of a parser heap cap). Caller cancellation is checked during reads/writes
  and between native operations; blocking parser calls have no proven interruption
  bound. Leases use separate dup descriptors for owned stream closure and exact
  rehash/count. No writable source alias, user URI, arbitrary file output or importPage.
- Working-copy transformation materializes inherited properties, applies selected
  page order and additional quarter turns, and streams save through the SAME store
  Writer. The synchronous-to-suspend bridge checks caller cancellation; no output
  buffer masquerades as a transformed artifact. Parser closes BEFORE stage returns,
  so close failure prevents sealing. PrivateSnapshotStore alone seals/mints ownership.
- Validator independently reopens/rehashes the two sealed byte sequences; validates
  order/count, persisted rotations, effective boxes, decoded streams/resources,
  annotation dictionaries/AP/page associations and exact source-orientation text,
  restoring rotations in finally. Native PDFium checks nonempty actual output
  pages and exact source/output pixels when no added rotation. Only after all
  required checks does the TEST-only adapter construct ValidatedPdfArtifact with
  the exact output and original plan objects. Same-parser structural comparison
  and bounded thumbnails are not full independent fidelity or production approval.
- M-specific EngineResult.Rejected gains defaulted cleanup status, forwarded by
  coordinator into preparation rejection. This prevents pending engine-output
  cleanup from disappearing when the coordinator's input cleanup completes. No
  AppError/Outcome, journal schema, transition, production adapter default or
  destination provenance requirement changed. EngineApprovalRequired remains default.

### New test source inventory (all NOT RUN)

Class `storage.transaction.MPdfboxSaveAsIntegrationTest`, real Android private I/O,
TransactionSnapshotCapturer, store/budget, journal, coordinator/session/publisher,
NativeMLeaseCopier and directly invoked disposable MNewDocumentProvider:

| Method | Source assertions / evidence distinction |
| --- | --- |
| realReorderRotationPublishesOnceWithDurableReadbackAndCleanup | Actual fixture02 reorder [1,0], +90/+270; prepared and verified/cleaned records; independent provider byte/hash readback; saved output page labels/rotations; source unchanged; cleanup removes sealed files, releases admission; restart-style read-only reconciliation observes existing verified record; duplicate never writes |
| workingCopyAnnotationsAndZeroTurnNativePixelsAreValidated | Fixture12 working-copy reverse validates actual AP/associations and native zero-turn pixels, then publishes; no stock import |
| realValidatorRejectsWrongPlanAndMalformedSealedOutput | Real engine output checked against a mismatching plan; separately sealed malformed bytes refused; owned snapshots released |
| refusedProviderCreateAndCancellationBeforeLaunchCannotPublish | Actual provider create refusal; separate injected pre-transform barrier cancellation, no escaped launch result; admission released |
| invalidIndexProtectedCorruptAndUnknownBytesNeverLaunch | Out-of-range plan; 14/15 and modified test-owned input hash reject; unknown bytes never enter parser; canonical copy restored for teardown |
| sealValidationAndPreparedCheckpointFailuresCannotIssueHandoff | Injected output fsync fault; explicit validator refusal; journal prepared checkpoint failure; no Ready request |
| actualProviderWriteRefusalCorruptReadbackAndReliableErrorsNeverVerify | Real provider WRITE_DENIED/CORRUPT_READBACK/PARTIAL_WRITE/CLOSE_ERROR; real regular/reliable descriptors; no verified result or duplicate retry; explicit unresolved disposition |
| injectedShortReadCloseFailureAndPrewriteRefusalStayUnverified | Injected short reader, exception after actual stream close, or capability revalidation refusal; assert zero/one writes and no verified result |
| staleDuplicateAndMissingCallbacksNeverReplayPublicWrite | Wrong token and lost callback, no write/replay; explicit owner acknowledgement |
| cancellationAtWriterBarriersRetainsUncertaintyAndStopsBeforeResolution | Deterministic before-copy/after-copy barriers AFTER possible destination creation; caller cancellation, no receipt, retained admission until explicit resolution; one attempt |
| finalCheckpointFaultAndExceptionalPrivateCleanupKeepIndependentOutcomes | Failed terminal checkpoint cannot verify; separate unexpected cleanup defect after durable commit preserves verified bytes, pending cleanup, exact diagnostic object, files/quota and admission release |

`MPdfboxIntegrationRig` shares one PrivateRoot/budget/store/journal and one cleanup
scope. Every test checks canonical source hash/size in teardown; provider closes and
workers join before the unique synthetic subtree is removed. Constructor failures
retain primary/suppressed cleanup failures. Provider descriptor effects are actual
Android operations; custom destination identity/capability/revalidation and explicit
fault barriers are injected seams, NOT OS grants/resolver/system-picker evidence.
No MAndroidDestination proof check is bypassed or modified: it is deliberately not
claimed by the direct test port. No registry/metadata dependency or implicit copy
of favorites/reading positions. Existing metadata/SAF/Reader tests are untouched.

JVM regression: `pdf.workflow.MTransactionLifecycleTest.engineCleanupDebtSurvivesOtherwiseCompletePreparationCleanup`
checks the new cleanup-status propagation, original rejection reason and reusable
admission. No trivial semantic-validator fake is used by positive PDF integration.

### M-T01–M-T20 attribution

M-T01/03/05 gain real capture/lease/seal/closure integration; M-T06 adds qualified
protected/corrupt/unknown rejection, not arbitrary parser/security acceptance;
M-T07 gains actual transformation + semantic/native checks for 02/12 (earlier
spike's broader 08/09/10/11/13 source remains). M-T08/09/11/12/13/14/17/19 gain
new-document byte readback, injected faults/cancellation, durable outcomes and
cleanup-debt integration. No genuine OS process restart/power-loss/revocation or
picker/grant proof. M-T10 retains existing same-name provider tests; M-T15 is
supported by absence of registration APIs, not new real Room/metadata acceptance.
M-T02/16 existing quotas/overflow/admission protections remain, not new exact-limit
or hostile-parser resource measurements. M-T04 external concurrent source changes,
M-T18 APK/backup/D2D isolation and M-T20 future production host remain separate
acceptance gaps. No requirement is declared runtime complete by new source alone.

### Owner scope decision and next validation

Owner explicitly omits the dedicated physical memory/performance campaign: no
benchmark suite, physical tablet profiling requirement, PSS/heap/CPU/latency
campaign or new numerical performance threshold is scheduled. This is a scope
waiver, **not technical PASS** and not proof against parser exhaustion/leaks.
Existing correctness, storage byte/margin limits and cleanup/cancellation invariants
remain. General physical functional acceptance policy is not silently rewritten;
any conflict at final phase acceptance requires owner policy disposition.
Security/BC advisories, JPX/unsupported-feature qualification, main/test binary
isolation and production engine approval remain OPEN; unrestricted stock import12
remains KNOWN_UNSAFE. User-facing Save As and production engines remain disabled.

Owner-only Windows commands from the published source (NOT executed by Codex):

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest
py -3 -m unittest discover -s scripts/tests -v
py -3 scripts/validate_localization.py
py -3 test-fixtures/scripts/verify_fixtures.py
# After manual matching app/test APK install to the explicitly authorized emulator:
adb -s emulator-5554 shell am instrument -w `
  -e class com.abdev.partituraspdf.storage.transaction.MPdfboxSaveAsIntegrationTest `
  com.abdev.partituraspdf.test/androidx.test.runner.AndroidJUnitRunner
```

Owner retains raw JUnit/counts, actual installed APK hashes/source identity and full
API26/27/36 CI results; focused integration does not replace the complete suite.
Run existing A1/B5/C05 coverage gates as applicable; M-specific coverage proposal
still requires separately approved configuration and actual measured evidence.
No coverage percentage is claimed. No automatic retries of failed assertions.

Full source/diff review and git diff --check only. BUILDS / JVM / ANDROID / PYTHON /
LINT / COVERAGE / VALIDATORS / DEPENDENCY INSTALLATION OR RESOLUTION / ADB / DEVICE /
CI: **NOT RUN**. No new measured result, production approval or Block 2 completion.

## Block A production engine and validator — source delivery, 2026-11-09

Starting/fetched branch revision `39a0332a87323ad151bf0914403fe7a862d9cc46`,
`feat/pdf-safe-write-m`, clean before edits. This entry is evidence of source
implementation/review only. Its publication commit identifies the new source;
no historical CI/fidelity result is attributed to it. Codex executed no build,
dependency resolution/install, test, compiler, validator, Lint, coverage, ADB,
benchmark, scanner or CI. Only source/public-metadata inspection and Git checks.

### New exact test inventory — all IMPLEMENTED — NOT EXECUTED

Android class:
`com.abdev.partituraspdf.storage.transaction.MProductionPdfEngineTest` (**11 methods**).
Uses the existing `MPdfboxIntegrationRig(useProduction=true)` with actual production
adapters/store/journal/coordinator and disposable direct provider. It is not a
system-picker/grant acceptance campaign.

| Method | Assertion and origin |
| --- | --- |
| `productionFactoryReordersRotatesPublishesAndCleans` | Explicit production factory, real transform/seal/validate/readback, one public write, durable CLEANED, duplicate callback blocked, exact provider evidence and unchanged source. |
| `supportedCorpusReorderExtractionGeometryAndAllQuarterTurns` | Canonical01/02/03/06/07/08/09/10/11/12/13; each0/90/180/270; unique last/first selections (single-page uses0). Actual production structural/text/native checks, 44 recipes. These are test expectations, not executed fidelity results. |
| `generalNoncanonicalDrawingAndInheritedResourcesWorkWithoutFixturePrivilege` | Generated noncanonical vector PDF with non-default200x300 box and inherited resources; production path has no fixture hash allowlist. Test source copy restored in finally. |
| `productionValidatorRejectsWrongPlan` | Actual transformed output cannot validate against another exact order/rotation plan. |
| `malformedOutputAndCorruptedSnapshotCannotMintArtifacts` | Sealed non-PDF bytes refused; private TEST source bytes corrupted after seal are rejected before transformed output handoff. |
| `actualProtectedCorruptAndOutOfRangeRequestsRejectWithoutPicker` | Real canonical14 password-protected and15 corrupt; actual typed protection/invalid-plan rejection, no picker handoff. |
| `objectGraphPolicyRejectsIndirectJpxFormsActionsSignaturesAndInlineImages` | Actual COS dictionaries/indirect stream and byte-backed content parser reject direct/indirect/array JPX filters, prohibited catalogs and inline image operators in pages and nested Forms. |
| `policyRejectsInvalidTreesMissingResourcesAndForeignAnnotationPages` | Invalid tree count, referenced missing font, foreign `/P` rejected by actual qualifier. |
| `alteredResourceGraphCannotReceiveValidationAuthority` | Serialize a deliberate resource mutation of real transformed bytes, stage/seal it, require validation refusal. |
| `cancellationDuringProductionSourceLeaseClosesPartialOutput` | Deterministic cancellable lease barrier inside real engine/store staging; exact cancellation identity and no retained partial/output file; source unchanged. Does not prove cancellation inside a synchronous native decode. |
| `realProductionSealCheckpointAndCleanupFaultsNeverInventSuccess` | Real output-seal/preparation-journal faults reject; post-verification cleanup debt retains independent verified bytes with PENDING/accounting. |

JVM class `com.abdev.partituraspdf.pdf.editing.SnapshotRandomAccessTest`
(**six methods**):

- `positionalReadSeekPeekAndRewindDoNotMoveOwnersChannel`
- `invalidSeekRewindAndReadSizesFailClosed`
- `closedViewNeverClosesBorrowedChannel`
- `mismatchedLengthAndClosedChannelAreRejected`
- `cancellationPropagatesTheExactObject`
- `truncationAfterLeaseValidationNeverInventsBytes`

Uses tiny generated private files, no Android rendering/mocks of PDF fidelity.
Tests parser input position/bounds/ownership/cancellation, not whole-parser memory.
New Python `scripts/tests/test_m_engine_packaging.py` has four synthetic ZIP tests:
`test_limited_main_assertions`, `test_fixture_notice_and_dependency_regressions`,
`test_test_code_leak`, `test_cli_failure_is_nonzero`.
No tests were removed/disabled and no prior assertions were reduced. Experimental
adapters still require their original exact fixture hashes and keep their tests;
only shared interface and PDFBox main-APK asset ownership were adjusted.

### M-T01–M-T20 additional attribution

| Requirement | Additional source coverage / remaining boundary |
| --- | --- |
| M-T01 | Real sealed source lease/hash and private output/seal/rehash; immutable original asserted. |
| M-T02 | Existing quota/free-space/overflow accounting reused unchanged; no new exact-limit execution. |
| M-T03 | Positional duplicate-FD parser view and lease cleanup tests; no unowned parser input file. |
| M-T04 | Rehash rejects altered private snapshot; no universal external-provider consistency or race proof. |
| M-T05 | Actual production output seal/journal failure and cancellation cleanup tests. |
| M-T06 | Actual protected/corrupt canonical inputs, malformed sealed output and unsupported policy cases. |
| M-T07 |44 production recipe expectations, all five boxes/order/rotation/decoded content/resources/annotations/text and native pixel checks; tests not executed, general arbitrary-PDF fidelity not accepted. |
| M-T08 | Production coordinator direct-provider publication and exact readback; actual system picker remains Block B. |
| M-T09 | Existing provider create/refusal tests retained; no grant escalation or new permission. |
| M-T10 | Existing same-name/independence proof boundary retained, no new collision policy. |
| M-T11 | Existing partial/close/reliable-provider error tests retained; new engine seal failure never creates publication authority. |
| M-T12 | Deterministic production lease cancellation; existing callback/write cancellation tests retained; synchronous parser interruption bounds unproven. |
| M-T13 | Actual production PREPARED checkpoint failure regression; journal/recovery format unchanged; no crash/power-loss campaign. |
| M-T14 | Existing permission checks/revocation simulations unchanged; no real grant revocation. |
| M-T15 | No registry, favorite, checkpoint or rebind mutation added. |
| M-T16 | Existing limits plus parser input/graph/decode/page/image/raster bounds; no hostile exhaustion immunity, no owner-omitted benchmark. |
| M-T17 | Reuses real disposable provider descriptors; API26/27/36 execution and actual picker/grants still pending. |
| M-T18 | Production dependency promotion requires revised packaging expectations; script/tests and notices added. Actual APK/manifest/native/backup/D2D inspection NOT RUN. |
| M-T19 | Existing primary/suppressed/cleanup debt contracts reused; production cancellation and cleanup-pending tests added. |
| M-T20 | No host UI/overwrite, automatic registration, metadata transfer or generic source WRITE path. |

### Exact owner-run Windows 10 / PowerShell verification

From repository root at the publication SHA, using the existing supported JDK/SDK
and an explicitly owner-selected disposable API26 emulator (not any physical device):

```powershell
git rev-parse HEAD
git status --short
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest
python -m unittest discover -s scripts/tests -v
python scripts\validate_localization.py
python test-fixtures\scripts\verify_fixtures.py
$env:ANDROID_SERIAL = 'OWNER_VERIFIED_API26_SERIAL'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.storage.transaction.MProductionPdfEngineTest,com.abdev.partituraspdf.storage.transaction.MPdfboxSaveAsIntegrationTest,com.abdev.partituraspdf.pdf.engine.spike.PdfboxAndroidSpikeTest'
```

Replace the placeholder with the verified emulator serial; do not adopt/reset an
unknown AVD. Repeat the same exact instrumentation selection on separately owned
API27/API36 environments under owner execution. Existing full ordinary Android
suite and coverage gates remain required; the selection alone is not full CI.
Use the existing coverage tasks/policies without weakening denominators or gates;
retain XML/HTML and exact source/APK/device identities. Owner must obtain
>=90% lines/>=80% branches for new testable business logic and all critical cases.
No percentage or test PASS is claimed in this source delivery.

JUnit reports: `app/build/test-results/testDebugUnitTest/`; Android XML/results:
`app/build/outputs/androidTest-results/connected/`; Lint:
`app/build/reports/lint-results-debug.html`. Retain actual Gradle output and exact
inventory, failures/skips/coverage, resolved dependency reports, APK hashes and
packaging evidence from the [current packaging protocol](M_PDFBOX_SECURITY_AND_PACKAGING_GATES.md#block-a-current-gates--2026-11-09).
Compilation alone is not instrumentation. Existing independent Python fidelity
collector can still supplement experimental outputs at its recorded scope; no
accepted general production PDF fidelity is inferred from past synthetic recipes.

### Disposition

Real production engine/validator/factory/test source and attribution assets:
**IMPLEMENTED — NOT EXECUTED**. Git whitespace/source scope review: recorded at
publication. Current complete fork security inventory: **BLOCKED** (Apache403),
actual resolved graph/BC API26 linkage/packaging/coverage: **NOT RUN**. Legal and
host-activation disposition: **OWNER DECISION REQUIRED**. Block A is **NOT
ACCEPTED** while mandatory gates remain open. Physical functional requirements
remain distinct; dedicated performance/memory/physical profiling is **NOT
APPLICABLE — OMITTED BY OWNER**, no new benchmark suite or made-up budget.
Block B real host/picker integration is not started and must not bypass these gates.

## Windows 11 Python harness and CSV corrections — 2026-11-09

Starting source `5fb19925a4ffede70025419f0f5b41f8ebdd80fe`, clean Phase M branch.
Owner reports Windows11/PowerShell, Python3.15 traceback paths: Debug/Release and
instrumentation compilation succeeded, JVM succeeded after symbolic-link permission
setup, localization PASS and15 fixtures PASS. Python:520 executed,5 failures and5
errors. These are owner-reported results on that revision, not a new Codex run or
Android production-engine acceptance. Individual retained traceback/process evidence
was not supplied with this correction assignment; do not claim each WinError32/5
or exit127 was independently reproduced or has the same cause.

Source-confirmed shell harness portability issues: unqualified `bash` can select
Windows' WSL launcher, native PATH concatenation is not an explicit POSIX search
path, and default `write_text` translates synthetic shell files to CRLF on Windows.
The former subprocess harness did not own a Windows descendant tree through cwd
cleanup. These explain concrete failure mechanisms, not an attribution of every
reported process lock. Existing seven methods still execute the unchanged real
`run_pdfium_ci.sh`, copied with LF into an isolated directory containing spaces.
No CI script/workflow, Gradle, manifest, PDF engine, fixtures or device behavior
was changed. Direct Bash tests were not replaced with mock-only assertions.

Current test harness selects an existing native Git/MSYS Bash/runtime on Windows,
refuses WSL/incomplete runtimes, allows explicit absolute `PDFIUM_TEST_BASH`,
and keeps POSIX Bash execution. LF/UTF-8 stubs, fixed POSIX PATH and explicit fake
ADB/Python bindings cannot fall through to real installed device commands. User
shell startup/exports are removed from the child environment; the selected Bash
executes the actual script by relative path. Gradle is a temporary shell stub.
Binary subprocess pipes avoid Windows stdin newline translation. Exit status,
stdout/stderr and selected interpreter are retained in assertion diagnostics.

Windows ownership uses standard-library ctypes Job Object APIs, with the Bash
child held at an stdin gate until assignment. Completion-port notification plus
active-process accounting confirms tree exit before temporary-directory removal.
Timeout/error cleanup terminates only that job (or the still-gated owned process),
joins with bounded waits and closes pipes/handles. POSIX uses an owned session and
EXIT wait; failure cleanup targets only its process group. No taskkill scan,
unrelated process termination, sleeps, cleanup retries or ignored PermissionError.
Capability/assignment/cleanup failures remain explicit test errors, never skips or
PASS. No mandatory WSL, admin privileges or new external dependencies.

**CSV source discrepancy corrected:** at the approved SHA the partial row already
had12 columns, including an empty epoch. The existing regression supplied no
partial samples. The confirmed Windows formatting defect is default CRLF from
`csv.writer` followed by atomic text-output newline translation, producing CR-CR-LF
and blank records. A narrow keyword-only `newline` option was added to existing
`j_atomic`; only M4 CSV requests `newline=''`. All other callers retain their
previous default; atomic path validation, exclusive temporary creation, fsync,
replace and cleanup are unchanged. CSV schema remains12 columns. Explicit partial
`worker_epoch` is retained when supplied, otherwise empty; value/unit remain
indices10/11. No epoch is inferred from another iteration. JSON/Markdown behavior
and rejection of incomplete PASS reports remain intact. Historical reports are
not rewritten and no benchmark/performance campaign is reactivated.

Regressions: all seven original real-shell methods/assertions retained; diagnostics
include exit127. Three new real-shell methods exercise standard127, failed final
verification and missing/multiple/offline/physical CI discovery. Six new harness
methods cover LF/UTF-8/space paths, Windows runtime selection versus WSL, explicit
missing-capability error, gated assignment/join/closure, timeout failure and visible
cleanup PermissionError. M4 regression now checks the exact header and every field,
normal/partial rows, missing/supplied epochs, comma/quote/embedded CRLF round-trip,
Windows text-translation simulation and no successful incomplete finalization.
The existing Markdown-write fault mock forwards the new optional keyword without
changing its assertion. Helper mocks test ownership orchestration, not Win32 or
Bash runtime correctness; those require the real owner executions below.

**IMPLEMENTED — NOT EXECUTED.** Codex performed source/Git review and whitespace
checks only. No tests, builds, syntax/compiler checks, ADB, dependency installation,
benchmark, Lint, coverage or CI were executed. Windows11/Python3.15 and Linux/CI
runtime compatibility remain NOT RUN on this increment. If native Git/MSYS Bash
or job ownership is unavailable, record the explicit error and resolve the
capability; do not skip tests or claim the suite passed. Current CI Python3.12
and owner Python3.15 support the standard-library exception-group diagnostics.
Production engine/security/runtime acceptance remains at its earlier open gates.

Owner-only Windows11 PowerShell commands, repository root at publication revision:

```powershell
# Only if automatic discovery cannot locate the existing Git Bash installation:
$env:PDFIUM_TEST_BASH = 'C:\Program Files\Git\usr\bin\bash.exe'
py -3 -m unittest discover -s scripts/tests -p "test_pdfium_ci_shell.py" -v
py -3 -m unittest discover -s scripts/tests -p "test_j_m4_benchmark.py" -v
py -3 -m unittest discover -s scripts/tests -v
```

Use the actual existing Bash location, not the example if absent; no installation
or privilege escalation is implied. Record Python version, selected Bash path,
source SHA, real counts and all failure output. The same unittest commands with
`python3` apply to Linux/CI; no workflow change/dispatch is made here. Expected
result is zero failures/errors, subject to actual owner execution, not a claim.

## API26 PDF integration private-root initialization correction — 2026-11-09

Starting source `d1f0e6bc48a41f03d62015b516b325d2fa205b74`, clean Phase M branch.
Owner reports Windows11, Android8.0/API26, `emulator-5554`: both APK installations
and instrumentation startup succeeded, existing PDFBox spike had no reported
failures, and20 integration failures (11 Save As,9 production) shared
`IOException: Untrusted private root` during `MPdfboxIntegrationRig` construction.
These are owner-reported initialization failures, not20 demonstrated PDF-operation
defects or production-engine acceptance. No raw XML/APK identities were supplied
with this assignment. Source at the required baseline actually contains11
production methods, not9: preserve this inventory discrepancy pending owner
verification of rebuilt APKs and raw test identities. Do not infer which two
methods were absent, stale binaries or a successful test from that count alone.

The reported exception corresponds to the unique ancestor-symlink guard in
`PrivateRoot`, before transaction parsing/transformation/publication. Source
confirms the rig passed `context.noBackupFilesDir` directly, whereas production
`PrivateRoot.forContext()` uses `context.noBackupFilesDir.canonicalFile`. A trusted
Android framework path exposed through a system-managed alias therefore reaches
that guard in the old rig. The exact offending ancestor was not independently
observed on the owner's emulator; no specific Android path alias is invented.

Correction: resolve **only the trusted framework base** to its canonical location
before creating the rig's UUID-named `m-pdfbox-integration-*` child. Its private
`transactions` root remains beneath that owned disposable child, separate from
production `pdf-transactions`. No caller-provided root or context injection, external
storage, generic canonicalization in `PrivateRoot`, symlink allowlist or test-only
production bypass. `PrivateFiles.kt` and every production storage/security/engine
implementation are unchanged, including `PrivateRoot.check()`, no-follow checks,
journal/durability/recovery behavior and artifact ownership. Provider lifecycle,
constructor primary/suppressed cleanup, coroutine cancellation/join and existing
fault injection/teardown are unchanged.

Three new API26-capable methods in the existing
`com.abdev.partituraspdf.storage.transaction.MProductionPdfEngineTest`:

- `canonicalFrameworkBaseInitializesConfinedRootAndCleanup`: actual rig/root
  construction, canonical framework parent, distinct production root, successful
  real transaction/artifact creation, rejection of unrelated/internal/external
  artifact paths, immutable fixture and removal of the disposable rig directory.
  External candidates are lexical checks only; no external file is accessed.
- `explicitSymlinkAncestorsAndArtifactsRemainRejected`: actual Android NIO symlinks
  inside test-owned storage, `PrivateRoot` ancestor rejection before directory
  creation, artifact/check/create and traversal rejection, intact sentinel bytes.
  Test links themselves are removed before normal teardown; cleanup failures stay
  visible without replacing the primary failure.
- `failedRigConstructorRemovesOnlyItsOwnDisposableDirectory`: force a missing
  fixture lookup failure during real construction, require the original exception,
  no residual new rig directory and preservation of a separately owned sibling
  directory/sentinel. Its sentinel cleanup remains checked, with secondary errors
  suppressed on the original failure.

Existing JVM `PrivateJournalTest.symbolicLinksAndEscapesNeverAuthorizeReadOrCleanup`
and Android `Block1PrivateStorageTest` real canonical-root/FD/durability coverage
remain intact. The new tests exercise the actual shared rig and Android security
checks, not merely `File.canonicalFile` or mocked policy. All prior11 production,
11 Save As and21 spike source methods remain enabled/unchanged. Current static
inventory is14 production +11 Save As +21 spike =46 selected methods, **not an
executed count**. No new class/runner is needed for the owner's selection below.

Status: **IMPLEMENTED — NOT EXECUTED**. Source/scope/diff inspection and Git
whitespace checks only; no Gradle, JVM, Python, Android instrumentation, ADB, CI,
Lint, coverage or measurements executed by Codex. Rebuild/install the publication
revision and retain exact JUnit identities, final completion and APK/source/device
attribution. Initialization success and substantive PDF/Save As outcomes remain
unverified; report any subsequently exposed failures faithfully. Production engine,
security/packaging/compatibility acceptance gates remain open. No dedicated
performance/memory campaign is reinstated and no historical result is rewritten.

Owner-only Windows PowerShell verification, at the publication revision and the
explicitly identified disposable API26 emulator:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class "com.abdev.partituraspdf.storage.transaction.MProductionPdfEngineTest,com.abdev.partituraspdf.storage.transaction.MPdfboxSaveAsIntegrationTest,com.abdev.partituraspdf.pdf.engine.spike.PdfboxAndroidSpikeTest" com.abdev.partituraspdf.test/androidx.test.runner.AndroidJUnitRunner
```
