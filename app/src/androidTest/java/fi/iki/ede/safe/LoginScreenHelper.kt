package fi.iki.ede.safe

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.safe.ui.activities.LoginScreen

interface LoginScreenHelper {
    fun getBiometricsCheckbox(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onNodeWithTag(LoginScreen.TESTTAG_BIOMETRICS_CHECKBOX)

    fun getBiometricsButton(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onNodeWithTag(LoginScreen.TESTTAG_BIOMETRICS_BUTTON)

    fun getLoginButton(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onNodeWithTag(LoginScreen.TESTTAG_LOGIN_BUTTON)

    fun getPasswordFields(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        componentActivityTestRule.onAllNodesWithTag(LoginScreen.TESTTAG_PASSWORD_PROMPT)

    fun getMinimumPasswordLength(componentActivityTestRule: AndroidComposeTestRule<ActivityScenarioRule<LoginScreen>, LoginScreen>) =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getInteger(R.integer.password_minimum_length)
}