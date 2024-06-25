package fi.iki.ede.safe.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.PasswordTextField
import fi.iki.ede.safe.ui.onNodeWithTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.utilities.NodeHelper
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Test plan, verify that...
 * - entered password is hidden [ensurePasswordIsHiddenTest]
 * - password is shown when clicked reveal button [ensureShownPasswordIsShownTest]
 * - zoom TODO:
 */
@RunWith(AndroidJUnit4::class)
class SiteEntryTextFieldTest : NodeHelper {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() = composeTestRule.setContent {
        SafeTheme {
            Column {
                PasswordTextField(
                    textTip = R.string.login_password_tip,
                    modifier = Modifier.testTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
                )
            }
        }
    }

    @Test
    fun ensurePasswordIsHiddenTest() {
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .assertIsEnabled()
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .performTextInput("abcd")
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .assertTextContains("••••")
    }

    @Test
    fun ensureShownPasswordIsShownTest() {
        val passwordText = "abcd"
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .assertIsEnabled()
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .performTextInput(passwordText)
        composeTestRule.onNodeWithTag(
            TestTag.PASSWORD_COMPOSABLE_IN_TESTS,
            useUnmergedTree = false
        ).onChild().performClick()
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .assertTextContains(passwordText)
    }
}