import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    //kotlin("jvm") version "2.0.0"
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false // 2.00
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false // Firebase crashalytics
    kotlin("plugin.power-assert") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

// https://kotlinlang.org/docs/whatsnew20.html#experimental-kotlin-power-assert-compiler-plugin
@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf("kotlin.assert", "kotlin.test.assertTrue")
}

// force all kotlin to be 2.0.0 some dependencies pull 1.9, some even 1.8
// even with this..
// e: C:/Users/ede/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-reflect/2.0.0/9c3d75110945233bf77d2e1a90604b100884db94/kotlin-reflect-2.0.0.jar!/META-INF/deserialization.common.jvm.kotlin_module: Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 1.9.0, expected version is 1.5.1.
//allprojects {
//    configurations.all {
//        resolutionStrategy.eachDependency {
//            if (requested.group == "org.jetbrains.kotlin") {
//                useVersion("2.0.0")
//            }
//        }
//    }
//}
