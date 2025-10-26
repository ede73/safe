package fi.iki.ede.safe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ApplicationSuffixTest {

    @Test
    fun verifyUnitTestSetup() {
        assert("true" == System.getProperty("test")) {
            """
# Make sure we're flagged as test mode, add to build.gradle.kts :app
# Handy when debugging mocking issues etc., in code you can:
#    require(System.getProperty("test") == "true") { "Mocking failed" }
tasks.withType<Test> {
    systemProperty("test", "true")
}
            """.trimIndent()
        }
    }

    @Disabled("fix later")
    fun verifyApplicationIdSuffix() {
        val expectedSuffix = ".test"
        val actualApplicationId = BuildConfig.APPLICATION_ID
        val hasCorrectSuffix = actualApplicationId.endsWith(expectedSuffix)

        assertEquals(
            true,
            hasCorrectSuffix,
            """
Current ApplicationId: $actualApplicationId
To protect debug experience, tests MUST have .safetest suffix.

Make sure build.gradle has:
android {
    buildTypes {
            debug {...}
            create("safetest") {
                initWith(buildTypes.getByName("debug"))
                applicationIdSuffix = ".safetest"
            }
    }
    testBuildType = "safetest"
}
            """,
        )
    }
}
