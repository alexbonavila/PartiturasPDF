# Phase J — M3-B2-B Android Manual System Locale Evidence

2026-10-22. Branch: `test/mvp-stabilization-j`.
Documentation checkout: `6b2294b715abbfd8787c0878caf8d95116fa4e35`.
Execution revisions: `fe79cf269e6efbe30bd98a030cc945eb010bc180` (API26) and
`6b2294b715abbfd8787c0878caf8d95116fa4e35` (API27/API36).

**Verification level: owner-reported results derived from retained local evidence.**
This report consolidates three separate owner-local execution records. Their private
raw directories are unavailable in this review checkout; Codex has not independently
reparsed their JUnit logs, rehashed their artifacts or revalidated their execution
metadata. The results below are attributed to the owner's supplied verification,
not a fresh independent artifact audit or new Android execution.

## Selected-scope summary

All PASS entries in this report have the owner-reported provenance above.

| Item | Result |
|---|---|
| API levels covered | 26, 27, 36 |
| Configurations per API | 4 |
| Total locale configurations | 12/12 PASS |
| Primary selected JUnit executions | 36/36 PASS |
| API26 baseline/restoration | PASS/PASS |
| API27 baseline/restoration | PASS/PASS |
| API36 baseline/restoration | PASS/PASS, after correction |
| Live locale transition | NOT RUN |

**There is no single overall PASS execution encompassing all three devices.**
The API26 evidence belongs to a globally **BLOCKED** run; its completed per-API
results remain valid according to the owner's verifier. The API27 and API36 records
are independent PASS executions scoped to their respective APIs. Consolidating
these results does not change any original report or create a new global run.

## Per-configuration results and language semantics

Each cell means configuration, visible localization and selected JUnit PASS as
reported by the owner's existing `report()` verification.

| Locale configuration / required priority | API26 | API27 | API36 |
|---|---|---|---|
| es-ES primary | PASS | PASS | PASS |
| ca-ES primary | PASS | PASS | PASS |
| en-US primary | PASS | PASS | PASS |
| fr-FR, en-US prefix | PASS | PASS | PASS |

These executions use `manual_policy=primary-prefix-v2`. Spanish, Catalan and English
cases require the specified first language. The French case requires explicitly
observed French first and English second, with Spanish application fallback.
Additional secondary languages are allowed after the required prefix. The coordinator
records the complete observed list and passes it to instrumentation; the table is
not a claim that the devices contained only the named languages. Complete observed
lists are unavailable here and are not reconstructed or guessed.

Restoration uses a different criterion: the complete final language order must equal
the complete original list recorded for that device in that run. The owner reports
that exact verification passed for each API. No particular initial list is invented
here, and earlier historical recovery lists are not substituted for these baselines.

## Execution provenance

| API / Android | Owner-local evidence directory | Execution revision | Original aggregate status | Recorded scope / API-specific outcome |
|---|---|---|---|---|
| 26 / 8.0 | `build/j-m3-b2b/22ffdb4262dc4a12a77bdcf6b570ac0f` | `fe79cf269e6efbe30bd98a030cc945eb010bc180` | **BLOCKED** | Global matrix interrupted later; API26 four configurations, baseline, exact restoration and 12/12 primary JUnit PASS |
| 27 / 8.1 | `build/j-m3-b2b/a328d39d16814b1497dba051d04e7408` | `6b2294b715abbfd8787c0878caf8d95116fa4e35` | **PASS** | Selected scope `[27]`; four configurations, baseline, exact restoration and 12/12 primary JUnit PASS |
| 36 / 16 | `build/j-m3-b2b/ee42080c88c44a76910f734558e7ef63` | `6b2294b715abbfd8787c0878caf8d95116fa4e35` | **PASS** | Selected scope `[36]`; four configurations, baseline, final exact restoration and 12/12 primary JUnit PASS |

Independent raw-evidence verification: **NOT PERFORMED for all three records**.
All three directories were checked and are absent from this checkout. Therefore the
existing reporter could not be invoked against them. No replacement evidence parser
was written. No artifact hashes, source digest, device serial/boot identities, APK
hashes, execution times or raw JUnit counts beyond the supplied results are fabricated.
No independent installed-binary attestation is claimed.

The owner supplied a local verification procedure that scans retained matrix.json
files with policy `primary-prefix-v2`, invokes the existing `report()` function and
selects the latest qualifying result per API by local file modification time. A
qualifying API requires all four configuration/localization/JUnit dimensions PASS,
baseline PASS and exact restoration PASS. This selection does not require the original
global aggregate to be PASS, which explains the API26 extraction. The procedure and
its printed outcomes are owner-supplied; Codex has not rerun them on private artifacts.
File modification time is a local selection rule, not a verified execution timestamp.

### Repository source comparison independently inspected

Both execution commits are present after fetching the existing feature branch.
`git diff --name-only fe79cf269e6efbe30bd98a030cc945eb010bc180 6b2294b715abbfd8787c0878caf8d95116fa4e35`
contains only:

- `docs/validation/J_M3_B2B_LOCALE_AUTOMATION.md`
- `scripts/j_m3_b2b_locale_matrix.py`
- `scripts/tests/test_j_m3_b2b_locale_matrix.py`

The entire `app` Git subtree is identical at both commits:
`d285b85c3ccee02a53eb81ae6b80872830161a85`.
Thus repository application code, Android tests and app build files are unchanged
between these revisions. Runner changes add API selection and bounded read-only ADB
identity recovery; associated host tests and instructions also change. This establishes
repository source equivalence for the app subtree, **not** identical built/installed
APKs, equivalent runtime environments, or independently verified execution-source digests.

## Executed selected JUnit inventory

The current coordinator identifies these existing methods:

1. `com.abdev.partituraspdf.app.integration.AppLocaleIntegrationTest.productionActivityUsesActualSystemPolicyAndRecreationRetainsHostAndLocalizedControls`
2. `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition`
3. `com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext`

There are three selected methods per configuration, twelve configurations and
**36 primary method executions**: twelve per API. These are repeated executions
of three existing methods, not thirty-six distinct test methods. This inventory is
verified in repository source; its execution attribution is owner-reported because
raw logs and JUnit XML are unavailable here.

Baseline and restoration instrumentation are additional **auxiliary** executions.
They are excluded from the 36 primary executions. A normal complete three-API
selection has one baseline and one restoration invocation per API; actual auxiliary
attempt counts, including any correction verification, require the retained records.
No exact total auxiliary count is inferred from the supplied summary.

## Functional coverage supported by the selected methods

The owner manually selected real Android system languages. The coordinator verifies
ADB locale channels before testing; instrumentation independently observes the complete
system list. The selected scope covers production MainActivity localization under
that configuration, asserted visible Library/Settings resources, Activity recreation
with retained host, and the registered Reader journey's localized controls.

The Reader method exercises real production UI/native isolated rendering with test-owned
local capabilities: registration/favorite state, visible rendered presentation, page
navigation, PAGE/WIDTH, Settings choices, Back/reopen and persisted registered position.
Its source/grant preservation assertions remain in place. Smoke establishes app identity.
The reported restoration verifies the originally recorded complete system-language order.

This is not a manual linguistic review of every string, error screen or application
screen. The test-owned Reader capability is not genuine external DocumentsUI/provider
authorization. Starting instrumentation after a locale change does not prove that an
already active Activity survived a live system-language transition.

## Interruption and correction history

- API26 completed all four configurations and exact restoration before the global
  matrix encountered an ADB infrastructure interruption during API27. The global
  record `22ffdb4262dc4a12a77bdcf6b570ac0f` remains **BLOCKED** permanently in this account.
- API27 was subsequently completed in the separate `[27]` run after the owner resolved
  emulator communication. Its original aggregate is PASS, not a resumed global PASS.
- API36 was completed in another separate `[36]` run. The first restoration check
  detected an extra French locale and returned BLOCKED. The owner removed that extra
  locale and repeated verification; the final restoration was PASS. The earlier
  BLOCKED check remains part of the owner-reported history and is not erased.

The retained details of these interruptions/correction attempts have not been inspected
here. Their precise commands, timing and auxiliary execution counts remain unverified.
Historical M2, M3-A, M3-B1 and M3-B2-A evidence and catalogue statuses remain unchanged.

## Outstanding acceptance boundaries

Outside this selected-scope PASS:

- **live_transition: NOT RUN** — no verified system-language switching of an already
  active Activity. Explicit recreation under the selected configuration is narrower.
- Genuine disconnected/offline guest startup and local document journeys.
- Device-specific AAB-derived language delivery and fully offline language availability;
  successful debug APK runs do not establish distribution acceptance.
- Complete manual linguistic review of all screens, especially contextual errors.
- Genuine external SAF/provider authorization and revocation.
- Physical Android 8.1 tablet and accessibility/TalkBack acceptance.
- Actual OS process death, worker loss and controlled PDFium diagnostics not covered here.
- Performance, memory and endurance acceptance; backup/restore/D2D where still pending.
- M3-C authentic coverage attribution, open M2 limitations and remaining Phase J milestones.

J08 remains **DEFERRED TO PHASE R**. M4–M7 are not completed by this report, and J14/J15
final consolidation/formal acceptance has not been performed.

## Closeout assessment and proposed next work

**M3-B2-B has complete owner-reported evidence for the selected manual locale
configuration, visible localization, selected JUnit, baseline and restoration dimensions
across API26/API27/API36.** It may be treated as completed for planning purposes,
subject to evidence provenance review and the owner's formal acceptance.

This does not formally accept all of M3-B or Phase J, and does not create a single
three-device overall PASS execution. Raw-artifact independent review remains unavailable.

Propose **M3-B2-C** next for genuine offline validation, with AAB language-delivery
acceptance included in a separately approved execution/evidence scope. Earlier planning
may label AAB work M3-B2-D; neither that work nor M3-C is promoted to PASS here. No next
milestone or device operation is started by this documentation closeout.
