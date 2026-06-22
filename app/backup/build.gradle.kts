plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.backup"
    }
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":app:cryptoobjects"))
                implementation(project(":app:db"))
                implementation(project(":crypto"))
                implementation(project(":dateutils"))
                implementation(project(":gpm"))
                implementation(project(":logger"))
                implementation(libs.okio)
                compileOnly(libs.kxml2)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(project(":app:preferences"))
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.material)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.kxml2)
            }
        }
    }
}

