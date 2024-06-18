import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    id("com.google.gms.google-services") // Firebase crashalytics
    id("com.google.firebase.crashlytics") // Firebase crashalytics
}

/**
 * To enable project wide flags for enabling/disabling features:
 *
 * In here:
 *   project.ext.set("FLAG_NAME", true)
 *   val FLAG_NAME: Boolean = project(":app").ext.get("FLAG_NAME") as Boolean
 *
 * In the other project (project/build.gradle.kts):
 *   val FLAG_NAME: Boolean = project(":app").ext.get("FLAG_NAME") as Boolean
 *   buildTypes {
 *     release { buildConfigField("Boolean", "FLAG_NAME", FLAG_NAME.toString())}
 *     debug { buildConfigField("Boolean", "FLAG_NAME", FLAG_NAME.toString())}
 *   }
 *
 * In code (of the project in question):
 *  throwIfFeatureNotEnabled(BuildConfig.FLAG_NAME) etc.
 */

abstract class GitCommitHashValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = output
        }
        return String(output.toByteArray(), Charset.defaultCharset()).trim()
    }
}

abstract class GitRevListCountValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            standardOutput = output
        }
        return String(output.toByteArray(), Charset.defaultCharset()).trim()
    }
}


android {
    namespace = "fi.iki.ede.safe"
    compileSdk = 34

    defaultConfig {
        applicationId = "fi.iki.ede.safe"
        minSdk = 26
        targetSdk = 34

        val gitRevListProvider = providers.of(GitRevListCountValueSource::class) {}
        val gitRevListCount = gitRevListProvider.get().toInt()

        val (versionMajor, versionMinor, versionPatch, versionBuild) = listOf(
            3,
            6,
            gitRevListCount / 100,
            gitRevListCount % 100
        )
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

    sourceSets["main"].manifest.srcFile("src/main/AndroidManifest.xml")
    sourceSets["debug"].manifest.srcFile("src/debug/AndroidManifest.xml")

    // See https://developer.android.com/build/build-variants
    buildTypes {
        val gitCommitHashProvider = providers.of(GitCommitHashValueSource::class) {}
        val gitCommitHash = gitCommitHashProvider.get()
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
                    // something fuckety fuck is going on with bundle tool
                    // (there's a 4 year old bug report in this too)
                    // it doesn't like if DFMs have service records with differing content
                    // and this should be FINE since each DFM is in their own namespace
                    // not even merge helped, so currently all service records of DFMs are
                    // stored under META-INF/services of the APP
                    merges += "/META-INF/services/fi.iki.ede.safe.splits.RegistrationAPI\$Provider"
                }
            }
            debug {
                packaging {
                    resources {
                        merges += "/META-INF/services/fi.iki.ede.safe.splits.RegistrationAPI\$Provider"
                    }
                }
            }
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${gitCommitHash}\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${gitCommitHash}\"")
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
    dynamicFeatures += setOf(":categorypager", ":oisaferestore", ":hibp")
    testFixtures {
        enable = true
    }
}

composeCompiler {
    enableStrongSkippingMode = true
}

dependencies {
    implementation(project(":app:crypto"))
    // cant dynamically filter these out as imports would fail and making stub is too much work..
    implementation(libs.app.update.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material.icons.extended.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.preference.ktx)
    // TODO: WHY IS THIS HERE?
    implementation(libs.androidx.ui.test.junit4.android)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.material)
    //implementation(libs.core.ktx)
    implementation(libs.feature.delivery.ktx)
    // Don't convert to catalog declaration, something is broken
    implementation(platform("com.google.firebase:firebase-bom:33.1.0")) // firebase crashalytics
    implementation("com.google.firebase:firebase-analytics") // firebase crashalytics (breadcrumbs)
    implementation(libs.firebase.crashlytics.ktx) // firebase crashalytics

    // Bring bouncy castle to unit tests
    testImplementation("org.bouncycastle:bcprov-jdk16:1.46")
    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
    testImplementation(libs.mockk)
    testImplementation(project(":app:crypto"))
    testImplementation(testFixtures(project(":app:crypto")))
    testImplementation(project(":app"))

    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)
    // Firebase testlab screenshot ability
    androidTestImplementation("com.google.firebase:testlab-instr-lib:0.2")
    // needed for val composeTestRule = createComposeRule() but not createComposeRule()<Activity>
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
    // Docs say: Test rules and transitive dependencies: but seems to work without
    //androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose_version")
    implementation(kotlin("reflect"))
    testFixturesImplementation(libs.kotlin.stdlib)
}

tasks.configureEach {
    // When ever doing release(Generate Signed App Bundle), run also all the tests...
    when (name) {
        "connectedDebugAndroidTest" -> dependsOn("unlockEmulator")
//        "bundleRelease" -> {
//            dependsOn("testReleaseUnitTest")
//            dependsOn("connectedAndroidTest")
//        }

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

abstract class MyValueSource @Inject constructor(private val execOperations: ExecOperations) :
    ValueSource<String?, ValueSourceParameters.None?> {
    override fun obtain(): String {
        // your custom implementation
        return ""
    }
}

abstract class UnlockEmulator2ValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(
                "adb", "shell",
                "dumpsys deviceidle|grep mScreenOn=false && input keyevent KEYCODE_POWER;dumpsys deviceidle|grep mScreenLocked=true && (input keyevent KEYCODE_MENU ;input text 0000;input keyevent KEYCODE_ENTER);true >/dev/null"
            )
            standardOutput = output
            isIgnoreExitValue = true
        }
        return String(output.toByteArray(), Charset.defaultCharset())
    }
}

// Things have gotten more "complicated" with cache, just doLast{exec{}} makes a cache failure
// need to use ValueSources nowadays, and them too in exact right order
// https://docs.gradle.org/current/userguide/configuration_cache.html
// ./gradlew --configuration-cache unlockEmulator
/* Enable...tasks.configureEach {when (name) {"connectedDebugAndroidTest" -> dependsOn("unlockEmulator")}} */
tasks.register("unlockEmulator") {
    val unlockEmulator2Provider = providers.of(UnlockEmulator2ValueSource::class) {}
    doLast {
        unlockEmulator2Provider.get()
    }
}

tasks.withType<Test> {
    testLogging {
        //showStandardStreams = true
    }
}
