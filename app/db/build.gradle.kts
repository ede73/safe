plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    @Suppress("DEPRECATION")
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":app:cryptoobjects"))
                implementation(project(":crypto"))
                implementation(project(":dateutils"))
                implementation(project(":logger"))
                implementation(project(":gpm"))
                implementation(libs.okio)
                implementation(libs.kotlinx.coroutines.core)
                api(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.material)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

android {
    namespace = "fi.iki.ede.db"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}