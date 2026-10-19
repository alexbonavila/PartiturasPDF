# F/G/H source-attributed coverage — 2026-10-19

Baseline: `adcca2bd07693e0d985eee1c611bc931be1dfed1`.
Measurements are from actual JVM JUnit execution, not mocked input or Android execution.
The closeout index records the tested source revision and current execution counts.
Tool: existing **JaCoCo 0.8.14**, Gradle 9.8.0, JDK25, Linux x86_64.
No shared Gradle/CI gate, dependency, threshold or production logic is changed.

## Reproduction

Save the exact external init script below outside the repository. Run from the checkout:

```bash
bash ./gradlew :app:testDebugUnitTest --rerun --console=plain --max-workers=4
bash ./gradlew -I /tmp/pre-i-fgh-coverage.init.gradle \
 :app:libraryAllCloseoutReport :app:readerAllCloseoutReport :app:settingsAllCloseoutReport \
 :app:libraryLogicCloseoutReport :app:readerLogicCloseoutReport :app:settingsLogicCloseoutReport \
 :app:libraryLogicCloseoutVerify :app:readerLogicCloseoutVerify :app:settingsLogicCloseoutVerify \
 --no-configuration-cache --console=plain --max-workers=4
```

Original class root: `app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`.
Sources: `app/src/main/java`. Genuine execution: `app/build/jacoco/testDebugUnitTest.exec`.
JUnit XML: `app/build/test-results/testDebugUnitTest`. XML reports: `/tmp/pre-i-coverage/<scope>.xml`;
HTML: `/tmp/pre-i-coverage/<scope>-html/index.html`. All are local generated artifacts, not Git inputs.
The six reports expose both whole-feature and nonvisual-logic denominators independently.
All/logic source attribution, exact class inventories and source hashes follow below.

## Scope rationale

Logic includes all handwritten controller, action/state, listing/registration/favorite/open,
frame/presentation lifetime, render planning, gesture/transform and recovery logic, including
nested/coroutine classes. ReaderPresenter/ReaderFrame are retained even though they interact
with graphical capabilities. No logic was excluded for low coverage. Standard JaCoCo Kotlin
compiler filters remain unchanged. Each feature is verified independently at 90% lines/80% branches.

Only Compose/Android presentation and tooling are outside the JVM logic gate: LibraryScreen,
LibraryScreenPreviews; ReaderScreen, ReaderPageSurface, ReaderScreenPreviews; SettingsScreen,
SettingsScreenPreviews. Their generated companions and preview providers are likewise excluded
from the logic projection, but remain in **All**. They require real Android probes; their JVM-only
misses are not Android coverage measurements. Review their runtime assertions on API26/27/36.
No requirement is waived and there is no full UI/feature coverage PASS from these projections.

No additional F/G/H behavior tests were needed to meet the measured business-logic minimums.
The separately added locale tests validate the resource migration and existing locale policy.
Worker-process coverage is outside these three feature packages, explicitly investigated in
[PDF_WORKER_COVERAGE_FEASIBILITY.md](PDF_WORKER_COVERAGE_FEASIBILITY.md), not silently certified.
The existing local coverage runner's F/G/H package counters are informational and require real
Android inputs; the external JVM projection here does not change or replace that runner.

## Exact external script

```groovy
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
// External closeout consumer of the existing JaCoCo JVM execution, no shared gate changes.
gradle.beforeProject { project ->
    if (project.path == ':app') {
        project.plugins.withId('com.android.application') {
            project.afterEvaluate {
                ['library', 'reader', 'settings'].each { feature ->
                    [false, true].each { logic ->
                        def label = feature + (logic ? 'Logic' : 'All')
                        def classes = project.fileTree(project.layout.buildDirectory.dir(
                            'intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes')) {
                            include "com/abdev/partituraspdf/${feature}/**/*.class"
                            if (logic) {
                                // Android presentation and tooling need real Android execution.
                                exclude '**/*Screen*', '**/*Preview*', '**/ReaderPageSurface*'
                            }
                        }
                        def report = project.tasks.register(label + 'CloseoutReport', JacocoReport) {
                            dependsOn 'testDebugUnitTest'
                            classDirectories.setFrom(classes)
                            sourceDirectories.setFrom(project.file('src/main/java'))
                            executionData.setFrom(project.layout.buildDirectory.file('jacoco/testDebugUnitTest.exec'))
                            outputs.upToDateWhen { false }
                            reports {
                                xml.required.set(true); html.required.set(true)
                                xml.outputLocation.set(new File('/tmp/pre-i-coverage/' + label + '.xml'))
                                html.outputLocation.set(new File('/tmp/pre-i-coverage/' + label + '-html'))
                            }
                            doFirst {
                                if (classes.files.empty) throw new GradleException('Empty feature class inventory')
                            }
                        }
                        if (logic) {
                            project.tasks.register(label + 'CloseoutVerify', JacocoCoverageVerification) {
                                dependsOn report
                                classDirectories.setFrom(classes)
                                sourceDirectories.setFrom(project.file('src/main/java'))
                                executionData.setFrom(project.layout.buildDirectory.file('jacoco/testDebugUnitTest.exec'))
                                violationRules { rule {
                                    limit { counter = 'LINE'; minimum = 0.90 }
                                    limit { counter = 'BRANCH'; minimum = 0.80 }
                                } }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

## Measured counters and provenance

Measured source/test tree: `46c1b117dcee26902e675426755de551bbb68e01` (later commits are documentation only).

| Scope | Classes reported | LINE covered/total | BRANCH covered/total | Interpretation |
|---|---:|---|---|---|
| libraryLogic | 97 | 451/451 — 100.00% | 410/466 — 87.98% | PASS scoped JVM logic thresholds |
| readerLogic | 51 | 478/486 — 98.35% | 510/590 — 86.44% | PASS scoped JVM logic thresholds |
| settingsLogic | 24 | 167/169 — 98.82% | 158/170 — 92.94% | PASS scoped JVM logic thresholds |
| libraryAll | 104 | 451/640 — 70.47% | 410/720 — 56.94% | JVM only, Android presentation NOT RUN |
| readerAll | 62 | 478/725 — 65.93% | 510/900 — 56.67% | JVM only, Android presentation NOT RUN |
| settingsAll | 28 | 167/340 — 49.12% | 158/243 — 65.02% | JVM only, Android presentation NOT RUN |

The current Reader result 478/486 lines, 510/590 branches is newly measured on the merged
implementation, not copied from its historical 507/590 branch report. No Android `.ec` was
available; no combined or UI percentage is asserted. The All counters are real JVM-only
reports, not zero Android coverage. Each logic verification task executed successfully.

### Exact class/source attribution


#### libraryLogic

| Source | LINE covered/total | BRANCH covered/total |
|---|---|---|
| LibraryPresentation.kt | 29/29 | 36/36 |
| LibraryController.kt | 190/190 | 194/210 |
| LibraryUiState.kt | 40/40 | 13/14 |
| LibraryDocuments.kt | 178/178 | 167/206 |
| LibraryAction.kt | 14/14 | 0/0 |
| Comparisons.kt | 0/0 | 0/0 |

All reported JVM logic classes (including generated nested/coroutine classes):

```text
com/abdev/partituraspdf/library/LibraryAction
com/abdev/partituraspdf/library/LibraryAction$Back
com/abdev/partituraspdf/library/LibraryAction$ChooseRoot
com/abdev/partituraspdf/library/LibraryAction$Document
com/abdev/partituraspdf/library/LibraryAction$Enter
com/abdev/partituraspdf/library/LibraryAction$Open
com/abdev/partituraspdf/library/LibraryAction$Refresh
com/abdev/partituraspdf/library/LibraryAction$Register
com/abdev/partituraspdf/library/LibraryAction$Retry
com/abdev/partituraspdf/library/LibraryAction$RetryFavorite
com/abdev/partituraspdf/library/LibraryAction$RetryLookup
com/abdev/partituraspdf/library/LibraryAction$RetryMetadata
com/abdev/partituraspdf/library/LibraryAction$RetryOpen
com/abdev/partituraspdf/library/LibraryAction$RetryRegistration
com/abdev/partituraspdf/library/LibraryAction$SetFavorite
com/abdev/partituraspdf/library/LibraryAssociation
com/abdev/partituraspdf/library/LibraryAssociation$Failed
com/abdev/partituraspdf/library/LibraryAssociation$LookingUp
com/abdev/partituraspdf/library/LibraryAssociation$Registered
com/abdev/partituraspdf/library/LibraryAssociation$Registering
com/abdev/partituraspdf/library/LibraryAssociation$Unregistered
com/abdev/partituraspdf/library/LibraryAssociationOperation
com/abdev/partituraspdf/library/LibraryContent
com/abdev/partituraspdf/library/LibraryContent$Failed
com/abdev/partituraspdf/library/LibraryContent$Loading
com/abdev/partituraspdf/library/LibraryContent$NotLoaded
com/abdev/partituraspdf/library/LibraryContent$Ready
com/abdev/partituraspdf/library/LibraryController
com/abdev/partituraspdf/library/LibraryController$Message
com/abdev/partituraspdf/library/LibraryController$Message$Action
com/abdev/partituraspdf/library/LibraryController$Message$Document
com/abdev/partituraspdf/library/LibraryController$Message$Loaded
com/abdev/partituraspdf/library/LibraryController$Message$Selection
com/abdev/partituraspdf/library/LibraryController$beginLoad$1
com/abdev/partituraspdf/library/LibraryController$chooseRoot$1
com/abdev/partituraspdf/library/LibraryController$documentAction$1
com/abdev/partituraspdf/library/LibraryController$documentResult$1
com/abdev/partituraspdf/library/LibraryController$enter$1
com/abdev/partituraspdf/library/LibraryController$initialize$1
com/abdev/partituraspdf/library/LibraryController$loaded$1
com/abdev/partituraspdf/library/LibraryController$navigate$1
com/abdev/partituraspdf/library/LibraryController$navigateBack$1
com/abdev/partituraspdf/library/LibraryController$refresh$1
com/abdev/partituraspdf/library/LibraryController$selection$1
com/abdev/partituraspdf/library/LibraryController$selectionFailure$1
com/abdev/partituraspdf/library/LibraryController$start$1$1
com/abdev/partituraspdf/library/LibraryDocuments
com/abdev/partituraspdf/library/LibraryDocuments$lookup$1
com/abdev/partituraspdf/library/LibraryDocuments$lookup$2$1
com/abdev/partituraspdf/library/LibraryDocuments$observe$1
com/abdev/partituraspdf/library/LibraryDocuments$observe$1$1
com/abdev/partituraspdf/library/LibraryDocuments$open$1
com/abdev/partituraspdf/library/LibraryDocuments$register$1
com/abdev/partituraspdf/library/LibraryDocuments$register$1$result$1
com/abdev/partituraspdf/library/LibraryDocuments$retryLookup$1
com/abdev/partituraspdf/library/LibraryDocuments$setFavorite$1
com/abdev/partituraspdf/library/LibraryDocuments$setFavorite$1$result$1
com/abdev/partituraspdf/library/LibraryDocuments$start$2$1
com/abdev/partituraspdf/library/LibraryErrorCategory
com/abdev/partituraspdf/library/LibraryFailure
com/abdev/partituraspdf/library/LibraryFailureOperation
com/abdev/partituraspdf/library/LibraryFavorite
com/abdev/partituraspdf/library/LibraryFavorite$Failed
com/abdev/partituraspdf/library/LibraryFavorite$Idle
com/abdev/partituraspdf/library/LibraryFavorite$Pending
com/abdev/partituraspdf/library/LibraryFavorite$Reconciling
com/abdev/partituraspdf/library/LibraryLocation
com/abdev/partituraspdf/library/LibraryLocation$Available
com/abdev/partituraspdf/library/LibraryLocation$Failed
com/abdev/partituraspdf/library/LibraryLocation$Initializing
com/abdev/partituraspdf/library/LibraryLocation$NoRoot
com/abdev/partituraspdf/library/LibraryOpening
com/abdev/partituraspdf/library/LibraryOpening$Accepted
com/abdev/partituraspdf/library/LibraryOpening$Failed
com/abdev/partituraspdf/library/LibraryOpening$Idle
com/abdev/partituraspdf/library/LibraryOpening$Pending
com/abdev/partituraspdf/library/LibraryOpening$Rejected
com/abdev/partituraspdf/library/LibraryOperation
com/abdev/partituraspdf/library/LibraryOperation$Canceled
com/abdev/partituraspdf/library/LibraryOperation$Failed
com/abdev/partituraspdf/library/LibraryOperation$Idle
com/abdev/partituraspdf/library/LibraryOperation$Pending
com/abdev/partituraspdf/library/LibraryPresentationKt
com/abdev/partituraspdf/library/LibraryPresentationKt$prepareLibraryListing$$inlined$compareBy$1
com/abdev/partituraspdf/library/LibraryPresentationKt$prepareLibraryListing$$inlined$thenBy$1
com/abdev/partituraspdf/library/LibraryPresentationKt$prepareLibraryListing$$inlined$thenBy$2
com/abdev/partituraspdf/library/LibraryPresentationKt$prepareLibraryListing$$inlined$thenBy$3
com/abdev/partituraspdf/library/LibraryPresentationKt$prepareLibraryListing$$inlined$thenBy$4
com/abdev/partituraspdf/library/LibraryPresentationKt$prepareLibraryListing$$inlined$thenBy$5
com/abdev/partituraspdf/library/LibraryRow
com/abdev/partituraspdf/library/LibraryRow$Directory
com/abdev/partituraspdf/library/LibraryRow$Pdf
com/abdev/partituraspdf/library/LibraryRowKey
com/abdev/partituraspdf/library/LibraryUiState
com/abdev/partituraspdf/library/OperationLocks
com/abdev/partituraspdf/library/OperationLocks$Entry
com/abdev/partituraspdf/library/OperationLocks$run$1
```

#### readerLogic

| Source | LINE covered/total | BRANCH covered/total |
|---|---|---|
| ReaderPresenter.kt | 25/25 | 43/52 |
| ReaderUiState.kt | 29/29 | 10/10 |
| ReaderInteraction.kt | 31/32 | 85/86 |
| ReaderRecovery.kt | 18/18 | 44/45 |
| ReaderRenderPlan.kt | 19/19 | 14/16 |
| ReaderFrame.kt | 32/35 | 17/22 |
| ReaderController.kt | 324/328 | 297/359 |

All reported JVM logic classes (including generated nested/coroutine classes):

```text
com/abdev/partituraspdf/reader/ReaderAction
com/abdev/partituraspdf/reader/ReaderAction$Fit
com/abdev/partituraspdf/reader/ReaderAction$Page
com/abdev/partituraspdf/reader/ReaderAction$Presented
com/abdev/partituraspdf/reader/ReaderAction$Retry
com/abdev/partituraspdf/reader/ReaderAction$Step
com/abdev/partituraspdf/reader/ReaderAction$Viewport
com/abdev/partituraspdf/reader/ReaderCheckpoint
com/abdev/partituraspdf/reader/ReaderCheckpoint$Canceled
com/abdev/partituraspdf/reader/ReaderCheckpoint$None
com/abdev/partituraspdf/reader/ReaderCheckpoint$Scheduling
com/abdev/partituraspdf/reader/ReaderCheckpoint$Unconfirmed
com/abdev/partituraspdf/reader/ReaderController
com/abdev/partituraspdf/reader/ReaderController$Kind
com/abdev/partituraspdf/reader/ReaderController$Work
com/abdev/partituraspdf/reader/ReaderController$Work$Ack
com/abdev/partituraspdf/reader/ReaderController$Work$Check
com/abdev/partituraspdf/reader/ReaderController$Work$Open
com/abdev/partituraspdf/reader/ReaderController$Work$Release
com/abdev/partituraspdf/reader/ReaderController$Work$Render
com/abdev/partituraspdf/reader/ReaderController$Work$Stop
com/abdev/partituraspdf/reader/ReaderController$execute$1
com/abdev/partituraspdf/reader/ReaderController$execute$result$1
com/abdev/partituraspdf/reader/ReaderController$execute$result$2
com/abdev/partituraspdf/reader/ReaderController$execute$result$3
com/abdev/partituraspdf/reader/ReaderController$perform$1
com/abdev/partituraspdf/reader/ReaderController$perform$4
com/abdev/partituraspdf/reader/ReaderController$perform$task$1
com/abdev/partituraspdf/reader/ReaderController$quiesce$1
com/abdev/partituraspdf/reader/ReaderController$render$1
com/abdev/partituraspdf/reader/ReaderController$render$result$2
com/abdev/partituraspdf/reader/ReaderController$retire$1
com/abdev/partituraspdf/reader/ReaderController$runWorker$1
com/abdev/partituraspdf/reader/ReaderController$runWorker$4
com/abdev/partituraspdf/reader/ReaderController$worker$1
com/abdev/partituraspdf/reader/ReaderFailure
com/abdev/partituraspdf/reader/ReaderFrame
com/abdev/partituraspdf/reader/ReaderGestureGate
com/abdev/partituraspdf/reader/ReaderImageLayout
com/abdev/partituraspdf/reader/ReaderInteractionKt
com/abdev/partituraspdf/reader/ReaderOpenIntent
com/abdev/partituraspdf/reader/ReaderPhase
com/abdev/partituraspdf/reader/ReaderPresentation
com/abdev/partituraspdf/reader/ReaderPresenter
com/abdev/partituraspdf/reader/ReaderRecoveryKt
com/abdev/partituraspdf/reader/ReaderRenderPlanKt
com/abdev/partituraspdf/reader/ReaderRetry
com/abdev/partituraspdf/reader/ReaderStage
com/abdev/partituraspdf/reader/ReaderTransform
com/abdev/partituraspdf/reader/ReaderUiState
com/abdev/partituraspdf/reader/ReaderViewport
```

#### settingsLogic

| Source | LINE covered/total | BRANCH covered/total |
|---|---|---|
| SettingsController.kt | 155/157 | 144/156 |
| SettingsAction.kt | 2/2 | 0/0 |
| SettingsUiState.kt | 10/10 | 14/14 |

All reported JVM logic classes (including generated nested/coroutine classes):

```text
com/abdev/partituraspdf/settings/SettingsAction
com/abdev/partituraspdf/settings/SettingsAction$RetryFit
com/abdev/partituraspdf/settings/SettingsAction$RetryObservation
com/abdev/partituraspdf/settings/SettingsAction$RetryTheme
com/abdev/partituraspdf/settings/SettingsAction$SelectFit
com/abdev/partituraspdf/settings/SettingsAction$SelectTheme
com/abdev/partituraspdf/settings/SettingsController
com/abdev/partituraspdf/settings/SettingsController$Field
com/abdev/partituraspdf/settings/SettingsController$Intent
com/abdev/partituraspdf/settings/SettingsController$Write
com/abdev/partituraspdf/settings/SettingsController$guarded$1
com/abdev/partituraspdf/settings/SettingsController$observe$1
com/abdev/partituraspdf/settings/SettingsController$observe$2$1
com/abdev/partituraspdf/settings/SettingsController$observe$2$1$1
com/abdev/partituraspdf/settings/SettingsController$start$1$1
com/abdev/partituraspdf/settings/SettingsController$start$1$2
com/abdev/partituraspdf/settings/SettingsController$write$1
com/abdev/partituraspdf/settings/SettingsController$write$task$1
com/abdev/partituraspdf/settings/SettingsOperation
com/abdev/partituraspdf/settings/SettingsOperation$Failed
com/abdev/partituraspdf/settings/SettingsOperation$Idle
com/abdev/partituraspdf/settings/SettingsOperation$Pending
com/abdev/partituraspdf/settings/SettingsOperation$Reconciling
com/abdev/partituraspdf/settings/SettingsUiState
```

### Production source SHA-256 inventory (including Android presentation/tooling)

| Source | SHA-256 | Classification |
|---|---|---|
| library/LibraryAction.kt | `639c1c861dc0b7f28341912d8a099dc730ba6c84b55eff8a3fd643e42a7765e7` | JVM logic |
| library/LibraryController.kt | `e727b6a0c5df27e5efd0ebd10759b30c4a556d4defc152cceaad0e70a5233dd6` | JVM logic |
| library/LibraryDocuments.kt | `7e7d7db7a29cc83ca78fa74085d453c263a08d1969c1cd17ddf3edbc1641f5c2` | JVM logic |
| library/LibraryPresentation.kt | `2762c8ab58b912db4481d752abc951ce19ff2857c966432797dedc75b109a780` | JVM logic |
| library/LibraryScreen.kt | `a72fcea4ad51e62638f3101dc4702cb2bf24339c42a4268cd251f2c63010d004` | Android presentation/tooling; retained in All |
| library/LibraryScreenPreviews.kt | `cbab194a3b68ab34108e43e8e47d35f623f16b2260336c526b34192302c40baf` | Android presentation/tooling; retained in All |
| library/LibraryUiState.kt | `7cbbff4aa57f45f546dbfc7214f851d3d780f17ab977e07fdb3fcf36163f9a36` | JVM logic |
| reader/ReaderController.kt | `17f5006a91a9978991b3636298e28243536a874a7dc0c1b0e5a7d13b60c4f226` | JVM logic |
| reader/ReaderFrame.kt | `452884b4030102e654c1b4d6254658d0c80fc293bf42f0a3cef7ae731f57d9ed` | JVM logic |
| reader/ReaderInteraction.kt | `9930bc3f83060ff9f232c21e0f694de34ebed56b697834b56b2f811b5d26fc43` | JVM logic |
| reader/ReaderPageSurface.kt | `e1dc34466070308f210b8ef3a8d704b29a0218ed52d6190f44a595ed12b35a87` | Android presentation/tooling; retained in All |
| reader/ReaderPresenter.kt | `20ed46352e8fee94bacfaabaaa1aedadc14bf3901abfcccf7d194d36e0b8c3a3` | JVM logic |
| reader/ReaderRecovery.kt | `06d81764c2f4c5aeb41cb9aa6e3896e7ffaff489aee4e6ba7ecf4e1bd41c5e8a` | JVM logic |
| reader/ReaderRenderPlan.kt | `d2bf31507ea2a0c2e93e2fca179d9f7b2ca24adfd90d7d444eac0d776efabf97` | JVM logic |
| reader/ReaderScreen.kt | `fafb80157f24fff2cdcb22bf1ed3ff8df98bfe7e34b1d6e08e71738b31effd81` | Android presentation/tooling; retained in All |
| reader/ReaderScreenPreviews.kt | `99d3e19ad12e13341f0c6f32e1f66fcc69dccb3ba69d0064862d9a8fe7e5b33b` | Android presentation/tooling; retained in All |
| reader/ReaderUiState.kt | `3da39b0ee27546307f764ee7b940ab8a45837953da0cf9c3a536884c04d2d4fe` | JVM logic |
| settings/SettingsAction.kt | `91775ac5410bae633aab74ee10e93d861192974d51ea7350470f50ac237b2640` | JVM logic |
| settings/SettingsController.kt | `73fb1c6966351a65bc65bb06bb043f700a25823231261ee7b85572b1bb450e29` | JVM logic |
| settings/SettingsScreen.kt | `9c74472043962a1738b9de9c3a09793621aeec21d766cf451be64b82ec3f8997` | Android presentation/tooling; retained in All |
| settings/SettingsScreenPreviews.kt | `88918b3beb27c8583e4fe665e777ecfdfa7a6b442f7cee27415a481f6fc317ff` | Android presentation/tooling; retained in All |
| settings/SettingsUiState.kt | `f3401541951e70070a82f17bccf7659ad5b26b492e5e2192c4bf022e30ddb698` | JVM logic |

Execution data SHA-256: `c005fac627ba1cc21c47a70c45e694d318b504132ec092c4dab79cf2aa00ad6d`.

Exact original class and XML hashes are retained locally at `/tmp/pre-i-coverage/provenance.json`.
A reproduction generates fresh JVM session identifiers; compare source/class IDs and counter models,
not equality of an unrelated execution-data session. Do not reuse old execution with new bytecode.
