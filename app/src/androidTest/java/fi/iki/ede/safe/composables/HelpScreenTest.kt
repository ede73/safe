package fi.iki.ede.safe.composables

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.HelpScreen
import fi.iki.ede.safe.ui.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class HelpScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<HelpScreen>()

    @Test
    fun helpScreenOpens() {
        composeTestRule.onNodeWithTag(TestTag.TEST_TAG_HELP)
            .assertIsDisplayed()
    }
}
