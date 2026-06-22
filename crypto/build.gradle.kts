plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.library)
}

kotlin {
    @Suppress("DEPRECATION")
    androidTarget {
    }
    jvm("desktop")
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":dateutils"))
                implementation(project(":logger"))
                implementation(libs.okio)
                api(libs.krypto)
                api(libs.cryptography.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.material)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.jna)
                implementation(libs.jna.platform)
            }
        }
    }
}

android {
    namespace = "fi.iki.ede.crypto"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        packaging {
            jniLibs {
                useLegacyPackaging = true
            }
            resources {
                // Required to exclude jupiter (junit) from android tests
                excludes += "META-INF/LICENSE.md"
                excludes += "META-INF/LICENSE-notice.md"
            }
        }
        unitTests {
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
            }
        }
    }
    testFixtures {
        enable = true
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)
    testFixturesImplementation(libs.kotlin.stdlib)
    testFixturesImplementation(libs.mockk)
    // Bring bouncy castle to unit tests
    testImplementation(libs.bcprov.jdk16)
    testImplementation(enforcedPlatform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.kxml2)
    testImplementation(libs.mockk)

    // Add the missing runtime dependencies for JUnit 5 to the correct test classpath
    testRuntimeOnly(enforcedPlatform(libs.junit5.bom))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    forkEvery = 1 // run each test in a new JVM
    // fails to work if reused JVM
    systemProperty("korlibs.crypto.try_prng_fixes", "false")
}
