package fi.iki.ede.safe

import android.content.Context
import android.os.Environment
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.KeystoreHelperMock4UnitTests
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.hexToByteArray
import fi.iki.ede.crypto.support.toHexString
import fi.iki.ede.safe.DataModelMocks.mockDataModel
import fi.iki.ede.safe.backupandrestore.BackupDatabase
import fi.iki.ede.safe.backupandrestore.RestoreDatabase
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import io.mockk.every
import io.mockk.isMockKMock
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// TODO: Missing actual encryption/decryption (due to use of bouncy castle, will be gotten rid of and fixed eventually)
// Though as long as we have static mock, we can test certain aspects of back/restore process
// including pre-made binary blob to ensure whole clock work functions as expected
// If we dynamically test, ie. backup and restore, we test current functionality
// against current functionality and might miss backwards compatibilities
// so we kinda need test both, protects us from
// - making backwards incompatible changes
// - from breaking current solution
class BackupDatabaseAndRestoreDatabaseTest {

    private val fakeChangedDateTime: ZonedDateTime =
        ZonedDateTime.of(1999, 12, 31, 1, 2, 3, 0, ZoneId.of(ZoneId.SHORT_IDS["PST"]))
    private lateinit var ks: KeyStoreHelper
    private lateinit var dbHelper: DBHelper

    @Before
    fun initializeMocks() {
        KeystoreHelperMock4UnitTests.mock()
        ks = KeyStoreHelperFactory.getKeyStoreHelper()

        // TODO: FIX mocking?? Still Valid?
//        KeyStoreHelper.importExistingEncryptedMasterKey(
//            SaltedPassword(salt, secret),
//            cipheredMasterKey
//        )

        // not really needed for restore, but here it is...
        dbHelper = mockPasswordObjectForBackup()
        require(dbHelper != null) { "DBHelper initializing failed" }
        require(isMockKMock(dbHelper)) { "DBHelper is not mocked" }
        dbHelper.storeSaltAndEncryptedMasterKey(salt, cipheredMasterKey)
        mockkObject(LoginHandler)
        every { LoginHandler.passwordLogin(any(), any()) } returns true

        //LoginHandler.passwordLogin(context, userPassword)
    }

    @After
    fun deinitMocks() {
        unmockkAll()
    }

    private fun mockZonedDateTimeNow(unixEpochSeconds: Long) {
        mockkStatic(ZonedDateTime::class)
        every { ZonedDateTime.now() } returns DateUtils.unixEpochSecondsToLocalZonedDateTime(
            unixEpochSeconds
        )
    }

    @Test
    fun backupTest() {
        val backupDatabase = BackupDatabase()

        mockZonedDateTimeNow(1234)

        val out = backupDatabase.generate(
            salt,
            cipheredMasterKey
        )
        unmockkStatic(ZonedDateTime::class)
        println(out)
        Assert.assertEquals(
            PASSWORD_ENCRYPTED_BACKUP_AT_1234.trimIndent().trim(),
            out.trimIndent().trim(),
        )

        runBlocking {
            DataModel.dump()
        }
    }

    private fun mockGetLastBackupTime(unixEpochSeconds: Long?) {
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("path/to/fake/directory")

        mockkObject(Preferences)
        every { Preferences.getLastBackupTime() } returns unixEpochSeconds?.let {
            DateUtils.unixEpochSecondsToLocalZonedDateTime(
                it
            )
        }
    }

    @Test
    fun backupTestOnlyACategory() {
        val backupDatabase = BackupDatabase()

        mockDataModel(linkedMapOf(Pair(DataModelMocks.makeCat(1, ks), listOf())))

        val out = backupDatabase.generate(
            salt,
            cipheredMasterKey
        )
        assertEquals(6, out.lines().size)
        unmockkObject(DataModel)
    }

    @Test
    fun testUserCanCancelOldBackupRestoration() {
        val dbHelper = mockDataModel(linkedMapOf())
        val r = RestoreDatabase()
        val context = mockkClass(Context::class)

        listOf(
            // TODO: don't have mocks in place to mimick no changed date...
//            // unknown backup creation time(set creationTime manually to null in RestoreDatabase), known last backup, must succeed(we wont consult user)
//            Triple(1234L, 1234L, true),
//            // unknown backup creation time(set creationTime manually to null in RestoreDatabase), unknown last backup, must succeed(we wont consult user)
//            Triple(1234L, null, true),
            // known backup creation time, unknown last backup, must succeed(we wont consult user)
            Triple(1234L, null, Pair(true, false)),
            // backup being restored is OLDER than last backup, must fail(assuming user says no)
            Triple(1234L, 2000L, Pair(false, true)),
            // backup being restored is NEWER than last backup, must succeed(we wont consult user)
            Triple(1234L, 1000L, Pair(true, false)),
            // backup being restored is same as last backup, must succeed(we wont consult user)
            Triple(1234L, 1234L, Pair(true, false)),
        ).forEach {
            mockZonedDateTimeNow(it.first)
            mockGetLastBackupTime(it.second)
            val succeed = it.third.first
            val mustAskUser = it.third.second
            try {
                var askedUser = false
                r.doRestore(
                    context,
                    PASSWORD_ENCRYPTED_BACKUP_AT_1234,
                    backupPassword,
                    dbHelper,
                    { _, _, _ -> }
                ) { thisBackupCreationTime, lastBackupDone ->
                    askedUser = true
                    if (!mustAskUser) {
                        throw Exception("We mustn't ask user, valid backup!")
                    }
                    false
                }
                assertTrue(
                    "Oh no, we should have asked user, but missed, now=${it.first}, last backup=${it.second}, backup=1234",
                    mustAskUser == askedUser
                )
                assertTrue(
                    "Expected success, but succeeded now=${it.first}, last backup=${it.second}, backup=1234",
                    succeed
                )
            } catch (c: CancellationException) {
                println("Failed as expected...$c")
                assertFalse(
                    "Expected failure, but succeeded now=${it.first}, last backup=${it.second}, backup=1234",
                    succeed
                )
            }
        }
    }

    @Test
    fun restoreTest() {
        val r = RestoreDatabase()
        val context = mockkClass(Context::class)

        mockZonedDateTimeNow(2000)
        mockGetLastBackupTime(1234)
        r.doRestore(
            mockk<Context>(),
            PASSWORD_ENCRYPTED_BACKUP_AT_1234,
            backupPassword,
            dbHelper,
            { _, _, _ -> }
        ) { thisBackupCreationTime, lastBackupDone ->
            throw Exception("We should not ask user anything, valid backup!")
        }
        runBlocking {
            DataModel.loadFromDatabase()
        }

        val passwords = DataModel.getPasswords()
        val categories = DataModel.getCategories()
        assertEquals(2, categories.size)
        assertEquals(4, passwords.size)

        assertEquals("encryptedcat1", categories[0].plainName)
        assertEquals("encryptedcat2", categories[1].plainName)

        (1..2).forEach { f ->
            (1..2).forEach { l ->
                val i = (f - 1) * 2 + (l - 1)
                if (i == 0) {
                    assertEquals(fakeChangedDateTime, passwords[i].passwordChangedDate)
                } else {
                    assertEquals(null, passwords[i].passwordChangedDate)
                }
                assertEquals(f.toLong(), passwords[i].categoryId)
                assertEquals("enc_desc$f$l", passwords[i].plainDescription)
                assertEquals("enc_web$f$l", passwords[i].plainWebsite)
                assertEquals("enc_user$f$l", passwords[i].plainUsername)
                assertEquals("enc_pwd$f$l", passwords[i].plainPassword)
                assertEquals("enc_note$f$l", passwords[i].plainNote)
            }
        }
    }

    private fun mockPasswordObjectForBackup() =
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

//        mockDb(
//            salt,
//            cipheredMasterKey // iv+cipher
//        )
//    }

    companion object {
        private val salt = Salt("9b90e143578bdbe7".hexToByteArray())
        private val backupPassword = Password("secret")

        // TODO: REPLACE WITH PROPER AES MASTER KEY
        @OptIn(ExperimentalEncodingApi::class)
        private val cipheredMasterKey =
            IVCipherText(
                "f670556d2e7992d1c1a074a126291ebe".hexToByteArray(),
                Base64.decode("oUDmV4ykP6IObp+nTkpQebaV8cZdd/Ni0IgM424XAgPazzJ4Zk4l6pEJfvEUgIXj")
            )

        // Some old and new date formats to ensure backwards compatibility
        // Date parsing is already extensively tested elsewhere
        private val OLD_DATE_FORMATS = listOf(
            "May 31, 2023, 11:21:12 AM Pacific Daylight Time",
            "May 31, 2023, 11:21:13",
            "May 31, 2023, 11:21:14 PM Pacific Standard Time",
            "12345678"
        )

        // XPath describing outcome (ie addition of an attribute, element/removal)
        private val XML_MODIFICATIONS = listOf(
            // add new proposed creation attribute
            Pair("/PasswordSafe/", "@changed=\"123456\""),
            // Future version - we can't parse (we can still try)
            Pair("/PasswordSafe/@version", "\"666\""),
            // Delete category name (so it fails to parse)
            Pair("/PasswordSafe/category[0]/@cipher_name", ""),
            // Delete item description - we can't add this
            Pair("/PasswordSafe/category[0]/item[0]/description", ""),
            // Add a new element, should never happen as
            // version controls, but still we should just ignore it too
            Pair("/PasswordSafe/category[0]/item[0]", "new_unknown_field"),
            // Make an decryption error
            Pair("/PasswordSafe/category[0]/@cipher_name", "foobar"),
        )

        // TODO: missing changed
        private const val TOP_LAYER_UNENCRYPTED_BACKUP_XML = """
<?xml version="1.0" encoding="utf-8"?>
<PasswordSafe version="1">
    <category cipher_name="0d0c0b0a090807060504030201" iv_name="31746163646574707972636e65100f0e">
        <item>
            <description iv="3131637365645f636e65100f0e0d0c0b">0a090807060504030201</description>
            <website iv="31316265775f636e65100f0e0d0c0b0a">090807060504030201</website>
            <username iv="3131726573755f636e65100f0e0d0c0b">0a090807060504030201</username>
            <password changed="946630923" iv="31316477705f636e65100f0e0d0c0b0a">090807060504030201
            </password>
            <note iv="313165746f6e5f636e65100f0e0d0c0b">0a090807060504030201</note>
        </item>
        <item>
            <description iv="3231637365645f636e65100f0e0d0c0b">0a090807060504030201</description>
            <website iv="32316265775f636e65100f0e0d0c0b0a">090807060504030201</website>
            <username iv="3231726573755f636e65100f0e0d0c0b">0a090807060504030201</username>
            <password iv="32316477705f636e65100f0e0d0c0b0a">090807060504030201</password>
            <note iv="323165746f6e5f636e65100f0e0d0c0b">0a090807060504030201</note>
        </item>
    </category>
    <category cipher_name="0d0c0b0a090807060504030201" iv_name="32746163646574707972636e65100f0e">
        <item>
            <description iv="3132637365645f636e65100f0e0d0c0b">0a090807060504030201</description>
            <website iv="31326265775f636e65100f0e0d0c0b0a">090807060504030201</website>
            <username iv="3132726573755f636e65100f0e0d0c0b">0a090807060504030201</username>
            <password iv="31326477705f636e65100f0e0d0c0b0a">090807060504030201</password>
            <note iv="313265746f6e5f636e65100f0e0d0c0b">0a090807060504030201</note>
        </item>
        <item>
            <description iv="3232637365645f636e65100f0e0d0c0b">0a090807060504030201</description>
            <website iv="32326265775f636e65100f0e0d0c0b0a">090807060504030201</website>
            <username iv="3232726573755f636e65100f0e0d0c0b">0a090807060504030201</username>
            <password iv="32326477705f636e65100f0e0d0c0b0a">090807060504030201</password>
            <note iv="323265746f6e5f636e65100f0e0d0c0b">0a090807060504030201</note>
        </item>
    </category>
</PasswordSafe>
        """
        private val PASSWORD_ENCRYPTED_BACKUP_AT_1234 = """
${salt.toHex()}
${cipheredMasterKey.iv.toHexString()}
${cipheredMasterKey.cipherText.toHexString()}
3e6566615364726f77737361502f3c3e
79726f67657461632f3c3e6d6574692f3c3e65746f6e2f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635653666363437353632333233223d76692065746f6e3c3e64726f77737361702f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663530373737343632333233223d76692064726f77737361703c3e656d616e726573752f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635353733373536323732333233223d766920656d616e726573753c3e657469736265772f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663537373536323632333233223d766920657469736265773c3e6e6f6974706972637365642f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635343635363337333632333233223d7669206e6f6974706972637365643c3e223232223d4449206d6574693c3e6d6574692f3c3e65746f6e2f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635653666363437353632333133223d76692065746f6e3c3e64726f77737361702f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663530373737343632333133223d76692064726f77737361703c3e656d616e726573752f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635353733373536323732333133223d766920656d616e726573753c3e657469736265772f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663537373536323632333133223d766920657469736265773c3e6e6f6974706972637365642f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635343635363337333632333133223d7669206e6f6974706972637365643c3e223132223d4449206d6574693c3e223130323033303430353036303730383039306130623063306430223d656d616e5f72656870696320226530663030313536653633363237393730373437353634363336313634373233223d656d616e5f76692079726f67657461633c3e79726f67657461632f3c3e6d6574692f3c3e65746f6e2f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635653666363437353631333233223d76692065746f6e3c3e64726f77737361702f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663530373737343631333233223d76692064726f77737361703c3e656d616e726573752f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635353733373536323731333233223d766920656d616e726573753c3e657469736265772f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663537373536323631333233223d766920657469736265773c3e6e6f6974706972637365642f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635343635363337333631333233223d7669206e6f6974706972637365643c3e223231223d4449206d6574693c3e6d6574692f3c3e65746f6e2f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635653666363437353631333133223d76692065746f6e3c3e64726f77737361702f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663530373737343631333133223d76692022333239303336363439223d6465676e6168632064726f77737361703c3e656d616e726573752f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635353733373536323731333133223d766920656d616e726573753c3e657469736265772f3c3130323033303430353036303730383039303e226130623063306430653066303031353665363336663537373536323631333133223d766920657469736265773c3e6e6f6974706972637365642f3c31303230333034303530363037303830393061303e226230633064306530663030313536653633366635343635363337333631333133223d7669206e6f6974706972637365643c3e223131223d4449206d6574693c3e223130323033303430353036303730383039306130623063306430223d656d616e5f72656870696320226530663030313536653633363237393730373437353634363336313634373133223d656d616e5f76692079726f67657461633c3e2234333231223d64657461657263202231223d6e6f6973726576206566615364726f77737361503c100f0e0d0c0b0a090807060504030201
"""
    }
}
