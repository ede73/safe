package fi.iki.ede.gpmui

import android.content.Context
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import kotlinx.coroutines.flow.StateFlow

typealias DBID = Long

interface DataModelIF {
    fun deleteAllSavedGPMs()
    suspend fun loadFromDatabase()
    fun markSavedGPMIgnored(id: DBID)
    fun linkSaveGPMAndSiteEntry(siteEntry: DecryptableSiteEntry, gpmId: DBID)
    suspend fun addOrEditCategory(
        category: DecryptableCategoryEntry,
        onAdd: suspend (DecryptableCategoryEntry) -> Unit = {}
    )

    suspend fun addGpmAsSiteEntry(
        savedGpmId: DBID,
        categoryId: DBID,
        onAdd: suspend (DecryptableSiteEntry) -> Unit
    )

    fun finishGPMImport(
        delete: Set<SavedGPM>,
        update: Map<IncomingGPM, SavedGPM>,
        add: Set<IncomingGPM>
    )

    // TODO: UGLY
    fun fetchSiteEntriesStateFlow(): StateFlow<List<DecryptableSiteEntry>>
    fun fetchUnprocessedGPMsFlow(): StateFlow<Set<SavedGPM>>
    fun fetchAllSavedGPMsFlow(): StateFlow<Set<SavedGPM>>

    fun findCategoryByName(name: String): DecryptableCategoryEntry?
    fun startEditPassword(context: Context, passwordId: DBID)
    fun firebaseLog(message: String)
    fun firebaseRecordException(message: String, ex: Throwable)
}