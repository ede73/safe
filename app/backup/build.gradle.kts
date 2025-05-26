plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.backup"
}

dependencies {
    implementation(project(":app:cryptoobjects"))
    implementation(project(":app:db"))
    implementation(project(":app:preferences"))
    implementation(project(":crypto"))
    implementation(project(":dateutils"))
    implementation(project(":gpm"))
    implementation(project(":logger"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
}