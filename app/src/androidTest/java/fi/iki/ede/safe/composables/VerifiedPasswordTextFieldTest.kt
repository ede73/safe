package fi.iki.ede.safe.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.VerifiedPasswordTextField
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.SafeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Test plan, verify that...
 * - ensure both password fields are displayed [ensureVerificationFieldShownTest]
 * - entered password is hidden [ensurePasswordIsHiddenTest]
 * - password is shown when clicked reveal button [ensureShownPasswordIsShownTest]
 * - verification password is shown when clicked reveal button [ensureShownVerificationPasswordIsShownTest]
 * - zoom TODO:
 */
@RunWith(AndroidJUnit4::class)
class VerifiedPasswordTextFieldTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() = composeTestRule.setContent {
        SafeTheme {
            Column {
                VerifiedPasswordTextField(
                    showVerification = true,
                    textTip = R.string.login_password_tip,
                    verifyPassword = R.string.login_verify_password_tip,
                    modifier = Modifier.testTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
                )
            }
        }
    }

    @Test
    fun ensureVerificationFieldShownTest() {
        composeTestRule.onAllNodesWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)[0].assertIsEnabled()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)[1].assertIsEnabled()
            .assertIsDisplayed()
    }

    @Test
    fun ensurePasswordIsHiddenTest() {
        composeTestRule.onAllNodesWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)[0].assertIsEnabled()
            .performTextInput("abcd")
        composeTestRule.onAllNodesWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)[0].assertTextContains(
            "••••"
        )
    }

    @Test
    fun ensureShownPasswordIsShownTest() {
        val passwordText = "abcd"
        composeTestRule.onAllNodesWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)[0].assertIsEnabled()
            .performTextInput(passwordText)
        composeTestRule.onAllNodesWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)[0].onChild()
            .performClick()
        composeTestRule.onAllNodesWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)[0].assertTextContains(
            passwordText
        )
    }

    @Test
    fun ensureShownVerificationPasswordIsShownTest() {
        val passwordText = "abcd"
        composeTestRule.onAllNodesWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)[1].assertIsEnabled()
            .performTextInput(passwordText)
        composeTestRule.onAllNodesWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)[1].onChild()
            .performClick()
        composeTestRule.onAllNodesWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)[1].assertTextContains(
            passwordText
        )
    }
}