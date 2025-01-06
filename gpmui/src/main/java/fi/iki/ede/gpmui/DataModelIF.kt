package fi.iki.ede.gpmui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBID
import kotlinx.coroutines.flow.StateFlow

interface DataModelIF {
    suspend fun addOrEditCategory(
        category: DecryptableCategoryEntry,
        onAdd: suspend (DecryptableCategoryEntry) -> Unit = {}
    )

    suspend fun addOrUpdateSiteEntry(
        siteEntry: DecryptableSiteEntry,
        onAdd: suspend (DecryptableSiteEntry) -> Unit = {}
    )

    fun fetchSiteEntriesStateFlow(): StateFlow<List<DecryptableSiteEntry>>
    fun findCategoryByName(name: String): DecryptableCategoryEntry?
    fun firebaseLog(message: String)
    fun firebaseRecordException(message: String, ex: Throwable)
    fun getReadableDatabase(): SQLiteDatabase
    fun getWritableDatabase(): SQLiteDatabase
    fun startEditPassword(context: Context, passwordId: DBID)
    suspend fun loadFromDatabase()
}