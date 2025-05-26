plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "fi.iki.ede.crypto"
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
    }
    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(project(":dateutils"))
    implementation(project(":logger"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)
    testFixturesImplementation(libs.kotlin.stdlib)
    testFixturesImplementation(libs.mockk)
    // Bring bouncy castle to unit tests
    testImplementation(libs.bcprov.jdk16)
    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
    testImplementation(libs.mockk)
}

tasks.withType<Test> {
    testLogging {
        //showStandardStreams = true
        //showExceptions = true
    }
}
