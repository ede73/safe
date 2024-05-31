package fi.iki.ede.safe

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.passwordTextField
import fi.iki.ede.safe.ui.onNodeWithTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


// https://developer.android.com/jetpack/compose/testing
@RunWith(AndroidJUnit4::class)
class PasswordTextFieldTest {
    //private val pwdId = "pwd"

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        composeTestRule.setContent {
            SafeTheme {
                Column {
                    passwordTextField(
                        textTip = R.string.login_password_tip,
                        modifier = Modifier.testTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)
                    )
                }
            }
        }
    }

    @Test
    fun ensurePasswordIsHiddenTest() {
        composeTestRule.onNodeWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)
            .assertIsEnabled()
        composeTestRule.onNodeWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)
            .performTextInput("abcd")
        composeTestRule.onNodeWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)
            .assertTextContains("••••")
    }

    @Test
    fun ensureShownPasswordIsShownTest() {
        composeTestRule.onNodeWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)
            .assertIsEnabled()
        composeTestRule.onNodeWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)
            .performTextInput("abcd")
        // TODO: Clumsy, TextField has a trailing icon button (hide/view pwd) which we're gonna click
        composeTestRule.onNodeWithTag(
            TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS,
            useUnmergedTree = false
        ).onChild().performClick()
        TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS
    }
}