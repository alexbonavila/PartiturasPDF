# Phase I application integration — I03/I04 review handoff

> **Current I12 audit — 2026-10-20:** I01–I11 are implemented locally. The dated sections below
> retain their original incremental boundaries. Use [Phase I acceptance](../acceptance/I_APP_MVP_ACCEPTANCE.md)
> for the final I-T01–I-T17 register, fresh local execution, source-specific coverage, Lint/AAB
> findings, manual procedure and outstanding owner decisions. Implementation is not acceptance.


Recorded 2026-10-19. This is an incremental implementation report, **not Phase I acceptance**.
Prompt 3 starts at `17a6c4eee5d03cccbe29815021b6d250bcbbe9de`, on
`feat/app-mvp-integration-i`, with a clean checkout. That revision and both Prompt 2
commits were present. Implementation revision: `b4963e71b94943733e25b5f459751c1aff32e63e`.
The subsequent documentation-only commit records this report; obtain its exact delivery
revision with `git log -1 --format=%H -- docs/integration/I_APP_MVP_HANDOFF.md`.
Neither this task nor Prompt 2 publishes the branch, creates a PR or merges anything.

## Implemented journey and ownership

The retained host still owns one graph/session, one Library controller, one Settings
controller and the same FolderNavigation passed to FoundationHost and Library. No graph,
controller or navigator is recreated by recomposition, listing refresh or Settings navigation.
The existing startup, stale route revision guards and quiescent graph shutdown remain intact.

Library ChooseRoot now suspends through RootPickerCoordinator until an Activity has actually
launched the picker or reported an expected launch error. This is **only acknowledgment**.
MainActivity uses the actual ActivityResultRegistry and StartActivityForResult contract,
launching the existing SafTreeGrantAccess.treePickerIntent(). No Compose effect launches it.
The callback forwards the real result code, URI representation and original intent flags;
missing data remains null/zero. Non-OK results are cancellation, without interpreting any data.

A UUID request token owns each registry key (`app.saf.root.<token>`). The token remains stable
for that request across Activity recreation and is never reused for another request. An
atomic owner-dispatcher claim precedes launch. A recreated Activity registers the existing
key but cannot launch it again. Registry results arriving before reconnection are buffered
by AndroidX; reconciliation waits for launch acknowledgment. A completing flag rejects
concurrent duplicates; token comparison rejects old/unrelated callbacks. Completion goes
once to the retained Library actor after navigator synchronization. Failed launch completes
only the launch acknowledgment, letting Library's existing failure handling run once.

Activity bindings exist only while STARTED. Cancellation unregisters their callbacks; Activity
destruction cancels its lifecycle scope. The coordinator contains no Activity, launcher, View,
Intent or retained Activity callback. Session callbacks reference only retained feature owners.
Terminal host disposal closes the coordinator immediately, releases a waiting acknowledgment,
rejects late results/commands, then joins children before closing the graph as in Prompt 2.
Unexpected provider cancellation reaches the host's existing terminal handler as the original
CancellationException and is rethrown; it is never a fake authorization/cancellation result.
Programming defects propagate unchanged through the existing owner exception handler.

Saved Activity state contains only the pending token and host lifetime identifier. Ordinary
recreation retains the same owner. If the process died, the new owner cannot authenticate the
old consumer: it exposes an incomplete-operation recovery state and does **not** register the
old key, infer permission, deliver an unrelated result or automatically reopen the picker.
Recovery reads the authoritative selected-root repository, or lets the user explicitly select
again. No transient document/process-death Reader restoration is implemented here.

## Authorization and consistency

The only successful selection sequence is:

1. foundation.rootPicker.acceptResult(actual URI, original flags), once.
2. folders.select(accepted root), once, using the existing shared navigator.
3. LibraryController.onRootSelectionFinished(Success(root)), once.

SafRootSelection already persists the selected root. This integration never additionally calls
FoundationHost.acceptPersistedRoot. The existing grant adapter validates the tree, persists
READ only even if Android offered WRITE, and verifies the OS grant list. No broad permission,
URI-grant injection, PDF copy, automatic registration, metadata deletion or grant release is added.
A stored URI is never proof of a current grant. Restoration uses the existing repository and
FoundationHost.selectedRoot, with their existing READ revalidation.

Cancellation delivers Success(null), without accepting/persisting/selecting a new root. Existing
Library behavior re-reads the retained trail. Invalid URI, denied grant and storage failure
remain their typed Outcomes; a failed replacement does not clear the old root or user metadata.

Persistence and navigation are separate. If persistence succeeds but navigator selection fails,
Library receives the exact typed failure, never success. There is no claimed rollback. The
previous in-memory trail remains intact where the existing navigator permits it. Library may
finish its existing failure-path listing, but the host displays recovery and blocks ordinary
Library commands rather than exposing actionable stale rows. Two explicit recovery choices exist:

- Retry authoritative roots.restore, then select the restored root and refresh Library. No repeat
  persistence, automatic write replay, timed retry loop or parallel restore/picker is allowed.
- Launch a new authentic picker, needed if a stored grant cannot be restored. Only a successful
  new persistence/navigation pair clears recovery. Canceling/rejecting it leaves recovery active.

If no authoritative root and no in-memory trail exist, retry returns to the normal no-root state.
It never silently blesses a retained trail whose authoritative root is absent. The original
failed-selection status remains an honest record in F after a restore/refresh; only a subsequent
real selection completes a new selection operation. Permission/provider/storage distinctions use
existing localized Library resources. Reader-unavailable text is unchanged; the four app boundary
strings no longer incorrectly say folder selection is unsupported. Resource inventory stays155.

Existing Library code continues to own rooted directory/PDF listings, breadcrumbs, navigation
versions, superseded loads, explicit registration, metadata observations, favorites and uncertainty
reconciliation. Settings/back retains its navigator. The host adds no duplicate feature state
machine. Registered and transient open requests still return Unsupported.OPERATION, never Accepted.

## Exact changed files

Paths in the first two groups are relative to `app/src/`:

| Kind | File |
| --- | --- |
| Modified | main/java/com/abdev/partituraspdf/MainActivity.kt |
| Modified | main/java/com/abdev/partituraspdf/app/host/AppHost.kt |
| Modified | main/java/com/abdev/partituraspdf/app/host/AppHostFactory.kt |
| Modified | main/java/com/abdev/partituraspdf/app/host/AppHostState.kt |
| Modified | main/java/com/abdev/partituraspdf/app/host/AppHostViewModel.kt |
| Added | main/java/com/abdev/partituraspdf/app/host/RootPickerCoordinator.kt |
| Added | main/java/com/abdev/partituraspdf/app/host/RootPickerBinding.kt |
| Modified | main/res/values/app.xml |
| Modified | main/res/values-es/app.xml |
| Modified | main/res/values-ca/app.xml |
| Modified | main/res/values-en/app.xml |
| Added | test/java/com/abdev/partituraspdf/app/host/RootPickerCoordinatorTest.kt |
| Added | test/java/com/abdev/partituraspdf/app/host/HostLibraryIntegrationTest.kt |
| Added | androidTest/java/com/abdev/partituraspdf/app/integration/RootPickerBindingTest.kt |
| Added | androidTest/java/com/abdev/partituraspdf/app/integration/LibraryPersistenceIntegrationTest.kt |

The sixteenth file is this new `docs/integration/I_APP_MVP_HANDOFF.md`. No files were deleted.
No protected F/G/H/storage/data/core/PDF/worker implementation, schema, dependency, Gradle,
CI, policy, fixture, existing test or infrastructure validator was changed.

## Tests and actual local evidence

Final source validation uses the existing activated cloud toolchain. No setup/dependency changes.

| Check | Actual result |
| --- | --- |
| assembleDebug and assembleDebugAndroidTest | PASS |
| lintDebug | PASS:0 errors,24 warnings, unchanged warning count |
| testDebugUnitTest and existing contract gate | PASS:478 executed,0 failed/errored/skipped |
| Python infrastructure | PASS:289 tests |
| Complete localization validator | PASS:Spanish default/es + ca/en, unchanged155 main resources |
| Canonical fixture verifier | PASS:all15 checksums/structure/rendering expectations |
| Production debug APK PDF inventory | PASS:0 PDFs; test APK has15 canonical PDFs |
| Whitespace/protected-file scope | PASS |
| API26/27/36 instrumentation | NOT RUN:no connected device or verified usable emulator |
| Authentic production MainActivity → DocumentsUI grant journey | NOT RUN |
| Hosted CI | NOT RUN:owner-reported outage; no Actions investigation/dispatch |
| Physical device/TalkBack/visual/provider acceptance | NOT RUN/deferred |

There are34 new deterministic JVM tests:25 coordinator tests and9 host/Library integration tests.
They cover acknowledgment versus authorization, exact flags, success/failure/cancel, duplicates,
early callbacks, recreation tokens, superseded results, partial updates, bounded recovery,
reauthorization, shutdown, original cancellation/defects, explicit registration, stale commands,
favorite uncertainty and registered/transient Reader rejection. Existing SAF/Library/foundation
JVM regressions execute in the full suite. All Prompt2 regressions remain unchanged.

Nine new Android tests compile with javap-confirmed public void JUnit4 signatures:
RootPickerBindingTest has6 AndroidX registry/Intent tests, including buffered results across registry
restoration and expected launch exceptions. LibraryPersistenceIntegrationTest has3 real Room/AtomicFile/
SAF catalog-mapping/navigation/controller tests, including registration/favorites across restart,
canceled/invalid replacement and revoked restoration. Their query/grant seams are **synthetic** and
explicitly labeled. They do not establish real OS persisted authorization. They use an in-memory
Room database and a unique owned cache subdirectory; cleanup joins owners before deleting only
that directory. No application database, actual user grant or existing private data is modified.
All8 Prompt2 Android tests remain compiled and enabled. Source inventory is378 standard tests plus
14 assigned diagnostics; this is an inventory, **not an executed-test count**.

The initial filtered JVM test invocation passed its selected methods but failed the existing
whole-contract coverage gate because the other contract tests were not executed. The full suite
was then used; no gate was excluded or weakened. Final commands all pass.

### Scoped JVM coverage

Existing JaCoCo0.8.14 agent/class output and execution data are reused. A temporary external Gradle
init script adds reports/verification without changing project Gradle or shared CI. Counters include
nested/coroutine classes. Both the combined logic and separate coordinator counters satisfy90/80:

| JVM source scope | LINE | BRANCH |
| --- | --- | --- |
| RootPickerCoordinator.kt only |76/76=100% |72/76=94.74% |
| Host ViewModel/state/features/session + navigation + coordinator |219/219=100% |187/210=89.05% |
| Unfiltered entire host/navigation packages, informational only |219/348=62.93% |187/302=61.92% |

The logic scope excludes Android composition/registry binding and Compose UI (AppHostFactory,
ProductionHostSession, RootPickerBinding and AppHostKt). These require Android execution. The last
row exposes their JVM misses; it is **not** combined Android coverage or a full-host coverage PASS.
Authentic Android coverage for those paths remains unmeasured. No worker/native coverage is claimed.

Commands executed (Bash wrapper invocation preserves repository file mode):

```bash
source /workspace/partituras-tools/activate.sh
bash ./gradlew -I /tmp/i03-coverage.init.gradle \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  :app:testDebugUnitTest --rerun :app:i03Verify :app:i03PickerCoverage :app:i03AllCoverage \
  --no-configuration-cache --max-workers=4 --console=plain
python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest \
  --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 scripts/local_e2e.py doctor
adb devices -l
git diff --check
git diff --cached --check
```

Doctor is BLOCKED:acceleration unavailable, no /dev/kvm, dedicated AVD UNPROVISIONED. The device
list is empty. No AVD was provisioned/started and no unknown/physical device was used. Coverage
XML/HTML are in `/tmp/i03-coverage/`; the tested external init script is `/tmp/i03-coverage.init.gradle`.
Build/Python/fixture logs and additional signature/scope evidence are under `/tmp/i03-*`; these are
local review artifacts, not committed reports or CI evidence.

On a separately verified owner-authorized API26 emulator, the pending standard test command is
`bash ./gradlew :app:connectedDebugAndroidTest --max-workers=4 --console=plain`, with ANDROID_SERIAL
explicitly set to that emulator. Inspect actual JUnit XML. The existing dedicated opt-in SAF journey
is `python3 scripts/local_e2e.py run --suite saf` after its ownership/doctor prerequisites; it exercises
the debug acceptance activity, not proof of the new MainActivity journey. Manually verify the new
launcher with real DocumentsUI selection/cancel/replacement, rotation while pending, subfolder/back,
registration/favorites, restart, revoked grant and unavailable-provider recovery. Do not infer PASS
from APK compilation, mocked grants, historical runs or an empty/emulated inventory.

## Prompt4 boundary and owner review

The exact extension point remains ProductionHostSession.load's `foundation.host(folders, ...,
open = { Outcome.Failure(AppError.Unsupported.OPERATION) })`. Prompt4 will receive the existing
registered/transient DocumentOpenRequest there. No ReaderRoute/ReaderController/native session was
introduced. Prompt4 must retain one route/controller and let G be the single opening authority.
Acceptance of a request cannot mean a displayed page or durable checkpoint.

Before implementing Reader Back, provide the required graphical retirement barrier: keep state
observation/consumer detach possible while G quiesces; await detach and pending native work, flush
actual visible-page checkpoints, retire leases and close the route before leaving/reopening. Never
block main, recycle a displayed bitmap or use a timeout to force-close live native work. Registered
positions use the existing SharedPreferences authority; transient requests do not become durable
capabilities. Refresh Library once on Reader return so F's accepted-open state can be used again.
No part of those Reader requirements is implemented by this task.

No confirmed defect requiring a protected-file correction was found. Local code is delivered for
review; authentic Android execution, UI/accessibility/provider acceptance, hosted gates and final
human approval remain blockers to certification. There is no authorization to publish, merge or
begin I05 in this assignment.

## Reproducing the local coverage reports

Save this tested init script outside the checkout as `/tmp/i03-coverage.init.gradle`
(or an explicitly selected local temporary path and adapt the command). It adds task-local
reports only, with the existing project JaCoCo installation. Separately inspect `picker.xml`
and require covered/(covered+missed) >=0.90 LINE and >=0.80 BRANCH for the coordinator;
`i03Verify` enforces the combined logic scope. This task also asserted both scopes directly
from the generated XML. Do not substitute JVM-only results for pending Android evidence.

```groovy
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
gradle.beforeProject { p ->
 if (p.path == ':app') p.plugins.withId('com.android.application') { p.afterEvaluate {
  def classes=p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
   include 'com/abdev/partituraspdf/app/host/AppHostViewModel*'
   include 'com/abdev/partituraspdf/app/host/AppHostState*'
   include 'com/abdev/partituraspdf/app/host/AppHostFeatures*'
   include 'com/abdev/partituraspdf/app/host/AppHostSession*'
   include 'com/abdev/partituraspdf/app/host/RootPickerCoordinator*'
   include 'com/abdev/partituraspdf/app/navigation/**'
  }
  def data=p.layout.buildDirectory.file('jacoco/testDebugUnitTest.exec')
  def report=p.tasks.register('i03Coverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports { xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i03-coverage/report.xml'))
    html.outputLocation.set(new File('/tmp/i03-coverage/html')) }
   doFirst { if(classes.files.empty || !data.get().asFile.isFile()) throw new GradleException('Missing real host classes/JVM execution') }
  }
  p.tasks.register('i03PickerCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/RootPickerCoordinator*'
   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i03-coverage/picker.xml'))
    html.outputLocation.set(new File('/tmp/i03-coverage/picker-html'))}
  }
  p.tasks.register('i03AllCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/**', 'com/abdev/partituraspdf/app/navigation/**'
   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i03-coverage/all.xml'))
    html.outputLocation.set(new File('/tmp/i03-coverage/all-html'))}
  }
  p.tasks.register('i03Verify',JacocoCoverageVerification) {
   dependsOn report
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   violationRules { rule { limit {counter='LINE';minimum=0.90};limit {counter='BRANCH';minimum=0.80} } }
  }
 } }
}
```

## I05 implementation update — 2026-10-19 (Prompt4)

The preceding I03/I04 report is historical evidence. This update supersedes its temporary
Reader-unsupported boundary; it does not retroactively claim device execution for that report.
Starting revision: `86ee91ca0f5be90607f83261450b37add152d2c7`, clean, on
`feat/app-mvp-integration-i`. I05 source revision:
`f6e9e180bb67f47406a1325694d60f922e055fa0`. A subsequent documentation-only commit records this
update; its exact SHA is available from `git log -1 --format=%H -- docs/integration/I_APP_MVP_HANDOFF.md`.
No branch publication, PR, merge or Prompt5 implementation is authorized/performed here.

### Production connection and opening arbitration

ProductionHostSession now supplies `open = { reading.accept(it) }` to the existing
FoundationDependencies.host callback. FoundationDependencies.reader(scope) constructs the existing
ReaderRouteOwner using the same graph's registry, read adapter, positions and isolated PDF engine.
No new engine, Room/DataStore instance or feature implementation is introduced.

ReaderHostLifetime owns one route/controller lifetime. Before dispatching F's Open/RetryOpen action,
the ViewModel arms a permit containing its observed row key and rendered navigation revision. When
F returns its registered/transient DocumentOpenRequest, the host verifies the current Library route,
revision and exact current row/association. It uses F's existing registered identity without an
extra registry lookup, and preserves an unregistered row's transient capability without registering
it. Missing, stale, failed-lookup or identity-mismatched rows cannot allocate a Reader. The permit is
consumed once. A live/exiting Reader rejects competing opens; ordinary Back cannot jump past its
retirement barrier. No resource appears in AppNavigation, only destination and revision.

After validation and confirmed fit, ownership is allocated, an increasing ReaderOpenIntent is
published, navigation enters Reader, and only ReaderController.open(intent) initiates route opening.
The returned Success(Unit) acknowledges that ownership/navigation intent, not PDF/session/render/
presentation/checkpoint success. Recomposition and recreation never call open. A later eligible
Library opening gets a new sequence and fresh route/controller after the retired lifetime closes.

Initial PAGE/WIDTH comes from H's existing authoritative preferences observation. The host reads
its current Ready snapshot once per accepted opening, with no additional DataStore observer.
Loading rejects the opening with STALE_SESSION (explicit retry after preferences become ready);
Failed preserves its exact error. Neither fabricates a persisted default or allocates a Reader.
Reader-local fit changes remain local. Continuous global fit/theme/locale effects remain Prompt7.
A fresh Runtime maxMemory/totalMemory/freeMemory snapshot supplies validated HeapAvailability
before G plans each render. Existing ReaderMemoryPolicy and graph/session ceilings do the bounding;
no physical-RAM substitute, new allocation policy, cache, adjacent prefetch or benchmark is added.

AppHost collects the retained G StateFlow with lifecycle-aware observation and forwards its existing
ReaderScreen actions unchanged through sequence/revision guards. Its Back callback and Android Back
use the same asynchronous exit. Requested, rendered, drawn and durably checkpointed pages remain
different states. Only the existing software SurfaceView/presenter can issue Presented; the host
never manufactures it or writes requested/rendered page positions.

### Exit, graphical release and terminal graph ownership

Reader Back synchronously marks `leaving`, rejects conflicting Reader actions, and exposes loading
while cleanup is pending. It does not prematurely announce that native closure succeeded.
The Reader lifetime's retirement StateFlow is collected **without lifecycle suspension**, separately
from the lifecycle-aware PDF state collector. `leaving` removes ReaderScreen and its AndroidView,
allowing the existing synchronous onRelease/onDetachedFromWindow presenter cleanup to run. Normal
Activity destruction also releases the old View; window invisibility uses G's existing detach path.
No Activity, SurfaceView, bitmap or launcher is retained by this host owner.

The one shared exit Deferred performs the following suspended operations, in order:

1. Await controller.quiesce(BACKGROUND), including pending native work and explicit frame release.
2. Inspect route.background() (actual pending checkpoint flush).
3. Await controller.retire().
4. Close the route and inspect the result.
5. Cancel/join the Reader-owned scope.
6. Publish the exit report; only then return to Library and dispatch its existing Refresh.

Unmount and quiescence may overlap; quiescence cannot finish early while an actual consumer still
holds the frame. The surface detaches after main-thread software drawing. No host timeout, forced
bitmap recycle, synthetic detach or main-thread runBlocking bypasses that barrier. Repeated Back
observes the same Deferred. Refresh resets F's Accepted row state and preserves the navigator,
registration and favorite metadata, permitting reopening the same/different document.

Cleanup runs NonCancellable in the retained host scope, not the Reader scope it must eventually
cancel. Each ordered step is inspected even if a previous step reports failure. Original exceptions
(including cancellation) and secondary suppressed failures remain inspectable. Typed errors retain
their exact stage/AppError in ReaderExitReport. Checkpoint failures are shown on Library with the
existing localized unconfirmed-checkpoint text and logged; other typed cleanup failures use the
existing localized action-failure text. None is a durable-save success. Unexpected cleanup defects
terminate the host after ordered Reader cleanup, preserving the original cause. Failed/retired
lifetimes are never reused. A prior typed report also remains available to final shutdown unless a
new accepted lifetime replaces it; its earlier logged evidence is retained.

AppHostSession.retireReader is a suspend shutdown hook. Final ViewModel disposal rejects opens,
closes the picker, marks Reader terminal and unmounts the UI via Closing. It awaits the same Reader
exit before stopping other features, canceling/joining graph users and closing the factory on IO.
An active route resolving Room or a delayed native operation therefore cannot race graph closure.
If terminal disposal arrives during Back, it shares cleanup and suppresses the normal navigation
return. This extends the existing independent owner scope; ViewModel's automatic viewModelScope
cancellation cannot interrupt it.

On configuration recreation the same ViewModel, lifetime, controller, route and intent remain.
The old surface detaches; the replacement surface borrows the same eligible frame and must draw
before its own presentation acknowledgment. No native reopen or fake checkpoint follows recreation.
No Reader process-death state is serialized or restored by this assignment.

### Changed files (I05 only)

All paths below are relative to `app/src/`:

| Kind | File |
| --- | --- |
| Modified | main/java/com/abdev/partituraspdf/app/host/AppHost.kt |
| Modified | main/java/com/abdev/partituraspdf/app/host/AppHostFactory.kt |
| Modified | main/java/com/abdev/partituraspdf/app/host/AppHostState.kt |
| Modified | main/java/com/abdev/partituraspdf/app/host/AppHostViewModel.kt |
| Modified | main/java/com/abdev/partituraspdf/app/navigation/AppNavigation.kt |
| Added | main/java/com/abdev/partituraspdf/app/host/ReaderHostLifetime.kt |
| Modified | main/res/values/app.xml |
| Modified | main/res/values-es/app.xml |
| Modified | main/res/values-ca/app.xml |
| Modified | main/res/values-en/app.xml |
| Added | test/java/com/abdev/partituraspdf/app/host/ReaderHostLifetimeTest.kt |
| Added | test/java/com/abdev/partituraspdf/app/host/ReaderHostIntegrationTest.kt |
| Added | test/java/com/abdev/partituraspdf/app/navigation/ReaderNavigationTest.kt |
| Added | androidTest/java/com/abdev/partituraspdf/app/integration/ReaderHostNativeIntegrationTest.kt |

The fifteenth changed file is this handoff. No files were deleted. MainActivity and all existing
tests are unchanged. F/G/H production logic, shared contracts, ReaderRouteOwner, native engine,
worker/AIDL/isolation, SAF/persistence, schemas, Gradle/dependencies, policies/CI and canonical
fixtures are unchanged. The boundary text now describes only deferred global settings effects;
all155 resource IDs and complete Spanish-default/es/ca/en translations remain intact.

### I05 tests and actual evidence

| Validation | Actual result |
| --- | --- |
| Debug APK and instrumentation APK compilation | PASS |
| JVM full suite and existing contract gate | PASS:511 tests,0 failures/errors/skips |
| Python infrastructure | PASS:289 tests |
| Lint | PASS:0 errors,25 warnings |
| Complete localization and formatting | PASS:155 main resources per locale, debug resources unchanged |
| Canonical fixture verification | PASS:all15 checksums/structure/rendering expectations |
| APK fixture inventory | PASS:debug0 PDFs, test APK15 canonical PDFs |
| Diff whitespace/protected-source inventory | PASS |
| Android native/display/PixelCopy/recreation tests | NOT RUN:no connected device/usable authorized emulator |
| API26/27/36 hosted checks | NOT RUN:owner-reported outage; no platform investigation/dispatch |
| Physical tablet/provider/TalkBack/manual UI acceptance | NOT RUN/deferred |

The additional Lint warning is UnusedResources for `app_reader_unavailable`, whose identifier is
preserved after replacing the stub. The other24 warnings remain. No suppression/gate change is used.

There are33 new JVM tests:19 Reader lifetime tests,11 ViewModel/F integration tests and3 explicit
Reader navigation tests. They exercise the actual G controller with contract route/native doubles,
not JVM-fabricated Android bitmaps. Coverage includes registered/transient identities, permits,
sequences, stale/duplicate callbacks, confirmed fit, fresh heap requests, native errors/cancellation,
delayed noncooperative opens/renders, explicit late frame detach, lease-release defects, exact
flush/retire/close order, original/secondary failures, repeated Back, final shutdown sharing the
exit, and F refresh enabling a fresh lifetime. All I01–I04/F/G/H regressions remain enabled.
Initial new test failures exposed a test-double property/method naming recursion; the fixture was
corrected without changing production semantics, existing tests or assertions.

Four new Android methods compile as public void JUnit4 tests. They connect a retained test-owned
host, real F/controller/route/Room/SharedPreferences, the real isolated engine and real G surface.
Canonical assets are hash-verified and copied to unique owned directories and opened read-only; the
file capability/listing seam is test-only and does **not** prove authentic SAF authorization.
Room is in-memory; positions use unique test-only preferences. Cleanup detaches the tracked
composition, awaits host retirement, then removes only owned files/preferences. The recreation
case tracks the replacement composition too, rather than treating old composition state as a
release barrier for the new View.

The tests require actual eligible Presented callbacks, native page navigation and local WIDTH fit;
PixelCopy verifies ink and paper inside the posted page bounds. They also require Back/reopening,
registered visible-page flush, transient nonregistration/no position persistence, unchanged
controller/route intent across Activity recreation with a new graphical consumer, private isolated
service flags and malformed/protected canonical failures. No host/test directly fabricates a
Presented signal in these Android tests. **None executed here**; APK compilation, test inventory
and the Python fixture renderer are not proof of Android PDF display or worker execution.
Source inventory is382 standard tests plus14 assigned diagnostics (396 total), not execution counts.

#### JVM coverage scope

The existing JaCoCo0.8.14 execution data/class outputs are reused through an external temporary init
script. No repository build or CI configuration changes. Exact source attribution, including
nested/coroutine classes:

| Source/scope | LINE | BRANCH |
| --- | --- | --- |
| ReaderHostLifetime.kt (coordinator + fit policy) |83/83=100% |47/52=90.38% |
| AppHostViewModel.kt |159/159=100% |162/192=84.38% |
| AppNavigation.kt |13/13=100% |16/16=100% |
| Combined host/state/navigation/SAF-picker/Reader logic |342/342=100% |297/336=88.39% |
| Unfiltered host/navigation packages, informational only |342/494=69.23% |297/446=66.59% |

Both new coordinator and affected ViewModel/navigation logic satisfy90/80 independently; the
combined average is not used to hide a deficient feature. Android composition, registry binding,
ProductionHostSession/AppHostFactory and Compose UI are outside the JVM logic gate and included
as misses in the informational whole-package row. Their authentic Android coverage is unmeasured.
No worker-process coverage is claimed. The generated XML was checked directly for90/80 on both
the Reader-only and aggregate scopes in addition to the aggregate Gradle verification task.

Final commands (Bash wrapper, existing activated toolchain):

```bash
source /workspace/partituras-tools/activate.sh
bash ./gradlew -I /tmp/i05-coverage.init.gradle \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  :app:testDebugUnitTest --rerun :app:i05Verify :app:i05ReaderCoverage :app:i05AllCoverage \
  --no-configuration-cache --max-workers=4 --console=plain
# After final test-only teardown/transient assertions:
bash ./gradlew :app:assembleDebugAndroidTest :app:lintDebug --max-workers=4 --console=plain
python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest \
  --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
adb devices -l
git diff --check
git diff --cached --check
```

The device list is empty; no unknown/physical device was used and no emulator was provisioned or
started. The established environment lacks usable acceleration. Toolchain/dependencies/setup
configuration remain unchanged. Logs, coverage HTML/XML and signatures are under `/tmp/i05-*`.
The current source commit is the validated code; this append is documentation only. Authentic
API26 execution and hosted gates remain mandatory outstanding evidence. Use the owner-authorized
emulator command documented above, inspect real JUnit XML, and never infer PASS from compilation.

### Prompt5 extension point and deferred requirements

Current ReaderHostLifetime serializes acceptance against final Back/retirement. AppHostSession's
retireReader hook is already joined before graph shutdown. Prompt5 must extend this foundation,
not introduce a competing manager or cancel the cleanup's owner first.

Still missing: Activity/host BACKGROUND, LOW_MEMORY, CRITICAL and RESUME event callbacks and their
serialized interaction with opening/Back/terminal disposal. For nonterminal background/trim,
quiesce G with graphical cleanup possible, inspect route.background(), apply the existing trim
policy's closeSession decision (LOW_MEMORY keeps it; BACKGROUND/CRITICAL close it), and finish
all mutations before G.resume(). Resume must use G's route.current identity/reopen mechanism;
the host must not separately call route.open or reuse retired controllers. Remount only when
barriers permit, with a genuine new graphical acknowledgment. Existing exit always retires and
therefore cannot serve as a resumable pause without an explicitly implemented extension.

No full process-death restore, live global settings/locale/window effects, expanded recovery
matrix, native worker instrumentation, memory/performance benchmark or acceptance closeout is
implemented here. No confirmed protected-component defect requiring owner approval was found.
Graphical detachment is protocol-tested and Android-test-defined, but actual device scheduling,
recreation and isolated native execution remain unverified here. These are pending validation,
not an acceptance waiver. Stop at I05 for owner review before Prompt5.

### Reproducing I05 local scoped coverage

Save the following tested script outside the checkout as `/tmp/i05-coverage.init.gradle`
and run the command above. Reader-only XML must also meet90% LINE/80% BRANCH; the aggregate
verification task alone is insufficient evidence for that separate scope. No project build
configuration is modified.

```groovy
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
gradle.beforeProject { p ->
 if (p.path == ':app') p.plugins.withId('com.android.application') { p.afterEvaluate {
  def classes=p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
   include 'com/abdev/partituraspdf/app/host/AppHostViewModel*'
   include 'com/abdev/partituraspdf/app/host/AppHostState*'
   include 'com/abdev/partituraspdf/app/host/AppHostFeatures*'
   include 'com/abdev/partituraspdf/app/host/AppHostSession*'
   include 'com/abdev/partituraspdf/app/host/RootPickerCoordinator*'
   include 'com/abdev/partituraspdf/app/host/Reader*'
   include 'com/abdev/partituraspdf/app/navigation/**'
  }
  def data=p.layout.buildDirectory.file('jacoco/testDebugUnitTest.exec')
  def report=p.tasks.register('i05Coverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports { xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i05-coverage/report.xml'))
    html.outputLocation.set(new File('/tmp/i05-coverage/html')) }
   doFirst { if(classes.files.empty || !data.get().asFile.isFile()) throw new GradleException('Missing real host classes/JVM execution') }
  }
  p.tasks.register('i05ReaderCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/Reader*'
   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i05-coverage/reader.xml'))
    html.outputLocation.set(new File('/tmp/i05-coverage/reader-html'))}
  }
  p.tasks.register('i05AllCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/**', 'com/abdev/partituraspdf/app/navigation/**'
   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i05-coverage/all.xml'))
    html.outputLocation.set(new File('/tmp/i05-coverage/all-html'))}
  }
  p.tasks.register('i05Verify',JacocoCoverageVerification) {
   dependsOn report
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   violationRules { rule { limit {counter='LINE';minimum=0.90};limit {counter='BRANCH';minimum=0.80} } }
  }
 } }
}
```


## I06 — Android lifecycle and Reader memory coordination (2026-10-19)

This dated update supersedes the I05 future-lifecycle boundary above while preserving its
historical evidence. Starting revision: `8a21cb5d9842880d7c94a27a52bb48decbfbb66a`.
Implementation revision: `9ed9c2f8c34037cdbf91bc572bc92fb24b932666`, on `feat/app-mvp-integration-i`.
The subsequent documentation-only commit containing this section is the delivery tip
(resolve with `git log -1 --format=%H -- docs/integration/I_APP_MVP_HANDOFF.md`). Both commits
remain local: no push, PR, Actions dispatch, merge, or Prompt 6 implementation.

### Exact scope

Eight source/test files and this existing handoff are changed; no files deleted:

- `app/src/main/java/com/abdev/partituraspdf/MainActivity.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/AndroidReaderLifecycle.kt` (new)
- `app/src/main/java/com/abdev/partituraspdf/app/host/AppHostViewModel.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/ReaderHostLifetime.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/AppHost.kt`
- `app/src/test/java/com/abdev/partituraspdf/app/host/ReaderHostIntegrationTest.kt`
- `app/src/test/java/com/abdev/partituraspdf/app/host/ReaderLifecycleIntegrationTest.kt` (new)
- `app/src/androidTest/java/com/abdev/partituraspdf/app/integration/ReaderHostNativeIntegrationTest.kt`
- `docs/integration/I_APP_MVP_HANDOFF.md`

Protected F/G/H implementation, foundation/SAF/renderer/worker, Binder/AIDL, contracts,
Room/schema, DataStore, dependencies, Gradle/CI/policies, resources and immutable fixtures
are unchanged. No process-death restore, global preference/locale/window handling,
performance benchmark, worker exporter or extra navigation functionality is introduced.

### Ownership, Android callbacks and policy

The existing I05 `ReaderHostLifetime` owns the same route, child scope, G controller and
`ReaderOpenIntent`. One serialized lifecycle drain extends that owner; there is no second
session manager, native engine or checkpoint store. MainActivity forwards `onStart`,
`onStop`, `onTrimMemory` and legacy `onLowMemory` using a per-Activity UUID. The retained
host holds identity data, never an Activity. New Activity registration invalidates stale
callbacks. Configuration `onStop` does not background/retire the Reader. Duplicate starts
cannot undo a foreground memory suspension. A BACKGROUND memory callback also clears
visible acknowledgement so the next actual start can recover.

| Actual event / named constant | Host action |
| --- | --- |
| `onStart` after superclass | Foreground acknowledgement; request serialized recovery if suspended |
| `onStop`, not changing configuration | BACKGROUND |
| `onStop`, changing configuration | Preserve Reader owner/session |
| `TRIM_MEMORY_RUNNING_LOW` | LOW_MEMORY |
| `TRIM_MEMORY_RUNNING_CRITICAL`, `MODERATE`, `COMPLETE` | CRITICAL |
| `TRIM_MEMORY_UI_HIDDEN` | BACKGROUND, except while changing configuration |
| `TRIM_MEMORY_BACKGROUND` | BACKGROUND |
| `onLowMemory` | Named `TRIM_MEMORY_COMPLETE` / CRITICAL |
| Unknown / `RUNNING_MODERATE` | No invented severity mapping |

Genuine low/critical memory pressure is still honored during configuration change. Modern
Android versions deprecate or cease delivering some legacy memory callbacks; this is not
proof they occur on API 36. Real `onStop` supplies the background path independently.

### Implemented operation traces

These are implementation/JVM traces, not a claim of Android execution:

- **BACKGROUND:** mark suspended and mask frame -> G `quiesce(BACKGROUND)` -> wait pending
  open/render and graphical detach -> route `background()` (confirmed checkpoint flush) ->
  route `close()` under existing memory policy -> retain controller, intent and child owner.
- **LOW_MEMORY:** mask frame -> G `quiesce(LOW_MEMORY)` -> release page/frame after consumer
  completion -> retain native session; no flush, close, reopen or automatic reallocation.
  Explicit retry or a later genuine foreground transition requests recovery.
- **CRITICAL:** mask frame -> G `quiesce(CRITICAL)` -> flush -> policy close; retain logical
  Reader lifetime. A stronger pending trim supersedes weaker work without downgrading it.
- **RESUME:** wait all preceding lifecycle work -> if foreground, current and recoverable,
  call G `resume()` -> G checks `route.current()` and validates retained session or performs
  its own reopen. No host `open`, new user sequence or second route construction occurs.
- **Configuration recreation:** ignore config stop -> old composition releases consumer ->
  same ViewModel/graph/controller/intent/native session -> new composition/viewport attaches.
  Old Activity IDs cannot stop/trim/start the replacement owner.
- **Back during cleanup:** mark leaving/unmount -> join lifecycle work, discard queued
  resume -> final quiesce -> flush only if not already closed -> G retire -> close only if
  not already closed -> cancel/join Reader scope -> Library + existing F refresh. No reappear.
- **Final host shutdown:** stop admission -> same leave operation, without Library return ->
  stop F/H -> cancel/join graph users -> existing IO graph close. No cancel-and-race reopen.

The drain job is registered before starting (safe with Main.immediate reentrancy). Repeated
completed trims do not repeat writes/closes; queued CRITICAL dominates BACKGROUND/LOW.
Native open/render cannot be overtaken by close; late completed output is disposed by G,
never published as a current frame. NonCancellable protects mandatory cleanup, with no
blocking main-thread join, sleep, forced recycling, or invented timeout success.

Temporary suspension keeps the same ReaderScreen composition/local zoom. The host collects
G state with `collectAsState()` even when ordinary lifecycle-aware collectors are stopped,
and supplies a masked `frame=null` snapshot. Existing surface visibility/detach completion
releases attachments after drawing. Final Back unmounts as before. This creates an explicit
release path independent of a STOPPED lifecycle collector; actual device scheduling is still
pending. Viewport updates remain legal while suspended, but page/fit/presentation actions
cannot allocate. The JVM tests explicitly withhold and then complete a consumer detach
without any state collector, proving ordering rather than fabricating graphical completion.

Typed checkpoint/close errors remain inspectable and logged; checkpoint failure is never
reported as durable success. Original exceptions and cancellation remain original causes,
with secondary typed failures retained in suppressed diagnostics. An uncertain close blocks
resume/current-session reuse; safe Back remains available. Unexpected causes cause existing
terminal host disposal. A close attempt is not silently retried as proof of successful close.

A delegating route port supplies only a RAM transient-page hint when G itself reopens: the
last actually presented page is clamped to the reopened document bounds. Merely requested or
rendered pages are not confirmations. Registered reopen continues to use the existing route's
authoritative confirmed checkpoint. The wrapper does not own/open a second native lifetime
or persist transient capabilities. Retry can display a new G open failure without inventing
successful reuse; foreign retained sessions are rejected by G's existing identity checks.

### Tests and actual local results

29 new JVM tests (21 lifecycle/controller protocol cases and 8 retained-host boundary cases)
cover background/low/critical/resume, coalescing, delayed flush/open/render/draw, obsolete
Activity and Reader generations, config retention, typed failures/original cancellation,
uncertain close, transient actual-presentation hints, registered recovery, viewport gating,
Back/terminal races, duplicate starts, and UI_HIDDEN-to-foreground acknowledgement. All prior
I01–I05 tests/assertions remain enabled.

Four new Android methods use the existing isolated native PDF engine and test-owned files:
genuine Activity CREATED/RESUMED, explicitly simulated low-memory with retained zoom/session,
explicitly simulated critical-memory/new session, and delayed real rendered output discarded
across background. A test-owned lifecycle observer forwards genuine Activity events to the
test host; it does not replace the production factory or claim real OS memory pressure.
The existing recreation test now additionally checks unchanged native session ID. Unique
preferences, isolated Room/files and final cleanup remain. All eight methods in that class
compile as JUnit 4 void methods. New methods are **compiled, NOT RUN** here.

Commands executed (SDK/JDK activated with `source /workspace/partituras-tools/activate.sh`):

```bash
bash ./gradlew -I /tmp/i06-coverage.init.gradle \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  :app:testDebugUnitTest --rerun :app:i06Verify :app:i06ReaderCoverage :app:i06AllCoverage \
  --no-configuration-cache --max-workers=4 --console=plain
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest \
  --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
git diff --check
```

| Check | Actual result |
| --- | --- |
| Debug and Android-test APK compilation | PASS |
| JVM JUnit XML | **540 tests, 0 failures/errors/skips** (baseline 511 +29) |
| Python infrastructure | **289 PASS** |
| Lint | **0 errors, 25 warnings**, unchanged I05 count |
| Localization | PASS, 155 main resources, complete es/ca/en parity |
| Immutable canonical fixture verification | PASS, all 15 |
| APK ZIP PDF inventory | Debug: 0 PDFs; Android-test APK: 15 fixtures |
| Android source inventory | 386 standard +14 assigned diagnostic =400; not execution counts |
| Android/API26/27/36, native worker, physical tablet, TalkBack | **NOT RUN** |
| Hosted Actions | **NOT RUN**; no network investigation/dispatch during owner-reported outage |

No authorized emulator is attached (`adb devices` empty); no emulator was created or modified.
Memory callbacks are not real device evidence. Legacy callback deprecation compiler warnings
are reported, not suppressed. Local logs `/tmp/i06-delivery-build.log`, `/tmp/i06-python.log`,
`/tmp/i06-fixtures.log` and JaCoCo reports are ephemeral, outside Git.

### Authentic JVM scoped JaCoCo evidence

Existing JaCoCo 0.8.14 reads actual `testDebugUnitTest.exec` and compiled debug classes.
No hand-written execution data or excluded hard branch was substituted.

| Scope / attribution | LINE | BRANCH |
| --- | --- | --- |
| Reader host + Android mapping, including nested/coroutine/data classes | **169/169 (100%)** | **160/181 (88.40%)** |
| `ReaderHostLifetime.kt` | 162/162 (100%) | 153/174 (87.93%) |
| `AndroidReaderLifecycle.kt` | 7/7 (100%) | 7/7 (100%) |
| `AppHostViewModel.kt` | 190/190 (100%) | 223/272 (81.99%) |
| Testable host/navigation logic aggregate | **459/459 (100%)** | **471/545 (86.42%)** |
| Unfiltered host/navigation including Android factory/binding/Compose | 459/620 (74.03%) | 471/663 (71.04%) |

Reader scope and aggregate each satisfy >=90% LINE / >=80% BRANCH. The unfiltered report
exposes Android-only misses; it is not a full Android acceptance PASS. This measures host
coordination only, not F/G/H feature re-certification or isolated worker execution. Synthetic
JVM page/session doubles test ordering, not Android/Pdfium rendering. The aggregate includes
existing navigation/root picker logic; Reader-only scope prevents that code hiding new misses.

### Prompt 6 retained versus durable boundaries

- **Configuration recreation:** current route/nav revision, Reader sequence/controller,
  graph owner, source capability, viewport-related state and transient hint live in retained
  ViewModel/Reader objects; old Activity cannot mutate a replacement. Local screen zoom is
  preserved across temporary suspension, not promised as durable process-death state.
- **Process death:** retained objects/jobs/sessions/bitmaps/URIs-as-capabilities do not survive.
  Existing saved bundle holds only host lifetime and pending SAF request UUID markers, not a
  Reader restore codec or permission. No process-death restore is implemented in I06.
- **Registered restore:** Prompt 6 must use stable registered document identity and existing
  registry mapping, validate current real grants/read access and revision, and use confirmed
  durable checkpoint semantics. A prior in-memory Ready state is not authorization.
- **Transient recovery:** capability and presented-page hint remain RAM-only. After process
  death require renewed legitimate selection/access; do not persist or trust a raw transient
  locator as an automatic restore credential.
- **Page restoration:** only successful actual presentation is eligible for existing confirmed
  checkpoints, never requested/rendered page. Registered storage retains its established
  revision/position rules; transient RAM hints are not a new durable store.
- **Grant revalidation:** G/current foundation reopening still owns validation. Prompt 6 must
  preserve live SAF permission/provider identity and typed denial/revocation results before
  reopening; saved state cannot manufacture persisted grants or bypass access mediation.

No confirmed protected-component defect requiring scope expansion was found. Real Android
scheduling/graphics/memory-pressure/recreation, native worker execution, external providers,
physical Android 8.1 tablet, TalkBack and phase-close acceptance remain pending evidence.
These are validation blockers, not waivers or claims of discovered production defects.
Stop after I06 for owner review; Prompt 6 requires its own authorization.

### Reproducing I06 scoped coverage

Save the following tested external init script as `/tmp/i06-coverage.init.gradle`, then run
the command above. It introduces no repository build or CI change. Inspect the Reader-only
XML independently against both thresholds in addition to the aggregate `i06Verify` task.

```groovy
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
gradle.beforeProject { p ->
 if (p.path == ':app') p.plugins.withId('com.android.application') { p.afterEvaluate {
  def classes=p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
   include 'com/abdev/partituraspdf/app/host/AppHostViewModel*'
   include 'com/abdev/partituraspdf/app/host/AppHostState*'
   include 'com/abdev/partituraspdf/app/host/AppHostFeatures*'
   include 'com/abdev/partituraspdf/app/host/AppHostSession*'
   include 'com/abdev/partituraspdf/app/host/RootPickerCoordinator*'
   include 'com/abdev/partituraspdf/app/host/Reader*'
   include 'com/abdev/partituraspdf/app/host/AndroidReaderLifecycle*'
   include 'com/abdev/partituraspdf/app/navigation/**'
  }
  def data=p.layout.buildDirectory.file('jacoco/testDebugUnitTest.exec')
  def report=p.tasks.register('i06Coverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports { xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i06-coverage/report.xml'))
    html.outputLocation.set(new File('/tmp/i06-coverage/html')) }
   doFirst { if(classes.files.empty || !data.get().asFile.isFile()) throw new GradleException('Missing real host classes/JVM execution') }
  }
  p.tasks.register('i06ReaderCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/Reader*'
   include 'com/abdev/partituraspdf/app/host/AndroidReaderLifecycle*'
   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i06-coverage/reader.xml'))
    html.outputLocation.set(new File('/tmp/i06-coverage/reader-html'))}
  }
  p.tasks.register('i06AllCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/**', 'com/abdev/partituraspdf/app/navigation/**'
   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i06-coverage/all.xml'))
    html.outputLocation.set(new File('/tmp/i06-coverage/all-html'))}
  }
  p.tasks.register('i06Verify',JacocoCoverageVerification) {
   dependsOn report
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   violationRules { rule { limit {counter='LINE';minimum=0.90};limit {counter='BRANCH';minimum=0.80} } }
  }
 } }
}
```


## I07 — Saved navigation and registered-position restoration (2026-10-19)

Starting revision: `a33c937ca2d3d9faa62f5de7ffc0b18d46a6cb13`.
Implementation: `66be7ed3dd89a980fac2df15a98e3a9deff3b522` on
`feat/app-mvp-integration-i`. The following documentation-only commit is the final delivery
(resolve with `git log -1 --format=%H -- docs/integration/I_APP_MVP_HANDOFF.md`). Both are
local only: no push/PR/merge or hosted CI dispatch. Historical I01–I06 evidence above remains
intact. This section supersedes I06's statement that process-restoration logic is unimplemented;
authentic Android system-process-death acceptance is still **NOT RUN**.

### Exact changed files

Paths relative to the repository; three new files, seven modified, none deleted:

- `app/src/main/java/com/abdev/partituraspdf/MainActivity.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/AppHostFactory.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/AppHostViewModel.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/ReaderHostLifetime.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/navigation/AppRestoration.kt` (new)
- `app/src/test/java/com/abdev/partituraspdf/app/host/AppRestorationIntegrationTest.kt` (new)
- `app/src/test/java/com/abdev/partituraspdf/app/navigation/AppRestorationTest.kt` (new)
- `app/src/androidTest/java/com/abdev/partituraspdf/app/integration/AppHostActivityTest.kt`
- `app/src/androidTest/java/com/abdev/partituraspdf/app/integration/ReaderHostNativeIntegrationTest.kt`
- `docs/integration/I_APP_MVP_HANDOFF.md`

### Three startup modes and representation

| Mode | Actual behavior |
| --- | --- |
| Configuration recreation | ViewModelProvider returns the same host; factory does not create another session or consume its saved argument. Same F/H, navigator, G route/controller/intent/native session remain eligible under I06. Old consumer detaches, new composition attaches; obsolete Activity UUID callbacks are ignored. |
| New host with eligible Android saved instance state | Fresh graph/controllers through the existing factory; decode small logical navigation, wait for authoritative startup, then restore Settings or attempt a registered Reader through G. Never reuse a frame, surface, descriptor, coroutine, worker/session or transient capability. |
| Ordinary fresh launch / absent or invalid saved state | Library. Restore selected root and preferences through existing repositories; do not reopen a previous Reader implicitly. |

The existing ComponentActivity saved-instance `Bundle` is the suitable already-installed
Android mechanism: `onSaveInstanceState` writes one string at `app.navigation`; onCreate
passes it to the existing ViewModel factory. No SavedStateHandle dependency or second durable
store is needed. The AndroidX retained owner wins during configuration recreation. The
existing host lifetime/pending-picker UUID markers remain unchanged.

`AppRestoration` uses explicit versioned wire strings `1:library`, `1:settings`, or
`1:reader:<DocumentId>`. No enum ordinal, URI, root/breadcrumb, page number, ReaderUiState,
bitmap, PDF bytes or native resource is encoded. Reader ID is bounded to 256 UTF-16 code
units, nonblank and free of ISO control characters; total Reader payload is at most 265 code
units. Opaque case, spaces and punctuation are preserved, not normalized into another ID.
Oversized/nonrestorable identities save as Library; absent/unknown version/destination,
blank/control/oversized identity and former transient representations decode as Library.
The bound is a restoration eligibility limit, not a change to the public DocumentId contract.

A snapshot while Reader is leaving, host is closing or registration is known missing is
Library. A transient Reader also snapshots as Library. Back invalidates pending restore
immediately, including before startup completes. A newer accepted user route's own identity
replaces the former snapshot. Android does not promise this Bundle after force-stop, crash,
task removal, or every process termination: without it startup is fresh Library. No durable
last-route history or live resource serialization has been introduced.

### Startup, root authorization and one-shot admission

Actual order is: independently decode bounded optional state -> construct one session/graph
on IO -> construct shared navigator/F/H/Reader lifetime -> connect existing admission/cleanup
callbacks -> pass foreground state -> start F and H once -> publish Ready -> wait for F root
initialization and H preference observation to leave Loading -> consider saved navigation.
Decoding occurs early for validation; it does not allocate a Reader or authorize access.

F already calls `FoundationHost.selectedRoot()`, which invokes
`SelectedRootRepository.restore()` and initializes the **same** FolderNavigation via select.
`PrivateSelectedRootRepository` rechecks the actual persisted READ grant. No second root save,
new grant, picker launch, registration mutation or metadata deletion occurs. NoRoot, revoked
permission, unavailable provider and other typed failures remain F's existing recovery states.
A replacement process returns to the authorized root, not a serialized nested trail. Existing
in-process nested navigation remains retained during configuration recreation.

Settings reconstruction uses the newly initialized H controller and observed preferences.
For Reader, this implementation conservatively requires an Available authorized root and Ready
preferences before admission. NoRoot/root failure/preference failure discards automatic Reader
restoration and leaves existing Library/Settings recovery available. After restoring access the
user must explicitly open a document; there is no hidden retry loop. This deliberately favors
safe root restoration over guessing a former nested location or bypassing a failed startup.

The host admits exactly one registered logical request using a restoration-bound permit and
current navigation revision. It does not fabricate a Library row or transient request. The same
I05 Reader lifetime then supplies one fresh ReaderOpenIntent to G; only G calls the existing
ReaderRouteOwner, which resolves `DocumentRegistry.resolve(document)` before the SAF adapter
opens current bytes and the existing isolated engine creates its new session. Navigation
acceptance alone does not certify registration, permission, native open or graphical success.
Missing registration / revoked permission / unavailable provider / malformed or protected PDF /
checkpoint read failure use existing typed Reader error and Back behavior. Registration and
favorites are never erased or inferred deleted because reopening failed. A missing registration
also makes subsequent navigation snapshots Library.

A root/preferences initialization barrier prevents early onStart from bypassing startup.
If backgrounded before admission, restoration waits for a later real onStart, without allocating
native work; repeated starts/startup calls cannot consume it twice. LOW/CRITICAL pressure
before Reader admission cancels automatic Reader restoration and stays in Library. Once
admitted, all stop/trim/resume/Back/terminal work uses the unchanged I06 queue and barriers.
Back/new user navigation/library commands cancel pending restore; stale revisions do not.
Terminal disposal cancels pending observation and joins graph users. A noncooperative registry
may finish an already started native open despite cancellation: the existing route/controller
closes/discards the late result before graph teardown, never publishes it into a newer route.
Original programming defects remain in the cause chain (coroutine stack-trace recovery can wrap
them), rather than becoming fake provider failures. No second lifecycle state machine exists.

### Position authority and durability

`SharedPreferencesReaderPositions` / `ReaderPositionRepository` remains the only production
page authority. Existing `ReaderRouteOwner` loads it after opening a new real session, then
`restoredPage` clamps to that session's actual count. Zero, nonzero, missing and formerly
longer-document positions retain existing semantics. Invalid stored types/negative values
are ignored by the existing repository without deletion/repair. No checkpoint is written just
because a stored page was read. Room legacy reading-position data is never consulted.

Actual eligible presentation can schedule a checkpoint; requested/rendered page or a successful
schedule is not durable confirmation. I06 quiesces, then inspects background/close flush results.
Reconstruction reads the repository, not a page in the saved Bundle. Interrupted/failed writes
may leave an earlier committed position; uncertain commits are not promised to roll back.
Android SharedPreferences can update its in-memory cache even when commit fails. Therefore the
same-process test doubles' deliberately retained earlier page are one failure scenario, not a
promise that every real failed write must restore that page. True new-process/disk behavior
needs device execution. Writer epochs/sequences remain unchanged; an obsolete writer cannot
legitimately overwrite a newer route. No second page store, migration or fallback is added.
Transient requests neither use registered position storage nor become eligible merely by having
been viewed: configuration retention is RAM-only; after process death the user must open again
using current access, or have explicitly registered the document through existing contracts.

### Tests, commands and actual results

35 new JVM methods: 31 fresh-host/real G + ReaderRouteOwner integration methods and 4 codec
methods. Storage/native are declared contract doubles, not fake Android rendering. Cases cover
all three startup modes, nested retention, fresh controllers/sessions, registered identity,
zero/nonzero/missing/clamped pages, no write on open, requested/rendered versus actual presented,
unconfirmed/failed/flush results, newer-writer rejection, transient fallback, malformed state,
root/prefs failure, permission/provider errors, forged/terminal restore permits, newer user
open, delayed registry, Back/disposal barriers, stale publication and original defect chains.
Controlled coroutine scheduling and explicit gates/StateFlow conditions replace sleeps.

Six new Android methods compile as JUnit4 void methods: Library Activity recreation plus five
synthetic Bundle/new-host tests for disk-Room registered reopening, actual presented-page flush,
out-of-range clamp, Settings reconstruction, transient nonrestoration and typed access loss.
They retain prior I05/I06 native assertions. Test fixture reuse is explicit: a unique owned cache
directory, uniquely named preferences, optional disk Room in that directory, canonical fixture
copy with SHA verification, old host fully disposed before reopening Room, final deletion only
of those test-owned paths. Normal fixture mode still uses in-memory Room. Existing real isolated
engine is used; no in-process PdfRenderer fallback. The contract access-loss injection is clearly
labeled **not real Android grant revocation**. Synthetic Bundle reconstruction does not recreate
a process or certify system-supplied saved state after kill; graceful old-host cleanup is not
crash emulation. Existing selected-root/persistence/SAF and FoundationPersistence invalid-value /
writer-epoch regressions remain enabled and compiled, but Android execution is NOT RUN.

Executed after `source /workspace/partituras-tools/activate.sh` where SDK/JDK are needed:

```bash
bash ./gradlew -I /tmp/i07-coverage.init.gradle \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  :app:testDebugUnitTest --rerun :app:i07Verify :app:i07ReaderCoverage :app:i07AllCoverage \
  --no-configuration-cache --max-workers=4 --console=plain
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest \
  --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
adb devices
git diff --check
```

| Evidence | Actual result |
| --- | --- |
| Debug + instrumentation APK builds | PASS, test methods compiled; no Android execution implied |
| JVM JUnit | **575 executed, 0 failures/errors/skips** (I06 540 +35); I01–I06 and existing ReaderRouteOwner/F/G/H/persistence regressions retained |
| Python infrastructure | **289 PASS** |
| Lint | **0 errors, 25 warnings**, unchanged I06 count; legacy trim deprecation compiler warnings remain visible |
| Localization | PASS, unchanged complete Spanish-default/es/ca/en resources (155 main IDs); no new strings |
| Canonical fixtures | **15 PASS**, immutable bytes/structure/render checks |
| APK PDF inventory | Debug 0; Android test APK 15 canonical fixtures |
| Android source inventory | 392 standard +14 assigned diagnostics =406; **source counts, not executed counts** |
| Android API26/27/36, native/worker/surface/process-kill/grant-revocation | **NOT RUN**; `adb devices` empty; no emulator created/adopted/modified |
| Hosted CI | **NOT RUN**, owner-reported outage; no platform investigation or workflow edits/dispatch |

Initial local iterations found and corrected test-fixture issues (wrong fake page-count
expectation, navigation callback replaced by the open bridge, missing await of asynchronous
IO cleanup and an overly strict assumption that a noncooperative canceled registry cannot
finish any native open). An Android fixture's recursive Kotlin inferred types required explicit
File/String types. Those iterations were FAIL, not acceptance evidence; the final command above
is PASS. Raw local logs `/tmp/i07-final-validation.log`, `/tmp/i07-final-python.log`,
`/tmp/i07-fixtures.log` and reports are ephemeral and excluded from Git.

### Authentic scoped JVM coverage

Existing JaCoCo 0.8.14 reads the actual JVM execution file and debug class files. Generated
nested/coroutine/data classes within included sources are retained; no difficult business path
was excluded. Source-group totals below sum the corresponding sourcefile counters in the real
report, not a different execution source.

| Source / scope | LINE | BRANCH |
| --- | --- | --- |
| New `AppRestoration.kt` codec | **16/16 (100%)** | **28/28 (100%)** |
| `AppHostViewModel.kt` including existing and new host behavior | **243/245 (99.18%)** | **318/376 (84.57%)** |
| `ReaderHostLifetime.kt` including registered restoration entry | **165/165 (100%)** | **157/178 (88.20%)** |
| Affected three-source group | **424/426 (99.53%)** | **503/582 (86.43%)** |
| Existing scoped host/navigation aggregate plus restoration | **531/533 (99.62%)** | **598/681 (87.81%)** |
| Unfiltered host/navigation including Android factory/binding/Compose | 531/694 (76.51%) | 598/799 (74.84%) |

New codec, affected source group and aggregate each exceed 90% LINE /80% BRANCH; individual
host/lifetime files also exceed both. MainActivity/Android factory, resource/UI behavior,
real SAF, native workers and surfaces are not certified by JVM coverage. The unfiltered row
exposes those Android-only misses rather than calling APK compilation coverage. No protected
feature/worker coverage, CI threshold or project Gradle configuration is changed.

### Prompt 7 handoff and remaining acceptance

- H's retained `SettingsController.state.preferences` exposes authoritative observed theme and
  PAGE/WIDTH; production graph preferences already supply it. Current initial Reader fit uses
  a confirmed Ready snapshot. Prompt 7 may apply observed theme at the outer theme composition
  and confirmed fit through existing ReaderAction.Fit with I06 sequencing; do not persist an
  in-reader local choice implicitly or reopen native sessions unnecessarily.
- `DeviceLocalePolicy` with delivered English/Catalan and Spanish default resources is available.
  Prompt 7 owns API26-compatible context/Activity locale application before resource reads and
  recreation on actual device-language change. Legacy LanguageSelection must not override it.
  I07 retained versus reconstructed-owner boundaries must remain distinct during that recreation.
- MainActivity and current destination/foreground/suspension state are the existing host/window
  integration points for Reader keep-screen-on; no window flags or runtime effects added here.
- Actual saved-state capture -> system-kill -> relaunch on an authorized disposable emulator,
  fresh-process disk positions, real grants/revocation, native/surface lifecycle, mandatory
  latest-head CI/API26 and compatibility API27/36 remain NOT RUN. Physical Android8.1 tablet,
  TalkBack, external providers and deferred C06 backup/D2D acceptance remain pending; no waiver.
- No confirmed protected-source defect requiring scope expansion was found. Missing device
  evidence and framework-dependent saved-state availability are limitations, not fabricated
  defects. Do not promise restore after force-stop/task removal/crash without an eligible Bundle.

No F/G/H, core/storage/data/persistence, worker/AIDL, schema, contracts, dependencies, Gradle/CI,
quality policy, canonical fixture or backup changes. No performance benchmark, global settings,
locale/window implementation, broad recovery redesign or Prompt 7 work. Stop after I07 for
owner review; no publication authorized by this assignment.

### Reproducing I07 coverage without repository build changes

Save this tested external script as `/tmp/i07-coverage.init.gradle` and run the command above.
The external task named `i07ReaderCoverage` now selects the new restoration codec only and
writes `/tmp/i07-coverage/reader.xml`; inspect `report.xml` for host/lifetime source counters
and `all.xml` for the unfiltered Android-inclusive denominator. Paths and tasks here are local
measurement tooling, not installed CI. Reconstruct the file from this text if `/tmp` is lost.

```groovy
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
gradle.beforeProject { p ->
 if (p.path == ':app') p.plugins.withId('com.android.application') { p.afterEvaluate {
  def classes=p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
   include 'com/abdev/partituraspdf/app/host/AppHostViewModel*'
   include 'com/abdev/partituraspdf/app/host/AppHostState*'
   include 'com/abdev/partituraspdf/app/host/AppHostFeatures*'
   include 'com/abdev/partituraspdf/app/host/AppHostSession*'
   include 'com/abdev/partituraspdf/app/host/RootPickerCoordinator*'
   include 'com/abdev/partituraspdf/app/host/Reader*'
   include 'com/abdev/partituraspdf/app/host/AndroidReaderLifecycle*'
   include 'com/abdev/partituraspdf/app/navigation/**'
  }
  def data=p.layout.buildDirectory.file('jacoco/testDebugUnitTest.exec')
  def report=p.tasks.register('i07Coverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports { xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i07-coverage/report.xml'))
    html.outputLocation.set(new File('/tmp/i07-coverage/html')) }
   doFirst { if(classes.files.empty || !data.get().asFile.isFile()) throw new GradleException('Missing real host classes/JVM execution') }
  }
  p.tasks.register('i07ReaderCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/navigation/AppRestoration*'

   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i07-coverage/reader.xml'))
    html.outputLocation.set(new File('/tmp/i07-coverage/reader-html'))}
  }
  p.tasks.register('i07AllCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/**', 'com/abdev/partituraspdf/app/navigation/**'
   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i07-coverage/all.xml'))
    html.outputLocation.set(new File('/tmp/i07-coverage/all-html'))}
  }
  p.tasks.register('i07Verify',JacocoCoverageVerification) {
   dependsOn report
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   violationRules { rule { limit {counter='LINE';minimum=0.90};limit {counter='BRANCH';minimum=0.80} } }
  }
 } }
}
```


## I08–I10 — Settings effects, automatic locale and Reader window (2026-10-19)

Starting revision: `991084ff176f16aaec110a5c981064754703e2ae`.
Implementation: `8634d965046486fe79658568e2968dd359b120e4`, on
`feat/app-mvp-integration-i`. The following evidence commit also removes an extra empty EOF
line in the new presentation-policy file caught by the staged whitespace check; it changes no
behavior. Resolve the delivery tip with `git log -1 --format=%H -- docs/integration/I_APP_MVP_HANDOFF.md`.
All commits remain local: no push, PR, merge or Actions dispatch. Historical I01–I07 evidence
is preserved; this section supersedes earlier statements that global effects/locale/window
integration are not implemented. Android runtime acceptance remains NOT RUN.

### Exact file scope

Five new and six modified files, none deleted:

- `app/src/main/java/com/abdev/partituraspdf/MainActivity.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/AppHost.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/AppHostViewModel.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/ReaderHostLifetime.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/AppPresentation.kt` (new, Android/Compose boundary)
- `app/src/main/java/com/abdev/partituraspdf/app/host/AppPresentationState.kt` (new, host policy/flows)
- `app/src/main/java/com/abdev/partituraspdf/app/localization/AppLocaleContext.kt` (new)
- `app/src/test/java/com/abdev/partituraspdf/app/host/AppPresentationIntegrationTest.kt` (new)
- `app/src/androidTest/java/com/abdev/partituraspdf/app/integration/AppLocaleIntegrationTest.kt` (new)
- `app/src/androidTest/java/com/abdev/partituraspdf/app/integration/ReaderHostNativeIntegrationTest.kt`
- `docs/integration/I_APP_MVP_HANDOFF.md`

No protected F/G/H, core/storage/data/position/checkpoint implementation, native worker/AIDL,
Room schema, dependencies, Gradle, CI/policy, backup or canonical fixture changes. Existing
material palettes, Reader gesture zones and H/G contracts remain intact. No new resource IDs,
strings, language selector, wake lock, foreground service, immersive mode, brightness/timeout
setting, profiling or Prompt 8 recovery matrix is introduced.

### I08 — One authoritative observation, global theme and default fit

Production H already owns the continuous `PreferencesRepository.observe()` collector. The
retained host now observes H's **StateFlow**, maps its preference observation and deduplicates
it; it does not subscribe to DataStore again or write anything. That observer is a child of
the existing session/graph attempt and is canceled/joined by existing disposal. Theme commands,
Pending/Reconciling/Failed operations and write acknowledgments cannot substitute for Ready.

`AppPresentation` wraps the entire AppHost including startup/failure and Library/Reader/Settings:
SYSTEM uses Compose's system-night configuration, LIGHT and DARK force the corresponding
existing PartiturasPDFTheme palette. Loading/Failed use a visual system fallback while retaining
the actual non-Ready observation; this is not a confirmed preference/default or automatic repair.
A genuine Ready update changes colors through recomposition, not Activity recreation, graph
construction or Reader opening. There is no process-global theme variable. H's selected values,
retries, uncertain-write reconciliation and repository semantics remain authoritative.

New Reader lifetimes still initialize from `confirmedReaderFit(H.state.preferences)`; unavailable
preferences reject opening explicitly through the existing policy, without indefinitely waiting
or silently assuming a persisted PAGE. The host remembers only the last confirmed default-fit
value for change detection. Repeated Ready, theme-only and legacy-language-only emissions do
not dispatch Fit or reset a local Reader menu choice. Loading/Failed do not reset this comparison
baseline. A **genuinely different Ready default** replaces the active Reader's local fit via the
existing ReaderAction.Fit boundary. This is the chosen external-update policy, not an implicit
write of the Reader-local menu back to settings.

Active fit dispatch uses G's existing page/viewport invalidation and does not reopen a PDF or
reset its page. During I06 suspension, only the latest external fit is retained in RAM and is
applied after mandatory cleanup, immediately before G.resume. It neither resumes automatically
nor allocates during memory suspension. Back/terminal reject effects into a leaving lifetime;
a new lifetime clears the pending value and reads the current authoritative default. No extra
Reader/session/checkpoint store or competing lifecycle coordinator is introduced.

The obsolete “global settings will be integrated later” banner is removed from AppHost. Its
resource ID/translations are retained for compatibility. Startup/failure columns now consume
safe-drawing insets as the ready shell already did. Existing nested safeDrawingPadding uses
Compose inset consumption; no additional Reader padding or gesture/layout redesign is added.

### I09 — Automatic Activity locale, API26+

`MainActivity.attachBaseContext` wraps the base in a configuration context **before UI resource
reads**. `systemAppLanguage()` takes language tags from `Resources.getSystem().configuration.locales`,
not a previously localized Activity or persisted legacy LanguageSelection. It delegates to the
existing DeviceLocalePolicy with delivered English/Catalan enabled. Primary es/ca/en and regional
variants map to those languages; unsupported/absent input maps to Spanish even with a supported
secondary language. A single resolved LocaleList makes that fallback explicit.

`Configuration(base.resources.configuration)` preserves the other configuration values and sets
only locales/layout direction. `createConfigurationContext`, LocaleList and these configuration
APIs are available on API26. No Locale.setDefault, global Resources.updateConfiguration, device
setting, AppCompat/per-app language store or new dependency is used. Contexts stay Activity-owned;
no Activity/Window/Context reference enters the retained host or a feature controller.

With no new manifest configChanges override, Android's ordinary configuration recreation handles
system language/night changes. OnStart also compares the Activity language with current system
policy after background return; a mismatch requests one recreation for that Activity and skips
its stale foreground acknowledgement. The replacement attach creates the localized context.
The per-Activity guard prevents repeated recreation requests by the same instance. Real system
language-change/relaunch behavior, including OEM/API differences, still requires execution.
I06 configuration-stop identity guards preserve retained owners; I07 reconstructs new owners only
when genuinely supplied a new ViewModel lifetime. Locale never changes saved IDs/grants or page
checkpoint authority. Unsupported-primary-to-unsupported-primary still resolves to Spanish.

### I10 — Activity-owned window and system bars

The AppPresentation DisposableEffect owns lifecycle/focus listeners and a STARTED-scoped wakeup
collector for exactly its Activity/composition. It reads host/nav/lifetime/controller state and
frame availability. Notifications carry no cached permission to enable a flag: the callback
re-reads current snapshots immediately before touching **that Activity's own Window**.

FLAG_KEEP_SCREEN_ON is enabled only for a Ready host, current Reader destination, non-leaving,
non-suspended lifetime, VISIBLE G state with a current active graphical consumer, and a RESUMED,
focused Activity. Mere retained controller existence, OPENING/ERROR, detached frames, hidden/
background Activities or obsolete snapshots cannot qualify. Frame availability changes wake the
collector even when the controller's StateFlow has not changed at detach. Pure eligibility and
actual Android flag mutation are deliberately separate coverage/evidence boundaries.

Back marks leaving before asynchronous cleanup; pause/stop/focus loss/closing all clear eligibility.
Lifecycle callbacks independently clear the flag when state drops below RESUMED. Composition
release cancels the job, removes lifecycle/focus listeners (including a replaced view tree), and
clears the flag. Old windows remain tied to old Activity lifecycles; no Window is retained by the
ViewModel. Foreground recovery requires a new real presentation before keeping the screen awake.
The I06 native/graphics barriers and I07 registered/transient restoration behavior remain unchanged.

System bars stay visible. A theme-derived surface scrim plus AndroidX SystemBarStyle.auto supplies
appropriate light/dark icon handling through existing API26-compatible edge-to-edge support. No
immersive navigation hiding, custom wake lock or duplicate Reader insets are added. Physical
contrast, landscape, font-scale, keyboard, gestures and TalkBack acceptance remain pending;
existing G layout/accessibility tests remain enabled and compiled, not claimed executed here.

### Tests and actual validation

29 new JVM methods exercise real retained H/G state machines with declared storage/native doubles:
theme SYSTEM/LIGHT/DARK; Loading/Failed; pending/failed/uncertain writes; actual observation versus
acknowledgment; one settings collector; no PDF reopen on theme; initial/default/local fits; repeated
Ready/theme/language emissions; external updates after pending writes while Reader is active;
failed-read explicit retry; new lifetimes; queued latest fit behind low/background cleanup; Back/
terminal safety; primary policy forwarding without global mutation; visible/error/detached Reader
eligibility; resumed/focused requirements; lifecycle wakeups and rapid repeated navigation. Existing
DeviceLocalePolicy/DeliveredLocalePolicy tests supply the language-policy matrix rather than a
second policy implementation. Existing I01–I07/F/G/H/persistence assertions remain enabled.

11 new Android methods compile: four real resource-context/Activity cases and seven native-host
presentation cases. Resource cases check es/ca/en and regions, unsupported-primary+supported-secondary
Spanish fallback, actual navigation/feature/accessibility strings, base/global nonmutation and
preserved density/orientation/night/font configuration, plus genuine Activity recreation/retained
host. Native cases check confirmed LIGHT/DARK/SYSTEM colors, **injected** Compose night configuration,
Reader theme recreation and old-window flag clearing, initial/local/external fit with the same real
isolated session, actual Window background/resume/Back/Settings/disposal, and synthetic new-host
reload of persisted theme/fit. Injection is not a claim of actual device system-language/night change.

The native test fixture now uses the existing production DataStore adapter on a unique file in its
owned cache directory. Its scope belongs to the test graph's children; disposal cancels/joins it
before reusing the file, closing Room or removing test files. It cannot write the production
app_preferences file. Field updates are real adapter writes; new-host tests reopen the same test
file only after disposal. Fixture PDFs remain verified, immutable originals with owned copies.

During review, an I07 test helper defect was found: ReaderTestComposition.mount is deliberately
one-use, but synthetic reconstruction previously tried to reuse it after unmount. Reconstruction
now creates a fresh test composition. No protected helper/production restoration code changed.
This is a confirmed test-only defect corrected here; the affected Android methods still need
actual execution. Two new JVM fixture expectations also initially failed because H correctly
replayed the prior Ready/Failed value; tests now explicitly clear replay and supply controlled
fresh read evidence, preserving H's real semantics and strengthening Ready assertions.

Executed after `source /workspace/partituras-tools/activate.sh` for SDK/JDK commands:

```bash
bash ./gradlew -I /tmp/i08-coverage.init.gradle \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  :app:testDebugUnitTest --rerun :app:i08Verify :app:i08ReaderCoverage :app:i08AllCoverage \
  --no-configuration-cache --max-workers=4 --console=plain
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest \
  --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
adb devices
git diff --check
```

| Evidence | Actual result |
| --- | --- |
| Debug/instrumentation APK builds | PASS; new methods compile as JUnit4 void; not Android execution |
| JVM JUnit | **604 executed, 0 failures/errors/skips** (I07 575 +29) |
| Python infrastructure | **289 PASS** |
| Lint | **0 errors, 27 warnings**; I07 had25; details below |
| Localization | PASS; unchanged complete Spanish-default/es/ca/en,155 main IDs |
| Canonical verifier | **15 PASS**, hashes/structure/render integrity unchanged |
| APK PDF ZIP inventory | Debug0; instrumentation15 canonical fixtures |
| Compiled APK resources | aapt2 dump confirms app_settings default/es Ajustes, ca Configuració, en Settings |
| Android source inventory | 403 standard +14 diagnostics =417; source counts, not executed tests |
| API26/27/36, native/surface/window, real language/night changes, physical device | **NOT RUN**, adb devices empty; no emulator created/adopted/modified |
| Hosted CI | **NOT RUN**, owner-reported outage; no investigation, dispatch or CI changes |

APK resources inspected with existing SDK `build-tools/37.0.0/aapt2 dump resources
app/build/outputs/apk/debug/app-debug.apk`; PDF ZIP counts and void signatures were inspected
locally. Raw `/tmp/i08-delivery-validation.log`, `/tmp/i08-python.log`, `/tmp/i08-fixtures.log`,
`/tmp/i08-apk-resources.txt` and coverage reports are temporary, excluded from Git. The final
whitespace correction changes no executable line and final diff/staged checks pass.

The two new Lint warnings are reported without suppression: the retained historical
app_integration_boundary ID is now unused after removing the obsolete banner; and
AppBundleLocaleChanges warns that default **AAB language splits** can omit a language after
runtime/system-language changes without language-delivery handling. The inspected universal
debug APK contains all translations. AAB/split-delivery behavior is **not validated or certified**;
if AAB distribution is adopted, the owner must approve the appropriate packaging/language-delivery
scope (for example disabling language splits) before relying on switching to a not-installed
language. No Gradle/dependency/Play Core change is inferred from this assignment or made merely
to silence Lint. This is a concrete packaging warning, not a demonstrated universal-APK failure.

### Authentic JaCoCo scope and denominators

Existing JaCoCo0.8.14 reads actual JVM execution and debug classes. All nested/coroutine classes
inside scoped sources are included. No difficult branch is filtered. Theme/window pure policy
is separated from the Android/Compose boundary because JVM execution cannot certify real Context,
Window or surface effects. Source group totals are sums of actual sourcefile counters.

| Scope / source | LINE | BRANCH |
| --- | --- | --- |
| New AppPresentationState.kt | **20/20 (100%)** | **41/49 (83.67%)** |
| Theme mapping method | 5/5 (100%) | 9/9 (100%) |
| Window eligibility + wakeup methods (rest of that source) | 15/15 (100%) | 32/40 (80%) |
| ReaderHostLifetime.defaultFit method | 5/5 (100%) | 8/8 (100%) |
| ReaderHostLifetime.kt full scope, including pending fit/lifecycle integration | **173/173 (100%)** | **167/188 (88.83%)** |
| AppHostViewModel.kt full scope, including H observation | **259/262 (98.85%)** | **327/386 (84.72%)** |
| Three affected policy/host/lifetime sources | **452/455 (99.34%)** | **535/623 (85.87%)** |
| Host/navigation logic aggregate | **575/578 (99.48%)** | **658/750 (87.73%)** |
| Unfiltered host/navigation/locale/MainActivity, including Android/Compose glue | 576/834 (69.06%) | 658/920 (71.52%) |

Each new testable policy scope and affected source group satisfies >=90% LINE /80% BRANCH.
The 8 uncovered branches in readerVisible are visible in the denominator; no manufactured
state or unsafe resource is injected solely to cover defensive checks. Actual AppPresentation
Android/Compose code is0/35 lines,0/8 branches here; MainActivity0/52 lines,0/38 branches;
AppLocaleContext1/10 lines is only the pure existing-policy forwarding call. The other9 Context/
system-resource lines remain NOT RUN, not “covered by compilation”. Existing locale-policy JVM
regressions pass; actual locale contexts/resources must execute in Android. This is not feature/
worker recertification, combined Android coverage or an acceptance waiver.

### Prompt 8 handoff — I11 still reserved

Remaining integration-recovery scenarios must preserve the now-installed effects:

- Permission revocation during navigation: retain registration/favorites and explicit authorization recovery.
- Provider unavailable during registered reopen: typed access failure, no inferred deletion or busy retry.
- Delayed Activity Result: retain request/generation checks; never synthesize flags or accept an obsolete result.
- Native worker failure: existing typed/exception semantics and graphical/native cleanup; no replacement renderer.
- Back versus recovery: no stale reopen, fit dispatch or screen-on re-enable into a retired destination.
- Failed checkpoint flush: inspect actual durability result; presentation is not a committed position.
- Uncertain settings write: reconcile observed values; no desired-value theme/default substitute or duplicate writer.
- Saved-state recovery inconsistency: keep I07 bounded/versioned registered-only eligibility and transient fallback.
- Cancellation/stale cleanup: await mandatory barriers; retain original causes, close uncertain sessions safely.

No full I11 recovery matrix, Prompt8/9 closeout, performance profiling, backup/D2D, physical
Android8.1/TalkBack/external-provider acceptance or worker exporter is implemented. Remaining
runtime evidence is NOT RUN/DEFERRED, not a newly confirmed production defect. The confirmed
one-use Android test helper defect is fixed above. AAB language delivery needs separate release
packaging validation/owner decision if applicable. No protected-source correction was necessary
for the implemented universal-APK path. Stop after I08–I10 for owner review.

### Reproducing I08–I10 scoped coverage

Save this tested external script as `/tmp/i08-coverage.init.gradle`, then run the command above.
The external task named i08ReaderCoverage selects AppPresentationState only (`reader.xml`);
report.xml contains host/lifetime source counters; all.xml exposes the Android-inclusive misses.
No project Gradle/CI file is changed. Compare policy/window scopes independently, not only the
aggregate verification task.

```groovy
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
gradle.beforeProject { p ->
 if (p.path == ':app') p.plugins.withId('com.android.application') { p.afterEvaluate {
  def classes=p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
   include 'com/abdev/partituraspdf/app/host/AppHostViewModel*'
   include 'com/abdev/partituraspdf/app/host/AppHostState*'
   include 'com/abdev/partituraspdf/app/host/AppPresentationState*'
   include 'com/abdev/partituraspdf/app/host/AppHostFeatures*'
   include 'com/abdev/partituraspdf/app/host/AppHostSession*'
   include 'com/abdev/partituraspdf/app/host/RootPickerCoordinator*'
   include 'com/abdev/partituraspdf/app/host/Reader*'
   include 'com/abdev/partituraspdf/app/host/AndroidReaderLifecycle*'
   include 'com/abdev/partituraspdf/app/navigation/**'
  }
  def data=p.layout.buildDirectory.file('jacoco/testDebugUnitTest.exec')
  def report=p.tasks.register('i08Coverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports { xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i08-coverage/report.xml'))
    html.outputLocation.set(new File('/tmp/i08-coverage/html')) }
   doFirst { if(classes.files.empty || !data.get().asFile.isFile()) throw new GradleException('Missing real host classes/JVM execution') }
  }
  p.tasks.register('i08ReaderCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/AppPresentationState*'

   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i08-coverage/reader.xml'))
    html.outputLocation.set(new File('/tmp/i08-coverage/reader-html'))}
  }
  p.tasks.register('i08AllCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/**', 'com/abdev/partituraspdf/app/navigation/**', 'com/abdev/partituraspdf/app/localization/**', 'com/abdev/partituraspdf/MainActivity*'
   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i08-coverage/all.xml'))
    html.outputLocation.set(new File('/tmp/i08-coverage/all-html'))}
  }
  p.tasks.register('i08Verify',JacocoCoverageVerification) {
   dependsOn report
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   violationRules { rule { limit {counter='LINE';minimum=0.90};limit {counter='BRANCH';minimum=0.80} } }
  }
 } }
}
```

## I11 — Cross-feature recovery inventory (2026-10-20)

Starting revision: `3883f34b5ab34af81eb699f6d0e4b5c9e97fe984` on
`feat/app-mvp-integration-i`. This matrix was written before I11 production edits and finalized after validation. It records
existing ownership first. All linked JVM suites pass in the final local run; new corrections are
identified below. Android/native/real-permission execution remains NOT RUN throughout.

Evidence aliases refer to existing `app/src/test/java/com/abdev/partituraspdf/` classes:
RP=`app/host/RootPickerCoordinatorTest`; RL=`app/host/ReaderLifecycleIntegrationTest`;
RH=`app/host/ReaderHostLifetimeTest`; HI=`app/host/ReaderHostIntegrationTest`;
AR=`app/host/AppRestorationIntegrationTest`; PR=`app/host/AppPresentationIntegrationTest`;
F=`library/LibraryControllerTest`, `LibraryDocumentLifecycleTest`, `LibraryRegistrationTest`,
`LibraryFavoritesTest`; G=`reader/ReaderRecoveryTest`, `ReaderLifecycleTest`,
`ReaderPresentationTest`; H=`settings/SettingsControllerTest` and existing Settings tests.
New regressions reuse these same fixtures/controllers, avoiding a second
recovery specification/manager. Android/native/real-grant evidence remains NOT RUN until actual
execution; JVM contract doubles cannot establish Android authorization or worker behavior.

| ID / trigger and failure type | Authoritative owner | Observable state / permitted recovery | Cleanup and stale protection | Evidence / final local status |
| --- | --- | --- | --- | --- |
| R01 Root grant revoked after success | SelectedRootRepository + F | Typed revoked; explicit ChooseRoot/Retry; preserve metadata | Restore rechecks READ; no root/registry erasure | AR.rootFailurePreservesRecoveryAndNeverStartsRegisteredRead; F; existing |
| R02 Picker canceled | Android result + RP/F | Success(null) means explicit selection cancellation only; keep prior root | No grant/store/select; pending token cleared once | RP.cancellationIgnoresUriFlagsAndPreservesPreviousNavigationWithoutPersistence; existing |
| R03 Grant/root write followed by navigator failure | RP | Explicit reconciliation overlay; re-read root, never promise rollback | Block old trail commands; no duplicate root save | RP.partialRootUpdateReportsFailureWithoutRollbackAndRequiresAuthoritativeReconciliation; existing |
| R04 Provider unavailable during listing | Catalog + F | Typed provider failure, contextual retry | Listing generations; preserve registration/favorites | F.listingFailureHasNarrowRetryAndKeepsRoot; already covered, JVM PASS |
| R05 Provider I/O versus confirmed deletion | SAF + F/G | Distinct localized failure; no destructive recovery | Never record deletion from transient failure | AR.revokedAndUnavailableAccessRemainTypedWithoutRegistryMutations; already covered; HI.crossFeatureOpenErrorMatrixPreservesExactCategoriesAndRegistrationWithoutImplicitRetry adds typed matrix, JVM PASS |
| R06 Late/duplicate picker result | RP | Ignore obsolete id; finish current request once | Pending/launched/completing identity | RP.oldCallbackCannotAuthorizeANewerRequest/concurrentDuplicateCallbackDuringAcceptanceCannotRepeatPersistence; existing |
| R07 Revoked access with favorite/checkpoint | Registry/positions + SAF | Keep stored state; explicitly reauthorize/open | No registry/metadata writes in failure path | Existing Android AppSafLifecycleIntegrationTest; NOT RUN; JVM AR existing |
| R08 Stale subfolder operation | F + FolderNavigation | Stale typed operation; refresh authoritative trail | Navigator version + listing generation | F.externallyChangedNavigatorRejectsCurrentLookingRowBeforeOpening; existing |
| R09 Registered open loses READ | ReaderRouteOwner + G | Existing typed Reader failure; retry access or Back | Resolve registry before read; release unused input | AR.revokedAndUnavailableAccessRemainTypedWithoutRegistryMutations; existing |
| R10 Provider fails during open | SAF + ReaderRouteOwner/G | Existing provider failure, not deletion | Descriptor ownership and cancellation handoff | HI.crossFeatureOpenErrorMatrixPreservesExactCategoriesAndRegistrationWithoutImplicitRetry + existing AR; JVM PASS; Android SAF tests NOT RUN |
| R11 Native open failure | G/route | Existing localized failure and Back | Quiesce/retire/route close before Library | RH.nativeOpenFailureIsVisibleInExistingGStateAfterHostAcknowledgment; existing |
| R12 Render/resource/unsupported failure | G | Eligible render/open retry or Back, never busy loop | One native operation; no invalid frame publication | G.recoveryMatrixKeepsTypedStagesAndNeverPromisesToRepairUnsupportedInput; JVM PASS; new native-host recovery compiled only |
| R13 Worker/session loss | Isolated engine + G | Native/stale error, never inferred revoked/deleted | G current-session validation and existing retry | RL.foreignRetainedSessionIsRejectedByGInsteadOfReusedOnResume; existing; real worker NOT RUN |
| R14 Back during delayed native open | G + ReaderHostLifetime | Leaving immediately; return after cleanup | Await noncooperative work, discard late result | RH.pendingNonCooperativeOpenFinishesBeforeReaderRouteCanClose; existing |
| R15 Back during frame detach | G frame + host | Unmount first; await explicit detach | No forced recycling/timeout success | RH.renderedIsNotPresentedAndBackWaitsForExplicitGraphicalDetachWithoutRecyclingEarly; existing |
| R16 Repeated Back | Host/lifetime | Same exit completion | One lazy Deferred; no competing lifetime | Existing HI/RL/RH; retain |
| R17 Close fails and route still owns a session | Host/lifetime | Explicit blocked Reader admission; Library remains usable | Retire G; inspect current owner, never reset uncertainty blindly | RL.retainedSessionAfterFailedCloseBlocksAllNewReaderAdmissionAfterSafeBack + HI.uncertainRetainedRouteReturnsToLibraryWithoutAllowingAnotherReaderButSettingsStillWorks; corrected, JVM PASS |
| R18 Background failure | Host/lifetime | Inspectable typed/original diagnostics; no fake save | Close ordering; blocked automatic reuse | RL.closeFailureBlocksAutomaticReuseAndBackRetainsExactFailureEvidence; existing + RL.finalCloseFailureCannotForgetAStillRetainedRouteSession; corrected, JVM PASS |
| R19 Resume after retirement | Host + G | Ignore obsolete recovery | Terminal/leaving/intent generation | RL.backDuringBackgroundWinsOverQueuedResumeAndDoesNotDoubleClose; existing |
| R20 Reopen after safe exit | Host + G | New route/controller/intent, preserved Library | Old scope joined before admission | HI.realFRegisteredRequestEntersRetainedReaderAndBackRefreshPermitsFreshReopen; existing |
| R21 Scheduled checkpoint | Route/checkpoint | Unconfirmed schedule, not durable save | Only actual presented capability can schedule | AR.pendingPresentationIsUnconfirmedAndReconstructionReadsEarlierCommittedPage; existing |
| R22 Failed flush | Route + host | Visible stage/error; continue safe cleanup | Quiesce, flush, retire, close; no fallback page | HI.typedCheckpointFailureRemainsVisibleOnLibraryAndInTerminalShutdownEvidence; existing |
| R23 Earlier committed position after failure | Position repository | Restore actual stored result; no rollback promise | Writer epoch/sequence unchanged | AR.failedFlushNeverInventsNewCommittedPageAndNewOwnerReadsActualStorage/newerWriterRejectsLateOldCheckpointAfterReconstruction; existing |
| R24 Invalid saved navigation | AppRestoration + host | Safe Library; no synthetic Reader | Bounded/versioned ID validation | AppRestorationTest; existing |
| R25 Revoked root during restore | SelectedRootRepository + host | Library's typed recovery; no auto picker/open | Root/preferences barrier | AR.rootFailurePreservesRecoveryAndNeverStartsRegisteredRead; existing |
| R26 Transient process reconstruction | Host/codec | Library; explicit fresh access required | No persisted transient URI/page | AR.transientSnapshotDowngradesToLibraryWithoutWritingPositions; existing |
| R27 Back cancels restoration | Host | Keep newer Library state | One-shot restore + revision/cancel | AR.backDuringRootRestorePreventsLateSavedRoutePublication; existing |
| R28 Newer route supersedes restore | Host | Current user's route wins | Cancel pending restore before actions | AR.newerUserOpeningReplacesCanceledSavedReaderIdentityAndSnapshot; existing |
| R29 Failed theme write | H + host presentation | Actual Ready theme remains; explicit H retry | No desired-value substitution | PR.failedThemeWriteCannotBecomeAnObservedSuccess; existing |
| R30 Uncertain fit write | H + host/lifetime | Reconcile observation; defer confirmed fit during suspension | One writer; pending latest fit behind cleanup | PR.pendingFitWriteWaitsForReadyAndReconciliationDoesNotInventFit; existing |
| R31 Repeated Ready vs local fit | H + host | Local fit retained until genuinely changed default | Distinct preference observation; confirmed-fit cache | PR.localReaderFitRemainsLocalAndRepeatedReadyDoesNotInvalidateItsFrame; existing |
| R32 Late fit while retiring | Host/lifetime | No dispatch to old controller; next lifetime reads current default | Leaving/terminal checks | PR.fitUpdateDuringLeavingCannotEnterOldControllerAndNextOpenUsesLatestSnapshot; existing |
| R33 Recovery observer ownership | H + host | Explicit retry joins old observation, no duplicate DataStore collector | Existing H generation/child ownership | PR.themeUpdatesAndRecreationNeverOpenAnotherPdfOrSettingsObserver; existing |
| R34 Background versus Back | Host/lifetime | One cleanup, no queued resume after exit | Join lifecycle job before terminal exit | RL.backDuringBackgroundWinsOverQueuedResumeAndDoesNotDoubleClose; existing |
| R35 Stronger pending trim | Host/lifetime + memory policy | CRITICAL dominates weaker work | One drain; no downgrade/repeated close | RL.lowThenCriticalWhileRenderIsNonCooperativeCannotDowngradeCleanup; existing |
| R36 Host disposal | Host | Closing then accurate Closed; no publication | Retire Reader, stop F/H, join users, close graph | HI.terminalGraphCloseWaitsForOngoingReaderBackAndFlushAndRejectsNewCommands; existing |
| R37 Stale Activity callback | Host | Ignore old identity, retain replacement | Activity UUID + Reader sequence | HI.configurationStopAndObsoleteActivityCallbacksDoNotCloseOrReopenReader; existing |
| R38 Uncertain root store / duplicate persistence | RP + selected-root repository | Reconcile authoritative stored root before acting, never replay write | One accept; private-storage failure can have committed | RP.uncertainPrivateRootWriteRequiresReadReconciliationRatherThanBlindPersistenceReplay; corrected, JVM PASS; duplicates already covered |
| R39 Provider cancellation during reconciliation | RP + owner | Original cancellation, not typed error/success | Notify owner only if current; close waits provider child | RP.unexpectedReconciliationReadCancellationNotifiesOwnerWithOriginalCause / unexpectedReconciliationSelectCancellationIsNotSuccessfulRecovery / disposedReconciliationCannotSelectFromALateRestoredRoot; corrected, JVM PASS |
| R40 Programming defect + secondary cleanup | Host/lifetime | Original cause plus all secondary diagnostics | No conversion to provider/deletion success | RH.typedCleanupFailureAndOriginalExceptionBothRemainInspectable; existing + RL.earlierTypedLifecycleFailuresRemainAttachedWhenFinalOwnershipInspectionThrows; corrected, JVM PASS |

Additional cross-cutting regressions now pass: RP.lateFailedNavigatorResultCannotPublishRecoveryAfterPickerClosure;
RL.finalCloseOwnershipInspectionIsAwaitedOnceBeforeAnyReplacementCanBeAdmitted;
HI.graphDisposalWaitsForFinalFailedCloseOwnershipInspectionWithoutReopening. The before-edit
inventory retained I08–I10 Lint27 (including unused boundary ID and AppBundleLocaleChanges).
Final I11 Lint26 is explained below; those two named warnings and AAB delivery/switching NOT RUN
remain explicit Prompt9 items. No Gradle packaging change is authorized or made.


### I11 final implementation and verified local evidence

Implementation revision: `d3dd07171268ad5d9bd02919cbee4bb247dc5286`. The following documentation-only commit is the delivery
tip (`git log -1 --format=%H -- docs/integration/I_APP_MVP_HANDOFF.md`). Branch remains
`feat/app-mvp-integration-i`; no reset/rebase, push, PR, merge, Actions dispatch or Prompt9 work.
Author/committer: alexbonavila <alexbonavila44@gmail.com>. Matrix R01–R40 above identifies owners,
failures, recovery, cleanup/staleness, evidence and already-covered versus corrected behavior.
All referenced JVM suites pass; every Android/native/manual row remains NOT RUN.

**Exact changed files, all modified, no additions/deletions:**

- `app/src/main/java/com/abdev/partituraspdf/app/host/AppHost.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/ReaderHostLifetime.kt`
- `app/src/main/java/com/abdev/partituraspdf/app/host/RootPickerCoordinator.kt`
- `app/src/main/res/values/app.xml`
- `app/src/main/res/values-es/app.xml`
- `app/src/main/res/values-ca/app.xml`
- `app/src/main/res/values-en/app.xml`
- `app/src/test/java/com/abdev/partituraspdf/app/host/RootPickerCoordinatorTest.kt`
- `app/src/test/java/com/abdev/partituraspdf/app/host/ReaderLifecycleIntegrationTest.kt`
- `app/src/test/java/com/abdev/partituraspdf/app/host/ReaderHostIntegrationTest.kt`
- `app/src/androidTest/java/com/abdev/partituraspdf/app/integration/ReaderHostNativeIntegrationTest.kt`
- `app/src/androidTest/java/com/abdev/partituraspdf/app/integration/AppLocaleIntegrationTest.kt`
- `docs/integration/I_APP_MVP_HANDOFF.md`

### Confirmed gaps corrected, without a new recovery framework

1. **Uncertain selected-root persistence:** acceptResult's PRIVATE_STORAGE_UNAVAILABLE can follow
   a committed root write. The picker now enters its existing explicit recovery state. It does
   not claim rollback or keep accepting old-trail commands as if persistence were unchanged.
   User reconciliation re-reads SelectedRootRepository.restore/READ authority and selects the
   same navigator; it never repeats root persistence. Invalid URI/missing flags/denied access
   remain distinct non-write failures, and explicit cancellation still preserves the old root.
2. **Late picker publication/selection:** after suspended navigator selection, closed owners
   cannot change recovery state or complete F's operation. After suspended reconciliation read,
   closure is checked before any new navigator select. Already-started noncooperative writes/
   selections can finish in their old owner; no rollback is invented, and graph teardown joins
   users. Their results do not publish into a replacement host or authorize another operation.
3. **Unexpected reconciliation cancellation:** the same original CancellationException now
   reaches the existing interruption/owner-disposal path used by picker acceptance. No callback
   is delivered to an already closed owner. It is never recast as permission denial or success;
   ordinary typed failures remain contextual explicit retries, and programming defects propagate.
4. **Failed-close uncertainty lost on Back:** final exit previously cleared recoveryBlocked and
   a later accept reset the close guard. Final exit now retires G and joins its scope, then uses
   route.current only when a typed close failure requires ownership reconciliation. A still-owned
   session blocks arm/accept/registered restore; Library and Settings remain usable with a localized
   explanation. No repeated close, fake success, native reuse or automatic competing open occurs.
   If current() is empty, a fresh lifetime is allowed while the failed close acknowledgment remains
   reported. In production ReaderRouteOwner a typed close failure can be a checkpoint failure after
   its native-finally release; this inspection permits that safe discard without freezing the app.
   Null current is an absence of a borrowable route owner, not an independent claim of OS worker
   death. Existing engine identity/death rules remain authoritative. Inspection exceptions/cancellation
   remain original causes and block reuse. Mandatory inspection is awaited by Back/disposal; no
   arbitrary timeout, forced recycle, main-thread blocking wait or automatic restart is introduced.
5. **Secondary typed evidence:** merging a prior lifecycle report with a later exception could
   leave typed failures only in ReaderHostState, while terminal callers received only the cause.
   The complete typed report is now attached to the original cause unless that same diagnostic
   set is already attached. Original/secondary exceptions and typed stages remain inspectable;
   there is no false flush/durability acknowledgment or error-category conversion.

The previously unused app_reader_unavailable key is repurposed, with complete matching es/ca/en
text, for the explicit blocked-cleanup state. Its Library message is a polite live region and
contains no stack traces/locators/private identifiers. No resource IDs/format contracts are added
or removed;155 main resources remain. Existing generic/feature retry UI is reused elsewhere.

Existing F/G/H behavior supplies listing/registration/favorite retries, native/render recovery,
checkpoint sequencing, settings observation and uncertain-write reconciliation. I11 does not
reimplement them. A registered identity never proves current permission. Revocation is detected
at the next authoritative access boundary; an already acquired descriptor/copied native lease
may remain valid under Android ownership semantics. No fabricated revocation stream or unsafe
forced destruction of such a lease is introduced. Future opens/resume revalidation use existing
contracts. Access/provider failure never triggers registry deletion, favorite mutation or a
fallback write to legacy Room position metadata.

### Regression evidence and Android boundary

Sixteen new JVM methods: six picker, six lifetime and four F/host/G integration cases. They use
existing fixtures, real F/H/G state machines and deterministic coroutine gates. The new typed-open
matrix preserves every Access category and representative validation/unsupported/resource/conflict
values without implicit retry or registration/favorite writes. New disposal tests hold the failed-
close ownership read pending and prove graph closure cannot overtake it. Existing I01–I10, F/G/H,
SAF, checkpoint, writer epoch, restoration and presentation regressions remain enabled.

Before production fixes, the targeted command below executed58 methods with10 failures. Those
failures demonstrate the host gaps; the final full suite is green. Two new cases already passed
because the safe-discard/closed-owner behavior they protect was partly present. A first Android
compile also exposed an incorrect new test resource name, corrected to the existing
reader_error_permission; no resource or validation was weakened to compile.

```bash
bash ./gradlew :app:testDebugUnitTest --tests '*RootPickerCoordinatorTest' \
  --tests '*ReaderLifecycleIntegrationTest' --max-workers=4 --console=plain
```

Four new Android methods compile as public JUnit4 void methods:

- `ReaderHostNativeIntegrationTest.realWorkerLossUsesExistingReaderRetryWithoutDeletingRegistrationFavoriteOrCommittedPage`:
  retires only the test engine's worker through its existing Binder protocol after exact PID,
  start-time and epoch checks, awaits Binder death, asserts STALE_SESSION, uses G retry, verifies
  a fresh native session and preserved real Room favorite/identity/committed position. It never
  kills an arbitrary PID or changes worker/AIDL code. This is prepared **real native execution**,
  but has NOT RUN here.
- `injectedProviderUnavailableIsContextualAndRetryRevalidatesBeforeRealNativeOpen`: explicit contract
  injection, existing localized retry, then real isolated engine if executed. Not a real SAF outage.
- `injectedCheckpointFailurePreservesRealEarlierPositionAndSafeReopenAfterEmptyRouteVerification`:
  injected write failure over real position storage/Room/native presentation. Real earlier page0
  remains authoritative; later actually presented page1 is not claimed durable. Back verifies
  the empty route, a fresh Reader restores page0, and only the two exact asserted expected
  ReaderExitException diagnostics are removed from the test's unexpected-failure ledger. Existing
  teardown assertions remain intact. This does not simulate physical disk failure.
- `AppLocaleIntegrationTest.contextualRecoveryMessagesStayTranslatedAndDistinctWithoutExposingTechnicalPaths`:
  actual es/ca/en resource contexts distinguish denied/revoked/provider/I/O/deletion/password and
  blocked-cleanup messages; this is not an executed UI/permission acceptance claim.

All existing Android tests still compile, including test-provider reads, real persisted-grant
queries, native isolation, graphical detach, background/recreation, settings and synthetic Bundle
reconstruction. Genuine DocumentsUI authorization, acquired-grant revocation, provider outage,
worker/surface execution and system process death remain NOT RUN; synthetic results/injected
contracts and compilation are never described as their PASS evidence.

### Exact local validation, coverage and limitations

After `source /workspace/partituras-tools/activate.sh` for SDK/JDK tools:

```bash
bash ./gradlew -I /tmp/i11-coverage.init.gradle \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  :app:testDebugUnitTest --rerun :app:i11Verify :app:i11ReaderCoverage :app:i11AllCoverage \
  --no-configuration-cache --max-workers=4 --console=plain
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest \
  --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
adb devices
git diff --check
```

| Evidence | Actual final local result |
| --- | --- |
| Debug and Android-test APK build | PASS; new/old instrumentation compiled, not executed |
| JVM JUnit | **620 executed, 0 failures/errors/skips** (I08–I10 604 +16) |
| Python / localization negative-control infrastructure | **289 PASS** |
| Localization | PASS:155 main IDs, Spanish default/es match, complete ca/en/format parity |
| Immutable canonical corpus | **15 PASS**, hash/structure/render verification unchanged |
| APK ZIP PDF inventory | Debug0; instrumentation15 canonical fixtures |
| Android source inventory | 407 standard +14 assigned diagnostics =421; source counts, not executions |
| Lint | **0 errors,26 warnings**, classification below |
| Android API26/27/36, native/worker/graphics/real grants/process kill | **NOT RUN**; adb devices empty, no emulator created/adopted/modified |
| Hosted CI | **NOT RUN**; owner-reported outage, no investigation/repair/dispatch/workflow changes |

Existing JaCoCo0.8.14 reads actual JVM execution and debug classes with nested/coroutine/data
classes included, no hard recovery path removed. Separate affected scope prevents unrelated
navigation/presentation coverage masking the new recovery logic:

| Source / scope | LINE | BRANCH |
| --- | --- | --- |
| ReaderHostLifetime.kt | **189/189 (100%)** | **190/214 (88.79%)** |
| RootPickerCoordinator.kt | **85/85 (100%)** | **80/86 (93.02%)** |
| Both affected recovery sources | **274/274 (100%)** | **270/300 (90%)** |
| Broader testable host/navigation scope |600/603 (99.50%) |689/786 (87.66%) |
| Unfiltered host/navigation/locale/MainActivity including Android/Compose |601/862 (69.72%) |689/962 (71.62%) |

Each affected source individually and the affected group exceed90% LINE/80% BRANCH. The broad
report is supporting regression evidence only. AppHost/resource/Android Context/Window/worker/
surface behavior is excluded from JVM business certification because it requires Android; the
unfiltered row exposes those misses rather than hiding them. No combined Android/native coverage
or final phase acceptance is claimed. Logs `/tmp/i11-before.log`, `/tmp/i11-final-validation.log`,
`/tmp/i11-python.log`, `/tmp/i11-fixtures.log` and XML/HTML reports are ephemeral and not committed.

I08–I10 reported27 Lint warnings. I11 now uses app_reader_unavailable for real recovery, resolving
that old unused-ID warning through authorized functionality, not suppression/deletion. The
**unused obsolete app_integration_boundary** and **AppBundleLocaleChanges** warnings introduced
in I08–I10 both remain. The universal APK contains complete translations; AAB language splitting,
installation/delivery and runtime system-language switching are NOT certified. No packaging,
Play Core/dependency, gate or policy change is authorized or made here. Prompt9 must keep both
warnings and AAB delivery as explicit acceptance items. Existing deprecation/compiler warnings
are not suppressed; no unrelated protected test code is changed to clean them up.

### Prompt9 prioritized acceptance checklist — preparation only

This is an explicit handoff, not implementation of Prompt9, a waiver or a merge recommendation.

1. **P0 / integration gaps:** review R01–R40 against actual I01–I11 behavior, especially retained
   close-owner blocking versus verified empty-owner safe discard and root-write uncertainty.
   No known uncorrected host defect is demonstrated by local tests; runtime evidence is incomplete.
2. **P0 / API26:** execute the complete latest-revision standard instrumentation and assigned native
   diagnostics under existing policy; inspect real JUnit inventory/counts/failures/skips, not APK build.
3. **P0 / API27:** obtain independent latest-revision compatibility execution and artifacts.
4. **P0 / API36:** obtain independent latest-revision compatibility execution and artifacts.
5. **Required physical acceptance:** owner-authorized physical Android8.1 tablet only; no personal
   data wipe/adoption. Record model/build/revision, screenshots and real outcomes.
6. **P0 / real SAF:** authentic picker READ grants, cancellation/replacement, nested access,
   persisted-grant revalidation and actual revocation/provider outages; preserve favorites/identity.
7. **P0 / native/graphics:** real canonical rendering, actual consumer release, owned worker death/
   retry and resource/session cleanup. Do not substitute injected AppErrors or mock grants.
8. **P0 / lifecycle:** background/resume/Back, real and clearly simulated memory trims, repeated
   Activity recreation during pending work, stale callback rejection and window clearing.
9. **P0 / process death:** eligible system snapshot -> real kill/relaunch in a disposable authorized
   environment; distinguish Bundle simulation, force-stop, task removal and unsaved termination.
10. **P0 / effects/locales:** actual observed theme/fit, uncertain writes, local fit distinction,
    system night/language changes, unsupported primary fallback, keyboard/TalkBack/large-font/insets.
11. **Release-format decision:** AAB language splits/delivery and language switching need owner-
    approved packaging scope if applicable. Current assignment does not alter Gradle packaging.
12. **Lint disposition:** review both retained I08–I10 warnings explicitly; no blanket suppression.
13. **Corpus/packaging:** reverify all15 hashes/structures and final production APK/AAB/split assets;
    fixture PDFs must remain test-only, and inspected language delivery must match artifact type.
14. **Scoped coverage:** reconcile actual JVM plus authorized Android measurements for affected
    sources; no project average may hide recovery gaps, no fabricated worker coverage.
15. **Docs/CI gates:** reconcile historical versus final evidence, execute all mandatory latest-head
    checks when available and retain actual logs/URLs/counts; do not repair platform outage in I11.
16. **Owner review / PR:** prepare final inventory, unresolved decisions and requested PR only with
    owner authorization. No push/PR/merge occurred here. Human owner decides phase acceptance.

Confirmed corrected defects are the five host gaps above and their10 failing-before regressions.
No protected-source exception was required; F/G/H, public contracts, persistence/schema, SAF/PDF/
worker/AIDL, Gradle/dependencies, CI/test policy and immutable fixtures remain unchanged. Remaining
limitations are environmental/unexecuted Android or manual/release-format evidence, not fabricated
PASS or independently demonstrated new production failures. Proposed protected changes: none in
I11; any later AAB configuration or worker/storage correction needs separate approval and evidence.
No backup/reinstall/restore, D2D, profiling or performance benchmark was executed. Stop after I11.

### Reproducing I11 scoped coverage

Save this tested external script as `/tmp/i11-coverage.init.gradle`. i11ReaderCoverage selects
both affected recovery sources (`reader.xml`); report.xml is broader logic and all.xml includes
Android/Compose misses. Run the final command above. No repository build or CI file is changed.

```groovy
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
gradle.beforeProject { p ->
 if (p.path == ':app') p.plugins.withId('com.android.application') { p.afterEvaluate {
  def classes=p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
   include 'com/abdev/partituraspdf/app/host/AppHostViewModel*'
   include 'com/abdev/partituraspdf/app/host/AppHostState*'
   include 'com/abdev/partituraspdf/app/host/AppPresentationState*'
   include 'com/abdev/partituraspdf/app/host/AppHostFeatures*'
   include 'com/abdev/partituraspdf/app/host/AppHostSession*'
   include 'com/abdev/partituraspdf/app/host/RootPickerCoordinator*'
   include 'com/abdev/partituraspdf/app/host/Reader*'
   include 'com/abdev/partituraspdf/app/host/AndroidReaderLifecycle*'
   include 'com/abdev/partituraspdf/app/navigation/**'
  }
  def data=p.layout.buildDirectory.file('jacoco/testDebugUnitTest.exec')
  def report=p.tasks.register('i11Coverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports { xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i11-coverage/report.xml'))
    html.outputLocation.set(new File('/tmp/i11-coverage/html')) }
   doFirst { if(classes.files.empty || !data.get().asFile.isFile()) throw new GradleException('Missing real host classes/JVM execution') }
  }
  p.tasks.register('i11ReaderCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/Reader*'
    include 'com/abdev/partituraspdf/app/host/RootPickerCoordinator*'

   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i11-coverage/reader.xml'))
    html.outputLocation.set(new File('/tmp/i11-coverage/reader-html'))}
  }
  p.tasks.register('i11AllCoverage',JacocoReport) {
   dependsOn 'testDebugUnitTest'
   classDirectories.setFrom(p.fileTree(p.layout.buildDirectory.dir('intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
    include 'com/abdev/partituraspdf/app/host/**', 'com/abdev/partituraspdf/app/navigation/**', 'com/abdev/partituraspdf/app/localization/**', 'com/abdev/partituraspdf/MainActivity*'
   })
   sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   outputs.upToDateWhen {false}
   reports {xml.required.set(true);html.required.set(true)
    xml.outputLocation.set(new File('/tmp/i11-coverage/all.xml'))
    html.outputLocation.set(new File('/tmp/i11-coverage/all-html'))}
  }
  p.tasks.register('i11Verify',JacocoCoverageVerification) {
   dependsOn report
   classDirectories.setFrom(classes);sourceDirectories.setFrom(p.file('src/main/java'));executionData.setFrom(data)
   violationRules { rule { limit {counter='LINE';minimum=0.90};limit {counter='BRANCH';minimum=0.80} } }
  }
 } }
}
```


## I12 — Final audit and delivery preparation (2026-10-20)

Starting/tested source revision: `f51cc42df996390cf7e6aacf6cff85e004c9ca17`, clean branch
`feat/app-mvp-integration-i`. Fetch succeeded; latest origin/main and original merge base both
`cd86db5c4f70201cfbe6970de20cdb3308a970d0`. No drift/synchronization, rebase, merge, push,
PR creation or Actions dispatch. All14 Phase I commits and38 changed paths reviewed.
Final audit delivery SHA: `git log -1 --format=%H -- docs/acceptance/I_APP_MVP_ACCEPTANCE.md`.
I12 changes documentation only; source/test/build trees stay identical to the tested revision.

### Final source and lifecycle ownership

I01–I02 AppHostFactory/ProductionHostSession and retained AppHostViewModel own one graph, F/H
controllers, shared F/Foundation navigator and attempt scope. I03–I04 RootPickerBinding forwards
actual Android result values to RootPickerCoordinator, single SafRootSelection persistence and
same-navigator synchronization. F retains bounded browse, explicit registration/favorites and
registered/transient intent classification. I05 ReaderHostLifetime grants one current row/restore
permit to G; no host duplicate route.open. I06 serializes background/low/critical/resume with G's
native/frame barriers; terminal Back joins cleanup before Library refresh/graph teardown. I07
restores bounded registered logical navigation only when eligible saved state and authoritative
root/preferences permit; SharedPreferences committed checkpoints remain sole page authority.
I08–I10 observe H's StateFlow, apply confirmed theme/fit, primary-device locale before resource
reads and Activity-owned Reader-only Window flags. I11 R01–R40 recovery and original diagnostics
remain; failed close is reconciled after G retires, never treated as success by resetting guards.

Only Activity/composition holds Window, registry and graphical consumer references. Configuration
recreation preserves ViewModel/graph/controllers and replaces the consumer; process death creates
new owners/access/native state. Worker remains nonexported, isolated and separate from graph
startup. No new dependency/module/manifest/Gradle/CI/shared-contract/persistence/worker/fixture
change is introduced. The original Phase I shared test assertion147→155 remains owner-authorized.

### Fresh final evidence and open requirements

[Acceptance register](../acceptance/I_APP_MVP_ACCEPTANCE.md) is the single final numerical and
requirement register, preserving all earlier dated results above. It records:

- PASS debug/release APKs (R8) and Android-test APK compilation;620 JVM0 failures/errors/skips;
  289 Python; complete155 main/20 debug resource IDs per locale;15 immutable fixture checks.
- Established `python3 scripts/local_e2e.py run --suite host` PASS, original fresh JVM XML/JSON/JUnit/
  HTML retained in run `20261019T224739Z-3ff2dc38f0f546a991ea7a3466d22081`. It executed no Android tests.
- JaCoCo0.8.14 source counters independently meet90% lines/80% branches for every included host/
  navigation logic source. AppHostViewModel259/262 lines,327/386 branches; ReaderHostLifetime189/189,
  190/214; RootPickerCoordinator85/85,80/86; presentation policy20/20,41/49; Android mapping7/7,7/7;
  navigation13/13,16/16; restoration16/16,28/28; host state11/11,0 branches.
- Aggregate600/603 lines99.50%,689/786 branches87.66%. Unfiltered Android-inclusive report601/862,
  689/950 is not combined/runtime coverage. Fresh Compose generated branch counts differ from I11
  by12 uncovered branches with unchanged sources/gated counters; no improvement is claimed from it.
- Source/test APK inventory421=407 standard+14 assigned diagnostics;24 standard isolation tests,
  29 controlled diagnostic invocations unchanged. All46 Phase I Android additions compile; NOT RUN.
- Actual debug/release production APKs and inspected debug AAB contain0 PDFs; test APK contains15
  exact canonical hashes. Compiled Policy B debug/release rules verified, including optimized paths.
- Lint0 errors/26 warnings:24 historical +unused obsolete banner +AppBundleLocaleChanges. Complete
  per-category dispositions retained in the register; no suppression/removal/dependency upgrade.
- Actual cached bundletool1.18.3 built AAB/APK-set and extracted en/ca/es API26 synthetic device
  selections. Initial en set contains base-en and Spanish-default master, no base-ca; inverse
  selections differ. All languages are in the bundle, not necessarily in an initial install set.
  Real delivery/system-language switching NOT RUN. Proposed minimal disable-language-splits
  correction needs separate owner Gradle approval; no packaging or delivery-client change made.
- API26/27/36, real SAF/native/Surface/process death, physical Android8.1, TalkBack/manual/linguistic
  acceptance NOT RUN. Doctor BLOCKED: no KVM, dedicated AVD UNPROVISIONED, no attached device.
  No emulator created/started/adopted or device data/grants changed. Hosted API access proxy403;
  all seven final-revision checks NOT RUN, no historical CI substitution or outage investigation.

I-T16 is PASS for production fixture exclusion. I-T01–I-T15/I-T17 retain NOT RUN final combined
acceptance because their mandatory Android/manual evidence is missing, despite passing logic.
The register provides the exact original criteria, production/test mapping, API reproduction,
20-action physical-tablet evidence table, real SAF/process-kill distinctions and current CI needs.
No newly demonstrated critical integration defect required a correction. Missing device evidence
is a blocker, not an invented code failure. AAB split delivery remains a concrete owner decision.

### Local review material and stopping point

New [prepared PR description](I_APP_MVP_PR_DESCRIPTION.md) contains title, complete branch scope,
commit/file inventory, validation, APIs and acceptance blockers. Other changed files: README,
PROJECT_STATUS, ROADMAP, this handoff and the new acceptance register (six documentation paths
in total; no files deleted). Historical snapshots remain labeled and retained. AGENTS/governance
status correction requires a separately approved task and was not made here.

**Ready for owner publication/review, acceptance pending** means review preparation only. No
merge-readiness, physical/device/backup/performance or full security certification follows.
Owner must approve packaging scope and publication separately, obtain latest-revision mandatory
CI/API26/API27/API36/manual evidence and decide formal acceptance. No next phase is started.
