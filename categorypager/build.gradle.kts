plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.categorypager"
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        packaging {
            jniLibs {
                useLegacyPackaging = true
            }
            resources {
                excludes += "META-INF/LICENSE.md"
                excludes += "META-INF/LICENSE-notice.md"
            }
        }
    }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":app:cryptoobjects"))
    implementation(project(":app:datamodel"))
    implementation(project(":app:theme"))
    implementation(project(":autolock"))
    implementation(project(":crypto"))
    implementation(project(":logger"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material.icons.extended.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.ui.test.junit4.android)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.material)

    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.test.runner) // fixed 1.6.1 test runner problem
    debugImplementation(libs.androidx.test.core) // fixed 1.6.1 test runner problem
    debugImplementation(libs.androidx.test.rules) // fixed 1.6.1 test runner problem
}