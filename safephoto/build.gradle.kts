plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting
        val androidMain by getting {
            dependencies {
                implementation(project(":logger"))
                implementation(project(":statemachine"))
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.camera.camera2)
                implementation(libs.androidx.camera.core)
                implementation(libs.androidx.camera.lifecycle)
                implementation(libs.androidx.camera.view)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.material3.android)
                implementation(libs.androidx.ui.tooling.preview.android)
                implementation(libs.guava)
                implementation(libs.material)
            }
        }
    }
}

android {
    namespace = "fi.iki.ede.safephoto"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
}