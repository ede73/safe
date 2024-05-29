package fi.iki.ede.safe

import android.content.Context
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.hexToByteArray
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.CryptoMocks.mockKeyStoreHelper
import fi.iki.ede.safe.DBMocks.mockDb
import fi.iki.ede.safe.DataModelMocks.mockDataModel
import fi.iki.ede.safe.backupandrestore.Backup
import fi.iki.ede.safe.backupandrestore.Restore
import fi.iki.ede.safe.model.DataModel
import io.mockk.mockkClass
import io.mockk.unmockkObject
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// TODO: Missing actual encryption/decryption (due to use of bouncy castle, will be gotten rid of and fixed eventually)
class BackupAndRestoreTest {

    private val fakeChangedDateTime: ZonedDateTime =
        ZonedDateTime.of(1999, 12, 31, 1, 2, 3, 0, ZoneId.of(ZoneId.SHORT_IDS["PST"]))

    @Test
    fun backupTest() {
        mockKeyStoreHelper()
        val backup = Backup()

        mockPasswordObjectForBackup()

        // TODO: FIX mocking
//        KeyStoreHelper.importExistingEncryptedMasterKey(
//            SaltedPassword(salt, secret),
//            cipheredMasterKey
//        )
        val out = backup.generate(
            salt,
            cipheredMasterKey
        )
        println(out)
        Assert.assertEquals(
            PASSWORD_ENCRYPTED_BACKUP.trimIndent().trim(),
            out.trimIndent().trim(),
        )

        runBlocking {
            DataModel.dump()
        }
    }

    @Test
    fun backupTestOnlyACategory() {
        mockKeyStoreHelper()
        val backup = Backup()
        mockPasswordObjectForBackup()

        val ks = KeyStoreHelperFactory.getKeyStoreHelper()
        mockDataModel(linkedMapOf(Pair(DataModelMocks.makeCat(1, ks), listOf())))

        val out = backup.generate(
            salt,
            cipheredMasterKey
        )
        assertEquals(6, out.lines().size)
        unmockkObject(DataModel)
    }

    @Test
    fun restore() {
        mockKeyStoreHelper()
        val dbHelper = mockDataModel(linkedMapOf())
        val r = Restore()
        val context = mockkClass(Context::class)
        r.doRestore(context, PASSWORD_ENCRYPTED_BACKUP, backupPassword, dbHelper)
        runBlocking {
            DataModel.loadFromDatabase()
        }

        assertEquals(2, DataModel.getCategories().size)
        assertEquals(4, DataModel.getPasswords().size)

        val encryptedcat1 = DataModel.getCategories()[0]
        val encryptedcat2 = DataModel.getCategories()[1]
        assertEquals("encryptedcat1", encryptedcat1.plainName)
        assertEquals("encryptedcat2", encryptedcat2.plainName)

        val password1 = DataModel.getPasswords()[0]
        val password2 = DataModel.getPasswords()[1]
        val password3 = DataModel.getPasswords()[2]
        val password4 = DataModel.getPasswords()[3]

        assertEquals("enc_desc11", password1.plainDescription)
        assertEquals("enc_web11", password1.plainWebsite)
        assertEquals("enc_user11", password1.plainUsername)
        assertEquals("enc_pwd11", password1.plainPassword)
        assertEquals("enc_note11", password1.plainNote)
        assertEquals(fakeChangedDateTime, password1.passwordChangedDate)

        assertEquals("enc_desc12", password2.plainDescription)
        assertEquals("enc_web12", password2.plainWebsite)
        assertEquals("enc_user12", password2.plainUsername)
        assertEquals("enc_pwd12", password2.plainPassword)
        assertEquals("enc_note12", password2.plainNote)
        assertEquals(null, password2.passwordChangedDate)

        assertEquals("enc_desc21", password3.plainDescription)
        assertEquals("enc_web21", password3.plainWebsite)
        assertEquals("enc_user21", password3.plainUsername)
        assertEquals("enc_pwd21", password3.plainPassword)
        assertEquals("enc_note21", password3.plainNote)
        assertEquals(null, password3.passwordChangedDate)

        assertEquals("enc_desc22", password4.plainDescription)
        assertEquals("enc_web22", password4.plainWebsite)
        assertEquals("enc_user22", password4.plainUsername)
        assertEquals("enc_pwd22", password4.plainPassword)
        assertEquals("enc_note22", password4.plainNote)
        assertEquals(null, password4.passwordChangedDate)
    }

    private fun mockPasswordObjectForBackup() {
        mockKeyStoreHelper()
        val ks = KeyStoreHelperFactory.getKeyStoreHelper()
        mockDataModel(
            linkedMapOf(
                Pair(
                    DataModelMocks.makeCat(1, ks), listOf(
                        DataModelMocks.makePwd(1, 11, ks, changedDate = fakeChangedDateTime),
                        DataModelMocks.makePwd(1, 12, ks)
                    )
                ),
                Pair(
                    DataModelMocks.makeCat(2, ks), listOf(
                        DataModelMocks.makePwd(2, 21, ks),
                        DataModelMocks.makePwd(2, 22, ks)
                    )
                )
            )
        )

        mockDb(
            salt,
            cipheredMasterKey // iv+cipher
        )
    }

    companion object {
        private val salt = Salt("9b90e143578bdbe7".hexToByteArray())
        private val backupPassword = Password("secret".toByteArray())

        // TODO: REPLACE WITH PROPER AES MASTER KEY
        @OptIn(ExperimentalEncodingApi::class)
        private val cipheredMasterKey =
            IVCipherText(
                "f670556d2e7992d1c1a074a126291ebe".hexToByteArray(),
                Base64.decode("oUDmV4ykP6IObp+nTkpQebaV8cZdd/Ni0IgM424XAgPazzJ4Zk4l6pEJfvEUgIXj")
            )
        private const val PASSWORD_ENCRYPTED_BACKUP = """
9b90e143578bdbe7
f670556d2e7992d1c1a074a126291ebe
a140e6578ca43fa20e6e9fa74e4a5079b695f1c65d77f362d0880ce36e170203dacf3278664e25ea91097ef1148085e3
3e6566615364726f77737361502f3c3e
79726f67657461632f3c3e6d6574692f3c3e65746f6e2f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635653666363437353632333233223d76692065746f6e3c3e64726f77737361702f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663530373737343632333233223d76692064726f77737361703c3e656d616e726573752f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635353733373536323732333233223d766920656d616e726573753c3e657469736265772f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663537373536323632333233223d766920657469736265773c3e6e6f6974706972637365642f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635343635363337333632333233223d7669206e6f6974706972637365643c3e6d6574693c3e6d6574692f3c3e65746f6e2f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635653666363437353632333133223d76692065746f6e3c3e64726f77737361702f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663530373737343632333133223d76692064726f77737361703c3e656d616e726573752f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635353733373536323732333133223d766920656d616e726573753c3e657469736265772f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663537373536323632333133223d766920657469736265773c3e6e6f6974706972637365642f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635343635363337333632333133223d7669206e6f6974706972637365643c3e6d6574693c3e223130323033303430353036303730383039306130623063306430223d656d616e5f72656870696320226530663030313536653633363237393730373437353634363336313634373233223d656d616e5f76692079726f67657461633c3e79726f67657461632f3c3e6d6574692f3c3e65746f6e2f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635653666363437353631333233223d76692065746f6e3c3e64726f77737361702f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663530373737343631333233223d76692064726f77737361703c3e656d616e726573752f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635353733373536323731333233223d766920656d616e726573753c3e657469736265772f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663537373536323631333233223d766920657469736265773c3e6e6f6974706972637365642f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635343635363337333631333233223d7669206e6f6974706972637365643c3e6d6574693c3e6d6574692f3c3e65746f6e2f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635653666363437353631333133223d76692065746f6e3c3e64726f77737361702f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663530373737343631333133223d76692022333239303336363439223d6465676e6168632064726f77737361703c3e656d616e726573752f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635353733373536323731333133223d766920656d616e726573753c3e657469736265772f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663537373536323631333133223d766920657469736265773c3e6e6f6974706972637365642f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635343635363337333631333133223d7669206e6f6974706972637365643c3e6d6574693c3e223130323033303430353036303730383039306130623063306430223d656d616e5f72656870696320226530663030313536653633363237393730373437353634363336313634373133223d656d616e5f76692079726f67657461633c3e2231223d6e6f6973726576206566615364726f77737361503c100f0e0d0c0b0a090807060504030201
"""
    }
}
