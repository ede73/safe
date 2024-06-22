package fi.iki.ede.safe.utilities

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.utilities.AutoMockingUtilities.Companion.mockIsBiometricsEnabled
import fi.iki.ede.safe.utilities.MockKeyStore.fakeEncryptedMasterKey
import fi.iki.ede.safe.utilities.MockKeyStore.fakeSalt
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass

/**
 * These special scenarios are logged out testing, usually in onCreate
 * with special start up conditions that cannot otherwise be tested
 * since @get:Rule loads the activity immediately before the test
 * and once the @Test code starts, it's too late to change on mock properties
 * onCreate is already DONE, actually even before @Before is run
 *
 * Test plan, verify that...
 * - initial site entries displayed [TODO:]
 * - moving site entry works [TODO:]
 * - adding site entry works [TODO:]
 * - deleting site entry works [TODO:]
 * - modifying site entry works [TODO:]
 * - TODO: test transition to LoginScreen after timeout
 */
open class LoggedOutTest {
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun beforeEachTest() {
        DBHelper4AndroidTest.justStoreSaltAndMasterKey(
            initializeMasterKey = fakeEncryptedMasterKey,
            initializeSalt = fakeSalt,
        )
        DBHelper4AndroidTest.initializeEverything(context)
        DBHelper4AndroidTest.configureDefaultTestDataModelAndDB()
    }

    fun transitionToLoginScreenIfNotLoggedIn(activityTestRule: AndroidComposeTestRule<*, *>) {
        activityTestRule.onAllNodesWithTag(TestTag.PASSWORD_PROMPT)[0]
            .assertIsDisplayed()
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun initialize() {
            MockKeyStore.mockKeyStore()

            mockkObject(BiometricsActivity)
            mockIsBiometricsEnabled { false }

            mockkObject(LoginHandler)
            every { LoginHandler.isLoggedIn() } returns false
        }

        @AfterClass
        @JvmStatic
        fun deInitialize() {
            unmockkAll()
        }
    }
}
