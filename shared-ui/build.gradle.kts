import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

val generateIosStrings = tasks.register("generateIosStrings") {
    val stringsFile = file("../app/src/main/res/values/strings.xml")
    val stringsFiFile = file("../app/src/main/res/values-fi/strings.xml")
    val outputDir = file("build/generated/strings/iosMain/kotlin")
    val outputFile = file("$outputDir/fi/iki/ede/safe/ui/composable/GeneratedStrings.kt")

    inputs.files(stringsFile, stringsFiFile)
    outputs.dir(outputDir)

    doLast {
        fun parseStrings(xmlFile: File): Pair<Map<String, String>, Map<String, Map<String, String>>> {
            val strings = mutableMapOf<String, String>()
            val plurals = mutableMapOf<String, Map<String, String>>()
            if (!xmlFile.exists()) return Pair(strings, plurals)
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(xmlFile)
            doc.documentElement.normalize()

            val stringNodes = doc.getElementsByTagName("string")
            for (i in 0 until stringNodes.length) {
                val element = stringNodes.item(i) as Element
                val name = element.getAttribute("name")
                val value = element.textContent
                strings[name] = value
            }

            val pluralNodes = doc.getElementsByTagName("plurals")
            for (i in 0 until pluralNodes.length) {
                val element = pluralNodes.item(i) as Element
                val name = element.getAttribute("name")
                val itemMap = mutableMapOf<String, String>()
                val itemNodes = element.getElementsByTagName("item")
                for (j in 0 until itemNodes.length) {
                    val itemElement = itemNodes.item(j) as Element
                    val quantity = itemElement.getAttribute("quantity")
                    val value = itemElement.textContent
                    itemMap[quantity] = value
                }
                plurals[name] = itemMap
            }
            return Pair(strings, plurals)
        }

        val (defStrings, defPlurals) = parseStrings(stringsFile)
        val (fiStrings, fiPlurals) = parseStrings(stringsFiFile)

        outputFile.parentFile.mkdirs()

        fun escapeString(str: String): String =
            str.replace("\\", "\\\\")
               .replace("\"", "\\\"")
               .replace("\n", "\\n")
               .replace("\r", "")
               .replace("$", "\\$")

        val sb = StringBuilder()
        sb.append("package fi.iki.ede.safe.ui.composable\n\n")
        sb.append("object GeneratedStrings {\n")

        sb.append("    val defaultStrings = mapOf<String, String>(\n")
        defStrings.forEach { (k, v) ->
            sb.append("        \"$k\" to \"${escapeString(v)}\",\n")
        }
        sb.append("    )\n\n")

        sb.append("    val defaultPlurals = mapOf<String, Map<String, String>>(\n")
        defPlurals.forEach { (k, map) ->
            sb.append("        \"$k\" to mapOf(\n")
            map.forEach { (qk, qv) ->
                sb.append("            \"$qk\" to \"${escapeString(qv)}\",\n")
            }
            sb.append("        ),\n")
        }
        sb.append("    )\n\n")

        sb.append("    val fiStrings = mapOf<String, String>(\n")
        fiStrings.forEach { (k, v) ->
            sb.append("        \"$k\" to \"${escapeString(v)}\",\n")
        }
        sb.append("    )\n\n")

        sb.append("    val fiPlurals = mapOf<String, Map<String, String>>(\n")
        fiPlurals.forEach { (k, map) ->
            sb.append("        \"$k\" to mapOf(\n")
            map.forEach { (qk, qv) ->
                sb.append("            \"$qk\" to \"${escapeString(qv)}\",\n")
            }
            sb.append("        ),\n")
        }
        sb.append("    )\n")
        sb.append("}\n")

        outputFile.writeText(sb.toString())
    }
}

kotlin {
    androidLibrary {
        namespace = "fi.iki.ede.safe.ui"
        /*
        buildFeatures {
            compose = true
        }
        */
    }
    jvm("desktop")
    iosArm64 {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(libs.compose.ui.tooling.preview)
                implementation(compose.material)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(project(":crypto"))
                implementation(project(":logger"))
                implementation(project(":app:datamodel"))
                implementation(project(":app:db"))
                implementation(project(":app:cryptoobjects"))
                implementation(project(":app:preferences"))
                implementation(project(":app:theme"))
                implementation(project(":app:backup"))
                implementation(project(":gpm"))
                implementation(project(":dateutils"))
                implementation(libs.kotlinx.datetime)
                implementation(libs.okio)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val iosMain by creating {
            kotlin.srcDirs(generateIosStrings)
        }
    }
}
