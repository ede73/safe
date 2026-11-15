package fi.iki.ede.safe.sync

import fi.iki.ede.safe.ui.sync.SyncConflictResolver
import fi.iki.ede.safe.ui.sync.SyncItem
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File

class LiveSyncRunnerTest {

    @Test
    fun testThreeWayFullConvergence() = runBlocking {
        val rootDir = File(".").canonicalFile.let {
            if (it.name == "desktop") it.parentFile else it
        }
        val androidSeed = File(rootDir, "desktop/build/seed_dbs/seed_android.db")
        val iosSeed = File(rootDir, "desktop/build/seed_dbs/seed_ios.db")
        val desktopSeed = File(rootDir, "desktop/build/seed_dbs/seed_desktop.db")

        val androidItems = readDbItems(androidSeed)
        val iosItems = readDbItems(iosSeed)
        val desktopItems = readDbItems(desktopSeed)

        println("=== INITIAL STATES ===")
        println("Android categories/items (${androidItems.size}):")
        androidItems.forEach { println("  [${it.categoryName}] ${it.name} (${it.username})") }

        println("iOS categories/items (${iosItems.size}):")
        iosItems.forEach { println("  [${it.categoryName}] ${it.name} (${it.username})") }

        println("Desktop categories/items (${desktopItems.size}):")
        desktopItems.forEach { println("  [${it.categoryName}] ${it.name} (${it.username})") }

        // STEP 1: Android <-> iOS Sync
        println("\n=== STEP 1: Android <-> iOS Sync ===")
        val androidIosConflicts = SyncConflictResolver.analyzeConflicts(androidItems, iosItems)
        val step1Merged = androidIosConflicts.map { c ->
            val catName = c.localItem?.categoryName?.ifBlank { null }
                ?: c.remoteItem?.categoryName?.ifBlank { null }
                ?: "General Vault"
            SyncItem(
                cxfItemId = c.localItem?.cxfItemId ?: c.remoteItem?.cxfItemId ?: c.conflictId,
                name = c.selectedName,
                username = c.selectedUsername,
                password = c.selectedPassword,
                note = c.selectedNote,
                modifiedAt = maxOf(c.localItem?.modifiedAt ?: 0L, c.remoteItem?.modifiedAt ?: 0L),
                isDeleted = c.selectedIsDeleted,
                categoryName = catName
            )
        }
        val step1Categories = step1Merged.map { it.categoryName }.distinct()
        println("Step 1 Merged total items: ${step1Merged.size}, categories (${step1Categories.size}): $step1Categories")

        // STEP 2: (Android + iOS) <-> Desktop Sync
        println("\n=== STEP 2: Desktop <-> (Android + iOS) Sync ===")
        val finalConflicts = SyncConflictResolver.analyzeConflicts(step1Merged, desktopItems)
        val fullyMerged = finalConflicts.map { c ->
            val catName = c.localItem?.categoryName?.ifBlank { null }
                ?: c.remoteItem?.categoryName?.ifBlank { null }
                ?: "General Vault"
            SyncItem(
                cxfItemId = c.localItem?.cxfItemId ?: c.remoteItem?.cxfItemId ?: c.conflictId,
                name = c.selectedName,
                username = c.selectedUsername,
                password = c.selectedPassword,
                note = c.selectedNote,
                modifiedAt = maxOf(c.localItem?.modifiedAt ?: 0L, c.remoteItem?.modifiedAt ?: 0L),
                isDeleted = c.selectedIsDeleted,
                categoryName = catName
            )
        }
        val finalCategories = fullyMerged.map { it.categoryName }.distinct()
        println("FINAL CONVERGED: ${fullyMerged.size} items across ${finalCategories.size} categories:")
        finalCategories.forEach { cat ->
            val catItems = fullyMerged.filter { it.categoryName == cat }
            println("  Category '$cat' (${catItems.size} items):")
            catItems.forEach { println("    - ${it.name} | user: ${it.username} | pass: ${it.password}") }
        }

        // Deploy converged database to seed_dbs and simulator/desktop/android
        val convergedDb = File(rootDir, "desktop/build/seed_dbs/converged.db")
        convergedDb.delete()
        writeDbItems(convergedDb, fullyMerged)
        println("\nConverged database written to ${convergedDb.absolutePath}")
    }

    private fun runSqlite(db: File, sql: String): String {
        val pb = ProcessBuilder("sqlite3", db.absolutePath, sql)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return out
    }

    private fun readDbItems(dbFile: File): List<SyncItem> {
        val items = mutableListOf<SyncItem>()
        val sql = "SELECT s.id, s.name, s.username, s.password, coalesce(s.note, ''), s.updated_at, s.is_deleted, c.name FROM siteentry s JOIN category c ON s.category_id = c.id;"
        val output = runSqlite(dbFile, sql)
        output.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 8) {
                items.add(
                    SyncItem(
                        cxfItemId = parts[0],
                        name = parts[1],
                        username = parts[2],
                        password = parts[3],
                        note = parts[4],
                        modifiedAt = parts[5].toLongOrNull() ?: 0L,
                        isDeleted = parts[6] == "1",
                        categoryName = parts[7]
                    )
                )
            }
        }
        return items
    }

    private fun writeDbItems(dbFile: File, items: List<SyncItem>) {
        val sb = StringBuilder()
        sb.append("CREATE TABLE IF NOT EXISTS category (id TEXT PRIMARY KEY, name TEXT UNIQUE, updated_at INTEGER, is_deleted INTEGER DEFAULT 0);\n")
        sb.append("CREATE TABLE IF NOT EXISTS siteentry (id TEXT PRIMARY KEY, category_id TEXT, name TEXT, username TEXT, password TEXT, note TEXT, updated_at INTEGER, is_deleted INTEGER DEFAULT 0);\n")
        
        val catMap = mutableMapOf<String, String>()
        val catOut = runSqlite(dbFile, "SELECT id, name FROM category;")
        catOut.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 2) {
                catMap[parts[1]] = parts[0]
            }
        }

        val now = System.currentTimeMillis()
        for (item in items) {
            val catName = item.categoryName.ifBlank { "General Vault" }
            var catId = catMap[catName]
            if (catId == null) {
                catId = "cat_${java.util.UUID.randomUUID().toString().take(8)}"
                val escapedCat = catName.replace("'", "''")
                sb.append("INSERT OR IGNORE INTO category (id, name, updated_at, is_deleted) VALUES ('$catId', '$escapedCat', $now, 0);\n")
                catMap[catName] = catId
            }
            val escapedName = item.name.replace("'", "''")
            val escapedUser = item.username.replace("'", "''")
            val escapedPass = item.password.replace("'", "''")
            val escapedNote = item.note.replace("'", "''")
            val del = if (item.isDeleted) 1 else 0
            sb.append(
                """
                INSERT INTO siteentry (id, category_id, name, username, password, note, updated_at, is_deleted)
                VALUES ('${item.cxfItemId}', '$catId', '$escapedName', '$escapedUser', '$escapedPass', '$escapedNote', ${item.modifiedAt}, $del)
                ON CONFLICT(id) DO UPDATE SET
                    category_id=excluded.category_id,
                    name=excluded.name,
                    username=excluded.username,
                    password=excluded.password,
                    note=excluded.note,
                    updated_at=excluded.updated_at,
                    is_deleted=excluded.is_deleted;
                """.trimIndent()
            ).append("\n")
        }
        runSqlite(dbFile, sb.toString())
    }
}
