plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
    id("de.mannodermaus.android-junit5") version "1.11.0.0"
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
                implementation(libs.napier)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.firebase.analytics)
                implementation(libs.firebase.crashlytics)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(project.dependencies.platform(libs.junit5.bom))
                implementation("org.junit.jupiter:junit-jupiter:5.11.3") // explicit version
                // 1.14.6 doesnt work in KMP
                implementation("io.mockk:mockk:1.13.8")                 // explicit version
            }
        }
//        val commonTest by getting {
//            dependencies {
//                implementation(kotlin("test"))
//                implementation(project.dependencies.platform(libs.junit5.bom))
//                implementation(libs.junit5.jupiter)
//                implementation(libs.mockk)
//            }
//        }
    }
}

android {
    namespace = "fi.iki.ede.logger"
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
