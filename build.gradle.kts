import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false // 2.00
    alias(libs.plugins.kotlin.multiplatform) apply false
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false // Firebase crashlytics
    kotlin("plugin.power-assert") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.jetbrains.compose) apply false
}

// https://kotlinlang.org/docs/whatsnew20.html#experimental-kotlin-power-assert-compiler-plugin
@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf("kotlin.assert", "kotlin.test.assertTrue")
}

subprojects {
    tasks.withType<Test> {
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi")
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        if (project.path != ":app:SafeLinter") {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    plugins.withId("com.android.library") {
        val libraryExtension =
            extensions.getByType(com.android.build.api.dsl.LibraryExtension::class.java)
        libraryExtension.apply {
            compileSdk = 36
            defaultConfig {
                minSdk = 26
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            buildTypes {
                getByName("release") {
                    val moduleProguardRules = project.file("proguard-rules.pro")
                    if (moduleProguardRules.exists()) {
                        proguardFiles.add(moduleProguardRules)
                        println("Applied module-specific 'proguard-rules.pro' to :${project.name}")
                    }
                }
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
            lint {
                baseline = file("lint-baseline.xml")
            }
            buildFeatures {
                buildConfig = true
            }
        }
    }

    plugins.withId("com.android.application") {
        val appExtension =
            extensions.getByType(com.android.build.api.dsl.ApplicationExtension::class.java)
        appExtension.apply {
            compileSdk = 36
            defaultConfig {
                minSdk = 26
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            buildTypes {
                getByName("release") {
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                    )
                }
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
            lint {
                baseline = file("lint-baseline.xml")
            }
            buildFeatures {
                buildConfig = true
            }
        }
    }

    plugins.withId("com.android.dynamic-feature") {
        val dynamicFeatureExtension =
            extensions.getByType(com.android.build.api.dsl.DynamicFeatureExtension::class.java)
        dynamicFeatureExtension.apply {
            compileSdk = 36
            defaultConfig {
                minSdk = 26
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }
    }

    plugins.withId("com.android.kotlin.multiplatform.library") {
        // KMP library modules produce a single AAR consumed by both debug and release app builds.
        // DEBUG cannot be a compile-time const — it must be read at runtime from the host app.
        // We generate a val (not const val) with lazy reflection; the host app's BuildConfig is
        // on the classpath at runtime, so Class.forName resolves correctly.
        val packageName = "fi.iki.ede.${project.name.replace("-", "")}"
        val outDir = layout.buildDirectory.dir("generated/buildconfig/commonMain/kotlin")
        val generateTask = tasks.register("generateBuildConfig") {
            outputs.dir(outDir)
            inputs.property("packageName", packageName)
            doLast {
                outDir.get().asFile.resolve(packageName.replace('.', '/')).mkdirs()
                outDir.get().asFile.resolve(packageName.replace('.', '/') + "/BuildConfig.kt")
                    .writeText(
                        """
                        |// Generated by Gradle — do not edit.
                        |package $packageName
                        |
                        |internal object BuildConfig {
                        |    // Runtime reflection: the host app's BuildConfig.DEBUG is on the classpath.
                        |    // Lazy so the class lookup happens after Application init, not at static-init time.
                        |    val DEBUG: Boolean by lazy {
                        |        try {
                        |            Class.forName("fi.iki.ede.safe.BuildConfig")
                        |                .getField("DEBUG").get(null) as? Boolean ?: false
                        |        } catch (_: Exception) {
                        |            false
                        |        }
                        |    }
                        |}
                        """.trimMargin()
                    )
            }
        }
        extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
            .sourceSets.getByName("commonMain")
            .kotlin.srcDir(generateTask)


        val kotlinExtension =
            extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
        kotlinExtension.targets.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
            compileSdk = 36
            minSdk = 26
            androidResources {
                enable = true
            }
            val moduleProguardRules = project.file("proguard-rules.pro")
            if (moduleProguardRules.exists()) {
                optimization {
                    consumerKeepRules.publish = true
                    consumerKeepRules.files.add(moduleProguardRules)
                }
                println("Applied module-specific 'proguard-rules.pro' as consumer rules to :${project.name}")
            }
        }
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.jupiter" || requested.group == "org.junit.platform") {
            useVersion("5.11.3") // match your BOM version exactly
        }
    }
}