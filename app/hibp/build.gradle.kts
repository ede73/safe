plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val ENABLE_HIBP: Boolean = project(":app").ext.get("ENABLE_HIBP") as Boolean

android {
    namespace = "fi.iki.ede.hibp"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_HIBP", ENABLE_HIBP.toString())
        }
        debug {
            buildConfigField("Boolean", "ENABLE_HIBP", ENABLE_HIBP.toString())
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    // Volley for HTTP breach checks
    implementation(libs.volley)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit.ktx)
}