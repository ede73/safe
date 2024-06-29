plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)

    //testImplementation("com.android.tools.lint:lint-tests:31.5.0")
}

kotlin {
    jvmToolchain(8) // Set Kotlin to target JVM 1.8
}

tasks {
    withType<Jar> {
        manifest {
            attributes("Lint-Registry-v2" to "fi.iki.ede.safelinter.SafeIssueRegistry")
        }
    }
}
