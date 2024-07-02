plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

//compileOptions {
//    sourceCompatibility = JavaVersion.VERSION_21
//    targetCompatibility = JavaVersion.VERSION_21
//}
//kotlinOptions { jvmTarget = JavaVersion.VERSION_21 }
kotlin { jvmToolchain(21) }
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}


dependencies {
//    compileOnly(libs.lint.api)
//    compileOnly(libs.lint.checks)
    compileOnly("com.android.tools.lint:lint-api:30.0.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    }
    compileOnly("com.android.tools.lint:lint-checks:30.0.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    }

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    //testImplementation(libs.lint.api)
    testImplementation("com.android.tools.lint:lint-api:30.0.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    }
    testImplementation(libs.lint.tests.v3000)
    testImplementation(kotlin("reflect"))
}

tasks {
    withType<Jar> {
        manifest {
            attributes("Lint-Registry-v2" to "fi.iki.ede.safelinter.SafeIssueRegistry")
        }
    }
}
