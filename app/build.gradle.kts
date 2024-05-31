plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

// SEPARATE_TEST_TYPE: Tried to enable separate test type
// all efforts fail, instrumentation tests disappear
// and application suffix isn't applied (to unit tests)

android {
    namespace = "fi.iki.ede.safe"
    compileSdk = 34

    defaultConfig {
        applicationId = "fi.iki.ede.safe"
        minSdk = 26
        targetSdk = 34

        val (versionMajor, versionMinor, versionPatch, versionBuild) = listOf(3, 0, 35, 0)
        versionCode =
            versionMajor * 10000 + versionMinor * 1000 + versionPatch * 100 + versionBuild
        versionName = "${versionMajor}.${versionMinor}.${versionPatch}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
        // SEPARATE_TEST_TYPE: testApplicationId = "$applicationId.safetest"
    }

    // See https://developer.android.com/build/build-variants
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            packaging {
                resources {
                    // AndroidStudio Koala adds all sort of this to release builds
                    excludes += setOf(
                        "**/DebugProbesKt.bin",
                        "**/base/junit/**",
                    )
                }
            }
            //buildConfigField("", "", "")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
        // SEPARATE_TEST_TYPE:
//        create("safetest") {
//            initWith(buildTypes.getByName("debug"))
//            applicationIdSuffix = ".safetest"
//        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // SEPARATE_TEST_TYPE: DOES NOT WORK testBuildType = "release"
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
    // needed for val composeTestRule = createComposeRule() but not createComposeRule()<Activity>
    debugImplementation(libs.androidx.ui.test.manifest)
    // Docs say: Test rules and transitive dependencies: but seems to work without
    //androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose_version")
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
