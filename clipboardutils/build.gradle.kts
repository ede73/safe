plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
}

kotlin {
    @Suppress("DEPRECATION")
    androidTarget()
    jvm("desktop")
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.material)
            }
        }
    }
}

android {
    namespace = "fi.iki.ede.clipboardutils"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
}