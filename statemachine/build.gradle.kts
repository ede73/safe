plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "fi.iki.ede.statemachine"
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
            }
        }
        packaging {
            resources {
                excludes += setOf(
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md"
                )
            }
        }
    }
}

dependencies {
    implementation(project(":logger"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)
    testImplementation(enforcedPlatform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.mockk)
}