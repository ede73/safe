plugins {
    kotlin("jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":crypto"))
    implementation(project(":app:db"))
    implementation(project(":app:cryptoobjects"))
    implementation(project(":app:backup"))
    implementation(project(":gpm"))
    implementation(project(":dateutils"))
    implementation(project(":app:preferences"))
    implementation(project(":shared-ui"))

    implementation(libs.okio)

    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    getByName("test") {
        java.srcDir("../crypto/src/testFixtures/kotlin")
    }
}
tasks.test {
    useJUnitPlatform()
}


compose.desktop {
    application {
        mainClass = "fi.iki.ede.safe.desktop.MainKt"
    }
}
