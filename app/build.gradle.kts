import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.charset.Charset
import java.util.Properties

//gradle.addListener(object : TaskExecutionListener {
//    var startTime: Long = 0
//
//    override fun beforeExecute(task: Task) {
//        startTime = System.currentTimeMillis()
//    }
//
//    override fun afterExecute(task: Task, taskState: TaskState) {
//        val endTime = System.currentTimeMillis()
//        val duration = endTime - startTime
//        Log.d(TAG,("Task ${task.path} took ${duration}ms to execute")
//    }
//})

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    id("com.google.gms.google-services") // Firebase crashlytics
    id("com.google.firebase.crashlytics") // Firebase crashlytics
    kotlin("plugin.serialization")
    //id("fi.iki.ede.safe.SafeLinter")
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
//    lint {
//        // Only check for issues from your custom linter
//        checkOnly.add("DisallowedImplicitToString") // Replace with your issue ID
//    }

    val localProperties = Properties().apply {
        load(FileInputStream(rootProject.file("local.properties")))
    }
    // TODO: if I cant make quick 'release build' instrumentation tests to work, remove all
    // instrumentationTest configs
//    signingConfigs {
//        create("instrumentationTest") {
//            storeFile = file(localProperties.getProperty("instrumentationKeyStore"))
//            keyAlias = localProperties.getProperty("instrumentationStoreKeyAlias")
//            // this is just a test key set, not a real key, so password doesn't matter
//            storePassword = localProperties.getProperty("instrumentationStorePassword")
//            keyPassword = localProperties.getProperty("instrumentationKeyPassword")
//        }
//    }
    namespace = "fi.iki.ede.safe"
    compileSdk = 35

    defaultConfig {
        applicationId = "fi.iki.ede.safe"
        minSdk = 26
        targetSdk = 35

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

        val services = "/META-INF/services/fi.iki.ede.safe.splits.RegistrationAPI\$Provider"

        val dynamicFeatureList = setOf(":categorypager", ":oisaferestore", ":hibp")
        dynamicFeatures += dynamicFeatureList

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
                    merges += services
                }
            }
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${gitCommitHash}\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${gitCommitHash}\"")
            packaging {
                resources {
                    merges += services
                }
            }
        }
// Seemingly "nice" idea to pull new build type for faster instrumentation tests
// how ever androidstudio doesnt allow attaching this to run configuration
// running the tests, fail to some Android Studio command line takes too long error
// DFM modules don't work (they cannot support applicationIdSuffix)
// even if you can disable DFM modules here, the META-INF/services forcibly comes in
// and linter complains for missing classes (can be fixed by empty)
// app/src/instrumentationTest/resources/META-INF/services/fi.iki.ede.safe.splits.RegistrationAPI$Provider
// Other than that, YES, this does produce release build type, signed with 'new debug key'
// and separate application index, so can install 'almost release' app along side
// playstore release app (i opted in playstore signing keys, so can't install release app
// in my emu any more due to keyconflict :( )
// and META-INF
//        create("instrumentationTest") {
//            initWith(getByName("release"))
//            isMinifyEnabled = false
//            isShrinkResources = false
//            applicationIdSuffix = ".instrumentation"
//            // DFMs don't support app suffix, do have to disable them
//            dynamicFeatures -= dynamicFeatureList
//            signingConfig = signingConfigs.getByName("instrumentationTest")
//            packaging {
//                resources {
//                    merges -= services
//                    excludes += services
//                }
//            }
//        }
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

    testFixtures {
        enable = true
    }
}

composeCompiler {
    enableStrongSkippingMode = true
}

dependencies {
    implementation(project(":app:cryptoobjects"))
    implementation(project(":app:db"))
    implementation(project(":gpmui"))
    lintChecks(project(":app:SafeLinter"))
    implementation(project(":crypto"))
    implementation(project(":app:preferences"))
    implementation(project(":app:theme"))
    implementation(project(":dateutils"))
    implementation(project(":autolock"))
    implementation(project(":statemachine"))
    implementation(project(":clipboardutils"))
    implementation(project(":notifications"))
    // cant dynamically filter these out as imports would fail and making stub is too much work..
    implementation(project(":gpm"))

    implementation(project(":safephoto"))
    implementation(project(":datepicker"))
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)

    implementation(libs.app.update.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material.icons.extended.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.kotlinx.coroutines.core)

    // TODO: WHY IS THIS HERE?
    implementation(libs.androidx.ui.test.junit4.android)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.material)
    implementation(libs.feature.delivery.ktx)
    // Don't convert to catalog declaration, something is broken
    implementation(platform("com.google.firebase:firebase-bom:33.7.0")) // firebase crashlytics
    implementation(libs.firebase.analytics) // firebase crashlytics (breadcrumbs)
    implementation(libs.firebase.crashlytics)
    implementation(libs.kotlinx.serialization.json)

    // Bring bouncy castle to unit tests
    testImplementation("org.bouncycastle:bcprov-jdk16:1.46")
    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
    testImplementation(libs.mockk)
    testImplementation(project(":crypto"))
    testImplementation(testFixtures(project(":crypto")))
    testImplementation(project(":app"))

    //androidTestImplementation(libs.androidx.test.junit.ktx)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)
    androidTestRuntimeOnly((libs.kotlin.stdlib))
    androidTestImplementation(libs.androidx.monitor)
    // camera

    // Firebase testlab screenshot ability
    //androidTestImplementation("com.google.firebase:testlab-instr-lib:0.2")
    // needed for val composeTestRule = createComposeRule() but not createComposeRule()<Activity>
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
    // Docs say: Test rules and transitive dependencies: but seems to work without
    //androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose_version")
    implementation(kotlin("reflect"))
    testFixturesImplementation(libs.kotlin.stdlib)
    debugRuntimeOnly(libs.androidx.test.runner)
    debugImplementation(libs.androidx.test.runner) // fixed 1.6.1 test runner problem
}

tasks.configureEach {
    // When ever doing release(Generate Signed App Bundle), run also all the tests...
    when (name) {
//        "connectedDebugAndroidTest" -> dependsOn("unlockEmulator")
//        "bundleRelease" -> {
//            dependsOn("testReleaseUnitTest")
//            dependsOn("connectedAndroidTest")
//        }

        "assembleDebug" -> {
            // UNIT TESTS
            dependsOn(
                "testDebugUnitTest",
                ":app:db:testDebugUnitTest",
                ":app:cryptoobjects:testDebugUnitTest",
                ":app:preferences:testDebugUnitTest",
                ":app:theme:testDebugUnitTest",
                ":autolock:testDebugUnitTest",
                ":categorypager:testDebugUnitTest",
                ":clipboardutils:testDebugUnitTest",
                ":crypto:testDebugUnitTest",
                ":datepicker:testDebugUnitTest",
                ":dateutils:testDebugUnitTest",
                ":gpm:testDebugUnitTest",
                ":gpmui:testDebugUnitTest",
                ":hibp:testDebugUnitTest",
                ":notifications:testDebugUnitTest",
                ":oisaferestore:testDebugUnitTest",
                ":safephoto:testDebugUnitTest",
                ":statemachine:testDebugUnitTest",
            )
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

configurations.all {
    resolutionStrategy {
        force("androidx.test:core:1.6.1")
    }
}

tasks.register("fullBuild") {
    dependsOn(
        "compileReleaseKotlin",
        "compileReleaseUnitTestKotlin",
        "compileDebugAndroidTestKotlin"
    )
}

kotlin {
    compilerOptions {
        extraWarnings.set(true)
    }
}