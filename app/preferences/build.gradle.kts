plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting
        val androidMain by getting {
            dependencies {
                implementation(project(":dateutils"))
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.preference.ktx)
                implementation(libs.material)
            }
        }
    }
}

android {
    namespace = "fi.iki.ede.preferences"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}