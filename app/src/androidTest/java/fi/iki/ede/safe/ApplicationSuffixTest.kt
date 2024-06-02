package fi.iki.ede.safe

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

class ApplicationSuffixTest {

    @Test
    fun verifyUnitTestSetup() {
        val testMode = InstrumentationRegistry.getArguments().getString("test")

        assert("true" == testMode) {
            """
# Make sure we're flagged as test mode, add to build.gradle.kts :app
# Handy when debugging mocking issues etc., in code you can:
#    require(System.getProperty("test") == "true") { "Mocking failed" }
defaultConfig {
  testInstrumentationRunnerArguments["test"] = "true"
}
# And you need @Before/@Before class in the androidTest
#            if (InstrumentationRegistry.getArguments().getString("test") == "true")
#                System.setProperty("test", "true")

            """.trimIndent()
        }
    }

    @Ignore
    fun verifyApplicationIdSuffix() {
        val expectedSuffix = ".test"
        val actualApplicationId =
            InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val hasCorrectSuffix = actualApplicationId.endsWith(expectedSuffix)

        assertEquals(
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
            true,
            hasCorrectSuffix
        )
    }
}
