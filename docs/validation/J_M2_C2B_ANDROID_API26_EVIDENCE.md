# M2-C2B Android API 26 execution evidence

Evidence reviewed 2026-10-21. This closes the approved nineteen-method M2 execution
subset plus smoke for owner review, not full M2 or Phase J acceptance.

## Source and environment

- Branch: `test/mvp-stabilization-j`.
- Executed revision: `83e0094cea8a20a90635f82b97b84610a11d2a2e`.
- Git tree: `232d69b0d02d4ffee52772ea033dc7923d53682c`.
- Source digest: `d5eba5bc97ef83f26292e46d9464fa4e2e5764b4047942d3f4d41659312a8387`.

Both original captures report clean source. Independently recomputing the existing
source-digest algorithm against the clean review checkout matched; tree, build-file
hashes and source-derived JVM inventory also matched both captures. These identities
refer to the executed baseline, not the later documentation working tree.

Recorded host: Darwin/ARM64, Python 3.13.9, Temurin JDK 25.0.4.1. Recorded device:
owner-authorized `Tablet2`, `emulator-5554`, API 26, `arm64-v8a`, `qemu=1`.
Android 8.0 and working Hypervisor.Framework/boot readiness are owner-reported context;
the raw run records establish API/ABI/emulator/AVD identity, not an independent
acceleration or complete OS-build inspection. No physical device is represented.
The nested `pre_execution_host_source` is a Tier A source capture with device
`NOT SELECTED`; actual Android identity is separately recorded in top-level `device`
and the instrumentation command. It must not be mistaken for a host-only test run.

## Independent verification and execution history

Safe ZIP inspection rejected absolute/traversal/symlink entries before extraction.
All ten manifest-listed files matched their SHA-256 values, with no unlisted evidence
files apart from the manifest itself. JSON, XML and raw instrumentation were inspected;
the existing `local_test_evidence.parse_android` independently reconstructed results.
Manifest and assistant summary were cross-checked, not treated as primary execution proof.
Hashes establish package integrity and internal consistency, not cryptographic device attestation.

| Run | Madrid local time, 2026-10-21 | Actual completed cases | Result |
|---|---|---:|---|
| `2aa7c781983a449782fb0d82ef6b1613` | 07:27:59–07:28:09 | 15 of 20 | BLOCKED: five missing identities; zero assertion failures |
| `78f78ee62b104c348082ca13bbaafae8` | 07:33:49–07:34:04 | 20 of 20 | PASS: zero failures, errors or skips |

Run 1 has `OK (15 tests)` and ADB exit 0, but is incomplete. Its XML `tests=16`
counts fifteen genuine Android cases plus one synthetic infrastructure error, not
sixteen Android tests. The original BLOCKED classification remains unchanged.
Missing methods (package prefix `com.abdev.partituraspdf.app.integration.`):

- `AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization`
- `ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition`
- `ReaderHostNativeIntegrationTest.registeredAccessFailureMatrixPreservesDurableStateAndOnlyRetriesRecoverableErrors`
- `ReaderHostNativeIntegrationTest.registeredMalformedPdfFailurePreservesFavoritePositionAndPreferences`
- `ReaderHostNativeIntegrationTest.registeredProtectedPdfFailurePreservesFavoritePositionAndPreferences`

Run 2 has twenty distinct starts and completions matching `expected.json`, the
catalogue selection and exact `-e class` command. XML agrees with the raw results:
`tests=20`, `failures=0`, `errors=0`, `skipped=0`. Raw completion is `OK (20 tests)`
and `INSTRUMENTATION_CODE: -1`; recorded host ADB exit is 0, with no blocker.
No missing, unexpected, duplicate or skipped method was accepted. Neither run was
rerun during this review, and the first attempt was not overwritten.

## APK provenance

Both execution records contain the same host-built APK hashes:

- Debug application: `af108ed0fb97c5d7116a3ffe4716892be96d4139ca22eb03280b901ccd00043b`.
- Instrumentation: `2989a146c17ccd1ccc20ea829655d81f89f96c38f8a35de9c68aea2b15740541`.

`OPERATOR_APK_INSTALLATION_TRANSCRIPT.md` separately records the owner's pre-reinstall
pulled instrumentation hash `a9003fb9b9c631ecbf8202514b1e44f0c76ba5668445137dc58c1c2bab3cf230`,
which differs from the host test APK hash, followed by two `install -r` outputs of
`Success`. This is explicitly transcribed owner-provided Terminal output, not an
original independently captured installation log. It supports a stale/different
installed APK explanation for missing tests, not a demonstrated product defect.
No APK binaries are included: none of these binary hashes can be recomputed here,
and no post-install on-device binary hash attestation is available. The subsequent
complete run establishes execution of the requested methods without filling that gap.

## Exact scenario attribution

Every row below is PASS only in run `78f78ee62b104c348082ca13bbaafae8` on API 26.
Methods are relative to `com.abdev.partituraspdf.`. Fixture identity, assertions,
bounds and cleanup remain defined in the [execution matrix](J_M2_ANDROID_EXECUTION_MATRIX.md)
and [catalogue](../../scripts/j_scenarios.json).

| Scenario | Executed JUnit method | Evidence origin |
|---|---|---|
| J-B-launcher | `app.integration.AppHostActivityTest.launcherShowsLibraryInsteadOfStarterGreeting` | production-entry |
| J-B-settings-navigation | `app.integration.AppHostActivityTest.settingsAndToolbarBackReuseTheSameFeatureOwners` | production-entry |
| J-B-registered-roundtrip | `app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition` | production-ui-native-test-capabilities |
| J-B-transient | `app.integration.ReaderHostNativeIntegrationTest.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration` | production-ui-native-test-capabilities |
| J-B-registered-recreation | `app.integration.ReaderHostNativeIntegrationTest.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen` | production-ui-native-test-capabilities |
| J-B-fit-default | `app.integration.ReaderHostNativeIntegrationTest.persistedDefaultFitAndLocalMenuRemainDistinctWhileConfirmedChangesStayInSameNativeSession` | production-ui-native-test-capabilities |
| J-B-folder-navigation | `app.integration.LibraryPersistenceIntegrationTest.acceptedRootListsNavigatesRegistersAndPersistsFavoriteAcrossControllerRestart` | simulated-provider-controller |
| J-B-picker-binding | `app.integration.RootPickerBindingTest.actualRegistryForwardsOriginalIntentUriAndFlagsWithoutAuthorizationOnLaunch` | simulated-activity-result |
| J-B-read-only-grant-contract | `app.integration.AppSafLifecycleIntegrationTest.hostPersistsOnlyReturnedReadCapabilityAndRequiresReportedConfirmation` | simulated-grant-platform |
| J06-registered-access | `app.integration.ReaderHostNativeIntegrationTest.registeredAccessFailureMatrixPreservesDurableStateAndOnlyRetriesRecoverableErrors` | production-ui-native-test-capabilities |
| J06-registered-malformed | `app.integration.ReaderHostNativeIntegrationTest.registeredMalformedPdfFailurePreservesFavoritePositionAndPreferences` | production-ui-native-test-capabilities |
| J06-registered-protected | `app.integration.ReaderHostNativeIntegrationTest.registeredProtectedPdfFailurePreservesFavoritePositionAndPreferences` | production-ui-native-test-capabilities |
| J06-checkpoint-native | `app.integration.ReaderHostNativeIntegrationTest.injectedCheckpointFailurePreservesRealEarlierPositionAndSafeReopenAfterEmptyRouteVerification` | production-ui-native-test-capabilities |
| J10-settings-reinitialize | `app.integration.AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization` | real-isolated-disk-adapters |
| J10-root-authorization | `data.foundation.FoundationPersistenceTest.rootSelectionReplacementRestartRevocationAndRecovery` | real-private-file-with-simulated-grants |
| J10-registered-restoration | `app.integration.ReaderHostNativeIntegrationTest.syntheticSavedBundleReopensDiskRegisteredScoreAtDurablyPresentedPageWithFreshNativeSession` | production-ui-native-with-synthetic-bundle |
| J10-transient-restoration | `app.integration.ReaderHostNativeIntegrationTest.syntheticTransientSnapshotCannotAutomaticallyReopenOrCreateRegisteredPosition` | production-ui-native-with-synthetic-bundle |
| J10-settings-reconstruction | `app.integration.ReaderHostNativeIntegrationTest.syntheticNewHostReloadsActualDataStoreThemeAndDefaultFitWithFreshReader` | production-ui-native-with-synthetic-bundle |
| J10-background | `app.integration.ReaderHostNativeIntegrationTest.genuineActivityBackgroundFlushesAndResumeReopensWithoutNewUserIntent` | production-ui-native-test-capabilities |

Smoke is separately PASS: `com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext`.
It verifies application identity only, not feature correctness. Six theme/fit pairs
and the injected access-error matrix are each one JUnit method, not additional test counts.

Real native and UI cases validate the production host/Reader route and visible frame
assertions where specified, using test-owned capabilities/stores. Controller/provider
and permission failures remain injected or simulated. Saved Bundle/new-host reconstruction
is not real OS process death. These passes do not establish genuine DocumentsUI grants,
external-provider behavior, or every ordinary Android test in the repository.

## Evidence identities and retention

Input archive: `PartiturasPDF_J_M2_C2B_API26_Evidence_COMPLETE.zip`.
Archive SHA-256: `89079f4a3ccb05b80ca23e1b136715f61ffc0a909002e03495b0b115bb131310`.
Paths below are archive-relative. Original owner-local run directories are
`build/j-m2-authorized/<run-id>/`; retain those unchanged. The archive was reviewed
outside Git and is not added to the repository. Private host paths and raw logs
are deliberately omitted. Retain the input archive with owner-controlled evidence;
these hashes alone cannot recover missing artifacts.

| Artifact | SHA-256 |
|---|---|
| `EVIDENCE_SUMMARY.md` | `9d4eabab49be9e8d6e30c8f1149c3ef8dd304d1c755db2875d7a2da94b8c7c66` |
| `OPERATOR_APK_INSTALLATION_TRANSCRIPT.md` | `ffe43c8ef2be41c2f6cdd91b56193cbe63964a99d305d3cb317129db881a2c34` |
| `first_blocked/execution.json` | `9d4244de4148ee8394ccb9a86733db81f1b4c6ba57268cb7bc4873f4e7893742` |
| `first_blocked/expected.json` | `dfa397d99ceb4f3152dd863bbdab73fd80dc72390cf5f23487fb2f2683a3dfc1` |
| `first_blocked/instrumentation.txt` | `0619aae58cb2860d3020501a4d1f326c029aa0a76d4c4efbbb8c7eaa8bb232ad` |
| `first_blocked/junit.xml` | `b350be2eaa04cb709f95e4270ccc94f3df1c819e6fd18cffca3cf6d5839b9697` |
| `second_pass/execution.json` | `882de19f5adb39f208eddc01f088d6f726148c8b81141707684e4432de476c38` |
| `second_pass/expected.json` | `dfa397d99ceb4f3152dd863bbdab73fd80dc72390cf5f23487fb2f2683a3dfc1` |
| `second_pass/instrumentation.txt` | `6db2dc90f3387115ab8c6e38425617042b0cb0e2ad62fec91c73bdca875e8de9` |
| `second_pass/junit.xml` | `367ca2af053c3e203bd0ada781c27ff0e1f3040ddd3f24de17a7de67c5133c3d` |
| `manifest.json` | `25f438d8fc127f7d8b9b26670a833f81ceaebcb8dae1948e52915359f0114f58` |

## Remaining acceptance and next milestone

The approved M2-C2B API26 execution/evidence deliverable is verified and ready for
owner closure review. Full M2/Phase J acceptance is not inferred. Still NOT RUN
in this evidence set: genuine DocumentsUI acquisition/persisted grants and production
picker-to-provider-to-Reader acceptance; actual OS permission revocation/external
provider failure; explicit worker-loss injection; genuine OS process death; fourteen
controlled Pdfium diagnostics; API27/API36 compatibility; physical Android tablet
acceptance; memory/endurance/performance and backup/restore/D2D acceptance.
J08 remains DEFERRED TO PHASE R.

Historical host-only reports, catalogue NOT RUN projections and original readiness
assessments remain unchanged. This separate report attributes genuine Android results
without importing them into the host-only Phase J profile or claiming full-suite CI.
Earlier 631-JVM/326-Python/build/Lint/localization/fixture results retain their original
provenance; none was rerun or relabeled during this documentation review. No coverage
percentage or new hosted-CI result is established by this subset.

Recommended next milestone: an owner-approved M3 plan for J07/J09/J11, with exact
revision/device/API26/27/36 matrix, locale/offline operations, evidence attribution
and outstanding M2 gaps explicitly carried forward. Device settings and execution
require operation-specific authorization. No M3 implementation or execution started.
No device commands, source/test changes, commit, push or merge occurred in this review.
