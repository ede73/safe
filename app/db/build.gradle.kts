plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.db"
    }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

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

configurations.matching { it.name == "kspAndroid" }.configureEach {
    project.dependencies.add(this.name, libs.androidx.room.compiler)
}

configurations.matching { it.name == "kspDesktop" }.configureEach {
    project.dependencies.add(this.name, libs.androidx.room.compiler)
}

configurations.matching { it.name.startsWith("kspIos") }.configureEach {
    project.dependencies.add(this.name, libs.androidx.room.compiler)
}


