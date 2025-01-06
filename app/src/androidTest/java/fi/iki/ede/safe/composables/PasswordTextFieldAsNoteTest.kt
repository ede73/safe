package fi.iki.ede.safe.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.PasswordTextField
import fi.iki.ede.safe.ui.onNodeWithTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.utilities.NodeHelper
import fi.iki.ede.theme.SafeTheme
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
class PasswordTextFieldAsNoteTest : NodeHelper {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() = composeTestRule.setContent {
        SafeTheme {
            Column {
                PasswordTextField(
                    textTip = R.string.password_entry_note_tip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS),
                    inputValue = "line1\nline2",
                    onValueChange = { },
                    enableZoom = false,
                    singleLine = false,
                    maxLines = 22,
                    highlight = false,
                )
            }
        }
    }

    @Test
    fun ensureNoteIsHiddenTest() {
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .assertIsEnabled()
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .assertTextContains("•••••••••••")
    }

    @Test
    fun ensureShownNoteIsShownTest() {
        val noteText = "abcd"
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .assertIsEnabled()
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .performTextInput(noteText)
        composeTestRule.onNodeWithTag(
            TestTag.PASSWORD_COMPOSABLE_IN_TESTS,
            useUnmergedTree = false
        ).onChild().performClick()
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .assertTextContains(noteText + "line1\nline2")
    }

    // we had issue with line feeds - being deleted (it was viewmodel, not the component)
    @Test
    fun ensureLineFeedCanBeEnteredCorrectlyTest() {
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .assertIsEnabled()
        composeTestRule.onNodeWithTag(
            TestTag.PASSWORD_COMPOSABLE_IN_TESTS,
            useUnmergedTree = false
        ).onChild().performClick()
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .performTextClearance()
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .performTextInput("Hi!\n")
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .performTextInput("It's\n")
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .performTextInput("me!\n")
        fun backspace() {
            composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
                .performKeyPress(
                    androidx.compose.ui.input.key.KeyEvent(
                        NativeKeyEvent(
                            android.view.KeyEvent(
                                android.view.KeyEvent.ACTION_DOWN,
                                android.view.KeyEvent.KEYCODE_DEL
                            )
                        )
                    )
                )
        }
        backspace()
        backspace()
        backspace()
        backspace()
        composeTestRule.onNodeWithTag(TestTag.PASSWORD_COMPOSABLE_IN_TESTS)
            .assertTextContains("Hi!\nIt's\n")
    }
}