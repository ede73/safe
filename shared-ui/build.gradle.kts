plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.safe.ui"
        /*
        buildFeatures {
            compose = true
        }
        */
    }
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(libs.compose.ui.tooling.preview)
                implementation(compose.material)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(project(":crypto"))
                implementation(project(":logger"))
                implementation(project(":app:datamodel"))
                implementation(project(":app:db"))
                implementation(project(":app:cryptoobjects"))
                implementation(project(":app:preferences"))
                implementation(project(":app:theme"))
                implementation(project(":dateutils"))
                implementation(libs.kotlinx.datetime)
                implementation(libs.okio)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

