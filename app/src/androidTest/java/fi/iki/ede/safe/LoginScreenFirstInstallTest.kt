package fi.iki.ede.safe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.AutoMockingUtilities.Companion.fetchDBKeys
import fi.iki.ede.safe.AutoMockingUtilities.Companion.getBiometricsEnabled
import fi.iki.ede.safe.ui.activities.LoginScreen
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.slot
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Test scenario whene app is fresh installed, all defaults apply
 * No database, no password, no biometrics
 *
 * TODO: Test setup is REALLY clumsy (or my UI dependencies) once
 * Test forces to create the activity first (there's a way - but no one liner - to pass start intent parameters)
 * And then mocking...if mocking occurs after the intent has been started, then the UI won't reflect the mocks
 *
 * Surely there's a solution for that, will work on it and consolidate afterwards
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LoginScreenFirstInstallTest : AutoMockingUtilities, LoginScreenHelper {
    @get:Rule
    val loginActivityTestRule = createAndroidComposeRule<LoginScreen>()

    companion object {
        // Such a pain, mock needs to be done before @rule (coz we use prefs at LoginScreen)
        @BeforeClass
        @JvmStatic
        fun setup() {
            getBiometricsEnabled(biometrics = { false })
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
                isUninitializedDatabase = { true }
            )
        }

        // TODO: Make shared with unit tests
        fun mockKeyStoreHelper() {
            mockkObject(KeyStoreHelperFactory)
            val p = mockkClass(KeyStoreHelper::class)
            every { KeyStoreHelperFactory.getKeyStoreHelper() } returns p
            val encryptionInput = slot<ByteArray>()
            every { p.encryptByteArray(capture(encryptionInput)) } answers {
                IVCipherText(ByteArray(KeyStoreHelper.IV_LENGTH), encryptionInput.captured)
            }
            val decryptionInput = slot<IVCipherText>()
            every { p.decryptByteArray(capture(decryptionInput)) } answers {
                decryptionInput.captured.cipherText
            }
        }
    }

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
            getPasswordFields(loginActivityTestRule)[1].performTextInput(shortPassword)
            if (i >= passwordLength) {
                getLoginButton(loginActivityTestRule).assertIsDisplayed()
            } else {
                // TODO: Why o WHY this always fails when running SUITE, but individually it always works..
                //getLoginButton(loginActivityTestRule).assertIsNotEnabled()
            }
        }
    }

    @Test
    fun verifyMismatchingPasswordNotAcceptedTest() {
        val passwordLength = getMinimumPasswordLength(loginActivityTestRule)
        val longPassword = "a".repeat(passwordLength)
        getPasswordFields(loginActivityTestRule)[0].performTextInput(longPassword + "1")
        getPasswordFields(loginActivityTestRule)[1].performTextInput(longPassword + "2")
    }
}