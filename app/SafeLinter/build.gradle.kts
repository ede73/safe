plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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
