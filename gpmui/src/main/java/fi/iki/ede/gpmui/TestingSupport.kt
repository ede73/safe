package fi.iki.ede.gpmui

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasTestTag
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
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
    override fun deleteAllSavedGPMs() {}
    override suspend fun loadFromDatabase() {}
    override fun markSavedGPMIgnored(id: DBID) {}
    override fun linkSaveGPMAndSiteEntry(siteEntry: DecryptableSiteEntry, gpmId: DBID) {}
    override suspend fun addOrEditCategory(
        category: DecryptableCategoryEntry,
        onAdd: suspend (DecryptableCategoryEntry) -> Unit
    ) {
    }

    override suspend fun addGpmAsSiteEntry(
        savedGpmId: DBID,
        categoryId: DBID,
        onAdd: suspend (DecryptableSiteEntry) -> Unit
    ) {
    }

    override fun finishGPMImport(
        delete: Set<SavedGPM>,
        update: Map<IncomingGPM, SavedGPM>,
        add: Set<IncomingGPM>
    ) {
    }

    override fun fetchSiteEntriesStateFlow(): StateFlow<List<DecryptableSiteEntry>> {
        TODO("Not yet implemented")
    }

    override fun fetchUnprocessedGPMsFlow(): StateFlow<Set<SavedGPM>> {
        TODO("Not yet implemented")
    }

    override fun fetchAllSavedGPMsFlow(): StateFlow<Set<SavedGPM>> {
        TODO("Not yet implemented")
    }

    override fun findCategoryByName(name: String): DecryptableCategoryEntry? {
        TODO("Not yet implemented")
    }

    override fun startEditPassword(context: Context, passwordId: DBID) {
        TODO("Not yet implemented")
    }

    override fun firebaseLog(message: String) {}
    override fun firebaseRecordException(message: String, ex: Throwable) {}
}