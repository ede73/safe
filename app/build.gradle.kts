plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

project.ext.set("ENABLE_HIBP", true)
project.ext.set("ENABLE_OIIMPORT", false)
val ENABLE_HIBP: Boolean = project(":app").ext.get("ENABLE_HIBP") as Boolean
val ENABLE_OIIMPORT = project(":app").ext.get("ENABLE_OIIMPORT") as Boolean

android {
    namespace = "fi.iki.ede.safe"
    compileSdk = 34

    defaultConfig {
        applicationId = "fi.iki.ede.safe"
        minSdk = 26
        targetSdk = 34

        val (versionMajor, versionMinor, versionPatch, versionBuild) = listOf(3, 0, 38, 0)
        versionCode =
            versionMajor * 10000 + versionMinor * 1000 + versionPatch * 100 + versionBuild
        versionName = "${versionMajor}.${versionMinor}.${versionPatch}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["test"] = "true"
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
            buildConfigField("Boolean", "ENABLE_HIBP", ENABLE_HIBP.toString())
            buildConfigField("Boolean", "ENABLE_OIIMPORT", ENABLE_OIIMPORT.toString())
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("Boolean", "ENABLE_HIBP", ENABLE_HIBP.toString())
            buildConfigField("Boolean", "ENABLE_OIIMPORT", ENABLE_OIIMPORT.toString())
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
    // cant dynamically filter these out as imports would fail and making stub is too much work..
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
    // When ever doing release(Generate Signed App Bundle), run also all the tests...
    when (name) {
        "connectedDebugAndroidTest" -> dependsOn("unlockEmulator")
        "bundleRelease" -> {
            dependsOn("testReleaseUnitTest")
            dependsOn("connectedAndroidTest")
        }

        "assembleDebug" -> {
            // UNIT TESTS
            dependsOn("testDebugUnitTest")
            // INSTRUMENTED TESTS, takes long time
            //task.dependsOn("connectedAndroidTest")
        }

//        "assembleRelease" -> {
//            // UNIT TESTS
//            dependsOn("testReleaseUnitTest")
//            dependsOn("connectedAndroidTest")
//        }
    }
}

// enable mocking ZonedDateTime (et.all)
// https://mockk.io/doc/md/jdk16-access-exceptions.html
tasks.withType<Test> {
    systemProperty("test", "true")
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
}

//tasks.named<AndroidTest>("connectedAndroidTest") {
//    print("=====connectedAndroidTest")
//}
//tasks.named<AndroidTest>("connectedDebugAndroidTest") {
//    print("=====connectedDebugAndroidTest")
//}

tasks.register("unlockEmulator") {
    /*
    Enable...
tasks.configureEach {when (name) {"connectedDebugAndroidTest" -> dependsOn("unlockEmulator")}}
     */
    doLast {
        exec {
            commandLine(
                "adb",
                "shell",
                "dumpsys deviceidle|grep mScreenOn=false && input keyevent KEYCODE_POWER;dumpsys deviceidle|grep mScreenLocked=true && (input keyevent KEYCODE_MENU ;input text 0000;input keyevent KEYCODE_ENTER);true"
            )
        }
    }
}
