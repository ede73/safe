plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
}

kotlin {
    @Suppress("DEPRECATION")
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting
        val androidMain by getting {
            dependencies {
                implementation(project(":app:preferences"))
                implementation(project(":notifications"))
                implementation(project(":logger"))
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.material)
                implementation(libs.kotlin.stdlib)
            }
        }
    }
}

android {
    namespace = "fi.iki.ede.autolock"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}