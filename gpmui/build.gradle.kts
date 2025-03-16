plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "fi.iki.ede.gpmui"
    compileSdk = 35

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
        compose = true
        buildConfig = true
    }
    lint {
        baseline = file("lint-baseline.xml")
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
    // libs.androidx.ui.test.android is pulling espresso 3.5.0 which in turn forces test core 1.5.0
//    implementation(libs.androidx.espresso.core) {
//        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
//    }
    implementation(libs.androidx.ui.test.android)
    {
        exclude(group = "androidx.test.espresso", module = "espresso-core")
    }
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.material)
    implementation(libs.androidx.runtime.livedata)

    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}