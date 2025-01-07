package fi.iki.ede.safe.model

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.db.DBID
import fi.iki.ede.gpmui.DataModelIF
import fi.iki.ede.safe.model.DataModel.categoriesStateFlow
import fi.iki.ede.safe.splits.IntentManager
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "DataModelForGPM"

object DataModelForGPM : DataModelIF {
    override fun getReadableDatabase(): SQLiteDatabase =
        DBHelperFactory.getDBHelper().readableDatabase

    override fun getWritableDatabase(): SQLiteDatabase =
        DBHelperFactory.getDBHelper().writableDatabase

    override suspend fun addOrEditCategory(
        category: DecryptableCategoryEntry,
        onAdd: suspend (DecryptableCategoryEntry) -> Unit
    ) = DataModel.addOrEditCategory(category, onAdd)

    override suspend fun addOrUpdateSiteEntry(
        siteEntry: DecryptableSiteEntry,
        onAdd: suspend (DecryptableSiteEntry) -> Unit
    ) = DataModel.addOrUpdateSiteEntry(siteEntry, onAdd)

    override fun fetchSiteEntriesStateFlow(): StateFlow<List<DecryptableSiteEntry>> =
        DataModel.siteEntriesStateFlow

    override fun findCategoryByName(name: String): DecryptableCategoryEntry? =
        categoriesStateFlow.value.firstOrNull { it.plainName == name }

    override suspend fun loadFromDatabase() = DataModel.loadFromDatabase()

    override fun startEditPassword(context: Context, passwordId: DBID) =
        IntentManager.startEditPassword(context, passwordId)
}