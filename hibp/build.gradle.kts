plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.hibp"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3.android)
    implementation(libs.material)
    // Volley for HTTP breach checks
    implementation(libs.volley)
    implementation(project(":app"))
    implementation(project(":app:theme"))
    implementation(project(":crypto"))
    implementation(project(":logger"))
}