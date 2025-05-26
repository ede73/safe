plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.logger"
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    // Don't convert to catalog declaration, something is broken
    implementation(platform("com.google.firebase:firebase-bom:33.14.0")) // firebase crashlytics
    implementation(libs.firebase.analytics) // firebase crashlytics (breadcrumbs)
    implementation(libs.firebase.crashlytics)

    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}