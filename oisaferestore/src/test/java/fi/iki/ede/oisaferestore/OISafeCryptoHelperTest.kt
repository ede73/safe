package fi.iki.ede.oisaferestore

import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.support.HexString
import fi.iki.ede.crypto.support.hexToByteArray
import fi.iki.ede.oisaferestore.OISafeCryptoMocks.getCryptoMock
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.security.Security

class OISafeCryptoHelperTest {
    private val fixedSalt = Salt("0ede0ede0ede0ede".hexToByteArray())
    private val fixedPassword = Password("password".toByteArray())

    companion object {
        @BeforeAll
        @JvmStatic
        fun loadBouncyCastle() {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }

    @Test
    fun genSalt() {
        val salt1 = OISafeCryptoHelper.generateSalt()
        val salt2 = OISafeCryptoHelper.generateSalt()
        assertNotEquals(salt1, salt2)
    }

    @Test
    fun encrypt() {
        val ch = getCryptoMock(Algorithm.EXTERNAL_OLD, SaltedPassword(fixedSalt, fixedPassword))

        // also doesn't work with bouncycastle!
        val result = ch.encrypt("secret")
        val result2 = ch.encrypt("secret")
        assertEquals(result2, result)
        assertEquals("216b32709184db94c43ea7f8de9dee82", result)

        val decrypted = ch.decrypt(result)
        // in java crypto side, missing IV!
        assertArrayEquals("secret".toByteArray(), decrypted)
    }

    @Test
    fun decrypt() {
        val ch = getCryptoMock(Algorithm.EXTERNAL_OLD, SaltedPassword(fixedSalt, fixedPassword))
        val decrypted = ch.decrypt("216b32709184db94c43ea7f8de9dee82")
        assertArrayEquals("secret".toByteArray(), decrypted)
    }

    @Test
    @Disabled("temp")
    fun tempMasterKey() {
        val ch = getCryptoMock(
            Algorithm.EXTERNAL_OLD,
            SaltedPassword(
                Salt("a1168b731161989b".hexToByteArray()),
                Password("juunou".toByteArray())
            )
        )

        val encryptedMasterKey =
            "065d56a0a3b1c10687af389f213e94997b7d95375da9b76f316f14c60e266b217813acc2263a0f4368c8615074ea7201979a88df2546588205203b6f00844b2041f2c2fc71999743cb58a836e2354e73"
        val unencryptedMasterKey = ch.decrypt(encryptedMasterKey)
        assertArrayEquals(
            "d31418ec165a12bd3a69ee59a0910e589dfa79094379233d692d3a7ec03625f8".toByteArray(),
            unencryptedMasterKey
        )

        val ich = getCryptoMock(
            Algorithm.IN_MEMORY_INTERNAL,
            SaltedPassword(
                Salt("a1168b731161989b".hexToByteArray()),
                Password("d31418ec165a12bd3a69ee59a0910e589dfa79094379233d692d3a7ec03625f8".toByteArray())
            )
        )
        val category = String(ich.decrypt("51cf1276a98ab68389295137e32359e9"))
        assertEquals("Cards", category)
        //

        val re = ch.encrypt(unencryptedMasterKey)
        assertEquals(encryptedMasterKey, re)
    }

    @Test
    fun testOldCrypto() {
        val bCategoryName: HexString = "5808af5cd4230fbe0a8267b5d91faa35"
        val masterKeyEncrypted: HexString =
            "ff1b6409b2d436d4be1438b70e8a29663168fafb145a5f0cc38957725d031daa2f47653587ef0f3109ffd9b460554a4190fd4921b013c0747a3f3a2645cf32ddc2c0a6ae67418544fdcc48a8aad70500"
        val datasetSalt = Salt("5049b760ba4aca3d".hexToByteArray())
        val passwordOfBackup = Password("abc123".toByteArray())

        val ch = OISafeCryptoHelper(Algorithm.EXTERNAL_OLD)
        ch.init(SaltedPassword(datasetSalt, passwordOfBackup))

        val unencryptedMasterKey = ch.decrypt(masterKeyEncrypted)
        val internalInMemoryOISafeCryptoHelper = OISafeCryptoHelper(Algorithm.IN_MEMORY_INTERNAL)
        internalInMemoryOISafeCryptoHelper.init(
            SaltedPassword(
                datasetSalt,
                Password(unencryptedMasterKey)
            )
        )
        val category = String(internalInMemoryOISafeCryptoHelper.decrypt(bCategoryName))
        assertEquals("cat1", category)
    }
}
