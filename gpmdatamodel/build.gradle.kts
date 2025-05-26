plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.gpmdatamodel"
}

dependencies {
    implementation(project(":app:cryptoobjects"))
    implementation(project(":app:datamodel"))
    implementation(project(":app:db"))
    implementation(project(":app:preferences"))
    implementation(project(":crypto"))
    implementation(project(":gpm"))
    implementation(project(":logger"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(libs.junit)
}