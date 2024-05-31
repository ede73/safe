package fi.iki.ede.safe

import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

@Ignore
class ApplicationSuffixTest {

    @Test
    fun verifyApplicationIdSuffix() {
        val expectedSuffix = ".safetest"
        val actualApplicationId = BuildConfig.APPLICATION_ID
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
