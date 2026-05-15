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
                implementation(project(":app:cryptoobjects"))
                implementation(project(":crypto"))
                implementation(project(":dateutils"))
                implementation(project(":logger"))
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.material)
                implementation(libs.okio)
            }
        }
    }
}

android {
    namespace = "fi.iki.ede.db"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}