package fi.iki.ede.safe

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.activities.SiteEntryListScreen
import fi.iki.ede.safe.utilities.MockDBHelper
import fi.iki.ede.safe.utilities.MockDataModel
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test plan, verify that...
 * - initial site entries displayed [TODO:]
 * - moving site entry works [TODO:]
 * - adding site entry works [TODO:]
 * - deleting site entry works [TODO:]
 * - modifying site entry works [TODO:]
 * - TODO: test transition to LoginScreen after timeout
 */
@RunWith(AndroidJUnit4::class)
class SiteEntryListScreenTest {
    @get:Rule
    val siteEntryActivityTestRule = createAndroidComposeRule<SiteEntryListScreen>()

    val testDispatcher = StandardTestDispatcher()

    @Before
    fun beforeEachTest() {
        MockDBHelper.initializeBasicTestDataModel()
    }

    @Test
    fun empty() {

    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun initialize() {
            MockDataModel.mockAllDataModelNecessities()

            mockkObject(LoginHandler)
            every { LoginHandler.isLoggedIn() } returns true
        }

        @AfterClass
        @JvmStatic
        fun deInitialize() {
            unmockkAll()
        }
    }
}