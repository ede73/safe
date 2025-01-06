plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.categorypager"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    kotlin { jvmToolchain(21) }
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        packaging {
            jniLibs {
                useLegacyPackaging = true
            }
            resources {
                excludes += "META-INF/LICENSE.md"
                excludes += "META-INF/LICENSE-notice.md"
            }
        }
    }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":crypto"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material.icons.extended.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.ui.test.junit4.android)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.material)
    implementation(project(":autolock"))
    implementation(project(":app:cryptoobjects"))
    implementation(project(":app:theme"))

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.junit.ktx)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)

    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.test.runner) // fixed 1.6.1 test runner problem
    debugImplementation(libs.androidx.test.core) // fixed 1.6.1 test runner problem
    debugImplementation(libs.androidx.test.rules) // fixed 1.6.1 test runner problem
}