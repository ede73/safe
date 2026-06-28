plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.autolock"
    }
    jvm("desktop")
    iosX64()
    iosArm64()
    iosSimulatorArm64()

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

