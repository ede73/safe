package fi.iki.ede.safe

import android.app.Activity.RESULT_CANCELED
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.AutoMockingUtilities.Companion.fetchDBKeys
import fi.iki.ede.safe.AutoMockingUtilities.Companion.mockIsBiometricsEnabled
import fi.iki.ede.safe.AutoMockingUtilities.Companion.mockIsBiometricsInitialized
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.activities.LoginScreen
import io.mockk.every
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test scenario when app is fresh installed, all defaults apply
 * No database, no password, no biometrics
 */
//@LargeTest
@RunWith(AndroidJUnit4::class)
class LoginScreenFirstInstallTest : AutoMockingUtilities, LoginScreenHelper {

    @After
    fun clearAll() {
        MyResultLauncher.afterEachTest()
    }

    @get:Rule
    val loginActivityTestRule = createAndroidComposeRule<LoginScreen>()

    @Test
    fun verifyPasswordPromptPresentTest() {
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

    @Test
    fun newLoginAndBioLaunchedWhenUnchecked() {
        every { BiometricsActivity.getRegistrationIntent(any()) } answers { Intent() }

        properPasswordLogin(false)

        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 0) { BiometricsActivity.getRegistrationIntent(any()) }
        verify(exactly = 1) { CategoryListScreen.startMe(any()) }
    }

    @Test
    fun newLoginAndBioLaunchedWhenChecked() {
        mockIsBiometricsEnabled { true }
        mockIsBiometricsInitialized { false }
//        val expectedIntent = Intent(
//            InstrumentationRegistry.getInstrumentation().targetContext,
//            BiometricsActivity::class.java
//        )
//        every { BiometricsActivity.getRegistrationIntent(any()) } returns expectedIntent

        MyResultLauncher.registerTestLaunchResult(TestTag.TEST_TAG_LOGIN_BIOMETRICS_REGISTER) {
            println("Cancelling biometrics")
            ActivityResult(RESULT_CANCELED, null)
        }
        // login and pop biometric prompt (the mock intent)
        properPasswordLogin(true)

        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 1) { BiometricsActivity.getRegistrationIntent(any()) }

        MyResultLauncher.fetchResults()

        val la =
            MyResultLauncher.getLaunchedIntentsAndCallback(TestTag.TEST_TAG_LOGIN_BIOMETRICS_REGISTER)
        assert(la.second.size == 1) {
            "Only one intent expected, got ${la.second.size}"
        }
        // Verify biometrics indeed was launched
        assert(la.second[0].component?.className == BiometricsActivity::class.qualifiedName) {
            "${la.second[0].component?.className} != ${BiometricsActivity::class.qualifiedName}"
        }
    }

    // TODO: add also test case for failed biometrics
    // TODO: cancelled biometrics

    private fun properPasswordLogin(biometricsRegister: Boolean) {
        mockkObject(LoginHandler)
        mockkObject(CategoryListScreen)
        every { LoginHandler.passwordLogin(any(), any()) } returns true
        every { LoginHandler.firstTimeLogin(any(), any()) } just runs
        every { CategoryListScreen.startMe(any()) } just runs

        // for the FIRST TIME init we do display biometrics
        getBiometricsCheckbox(loginActivityTestRule).assertIsDisplayed()

        val biometricsChecked =
            getBiometricsCheckbox(loginActivityTestRule).fetchSemanticsNode().config[SemanticsProperties.ToggleableState]

        if (biometricsRegister) {
            if (biometricsChecked == androidx.compose.ui.state.ToggleableState.Off) {
                getBiometricsCheckbox(loginActivityTestRule).performClick()
                getBiometricsCheckbox(loginActivityTestRule).assertIsChecked()
            }
        } else {
            if (biometricsChecked == androidx.compose.ui.state.ToggleableState.On) {
                getBiometricsCheckbox(loginActivityTestRule).performClick()
                getBiometricsCheckbox(loginActivityTestRule).assertIsNotChecked()
            }
        }
        getPasswordFields(loginActivityTestRule)[0].performTextInput("quite_a_password")
        getPasswordFields(loginActivityTestRule)[1].performTextInput("quite_a_password")
        getLoginButton(loginActivityTestRule).performClick()

        verify(exactly = 1) { LoginHandler.firstTimeLogin(any(), any()) }
    }

    companion object {

        // Such a pain, mock needs to be done before @rule (coz we use prefs at LoginScreen)
        @BeforeClass
        @JvmStatic
        fun setup() {
            MyResultLauncher.beforeClassJvmStaticSetup()

            // TODO: MOCK THIS TOO!
            BiometricsActivity.clearBiometricKeys()

            mockIsBiometricsEnabled(biometrics = { false })
            mockKeyStoreHelper()

            val ks = KeyStoreHelperFactory.getKeyStoreHelper()

            fetchDBKeys(
                masterKey = { IVCipherText.getEmpty() },
                salt = { Salt.getEmpty() },
                fetchPasswordsOfCategory = {
                    listOf()
                },
                fetchCategories = {
                    listOf(DecryptableCategoryEntry().let {
                        it.id = 1
                        it.encryptedName = ks.encryptByteArray("one".toByteArray())
                        it
                    })
                },
                isFirstTimeLogin = { true }
            )
        }

        // TODO: Make shared with unit tests
        fun mockKeyStoreHelper() {
            mockkObject(KeyStoreHelperFactory)
            val p = mockkClass(KeyStoreHelper::class)
            every { KeyStoreHelperFactory.getKeyStoreHelper() } returns p
            val encryptionInput = slot<ByteArray>()
            every { p.encryptByteArray(capture(encryptionInput)) } answers {
                IVCipherText(ByteArray(CipherUtilities.IV_LENGTH), encryptionInput.captured)
            }
            val decryptionInput = slot<IVCipherText>()
            every { p.decryptByteArray(capture(decryptionInput)) } answers {
                decryptionInput.captured.cipherText
            }
        }
    }
}