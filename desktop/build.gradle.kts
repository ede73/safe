plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.11.0"
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":crypto"))
    implementation(project(":app:db"))
    implementation(project(":app:cryptoobjects"))
    implementation(project(":dateutils"))
    implementation(project(":logger"))

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

val copySharedUi by tasks.registering(Copy::class) {
    from("../app/src/main/java/fi/iki/ede/safe/ui/composable") {
        include("CategoryList.kt")
        include("SiteEntryList.kt")
        include("SiteEntryRowHeader.kt")
    }
    into(layout.buildDirectory.dir("generated/sharedUi/fi/iki/ede/safe/ui/composable"))
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(layout.buildDirectory.dir("generated/sharedUi"))
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(copySharedUi)
}

compose.desktop {
    application {
        mainClass = "fi.iki.ede.safe.desktop.MainKt"
    }
}
