import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    kotlin("plugin.power-assert") version "2.0.0"
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.android.library) apply false
}

// https://kotlinlang.org/docs/whatsnew20.html#experimental-kotlin-power-assert-compiler-plugin
@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf("kotlin.assert", "kotlin.test.assertTrue")
}
