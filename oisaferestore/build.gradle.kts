plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.oisaferestore"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    lint {
        baseline = file("lint-baseline.xml")
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
