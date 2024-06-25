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
import fi.iki.ede.safe.DataModelMocks.mockDataModelFor_UNIT_TESTS_ONLY
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
import kotlin.system.measureTimeMillis

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

    @Test
    fun benchMarkRestore() {
        val r = RestoreDatabase()
        mockZonedDateTimeNow(2000)
        mockGetLastBackupTime(1234)

        // OLD Restore: 6.772 ms
        // flow1 Restore: 1.219ms ms
        // flow2 Restore: 13.03ms // chunks size from 16 to 25600 doesnt much vary!
        val count = 1000.0
        println("Restore: " + (measureTimeMillis {
            (1..count.toInt()).forEach {
                r.doRestore(
                    mockk<Context>(),
                    PASSWORD_ENCRYPTED_BACKUP_AT_1234,
                    backupPassword,
                    dbHelper,
                    { _, _, _ -> }
                ) { thisBackupCreationTime, lastBackupDone ->
                    throw Exception("We should not ask user anything, valid backup!")
                }
            }
        } / count) + " ms")
    }

    @Test
    fun benchmarkBackup() {
        mockZonedDateTimeNow(1234)

        // OLD Backup: 1.514 ms
        // flow1 Backup: 1.712 ms
        // flow2 Backup: 1.346 ms
        val count = 1000.0
        println("Restore: " + (measureTimeMillis {
            (1..count.toInt()).forEach {
                val out = runBlocking { BackupDatabase.backup().toString() }
            }
        } / count) + " ms")

        unmockkStatic(ZonedDateTime::class)
    }

    private fun mockZonedDateTimeNow(unixEpochSeconds: Long) {
        mockkStatic(ZonedDateTime::class)
        every { ZonedDateTime.now() } returns DateUtils.unixEpochSecondsToLocalZonedDateTime(
            unixEpochSeconds
        )
    }

    @Test
    fun backupTest() {

        mockZonedDateTimeNow(1234)

        val out = runBlocking { BackupDatabase.backup().toString() }
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
        every { Preferences.getSoftDeleteDays() } returns 0
    }

    @Test
    fun backupTestOnlyACategory() {
        val db = mockDataModelFor_UNIT_TESTS_ONLY(
            linkedMapOf(
                Pair(
                    DataModelMocks.makeCat(1, ks),
                    listOf()
                )
            )
        )

        val out = runBlocking { BackupDatabase.backup().toString() }
        assertEquals(6, out.lines().size)
        unmockkObject(DataModel)
    }

    @Test
    fun testUserCanCancelOldBackupRestoration() {
        val dbHelper = mockDataModelFor_UNIT_TESTS_ONLY(linkedMapOf())
        val r = RestoreDatabase()
        val context = mockkClass(Context::class)

        listOf(
            // TODO: don't have mocks in place to mimic no changed date...
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

        val passwords = DataModel.getSiteEntries()
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
                assertEquals("enc_desc$f$l", passwords[i].cachedPlainDescription)
                assertEquals("enc_web$f$l", passwords[i].plainWebsite)
                assertEquals("enc_user$f$l", passwords[i].plainUsername)
                assertEquals("enc_pwd$f$l", passwords[i].plainPassword)
                assertEquals("enc_note$f$l", passwords[i].plainNote)
            }
        }
    }

    private fun mockPasswordObjectForBackup() =
        mockDataModelFor_UNIT_TESTS_ONLY(
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

        // TODO: missing changed <item id="2" deleted="1234>
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
0102030405060708090a0b0c0d0e0f10
3d5262777671687a6d596a6a682e797573716a6b6b3b25392b2a687e686f7b75653f21353735332a3736686d796b687f737b236d73596969646f362e3d3f3f22313133303533373e393d3b343d373f71316033673562376d396c3a3c2f2e6c79716a66765a6866656c37293a39386c26313535336631313f3a3c6f3a6938362660353b3766243934607e6e612d474b2d233332263b3a636d7a6979657d7a667f6f226a723824373939383b3f3d3a3f2531343333353e3731396b3b6e3d6d3f7431673362343625363f3e3d6f3b3e3a72373335373232316a3a32386e31216b757261716d75726e676734377b686c7c797567236d733b2538383a393c3e3e3b203432353432363f38303a6a3c6f3e6c20653266346337372a373c3f3a6e383f25633531323630323b3039333022796a72726b77613b3a727b6c78656d606b2f79773f2134343635383a3a3f3c383e392036323b343c3666386b3a683c693e6a20673333263b30333e6a3c3b396f393f273434313364353f3b6b3624797e6b7d7e606f663a3976667b7a7d647e692e6c78606c6461613b25313d3c3d3f3d373d2323226a723824373939383b3f3d3a3f2531343333353e3731396b3b6e3d6d3f7431673362343625363f3e3d6f3b3e3a7236373435333534313a3237237d6f7c63766d71603b3a69677d6f2b657b332d2030323134363633383c3a3d3c3a3e372038326234673664386d3a6e3c6b3f3f323f3437326630373d6b3c693a34393c2665313b37673a2866667e6e3231216664646f3d386c72626529434f312f3f3d323f3e676176657561797e6263632e66663c2033353534373b393e3b393d383f27313a333d3567376a39693b683d6b3f763032213a3332316b3f3a3e6e3b3f392336363566363e343035256f697e6d7d7971766a6b6b383b7f6c687865796b2f79773f2134343635383a3a3f3c383e392036323b343c3666386b3a683c693e6a20673333263b30333e6a3c3b396f393d26323436373c356534267d6e6e7e677b753f3e767760746969646f2b657b332d2030323134363633383c3a3d3c3a3e372038326234673664386d3a6e3c6b3f3f323f3437326630373d6b3d3b3b38383d2760313b373d3a287d7a6f79626c636a2e3d7262777671687a6d2a627a302c3f21313033373532373d393c3b3b3d363f29316333663565376c396f3b6a3c3e2d2e373635673336326a3e3f3c3d3b3d3c2932603f2b7567747b7e6579683332617f7567236d733b2538383a393c3e3e3b203432353432363f38303a6a3c6f3e6c20653266346337372a373c3f3a6e383f25633461323c31343e6d39333f3532207e6e76663a39296e7c6c673530226d6e6464656c767c383b6b687e6e6b627c763068745c6a646b62352b3a3a3c3f3e3c203532363433363038313a323c6c3e6d20623267346036613939282b6f647e6775735d6d6568633a2a3f3e3d6f3b3e3826366134323235316c3f6e3d353b6f38283264213a396f736d642a4248302c3d21233c3f606075647a607a7f6562602f79773f2134343635383a3a3f3c383e392036323b343c3666386b3a683c693e6a20673333263b30333e6a3c3b396f383e26323537326735653b6b362468687d6c626872776d6a6839347e6f697f647a6a3068743e263537373a39393b383d3b3f263135333c353f376939683b6f3d6a3f75316432342738313c3f693d3c386c3822373135313667343035257c696f7d6664643c3f717663756668676e2c64783232313333363535373c393f3b3a3d393f28313b33653564376b396e3b693d683e20233c3530336531383c683c3c3a3b39223663306636643b277c796e7e636f62753f3e7365767570677b6e2b657b332d2030323134363633383c3a3d3c3a3e372038326234673664386d3a6e3c6b3f3f323f3437326630373d6b3d3e3b3c383c2360313b382a76667b7a7d647e6930337e6e7666246c703a2a393b3b3e3d3d3f24313733323531373039333b6d3d6c3f7331663361356036382b343d383b6d392034603566333f303b3f6e386e3e6c333f6f6d77613b3a28617d6f663231677b756c224a403824353a2b343768687d6c626872776d6a6827617f37293c3c3e3d2032323734303631383e3a333c343e6e2063326034613662386f3b3b2e33383b26623433316730363e3a3d3f3a6f3d6d23393e2c606075647a607a7f656260312c766761776c726228607c362e3d3f3f22313133303533373e393d3b343d373f71316033673562376d396c3a3c2f303924376135343064303a3f393d393e6f3c723d2d746167756e7c6c3437797e6b7d7e606f66246c703a2a393b3b3e3d3d3f24313733323531373039333b6d3d6c3f7331663361356036382b343d383b6d3920346034343233313a3e6b386e3e36333f747166766b676a6d37367b6d7e7d787f7366236d733b2538383a393c3e3e3b203432353432363f38303a6a3c6f3e6c20653266346337372a373c3f3a6e383f25633536333430343b68396930227e6e6372756c7661383b66667e6e2c64783232313333363535373c393f3b3a3d393f28313b33653564376b396e3b693d683e20233c3530336531383c683d6e3b37382337663066363e3b2767657f693332207975676e3a392964697d6f6c637f77312c2e5262777671687a6d596a6a6830
"""
    }
}
