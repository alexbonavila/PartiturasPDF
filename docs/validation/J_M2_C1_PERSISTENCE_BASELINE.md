# Phase J M2-C1 — persistence and execution readiness

2026-10-20. Parent `efad21e0f74fdd21e5f56b9c481b68fa8c06a09b`, branch
`test/mvp-stabilization-j`. Clean preflight confirmed separate M1, M2-A and M2-B commits.
No M2-C2/device execution is authorized. No production or protected infrastructure changes.
J08 remains deferred to Phase R.

## Persistence audit and reused evidence

All class names below are relative to com.abdev.partituraspdf. These are implementations,
not Android runtime PASS claims. See J_M2_ANDROID_EXECUTION_MATRIX.md for exact execution IDs.

| Requirement | Existing tests reused | Evidence boundary |
|---|---|---|
| Room identity/favorite reload | app.integration.AppPersistenceIntegrationTest.sharedRoomObservationAndMetadataSurviveACompletelyNewGraphAndDatabase | Actual isolated disk Room and new graph/connection; no process death. |
| Theme/fit serialization | data.preferences.DataStorePreferencesRepositoryTest.allThemesPersistTheirExplicitStableKeys; allPageFitsPersistTheirExplicitStableKeys; completePreferencesRoundTripExactlyThroughRecreatedStore | Actual serialized values; only one non-default complete snapshot previously tested across fresh file owners. |
| Graph settings reload | app.integration.AppPersistenceIntegrationTest.graphPreferencesAreIndependentAndPersistAfterStoreAndRoomOwnersReopen | Actual borrowed DataStore ownership and disk Room; final LIGHT/WIDTH snapshot, not all six pairs. |
| Registered displayed page and recreation | app.integration.ReaderHostNativeIntegrationTest.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen | Native visible frame/checkpoint; genuine Activity recreation retains owner/session. |
| Registered navigation reconstruction | app.integration.ReaderHostNativeIntegrationTest.syntheticSavedBundleReopensDiskRegisteredScoreAtDurablyPresentedPageWithFreshNativeSession | Real stores/renderer with synthetic Bundle/new host. Not real OS process death. |
| Requested/rendered versus presented/durable | app.host.AppRestorationIntegrationTest.requestedAndRenderedPagesNeverBecomeDurableDuringReconstruction; pendingPresentationIsUnconfirmedAndReconstructionReadsEarlierCommittedPage; failedFlushNeverInventsNewCommittedPageAndNewOwnerReadsActualStorage; successfulBackgroundFlushIsRestoredByNewOwnerWithoutAnotherPositionStore | Deterministic JVM stores distinguish requested/rendered, presented, scheduled and committed. Existing scheduling success is explicitly unconfirmed. |
| Actual checkpoint failure | app.integration.ReaderHostNativeIntegrationTest.injectedCheckpointFailurePreservesRealEarlierPositionAndSafeReopenAfterEmptyRouteVerification | Real earlier SharedPreferences position, injected write failure; safe reopen requires empty route, no fake disk-full claim. |
| Transient identity | app.host.AppRestorationIntegrationTest.transientSnapshotDowngradesToLibraryWithoutWritingPositions; app.integration.ReaderHostNativeIntegrationTest.syntheticTransientSnapshotCannotAutomaticallyReopenOrCreateRegisteredPosition; transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration | No implicit registration or position. Synthetic reconstruction is not process death. |
| Selected root versus authorization | data.foundation.FoundationPersistenceTest.rootSelectionReplacementRestartRevocationAndRecovery; app.integration.AppSafLifecycleIntegrationTest.realPersistedGrantQueryDoesNotTurnRegistrationIntoAuthorization | Real private root file with simulated allowed flag; real platform grant query is distinct. Neither proves a new genuine grant. |
| Background/foreground | app.integration.ReaderHostNativeIntegrationTest.genuineActivityBackgroundFlushesAndResumeReopensWithoutNewUserIntent | Actual Activity lifecycle; same intent, new native session on resume. |
| Registration/settings survive failures | M2-B registeredAccessFailureMatrixPreservesDurableStateAndOnlyRetriesRecoverableErrors; registeredMalformedPdfFailurePreservesFavoritePositionAndPreferences; registeredProtectedPdfFailurePreservesFavoritePositionAndPreferences | Disk stores, injected read failures or canonical bad input. No actual grant revocation/deletion. |
| Startup recovery | app.host.AppHostViewModelTest.operationalStartupFailureClosesPartialAttemptAndExplicitRetryUsesFreshResources | JVM deterministic resource cleanup/retry; already J06-mapped, no duplicate test. |

## Demonstrated gap and one new test

`AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization`
(`J10-settings-reinitialize`) checks each SYSTEM/LIGHT/DARK x PAGE/WIDTH pair through a fresh
DataStore file owner and reopened disk Room graph. It asserts authoritative Loading -> Ready
observations before and after reinitialization, the complete expected preferences, independently
issued registration identity and favorite retention. The old store job is canceled and joined
before creating its replacement; no duplicate active file owner is possible. The graph never
owns/cancels its borrowed store. Existing UUID-only cleanup is reused. The overall new matrix
is bounded to 60 seconds; each observation uses the existing 10-second bound. This is one JUnit
test with six combinations, not six executed tests. It does not claim Activity recreation or
process death, and it does not update the production DataStore.

No extra lifecycle/checkpoint/root tests are added where existing assertions already establish
the requirement. Five reused JVM methods receive stable J10 contract IDs under J-A-jvm; six
Android methods (one new, five reused) receive J10 IDs, real/synthetic origins and NOT RUN status.
Previous M2-A/B mappings remain intact. Execution-group metadata only describes a future safe
selection; it does not add a runner path or authorize/trigger execution. Python validates that
this selection resolves to ordinary tests, excludes genuine SAF and worker-loss operations,
and cannot become Android PASS from host checks.

## Static emulator assessment — BLOCKED

Only files were inspected. The available config declares API26 google_apis/x86_64, 2G RAM;
this image/ABI is compatible with the current Linux x86_64 host in principle. The SDK image
files exist. However, the existing local runner's build/local-e2e/device.json ownership manifest
is absent, the AVD has a different identity from the runner's reserved UUID naming convention,
and /dev/kvm is absent. Acceleration, disposable ownership, live serial, API, boot state and
screen configuration are NOT VERIFIED. It must not be adopted, wiped, started or used.
No emulator/ADB command was executed. A separate owner's Mac/Windows AVD cannot be certified
from these cloud files. An owner-approved, verified disposable API26 emulator is required later.

## Validation and remaining acceptance

Authorized checks are the existing JVM/coverage, Python, build/Lint/instrumentation compilation,
localization, all 15 immutable fixtures and Phase J host-profile evidence. Actual run paths/counts
are recorded in the delivery report. Android remains NOT RUN; no missing device becomes PASS.
No previous evidence is relabeled. No new production defect was reproduced or inferred from
missing Android execution. New-source evidence records the parent revision plus dirty-source digest.

Remaining: authorized runtime execution of the selected tests; genuine production system picker
-> provider -> Reader acceptance; real permission/provider loss; genuine OS process death;
physical-device checks. Backup/restore, grant revocation, force-stop, uninstall/clear, worker-loss
induction and controlled diagnostics require separate approval and are excluded from the proposed
first run. No M2-C2, commit, push, PR, merge, tag or release.
