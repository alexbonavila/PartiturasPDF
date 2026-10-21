# M3-B2-A verified J09 Android execution evidence

2026-10-21. This report records independently reviewed owner-operated Android
JUnit executions under the original en-US configuration. It closes only the
reviewed test-method evidence subset, not locale/offline or overall M3 acceptance.

## Source identity and review provenance

- Branch: `test/mvp-stabilization-j`.
- Execution revision: `df643959c4d6bd19f8301f972d4e00bca9636596`.
- Git tree: `1042d26549310ad8faaf9321d52200d43d79c3b9`.
- Source digest: `20d4f768024d7c24ec5e54fb5aafe007ef9029fc725a8c4517171dccb4988b95`.

The independent review confirmed clean execution-source captures and recomputed
that digest using existing repository evidence tooling against the clean baseline.
Branch/revision/tree/build hashes and source-derived JVM inventory agree across
all three captures. This source identity belongs to the execution, not this later
documentation working tree. Host captures record Darwin ARM64, Python 3.13.9 and
Temurin JDK25.0.4.1. Their nested Tier A `device=NOT SELECTED` describes source capture;
actual device identity is recorded separately at the execution record's top level.

## Independent per-API outcomes

All devices record qemu=1, boot=1, ABI arm64-v8a. The original logs were independently
parsed using `local_test_evidence.parse_android`, rather than trusting status fields.
Expected.json, explicit command selections, raw completions and JUnit XML match
exactly: eight J09 methods plus separate smoke, no missing/duplicate/unexpected
identities, failures, errors or skips. Each log has `OK (9 tests)` and valid
`INSTRUMENTATION_CODE: -1`; each recorded ADB process exit is 0 with no blocker.

| API / release | Recorded AVD / serial | Original run directory | Madrid time, 2026-10-21 | Verdict |
|---|---|---|---|---|
| 26 / 8.0.0 | Tablet2 / emulator-5554 | `api26-3688d05233d247fc83a4cdefe78ead10` | 09:50:11–09:50:18 | PASS: 9/9; failures/errors/skips 0/0/0 |
| 27 / 8.1.0 | Tablet_27_Edited / emulator-5556 | `api27-c63c9aef13884db8bd4f8e609f765240` | 09:50:56–09:51:03 | PASS: 9/9; failures/errors/skips 0/0/0 |
| 36 / 16 | Tablet_36 / emulator-5558 | `api36-0197f3d3fd8d4ce98dc305a08b2ce252` | 09:51:33–09:51:56 | PASS: 9/9; failures/errors/skips 0/0/0 |

Total: **27 completed executions of nine distinct JUnit methods**. Repeated internal
locale/theme/fit combinations are not additional JUnit counts. These are selected
instrumentation results, not full-suite CI or new code-coverage measurements.

## Exact scenario mapping

All eight rows below have JUnit PASS on each recorded API, limited to their actual
assertions. Methods are relative to `com.abdev.partituraspdf.`. See the
[locale/offline matrix](J_M3_B_LOCALE_OFFLINE_MATRIX.md) and
[catalogue](../../scripts/j_scenarios.json) for fixture hashes and requirements.

| J09 scenario | Executed method | Evidence origin |
|---|---|---|
| J09-resource-languages | `app.integration.AppLocaleIntegrationTest.deliveredPrimaryLanguagesResolveRealNavigationFeatureAndAccessibilityResources` | scoped-real-resource-context-simulated-locale |
| J09-unsupported-fallback | `app.integration.AppLocaleIntegrationTest.unsupportedPrimaryNeverUsesSupportedSecondaryAndEmptyInputsUseSpanishResources` | scoped-real-resource-context-simulated-locale |
| J09-configuration-preservation | `app.integration.AppLocaleIntegrationTest.configurationContextPreservesNightOrientationDensityFontScaleAndLeavesBaseAndGlobalUntouched` | scoped-real-resource-context-simulated-locale |
| J09-activity-recreation | `app.integration.AppLocaleIntegrationTest.productionActivityUsesActualSystemPolicyAndRecreationRetainsHostAndLocalizedControls` | production-activity-current-device-locale |
| J09-error-resources | `app.integration.AppLocaleIntegrationTest.contextualRecoveryMessagesStayTranslatedAndDistinctWithoutExposingTechnicalPaths` | scoped-real-resource-context-simulated-locale |
| J09-local-registered-journey | `app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition` | production-ui-native-test-capabilities |
| J09-local-transient-journey | `app.integration.ReaderHostNativeIntegrationTest.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration` | production-ui-native-test-capabilities |
| J09-local-persistence | `app.integration.AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization` | real-isolated-disk-adapters |

Separate smoke: `com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext`, PASS
on each API, establishing application identity only. Five AppLocaleIntegrationTest
methods validate scoped resource contexts, configuration preservation and actual
Activity recreation/current-system policy. They do not change system languages.
Reader cases use production UI/native rendering with test-owned local capabilities;
the persistence matrix uses isolated real stores. Neither establishes genuine
external-provider permission or independently proves offline operation.

## APK and configuration attribution

All three records contain matching host/installed hashes for both APKs:

| APK | Recorded host and installed SHA-256 |
|---|---|
| Application | `af108ed0fb97c5d7116a3ffe4716892be96d4139ca22eb03280b901ccd00043b` |
| Instrumentation | `79f7d04a300cb539caa39a4d04ce7a7199794f0e276d587497459de3ad798ec5` |

Six recorded installed/host comparisons agree. No APK binaries, new installation
log or separate raw hash-command output is included in this archive. Therefore this
review confirms consistency of recorded hash values, not independent binary
attestation, proof of the build process or continuous installed-package identity.

Before/after settings snapshots are identical for each API: locale en-US, Wi-Fi=1,
airplane mode=0, mobile data=1, with matching recorded configuration strings. This
supports unchanged recorded endpoints only; there is no continuous settings/network
monitoring or independent live-device inspection here. Enabled connectivity settings
do not establish actual network reachability or absence. No disconnected-guest
execution or settings restoration exercise is claimed.

## Evidence integrity and retention

Input archive: `PartiturasPDF_M3_B2A_J09_Evidence.zip`.
Archive SHA-256: `3a172db23a4b97fb5dd6cc97f314b20c935a2dbb0aae3316073c95f8e5971c8d`.
All twelve payload checksums match SHA256SUMS.txt, with no unlisted payloads.
Safe archive inspection checked duplicate/traversal/absolute/symlink entries.
JSON/XML/raw instrumentation and source/catalogue identities were cross-checked.
No contradictory executed inventory was found. Checksums establish integrity and
internal consistency, not a signed device attestation.

Paths below are archive-relative; preserve the unchanged original archive privately.
Raw logs, host-private paths and the ZIP are not added to Git. Review originals
for privacy before sharing; hashes are references, not substitutes for retaining files.

| Evidence artifact | SHA-256 |
|---|---|
| `api26-3688d05233d247fc83a4cdefe78ead10/execution.json` | `94fd384734833d236f8df538f49ece0fe2d0911d824c6463b647c5db467753d5` |
| `api26-3688d05233d247fc83a4cdefe78ead10/expected.json` | `c255f289a99b12d16bf7f7eb9f6260f9f0a4e31320b5af33da7515e709ae6959` |
| `api26-3688d05233d247fc83a4cdefe78ead10/instrumentation.txt` | `9809147017b8a9ef8e7da6b8842e523dcfb9aa03a916fc94799919a9324ba7e8` |
| `api26-3688d05233d247fc83a4cdefe78ead10/junit.xml` | `fa032b8348f8bdb89b469ced36bc230f9245a25b3f327b1be7dc14e0a145972e` |
| `api27-c63c9aef13884db8bd4f8e609f765240/execution.json` | `e9ecdb7dac2d2d7740416c0a18141e3b9a8a1c4cb79878d64602e032571749aa` |
| `api27-c63c9aef13884db8bd4f8e609f765240/expected.json` | `c255f289a99b12d16bf7f7eb9f6260f9f0a4e31320b5af33da7515e709ae6959` |
| `api27-c63c9aef13884db8bd4f8e609f765240/instrumentation.txt` | `4a53cf5656307ac059e5e3bd4e9766f3070d46a5d6c7ba8682d8eb494480b2fb` |
| `api27-c63c9aef13884db8bd4f8e609f765240/junit.xml` | `d194921a5fb6004170eaa67741356f9093c7272c693553d8fec6985dff2b66ad` |
| `api36-0197f3d3fd8d4ce98dc305a08b2ce252/execution.json` | `e0d3e520d4cbfdf91b2d5033009347f8a4e5a77501a44eb34ec6f39ed6f99356` |
| `api36-0197f3d3fd8d4ce98dc305a08b2ce252/expected.json` | `c255f289a99b12d16bf7f7eb9f6260f9f0a4e31320b5af33da7515e709ae6959` |
| `api36-0197f3d3fd8d4ce98dc305a08b2ce252/instrumentation.txt` | `e62b439839e6e364fe6afc597342faf14f7c53f929f685df832a4a49666164f8` |
| `api36-0197f3d3fd8d4ce98dc305a08b2ce252/junit.xml` | `cabaad808cf78989c48c989db47c652b6543e78f5c42db8c214801f27978df2e` |
| `SHA256SUMS.txt` | `d0d725412ec368f108a467bca5a86924147cdcb5846c3a01ac2b488e8f1b1190` |

## Outstanding acceptance and next step

The following are **NOT RUN in this evidence set** or remain unresolved:

- Actual Android primary-language changes to es-ES, ca-ES, en-US and unsupported
  primary/fallback configurations. Running under original en-US is not testing
  a language-change transition.
- Production-visible translations across all Library, Reader, Settings and error
  screens; scoped resource assertions are narrower than linguistic/UI acceptance.
- Verified guest network disconnection and local document journeys under that state.
- Actual device-language change followed by recreation and verified restoration
  of changed device language/connectivity. No changed-state restoration was exercised.
- AAB-derived installed APK language delivery and offline availability of all bundles.
- Genuine external SAF/provider behavior, physical-device acceptance, M3-C authentic
  coverage attribution, worker loss, real OS process death, controlled Pdfium diagnostics,
  backup/D2D and remaining memory/performance/endurance acceptance.

J08 remains DEFERRED TO PHASE R. Historical M2/M3-A evidence, original classifications,
M3-B1 planning text and host-only NOT RUN projections are preserved. The catalogue
remains descriptive and is not retroactively promoted to PASS. This report separately
attributes the genuinely executed methods without closing broader acceptance.

Recommended next step: owner review of this documentation, followed only on separate
authorization by a precise M3-B2-B device-language/visible-UI plan with named devices,
exact states, observations, settings restoration and bounded method selections.
Disconnected networking and AAB acceptance require their own approved execution
and artifact procedures. No M3-B2-B, M3-B2-C or M3-C begins in this task.
No instrumentation, device operation, source change, commit or publication occurred.
