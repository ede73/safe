plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.gpmui"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":app:cryptoobjects"))
    implementation(project(":app:datamodel"))
    implementation(project(":app:db"))
    implementation(project(":app:preferences"))
    implementation(project(":app:theme"))
    implementation(project(":autolock"))
    implementation(project(":crypto"))
    implementation(project(":dateutils"))
    implementation(project(":gpm"))
    implementation(project(":gpmdatamodel"))
    implementation(project(":logger"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material.icons.extended.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.ui.test.android)
    {
        exclude(group = "androidx.test.espresso", module = "espresso-core")
    }
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.material)
    implementation(libs.androidx.runtime.livedata)

    implementation(libs.okio)

    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(libs.junit)
}