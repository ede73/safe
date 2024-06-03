package fi.iki.ede.safe.utilities

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.LoginScreen
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.ui.onNodeWithTag

interface LoginScreenHelper {
    fun SemanticsNodeInteraction.assertIsNotChecked(): SemanticsNodeInteraction = assert(
        SemanticsMatcher.expectValue(
            SemanticsProperties.ToggleableState,
            androidx.compose.ui.state.ToggleableState.Off
        )
    )

    fun SemanticsNodeInteraction.assertIsChecked(): SemanticsNodeInteraction = assert(
        SemanticsMatcher.expectValue(
            SemanticsProperties.ToggleableState,
            androidx.compose.ui.state.ToggleableState.On
        )
    )

    fun getBiometricsCheckbox(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_BIOMETRICS_CHECKBOX)

    fun getBiometricsButton(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_BIOMETRICS_BUTTON)

    fun getLoginButton(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_LOGIN_BUTTON)

    fun getPasswordFields(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_PROMPT)

    fun getMinimumPasswordLength(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getInteger(R.integer.password_minimum_length)
}