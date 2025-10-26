plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.datamodel"
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
            }
        }
    }
}

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

    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(enforcedPlatform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
}