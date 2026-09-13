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
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        androidMain.dependencies {
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
        named("androidHostTest") {
            dependencies {
                implementation(project.dependencies.platform(libs.junit5.bom))
                implementation(libs.junit5.jupiter)
            }
        }
    }
}