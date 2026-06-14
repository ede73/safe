plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
    }
    jvm("desktop")
//    js(IR) {
//        browser()
//    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":crypto"))
                implementation(project(":logger"))
                implementation(libs.okio)
                implementation(libs.ktor.http)
                implementation(libs.androidx.room.runtime)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(project(":app:cryptoobjects"))
                implementation(libs.androidx.core.ktx)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(project.dependencies.platform(libs.junit5.bom))
                implementation(libs.junit5.jupiter)
                implementation(libs.mockk)
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
        }
//        val commonTest by getting {
//            dependencies {
//                implementation(kotlin("test"))
//                implementation(enforcedPlatform(libs.junit5.bom))
//                implementation(libs.junit5.jupiter)
//                implementation(libs.mockk)
//                implementation(project(":logger"))
//            }
//        }
    }
}

android {
    namespace = "fi.iki.ede.gpm"
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
            }
        }
    }
}
