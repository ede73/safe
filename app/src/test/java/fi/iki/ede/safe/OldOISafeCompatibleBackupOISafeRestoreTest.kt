package fi.iki.ede.safe

import android.database.sqlite.SQLiteDatabase
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.crypto.EncryptedPassword
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.hexToByteArray
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.safe.CryptoMocks.mockKeyStoreHelper
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.oisafecompatibility.OISafeRestore
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.slot
import junit.framework.TestCase.assertEquals
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.Security

// TODO: Missing actual encryption/decryption (due to use of bouncy castle, will be gotten rid of and fixed eventually)
class OldOISafeCompatibleBackupOISafeRestoreTest {
    private val passwordOfBackup = Password("abc123".toByteArray())

    @Before
    fun setUp() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    @Ignore("yeap")
    fun restoreTest() {

        mockKeyStoreHelper()

        fun encryptWithNewKey(value: ByteArray): IVCipherText {
            return IVCipherText(byteArrayOf(), value) // TODO: actually encrypt for precise results
        }

        val dbHelper = mockkClass(DBHelper::class)
        val db = mockkClass(SQLiteDatabase::class)

        var inTransaction = false
        every { dbHelper.beginRestoration() } answers {
            inTransaction = true
            db
        }
        val newsalt = slot<Salt>()
        val newEncryptedMasterKey = slot<IVCipherText>()
        every {
            dbHelper.storeSaltAndEncryptedMasterKey(
                capture(newsalt),
                capture(newEncryptedMasterKey)
            )
        } answers {}
        every { db.inTransaction() } answers { inTransaction }
        val cat = slot<DecryptableCategoryEntry>()
        val categories = mutableListOf<DecryptableCategoryEntry>()
        every { dbHelper.addCategory(capture(cat)) } answers {
            categories.add(cat.captured)
            (categories.size + 1).toLong()
        }
        val pwd = slot<DecryptablePasswordEntry>()
        val passwords = mutableListOf<DecryptablePasswordEntry>()
        every { dbHelper.addPassword(capture(pwd)) } answers {
            passwords.add(pwd.captured)
            (passwords.size + 1).toLong()
        }
        every { db.setTransactionSuccessful() } answers {}
        every { db.endTransaction() } answers { inTransaction = false }
        every { dbHelper.fetchAllCategoryRows() } answers { listOf() }

        // this is not a real renewed key against backup password, this is fake
        val fakeAESMasterKey =
            "00112233445566778899AABBCCDDEEFF99887766554433221100123456789ABC".hexToByteArray()
        val previouslyEncryptedFakeMasterKey = IVCipherText(
            "7de6f4ca8600f3c82b19fb3c58e2dccf".hexToByteArray(),
            "5146e48deeef9bb1b5a914823d24941d1c425a844316cd40aaeddd1b35cfd68fda86312e98d25a573df36c82ff700646".hexToByteArray()
        )
        val encryptedRenewedSaltedMasterKey = Pair(fakeBackupSalt, previouslyEncryptedFakeMasterKey)

        val totalPasswords = OISafeRestore.readAndRestore(
            ByteArrayInputStream(
                expectedBackupContent.trimIndent().toByteArray()
            ),
            ::encryptWithNewKey,
            passwordOfBackup,
            encryptedRenewedSaltedMasterKey,
            dbHelper,
        )
        Assert.assertEquals(2, totalPasswords)
        Assert.assertEquals(2, categories.size)
        Assert.assertEquals(2, passwords.size)

        assertEquals("secret", passwords[0].plainDescription)
        assertEquals("", passwords[0].plainWebsite)
        assertEquals("piip", passwords[0].plainUsername)
        assertEquals("puup", passwords[0].plainPassword)
        assertEquals("", passwords[0].plainNote)

        assertEquals("abc", passwords[1].plainDescription)
        assertEquals("", passwords[1].plainWebsite)
        assertEquals("", passwords[1].plainUsername)
        assertEquals("123", passwords[1].plainPassword)
        assertEquals("note notes and more notes", passwords[1].plainNote)

        assertEquals("cat1", categories[0].plainName)
        assertEquals("cat2", categories[1].plainName)

        // Final test would be the renewed master key actually can be decrypted!
        val pbkdf2key = KeyStoreHelper.generatePBKDF2(newsalt.captured, passwordOfBackup)
        assertArrayEquals(
            fakeAESMasterKey,
            KeyManagement.decryptMasterKey(pbkdf2key, newEncryptedMasterKey.captured).encoded
        )
    }

    companion object {
        private val fakeBackupSalt = Salt("5049b760ba4aca3d".hexToByteArray())
        private val encryptedMasterKey =
            EncryptedPassword("ff1b6409b2d436d4be1438b70e8a29663168fafb145a5f0cc38957725d031daa2f47653587ef0f3109ffd9b460554a4190fd4921b013c0747a3f3a2645cf32ddc2c0a6ae67418544fdcc48a8aad70500".hexToByteArray())
        private val expectedBackupContent = """
        <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
        <OISafe version="1" date="Jun 17, 2023 4:02:42 PM Pacific Daylight Time">
          <MasterKey>${encryptedMasterKey.toHex()}</MasterKey>
          <Salt>${fakeBackupSalt.toHex()}</Salt>
          <Category name="5808af5cd4230fbe0a8267b5d91faa35">
            <Entry>
              <RowID>1</RowID>
              <Description>d28f1692ddd263dcbbbcac122a49e69f</Description>
              <Website>d4a6d46ef9756a34d793b227a861c8b3</Website>
              <Username>470d6cdf4372592da96da07f03f022f0</Username>
              <Password>d99557e936f8a7e4653aacc9c802b480</Password>
              <Note>d4a6d46ef9756a34d793b227a861c8b3</Note>
              <UniqueName></UniqueName>
            </Entry>
          </Category>
          <Category name="e561f69af06feab6e3330e38c3280b87">
            <Entry>
              <RowID>2</RowID>
              <Description>a7bf206b97f7a06aab1275381b337f9b</Description>
              <Website>d4a6d46ef9756a34d793b227a861c8b3</Website>
              <Username>d4a6d46ef9756a34d793b227a861c8b3</Username>
              <Password>bc012a29817d403f2d60558ec63e55b4</Password>
              <Note>7a555d65f72e907ff97a55be75bc33a0b6b051eab5d36ab37b5bac417c1ffe4a</Note>
              <UniqueName></UniqueName>
            </Entry>
          </Category>
        </OISafe>
        """
    }
}