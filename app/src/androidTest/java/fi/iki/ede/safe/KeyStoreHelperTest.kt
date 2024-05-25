package fi.iki.ede.safe


import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class KeyStoreHelperTest {

    @Test
    fun testKeyStore() {
        val keyStoreHelper = KeyStoreHelper()
        val biokey = "abba"
        val encryptedBin = keyStoreHelper.encryptByteArray(biokey.toByteArray())
        val decrypted = keyStoreHelper.decryptByteArray(encryptedBin)
        Assert.assertEquals(biokey, String(decrypted))
    }

    @Test
    @Ignore("Not implemented")
    fun testKeyRotation() {
        // Need to implement keyrotation test as well
        // Once the X509 expires, we need to reinitialize everything!
        // Maybe sooner?
        val keyStoreHelper = KeyStoreHelper()
        val biokey = "abba"
        val encrypted =
            keyStoreHelper.encryptByteArray(biokey.toByteArray())

        // Not implemented
        keyStoreHelper.rotateKeys()
        // We've lost the ENCRYPTED KEY at this point for sure
        // Can we have access to that via keystore?

    }
}