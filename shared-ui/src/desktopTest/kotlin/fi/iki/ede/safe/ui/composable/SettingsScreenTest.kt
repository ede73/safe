package fi.iki.ede.safe.ui.composable

import androidx.compose.ui.test.*
import fi.iki.ede.preferences.Preferences
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * SettingsScreenTest: Desktop JVM UI test verifying SettingsScreen display.
 */
class SettingsScreenTest {

    @OptIn(ExperimentalTestApi::class, kotlin.time.ExperimentalTime::class)
    @Test
    fun verifySettingsScreenDisplays() = runComposeUiTest {
        // Redirect user.home to a temporary directory to isolate Datastore preferences file
        val tempDir = java.nio.file.Files.createTempDirectory("test_settings_home").toFile()
        tempDir.deleteOnExit()
        val originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.absolutePath)

        try {
            Preferences.initialize()

            var editExtensionsCalled = false
            setContent {
                SettingsScreen(
                    onBack = {},
                    onEditExtensions = { editExtensionsCalled = true }
                )
            }

            // Assert security section header is displayed
            onNodeWithText("Security").assertIsDisplayed()

            // Assert default username preference field is displayed
            onNodeWithText("Default username").assertIsDisplayed()

            // Click Edit Extensions button and verify callback
            onNodeWithText("Edit Extensions").performScrollTo().performClick()
            waitForIdle()
            assertTrue(editExtensionsCalled)
        } finally {
            if (originalUserHome != null) {
                System.setProperty("user.home", originalUserHome)
            }
        }
    }
}
