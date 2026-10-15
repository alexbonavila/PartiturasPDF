plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    jacoco
}

// The Room plugin declares schema inputs/outputs for cache-aware KSP processing.
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.abdev.partituraspdf"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.abdev.partituraspdf"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Native adapter coverage requires the real Android runtime.
            enableAndroidTestCoverage = true
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Instrumentation APK only: canonical PDFs and manifest, never production APK assets.
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("test-fixtures"))
    sourceSets.getByName("androidTest").assets.srcDir("schemas")

}

dependencies {
    // Room 2.7+ includes Kotlin/coroutine APIs in runtime; room-ktx is redundant.
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.uiautomator)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// A1 needs measured, scoped JVM coverage. This build-only tool supports daemon JDK 25.
jacoco { toolVersion = libs.versions.jacoco.get() }

// AGP 9 built-in Kotlin output; scope includes all handwritten A1 logic, no UI/resources.
val contractClasses = fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
    include("com/abdev/partituraspdf/core/**")
    include("com/abdev/partituraspdf/storage/api/**")
    include("com/abdev/partituraspdf/pdf/api/**")
}
val contractExecution = layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")
val contractSources = files("src/main/java")

val validateContractCoverageInputs = tasks.register("validateContractCoverageInputs") {
    dependsOn("testDebugUnitTest")
    val classes = contractClasses
    val execution = contractExecution
    doLast {
        check(classes.files.isNotEmpty()) { "No contract classes found for coverage" }
        check(execution.get().asFile.isFile) { "Missing unit test coverage evidence" }
    }
}

val contractCoverageReport = tasks.register<JacocoReport>("contractCoverageReport") {
    dependsOn(validateContractCoverageInputs)
    classDirectories.setFrom(contractClasses)
    sourceDirectories.setFrom(contractSources)
    executionData.setFrom(contractExecution)
    reports {
        xml.required.set(true)
        html.required.set(true)
        // Retained by the existing unit-test-reports artifact without changing CI.
        xml.outputLocation.set(layout.buildDirectory.file("reports/tests/testDebugUnitTest/contract-coverage.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/tests/testDebugUnitTest/contract-coverage"))
    }
}

val verifyContractCoverage = tasks.register<JacocoCoverageVerification>("verifyContractCoverage") {
    dependsOn(contractCoverageReport)
    classDirectories.setFrom(contractClasses)
    sourceDirectories.setFrom(contractSources)
    executionData.setFrom(contractExecution)
    violationRules {
        rule {
            limit { counter = "LINE"; minimum = "0.90".toBigDecimal() }
            limit { counter = "BRANCH"; minimum = "0.80".toBigDecimal() }
        }
    }
}

// The unchanged unit-tests CI command enforces the scoped policy as well as executing tests.
tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") finalizedBy(verifyContractCoverage)
}

// B5 coverage: measure the native PDF adapter separately from shared A1 contracts.
val pdfAdapterClasses = fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
    include("com/abdev/partituraspdf/pdf/android/**")
}

// Include real API 26 PdfRenderer execution in the B-specific coverage denominator.
// JVM fakes cannot exercise AndroidNativePdfDocument/Page and AndroidRenderBuffer.
val pdfAdapterAndroidExecution = fileTree(layout.buildDirectory.dir("outputs/code_coverage/debugAndroidTest/connected")) {
    include("**/*.ec")
}
val validatePdfAdapterAndroidExecution = tasks.register("validatePdfAdapterAndroidExecution") {
    dependsOn("connectedDebugAndroidTest")
    val evidence = pdfAdapterAndroidExecution
    doLast {
        check(evidence.files.any { it.isFile && it.length() > 0L }) {
            "Missing Android instrumentation coverage. Verify API 26 ran with enableAndroidTestCoverage enabled."
        }
    }
}

val pdfAdapterCoverageReport = tasks.register<JacocoReport>("pdfAdapterCoverageReport") {
    dependsOn("testDebugUnitTest", validatePdfAdapterAndroidExecution)
    classDirectories.setFrom(pdfAdapterClasses)
    sourceDirectories.setFrom(contractSources)
    executionData.setFrom(contractExecution, pdfAdapterAndroidExecution)
    reports {
        xml.required.set(true)
        html.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/tests/testDebugUnitTest/pdf-adapter-coverage.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/tests/testDebugUnitTest/pdf-adapter-coverage"))
    }
}

val verifyPdfAdapterCoverage = tasks.register<JacocoCoverageVerification>("verifyPdfAdapterCoverage") {
    dependsOn(pdfAdapterCoverageReport)
    classDirectories.setFrom(pdfAdapterClasses)
    sourceDirectories.setFrom(contractSources)
    executionData.setFrom(contractExecution, pdfAdapterAndroidExecution)
    violationRules {
        rule {
            limit { counter = "LINE"; minimum = "0.90".toBigDecimal() }
            limit { counter = "BRANCH"; minimum = "0.80".toBigDecimal() }
        }
    }
}

// C05-INFRA: consume one explicitly prepared same-build JVM/API26 run; never execute an emulator here.
val persistenceReports = layout.buildDirectory.dir("reports/persistence")
val persistenceClasses = layout.buildDirectory.dir(
    "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
)
val persistenceTool = rootProject.file("scripts/persistence_coverage.py")
val persistenceJacoco = configurations.named("jacocoAnt")
val persistenceAdb = androidComponents.sdkComponents.adb

abstract class PersistenceToolArguments : org.gradle.process.CommandLineArgumentProvider {
    @get:Classpath abstract val jacocoClasspath: ConfigurableFileCollection
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val script: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val adbExecutable: RegularFileProperty
    @get:Input abstract val mode: Property<String>
    @get:Input abstract val versions: MapProperty<String, String>

    override fun asArguments(): Iterable<String> = listOf(
        script.get().asFile.absolutePath, mode.get(),
        "--jacoco-classpath", jacocoClasspath.asPath,
        "--adb", adbExecutable.get().asFile.absolutePath,
    ) + versions.get().flatMap { (name, value) -> listOf("--$name", value) }
}

fun Exec.persistenceCommand(mode: String) {
    executable = "python3"
    val arguments = objects.newInstance<PersistenceToolArguments>()
    arguments.script.set(persistenceTool)
    arguments.jacocoClasspath.from(persistenceJacoco)
    arguments.adbExecutable.set(persistenceAdb)
    arguments.mode.set(mode)
    arguments.versions.set(mapOf(
        "gradle-version" to gradle.gradleVersion,
        "agp-version" to libs.versions.agp.get(),
        "jdk-version" to System.getProperty("java.version"),
        "jacoco-version" to libs.versions.jacoco.get(),
    ))
    argumentProviders.add(arguments)
}

val testPersistenceCoverageInputs = tasks.register<Exec>("testPersistenceCoverageInputs") {
    dependsOn("compileDebugKotlin", "compileDebugUnitTestJavaWithJavac", "compileDebugAndroidTestJavaWithJavac")
    persistenceCommand("test-inputs")
}

val preparePersistenceCoverageRun = tasks.register<Exec>("preparePersistenceCoverageRun") {
    dependsOn("assembleDebug", "assembleDebugAndroidTest", "compileDebugUnitTestJavaWithJavac", testPersistenceCoverageInputs)
    persistenceCommand("prepare")
}

val validatePersistenceCoverageInputs = tasks.register<Exec>("validatePersistenceCoverageInputs") {
    // Deliberately no test/compile dependency: consume captured data without rebuilding its classes.
    persistenceCommand("validate")
}

val persistenceCoverageTasks = listOf("metadata", "preferences").map { scope ->
    tasks.register<JacocoReport>("${scope}PersistenceCoverageReport") {
        dependsOn(validatePersistenceCoverageInputs)
        classDirectories.setFrom(fileTree(persistenceClasses) {
            include("com/abdev/partituraspdf/data/$scope/**")
            exclude("**/DocumentDao_Impl.class", "**/DocumentDao_Impl\$*.class",
                "**/MetadataDatabase_Impl.class", "**/MetadataDatabase_Impl\$*.class") // KSP output only.
        })
        sourceDirectories.setFrom(files("src/main/java"))
        executionData.setFrom(
            layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"),
            fileTree(layout.buildDirectory.dir("outputs/code_coverage/debugAndroidTest/connected")) {
                include("**/*.ec")
            },
        )
        // Each input validation writes a fresh stamp; stale cached XML must not follow it.
        outputs.upToDateWhen { false }
        reports {
            xml.required.set(true)
            html.required.set(true)
            xml.outputLocation.set(persistenceReports.map { it.file("$scope.xml") })
            html.outputLocation.set(persistenceReports.map { it.dir("$scope.html") })
        }
    }
}

val verifyPersistenceCoverage = tasks.register<Exec>("verifyPersistenceCoverage") {
    dependsOn(persistenceCoverageTasks)
    persistenceCommand("verify")
}

// Reproduce a completed retained run offline; this does not perform or certify a new Android run.
val auditPersistenceCoverage = tasks.register<Exec>("auditPersistenceCoverage") {
    persistenceCommand("audit")
}
