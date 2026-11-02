# Phase J — M3-B2-C Android Offline Evidence

Execution date: **2026-10-22**, as supplied by the owner.
Branch: `test/mvp-stabilization-j`.
Original execution source: `6b2294b715abbfd8787c0878caf8d95116fa4e35`.
Projection's recorded review checkout: the same revision.
Documentation baseline: `0e99f93e5a14145f332d9c1af07151dae52d76ac`.

**Selected functional offline scope: completed on owner-reported evidence**, with
successful later radio-settings restoration. This is not full M3-B or Phase J acceptance.
The original execution remains **BLOCKED**; no uninterrupted PASS run is fabricated.

## Inputs and verification level

Original owner-local evidence directory:
`build/j-m3-b2c/offline-three-e7b58701943e415f9fb9d219c11dd24a`.

Authoritative supplied projection:
`offline-closeout-review-8393c46c9c564465ae4e6103c61c2845.json`.
Its type is `owner-local-m3-b2c-post-run-restoration-projection`, schema 1, with
`reviewed_at_utc=2026-10-22T15:56:12.608518+00:00` as recorded by the owner-local script.
That is a recorded review timestamp, not the instrumentation start/end time.

The projection was independently inspected for required fields, exact API scope,
unique method inventory, counts, affirmative offline observations, matching original/
current radio settings, identity flags and restoration results. Its supplied bytes
were independently hashed. All five method identities exist in repository Android
test source. No new evidence parser or test infrastructure was created.

The original directory is absent from this checkout. Therefore primary JUnit outcomes,
monitoring observations, device/boot identity matches, original restoration failures
and later successful restoration are **owner-reported from the supplied projection**.
Codex did not independently parse original instrumentation/JUnit XML, inspect monitoring
samples, rehash original artifacts, query a device or perform binary attestation.
The projection's PASS field alone was not used as proof: each required per-API condition
was checked for consistency. The raw JSON, device identifiers and private logs are not
committed. Retain the original records and projection privately for future review.

## Methodology and exact selected scope

The owner executed five existing instrumentation methods on each of API26, API27 and
API36 under recorded offline conditions. Connectivity was checked before and after
instrumentation and sampled during it. Primary functional results, offline observations,
initial cleanup and subsequent restoration are separate dimensions.

| API / Android | Primary JUnit | Before/after offline checks | During-test samples | All recorded samples | Original restoration | Later restoration |
|---|---|---|---|---|---|---|
| 26 / 8.0 | 5/5 PASS | PASS | 5 | PASS | **BLOCKED** | PASS |
| 27 / 8.1 | 5/5 PASS | PASS | 5 | PASS | **BLOCKED** | PASS |
| 36 / 16 | 5/5 PASS | PASS | 12 | PASS | PASS | PASS |

All entries above are projection-attributed owner reports. Each API has
`original_test_status=PASS`, five methods and `non_pass=0`. There are **15/15 primary
JUnit executions** of five distinct methods, not fifteen distinct methods or a full
Android test-suite run. The projection confirms `offline_selected_scope=PASS` per API
and `combined_selected_scope=PASS` after the separate restoration verification.

The during-test sample counts sum to 22. They exclude the separately reported before/
after observations, whose counts and raw contents are not supplied. Each API has at
least one passing during-test observation. The projection reports all recorded offline
samples passing, but supplies neither sampling intervals nor network interface/probe
details. These are sampled observations, **not continuous packet-level network isolation**.
No condition between samples or outside the observed intervals is certified here.

## Selected JUnit inventory and functional attribution

The projection lists exactly:

1. `com.abdev.partituraspdf.app.integration.AppHostActivityTest.launcherShowsLibraryInsteadOfStarterGreeting`
2. `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition`
3. `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration`
4. `com.abdev.partituraspdf.app.integration.AppPersistenceIntegrationTest.everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization`
5. `com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext`

This selection exercises production launcher/Library entry, the registered Reader/UI/
Settings journey, transient native reading and navigation without registration, real
isolated Room/DataStore reinitialization across the theme/fit combinations, and app
identity smoke. Existing Reader methods assert visible rendered presentation rather
than treating render dispatch as frame presentation. Their canonical local test
capabilities are narrower than genuine external SAF/provider authorization.

Method identity and existing source responsibilities were inspected here; actual
execution and offline attribution remain owner-reported. One persistence method may
iterate multiple combinations without becoming multiple JUnit cases. The projection
does not establish a locale-by-offline cross-product or exhaustive document coverage.
Auxiliary checks and later restoration verification are not additional primary tests.

## Original blockers and later restoration

The projection explicitly preserves `original_run_status_unchanged=BLOCKED`.
Original API26/API27 restoration checks were BLOCKED; the owner reports those checks
occurred before Wi-Fi was fully restored. API36's original restoration was PASS.
These initial classifications remain unchanged and must not be overwritten by the
subsequent review.

The separate post-run projection records, for **each** API:

- Original airplane mode `0` and Wi-Fi `1`.
- Later observed airplane mode `0` and Wi-Fi `1`.
- `same_boot=true` and `avd_and_api_match=true`.
- `later_radio_settings_restoration=PASS`.

Thus the original and later radio-settings values match in all three records, including
matching device/boot identity flags. Exact boot identifiers are neither supplied nor
invented in this report. This is later restoration evidence, not evidence that the
original cleanup had already passed or that radio settings remained continuously stable.
Restored settings do not independently establish subsequent Internet reachability.
No claim is made about unreported settings such as mobile data or device language.

## Evidence hashes and integrity limits

| Evidence reference | SHA-256 | Verification level |
|---|---|---|
| Supplied `offline-closeout-review-8393c46c9c564465ae4e6103c61c2845.json` | `b0df0a6f9e6e3b1617bd0be957bdcfc2515e65b9d41b6d86081d8cae1cff0844` | Independently computed from the supplied file |
| Original execution summary | `37281e5c5372fcedba4e10c280a46af7d79f2fca6e9f846a5bc67de3d6a1408a` | Recorded projection value; original file unavailable |
| API26 retained JUnit artifact | `7da587ee01397fa1e875b0e9143649b156e34209422b1f9dd718c6b05abf408c` | Recorded projection value; original file unavailable |
| API27 retained JUnit artifact | `4d6a559d4adfe8e803b94537c67fa85a3d0e79b3bcd905e472caf9bc5c914f80` | Recorded projection value; original file unavailable |
| API36 retained JUnit artifact | `4590fab0d3a7af87e3d13d5347bcd8353ff31636d6a6fe49a7b122003307be96` | Recorded projection value; original file unavailable |

The projection does not supply relative filenames for those summary/JUnit artifacts;
none are guessed. Their hash formats and exact copied values were checked, but the
original payloads could not be rehashed. The projection checksum identifies the supplied
bytes, not a signed device attestation or proof of authenticity of every underlying
observation. No APK hash or independent installed-binary verification is available here.

The documentation checkout differs from the execution revision only through the prior
M3-B2-B documentation closeout; no application or test implementation change is inferred.
Historical [locale evidence](J_M3_B2B_ANDROID_EVIDENCE.md), M2/M3-A reports, original raw
results and catalogue statuses remain unchanged. This closeout does not rerun any tests.

## Closeout and remaining acceptance

The supplied projection confirms all selected closeout conditions: three APIs with
five passing primary methods each, valid recorded offline checks before/during/after,
and separate later restoration to original airplane/Wi-Fi settings with matching
identity flags. **M3-B2-C selected functional offline scope is completed on that
owner-reported basis**, subject to the owner's formal acceptance and the stated
independent-verification limitations. The original run remains globally BLOCKED.

Outside this selected completion:

- AAB-derived device-specific language delivery and fully offline language availability.
- Genuine external SAF/provider access, authorization and revocation.
- Physical Android device acceptance, including Android 8.1 tablet validation.
- Unobserved network conditions, continuous packet capture, every transport/interface,
  and exhaustive offline documents or locale combinations.
- Full manual linguistic review, contextual error-screen review and TalkBack acceptance.
- Live Activity language-transition acceptance, which this offline run does not establish.
- Additional OS process-death, worker-loss, recovery, backup/D2D, performance, memory or
  endurance acceptance not explicitly demonstrated by this selection.
- M3-C authentic coverage attribution and remaining Phase J milestones, including J14/J15
  final consolidation. J08 remains **DEFERRED TO PHASE R**.

The next proposed milestone is **AAB language-delivery validation**, separately scoped
and authorized. This report does not start it, accept all of M3-B or Phase J, or create
a new uninterrupted PASS execution from historical BLOCKED and later PASS evidence.
