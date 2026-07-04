plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.statemachine"
        /*
        buildFeatures {
            compose = true
        }
        */
        withHostTestBuilder { }
    }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":logger"))
                implementation(compose.runtime)
            }
        }
        val androidMain by getting {
            kotlin.srcDirs("src/main/java")
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.material)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidHostTest by getting {
            kotlin.srcDir("src/test/java")
            dependencies {
                implementation(project.dependencies.platform(libs.junit5.bom))
                implementation(libs.junit5.jupiter)
                implementation(libs.mockk)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
