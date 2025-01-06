package fi.iki.ede.safe.model

import android.content.Context
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpmui.DataModelIF
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DataModel.allSavedGPMsFlow
import fi.iki.ede.safe.model.DataModel.categoriesStateFlow
import fi.iki.ede.safe.model.DataModel.siteEntriesStateFlow
import fi.iki.ede.safe.model.DataModel.unprocessedGPMsFlow
import fi.iki.ede.safe.splits.IntentManager
import kotlinx.coroutines.flow.StateFlow

object DataModelForGPM : DataModelIF {
    override fun deleteAllSavedGPMs() = DataModel.deleteAllSavedGPMs()

    override suspend fun loadFromDatabase() = DataModel.loadFromDatabase()

    override fun markSavedGPMIgnored(id: DBID) = DataModel.markSavedGPMIgnored(id)

    override fun linkSaveGPMAndSiteEntry(
        siteEntry: DecryptableSiteEntry,
        gpmId: DBID
    ) = DataModel.linkSaveGPMAndSiteEntry(siteEntry, gpmId)

    override suspend fun addOrEditCategory(
        category: DecryptableCategoryEntry,
        onAdd: suspend (DecryptableCategoryEntry) -> Unit
    ) = DataModel.addOrEditCategory(category, onAdd)

    override suspend fun addGpmAsSiteEntry(
        savedGpmId: DBID,
        categoryId: DBID,
        onAdd: suspend (DecryptableSiteEntry) -> Unit
    ) = DataModel.addGpmAsSiteEntry(savedGpmId, categoryId, onAdd)

    override fun finishGPMImport(
        delete: Set<SavedGPM>,
        update: Map<IncomingGPM, SavedGPM>,
        add: Set<IncomingGPM>
    ) = DataModel.finishGPMImport(delete, update, add)

    override fun fetchSiteEntriesStateFlow(): StateFlow<List<DecryptableSiteEntry>> =
        siteEntriesStateFlow

    override fun fetchUnprocessedGPMsFlow(): StateFlow<Set<SavedGPM>> = unprocessedGPMsFlow
    override fun fetchAllSavedGPMsFlow(): StateFlow<Set<SavedGPM>> = allSavedGPMsFlow
    override fun findCategoryByName(name: String): DecryptableCategoryEntry? =
        categoriesStateFlow.value.firstOrNull { it.plainName == name }

    override fun startEditPassword(context: Context, passwordId: fi.iki.ede.gpmui.DBID) =
        IntentManager.startEditPassword(context, passwordId)

    override fun firebaseLog(message: String) {
        firebaseLog(message)
    }

    override fun firebaseRecordException(message: String, ex: Throwable) {
        firebaseRecordException(message, ex)
    }
}