# Phase J M2-A — functional baseline for owner review

2026-10-20. Branch `test/mvp-stabilization-j`; parent M1
`a99d1d4b8a77ad235399093805183c4cfaa8c7f6`. Changes remain uncommitted.
This extends J02 only. No M2-B/C, process killing, backup, benchmarks, stress or J08 work.
Android runtime execution is **NOT RUN**; APK compilation is not execution evidence.

## Existing coverage reused

Class names below are relative to `com.abdev.partituraspdf`.
The `J-B-*` IDs resolve to exact methods in `scripts/j_scenarios.json`.

| Scenario ID | Existing class.method | Actual path and limits |
|---|---|---|
| J-B-launcher | app.integration.AppHostActivityTest.launcherShowsLibraryInsteadOfStarterGreeting | Real production MainActivity/default graph and usable Library shell; no document or picker. |
| J-B-settings-navigation | app.integration.AppHostActivityTest.settingsAndToolbarBackReuseTheSameFeatureOwners | Production Settings/Back UI retains owners. Also androidBackReturnsFromSettingsWithoutDisposingGraph covers Android Back. |
| J-B-transient | app.integration.ReaderHostNativeIntegrationTest.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration | Real host/Library/Reader UI, isolated native engine, posted score pixels and presented-page acknowledgment; test-owned file capability and injected host session. No registration or position is invented. |
| J-B-registered-recreation | app.integration.ReaderHostNativeIntegrationTest.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen | Real Room, native worker, displayed-page checkpoint, Activity recreation and isolation metadata; registration initiated through the host test API, not UI. |
| J-B-fit-default | app.integration.ReaderHostNativeIntegrationTest.persistedDefaultFitAndLocalMenuRemainDistinctWhileConfirmedChangesStayInSameNativeSession | Native PAGE/WIDTH with real DataStore changes through repository API; not Settings clicks. |
| J-B-folder-navigation | app.integration.LibraryPersistenceIntegrationTest.acceptedRootListsNavigatesRegistersAndPersistsFavoriteAcrossControllerRestart | Controller-level enter/back, registration, favorites and restart using real Room; synthetic catalog and controlled grant callback/platform. Not DocumentsUI authorization. |
| J-B-picker-binding | app.integration.RootPickerBindingTest.actualRegistryForwardsOriginalIntentUriAndFlagsWithoutAuthorizationOnLaunch | Actual ActivityResultRegistry with simulated result; verifies forwarding, not genuine picker selection. |
| J-B-read-only-grant-contract | app.integration.AppSafLifecycleIntegrationTest.hostPersistsOnlyReturnedReadCapabilityAndRequiresReportedConfirmation | Production adapter/host with controlled grant platform, rejects WRITE-only and unconfirmed capabilities. Not an OS-granted permission. |
| J-B-genuine-saf | storage.saf.SafLocalE2eTest.acquire (scripts/local-e2e/androidTest) | Existing opt-in runner/debug acceptance Activity and real DocumentsUI; persists/verifies READ-only access and canonical bytes on guarded API26 emulator. It is not the production MainActivity picker-to-Reader flow. Do not invoke without separate owner/device authorization. |

Existing LibraryScreenTest and SettingsScreenTest validate detailed state presentation and
callback dispatch against supplied state. They do not establish controller/storage/native E2E.
Existing ReaderNativeIntegrationTest validates native rendering; ReaderScreenTest covers
presentation and controls separately. Neither is relabeled as a complete production graph trip.

## Gap and minimum addition

Previously, Settings effects in the native host suite used repository updates, and registration
used host calls. There was no single UI-driven registered Library -> native Reader -> Library
-> Settings -> Library -> reopened native Reader journey proving their composition together.

Add **one** ordinary instrumentation test:
`app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition`
(`J-B-registered-roundtrip`). It reuses the existing MainActivity Compose rule, test-owned
AppHostViewModel/session, in-memory Room, file-backed DataStore, READ_ONLY ParcelFileDescriptor,
production Library/Reader/Settings code and isolated native worker. It does not use the default
AppGraphFactory for these fixtures; MainActivity is the Android/lifecycle/container entry point.

The test asserts:

1. Real UI registration and favorite commands reconcile to observed Room-backed state.
2. Canonical fixture 02 (two pages; SHA-256 from immutable manifest) is actually posted to a
   Surface, with both ink/paper PixelCopy observations and Reader presentation acknowledgment.
3. Next page is presented as page index 1; WIDTH and PAGE each produce a different visible
   frame while preserving the presented page. Native render completion alone is insufficient.
4. Toolbar Back retires the Reader, releases its frame and empties its route; page 1 is durable.
5. Settings radio clicks select DARK/WIDTH only after observed authoritative preferences;
   independent repository observation confirms both values. Settings does not open a PDF.
6. UI Back/open returns to the same registered identity at the committed page with WIDTH,
   a fresh controller and actually visible canonical pixels; favorite remains true.
7. Source bytes and all pre-existing persisted URI grants remain unchanged.

No arbitrary delays, fake Presented events, fabricated Android grants, production hooks or
persistent application-store modifications are introduced. Existing suite teardown detaches
surfaces, awaits owned host/Reader cleanup and deletes only UUID-owned stores/copies. The
PixelCopy helper uses the actual fit when calculating its sample region (PAGE remains unchanged
for existing callers). No fixture original is written.

## Evidence and execution boundary

The M1 host profile remains the only executor. Ten Tier B catalogue mappings are descriptive:
`implemented=true` means the test exists; `status=NOT RUN` means no device execution evidence.
They have no host `check`, cannot become a host command, and cannot declare PASS/FAIL from
compilation. Each report carries mapped method, null executed method, source revision/digest,
fixture ID/hash (or explicit synthetic/no-file identity), evidence origin, expected outcome and
required environment. Actual device/environment is NOT SELECTED/NOT RUN. Host PASS applies
only to the eight Tier A checks; it is never Android acceptance.

Python regressions resolve all mappings to real source inventory, check canonical fixture hashes,
preserve NOT RUN and reject attempts to make a device mapping a host command or a PASS.
The profile still rejects all device suites before execution. No importer, alternate runner or
automatic Android dispatch is introduced. Actual future results require authorized execution
and trustworthy full JUnit evidence for the exact revision; the catalogue is not that evidence.

Owner-review commands (host only):

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --max-workers=4 --console=plain
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 scripts/local_e2e.py run --suite host --report-profile phase-j
git diff --check
```

Pending: authentic Android execution of the new test and reused methods on an explicitly
identified/authorized device; production MainActivity system picker -> actual provider -> native
Reader combined acceptance; external provider and physical-tablet verification. The existing
genuine SAF test uses a separate debug Activity and grants no proof for those unexecuted paths.
No permissions, worker protocol, production behavior, CI gates or immutable fixtures change.
