# M3-B1 locale and offline acceptance matrix — NOT RUN

2026-10-21. Baseline `71a62c86deaf9f9cb732daad3dce4797762b0328`, branch
`test/mvp-stabilization-j`. Mapping only; no new execution, installation or settings
change. [M3-A2](J_M3_A2_ANDROID_COMPATIBILITY_EVIDENCE.md) remains historical
compatibility evidence, not locale/offline PASS. M2 and J07 mappings are unchanged.

## Implementation and reused evidence boundaries

DeviceLocalePolicy selects the primary es/ca/en language; unsupported primary falls
back to Spanish even if a secondary supported language is present. Production
AppLocaleContext reads system resources and applies an Activity-owned override;
legacy persisted language is not an active override. No in-app selector is permitted.
Default/values-es Spanish plus values-ca/en delivery, formatting and parity are
checked by validate_localization.py and its existing negative tests. Production
manifest has no INTERNET permission and Gradle disables AAB language splitting.
These are source/static facts, not proof of installed distribution or offline use.

Existing JVM DeliveredLocalePolicyTest methods (package com.abdev.partituraspdf.settings):
`deliveredRegionalLanguagesUseTheirOwnBundles`,
`unsupportedPrimaryLanguageFallsBackToSpanishRatherThanSecondaryEnglish`, and
`completedBundlesDoNotSilentlyChangeUndeliveredPolicyDefaults` validate pure policy;
reuse the full JVM suite, do not infer Android execution from them.
Existing LocalizedResourcesTest methods `spanishResourcesResolveWithoutChangingGlobalLocale`,
`catalanResourcesResolveWithoutChangingGlobalLocale`,
`englishResourcesResolveWithoutChangingGlobalLocale`, and
`unsupportedLocaleUsesSpanishDefaultResources` provide scoped resource checks.
They overlap the selected integrated resource checks and are not added merely to
increase counts. MainActivity context tests do not establish manual device changes.

## Selected Android methods

All eight mappings apply independently to API26/27/36 and initially remain NOT RUN.
Methods are relative to `com.abdev.partituraspdf.`. Each requires matching source and
APKs, ordinary AndroidJUnitRunner, verified owned disposable emulator and unlocked
screen. Canonical fixtures/hashes resolve to the unchanged manifest. All raw results
must retain exact method identity and source/device/configuration attribution.

| Scenario | Exact JUnit method | Observable assertion / capability | Evidence boundary |
|---|---|---|---|
| J09-resource-languages | `app.integration.AppLocaleIntegrationTest.deliveredPrimaryLanguagesResolveRealNavigationFeatureAndAccessibilityResources` | Real scoped es/ca/en contexts resolve navigation, Library/Reader/Settings and accessibility resources; not device-language switching / fixture none | scoped-real-resource-context-simulated-locale |
| J09-unsupported-fallback | `app.integration.AppLocaleIntegrationTest.unsupportedPrimaryNeverUsesSupportedSecondaryAndEmptyInputsUseSpanishResources` | Unsupported primary with supported secondary, empty or blank inputs resolves Spanish scoped resources / fixture none | scoped-real-resource-context-simulated-locale |
| J09-configuration-preservation | `app.integration.AppLocaleIntegrationTest.configurationContextPreservesNightOrientationDensityFontScaleAndLeavesBaseAndGlobalUntouched` | Scoped Catalan context preserves orientation, night mode, density and font scale without changing base/global configuration / fixture none | scoped-real-resource-context-simulated-locale |
| J09-activity-recreation | `app.integration.AppLocaleIntegrationTest.productionActivityUsesActualSystemPolicyAndRecreationRetainsHostAndLocalizedControls` | Actual MainActivity follows current system policy; recreation retains host, Settings content and localized Back control / fixture none | production-activity-current-device-locale |
| J09-error-resources | `app.integration.AppLocaleIntegrationTest.contextualRecoveryMessagesStayTranslatedAndDistinctWithoutExposingTechnicalPaths` | Scoped es/ca/en recovery strings are distinct and nonblank without technical URI/exception leakage; not visible error-screen linguistic acceptance / fixture none | scoped-real-resource-context-simulated-locale |
| J09-local-registered-journey | `app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition` | UI registration and favorite; posted score pixels, page 2 and PAGE/WIDTH; UI Back retires route; Settings UI durably changes theme/fit; reopen preserves registration, favorite and page; source and grants unchanged; offline attribution requires separately recorded disconnected environment, never inferred from this method alone / fixture 02 | production-ui-native-test-capabilities |
| J09-local-transient-journey | `app.integration.ReaderHostNativeIntegrationTest.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration` | Visible canonical pixels, next page and WIDTH; retired controller on Back; no registration or persisted position; new controller on reopen; offline attribution requires separately recorded disconnected environment, never inferred from this method alone / fixture 02 | production-ui-native-test-capabilities |
| J09-local-persistence | `app.integration.AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization` | All six theme/fit combinations survive joined DataStore and Room graph reinitialization; registration/favorite unchanged; offline attribution requires separately recorded disconnected environment, never inferred from this method alone / fixture synthetic | real-isolated-disk-adapters |

Add separate identity smoke `com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext`:
**nine exact JUnit cases per selected configuration**, not nine locale/device states.
One resource test loops several tags and one persistence test loops six theme/fit
pairs; those loops are not independently executed JUnit cases. Resource contexts do
not mutate the device. Reader native paths use test-owned local file capabilities
and production presentation, not a genuine DocumentsUI selection or external provider.

Existing bounds: Activity readiness 15 s; Reader state 15 s and presentation/PixelCopy
20 s; persistence matrix 60 s with 10 s observations. Scoped resource methods have no
explicit method wall timeout. Use the existing 1200 s external run watchdog.
Cleanup awaits owned host/route/store work, closes leases/descriptors/stores and removes
only owned UUID copies. No timeout permits unsafe native recycling or broad data clearing.

## Acceptance configurations and missing runtime evidence

| Requirement | Future cases | Evidence required beyond selected methods | Initial status |
|---|---|---|---|
| Device primary languages | es-ES, ca-ES, en-US independently on API26/27/36 | Recorded ordered system locales; actual Activity language and visible Library, Reader, Settings and contextual error text | NOT RUN |
| Spanish fallback | fr-FR primary, optionally en-US secondary | Actual system order and Spanish production controls; scoped simulated fallback alone is insufficient | NOT RUN |
| Recreation/configuration | Same approved language states | Real Activity recreation with retained host; actual language-change transition is separate from static context tests | NOT RUN |
| Offline startup/journeys | Approved disconnected state for each language/API row | Before/during/after connectivity evidence, fresh Activity launch, visible local PDF, navigation/Back/reopen and authoritative persisted settings/metadata | NOT RUN |
| Offline delivery | Exact debug APK and AAB-derived installed APK set | Retained artifact hashes; es/ca/en compiled resources and bundle language-split configuration; all languages usable without fetching missing splits | NOT RUN |
| Settings restoration | Every changed device | Original ordered locales/connectivity plus verified final restoration; failure is BLOCKED | NOT RUN |

The initial complete cross-product is 3 APIs x 4 primary-language states. Run scoped
multi-language resource checks once per API where useful; repeat only relevant actual
Activity/journey methods per configuration and record the exact selection/count for
each run. No entire class or unfiltered suite is implicitly authorized. Connectivity
must be explicitly verified; absence of INTERNET or injecting a provider failure is
not offline evidence. Airplane mode alone does not prove Wi-Fi is disabled. Local
emulator/ADB transport must remain usable without giving the guest network access.

**Gaps requiring separate test approval if automation is required:** existing methods
do not assert every rendered screen/error translation after a real system-language
change, do not themselves measure connectivity, and do not prove AAB store-delivery
semantics. Record owner-visible observations independently, or request a narrowly
scoped extension to AppLocaleIntegrationTest before claiming fully automated acceptance.
Do not add production hooks, broadcast locale hacks or optimistic PASS to bridge gaps.
AAB artifact inspection needs an existing available bundletool/SDK resource tool;
if unavailable, mark delivery verification BLOCKED, never install a dependency silently.
No signing key or private artifact should enter Git. Universal APK inspection alone
is not proof of the actual device-specific AAB delivery path.

## Proposed M3-B2 procedure — future authorization only

1. Owner approves exact revision, device/serial/API/ABI, disposable app installation,
   language states, guest connectivity changes, bounded test selection, visible manual
   observations and restoration. Reverify identity/boot/screen; old Tablet2 identity is
   historical. API27/36 require their own explicit authorization. No uninstalls or data
   clearing are part of this procedure.
2. Record source capture with existing j_capture, build matching debug/test APKs with
   `bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`, retain hashes and
   installed-package identity. For AAB acceptance additionally build `:app:bundleDebug`
   and review generated APK set/resources using the existing supported toolchain;
   installed distribution and debug artifacts must not be conflated.
3. Before any settings change, retain original ordered language list, Wi-Fi/mobile/
   airplane state and connectivity evidence. Owner changes languages through the real
   Android Settings UI and verifies system state on each API; no assumed cross-version
   shell locale setter. Do not force-stop or restart unrelated processes.
4. Select exact methods from catalogue group `m3-locale-offline-observation` plus smoke;
   turn each final dot into `#` and join with commas. For approved per-state reductions,
   retain the explicit expected inventory rather than falsely expecting all nine.
   Reuse the [M2 bounded execution block](J_M2_ANDROID_EXECUTION_MATRIX.md) and its
   `local_e2e.execute(command,timeout=1200)`, `parse_android` and `write_junit` helpers.
   Replace its group, expected count and exact API/device identity guard explicitly;
   do not run that API26/M2 block unchanged or weaken the local runner's API26 guard.
5. With owner-approved guest network disconnection recorded and verified, execute:
   `adb -s "$ANDROID_SERIAL" shell am instrument -w -r -e class "$selected" com.abdev.partituraspdf.test/androidx.test.runner.AndroidJUnitRunner`.
   This command is illustrative and must be invoked through the existing bounded
   helper, not pasted as an unbounded alternative. The selected Activity tests launch
   actual MainActivity; no manual extra cold-start/force-stop claim is inferred.
6. Preserve per-API/per-language unique expected.json, raw output, JUnit and execution
   metadata with settings/connection observations, fixture/source/APK identities,
   times and exit. Separately retain visible-screen/manual evidence. Record absence
   of connectivity evidence as offline BLOCKED even when JUnit assertions pass.
7. In a finally-style owner-controlled procedure, restore exact original language
   order and connectivity, then verify restoration. Never auto-clear app data/grants,
   induce worker death, uninstall or reset as cleanup. Preserve partial results and
   report restoration uncertainty without masking original assertion failures.

A genuine complete selected JUnit assertion failure is FAIL; incomplete/missing/
duplicate/skipped results, invalid completion, nonzero success-path ADB exit or
unverified environment is BLOCKED. JUnit PASS does not automatically promote device
locale, offline, AAB or manual acceptance. Each dimension needs its own evidence.
Host-only Phase J reports/catalogue remain NOT RUN for Android until separately
attributed execution review; no new importer or runner is introduced.

## Exclusions and review boundaries

No genuine grant acquisition/revocation, worker loss, OS process death, backup/D2D,
uninstall/clear, full suite, benchmarks/endurance or physical-device operation is
implied. Those M2/M3 gaps remain open; J08 stays DEFERRED TO PHASE R. M3-C coverage
is separate. This document authorizes neither M3-B2 nor device configuration changes.
