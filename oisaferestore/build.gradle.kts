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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    kotlin { jvmToolchain(21) }
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

//    testFixtures {
//        enable = true
//    }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":app:crypto"))
    implementation(project(":dateutils"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.material3.android)
    implementation(project(":autolock"))

    // Bring bouncy castle to unit tests
    testImplementation(libs.bcprov.jdk16)
    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
    testImplementation(libs.mockk)
    testImplementation(project(":app:crypto"))

    //androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.junit.ktx)
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
        //showExceptions = true
    }
}
