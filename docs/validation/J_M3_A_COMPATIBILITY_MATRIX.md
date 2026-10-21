# M3-A1 Android compatibility matrix — NOT RUN

2026-10-21. Mapping baseline `d4b276d565a96122086a1af86c43699cbaf6caba`,
branch `test/mvp-stabilization-j`. This is descriptive preparation only: no M3-A2,
installation, runtime execution or device-setting changes are authorized.

## Inventory and execution boundary

Seventeen representative J07 methods below apply independently to API **26 (8.0),
27 (8.1), and 36**. All three API result columns are initially **NOT RUN** for every
row. Add the existing `com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext`
smoke separately: a future complete selection is **18 distinct JUnit cases per API**,
not the former M2 twenty-case selection. Methods below are relative to
`com.abdev.partituraspdf.`; authoritative identifiers also live in
[the catalogue](../../scripts/j_scenarios.json), group `m3-compatibility-ordinary`.

The existing schema has no structured API applicability field. The supported
`test_environment` string records `Android API 26/27/36`; M3-specific integrity tests
check this exact convention. Runtime `j_catalogue` retains its existing generic
validation: it does not enforce new API-specific semantics. No schema, loader,
runner or automatic execution path is added. Catalogue mappings and host-only
reports must remain NOT RUN regardless of historical M2 results.

Each row requires matching source/app/test APKs, installed test provider where used,
API-specific native-compatible emulator ABI and an unlocked foreground display.
Production-entry cases initialize the real app graph: the entire installation and
its existing data must be confirmed disposable. Other fixtures use owned stores.
Expected outcome is identical on all three APIs: satisfy the existing assertions.
No invented API-specific error substitutions, raster hashes or timeout relaxation.

| J07 ID | Exact method | Fixture / origin | Observable requirement | Existing bounds / cleanup |
|---|---|---|---|---|
| J07-launcher | `app.integration.AppHostActivityTest.launcherShowsLibraryInsteadOfStarterGreeting` | none / production-entry | MainActivity shows usable Library shell and settings action; host destination is LIBRARY | 10 s readiness; owned cleanup below |
| J07-settings-navigation | `app.integration.AppHostActivityTest.settingsAndToolbarBackReuseTheSameFeatureOwners` | none / production-entry | Settings and toolbar Back retain feature owners and return to Library | 10 s readiness; owned cleanup below |
| J07-registered-roundtrip | `app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition` | 02 / production-ui-native-test-capabilities | UI registration and favorite; posted score pixels, page 2 and PAGE/WIDTH; UI Back retires route; Settings UI durably changes theme/fit; reopen preserves registration, favorite and page; source and grants unchanged | 15 s state; 20 s presentation; owned cleanup below |
| J07-transient | `app.integration.ReaderHostNativeIntegrationTest.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration` | 02 / production-ui-native-test-capabilities | Visible canonical pixels, next page and WIDTH; retired controller on Back; no registration or persisted position; new controller on reopen | 15 s state; 20 s presentation; owned cleanup below |
| J07-registered-recreation | `app.integration.ReaderHostNativeIntegrationTest.registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen` | 02 / production-ui-native-test-capabilities | Registered presented page flushes; Activity recreation retains native owner; isolated nonexported worker | 15 s state; 20 s presentation; owned cleanup below |
| J07-fit-default | `app.integration.ReaderHostNativeIntegrationTest.persistedDefaultFitAndLocalMenuRemainDistinctWhileConfirmedChangesStayInSameNativeSession` | 02 / production-ui-native-test-capabilities | Local PAGE override remains distinct from durable WIDTH; confirmed fit change reaches visible frame without session replacement | 15 s state; 20 s presentation; owned cleanup below |
| J07-registered-access | `app.integration.ReaderHostNativeIntegrationTest.registeredAccessFailureMatrixPreservesDurableStateAndOnlyRetriesRecoverableErrors` | 02 / production-ui-native-test-capabilities | Exact denied/revoked/unavailable/IO/deleted errors; no native open on failure; identity/favorite/confirmed page/settings retained; UI Retry renders only recoverable failures; no real grant change | 15 s state; 20 s presentation; owned cleanup below |
| J07-registered-malformed | `app.integration.ReaderHostNativeIntegrationTest.registeredMalformedPdfFailurePreservesFavoritePositionAndPreferences` | 15 / production-ui-native-test-capabilities | Native invalid PDF has no successful frame or Retry; safe Back preserves real registration/favorite/position/preferences and source bytes | 15 s state; 20 s presentation; owned cleanup below |
| J07-registered-protected | `app.integration.ReaderHostNativeIntegrationTest.registeredProtectedPdfFailurePreservesFavoritePositionAndPreferences` | 14 / production-ui-native-test-capabilities | Exact PASSWORD_PROTECTED or DOCUMENT_FORMAT; no frame or Retry; safe Back preserves real metadata/preferences and bytes | 15 s state; 20 s presentation; owned cleanup below |
| J07-settings-reinitialize | `app.integration.AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization` | synthetic / real-isolated-disk-adapters | All six theme/fit combinations survive joined DataStore and Room graph reinitialization; registration/favorite unchanged | 60 s matrix; 10 s observations; owned cleanup below |
| J07-root-authorization | `data.foundation.FoundationPersistenceTest.rootSelectionReplacementRestartRevocationAndRecovery` | synthetic / real-private-file-with-simulated-grants | Remembered root reloads; simulated permission loss fails restore without erasing stored replacement; renewed permission restores it | No method wall timeout; external 1200 s watchdog; owned cleanup below |
| J07-picker-binding | `app.integration.RootPickerBindingTest.actualRegistryForwardsOriginalIntentUriAndFlagsWithoutAuthorizationOnLaunch` | synthetic / simulated-activity-result | ActivityResultRegistry forwards original tree result and flags; launch alone never authorizes | 10 s exercise/metadata; owned cleanup below |
| J07-read-only-grant-contract | `app.integration.AppSafLifecycleIntegrationTest.hostPersistsOnlyReturnedReadCapabilityAndRequiresReportedConfirmation` | synthetic / simulated-grant-platform | READ-only take requested; reported persisted grant confirmation required; metadata preserved | 10 s exercise/metadata; owned cleanup below |
| J07-resolver-listing | `storage.saf.SafResolverIntegrationTest.realResolverListsOnlyPdfAndFolders` | synthetic / real-resolver-test-provider | Listing is complete; exactly four PDF/folder entries, correct identity and no registration; not a DocumentsUI grant | No method wall timeout; external 1200 s watchdog; owned cleanup below |
| J07-copy-cancellation | `app.integration.SafPdfIntegrationTest.cancellationDuringRealCopyPropagatesAndDiscardsIncompleteFile` | 10 / real-resolver-test-pipe-injected-cancellation | Actual copy progress precedes cancellation; CancellationException propagates and incomplete copy directory is empty | No method wall timeout; external 1200 s watchdog; owned cleanup below |
| J07-copy-lease-ownership | `app.integration.SafPdfIntegrationTest.sessionCloseReleasesCopyWhileCallerRetainsBitmapUntilLeaseClose` | 02 / real-resolver-test-pipe-direct-native-engine | Session close removes copy but retains usable caller bitmap; lease close recycles it and subsequent bitmap access fails | No method wall timeout; external 1200 s watchdog; owned cleanup below |
| J07-posted-pixels | `reader.ReaderNativeIntegrationTest.c_nativePostedPixelsSurviveOriginalLeaseRecycling` | 01 / real-native-surface-test-route | PixelCopy sees real ink; posted pixels remain identical after original bitmap lease is recycled | 20 s visibility; 30 s retirement; owned cleanup below |

`bounded_wait_ms` is a descriptive upper observation/watchdog bound, not a new
per-method timeout implementation. It does not replace individual shorter waits.
Use an external 1200-second run watchdog through the existing executor. Never
interpret timeout as permission to recycle an in-flight native buffer or force-close
an unknown owner. Existing teardown detaches composition, joins/cancels owned jobs,
closes Room/DataStore/session/descriptor/bitmap owners, verifies source bytes where
tested and removes only test-owned UUID data. Resolver listing owns no copy;
copy tests close handles and delete their own directory. Failed cleanup blocks
acceptance; preserve diagnostics before any separately approved intervention.

## Selection rationale and coverage boundaries

- Startup/Settings reuse production entry tests; the combined registered trip covers
  registration, favorites, PAGE/WIDTH, Settings persistence, Back and visible reopening.
- Transient, retained recreation and default/local fit cases cover distinct identity,
  lifetime and preference semantics rather than duplicate the trip.
- Three registered error cases cover injected access outcomes and real canonical
  malformed/protected inputs. Keep their exact existing error contracts, including
  the protected case's PASSWORD_PROTECTED or DOCUMENT_FORMAT alternatives.
- Reinitialization covers all six theme/fit pairs with joined DataStore owners and
  reopened Room; root restoration uses simulated permission availability, not revoke.
- Binding/grant contracts, real resolver listing, cancellation after actual pipe-copy
  progress and copy/bitmap ownership cover complementary adapter boundaries.
- Posted pixels tests actual surface content after original lease recycling. It
  uses a test route and direct native adapter, not proof of isolated-worker coverage.

Canonical fixture IDs resolve to the immutable manifest and its hashes; synthetic
provider bytes are not canonical PDFs or real external-provider authorization.
Selected SafPdf tests use the test provider; copy cancellation is deliberately
injected after progress, not worker death. Native host tests use injected capabilities
with production UI/isolated rendering; visible-frame assertions are retained.

Inspected additional scheduling/lifetime/checkpoint/cancellation coverage remains
reusable: ReaderSchedulingTest and ReaderSessionLifetimeTest are JVM contract evidence,
not Android compatibility PASS. Existing injected checkpoint failure, background,
synthetic saved-Bundle restoration and broader native corpus tests remain outside
this minimum first group; their omission is not acceptance. No new assertion gap
justifies changing Android test implementations in M3-A1. A later supplemental
selection requires explicit review, not automatic whole-class execution.

## Historical versus fresh evidence

[M2-C2B](J_M2_C2B_ANDROID_API26_EVIDENCE.md) verifies 19 ordinary methods plus smoke
(20/20) at `83e0094cea8a20a90635f82b97b84610a11d2a2e`. Its first attempt remains
BLOCKED. The later `d4b276d565a96122086a1af86c43699cbaf6caba` commit documents those
results; it was not that execution's source. None becomes a new J07 PASS.
Historical CI99/CI101 and L04 compatibility are exact-revision historical evidence,
not fresh M3 API27/36 results. Preserve original reports and the M2 group unchanged.

## Future M3-A2 procedure and authorization checkpoints

1. Freeze and record the execution revision, tree, source digest and expected
   eighteen identities; source-derived inventory must contain every method.
2. Recheck owner authorization, emulator name/serial/API/ABI/qemu/boot/display and
   disposable installation/data before any write. API26 candidate is Tablet2,
   previously emulator-5554, arm64-v8a; previous identity is not proof of current state.
3. API27/36 have no authorized emulator. Inspect installed/available SDK images
   and native host compatibility first. Apple Silicon requires a supported native
   ARM64 image and acceleration; do not assume API27 images exist or use an x86
   software fallback. If unavailable, propose an independently authorized compatible
   x86 host or hosted CI; no provisioning is authorized here.
4. Build matching debug/test APKs with the current wrapper, JDK25, compile SDK37
   and build tools37. Verify manifest runner, fixture packaging and APK hashes.
   Install only after exact-device approval. Record installed-package verification
   separately from host APK hashes; owner transcripts are not binary attestation.
5. Select only this group's exact `Class#method` entries plus smoke. Reuse
   `local_e2e.execute(..., timeout=1200)`, `local_test_evidence.parse_android` and
   `write_junit` as in the [M2 procedure](J_M2_ANDROID_EXECUTION_MATRIX.md), replacing
   its selection and API guard explicitly. Never weaken the runner's API26 guard.
   Do not execute that M2 block unchanged: its catalogue filter and API are different.
6. Retain per-API unique raw output, expected.json, JUnit, command/exit/times,
   device/source/class/APK identity and failure diagnostics. Preserve partial logs
   on timeout or interruption. PASS requires all 18 unique expected results,
   no skips/errors/failures, valid runner completion and host exit 0. Trustworthy
   completed assertion failures are FAIL; missing/duplicate/unexpected/incomplete
   evidence or environment/cleanup uncertainty is BLOCKED. ADB exit 0 alone is insufficient.
7. Review each API independently before documenting fresh results. Do not upload
   private raw logs automatically or relabel historical host-only reports.

This document is a runbook design, not another runner or a dispatch command.
API27/36 require distinct identity checks; no current local runner multi-API support
is implied. Hosted `ci.yml` supports compatibility=true, but invokes broader suites
and controlled diagnostics: dispatch needs separate authorization for that scope.

## Exclusions and acceptance

Excluded: genuine DocumentsUI acquisition/release, OS grant revoke, explicit worker
loss, forced process death, clear/uninstall/restore, backup/D2D, fourteen controlled
Pdfium diagnostics, full-suite/endurance execution, physical tablet acceptance.
Safe simulations above cannot close these gaps. J08 remains DEFERRED TO PHASE R.
API27 emulator results cannot substitute for physical Android 8.1 acceptance.

M3-A1 acceptance is valid source/catalogue/matrix mapping with passing Tier A integrity
checks and no device operations. M3-A2 readiness/execution, M3-B locale/offline tests,
M3-C coverage and formal M3 acceptance remain separate approvals and evidence.
