package fi.iki.ede.safe

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.ui.composable.passwordTextField
import fi.iki.ede.safe.ui.theme.SafeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


// https://developer.android.com/jetpack/compose/testing
@RunWith(AndroidJUnit4::class)
class PasswordTextFieldTest {
    private val pwdId = "pwd"

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        composeTestRule.setContent {
            SafeTheme {
                Column {
                    passwordTextField(
                        textTip = R.string.login_password_tip,
                        modifier = Modifier.testTag(pwdId)
                    )
                }
            }
        }
    }

    @Test
    fun ensurePasswordIsHiddenTest() {
        composeTestRule.onNodeWithTag(pwdId).assertIsEnabled()
        composeTestRule.onNodeWithTag(pwdId).performTextInput("abcd")
        composeTestRule.onNodeWithTag(pwdId).assertTextContains("••••")
    }

    @Test
    fun ensureShownPasswordIsShownTest() {
        composeTestRule.onNodeWithTag(pwdId).assertIsEnabled()
        composeTestRule.onNodeWithTag(pwdId).performTextInput("abcd")
        // TODO: Clumsy, TextField has a trailing icon button (hide/view pwd) which we're gonna click
        composeTestRule.onNodeWithTag(pwdId, useUnmergedTree = false).onChild().performClick()
        composeTestRule.onNodeWithTag(pwdId).assertTextContains("abcd")
    }
}