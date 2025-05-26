plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.autolock"
}

dependencies {
    // TODO: REMOVE
    implementation(project(":app:preferences"))
    implementation(project(":notifications"))
    implementation(project(":logger"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(libs.junit)
}