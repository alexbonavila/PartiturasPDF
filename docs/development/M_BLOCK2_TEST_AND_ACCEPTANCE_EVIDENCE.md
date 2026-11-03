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
