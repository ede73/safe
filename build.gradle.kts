import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false // 2.00
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
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

// constant problems with androidx.test.runner (or rules, but usualy runner) being pulled
// by other libraries is old ancient conflicting version!
subprojects {
    dependencies {
        configurations.all {
            exclude(group = "androidx.test", module = "runner")
        }
    }
}

//configurations.all {
//    resolutionStrategy.eachDependency {
//        if (requested.group == "androidx.test" && requested.name == "runner") {
//            useVersion("1.6.1")
//        }
//    }
//}