import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeLocalSigningTest {
    @Test
    fun pkcs12UsesOnePasswordForStoreAndPrivateKey() {
        assertEquals(
            listOf(SafeLocalSigning.KEYSTORE_PASSWORD_SERVICE),
            SafeLocalSigning.passwordServices("PKCS12")
        )
    }

    @Test
    fun jksUsesSeparateStoreAndPrivateKeyPasswords() {
        assertEquals(
            listOf(
                SafeLocalSigning.KEYSTORE_PASSWORD_SERVICE,
                SafeLocalSigning.PRIVATE_KEY_PASSWORD_SERVICE
            ),
            SafeLocalSigning.passwordServices("JKS")
        )
    }

    @Test
    fun acceptsOnlySuccessfulNonEmptyKeychainReads() {
        assertTrue(SafeLocalSigning.hasNonEmptyPassword(0, "password\n"))
        assertFalse(SafeLocalSigning.hasNonEmptyPassword(0, "\n"))
        assertFalse(SafeLocalSigning.hasNonEmptyPassword(1, "password\n"))
    }
}
