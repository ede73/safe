package fi.iki.ede.safe

import android.content.Context
import android.os.Environment
import fi.iki.ede.backup.BackupDatabase
import fi.iki.ede.backup.RestoreDatabase
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.KeystoreHelperMock4UnitTests
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.IKeyStoreHelper
import fi.iki.ede.crypto.keystore.KMPKey
import fi.iki.ede.crypto.keystore.KMPSecretKeySpec
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.db.DBHelper
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.gpmdatamodel.db.GPMDB
import fi.iki.ede.logger.Logger
import fi.iki.ede.logger.firebaseRecordException
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.DataModelMocks.mockDataModelFor_UNIT_TESTS_ONLY
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.gpm.model.*
import io.mockk.every
import io.mockk.isMockKMock
import io.mockk.mockkClass
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.system.measureTimeMillis
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val TAG = "BackupDatabaseAndRestoreDatabaseTest"

// TODO: Missing actual encryption/decryption (due to use of bouncy castle, will be gotten rid of and fixed eventually)
// Though as long as we have static mock, we can test certain aspects of back/restore process
// including pre-made binary blob to ensure whole clock work functions as expected
// If we dynamically test, ie. backup and restore, we test current functionality
// against current functionality and might miss backwards compatibilities
// so we kinda need test both, protects us from
// - making backwards incompatible changes
// - from breaking current solution
@ExperimentalTime
class BackupDatabaseAndRestoreDatabaseTest {
    private val fakeChangedDateTime: Instant =
        LocalDateTime(1999, 12, 31, 1, 2, 3, 0).toInstant(TimeZone.of("America/Los_Angeles"))
    private lateinit var dbHelper: DBHelper

    @BeforeEach
    fun before() {
        mockkStatic("fi.iki.ede.logger.FirebaseUtilitiesKt")
        every { firebaseRecordException(any(), any()) } returns Unit

        mockkObject(Preferences)
        every { Preferences.storeAllExtensions(any()) } returns Unit
        every { Preferences.getAllExtensions() } returns emptySet()

        KeystoreHelperMock4UnitTests.mock()

        // not really needed for restore, but here it is...
        dbHelper = mockPasswordObjectForBackup()
        require(isMockKMock(dbHelper)) { "DBHelper is not mocked" }
        dbHelper.storeSaltAndEncryptedMasterKey(salt, cipheredMasterKey)
        mockkObject(LoginHandler)
        every { LoginHandler.passwordLogin(any()) } returns true
    }

    @AfterEach
    fun after() {
        unmockkObject(Preferences)
        unmockkStatic("fi.iki.ede.logger.FirebaseUtilitiesKt")
        unmockkAll()
    }

    @Test
    fun benchMarkRestore() {
        val r = RestoreDatabase()
        mockClockSystemNow(2000)
        mockGetLastBackupTime(1234)

        // OLD Restore: 6.772 ms
        // flow1 Restore: 1.219ms ms
        // flow2 Restore: 13.03ms // chunks size from 16 to 25600 doesnt much vary!
        val count = 1000.0
        Logger.d(TAG, "Restore: " + (measureTimeMillis {
            (1..count.toInt()).forEach {
                r.doRestore(
                    Buffer().writeUtf8(PASSWORD_ENCRYPTED_BACKUP_AT_1234),
                    backupPassword,
                    dbHelper,
                    Preferences.getLastBackupTime(),
                    GPMDB::linkSaveGPMAndSiteEntry,
                    GPMDB::addSavedGPM,
                    { _ -> true },
                    { _, _, _ -> }
                ) { thisBackupCreationTime, lastBackupDone ->
                    throw Exception("We should not ask user anything, valid backup!")
                }
            }
        } / count) + " ms")
    }

    @Test
    fun benchmarkBackup() {
        mockClockSystemNow(1234)

        // OLD Backup: 1.514 ms
        // flow1 Backup: 1.712 ms
        // flow2 Backup: 1.346 ms
        val count = 1000.0
        Logger.d(TAG, "Restore: " + (measureTimeMillis {
            (1..count.toInt()).forEach {
                runBlocking {
                    val buffer = Buffer()
                    BackupDatabase.backup(
                        DataModel.categoriesStateFlow.value,
                        DataModel.softDeletedStateFlow.value,
                        DataModel::getSiteEntriesOfCategory,
                        GPMDB.fetchAllSiteEntryGPMMappings(),
                        GPMDataModel.allSavedGPMsFlow.value.toSet(),
                        buffer
                    )
                    buffer.readUtf8()
                }
            }
        } / count) + " ms")

        unmockkObject(Clock.System)
    }

    private fun mockClockSystemNow(unixEpochSeconds: Long) {
        mockkObject(Clock.System)
        every { Clock.System.now() } returns DateUtils.unixEpochSecondsToInstant(
            unixEpochSeconds
        )
    }

    @Test
    fun backupTest() {
        mockClockSystemNow(1234)
        mockGetLastBackupTime(1234)
        val finalBuffer = Buffer()
        runBlocking {
            BackupDatabase.backup(
                DataModel.categoriesStateFlow.value,
                DataModel.softDeletedStateFlow.value,
                DataModel::getSiteEntriesOfCategory,
                GPMDB.fetchAllSiteEntryGPMMappings(),
                GPMDataModel.allSavedGPMsFlow.value.toSet(),
                finalBuffer
            )
        }
        unmockkObject(Clock.System)
        val out = finalBuffer.readUtf8()
        if (PASSWORD_ENCRYPTED_BACKUP_AT_1234.trimIndent().trim() != out.trimIndent().trim()) {
            println("ACTUAL_NEW_BACKUP_HEX:\n" + out.trim())
        }
        assertEquals(
            PASSWORD_ENCRYPTED_BACKUP_AT_1234.trimIndent().trim(),
            out.trimIndent().trim(),
        )

        runBlocking {
            DataModel.dumpModelInDebugMode()
        }
    }

    private fun mockGetLastBackupTime(unixEpochSeconds: Long?) {
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("path/to/fake/directory") // KMP

        mockkObject(Preferences)
        every { Preferences.storeAllExtensions(any()) } returns Unit
        every { Preferences.getAllExtensions() } returns emptySet()
        every { Preferences.getLastBackupTime() } returns unixEpochSeconds?.let {
            DateUtils.unixEpochSecondsToInstant(
                it
            )
        }
        every { Preferences.getSoftDeleteDays() } returns 0
    }

    @Test
    fun backupTestOnlyACategory() {
        mockDataModelFor_UNIT_TESTS_ONLY(
            linkedMapOf(
                Pair(
                    DataModelMocks.makeCat(1),
                    listOf()
                )
            )
        )

        val out = runBlocking {
            val buffer = Buffer()
            BackupDatabase.backup(
                DataModel.categoriesStateFlow.value,
                DataModel.softDeletedStateFlow.value,
                DataModel::getSiteEntriesOfCategory,
                GPMDB.fetchAllSiteEntryGPMMappings(),
                GPMDataModel.allSavedGPMsFlow.value.toSet(),
                buffer
            )
            buffer.readUtf8()
        }
        assertEquals(6, out.lines().size)
        unmockkObject(DataModel)
    }

    @Test
    fun cxfBackupAndRestoreTest() {
        val dbHelper = mockDataModelFor_UNIT_TESTS_ONLY(linkedMapOf())
        mockClockSystemNow(1234)
        mockGetLastBackupTime(1234)

        runBlocking {
            val cxfDb = dbHelper.database
            val accId = cxfDb.cxfAccountDao().insert(
                fi.iki.ede.db.cxf.CXFAccount(
                    cxfAccountId = "acc-123",
                    encryptedEmail = IVCipherText.getEmpty()
                )
            )

            cxfDb.cxfImportDao().insert(
                fi.iki.ede.db.cxf.CXFImport(
                    accountId = accId,
                    encryptedCxfItemId = IVCipherText.getEmpty(),
                    type = "password",
                    encryptedName = IVCipherText.getEmpty(),
                    encryptedUrl = IVCipherText.getEmpty(),
                    encryptedUsername = IVCipherText.getEmpty(),
                    encryptedPassword = IVCipherText.getEmpty(),
                    encryptedNote = IVCipherText.getEmpty(),
                    hash = "hash-123"
                )
            )

            cxfDb.cxfPasskeyDao().insert(
                fi.iki.ede.db.cxf.CXFPasskey(
                    accountId = accId,
                    encryptedCxfItemId = IVCipherText.getEmpty(),
                    rpId = "example.com",
                    encryptedName = IVCipherText.getEmpty(),
                    encryptedUrl = IVCipherText.getEmpty(),
                    encryptedUsername = IVCipherText.getEmpty(),
                    encryptedCredentialId = IVCipherText.getEmpty(),
                    encryptedUserHandle = IVCipherText.getEmpty(),
                    encryptedNote = IVCipherText.getEmpty(),
                    hash = "passkey-hash-123"
                )
            )

            val xmlBuf = Buffer()
            val xmlSink = xmlBuf.buffer()
            BackupDatabase().generateXMLExport(
                xmlSink,
                emptyList(),
                emptySet(),
                emptyMap(),
                emptySet(),
                { emptyList() }
            )
            xmlSink.flush()
            val rawXml = xmlBuf.readUtf8()
            assertTrue(rawXml.contains("cxfimport"))
            assertTrue(rawXml.contains("cxfpasskey"))

            val buffer = Buffer()
            BackupDatabase.backup(
                emptyList(),
                emptySet(),
                { emptyList() },
                emptyMap(),
                emptySet(),
                buffer
            )
            val encryptedBackup = buffer.readUtf8()

            val restore = RestoreDatabase()
            restore.doRestore(
                Buffer().writeUtf8(encryptedBackup),
                backupPassword,
                dbHelper,
                Preferences.getLastBackupTime(),
                { _, _ -> },
                { },
                { true },
                { _, _, _ -> },
                { _, _ -> true }
            )

            val accounts = cxfDb.cxfAccountDao().getAll()
            val imports = cxfDb.cxfImportDao().getAll()
            val passkeys = cxfDb.cxfPasskeyDao().getAll()

            assertEquals(1, accounts.size)
            assertEquals(1, imports.size)
            assertEquals(1, passkeys.size)
            assertEquals("acc-123", accounts.first().cxfAccountId)
            assertEquals("hash-123", imports.first().hash)
            assertEquals("passkey-hash-123", passkeys.first().hash)

            // Verify restoring again does not duplicate entries
            restore.doRestore(
                Buffer().writeUtf8(encryptedBackup),
                backupPassword,
                dbHelper,
                Preferences.getLastBackupTime(),
                { _, _ -> },
                { },
                { true },
                { _, _, _ -> },
                { _, _ -> true }
            )

            assertEquals(1, cxfDb.cxfAccountDao().getAll().size)
            assertEquals(1, cxfDb.cxfImportDao().getAll().size)
            assertEquals(1, cxfDb.cxfPasskeyDao().getAll().size)
        }
    }

    @Test
    fun testUserCanCancelOldBackupRestoration() {
        val dbHelper = mockDataModelFor_UNIT_TESTS_ONLY(linkedMapOf())
        val r = RestoreDatabase()

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
            mockClockSystemNow(it.first)
            mockGetLastBackupTime(it.second)
            val succeed = it.third.first
            val mustAskUser = it.third.second
            try {
                var askedUser = false
                r.doRestore(
                    Buffer().writeUtf8(PASSWORD_ENCRYPTED_BACKUP_AT_1234),
                    backupPassword,
                    dbHelper,
                    Preferences.getLastBackupTime(),
                    GPMDB::linkSaveGPMAndSiteEntry,
                    GPMDB::addSavedGPM,
                    { _ -> true },
                    { _, _, _ -> }
                ) { thisBackupCreationTime, lastBackupDone ->
                    askedUser = true
                    if (!mustAskUser) {
                        throw Exception("We mustn't ask user, valid backup!")
                    }
                    false
                }
                assertTrue(
                    mustAskUser == askedUser,
                    "Oh no, we should have asked user, but missed, now=${it.first}, last backup=${it.second}, backup=1234",
                )
                assertTrue(
                    succeed,
                    "Expected success, but succeeded now=${it.first}, last backup=${it.second}, backup=1234",
                )
            } catch (c: CancellationException) {
                Logger.d(TAG, "Failed as expected...$c")
                assertFalse(
                    succeed,
                    "Expected failure, but succeeded now=${it.first}, last backup=${it.second}, backup=1234",
                )
            }
        }
    }

    @Test
    fun restoreTest() {
        val r = RestoreDatabase()
        mockkClass(Context::class)

        mockClockSystemNow(2000)
        mockGetLastBackupTime(1234)
        r.doRestore(
            Buffer().writeUtf8(PASSWORD_ENCRYPTED_BACKUP_AT_1234),
            backupPassword,
            dbHelper,
            Preferences.getLastBackupTime(),
            GPMDB::linkSaveGPMAndSiteEntry,
            GPMDB::addSavedGPM,
            { _ -> true },
            { _, _, _ -> }
        ) { _, _ ->
            throw Exception("We should not ask user anything, valid backup!")
        }
        runBlocking {
            DataModel.loadFromDatabase {
                GPMDataModel.loadFromDatabase()
            }
        }

        val passwords = DataModel.siteEntriesStateFlow.value.filter { it.categoryId > 0L }
        val categories = DataModel.categoriesStateFlow.value.filter { (it.id ?: 0L) > 0L }
        assertEquals(2, categories.size)
        assertEquals(4, passwords.size)

        assertEquals("encryptedcat1", categories[0].plainName)
        assertEquals("encryptedcat2", categories[1].plainName)

        runBlocking {
            val cxfDb = dbHelper.database
            val cxfAccounts = cxfDb.cxfAccountDao().getAll()
            val cxfImports = cxfDb.cxfImportDao().getAll()
            val cxfPasskeys = cxfDb.cxfPasskeyDao().getAll()
            assertEquals(1, cxfAccounts.size)
            assertEquals(1, cxfImports.size)
            assertEquals(1, cxfPasskeys.size)
            assertEquals("acc-hex-1", cxfAccounts.first().cxfAccountId)
            assertEquals("hex-cxf-hash-1", cxfImports.first().hash)
            assertEquals("hex-passkey-hash-1", cxfPasskeys.first().hash)
        }

        (1..2).forEach { f ->
            (1..2).forEach { l ->
                val i = (f - 1) * 2 + (l - 1)
                if (i == 0) {
                    // target runs on what ever timezone (github Etc/UTC) so convert to our test TZ
                    val actualChangedDateTime = passwords[i].passwordChangedDate
                    assertEquals(fakeChangedDateTime, actualChangedDateTime)
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

    class KmpKeyStoreHelper(private val masterKey: KMPSecretKeySpec) : IKeyStoreHelper {
        override fun testingDeleteKeys_DO_NOT_USE() {}
        override fun rotateKeys() {}
        override fun getOrCreateBiokey(): KMPKey = masterKey

        override var decrypterProviderWithKey: (IVCipherText, KMPKey) -> ByteArray =
            { encrypted, key ->
                if (encrypted.iv.isEmpty()) {
                    byteArrayOf()
                } else {
                    val keyBytes = when (key) {
                        is KMPSecretKeySpec -> key.values
                        is SecretKeySpec -> key.encoded
                        else -> throw IllegalArgumentException("Unsupported key type: ${key::class}")
                    }
                    korlibs.crypto.AES.decryptAesCbc(
                        encrypted.cipherText,
                        keyBytes,
                        encrypted.iv,
                        korlibs.crypto.Padding.PKCS7Padding
                    )
                }
            }

        override var decrypterProvider: (IVCipherText) -> ByteArray = { encrypted ->
            if (encrypted.iv.isEmpty()) {
                byteArrayOf()
            } else {
                korlibs.crypto.AES.decryptAesCbc(
                    encrypted.cipherText,
                    masterKey.values,
                    encrypted.iv,
                    korlibs.crypto.Padding.PKCS7Padding
                )
            }
        }

        override var encrypterProviderWithKey: (ByteArray, KMPKey) -> IVCipherText = { _, _ ->
            throw NotImplementedError()
        }

        override var encrypterProvider: (ByteArray) -> IVCipherText = { _ ->
            throw NotImplementedError()
        }
    }

    private fun mockPasswordObjectForBackup(): DBHelper {
        val dbHelper = mockDataModelFor_UNIT_TESTS_ONLY(
            linkedMapOf(
                Pair(
                    DataModelMocks.makeCat(1), listOf(
                        DataModelMocks.makePwd(1, 11, changedUtcDate = fakeChangedDateTime),
                        DataModelMocks.makePwd(1, 12)
                    )
                ),
                Pair(
                    DataModelMocks.makeCat(2), listOf(
                        DataModelMocks.makePwd(2, 21),
                        DataModelMocks.makePwd(2, 22)
                    )
                )
            )
        )
        runBlocking {
            val cxfDb = dbHelper.database
            val accId = cxfDb.cxfAccountDao().insert(
                fi.iki.ede.db.cxf.CXFAccount(
                    cxfAccountId = "acc-hex-1",
                    encryptedEmail = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "646c6076".hexToByteArray()
                    )
                )
            )
            cxfDb.cxfImportDao().insert(
                fi.iki.ede.db.cxf.CXFImport(
                    accountId = accId,
                    encryptedCxfItemId = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "313031".hexToByteArray()
                    ),
                    type = "password",
                    encryptedName = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "6f636e61".hexToByteArray()
                    ),
                    encryptedUrl = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "766761".hexToByteArray()
                    ),
                    encryptedUsername = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "74716676".hexToByteArray()
                    ),
                    encryptedPassword = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "716370777269756c".hexToByteArray()
                    ),
                    encryptedNote = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "6f6d7761".hexToByteArray()
                    ),
                    hash = "hex-cxf-hash-1",
                    flaggedIgnored = false
                )
            )
            cxfDb.cxfPasskeyDao().insert(
                fi.iki.ede.db.cxf.CXFPasskey(
                    accountId = accId,
                    encryptedCxfItemId = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "313032".hexToByteArray()
                    ),
                    rpId = "example.org",
                    encryptedName = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "6f636e61".hexToByteArray()
                    ),
                    encryptedUrl = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "766761".hexToByteArray()
                    ),
                    encryptedUsername = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "74716676".hexToByteArray()
                    ),
                    encryptedCredentialId = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "63726564".hexToByteArray()
                    ),
                    encryptedUserHandle = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "68616e646c65".hexToByteArray()
                    ),
                    encryptedNote = IVCipherText(
                        "0102030405060708090a0b0c0d0e0f10".hexToByteArray(),
                        "6f6d7761".hexToByteArray()
                    ),
                    hash = "hex-passkey-hash-1",
                    flaggedIgnored = false
                )
            )
        }
        return dbHelper
    }

    @Test
    fun restoreBackupImmediateTest() {
        // the purpose of ready made encrypted backup below is to capture backwards/forwards
        // breaking changes, let's have one more - more precise just ensuring the current
        // backup can fully be restored in the exact condition it is described in XML
        mockkConstructor(BackupDatabase::class)
        every {
            anyConstructed<BackupDatabase>().generateXMLExport(
                outputSink = any(),
                categoriesList = any(),
                softDeletedEntries = any(),
                siteEntryGPMMappings = any(),
                allSavedGPMs = any(),
                getSiteEntriesOfCategory = any(),
            )
        } answers {
            val outputSink = arg<okio.BufferedSink>(0)
            outputSink.writeUtf8(TOP_LAYER_UNENCRYPTED_BACKUP_XML.trim())
        }

        val finalOutput = runBlocking {
            val buffer = Buffer()
            BackupDatabase.backup(
                DataModel.categoriesStateFlow.value,
                DataModel.softDeletedStateFlow.value,
                DataModel::getSiteEntriesOfCategory,
                GPMDB.fetchAllSiteEntryGPMMappings(),
                GPMDataModel.allSavedGPMsFlow.value.toSet(),
                buffer
            )
            buffer.readUtf8()
        }

        unmockkConstructor(BackupDatabase::class)

        val r = RestoreDatabase()
        mockClockSystemNow(2000)
        mockGetLastBackupTime(1234)
        r.doRestore(
            Buffer().writeUtf8(finalOutput),
            backupPassword,
            dbHelper,
            Preferences.getLastBackupTime(),
            GPMDB::linkSaveGPMAndSiteEntry,
            GPMDB::addSavedGPM,
            { _ -> true },
            { _, _, _ -> }
        ) { _, _ ->
            throw Exception("We should not ask user anything, valid backup!")
        }

        runBlocking {
            DataModel.loadFromDatabase {
                GPMDataModel.loadFromDatabase()
            }
        }

        if (BuildConfig.DEBUG) {
            Logger.i(
                TAG,
                "Categories: ${
                    DataModel.categoriesStateFlow.value.map { it.id }.joinToString(",")
                }"
            )
            Logger.i(
                TAG,
                "SiteEntries: ${
                    DataModel.siteEntriesStateFlow.value.map { it.id }.joinToString(",")
                }"
            )
            Logger.i(
                TAG,
                "GPMs: ${
                    GPMDataModel.allSavedGPMsFlow.value.map { it.id }
                        .joinToString(",")
                }"
            )
            Logger.i(
                TAG,
                "GPM linked to SiteEntry:" + GPMDataModel.siteEntryToSavedGPMStateFlow.value.entries.joinToString { (key, value) ->
                    "Key: $key, Values: ${
                        value.joinToString(
                            ", "
                        )
                    }"
                })
        }
        val categories = DataModel.categoriesStateFlow.value.filter { (it.id ?: 0L) > 0L }
        assertEquals(2, categories.size)
        assertEquals("encryptedcat1", categories[0].plainName)
        assertEquals(1L, categories[0].id)
        assertEquals("encryptedcat2", categories[1].plainName)
        assertEquals(2L, categories[1].id)

        val gpms = GPMDataModel.allSavedGPMsFlow.value
        assertEquals(2, gpms.size)
        assertEquals("hash", gpms.first().hash)
        assertEquals(1L, gpms.first().id)
        assertEquals(false, gpms.first().flaggedIgnored)
        assertEquals("web", gpms.first().cachedDecryptedUrl)
        assertEquals("user", gpms.first().cachedDecryptedUsername)
        assertEquals("name", gpms.first().cachedDecryptedName)
        assertEquals("note", gpms.first().cachedDecryptedNote)
        assertEquals("password", gpms.first().cachedDecryptedPassword)
        assertEquals(2L, GPMDataModel.getLinkedGPMs(202L).first().id)

        val passwords = DataModel.siteEntriesStateFlow.value
        val categoryIds = listOf(1L, 2L, 2L, 1L)
        val siteEntryIds = listOf(101L, 201L, 202L, 203L)
        val mockIds = listOf(11L, 21L, 22L, 12L)
        (0..3).forEach { id ->
            val categoryId = categoryIds[id]
            if (id == 0) {
                // target runs on what ever timezone (github Etc/UTC) so convert to our test TZ
                val actualChangedDateTime = passwords[id].passwordChangedDate
                assertEquals(fakeChangedDateTime, actualChangedDateTime)
            } else {
                assertEquals(null, passwords[id].passwordChangedDate)
            }
            assertEquals(categoryId, passwords[id].categoryId)
            val mockId = mockIds[id]
            assertEquals(siteEntryIds[id], passwords[id].id)
            assertEquals("enc_desc$mockId", passwords[id].plainDescription)
            assertEquals("enc_web$mockId", passwords[id].plainWebsite)
            assertEquals("enc_user$mockId", passwords[id].plainUsername)
            assertEquals("enc_pwd$mockId", passwords[id].plainPassword)
            assertEquals("enc_note$mockId", passwords[id].plainNote)
            // TODO: Actually mock is bit lacking, in REAL data model this password would never
            // pass since it was soft deleted so long ago...
            if (passwords[id].id !in setOf(101L, 201L, 202L)) {
                assertEquals(666, passwords[id].deleted)
                assertEquals(
                    "[payments:visa,mastercard, phones:123-456-7890]",
                    passwords[id].plainExtensions.map { m ->
                        m.key + ":" + m.value.joinToString(
                            ","
                        )
                    }.toString()
                )
            } else {
                assertEquals(0, passwords[id].deleted)
                assertEquals(0, passwords[id].plainExtensions.size)
            }
            assertEquals(null, passwords[id].plainPhoto)
        }
    }

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

        // TODO: missing changed <item id="2" deleted="1234>
        private const val TOP_LAYER_UNENCRYPTED_BACKUP_XML = """
<PasswordSafe version="1" created="1234">
    <category iv_name="0102030405060708090a0b0c0d0e0f10" cipher_name="646c60767c76736d6d696a783c">
        <item ID="101">
            <description iv="0102030405060708090a0b0c0d0e0f10">646c605b6163746b383b</description>
            <website iv="0102030405060708090a0b0c0d0e0f10">646c605b7263653938</website>
            <username iv="0102030405060708090a0b0c0d0e0f10">646c605b7075627a383b</username>
            <password changed="946630923" iv="0102030405060708090a0b0c0d0e0f10">646c605b7571633938</password>
            <note iv="0102030405060708090a0b0c0d0e0f10">646c605b6b69736d383b</note>
            </item>
        <item ID="102" deleted="666">
            <description iv="0102030405060708090a0b0c0d0e0f10">646c605b6163746b3838</description>
            <website iv="0102030405060708090a0b0c0d0e0f10">646c605b726365393b</website>
            <username iv="0102030405060708090a0b0c0d0e0f10">646c605b7075627a3838</username>
            <password iv="0102030405060708090a0b0c0d0e0f10">646c605b757163393b</password>
            <note iv="0102030405060708090a0b0c0d0e0f10">646c605b6b69736d3838</note>
            <extension iv="0102030405060708090a0b0c0d0e0f10">7a2073657c6b62667d792936562c797972632128276b667b7d6f796f6c7c6b325c2e21746d69696d7a2831572f3f3d232c36363228313f3139285671</extension>
        </item>
    </category>
    <category iv_name="0102030405060708090a0b0c0d0e0f10" cipher_name="646c60767c76736d6d696a783f">
        <item ID="201">
            <description iv="0102030405060708090a0b0c0d0e0f10">646c605b6163746b3b3b</description>
            <website iv="0102030405060708090a0b0c0d0e0f10">646c605b7263653a38</website>
            <username iv="0102030405060708090a0b0c0d0e0f10">646c605b7075627a3b3b</username>
            <password iv="0102030405060708090a0b0c0d0e0f10">646c605b7571633a38</password>
            <note iv="0102030405060708090a0b0c0d0e0f10">646c605b6b69736d3b3b</note>
        </item>
        <item ID="202">
            <description iv="0102030405060708090a0b0c0d0e0f10">646c605b6163746b3b38</description>
            <website iv="0102030405060708090a0b0c0d0e0f10">646c605b7263653a3b</website>
            <username iv="0102030405060708090a0b0c0d0e0f10">646c605b7075627a3b38</username>
            <password iv="0102030405060708090a0b0c0d0e0f10">646c605b7571633a3b</password>
            <note iv="0102030405060708090a0b0c0d0e0f10">646c605b6b69736d3b38</note>
        </item>
    </category>
    <imports>
        <gpm>
            <gpmitem hash="hash" name="1" password_ids="102" iv_name="0102030405060708090a0b0c0d0e0f10" cipher_name="6f636e61" iv_note="0102030405060708090a0b0c0d0e0f10" cipher_note="6f6d7761" iv_password="0102030405060708090a0b0c0d0e0f10" cipher_password="716370777269756c" status="0" iv_url="0102030405060708090a0b0c0d0e0f10" cipher_url="766761" iv_username="0102030405060708090a0b0c0d0e0f10" cipher_username="74716676" />
            <gpmitem hash="hash" name="2" password_ids="202" iv_name="0102030405060708090a0b0c0d0e0f10" cipher_name="6f636e61" iv_note="0102030405060708090a0b0c0d0e0f10" cipher_note="6f6d7761" iv_password="0102030405060708090a0b0c0d0e0f10" cipher_password="716370777269756c" status="0" iv_url="0102030405060708090a0b0c0d0e0f10" cipher_url="766761" iv_username="0102030405060708090a0b0c0d0e0f10" cipher_username="74716676" />
        </gpm>
        <cxf>
            <cxfaccount cxf_account_id="acc-hex-1" iv_email="0102030405060708090a0b0c0d0e0f10" cipher_email="646c6076" />
            <cxfimport cxf_account_id="acc-hex-1" iv_cxf_item_id="0102030405060708090a0b0c0d0e0f10" cipher_cxf_item_id="313031" type="password" iv_name="0102030405060708090a0b0c0d0e0f10" cipher_name="6f636e61" iv_url="0102030405060708090a0b0c0d0e0f10" cipher_url="766761" iv_username="0102030405060708090a0b0c0d0e0f10" cipher_username="74716676" iv_password="0102030405060708090a0b0c0d0e0f10" cipher_password="716370777269756c" iv_note="0102030405060708090a0b0c0d0e0f10" cipher_note="6f6d7761" hash="hex-cxf-hash-1" flagged_ignored="0" />
            <cxfpasskey cxf_account_id="acc-hex-1" iv_cxf_item_id="0102030405060708090a0b0c0d0e0f10" cipher_cxf_item_id="313032" relying_party="example.org" iv_name="0102030405060708090a0b0c0d0e0f10" cipher_name="6f636e61" iv_url="0102030405060708090a0b0c0d0e0f10" cipher_url="766761" iv_username="0102030405060708090a0b0c0d0e0f10" cipher_username="74716676" iv_credential_id="0102030405060708090a0b0c0d0e0f10" cipher_credential_id="63726564" iv_user_handle="0102030405060708090a0b0c0d0e0f10" cipher_user_handle="68616e646c65" iv_note="0102030405060708090a0b0c0d0e0f10" cipher_note="6f6d7761" hash="hex-passkey-hash-1" flagged_ignored="0" />
        </cxf>
    </imports>
    </PasswordSafe>
        """
        private val PASSWORD_ENCRYPTED_BACKUP_AT_1234 = """
${salt.toHex()}
${cipheredMasterKey.iv.toHexString()}
${cipheredMasterKey.cipherText.toHexString()}
0102030405060708090a0b0c0d0e0f10
3d5262777671687a6d596a6a682e797573716a6b6b3b25392b2a687e686f7b75653f21353735332a3736686d796b687f737b236d73596969646f362e3d3f3f22313133303533373e393d3b343d373f71316033673562376d396c3a3c2f2e6c79716a66765a6866656c37293a39386c26313535336631313f3a3c6f3a6938362660353b3766243934607e6e612d474b2d233332263b3a636d7a6979657d7a667f6f226a723824373939383b3f3d3a3f2531343333353e3731396b3b6e3d6d3f7431673362343625363f3e3d6f3b3e3a72373335373232316a3a32386e31216b757261716d75726e676734377b686c7c797567236d733b2538383a393c3e3e3b203432353432363f38303a6a3c6f3e6c20653266346337372a373c3f3a6e383f25633531323630323b3039333022796a72726b77613b3a727b6c78656d606b2f79773f2134343635383a3a3f3c383e392036323b343c3666386b3a683c693e6a20673333263b30333e6a3c3b396f393f273434313364353f3b6b3624797e6b7d7e606f663a3976667b7a7d647e692e6c78606c6461613b25313d3c3d3f3d373d2323226a723824373939383b3f3d3a3f2531343333353e3731396b3b6e3d6d3f7431673362343625363f3e3d6f3b3e3a7236373435333534313a3237237d6f7c63766d71603b3a69677d6f2b657b332d2030323134363633383c3a3d3c3a3e372038326234673664386d3a6e3c6b3f3f323f3437326630373d6b3c693a34393c2665313b37673a2866667e6e3231216664646f3d386c72626529434f312f3f3d323f3e676176657561797e6263632e66663c2033353534373b393e3b393d383f27313a333d3567376a39693b683d6b3f763032213a3332316b3f3a3e6e3b3f392336363566363e343035256f697e6d7d7971766a6b6b383b7f6c687865796b2f79773f2134343635383a3a3f3c383e392036323b343c3666386b3a683c693e6a20673333263b30333e6a3c3b396f393d26323436373c356534267d6e6e7e677b753f3e767760746969646f2b657b332d2030323134363633383c3a3d3c3a3e372038326234673664386d3a6e3c6b3f3f323f3437326630373d6b3d3b3b38383d2760313b373d3a287d7a6f79626c636a2e3d7262777671687a6d2a627a302c3f21313033373532373d393c3b3b3d363f29316333663565376c396f3b6a3c3e2d2e373635673336326a3e3f3c3d3b3d3c2932603f2b7567747b7e6579683332617f7567236d733b2538383a393c3e3e3b203432353432363f38303a6a3c6f3e6c20653266346337372a373c3f3a6e383f25633461323c31343e6d39333f3532207e6e76663a39296e7c6c673530226d6e6464656c767c383b6b687e6e6b627c763068745c6a646b62352b3a3a3c3f3e3c203532363433363038313a323c6c3e6d20623267346036613939282b6f647e6775735d6d6568633a2a3f3e3d6f3b3e3826366134323235316c3f6e3d353b6f38283264213a396f736d642a4248302c3d21233c3f606075647a607a7f6562602f79773f2134343635383a3a3f3c383e392036323b343c3666386b3a683c693e6a20673333263b30333e6a3c3b396f383e26323537326735653b6b362468687d6c626872776d6a6839347e6f697f647a6a3068743e263537373a39393b383d3b3f263135333c353f376939683b6f3d6a3f75316432342738313c3f693d3c386c3822373135313667343035257c696f7d6664643c3f717663756668676e2c64783232313333363535373c393f3b3a3d393f28313b33653564376b396e3b693d683e20233c3530336531383c683c3c3a3b39223663306636643b277c796e7e636f62753f3e7365767570677b6e2b657b332d2030323134363633383c3a3d3c3a3e372038326234673664386d3a6e3c6b3f3f323f3437326630373d6b3d3e3b3c383c2360313b382a76667b7a7d647e6930337e6e7666246c703a2a393b3b3e3d3d3f24313733323531373039333b6d3d6c3f7331663361356036382b343d383b6d392034603566333f303b3f6e386e3e6c333f6f6d77613b3a28617d6f663231677b756c224a403824353a2b343768687d6c626872776d6a6827617f37293c3c3e3d2032323734303631383e3a333c343e6e2063326034613662386f3b3b2e33383b26623433316730363e3a3d3f3a6f3d6d23393e2c606075647a607a7f656260312c766761776c726228607c362e3d3f3f22313133303533373e393d3b343d373f71316033673562376d396c3a3c2f303924376135343064303a3f393d393e6f3c723d2d746167756e7c6c3437797e6b7d7e606f66246c703a2a393b3b3e3d3d3f24313733323531373039333b6d3d6c3f7331663361356036382b343d383b6d3920346034343233313a3e6b386e3e36333f747166766b676a6d37367b6d7e7d787f7366236d733b2538383a393c3e3e3b203432353432363f38303a6a3c6f3e6c20653266346337372a373c3f3a6e383f25633536333430343b68396930227e6e6372756c7661383b66667e6e2c64783232313333363535373c393f3b3a3d393f28313b33653564376b396e3b693d683e20233c3530336531383c683d6e3b37382337663066363e3b2767657f693332207975676e3a392964697d6f6c637f77312c686f736b777274363569736a33326c68676360676a73697c2969736a526f6c736e776d705a6f63352b6b686f20666a682c3321246c70586d646b6260302c3f21313033373532373d393c3b3b3d363f29316333663565376c396f3b6a3c3e2d30626b736c6074586d646b6260302c39243761353432302527373668746b6762606e707724667e61576869686378607b4f68663e2664656425616f73213c2c2f79775d607c63596e7c6c67546569332d2030323134363633383c3a3d3c3a3e372038326234673664386d3a6e3c6b3f3f3221616a746d6375576a726d53647a6a7d5e6b67392735363b39393a2e2d7a7660643f21746475747f66786f2e2d67794f6f636e613824373939383b3f3d3a3f2531343333353e3731396b3b6e3d6d3f7431673362343625286a637b64687c507e606f66392730613e3a3c6e3a3c2c2f79775d7676693b2538383a393c3e3e3b203432353432363f38303a6a3c6f3e6c20653266346337372a2969627c656b7d4f74706f392731313e3e3c3a2e2d67794f747166766b676a6d34283b3d3d3c3f23313633313530373f39323b353d6f3f72316133603563376e383a292c6e677f7864705c717663756668676e312f393b2730343533332427617f557b6d7e7d787f73663e263537373a39393b383d3b3f263135333c353f376939683b6f3d6a3f75316432342726646179626e7e527e6e6372756c76613b253f383c383b3d39382733343a333030642a29637d5363617b753c2033353534373b393e3b393d383f27313a333d3567376a39693b683d6b3f7630322124666f77606c785462627a6a2d233465326131303e38282b646c7d672d236a667c28657f6e24626a7f65233e3221646f656261626c56636c62627c6a743c2033262a383b6b716c7b6d7e7d64757822607c6359666b6a657e62795166743c206267662b6f6d71273a2e2d67794f627a655b6c72626556636f312f3e3e2033323034313632383f3a3c3c353e362060326134663663386c3a6d3d3d2c2f7368726b61775964706f55627868635079653f21373435373b3b282b7e686276796f655c746474737134286e746c637f7c642c6c76622427617f55656d606b3232313333363535373c393f3b3a3d393f28313b33653564376b396e3b693d683e202322606d756e627a56646a6168332d26673430326030362a29637d53787c632d23323234373634383d3a3e3c3b3e382039323a34643665386a3a6f3c683e6921312023676c766f6d7b557e7e61332d2737343432342427617f557e7f687c61716c673e263537373a39393b383d3b3f263135333c353f376939683b6f3d6a3f75316432342726646179626e7e527b7c75736c6269603b253f3d3d3a3a3b393932216b755b6674626c6c647f656c625079653f2134343635383a3a3f3c383e392036323b343c3666386b3a683c693e6a206733332625656e78616f79536e7c6a74646c776d646a58616d37293a3e393d2634343726256f71577c796e7e52666e7e656e6639273636383b3a383c393e3a20373234343d363e38683a693c6e3e6b20643265353524276b607a63697f517a6364705c6c646863646c37293a35383e26643437326630322a29637d5363617b753c2033353534373b393e3b393d383f27313a333d3567376a39693b683d6b3f7630322124666f77606c785462627a6a2d233465326131303e38282b646c7d672d236a667c2876667b7a616e7520666e63692f322625606b696e6d6e685267687e6e7066603824372a263437236e76692e3d2d6a697569757c7a3437235d6f7c63766d71605667616d37
"""
    }
}
