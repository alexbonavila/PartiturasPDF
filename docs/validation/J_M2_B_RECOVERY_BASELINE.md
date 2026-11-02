# Phase J M2-B — error and recovery regression

2026-10-20. Parent M2-A: `7523c8edc78ff53fe05cc3a29cc936d94fba9fe5`;
branch `test/mvp-stabilization-j`. M1 and M2-A are separate local commits and the
working tree was clean before this work. This is J06 test/evidence work only.
No production changes or reproduced production defect are claimed. M2-C has not begun.
J08 remains deferred to Phase R.

## Reused coverage and contract audit

All class names are relative to `com.abdev.partituraspdf`. Reuse assertions instead
of duplicating them. JVM doubles exercise the production contract, not actual Android,
PDFium, OS grants, disk exhaustion or native crashes.

| Concern | Existing class.method / evidence | Boundary |
|---|---|---|
| Permission/provider distinctions | app.integration.AppSafLifecycleIntegrationTest.graphReadFailuresKeepTypedBoundariesAndNeverReconcileRegistration; hostPersistsOnlyReturnedReadCapabilityAndRequiresReportedConfirmation; knownGrantLossAndRestorationPreserveMetadataUntilExplicitHostObservation | Actual adapters/SQL with controlled grant/provider platform; no real OS revocation. |
| Actual resolver and bounded copies | storage.saf.SafResolverIntegrationTest.realResolverListsOnlyPdfAndFolders; streamedProviderDocumentUsesBoundedCopyAndDeletesIt; app.integration.SafPdfIntegrationTest.failedProviderPipeIsIoFailureRatherThanDeletionOrInvalidPdf; cancellationDuringRealCopyPropagatesAndDiscardsIncompleteFile | Real resolver/test provider/pipe, not an external provider outage or genuine SAF authorization. |
| Picker cancellation, duplicates and stale tokens | app.host.RootPickerCoordinatorTest.cancellationIgnoresUriFlagsAndPreservesPreviousNavigationWithoutPersistence; concurrentDuplicateCallbackDuringAcceptanceCannotRepeatPersistence; oldCallbackCannotAuthorizeANewerRequest | Deterministic gates and simulated result/platform; one-shot persistence and no newer-state replacement. |
| Failed root replacement and remembered root | app.host.RootPickerCoordinatorTest.partialRootUpdateReportsFailureWithoutRollbackAndRequiresAuthoritativeReconciliation; uncertainPrivateRootWriteRequiresReadReconciliationRatherThanBlindPersistenceReplay | Stored root may have committed despite failed navigation/acknowledgment; never assume rollback or replay persistence. |
| Unsupported/encrypted/malformed behavior | reader.ReaderRecoveryTest.terminalOpenErrorsRequireHostReviewOrANewIntentInsteadOfADeadRetryButton; malformedOrUnsupportedRenderCannotBeSilentlyRetriedByResize; app.integration.ReaderHostNativeIntegrationTest.malformedCanonicalPdfFailsInIsolatedReaderAndStillReturnsSafely; protectedCanonicalPdfRemainsAnExplicitReaderFailureAndCanBeRetired | JVM full typed error matrix plus genuine canonical input/native tests pending Android execution. No promise to decrypt/repair. |
| Native retry and cancellation | reader.ReaderSchedulingTest.closedOrStaleSessionRequiresExplicitRouteReopen; expectedRenderFailureIsRetryableAndCancellationIsDistinct | Typed recovery contract, explicit reopen after destructive cancellation. |
| D04 late output and nondestructive supersession | reader.ReaderSessionLifetimeTest.supersessionDuringDisplayInfoKeepsHealthySession; supersessionDuringRenderDiscardsOldLeaseWithoutNativeCancellation; completedLateRenderReusesSessionOnlyAfterFreshNativeValidation; completedBitmapCannotAuthorizeReuseOfAnInvalidNativeSession | Gated fake native session; fresh native validation required before candidate reuse; obsolete bitmap released once. |
| Destructive cancellation and retirement | reader.ReaderSessionLifetimeTest.destructiveCancellationNeverReborrowsInvalidSessionAndWaitsForExplicitResume; retirementDrainsDestructiveCancellationWithoutReopeningOrPublishing | No ignored cancellation or calls after invalidation; no actual worker kill. |
| Replaced requests | app.coordination.ReaderRouteOwnerTest.supersededOpenClosesLateSessionAndOnlyNewRequestSurvives; reader.ReaderSchedulingTest.replacingDocumentWaitsForNativeSlotAndDiscardsItsOldBitmap | Existing late-success and render-generation coverage retained. |
| Checkpoint and cleanup failures | app.host.ReaderHostLifetimeTest.checkpointAndCloseTypedFailuresAreVisibleAndNeverPreventRemainingCleanup; originalCleanupDefectAndSecondaryFailuresArePreservedWithoutSkippingClose; app.integration.ReaderHostNativeIntegrationTest.injectedCheckpointFailurePreservesRealEarlierPositionAndSafeReopenAfterEmptyRouteVerification | Scheduling acknowledgment is not durable success. Failed flush preserves earlier committed page; typed and original causes retained. |
| Startup and explicit recovery | app.host.AppHostViewModelTest.operationalStartupFailureClosesPartialAttemptAndExplicitRetryUsesFreshResources; originalStartupFailureSurvivesCleanupFailureAndDisablesUnsafeRetry | One fresh owner on explicit retry; unsafe cleanup failure blocks retry. |
| Valid settings preserved | settings.SettingsControllerTest.failedFitIsIndependentAndRetryPreservesLegacyLanguageAndTheme; uncertainCancellationAfterPersistenceUsesFreshObservationAndNeverAssumesRollback | Authoritative observation and independent fields; no optimistic success/default substitution. |

Existing genuine worker-loss/SAF release tests are not executed or newly scheduled here.
Their process retirement and permission mutations remain outside M2-B execution authorization.
Controlled Pdfium diagnostics retain their original assignments and classifications unchanged.

## Missing coverage added (four JUnit methods)

1. `app.coordination.ReaderRouteOwnerTest.lateFailedRegisteredOpenCannotReplaceQueuedDifferentDocumentAndReleasesInput`
   (`J06-late-failed-open`, JVM): gated older registered engine failure while a different transient
   request is queued. Exact failure is retained; only replacement becomes current; failed input
   closes once, successful input stays live until route close, final session closes once; registered
   identity and positions remain untouched. Existing tests used late success/the same location.
   No sleeps, Android objects, native code or alternate executor are needed.
2. `app.integration.ReaderHostNativeIntegrationTest.registeredAccessFailureMatrixPreservesDurableStateAndOnlyRetriesRecoverableErrors`
   (`J06-registered-access`, Android NOT RUN): five injected read failures, denied/revoked/unavailable/
   provider I/O/deleted. No engine open or frame on failed access; exact typed OPEN failure; real
   Room registration/favorite, confirmed SharedPreferences page and file-backed DataStore settings
   preserved. Recoverable cases use a displayed/clickable UI Retry then await actual presentation;
   terminal deleted has no dead Retry. Source bytes and pre-existing grants are unchanged. The
   injected deleted result does not prove a real deletion, and no metadata is automatically erased.
   This is one JUnit matrix, not five separately executed test counts.
3. `app.integration.ReaderHostNativeIntegrationTest.registeredMalformedPdfFailurePreservesFavoritePositionAndPreferences`
   (`J06-registered-malformed`, Android NOT RUN): canonical 15; exact INVALID_DOCUMENT, no frame,
   retained session or presented page, no unsupported repair Retry; safe Back preserves stored state.
4. `app.integration.ReaderHostNativeIntegrationTest.registeredProtectedPdfFailurePreservesFavoritePositionAndPreferences`
   (`J06-registered-protected`, Android NOT RUN): canonical 14; accepts exactly PASSWORD_PROTECTED
   or DOCUMENT_FORMAT; same state/ownership/persistence checks. Original assertions are unchanged.

The three Android tests reuse the existing MainActivity Compose rule, injected fixture host and
read capabilities, actual production route/controller/isolated engine, UUID-owned disk Room,
DataStore and positions. They do not use the production graph's document providers and do not
establish genuine SAF permission behavior. Canonical bytes are verified against the manifest
by the existing mount and copied to owned storage. No source fixture is changed. Existing
teardown unmounts/detaches, awaits owner retirement, closes Room and deletes only owned stores.
Database reads after errors establish stored values in a live disk-backed store; this does not
claim backup, system process-death or fresh-process durability acceptance.

## Phase J evidence

Ten `J06-*` JVM mappings are nested under the existing `J-A-jvm` scenario's `test_cases`;
four Android mappings are ordinary descriptive Tier B entries. The original ten M2-A mappings
are unchanged. Metadata includes exact method, failure classification, fixture/provider origin,
expected outcome and recovery/cleanup assertions. Canonical 02/14/15 hashes come from the
unchanged manifest; all simulated failures are explicitly identified.

No new runner, device command, result importer, CI hook or worker bridge is added. A JVM mapped
PASS requires that exact method in the existing hash-checked, complete, unskipped JVM inventory.
Missing mapped methods invalidate evidence. Unvalidated per-method results are BLOCKED (or
NOT RUN if no JVM command ran), never inferred PASS from another case. Method-specific statuses
are intentionally conservative when full JVM evidence is invalid. Android mappings remain
NOT RUN with null executed method/device NOT SELECTED; compilation cannot promote them.
Host environment/revision/source digest and artifact hashes remain attributable through M1.
Python regressions validate real source mappings, fixture hashes, duplicate IDs, required
classification metadata and the executed-method requirement.

## Validation commands and evidence boundary

```bash
bash ./gradlew :app:testDebugUnitTest --tests '*ReaderRouteOwnerTest' --console=plain --max-workers=4
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --max-workers=4 --console=plain
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 scripts/local_e2e.py run --suite host --report-profile phase-j
git diff --check
```

The targeted JVM invocation executed six passing route tests but failed the **unchanged** global
contract coverage finalizer because a single class is insufficient for its scope. The full JVM
suite then passed 631 tests and that gate. Debug build, instrumentation compilation and Lint
passed. Final host-profile output records fresh full-suite execution, release build, Python,
localization and fixture results against the final working-source digest; see the delivery report
for exact run paths/counts. No earlier result is relabeled as a new run.

## Remaining gaps / acceptance

All Android cases above and reused native/provider cases await explicit M2-C device execution
authorization. No install, launch, instrumentation, emulator operation, grant revocation,
force-stop, native crash induction or worker termination occurred in M2-B. Genuine external
provider failures, real OS permission loss, physical-device recovery, backup/restore and actual
system process death are unverified. No new production failure was reproduced; source risks
remain hypotheses unless supported by later authorized runtime evidence. M2-B provides regression
implementation and host evidence, not native/runtime acceptance. No commit or publication.
