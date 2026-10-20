# Prepared Phase I pull request — local draft only

Proposed title: **feat(app): integrate Library Reader and Settings MVP (Phase I)**

Target: `main`; source: `feat/app-mvp-integration-i`. The audited baseline branch was published at
`f11ca0c6e3324ea6db00b792333fe7217152026b`. **The language correction is local-only and NOT PUSHED.**
No PR was created; correction publication/PR creation require separate authorization. No auto-merge.

## Problem and resulting application behavior

MainActivity previously displayed the starter greeting while Library, Reader and Settings existed
as independent components. Phase I connects them through one retained application host: choose a
real Android SAF folder, browse bounded subfolders, explicitly register/favorite a score, open a
registered or temporary PDF through the existing isolated worker, return safely and change observed
theme/default fit. Primary es/ca/en is automatic, unsupported primary falls back to Spanish; no
manual selector. Eligible recreation retains owners; saved-state reconstruction revalidates only
registered logical identity and actual committed positions, never transient grants/native objects.

## Implementation and architecture

- I01–I02: retained AppHostViewModel/ProductionHostSession; one AppGraph/Room owner, borrowed
  application DataStore and identical F/Foundation folder navigator; revision-guarded navigation.
- I03–I04: real AndroidX Activity Result binding; original URI/flags/code; launch acknowledgment
  separate from authorization; single READ/root-persistence reconciliation, explicit F registration
  and Room favorites. No grant fabricated or pre-existing grant released on replacement.
- I05: current F row/registered restore admission, monotonic Reader intent, G-only route open;
  canonical rendering through production isolated engine. No new renderer, cache or copied feature.
- I06–I07: ordered graphical/native/checkpoint barriers on background/memory/Back; no main-thread
  blocking/forced recycle. Bounded eligible saved navigation, registered re-resolve/clamped durable
  SharedPreferences position; transient process reconstruction returns Library.
- I08–I10: H authoritative observation drives theme/default fit without duplicate DataStore
  subscription; Reader-local fit remains local until a genuine changed default. Activity locale
  context before resource reads, recreation guards and visible-Reader-only keep-screen-on/insets.
- I11: uncertain root writes reconcile rather than replay; stale/closed picker results/cancellation
  remain safe; failed Reader close verifies route ownership after G retirement, blocks unsafe reuse
  and keeps Library/Settings usable; original and secondary diagnostics retained.
- I12: final source/security/artifact audit, fresh local tests/coverage, I-T01–I-T17 register,
  Lint/AAB investigation, manual scripts and truthful status updates. Documentation-only closeout;
  no further functionality or protected-component corrections.

The sole approved Gradle change is `android.bundle.language.enableSplit=false`, retaining all
supported translations in every base APK. No public API, F/G/H internals, shared persistence/schema,
worker/AIDL/isolation, dependencies, minimum SDK, other packaging/signing/optimization setting, CI,
quality threshold, policy or immutable fixture change. MainActivity and
app host/navigation/locale packages are the integration owners. The sole shared infrastructure
change is the previously separately owner-approved localization inventory count147→155; all parity,
format/negative/control assertions remain. Foundation launcher test now asserts Library rather
than the removed greeting; other theme/locale assertions remain enabled.

## Requirement mapping and evidence

[I_APP_MVP_ACCEPTANCE.md](../acceptance/I_APP_MVP_ACCEPTANCE.md) preserves the exact original
I-T01–I-T17 descriptions, source symbols, JVM/Android evidence, local/hosted status, manual needs
and limits. [I_APP_MVP_HANDOFF.md](I_APP_MVP_HANDOFF.md) retains dated incremental results and I11's
R01–R40 recovery matrix. Final tested source: `f51cc42df996390cf7e6aacf6cff85e004c9ca17`;
I12 changes documentation only. Final review SHA must be taken from Git, not assumed from this text:
`git log -1 --format=%H -- docs/acceptance/I_APP_MVP_ACCEPTANCE.md`.

Latest fetched main/merge base: `cd86db5c4f70201cfbe6970de20cdb3308a970d0`; no drift at I12 review.
Recheck before eventual publication/merge; do not silently rebase/merge if it changes.

## Fresh local results (Linux x86_64 / JDK25 / SDK37)

| Check | Actual result |
|---|---|
| Debug, release/R8 and Android-test APK compilation | PASS; test compilation is not execution |
| JVM + original JUnit XML / contract gate |620 PASS,0 failures/errors/skips;197 app host/navigation methods |
| Python infrastructure |289 PASS, localization negatives included |
| Existing host orchestrator |PASS all8 groups, fresh620 JVM +289 Python, JSON/JUnit/HTML retained |
| Localization |PASS155 main+20 debug IDs in each default/es/ca/en; primary policy JVM PASS |
| Immutable fixture verifier |15 PASS, original bytes/manifest unchanged |
| Production packaging |0 PDFs in debug/release APKs and inspected debug AAB;15 exact canonical hashes in test APK;421 source IDs in test DEX; opt-in SAF class absent |
| Lint |0 errors,25 warnings after the approved language correction; AppBundleLocaleChanges resolved without suppression |
| Each JVM logic source |>=90% LINE/80% BRANCH; aggregate600/603 lines99.50%,689/786 branches87.66% |
| Android/API26/27/36/native/surface/real grants/process kill |NOT RUN; no connected device/KVM, dedicated AVD UNPROVISIONED |
| Hosted seven jobs and compatibility |NOT RUN; API access BLOCKED proxy403, no final-revision run/artifact |
| Physical Android8.1 / linguistic / TalkBack |NOT RUN |

The final register provides exact commands and file-specific coverage. Android factory/session,
Compose UI, Activity/context/Window and native worker coverage is unmeasured; unfiltered JVM
misses are disclosed, not excluded as proven coverage. API counts are inventory, not PASS counts.

## Lint, AAB and remaining blockers

The owner approved `android.bundle.language.enableSplit=false` after I12. Fresh release/debug
AAB metadata explicitly negates LANGUAGE splitting; removing that one entry leaves the complete
parsed baseline BundleConfig unchanged. All8 API26 initial en/ca/es/fr selections retain all
supported es/default,es,ca,en copy in the base, requiring no extra language APK. ABI/density
inventories remain unchanged (debug mdpi retained; optimized release had no density split in either
build). This includes bundled dependency locales; no resource pruning or Kotlin locale change.

The old baseline base-resource regression fails; the same check passes all corrected bases for
155 main strings/debug and154/release (unused banner already shrunk in baseline). Production APKs/
AABs contain0 PDFs; test APK retains15 exact canonical hashes. Fresh builds,620 JVM,289 Python,
localization and fixtures PASS. Lint is now0 errors/25 warnings; only AppBundleLocaleChanges disappears.
The obsolete banner and all other warnings remain unchanged, not suppressed or deleted.

Exact artifact/size/hash/reproduction and owner authorization evidence is in the
[acceptance correction addendum](../acceptance/I_APP_MVP_ACCEPTANCE.md#owner-approved-offline-language-delivery-correction--2026-10-20).
Initial pre-commit release AAB3,438,057 bytes (+4); final implementation-commit rebuild3,438,058
(+5) because AGP updates embedded version-control metadata; debug14,984,186 (−4) is unchanged.
Base APK payload grows by including all bundled translations; AAB-byte deltas are not device
install/download/performance claims. Exact final correction SHA:
`git log -1 --format=%H -- app/build.gradle.kts`; validated Gradle blob `8fc68429518a66b131217466bf05f05762123965`.

**Real-device offline switching remains NOT RUN**, as do final API26/27/36, physical Android8.1
and hosted checks. No KVM/authorized connected device. English-install→offline Catalan→Spanish→
English and unsupported-language Spanish fallback must be observed on a verified disposable target;
artifact selection is not execution. Packaging approval is resolved, runtime I09/Phase I acceptance
is not. Correction publication needs separate owner authorization; no PR or merge occurred.

Mandatory current-head/current-base `build`, `lint`, `unit-tests`, `instrumented-api26`, `localization`,
`pdf-fixtures`, `quality-gate`, independent API27/36, real SAF→F→G surface/checkpoint/worker recovery,
eligible process death and device locale/window behavior must execute. Inspect original XML,407
standard cases,14 assigned diagnostics,29 controlled invocations and24 standard isolated tests;
do not recast classified diagnostic outcomes as standard PASS or accept skips/missing inventory.

Manual evidence remains required: physical Android8.1, portrait/landscape, theme, larger fonts,
keyboard, TalkBack, human translations and external providers. The20-action safe manual table and
process-death/grant-revocation procedures are in the acceptance register. No user data reset,
destructive backup or unknown emulator operation is authorized. Existing C06 backup/D2D and
physical/performance acceptance remain separately open; no benchmark performed or budget certified.

Known product limits: read-only PDF workflow, no editing/annotations/Drive/global search/file
mutations/prefetch; visual zoom is not native tiled rendering. Existing15%-minimum48dp edge bands
versus specification's25%/center-toggle proposal is a retained owner UX decision, not changed here.
No performance, comprehensive security, Play delivery or physical tablet acceptance is claimed.

Recommendation: **Ready for owner publication/review, acceptance pending.** Not merge-ready or
formally accepted. Keep future PR draft while mandatory runtime/manual/CI evidence remains open.
Only the human owner decides publication, acceptance and merge. Do not begin another phase.

## Phase I commits before I12 closeout

```text
610f4b1 feat(app): add retained host ownership and navigation (I01-I02)
17a6c4e feat(app): connect Library and Settings launcher shell
b4963e7 feat(app): integrate retained SAF picker and Library (I03-I04)
86ee91c docs(app): record SAF integration evidence and I05 handoff
f6e9e18 feat(app): connect Reader opening and safe retirement (I05)
8a21cb5 docs(app): record I05 Reader validation and lifecycle handoff
9ed9c2f feat(app): coordinate Reader lifecycle and memory policy (I06)
a33c937 docs(app): record I06 lifecycle evidence and restoration boundaries
66be7ed feat(app): restore eligible navigation and registered Reader state (I07)
991084f docs(app): record I07 restoration evidence and runtime settings handoff
8634d96 feat(app): apply confirmed settings locale and Reader window effects (I08-I10)
3883f34 docs(app): record I08-I10 presentation evidence and I11 handoff
d3dd071 fix(app): harden root reconciliation and Reader cleanup recovery (I11)
f51cc42 docs(app): record I11 recovery matrix and final-validation handoff
```

The subsequent local I12 documentation commit is identified by the Git command above. No previous
commit was rewritten. Exact complete changed-file inventory against main (including this closeout):

```text
README.md
app/build.gradle.kts
app/src/androidTest/java/com/abdev/partituraspdf/FoundationInstrumentedTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/app/integration/AppHostActivityTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/app/integration/AppLocaleIntegrationTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/app/integration/AppStartupScreenTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/app/integration/LibraryPersistenceIntegrationTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/app/integration/ReaderHostNativeIntegrationTest.kt
app/src/androidTest/java/com/abdev/partituraspdf/app/integration/RootPickerBindingTest.kt
app/src/main/java/com/abdev/partituraspdf/MainActivity.kt
app/src/main/java/com/abdev/partituraspdf/app/host/AndroidReaderLifecycle.kt
app/src/main/java/com/abdev/partituraspdf/app/host/AppHost.kt
app/src/main/java/com/abdev/partituraspdf/app/host/AppHostFactory.kt
app/src/main/java/com/abdev/partituraspdf/app/host/AppHostState.kt
app/src/main/java/com/abdev/partituraspdf/app/host/AppHostViewModel.kt
app/src/main/java/com/abdev/partituraspdf/app/host/AppPresentation.kt
app/src/main/java/com/abdev/partituraspdf/app/host/AppPresentationState.kt
app/src/main/java/com/abdev/partituraspdf/app/host/ReaderHostLifetime.kt
app/src/main/java/com/abdev/partituraspdf/app/host/RootPickerBinding.kt
app/src/main/java/com/abdev/partituraspdf/app/host/RootPickerCoordinator.kt
app/src/main/java/com/abdev/partituraspdf/app/localization/AppLocaleContext.kt
app/src/main/java/com/abdev/partituraspdf/app/navigation/AppNavigation.kt
app/src/main/java/com/abdev/partituraspdf/app/navigation/AppRestoration.kt
app/src/main/res/values-ca/app.xml
app/src/main/res/values-en/app.xml
app/src/main/res/values-es/app.xml
app/src/main/res/values/app.xml
app/src/test/java/com/abdev/partituraspdf/app/host/AppHostViewModelTest.kt
app/src/test/java/com/abdev/partituraspdf/app/host/AppPresentationIntegrationTest.kt
app/src/test/java/com/abdev/partituraspdf/app/host/AppRestorationIntegrationTest.kt
app/src/test/java/com/abdev/partituraspdf/app/host/HostLibraryIntegrationTest.kt
app/src/test/java/com/abdev/partituraspdf/app/host/ReaderHostIntegrationTest.kt
app/src/test/java/com/abdev/partituraspdf/app/host/ReaderHostLifetimeTest.kt
app/src/test/java/com/abdev/partituraspdf/app/host/ReaderLifecycleIntegrationTest.kt
app/src/test/java/com/abdev/partituraspdf/app/host/RootPickerCoordinatorTest.kt
app/src/test/java/com/abdev/partituraspdf/app/navigation/AppNavigationTest.kt
app/src/test/java/com/abdev/partituraspdf/app/navigation/AppRestorationTest.kt
app/src/test/java/com/abdev/partituraspdf/app/navigation/ReaderNavigationTest.kt
docs/PROJECT_STATUS.md
docs/ROADMAP.md
docs/acceptance/I_APP_MVP_ACCEPTANCE.md
docs/integration/I_APP_MVP_HANDOFF.md
docs/integration/I_APP_MVP_PR_DESCRIPTION.md
scripts/tests/test_complete_localization.py
```


## Local packaging correction after I12

The owner-approved correction commit contains only app/build.gradle.kts and the three Phase I
acceptance/handoff/PR-description files. Source locale policy/translation/Kotlin and repository
infrastructure/tests stay unchanged. Historical I12 findings remain in the acceptance record;
this updated description presents current packaging and unresolved runtime acceptance accurately.
No next phase, push, PR, merge or policy/threshold change is authorized/performed by this correction.


Tested implementation commit: `7381ac5fc8dc618c4f0ab40ee8ed6deb3679bd88`. Final-commit builds
and620 JVM tests PASS. The rebuilt release hash is
`8e8698d42a431d0620ced0a96f489506f24f19c8b59882e4dcc74f6d650c7f58`; same configuration and all4
release delivery selections reverified using the same existing debug key. The follow-up commit
records provenance only, preserving original pre-commit measurements and the exact tested source.
Actual Android switching/compatibility/physical/CI acceptance remains NOT RUN and correction is
not pushed. See the acceptance record for the one-byte VCS metadata explanation and evidence.
