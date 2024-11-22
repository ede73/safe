plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.hibp"
    compileSdk = 35

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
}

dependencies {
    implementation(project(":app"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.activity.compose)
    // Volley for HTTP breach checks
    implementation(libs.volley)
    implementation(project(":app:crypto"))

    testImplementation(libs.junit)

    //androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.junit.ktx)
}