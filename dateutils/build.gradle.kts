plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
    }
    jvm("desktop")
    js(IR) {
        browser()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.datetime)
                implementation(project(":logger"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
            }
        }
    }
}

android {
    namespace = "fi.iki.ede.dateutils"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
            }
        }
    }
}
