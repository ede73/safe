plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.oisaferestore"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":app:backup"))
    implementation(project(":app:cryptoobjects"))
    implementation(project(":app:datamodel"))
    implementation(project(":app:db"))
    implementation(project(":autolock"))
    implementation(project(":crypto"))
    implementation(project(":dateutils"))
    implementation(project(":logger"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3.android)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.test.junit.ktx)

    // Bring bouncy castle to unit tests
    testImplementation(libs.bcprov.jdk16)
    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
    testImplementation(libs.mockk)
    testImplementation(project(":crypto"))

}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
        //showExceptions = true
    }
}
