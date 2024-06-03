package fi.iki.ede.safe

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
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.verifiedPasswordTextField
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


// https://developer.android.com/jetpack/compose/testing
@RunWith(AndroidJUnit4::class)
class VerifiedPasswordTextFieldTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        composeTestRule.setContent {
            SafeTheme {
                Column {
                    verifiedPasswordTextField(
                        showVerification = true,
                        textTip = R.string.login_password_tip,
                        verifyPassword = R.string.login_verify_password_tip,
                        modifier = Modifier.testTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)
                    )
                }
            }
        }
    }

    @Test
    fun ensureVerificationFieldShownTest() {
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[0].assertIsEnabled()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[1].assertIsEnabled()
            .assertIsDisplayed()
    }

    @Test
    fun ensurePasswordIsHiddenTest() {
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[0].assertIsEnabled()
            .performTextInput("abcd")
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[0].assertTextContains(
            "••••"
        )
    }

    @Test
    fun ensureShownPasswordIsShownTest() {
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[0].assertIsEnabled()
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[0].performTextInput(
            "abcd"
        )
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[0].onChild()
            .performClick()
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[0].assertTextContains(
            "abcd"
        )
    }

    @Test
    fun ensureShownVerificationPasswordIsShownTest() {
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[1].assertIsEnabled()
            .performTextInput("abcd")
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[1].onChild()
            .performClick()
        composeTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_COMPOSABLE_IN_TESTS)[1].assertTextContains(
            "abcd"
        )
    }
}