plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.datepicker"
        /*
        buildFeatures {
            compose = true
        }
        */
    }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":dateutils"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.material3.android)
                implementation(libs.androidx.ui.tooling.preview.android)
                implementation(libs.material)
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}

