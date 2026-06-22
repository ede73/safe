plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.datamodel"
        withHostTestBuilder { }
    }
    jvm("desktop")

    sourceSets {
        val commonMain by getting
        val androidMain by getting {
            dependencies {
                implementation(project(":app:cryptoobjects"))
                implementation(project(":app:db"))
                implementation(project(":app:preferences"))
                implementation(project(":dateutils"))
                implementation(project(":crypto"))
                implementation(project(":logger"))
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.material)
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation(project.dependencies.platform(libs.junit5.bom))
                implementation(libs.junit5.jupiter)
            }
        }
    }
}