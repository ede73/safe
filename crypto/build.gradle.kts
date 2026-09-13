plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.crypto"
        withHostTestBuilder { }
    }
    jvm("desktop")
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":dateutils"))
            implementation(project(":logger"))
            implementation(libs.okio)
            api(libs.krypto)
            api(libs.cryptography.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.material)
        }
        named("androidHostTest") {
            dependencies {
                implementation(libs.bcprov.jdk16)
                implementation(project.dependencies.platform(libs.junit5.bom))
                implementation(libs.junit5.jupiter)
                implementation(libs.kxml2)
                implementation(libs.mockk)
                runtimeOnly(project.dependencies.platform(libs.junit5.bom))
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
        }
        named("desktopMain") {
            dependencies {
                implementation(libs.jna)
                implementation(libs.jna.platform)
            }
        }
    }
}

tasks.withType<Test> {
    forkEvery = 1 // run each test in a new JVM
    // fails to work if reused JVM
    systemProperty("korlibs.crypto.try_prng_fixes", "false")
}


