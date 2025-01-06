package fi.iki.ede.gpmui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasTestTag
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBID
import kotlinx.coroutines.flow.StateFlow

fun Modifier.testTag(tag: TestTag) = semantics(
    properties = {
        // Make sure we don't leak stuff to production
        if (BuildConfig.DEBUG) {
            testTag = tag.name
        }
    }
)

fun SemanticsNodeInteractionsProvider.onAllNodesWithTag(
    testTag: TestTag,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteractionCollection = onAllNodes(hasTestTag(testTag.name), useUnmergedTree)

fun SemanticsNodeInteractionsProvider.onNodeWithTag(
    testTag: TestTag,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction = onNode(hasTestTag(testTag.name), useUnmergedTree)

fun getFakeDataModel() = object : DataModelIF {
    override suspend fun loadFromDatabase() {}
    override suspend fun addOrEditCategory(
        category: DecryptableCategoryEntry,
        onAdd: suspend (DecryptableCategoryEntry) -> Unit
    ) {
    }

    override suspend fun addOrUpdateSiteEntry(
        siteEntry: DecryptableSiteEntry,
        onAdd: suspend (DecryptableSiteEntry) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun fetchSiteEntriesStateFlow(): StateFlow<List<DecryptableSiteEntry>> {
        TODO("Not yet implemented")
    }

    override fun findCategoryByName(name: String): DecryptableCategoryEntry? {
        TODO("Not yet implemented")
    }

    override fun startEditPassword(context: Context, passwordId: DBID) {
        TODO("Not yet implemented")
    }

    override fun getReadableDatabase(): SQLiteDatabase {
        TODO("Not yet implemented")
    }

    override fun getWritableDatabase(): SQLiteDatabase {
        TODO("Not yet implemented")
    }

    override fun firebaseLog(message: String) {}
    override fun firebaseRecordException(message: String, ex: Throwable) {}
}