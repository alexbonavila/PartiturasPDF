# M3-A2 Android compatibility execution evidence

Reviewed 2026-10-21. Owner-provided local execution artifacts support PASS for the
selected J07 subset independently on API26, API27 and API36. This is not full-suite
CI, physical-device acceptance, or completion of M3-B/M3-C.

## Source and artifact identity

- Branch: `test/mvp-stabilization-j`.
- Executed revision: `a9905d497979520cfabf59e038b51f0ed453a8a3`.
- Git tree: `20368dacb320b5e058e121b6e29968527dde9cd1`.
- Source digest: `a4254409464c5fb3fbb9730dc39f6328fc892d11128d11d92bb97ca513df3670`.

The review checkout was clean at that revision. Existing `j_capture` independently
recomputed the matching digest. All three execution-source captures agree on branch,
revision, tree, clean state, build-file hashes and source-derived JVM inventory.
The nested Tier A source capture's `device=NOT SELECTED` describes source capture;
top-level device and command identify the actual Android execution separately.
Recorded host: Darwin ARM64, Python 3.13.9, Temurin JDK 25.0.4.1.

Recorded host APK hashes, identical across all three installations/runs:

| APK | SHA-256 |
|---|---|
| Application | `af108ed0fb97c5d7116a3ffe4716892be96d4139ca22eb03280b901ccd00043b` |
| Instrumentation | `79f7d04a300cb539caa39a4d04ce7a7199794f0e276d587497459de3ad798ec5` |

## Device matrix and independent results

All records report qemu=1, boot=1, ABI arm64-v8a. API/release/AVD/serial agree
between installation and execution records; device properties are recorded owner
artifacts, not live device measurements made by this review.

| API / release | AVD / recorded serial | Evidence directory / run ID | Madrid time, 2026-10-21 | Verdict |
|---|---|---|---|---|
| 26 / 8.0.0 | Tablet2 / emulator-5554 | `api26-eeeeaf3c356047fe89b07d5f5f8bd431` | 08:59:34–08:59:47 | PASS: 18/18, 0 failures/errors/skips |
| 27 / 8.1.0 | Tablet_27_Edited / emulator-5556 | `api27-fef96369ca0e495e939eedd516e40d07` | 09:01:13–09:01:25 | PASS: 18/18, 0 failures/errors/skips |
| 36 / 16 | Tablet_36 / emulator-5558 | `api36-baef10bc83d4464da96969c1edf9096b` | 09:02:10–09:02:44 | PASS: 18/18, 0 failures/errors/skips |

For each run, `expected.json` contains precisely seventeen catalogue J07 methods
plus smoke. The explicit command selects exactly those eighteen methods using the
ordinary AndroidJUnitRunner. Independent `parse_android` reconstruction validates
unique starts/completions, exact inventory, no skips or unexpected results, and
normal completion. JUnit XML agrees on every identity and declares tests=18,
failures=0, errors=0, skipped=0. Each original log has `OK (18 tests)` and valid
`INSTRUMENTATION_CODE: -1`; each execution record has ADB exit=0 and no blocker.
There are 54 completed executions across APIs, not 54 distinct methods.

## Scenario attribution

All following mappings have API26/API27/API36 PASS only for the runs above.
JUnit methods are relative to `com.abdev.partituraspdf.`. Original fixture hashes,
assertions, bounds and cleanup remain in the [matrix](J_M3_A_COMPATIBILITY_MATRIX.md)
and [catalogue](../../scripts/j_scenarios.json).

| J07 scenario | Executed method | Evidence origin |
|---|---|---|
| J07-launcher | `app.integration.AppHostActivityTest.launcherShowsLibraryInsteadOfStarterGreeting` | production-entry |
| J07-settings-navigation | `app.integration.AppHostActivityTest.settingsAndToolbarBackReuseTheSameFeatureOwners` | production-entry |
| J07-registered-roundtrip | `app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition` | production-ui-native-test-capabilities |
| J07-transient | `app.integration.ReaderHostNativeIntegrationTest.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration` | production-ui-native-test-capabilities |
| J07-registered-recreation | `app.integration.ReaderHostNativeIntegrationTest.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen` | production-ui-native-test-capabilities |
| J07-fit-default | `app.integration.ReaderHostNativeIntegrationTest.persistedDefaultFitAndLocalMenuRemainDistinctWhileConfirmedChangesStayInSameNativeSession` | production-ui-native-test-capabilities |
| J07-registered-access | `app.integration.ReaderHostNativeIntegrationTest.registeredAccessFailureMatrixPreservesDurableStateAndOnlyRetriesRecoverableErrors` | production-ui-native-test-capabilities |
| J07-registered-malformed | `app.integration.ReaderHostNativeIntegrationTest.registeredMalformedPdfFailurePreservesFavoritePositionAndPreferences` | production-ui-native-test-capabilities |
| J07-registered-protected | `app.integration.ReaderHostNativeIntegrationTest.registeredProtectedPdfFailurePreservesFavoritePositionAndPreferences` | production-ui-native-test-capabilities |
| J07-settings-reinitialize | `app.integration.AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization` | real-isolated-disk-adapters |
| J07-root-authorization | `data.foundation.FoundationPersistenceTest.rootSelectionReplacementRestartRevocationAndRecovery` | real-private-file-with-simulated-grants |
| J07-picker-binding | `app.integration.RootPickerBindingTest.actualRegistryForwardsOriginalIntentUriAndFlagsWithoutAuthorizationOnLaunch` | simulated-activity-result |
| J07-read-only-grant-contract | `app.integration.AppSafLifecycleIntegrationTest.hostPersistsOnlyReturnedReadCapabilityAndRequiresReportedConfirmation` | simulated-grant-platform |
| J07-resolver-listing | `storage.saf.SafResolverIntegrationTest.realResolverListsOnlyPdfAndFolders` | real-resolver-test-provider |
| J07-copy-cancellation | `app.integration.SafPdfIntegrationTest.cancellationDuringRealCopyPropagatesAndDiscardsIncompleteFile` | real-resolver-test-pipe-injected-cancellation |
| J07-copy-lease-ownership | `app.integration.SafPdfIntegrationTest.sessionCloseReleasesCopyWhileCallerRetainsBitmapUntilLeaseClose` | real-resolver-test-pipe-direct-native-engine |
| J07-posted-pixels | `reader.ReaderNativeIntegrationTest.c_nativePostedPixelsSurviveOriginalLeaseRecycling` | real-native-surface-test-route |

Separate smoke: `com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext`, PASS
on each API; this establishes application identity only. Six preference combinations
and access-error matrices each remain one executed JUnit method.

## Installation and attestation boundaries

Installation run `0a99c02de5d2475096a3fd7e24f7dd8e` records successful app/test
installations on all three emulators before instrumentation. Each device's two
`installed_sha256` values match the corresponding host APK hashes, and each later
execution record repeats those matches: six installed-host comparisons agree.

The archive includes JSON records, not host or pulled installed APK binaries,
raw hashing commands, or separate per-install command logs/exit records. Therefore
this review verifies consistency of recorded installed hashes; it cannot independently
rehash the APKs, attest package state at every instant, prove the build process,
or establish cryptographic device provenance. SHA256SUMS integrity is not a signature.

**Operational authorization clarification — owner statement, 2026-10-21:**
API26 installation actions record successful uninstallations of both the app and
test package before reinstalling; API27/API36 record packages as not installed.
The owner states in the final-review conversation that they previously requested
instructions to uninstall old APKs and install the latest ones, were informed that
uninstalling would delete app-private data and persisted SAF permissions, and then
executed the procedure with `--delete-existing-data`. This conversational instruction
and acknowledged execution establish the owner's stated authorization context.

The original archive contains no separately archived operation-specific approval,
signed authorization, or independent proof that prior application data was disposable.
The flag and warning history above are attributed to the owner's clarification,
not independently reconstructed from the archive. Do not infer preservation of
pre-existing API26 data from passing tests. These operational evidence limitations
do not invalidate the verified JUnit results, but data-disposability attestation
remains absent. No uninstall or device operation was performed by Codex in this review.

## Verification and evidence retention

Input: `PartiturasPDF_M3_A2_Evidence.zip`, SHA-256
`cc5ee04006aa32ffd057934a1df10aa9f6d4135f7ada12467c82f90b2e712cfa`.
Safe extraction checked duplicate entries, traversal, absolute paths and symlinks.
All thirteen listed payload hashes matched SHA256SUMS.txt; there are no unlisted
payload files. JSON, XML, explicit selection, device and source identities were
cross-checked using existing evidence tooling. No tests or device commands ran.

Below are archive-relative paths; retain the original archive privately. It is not
added to Git. Raw logs may contain private device/host information and should not
be uploaded automatically. This report omits host-private paths and raw diagnostics.

| Evidence file | SHA-256 |
|---|---|
| `install-0a99c02de5d2475096a3fd7e24f7dd8e/install.json` | `c99f878568d31cec525b23082c59acc657db6f95cceefd66b2b90c06ff3d8438` |
| `api26-eeeeaf3c356047fe89b07d5f5f8bd431/execution.json` | `6635d5fe7ac1b923cc48cf7c498577e47cad36588b480abfccff8f516f7f57c3` |
| `api26-eeeeaf3c356047fe89b07d5f5f8bd431/expected.json` | `96488ccc30430d26212666e3b4c0e97b26abb1b35103980e1f14a8622422d42b` |
| `api26-eeeeaf3c356047fe89b07d5f5f8bd431/instrumentation.txt` | `167affeaf4da3afb7a63c291e715f81aa4a22f0650cb3a4f954deec35568deae` |
| `api26-eeeeaf3c356047fe89b07d5f5f8bd431/junit.xml` | `d7a8ead1fdb6cf4814794fdb46e31da5e68bd4ff614ffd1d81c79f3c1dfe97d1` |
| `api27-fef96369ca0e495e939eedd516e40d07/execution.json` | `b4a2cdecaa4ef7f9efacfb20d60dea470229b1e650571f51b7ee366f25c0075f` |
| `api27-fef96369ca0e495e939eedd516e40d07/expected.json` | `96488ccc30430d26212666e3b4c0e97b26abb1b35103980e1f14a8622422d42b` |
| `api27-fef96369ca0e495e939eedd516e40d07/instrumentation.txt` | `34f708b8e7991b9460a9ddd0c8ac78a61d7a50df88253890c9f4f5d6213524f5` |
| `api27-fef96369ca0e495e939eedd516e40d07/junit.xml` | `042b22e945c053781e974eaca87f9c7117d4fd54416d6f65420d0730b9dd3296` |
| `api36-baef10bc83d4464da96969c1edf9096b/execution.json` | `47d9b9514d6549c0d47786efa94b3a765d94b9c964710ba618f167c205695f52` |
| `api36-baef10bc83d4464da96969c1edf9096b/expected.json` | `96488ccc30430d26212666e3b4c0e97b26abb1b35103980e1f14a8622422d42b` |
| `api36-baef10bc83d4464da96969c1edf9096b/instrumentation.txt` | `875995d3bbd41051bf2495d8cef55ced3abeac6400683e7f7a56d4d969d0d98f` |
| `api36-baef10bc83d4464da96969c1edf9096b/junit.xml` | `7f28f337a0d52fb9a2d42d288bb3509d5e12de0e82e1a02cbdbb438e1be65a51` |
| `SHA256SUMS.txt` | `9d9f76642b1414ff0fd3cdb30e80c031993c89ce721b65a39b555427dad6c82b` |

## Limits and outstanding acceptance

Native host tests use real production UI/isolated rendering with injected fixture
capabilities. Resolver tests use test providers; copy cancellation is injected after
real progress. Posted-pixel/copy-lease tests also exercise direct native paths and
cannot certify worker-process coverage. Synthetic grant flags are not OS revocation.
No hidden full-suite, controlled diagnostic, or cross-API coverage result is inferred.

Still uncompleted by these artifacts: genuine DocumentsUI SAF acquisition/revocation,
external-provider acceptance, explicit worker loss, actual OS process death, fourteen
controlled Pdfium diagnostics, physical tablet acceptance, backup/restore/D2D,
performance/endurance/memory, M3-B locale/offline acceptance and M3-C authentic coverage
attribution. J08 remains DEFERRED TO PHASE R. Emulator API27 is not physical Android
8.1 acceptance. Selected API26/27/36 functional compatibility evidence is verified;
formal milestone acceptance remains for the owner, including the operational issue above.

Historical M2 evidence, the initial BLOCKED attempt, its subsequent PASS, catalogue
NOT RUN projections and immutable host-only reports remain unchanged. M2's execution
revision `83e0094cea8a20a90635f82b97b84610a11d2a2e` is distinct from these M3 runs.
No new CI result, production change, test implementation, runner, fixture or gate
change is introduced. Only a local documentation commit is authorized at final review; no push, PR, merge,
M3-B or M3-C execution is authorized or performed.
