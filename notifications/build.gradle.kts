plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.notifications"
    }
    jvm("desktop")
//    js {
//        browser()
//    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting
        val androidMain by getting {
            dependencies {
                implementation(project(":app:preferences"))
                implementation(project(":logger"))
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.appcompat)
            }
        }
    }
}

