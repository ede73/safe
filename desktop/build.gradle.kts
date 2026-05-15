plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.11.0"
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":crypto"))
    implementation(project(":dateutils"))
    implementation(project(":logger"))

    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "fi.iki.ede.safe.desktop.MainKt"
    }
}
