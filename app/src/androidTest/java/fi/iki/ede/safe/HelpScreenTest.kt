package fi.iki.ede.safe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.ui.activities.HelpScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class HelpScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<HelpScreen>()

    @Test
    fun whatever_never_works() {
        composeTestRule.onNodeWithTag("help").assertIsDisplayed()
    }
}
