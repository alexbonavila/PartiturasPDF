# Phase M — Block 1 source delivery / owner-run evidence ledger

2026-11-03. Branch: `feat/pdf-safe-write-m`.
Reviewed start: `ad4c0ee07497c0c49471e03f33d5440d4ab76743`.
Authority: supplied `PARTITURASPDF_PHASE_M_BLOCK1_CODEX_IMPLEMENTATION.md`.
Status: **IMPLEMENTATION SOURCE DELIVERED FOR OWNER VALIDATION; NOT ACCEPTED**.
No build, test, validator, instrumentation, emulator or CI command was run.
The code has been inspected textually; successful compilation is not established.

See [actual contract surface and ownership](M_BLOCK1_APPROVED_CONTRACTS.md),
[historical M-M2A review](M_M2_CONTRACT_FREEZE_REVIEW.md) and
[critical test plan](M_M1_CRITICAL_TEST_PLAN.md). Historical evidence is unchanged.
S-01=A/S-02=B/S-03=B/S-04=B now follow the owner's Block 1 order, not a retroactive
change to the historical OPEN decisions in M-M2A.

## Exact new-file inventory

Paths are relative to the repository. All production files are under
`app/src/main/java/com/abdev/partituraspdf/`:

- `storage/api/SnapshotContracts.kt`: selected public values/results/final wrappers,
  internal owner/lease ports. No edits to existing read APIs or core.
- `storage/api/RecoveryContracts.kt`: TransactionId used by staging and journal.
- `storage/transaction/PrivateFiles.kt`: confined no-follow paths, IO fault seam,
  Android RO independent opens, file/directory sync, no-replace preconditions,
  bounded directory enumeration and private-byte evidence reread.
- `storage/transaction/MStorageBudget.kt`: central measured occupancy/reservations,
  fixed upper policy, free-space margin and retained-byte accounting.
- `storage/transaction/PrivateSnapshotStore.kt`: actual sequential bounded staging,
  shared seal/owner/lease lifecycle, cancel-safe handoff and joined release result.
- `storage/transaction/TransactionSnapshotCapturer.kt`: actual injected openRead
  capture, including pipes, 64 KiB stream, narrow access mapping, cancellation
  closure attempt; source remains read-only.
- `storage/transaction/PrivateJournal.kt`: deterministic schema 1/checksum/chain,
  private transitions, immutable checkpoint write/sync/rename/dir-sync/reread;
  reserved external states fail closed.
- `storage/transaction/PrivateRecovery.kt`: bounded classification/rehash/retention,
  no provider access or automatic deletion/replay.
- `pdf/workflow/WriteAdmissionGate.kt`: immediate injected BUSY/admission primitive.

New JVM test files under matching `app/src/test/java/...` packages:
`Block1ContractsTest.kt`, `MStorageBudgetTest.kt`, `PrivateJournalTest.kt`,
`PrivateSnapshotStoreTest.kt`, `WriteAdmissionGateTest.kt`.
New Android file under matching `app/src/androidTest/java/...`:
`storage/transaction/Block1PrivateStorageTest.kt`.
New documentation: this file and `M_BLOCK1_APPROVED_CONTRACTS.md`.
No tracked file edited/deleted; no production/test manifest, policy, resource,
build, dependency, fixture, existing test, Reader, SAF or host change.

## Test source inventory — execution NOT RUN

There are **28 JVM methods and 4 Android methods written**, not executed.
The exact method declarations reside in these new files, all annotated @Test:

| Class | Method inventory / assertions |
| --- | --- |
| Block1ContractsTest (9) | canonicalIdentifiersRejectAmbiguousAndNonCanonicalText; inputCeilingCannotBeRaisedByCaller; productionBudgetCeilingsCannotBeRaisedOrMarginRemoved; schemaRoundTripIsExactAndDeterministic; checksumTruncationAndUnboundedPayloadReject; unknownVersionRejectsEvenWithRecomputedChecksum; sequenceAndGenesisSyntaxAreStrict; publicEffectStatesAreNotReachableFromPrivateWriter; privateOutputIsUnvalidatedAndEvidenceCannotChange |
| MStorageBudgetTest (4) | exactAggregateReservationAcceptsButNextByteRejects; freeMarginAndOutstandingReservationsBothConstrainAdmission; unknownFilesAreChargedAcrossRestartAndNeverSwept; corruptScanBlocksNewAdmissionInsteadOfIgnoringOccupancy |
| PrivateJournalTest (6) | appendReopenAndRecoveryAreIdempotent; failedDirectorySyncNeverAcknowledgesAndOlderRecordSurvives; writeSyncAndRenameFaultsLeaveUnconfirmedArtifactsCharged; corruptPredecessorGapAndForeignFilesBlockWithoutDeletion; limitAndFailedDeletionKeepQuotaUntilConfirmedUnlink; symbolicLinksAndEscapesNeverAuthorizeReadOrCleanup |
| PrivateSnapshotStoreTest (7) | stagingUsesMeasuredBytesAndSameOwnerUntilLastLeaseCloses; emptyOverLimitAndFailedSyncNeverSeal; cancellationIdentitySurvivesCleanupFaultAndRetainedCharge; failedDeletionIsCleanupPendingNotReleased; canceledWaiterDoesNotCancelOwnerOrLiveLease; writeCloseAndRereadFailuresNeverEscapeAsCaptured; originalFailureIsNotReplacedByCleanupFailure |
| WriteAdmissionGateTest (2) | competingAdmissionIsImmediateAndCloseIsIdempotent; cancellationReleasesOwnedAdmissionWithoutQueuing |
| Block1PrivateStorageTest (4, real API26 required) | pipeCaptureRehashesImmutableBytesAndIndependentOffsets; canonicalFixtureCapturePreservesOriginalAndRegisteredAssociationIsOptional; actualDirectorySyncCheckpointReopenAndCorruptionFailClosed; observedLimitNotProviderMetadataControlsPipeCopy |

The Android fixture test reads fixture 02 from the existing instrumentation-only
assets, compares source hash/length with manifest identity, and checks exact
snapshot evidence and original survival. Pipe tests use test-owned PFDs through
the real existing DocumentReadAccess interface; no new provider is required.
The filesystem test uses actual no-backup storage and Android directory fsync;
unsupported sync fails its assertion, rather than becoming skipped/PASS.
Restart evidence is reconstruction of ledger/journal objects, **not OS process
death**. Backup exclusion is no-backup location source/placement evidence, not a
new backup/restore campaign. No real picker/provider WRITE behavior is tested.

JVM fault tests exercise the filesystem seam, not Android descriptor behavior.
No test-source count establishes runtime inventory, assertion success or coverage.
All missing exact-byte/64 MiB boundary, full cancellation/acquisition interleaving,
provider blockage, every individual fsync/close fault, forged-chain, hostile-input,
and real-device lifecycle permutations remain **PENDING OWNER VALIDATION**, not
implicitly covered by aggregate counts. The added tests form an initial focused
suite, not proof of 100% critical-scenario acceptance.

## M01–M12 allocation

| Original task | Block 1 truth / remaining work |
| --- | --- |
| M01 inventory | Accepted M-M1 source/threat findings retained; no new acceptance execution |
| M02 contracts | Selected snapshot slice implemented; deferred public publishing/validation absent |
| M03 capture | Real private bounded capture/seal/RO leases implemented; runtime unverified |
| M04 staging | Real unvalidated output staging using same physical owner; runtime unverified |
| M05 validation | DEFERRED/BLOCKED by approved real engine/independent validation |
| M06 engine evaluation | NOT IMPLEMENTED here; no dependency selected/installed |
| M07 Save As | Only private journal/admission prerequisites; no public Save As or host integration |
| M08 recovery | Conservative private scan and initial fault test source; external reconciliation/process interruption pending |
| M09 metadata identity | No mutation; separate identity invariant preserved, later integration deferred |
| M10 API26 providers | Pipe/RO/FD/fsync test source exists; DocumentsProvider/create/write acceptance deferred |
| M11 privacy/backup | Private no-backup paths, redacted types, bounded scans; execution/packaging proof pending |
| M12 handoff | This source ledger and contract document; no formal acceptance or PR |

Original milestone order remains M-M2 contracts → M-M3 snapshots/staging →
M-M4 validation/engine → M-M5 Save As → M-M6 faults/API26 → M-M7 integration →
M-M8 owner acceptance. The explicit consolidated Block 1 order implements private
M-M5 prerequisites now; it does not complete later Save As acceptance.

## M-T01–M-T20 traceability

Every row is NOT RUN. “Source” means implementation/tests exist, never PASS.

| ID | Applicable Block 1 evidence / remaining scope |
| --- | --- |
| M-T01 | Capture/rehash/source integrity, independent leases test source; registered/provider-current identity not newly attested |
| M-T02 | Observed-byte checks, smaller injected bounds, free/aggregate tests; full ceiling/provider metadata permutations pending |
| M-T03 | Cancellation/cleanup test source; real denied/revoked/stalled provider matrix pending |
| M-T04 | Snapshot evidence explicitly private-byte-only; external writer runtime experiment pending |
| M-T05 | Staging write/sync/close/reread/limit test source; no partial mint path |
| M-T06 | DEFERRED engine-backed corrupt/protected validation |
| M-T07 | DEFERRED independent geometry/rotation/content/annotation validation |
| M-T08 | DEFERRED actual separately authorized Save As/readback |
| M-T09 | DEFERRED destination WRITE/create denial; Block 1 acquires neither |
| M-T10 | DEFERRED real provider same-name conflict; no overwrite path added |
| M-T11 | DEFERRED partial public write/provider death/network-disconnect simulation |
| M-T12 | Private cancel primary/cleanup semantics test source; public commit windows deferred |
| M-T13 | Private journal/reconstruction/corrupt retention test source; full restart/picker/public state matrix deferred |
| M-T14 | DEFERRED destination permission races |
| M-T15 | No registry/rebind/favorite/checkpoint changes; future explicit integration tests deferred |
| M-T16 | Policy/budget/retention/admission test source; hostile engine CPU/heap/resource evidence blocked by engine decision |
| M-T17 | Real pipe/RO/directory-sync Android test source; genuine DocumentsProvider create/write/readback deferred |
| M-T18 | No fixture/build/source-set changes; release packaging verification NOT RUN |
| M-T19 | Primary cancellation/programmer error with cleanup fault, retained charges test source; native parser closure deferred |
| M-T20 | No UI/provider write/delete/permission path in new source; future integration negative runtime evidence pending |

## Owner-run commands (NOT executed by Codex)

From the branch checkout, using the existing supported JDK/SDK setup:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.abdev.partituraspdf.storage.transaction.*' \
  --tests 'com.abdev.partituraspdf.pdf.workflow.WriteAdmissionGateTest'
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Filtered JVM invocations may trigger existing coverage finalizers with incomplete
scope; preserve their actual results. Do not disable gates. Then owner-run the
full existing mandatory JVM/regression/localization/fixtures/CI checks at the
implementation SHA. No results from earlier revisions are relabeled here.

Only after separately selecting/authorizing a disposable test installation on an
owned API26 device, use the existing Gradle instrumentation selector:

```bash
ANDROID_SERIAL='<owner-authorized-serial>' ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.storage.transaction.Block1PrivateStorageTest
```

This operation builds/installs/runs test APKs: it is an owner action, not executed
here. Require exactly four completed selected JUnit identities, zero failures,
errors or unexplained skips. Keep source revision/clean tree, APK identities,
device/API/ABI, raw logs and XML; incomplete/uncertain evidence is BLOCKED.
API27/recent Android and applicable physical Android 8.1 evidence remain pending.

**COVERAGE GAP:** existing configured scopes may omit storage.transaction and
pdf.workflow. Coverage is NOT RUN and **BLOCKED — requires separately authorized
coverage configuration** for those production denominators. No build/tooling
changes or threshold reductions were made. The 90% line/80% branch and complete
critical-scenario gates remain mandatory before acceptance.

## Static findings and residual blockers

1. No engine, semantic PDF validator, provider writer, known destination/grant
   binding, true public recovery or host startup wiring exists. Reserved states
   are conservative schema placeholders, not receipts or launch authorization.
2. Private file rename relies on one injected process owner, a no-replace
   precondition and root mutation mutex. It is not cross-process exclusion or an
   atomic no-replace primitive against malicious same-UID code. Future composition
   must not construct two roots/ledgers for the same directory. No hostile same-UID
   security guarantee is made. Symlink checks assume the private owner controls
   the namespace; no existing shared contract was weakened.
3. Required directory fsync failures reject/leave unconfirmed state. Actual API26
   filesystem behavior, ancestor sync, FD independent offsets and physical failure
   durability are PENDING OWNER-RUN; no hardware-power-loss guarantee is claimed.
4. Provider read and close can block indefinitely despite the structured closure
   attempt; bounded responsiveness is BLOCKED pending actual provider evidence.
   No timeout, retry, worker killing or shared API modification conceals it.
5. Cleanup only deletes an artifact proven owned by its live writer/snapshot.
   Restart scan never auto-deletes unknown/partial/corrupt/uncertain artifacts;
   empty directories and conservative reservations may require future reviewed
   disposition. Scan limits reject instead of silently truncating evidence.
6. Recovery does not resurrect snapshot owners/leases or claim provider authority.
   Recognized children are rehashed but orphan capture directories remain BLOCKED.
   Automatic recovery of usable file ownership and safe post-restart disposal need
   an additional reviewed lifecycle binding; retention is the fail-closed behavior.
7. Admission is the injected workflow primitive, not new host wiring. A future
   composed workflow must retain one admission across its complete transaction,
   share the single budget/store, and join cleanup; low-level internal calls do
   not confer write authorization. No normal Reader operation is locked.

## Execution ledger and delivery boundary

Actually executed: attachment/source/policy text reads, rg/file inventory, git
status/branch/HEAD/history/remote inspection, scoped diff/whitespace review and
normal commit/publication on the existing M branch. File-writing commands only
created the approved new implementation/test/documentation files. No test command
or validation script was executed; source inspection is not compilation.

**BUILD: NOT RUN; TESTS: NOT RUN; LINT: NOT RUN; CI: NOT RUN;
COVERAGE: NOT RUN; INSTRUMENTATION: NOT RUN.**

Ready for owner static review and owner-run validation, not phase acceptance or
production integration. Block 2 requires engine comparison/license/security/API26
approval, independent output validation, evidence-bound provider/picker/journal
contracts and separately authorized host integration. No PR, merge, rebase, tag,
release or Block 2 implementation performed.
