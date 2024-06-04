package fi.iki.ede.safe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.SiteEntrySearchScreen
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.ui.onNodeWithTag
import fi.iki.ede.safe.utilities.MockDBHelper
import fi.iki.ede.safe.utilities.MockDBHelper.DEFAULT_2ND_CATEGORY
import fi.iki.ede.safe.utilities.MockDataModel
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test plan, verify that...
 * - finds the passwords by description [trySearch]
 * - finds the passwords by encrypted passwords TODO:
 * - finds the passwords by encrypted notes TODO:
 * - finds the passwords by encrypted website TODO:
 * - finds the passwords by encrypted username TODO:
 * - TODO: test transition to LoginScreen after timeout
 */
@RunWith(AndroidJUnit4::class)
class SearchListScreenTest {
    @get:Rule
    val siteEntryActivityTestRule = createAndroidComposeRule<SiteEntrySearchScreen>()

    @Before
    fun beforeEachTest() {
        MockDBHelper.initializeBasicTestDataModel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun trySearch() = runTest {
        siteEntryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_SEARCH_TEXT_FIELD)
            .assertIsDisplayed()
        // DEFAULT_2ND_CATEGORY -> category name is part of default passwords
        siteEntryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_SEARCH_TEXT_FIELD)
            .performTextInput(DEFAULT_2ND_CATEGORY)
        siteEntryActivityTestRule.waitForIdle()

        advanceUntilIdle()

        siteEntryActivityTestRule.waitUntil {
            siteEntryActivityTestRule.onAllNodesWithTag(
                TestTag.TEST_TAG_SEARCH_MATCH,
                useUnmergedTree = true
            ).fetchSemanticsNodes().size == 2
        }
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
