package fi.iki.ede.safe

import android.app.KeyguardManager
import android.content.Context
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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

    @Test
    fun unlockDevice() {
        val keyguardManager =
            InstrumentationRegistry.getInstrumentation().context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardLocked) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            if (!device.isScreenOn) {
                device.wakeUp()
            }
            device.swipe(100, 1000, 100, 500, 2)
            device.pressKeyCode(KeyEvent.KEYCODE_0)
            device.pressKeyCode(KeyEvent.KEYCODE_0)
            device.pressKeyCode(KeyEvent.KEYCODE_0)
            device.pressKeyCode(KeyEvent.KEYCODE_0)
            device.pressEnter()
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
