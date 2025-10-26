plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.logger"
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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    // Don't convert to catalog declaration, something is broken
    implementation(platform(libs.firebase.bom)) // firebase crashlytics
    implementation(libs.firebase.analytics) // firebase crashlytics (breadcrumbs)
    implementation(libs.firebase.crashlytics)

    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(enforcedPlatform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.mockk)
}
