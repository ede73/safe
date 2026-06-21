@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.desktop

import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBHelperFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import fi.iki.ede.crypto.KeystoreHelperMock4UnitTests
import io.mockk.unmockkAll

class ImportExportTest {
    private lateinit var db: DBHelper

    @BeforeEach
    fun setUp() {
        KeystoreHelperMock4UnitTests.mock()

        db = DBHelper(databaseName = null)
        DBHelperFactory.initializeDatabase(db)
        val password = "secret"
        try {
            val (salt, cipheredKey) = fi.iki.ede.crypto.keystore.KeyStoreHelper.createNewKey(fi.iki.ede.crypto.Password(password))
            db.storeSaltAndEncryptedMasterKey(salt, cipheredKey)
        } catch (e: Exception) {
            // Already initialized or mock setup is enough
        }
        db.clearAllData()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        db.database.close()
        DBHelperFactory.clearDatabase()
    }

    @Test
    fun testImportExportRoundTrip() {
        val originalBackupContent = PASSWORD_ENCRYPTED_BACKUP_AT_1234.trimIndent()
        val password = "secret"

        // 1. Import original backup XML
        val importedCount = BackupImporter.importFromXml(originalBackupContent, password, db)
        assertTrue(importedCount > 0, "Should import at least one password entry")

        val categoriesBefore = db.fetchAllCategoryRows()
        val siteEntriesBefore = db.fetchAllRows()

        // Verify categories and site entries are loaded
        assertEquals(2, categoriesBefore.size, "Should import 2 categories")
        assertEquals(4, siteEntriesBefore.size, "Should import 4 site entries")

        // 2. Export to XML
        val exportedBackupContent = BackupExporter.exportToXml(db)
        try {
            java.io.File("exported_backup.xml").writeText(exportedBackupContent)
            System.err.println("Successfully wrote exported_backup.xml of size: " + exportedBackupContent.length)
        } catch (e: Exception) {
            System.err.println("Failed to write scratch file: " + e.message)
        }
        assertNotNull(exportedBackupContent)
        assertTrue(exportedBackupContent.lines().size >= 5, "Exported backup should have at least 5 lines")

        // 3. Clear database
        db.clearAllData()
        
        assertEquals(0, db.fetchAllCategoryRows().size)
        assertEquals(0, db.fetchAllRows().size)
        

        // 4. Import the exported backup XML
        val reImportedCount = BackupImporter.importFromXml(exportedBackupContent, password, db)
        assertEquals(importedCount, reImportedCount, "Re-imported count should match originally imported count")

        val categoriesAfter = db.fetchAllCategoryRows()
        val siteEntriesAfter = db.fetchAllRows()

        // 5. Assert equivalence
        assertEquals(categoriesBefore.size, categoriesAfter.size)
        for (i in categoriesBefore.indices) {
            val before = categoriesBefore[i]
            val after = categoriesAfter.firstOrNull { it.plainName == before.plainName }
            assertNotNull(after, "Category '${before.plainName}' was not preserved")
        }

        assertEquals(siteEntriesBefore.size, siteEntriesAfter.size)
        for (before in siteEntriesBefore) {
            val after = requireNotNull(siteEntriesAfter.firstOrNull { it.cachedPlainDescription == before.cachedPlainDescription }) {
                "Site entry '${before.cachedPlainDescription}' was not preserved"
            }
            
            assertEquals(before.plainUsername, after.plainUsername)
            assertEquals(before.plainPassword, after.plainPassword)
            assertEquals(before.plainWebsite, after.plainWebsite)
            assertEquals(before.plainNote, after.plainNote)
            assertEquals(before.deleted, after.deleted)
            assertEquals(before.photo, after.photo)
            assertEquals(before.extensions, after.extensions)
            assertEquals(before.passwordChangedDate, after.passwordChangedDate)
        }
    }

    companion object {
        private val PASSWORD_ENCRYPTED_BACKUP_AT_1234 = """
9b90e143578bdbe7
f670556d2e7992d1c1a074a126291ebe
a140e6578ca43fa20e6e9fa74e4a5079b695f1c65d77f362d0880ce36e170203dacf3278664e25ea91097ef1148085e3
0102030405060708090a0b0c0d0e0f10
3d5262777671687a6d596a6a682e797573716a6b6b3b25392b2a687e686f7b75653f21353735332a3736686d796b687f737b236d73596969646f362e3d3f3f22313133303533373e393d3b343d373f71316033673562376d396c3a3c2f2e6c79716a66765a6866656c37293a39386c26313535336631313f3a3c6f3a6938362660353b3766243934607e6e612d474b2d233332263b3a636d7a6979657d7a667f6f226a723824373939383b3f3d3a3f2531343333353e3731396b3b6e3d6d3f7431673362343625363f3e3d6f3b3e3a72373335373232316a3a32386e31216b757261716d75726e676734377b686c7c797567236d733b2538383a393c3e3e3b203432353432363f38303a6a3c6f3e6c20653266346337372a373c3f3a6e383f25633531323630323b3039333022796a72726b77613b3a727b6c78656d606b2f79773f2134343635383a3a3f3c383e392036323b343c3666386b3a683c693e6a20673333263b30333e6a3c3b396f393f273434313364353f3b6b3624797e6b7d7e606f663a3976667b7a7d647e692e6c78606c6461613b25313d3c3d3f3d373d2323226a723824373939383b3f3d3a3f2531343333353e3731396b3b6e3d6d3f7431673362343625363f3e3d6f3b3e3a7236373435333534313a3237237d6f7c63766d71603b3a69677d6f2b657b332d2030323134363633383c3a3d3c3a3e372038326234673664386d3a6e3c6b3f3f323f3437326630373d6b3c693a34393c2665313b37673a2866667e6e3231216664646f3d386c72626529434f312f3f3d323f3e676176657561797e6263632e66663c2033353534373b393e3b393d383f27313a333d3567376a39693b683d6b3f763032213a3332316b3f3a3e6e3b3f392336363566363e343035256f697e6d7d7971766a6b6b383b7f6c687865796b2f79773f2134343635383a3a3f3c383e392036323b343c3666386b3a683c693e6a20673333263b30333e6a3c3b396f393d26323436373c356534267d6e6e7e677b753f3e767760746969646f2b657b332d2030323134363633383c3a3d3c3a3e372038326234673664386d3a6e3c6b3f3f323f3437326630373d6b3d3b3b38383d2760313b373d3a287d7a6f79626c636a2e3d7262777671687a6d2a627a302c3f21313033373532373d393c3b3b3d363f29316333663565376c396f3b6a3c3e2d2e373635673336326a3e3f3c3d3b3d3c2932603f2b7567747b7e6579683332617f7567236d733b2538383a393c3e3e3b203432353432363f38303a6a3c6f3e6c20653266346337372a373c3f3a6e383f25633461323c31343e6d39333f3532207e6e76663a39296e7c6c673530226d6e6464656c767c383b6b687e6e6b627c763068745c6a646b62352b3a3a3c3f3e3c203532363433363038313a323c6c3e6d20623267346036613939282b6f647e6775735d6d6568633a2a3f3e3d6f3b3e3826366134323235316c3f6e3d353b6f38283264213a396f736d642a4248302c3d21233c3f606075647a607a7f6562602f79773f2134343635383a3a3f3c383e392036323b343c3666386b3a683c693e6a20673333263b30333e6a3c3b396f383e26323537326735653b6b362468687d6c626872776d6a6839347e6f697f647a6a3068743e263537373a39393b383d3b3f263135333c353f376939683b6f3d6a3f75316432342738313c3f693d3c386c3822373135313667343035257c696f7d6664643c3f717663756668676e2c64783232313333363535373c393f3b3a3d393f28313b33653564376b396e3b693d683e20233c3530336531383c683c3c3a3b39223663306636643b277c796e7e636f62753f3e7365767570677b6e2b657b332d2030323134363633383c3a3d3c3a3e372038326234673664386d3a6e3c6b3f3f323f3437326630373d6b3d3e3b3c383c2360313b382a76667b7a7d647e6930337e6e7666246c703a2a393b3b3e3d3d3f24313733323531373039333b6d3d6c3f7331663361356036382b343d383b6d392034603566333f303b3f6e386e3e6c333f6f6d77613b3a28617d6f663231677b756c224a403824353a2b343768687d6c626872776d6a6827617f37293c3c3e3d2032323734303631383e3a333c343e6e2063326034613662386f3b3b2e33383b26623433316730363e3a3d3f3a6f3d6d23393e2c606075647a607a7f656260312c766761776c726228607c362e3d3f3f22313133303533373e393d3b343d373f71316033673562376d396c3a3c2f303924376135343064303a3f393d393e6f3c723d2d746167756e7c6c3437797e6b7d7e606f66246c703a2a393b3b3e3d3d3f24313733323531373039333b6d3d6c3f7331663361356036382b343d383b6d3920346034343233313a3e6b386e3e36333f747166766b676a6d37367b6d7e7d787f7366236d733b2538383a393c3e3e3b203432353432363f38303a6a3c6f3e6c20653266346337372a373c3f3a6e383f25633536333430343b68396930227e6e6372756c7661383b66667e6e2c64783232313333363535373c393f3b3a3d393f28313b33653564376b396e3b693d683e20233c3530336531383c683d6e3b37382337663066363e3b2767657f693332207975676e3a392964697d6f6c637f77312c2e5262777671687a6d596a6a6830
"""
    }
}
