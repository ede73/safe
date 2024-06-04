package fi.iki.ede.safe.utilities

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import org.junit.rules.TestRule

/**
 * There are so many utilities and functionalities
 * for debugging that can't recall all, collecting
 * useful ones here
 */
interface NodeHelper {
    // Without unmergedTree dump may be imcomplete
    fun <T : TestRule, C : ComponentActivity> dumpComposeTestRuleHierarchy(
        composeTestRule: ComposeTestRule,
        tag: String = "ComposeHierarchy"
    ) =
        composeTestRule.onRoot(useUnmergedTree = true).printToLog(tag)
}
