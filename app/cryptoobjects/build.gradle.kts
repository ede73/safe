plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.cryptoobjects"
}

dependencies {
    implementation(project(":crypto"))
    implementation(project(":dateutils"))
    implementation(project(":logger"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material)
}