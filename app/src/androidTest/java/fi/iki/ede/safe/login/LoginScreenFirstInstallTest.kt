package fi.iki.ede.safe.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.activities.LoginScreen
import fi.iki.ede.safe.utilities.AutoMockingUtilities
import fi.iki.ede.safe.utilities.AutoMockingUtilities.Companion.mockIsBiometricsEnabled
import fi.iki.ede.safe.utilities.AutoMockingUtilities.Companion.mockIsBiometricsInitialized
import fi.iki.ede.safe.utilities.AutoMockingUtilities.Companion.mockIsFirstTimeLogin
import fi.iki.ede.safe.utilities.LoginScreenHelper
import fi.iki.ede.safe.utilities.MyResultLauncher
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test scenario when app is fresh installed, all defaults apply
 * No database, no password, no biometrics
 *
 * Test plan, verify that...
 * - Verify both password fields are displayed [verifyPasswordPromptPresentTest]
 * - TODO: Move to VerifiedPasswordTextFieldTest Verify password minimum length is required [verifyPasswordLengthRequirementTest]
 * - TODO: Move to VerifiedPasswordTextFieldTest Verify first login only succeeds with matching passwords [verifyMismatchingPasswordNotAcceptedTest]
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenFirstInstallTest : AutoMockingUtilities, LoginScreenHelper {
    @get:Rule
    val loginActivityTestRule = createAndroidComposeRule<LoginScreen>()

    @After
    fun clearAll() {
        MyResultLauncher.afterEachTest()
    }

    @Test
    fun verifyPasswordPromptPresentTest() {
        mockIsBiometricsEnabled { true }
        getPasswordFields(loginActivityTestRule)[0].assertIsDisplayed()
        getPasswordFields(loginActivityTestRule)[1].assertIsDisplayed()
        getPasswordFields(loginActivityTestRule)[0].assertIsFocused()
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        getBiometricsButton(loginActivityTestRule).assertDoesNotExist()
        getBiometricsCheckbox(loginActivityTestRule).assertIsDisplayed()
    }

    @Test
    fun verifyPasswordLengthRequirementTest() {
        val passwordLength = getMinimumPasswordLength(loginActivityTestRule)
        // going thru whole range is way too slow, just 1, and threshold (+-1)
        for (i in listOf(1, passwordLength - 1, passwordLength, passwordLength + 1)) {
            val shortPassword = "a".repeat(i)

            getPasswordFields(loginActivityTestRule)[0].performTextInput(shortPassword)
            // there's now mismatch, MUST not accept
            getLoginButton(loginActivityTestRule).assertIsNotEnabled()
            getPasswordFields(loginActivityTestRule)[1].performTextInput(shortPassword)
            getLoginButton(loginActivityTestRule).assertIsDisplayed()

            if (shortPassword.length >= passwordLength) {
                getLoginButton(loginActivityTestRule).assertIsEnabled()
            } else {
                getLoginButton(loginActivityTestRule).assertIsNotEnabled()
            }
            getPasswordFields(loginActivityTestRule)[0].performTextClearance()
            getPasswordFields(loginActivityTestRule)[1].performTextClearance()
        }
    }

    @Test
    fun verifyMismatchingPasswordNotAcceptedTest() {
        val passwordLength = getMinimumPasswordLength(loginActivityTestRule)
        val longPassword = "a".repeat(passwordLength)
        getPasswordFields(loginActivityTestRule)[0].performTextInput(longPassword + "1")
        getLoginButton(loginActivityTestRule).assertIsNotEnabled()
        getPasswordFields(loginActivityTestRule)[1].performTextInput(longPassword + "2")
        getLoginButton(loginActivityTestRule).assertIsNotEnabled()
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun initialize() {
            mockkObject(Preferences)
            mockkObject(BiometricsActivity)
            // first time running
            mockIsFirstTimeLogin { true }
            mockIsBiometricsInitialized { false }
        }

        @AfterClass
        @JvmStatic
        fun deInitialize() {
            unmockkAll()
        }
    }
}