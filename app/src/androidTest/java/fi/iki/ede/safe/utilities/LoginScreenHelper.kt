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
import kotlin.time.ExperimentalTime

interface LoginScreenHelper : NodeHelper {
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

    @ExperimentalTime
    fun getBiometricsCheckbox(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onNodeWithTag(TestTag.BIOMETRICS_CHECKBOX)

    @ExperimentalTime
    fun getBiometricsButton(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onNodeWithTag(TestTag.BIOMETRICS_BUTTON)

    @ExperimentalTime
    fun getLoginButton(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onNodeWithTag(TestTag.LOGIN_BUTTON)

    @ExperimentalTime
    fun getPasswordFields(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onAllNodesWithTag(TestTag.PASSWORD_PROMPT)

    @ExperimentalTime
    fun getMinimumPasswordLength(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getInteger(R.integer.password_minimum_length)
}