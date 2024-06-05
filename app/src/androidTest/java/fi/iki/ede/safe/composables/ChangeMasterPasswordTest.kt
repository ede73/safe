package fi.iki.ede.safe.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.EnterNewPassword
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.ui.onNodeWithTag
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.utilities.NodeHelper
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Test changing master password, this was already doubly broken
 * New master key generation had wrong parameter
 * Actual EnterNewPassword component didn't accept anything ever
 */
@RunWith(AndroidJUnit4::class)
class ChangeMasterPasswordTest : NodeHelper {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() = composeTestRule.setContent {
        SafeTheme {
            Column {
                EnterNewPassword {
                    val (oldPassword, newPassword) = it

                }
            }
        }
    }

    @Test
    fun ensurePasswordIsHiddenTest() {
        val passwordFields =
            composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_TEXT_FIELD)
        val button =
            composeTestRule.onNodeWithTag(TestTag.TEST_TAG_CHANGE_PASSWORD_OK)
        passwordFields[0].assertIsDisplayed()
        passwordFields[1].assertIsDisplayed()
        passwordFields[2].assertIsDisplayed()
        button.assertIsNotEnabled()
        passwordFields[0].performTextInput("aaaaaaaa")
        button.assertIsNotEnabled()
        passwordFields[1].performTextInput("aaaaaaaa")
        button.assertIsNotEnabled()
        passwordFields[2].performTextInput("aaaaaaaa")
        button.assertIsNotEnabled()
        passwordFields[2].performTextClearance()
        button.assertIsNotEnabled()
        passwordFields[2].performTextInput("bbbbbbbb")
        button.assertIsNotEnabled()
        passwordFields[1].performTextClearance()
        button.assertIsNotEnabled()
        passwordFields[1].performTextInput("bbbbbbbb")
        button.assertIsEnabled()
    }
}