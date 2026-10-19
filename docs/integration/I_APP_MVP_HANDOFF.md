# Phase I application integration — I03/I04 review handoff

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
