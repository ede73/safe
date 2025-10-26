import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false // 2.00
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false // Firebase crashlytics
    kotlin("plugin.power-assert") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
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
    tasks.withType<KotlinCompile>().configureEach {
        if (project.path != ":app:SafeLinter") {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }
    }
    project.afterEvaluate {
        val androidExtension =
            this.extensions.findByType(com.android.build.api.dsl.CommonExtension::class.java)
        androidExtension?.apply {
            compileSdk = 36

            defaultConfig {
                minSdk = 26 // Or from libs.versions.toml / gradle.properties
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            buildTypes {
                getByName("release") {
                    //isMinifyEnabled = false
                    if (project.plugins.hasPlugin("com.android.application")) {
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    } else {
                        val moduleProguardRules = project.file("proguard-rules.pro")
                        if (moduleProguardRules.exists()) {
                            proguardFiles.add(moduleProguardRules)
                            println("Applied module-specific 'proguard-rules.pro' to :${project.name}")
                        }
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
            //                (this as? com.android.build.api.dsl.TestedExtension)?.let { testedExtension ->
            //                    testedExtension.kotlinOptions { // This might work depending on plugin versions
            //                        jvmTarget = JavaVersion.VERSION_11.toString()
            //                    }
            //                }
            //                if (kotlinExtension != null) {
            //                    kotlinExtension.kotlinOptions {
            //                        jvmTarget = "11"
            //                    }
            //                }
            buildFeatures {
                buildConfig = true
            }
        }
    }
}


