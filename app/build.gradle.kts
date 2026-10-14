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
