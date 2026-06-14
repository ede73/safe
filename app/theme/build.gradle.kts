plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
    id("org.jetbrains.compose") version "1.11.0"
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.material3)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.material.icons.extended.android)
                implementation(libs.androidx.material3.android)
                implementation(libs.androidx.ui.tooling.preview.android)
                implementation(libs.material)
            }
        }
    }
}

android {
    namespace = "fi.iki.ede.theme"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
}