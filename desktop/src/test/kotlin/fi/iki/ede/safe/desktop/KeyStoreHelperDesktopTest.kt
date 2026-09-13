@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.desktop

import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KeyStoreHelperDesktopTest {

    @Test
    fun testCreateNewKeyAndEncryptDecrypt() {
        val password = Password("TestPassword123!")
        val (salt, cipheredKey) = KeyStoreHelper.createNewKey(password)

        assertNotNull(salt)
        assertNotNull(cipheredKey)
        assertEquals(8, salt.salt.size)

        // Test encryption and decryption with helper
        val helper = KeyStoreHelperFactory.provideKeyStoreHelper
        assertNotNull(helper)

        val plaintext = "Secret category or password entry"
        val encrypted = plaintext.encrypt()
        assertNotNull(encrypted)
        assertTrue(encrypted.cipherText.isNotEmpty())
        assertTrue(encrypted.iv.isNotEmpty())

        val decrypted = encrypted.decrypt()
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun testImportExistingEncryptedMasterKey() {
        val password = Password("SecureMasterPassword456!")
        val (salt, cipheredKey) = KeyStoreHelper.createNewKey(password)

        // Reset helper
        val plaintext = "Sensitive data to decrypt later"
        val encrypted = plaintext.encrypt()

        // Re-import existing encrypted master key with password
        KeyStoreHelper.importExistingEncryptedMasterKey(
            SaltedPassword(salt, password),
            cipheredKey
        )

        val decrypted = encrypted.decrypt()
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun testBiometricKeyLoginFlow() {
        val password = Password("BiometricPassword789!")
        KeyStoreHelper.createNewKey(password)

        val bioEncryptedKey = KeyStoreHelper.getBiometricEncryptedMasterKey()
        assertNotNull(bioEncryptedKey)

        val plaintext = "Bio-protected entry"
        val encrypted = plaintext.encrypt()

        // Login with biometric key
        val loginSuccess = KeyStoreHelper.loginWithBiometricKey(bioEncryptedKey!!)
        assertTrue(loginSuccess)

        val decrypted = encrypted.decrypt()
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun testImportExistingEncryptedMasterKeyWithWrongPasswordFails() {
        val correctPassword = Password("CorrectPassword123!")
        val wrongPassword = Password("WrongPassword456!")
        val (salt, cipheredKey) = KeyStoreHelper.createNewKey(correctPassword)

        assertThrows(Exception::class.java) {
            KeyStoreHelper.importExistingEncryptedMasterKey(
                SaltedPassword(salt, wrongPassword),
                cipheredKey
            )
        }
    }

    @Test
    fun testMasterKeyRoundTripWithMultiplePasswords() {
        val passwords = listOf("12345678", "AlphaBeta123!", "SuperSecretVaultPassword#2026")
        for (pwdStr in passwords) {
            val pwd = Password(pwdStr)
            val (salt, cipheredKey) = KeyStoreHelper.createNewKey(pwd)

            val plaintext = "Category Data for $pwdStr"
            val encrypted = plaintext.encrypt()

            KeyStoreHelper.importExistingEncryptedMasterKey(
                SaltedPassword(salt, pwd),
                cipheredKey
            )

            val decrypted = encrypted.decrypt()
            assertEquals(plaintext, decrypted)
        }
    }
}
