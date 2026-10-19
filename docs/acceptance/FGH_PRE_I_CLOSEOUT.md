# Pre-Phase-I F/G/H closeout and acceptance register

Recorded **2026-10-19**. Owner-approved C01–C05 preparation; no Phase I or merge authorization.

## Revision identity and evidence boundaries

- Verified starting/current upstream main: **`adcca2bd07693e0d985eee1c611bc931be1dfed1`**.
  Fetch confirmed no drift from the supplied handoff. This includes F #18, G #19 and H #20.
- Working/delivery branch: **`chore/pre-i-fgh-closeout`**, created from that main; no rebase/merge.
- C01 documentation: `be02ba1` (`docs: reconcile merged F G H and PDFium status`).
- Resource/test/validator implementation: **`46c1b117dcee26902e675426755de551bbb68e01`**
  (`l10n: complete F G H resources and Spanish fallback`). This is the exact tested source tree;
  subsequent commits contain only reports/documentation and do not change application/test code.
- C03 coverage record: `d24efa9` (`test: verify scoped F G H coverage`).
- C04 investigation: `46a2067` (`docs: investigate isolated PDF worker coverage capture`).
- Final publication revision is the commit containing this record. Resolve its exact full Git ID
  with `git log -1 --format=%H -- docs/acceptance/FGH_PRE_I_CLOSEOUT.md` and compare with
  `git ls-remote origin refs/heads/chore/pre-i-fgh-closeout`. The delivery response/PR description
  records that full SHA; a document cannot literally embed its own content-addressed commit ID.
  Never substitute a subsequent upstream or unrelated scheduled revision.

Authority remains [AGENTS](../../AGENTS.md), [testing policy](../TESTING_POLICY.md),
[change control](../CHANGE_CONTROL.md), [DoD](../DEFINITION_OF_DONE.md), and
[actual frozen contracts](../planning/FGH_FINAL_CONTRACT_FREEZE.md). The explicit user request
supersedes the attachment's proposed AGENTS status edit and conditional worker implementation:
**AGENTS is untouched; worker work is investigation only**. No governance, gate, threshold,
dependency, SDK, fixture, production Kotlin, manifest, AIDL or persistence change is included.

## C01–C05 implementation and ownership

| ID | Delivered work | Verification / remaining boundary |
|---|---|---|
| C01 | README, status, roadmap reconcile merged F/G/H; chronological G/isolated renderer addenda; technical stack localization update | Source/Git review PASS; dated prior results retained |
| C02 | Full main es/ca/en and Spanish default; complete debug SAF UI; strict validator and locale regressions | Structural/format PASS, Android tests compiled; human linguistic and real resource resolution pending |
| C03 | Independently measured Library/Reader/Settings JVM logic and whole-package projections | Each logic scope meets 90/80; Android/Compose combined coverage NOT RUN |
| C04 | [Worker process coverage feasibility](PDF_WORKER_COVERAGE_FEASIBILITY.md) | Instrumented bytecode confirmed; authentic worker export BLOCKED, design requires separate approval |
| C05 | This register, [coverage reproduction/inventory](FGH_COVERAGE.md), [translation inventory](FGH_TRANSLATION_INVENTORY.md) | Delivery for owner review, not formal phase acceptance |

F supplies browsing, explicit registration, favorites and registered/transient open requests.
G supplies bounded one-page read-only rendering/presentation, navigation, fit, visual-only zoom,
frame/lifecycle barriers and presented-page checkpoint scheduling. The native engine now runs
through a separate isolated service on the production path. H supplies theme/fit commands,
authoritative observation, explicit retry and uncertain-write reconciliation. `MainActivity`
still displays Greeting. No real picker → library → reader → settings journey exists yet.
Global search, file mutations, PDF editing, annotations, Drive and adjacent prefetch are not shipped.

## Current local execution

Linux x86_64; JDK 25.0.4.1, Gradle 9.8.0, AGP 9.4.1, JaCoCo 0.8.14, Python 3.12.14,
compile SDK37/build-tools37.0.0; minSdk remains26. Existing environment activation only;
no tool/dependency/build configuration was changed.

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease \
 :app:lintDebug :app:testDebugUnitTest --rerun --max-workers=4 --console=plain
python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest \
 --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 scripts/local_e2e.py doctor
git diff --check
```

| Check | Actual result |
|---|---|
| Debug/release APK, including R8 | PASS |
| Android instrumentation APK | PASS compilation, not execution |
| JVM JUnit XML | **423 executed, 0 failures/errors/skips**; Library76, Reader89, Settings44 (41 controller +3 locale-policy) |
| Python infrastructure | **289 PASS**; eight new complete-localization tests; historical staged/negative tests preserved |
| Lint | **0 errors, 24 warnings**. Three additional report entries arise from explicit English debug resource copy; PluralsCandidate suggestions retained without suppression/type-contract changes |
| Localization | PASS complete main147 + debug20 keys per locale, Spanish-default equality, strict types/formats |
| Canonical fixtures | **15 PASS**, original hashes/manifest unchanged; expected corruption remains expected |
| Packaging | Debug/release contain0 PDF fixtures; test APK contains15 canonical PDFs |
| New locale JUnit signatures | javap confirms four public void Android test methods |
| Device prerequisites | doctor exits2/BLOCKED: acceleration unavailable, dedicated AVD UNPROVISIONED; adb devices empty, no /dev/kvm |
| API26/27/36 here | **NOT RUN**; no device created, adopted, wiped, reset or installed |

Logs: `/tmp/pre-i-local-build-final.log`, `/tmp/pre-i-python-final.log`,
`/tmp/pre-i-coverage-final.log`, `/tmp/pre-i-fixtures.log`, `/tmp/pre-i-doctor.log`.
JUnit: `app/build/test-results/testDebugUnitTest`; Lint: `app/build/reports/lint-results-debug.xml`.
An intermediate Lint run failed on20 missing English debug strings after adding main values-en.
That genuine migration gap was fixed by completing the debug source set, then rerunning all affected
build/Lint checks successfully; no suppression or gate bypass. The existing greeting test's French
expectation was updated from English to Spanish and renamed accordingly, retaining all locale/theme
assertions. There was no demonstrated F/G/H business-logic defect or production correction.

## Translation and resource resolution

[Exact ID/file-hash inventory](FGH_TRANSLATION_INVENTORY.md): main3 starter +72F +48G +24H =147;
plus20 preexisting debug SAF labels. All four locale bundles have identical named-resource sets.
Default/es copy is identical by value; original English starter/debug copy is now explicit in en.
Feature English/Catalan translations preserve pending versus confirmed writes, temporary versus
registered identity, read permission loss, retained metadata, visual-only zoom and no-source-change
claims. IDs, `%1$s`, `%1$d`, `%2$d`, `%2$s`, `%%` and caller contracts remain intact.
No manual selector, legacy language activation, runtime Activity locale handling or new preference.

The CLI now requires complete es/ca/en and Spanish fallback for main and debug, and rejects residual
staging. Historical staging support survives only as separately tested library behavior; it cannot
exempt the repository's complete-delivery CLI. Existing malformed XML/format/type, duplicate,
alias, empty, plural and array validations remain in place. Eight complete-delivery tests include
missing en, mismatched formats, default mismatch, duplicates and residual staging rejection.

Actual `aapt2 dump resources` on the debug APK confirms Spanish default plus ca/en/es values for
starter, Library, Reader and Settings (dump retained at `/tmp/pre-i-compiled-resources.txt`).
This is compiled-resource evidence, not Android's runtime selection behavior.

`settings.DeliveredLocalePolicyTest` (JVM, three PASS) exercises delivered regional en/ca/es,
unsupported-primary/empty language fallback to es, and unchanged undelivered policy defaults.
`settings.LocalizedResourcesTest` (four compiled, **NOT RUN**) tests es-ES, ca-ES, en-US, fr-FR in
separate configuration contexts; verifies starter/F/G/H actual strings and formatted page counts;
does not mutate device-global locale. `FoundationInstrumentedTest` retains both-theme greeting
and accessibility checks with Spanish unsupported-language fallback. These do not install Phase I.
Independent human/native-speaker language review and actual screen clipping/TalkBack remain pending.

## F/G/H coverage

Complete scope, source hashes, source-level counters, exact compiled logic class inventories,
whole-package projections and external script: [FGH_COVERAGE.md](FGH_COVERAGE.md).
Actual original bytecode/JVM exec inputs were analyzed by existing JaCoCo0.8.14. No Android `.ec`
is available here. Each logic verification is independent; no project average hides a feature gap.

| Feature / source scope | LINE | BRANCH | Result |
|---|---|---|---|
| F Library nonvisual logic | **451/451 —100%** | **410/466 —87.98%** | PASS JVM logic thresholds |
| G Reader nonvisual logic, including frame/presenter/interaction | **478/486 —98.35%** | **510/590 —86.44%** | PASS JVM logic thresholds |
| H Settings nonvisual logic | **167/169 —98.82%** | **158/170 —92.94%** | PASS JVM logic thresholds |
| All Library sources, JVM only | 451/640 —70.47% | 410/720 —56.94% | Not combined/UI acceptance |
| All Reader sources, JVM only | 478/725 —65.93% | 510/900 —56.67% | Not combined/UI acceptance |
| All Settings sources, JVM only | 167/340 —49.12% | 158/243 —65.02% | Not combined/UI acceptance |
| Isolated worker PID | NOT MEASURED | NOT MEASURED | BLOCKED, not zero or passing coverage |

Only Android presentation/Compose-generated and preview tooling are outside the JVM logic gate;
they remain visible in All. No difficult business logic was dropped. No new coverage-padding tests
were necessary. Existing local runner F/G/H informational scopes are unchanged. Full UI/combined
feature coverage requires genuine Android execution; no new permanent hosted feature gate is claimed.

## Feature-to-test mapping

Names below are exact existing method IDs (package prefix `com.abdev.partituraspdf`). JVM PASS applies
to the tested source revision above; all Android entries are compiled/source-reviewed, NOT RUN here.

| Feature obligation | Actual JVM method | Android evidence still required |
|---|---|---|
| F root restoration/replacement | library.LibrarySelectionUiRegressionTest.failedReplacementSelectionRestoresExistingContentWithoutClaimingAuthorization | LibraryScreenTest,29 standard tests |
| F stale/provider concurrency | library.LibraryConcurrencyTest.noncooperativeOldRefreshCannotOverwriteNewRows | Existing SAF/foundation provider tests and real host grant delivery |
| F registration/error distinction | library.LibraryRegistrationTest.notRegisteredIsDistinctFromEveryOperationalLookupFailureAndRetryIsTargeted | Existing Room registry/integration and F UI tests |
| F favorite uncertainty | library.LibraryDocumentLifecycleTest.canceledFavoriteWithFailedReadExposesUncertaintyNotGuessedFavorite | Existing metadata/DataStore and F UI tests |
| G bounded plan | reader.ReaderRenderPlanTest.insufficientBudgetAndUnrepresentableAspectFailRatherThanInventPixels | ReaderNativeIntegrationTest and actual surfaces/native service |
| G failure/cancellation | reader.ReaderSchedulingTest.expectedRenderFailureIsRetryableAndCancellationIsDistinct | ReaderScreenTest/native integration,40 G standard source tests in total |
| G graphics ownership | reader.ReaderPresenterTest.sourceLeaseCannotCloseDuringSynchronousConsumptionEvenIfRetirementBegins | Actual Surface/PixelCopy and detach/worker execution |
| G checkpoint | reader.ReaderPresentationTest.typedAcknowledgementFailureIsObservableAndExplicitRetryDoesNotRerender | Genuine position persistence/host background lifecycle |
| H read/selection authority | settings.SettingsControllerTest.themeAcknowledgementWaitsForObservationWithoutRestartingCollector | SettingsScreenTest,24 standard cases |
| H uncertain writes | settings.SettingsControllerTest.failedReadDuringUncertainRecoveryRetainsTargetUntilExplicitRetry | Existing DataStore integration plus H Compose |
| Locale policy | settings.DeliveredLocalePolicyTest.unsupportedPrimaryLanguageFallsBackToSpanishRatherThanSecondaryEnglish | LocalizedResourcesTest,4 new standard cases |

Total Android source inventory: **375 unique =361 standard +14 assigned diagnostic tests**;
**29 controlled diagnostic invocations** and24 standard native-isolation tests preserved.
Settings package has28 standard cases (24 screen +4 locale); F29; G40. These are source inventories,
not executed Android counts. No existing acceptance test was disabled or diagnostic reclassified.

## Isolated PDF worker finding and approval boundary

[Full C04 investigation/design](PDF_WORKER_COVERAGE_FEASIBILITY.md) distinguishes every handwritten
client/worker/shared-wire source. Actual AGP debug-transformed service bytecode contains `$jacocoInit`.
The application runner dumps only its process; the isolated UID's worker probes have no current
flush/export capability, and self-kill/native crash can lose data. No authentic worker coverage
or crash-path percentage is available. Existing pure JVM, direct native or client coverage cannot
certify worker execution. No process was kept alive or reused to manufacture coverage.

Recommended next task: separately approve a debug/test-only bounded PFD/pipe snapshot bridge, exact
PID/UID/start/epoch/run/APK/class/source provenance, API26 runtime validation, fail-closed missing/
wrong/stale/truncated/crash data tests and independent API27/36 proof. Retain nonexported isolated
UID, emergency retirement and raw diagnostic outcomes. Any AIDL/worker/Gradle/CI changes require
new owner approval; none are implemented here.

## Hosted CI and historical baseline

Owner-provided handoff attributes post-merge [run39251879166](https://github.com/alexbonavila/PartiturasPDF/actions/runs/39251879166)
to exact main `adcca2bd07693e0d985eee1c611bc931be1dfed1`: seven mandatory jobs plus API27/API36
PASS,420 JVM and357 ordinary Android cases on each API, plus separately validated controlled
PDFium diagnostics. This is **attributed historical evidence**, not new artifact inspection.
The closeout adds3 JVM and4 Android locale tests; that baseline does not certify this changed branch.

Current API read attempt returned:
`Get "https://api.github.com/repos/alexbonavila/PartiturasPDF/actions/runs/39251879166": Forbidden`.
No raw new hosted JUnit/artifacts were obtainable; no current native diagnostic classification is
claimed. Known raw diagnostic failures must remain failed raw JUnit under the existing validated
policy, not flattened into a statement that every native invocation passed.

| Required job on final closeout revision | Current evidence |
|---|---|
| build | Local PASS; hosted NOT VERIFIED/API BLOCKED |
| lint | Local PASS; hosted NOT VERIFIED/API BLOCKED |
| unit-tests | Local423 PASS; hosted NOT VERIFIED/API BLOCKED |
| instrumented-api26 | NOT RUN here; hosted NOT VERIFIED/API BLOCKED |
| localization | Local PASS; hosted NOT VERIFIED/API BLOCKED |
| pdf-fixtures | Local15 PASS; hosted NOT VERIFIED/API BLOCKED |
| quality-gate | NOT VERIFIED/API BLOCKED |
| compatibility-api27 / compatibility-api36 | NOT RUN here; dispatch/evidence requires API access or owner execution |

Publication/PR attempts and exact final remote SHA are recorded in the delivery response. If API
creation is forbidden, no PR is claimed. Owner action: open a Draft from
`chore/pre-i-fgh-closeout` targeting main, then run existing workflow:

```bash
gh workflow run ci.yml --repo alexbonavila/PartiturasPDF \
 --ref chore/pre-i-fgh-closeout -f compatibility=true
```

Inspect actual head SHA, seven mandatory conclusions and API27/36 independently; download original
JUnit/artifacts, verify361 standard tests including4 locale cases, no missing/unexpected/duplicate/
failed/errored/skipped standard cases, and review all29 controlled diagnostic invocations with
approved classification. Do not equate compiled APKs or successful dispatch with executed PASS.

## Distinct completion decisions

| Level | Current status |
|---|---|
| F/G/H components implemented/merged | YES, baseline #18/#19/#20; no navigable app claim |
| Baseline automated CI | Owner-attributed PASS on adcca2bd; not new closeout revision evidence |
| Closeout local code/resource checks | PASS as detailed; Android/combined coverage remains unavailable |
| Technical handoff for I | Documentation, bundles and JVM logic evidence supplied; owner decides whether open runtime/worker evidence blocks starting I |
| Formally accepted by owner under DoD | NOT ESTABLISHED here; no merge or phase-acceptance authority exercised |
| Full application acceptance | NOT COMPLETE; requires I and applicable manual/physical acceptance |

## Deferred acceptance / owner decisions — 2026-10-19

| ID | Outstanding item | Status and resumption condition |
|---|---|---|
| FGH-D01 | Physical Android8.1/API27 tablet | DEFERRED BY OWNER / NOT RUN; selected physical hardware and separately approved safe scenarios |
| FGH-D02 | C06 real backup/uninstall/reinstall/restore | DEFERRED / NOT RUN; deliberately disposable installation, explicit destructive consent, real transport/evidence |
| FGH-D03 | Genuine D2D and other OEM/cross-platform transfer | DEFERRED / NOT RUN; mechanism-specific supported source/destination, not cloud-transport substitution |
| FGH-D04 | External SAF providers/grant revocation/restarts | DEFERRED / NOT RUN beyond existing controlled evidence; approved provider matrix and authentic READ grants |
| FGH-D05 | Manual visuals, TalkBack, keyboard/D-pad, large text, tablet rotation/contrast/clipping | NOT RUN; translated screenshots/real UI after I, not source/preview compilation certification |
| FGH-D06 | Independent Spanish/Catalan/English linguistic review | PENDING human/native-speaker review of all current copy and error semantics |
| FGH-D07 | Two-process low-RAM memory/performance/latency | DEFERRED until functional navigable Phase I; **no benchmarks/profiling/PSS/RSS attempted or claimed** |
| FGH-D08 | Full-product UX fit | Owner decision pending: spec25% edges +center-toggle versus G15% bands/min48dp; zero adjacent prefetch under current conservative budget; no behavioral change here |
| FGH-D09 | Isolated worker process coverage | BLOCKED; specific collection implementation requires separate approval |
| FGH-D10 | Latest closeout API26/27/36, Compose and combined coverage | NOT RUN/BLOCKED here; owner/hosted exact-revision execution and retained results |
| FGH-D11 | Distribution license/security review | DEFERRED to release work; restrictive root license and test-only tool licensing unchanged |

[P05 deferred acceptance](../planning/PRE_FGH_DEFERRED_ACCEPTANCE.md),
[Phase C register](../PHASE_C_DEFERRED_ACCEPTANCE.md) and
[C06 guarded procedure](../C06_BACKUP_RESTORE_ACCEPTANCE.md) remain authoritative detailed records.
Deferral is not a waiver; no backup procedure, grant change or physical-device operation ran here.

## Exact Phase-I integration responsibilities (not implemented)

1. Replace starter Activity host through a separately approved task. Retain one application graph
   from AppGraphFactory; reuse FoundationDependencies, Room and DataStore; do not initialize them
   in the isolated service or create feature-local stores.
2. Launch the real SAF tree picker, pass actual Activity Result flags to existing mediation, persist
   READ only and distinguish cancellation/denial/revocation. Never fabricate grants or callbacks.
3. Retain LibraryController with the **same** FolderNavigation/FeatureHost instances across eligible
   recreation, forward selection completion once, route typed registered/transient requests to G.
4. Retain ReaderRoute/ReaderController and monotonic opening intents; do not duplicate opens. Restore
   registered logical IDs only; transient capabilities must be reacquired after process death.
5. Honor G quiesce/detach/retire barriers before route background/close. Inspect checkpoint flush/close
   outcomes; a displayed-page scheduling acknowledgement is not durable storage confirmation. Own
   background/trim shutdown, fresh heap policy inputs, screen-on/immersive flags and safe resume.
6. Inject existing preferences/SettingsCommands into a retained SettingsController; start, collect
   StateFlow and dispatch typed actions with host lifecycle; terminal close at ownership end.
   Apply observed theme at app root and PageFit through the approved G boundary. No H→G imports.
7. Apply automatic primary device locale through existing DeviceLocalePolicy with explicit complete
   English/Catalan delivery flags and Spanish fallback on API26+. Legacy saved language is inactive;
   no manual selector. Verify actual Activity recreation and system-language changes separately.
8. Execute real picker→F→G→H→back/recreation/restart flows, translated screenshots, native service
   recovery and actual API26/27/36 acceptance. Resource-context tests do not prove those journeys.
9. Revisit hardware performance only after the navigable application exists, under separately approved
   measurement scope; do not reinterpret current static budgets as measured low-RAM acceptance.

**Decision:** implementation preparation and local evidence delivered for owner review. Hosted,
Android/worker and listed manual requirements remain open; no unconditional formal acceptance,
merge readiness, Phase-I implementation or authorization to start it is claimed.
