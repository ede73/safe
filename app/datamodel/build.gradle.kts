plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
}

kotlin {
    @Suppress("DEPRECATION")
    androidTarget()
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
    }
}

android {
    namespace = "fi.iki.ede.datamodel"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all { it.useJUnitPlatform() }
        }
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(enforcedPlatform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
}