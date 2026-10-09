plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    jacoco
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
}

dependencies {
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
