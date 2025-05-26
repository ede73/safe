plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.notifications"
}

dependencies {
    // TODO: REMOVE
    implementation(project(":app:preferences"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(libs.junit)
}