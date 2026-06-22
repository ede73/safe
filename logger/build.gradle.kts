plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.logger"
        withHostTestBuilder { }
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
        val androidHostTest by getting {
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
//                implementation(project.dependencies.platform(libs.junit5.bom))
//                implementation(libs.junit5.jupiter)
//                implementation(libs.mockk)
//            }
//        }
    }
}

