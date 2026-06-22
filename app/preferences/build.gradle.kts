plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
}

kotlin {
    @Suppress("DEPRECATION")
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidx.datastore.core)
                implementation(project(":dateutils"))
                implementation(project(":crypto"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val androidMain by getting {
            dependencies {
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