plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.safe"
    compileSdk = 34

    defaultConfig {
        applicationId = "fi.iki.ede.safe"
        minSdk = 26
        targetSdk = 34

        val (versionMajor, versionMinor, versionPatch, versionBuild) = listOf(3, 0, 30, 0)
        versionCode =
            versionMajor * 10000 + versionMinor * 1000 + versionPatch * 100 + versionBuild
        versionName = "${versionMajor}.${versionMinor}.${versionPatch}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
    // this should have been deprecated in Kotlin 2.0 but some why AndroidStudio wants it
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

composeCompiler {
    enableStrongSkippingMode = true
}

dependencies {
    implementation(project(":app:crypto"))
    implementation(project(":app:hibp"))
    implementation(project(":app:oisafecompatibility"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material.icons.extended.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.ui.test.junit4.android)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.material)

    // Bring bouncy castle to unit tests
    testImplementation("org.bouncycastle:bcprov-jdk16:1.46")
    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.runner)

    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)
}

tasks.configureEach {
    if (name == "bundleRelease") {
        dependsOn("testReleaseUnitTest")
        // There are no instrumented release tests
    }
    if (name == "assembleDebug") {
        // UNIT TESTS
        dependsOn("testDebugUnitTest")
        // INSTRUMENTED TESTS, takes long time
        //task.dependsOn("connectedAndroidTest")
    }
    if (name == "assembleRelease") {
        // UNIT TESTS
        dependsOn("testReleaseUnitTest")
    }
}
