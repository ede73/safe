package fi.iki.ede.safe.ui.composable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import fi.iki.ede.db.DBID

class SearchEntrySnapshotTrackerTest {
    private data class Entry(val id: DBID, val name: String)

    @Test
    fun queryStartedBeforeLoadingFindsEntryWhenItArrives() {
        val tracker = SearchEntrySnapshotTracker<Entry> { it.id }
        tracker.seed(persistentListOf())
        val activeQuery = "ford"
        val loaded = Entry(1, "Ford")
        val changes = tracker.update(persistentListOf(loaded))
        val searched = changes.changedEntries.filter {
            it.name.contains(activeQuery, ignoreCase = true)
        }

        assertEquals(persistentListOf(loaded), searched)
    }

    @Test
    fun successiveBatchesOnlySearchNewEntries() {
        val tracker = SearchEntrySnapshotTracker<Entry> { it.id }
        val first = Entry(1, "Ford")
        val second = Entry(2, "Volvo")
        val third = Entry(3, "Ford Transit")
        val searchedIds = mutableListOf<DBID>()

        tracker.seed(persistentListOf(first))
        tracker.update(persistentListOf(first, second)).changedEntries.forEach { searchedIds += it.id }
        tracker.update(persistentListOf(first, second, third)).changedEntries.forEach { searchedIds += it.id }

        assertEquals(mutableListOf<DBID>(2L, 3L), searchedIds)
    }

    @Test
    fun appendAndSameIdReplacementInOneSnapshotAreBothReported() {
        val tracker = SearchEntrySnapshotTracker<Entry> { it.id }
        val beforeEdit = Entry(7, "Ford")
        val afterEdit = Entry(7, "Toyota")
        val appended = Entry(8, "Volvo")
        tracker.seed(persistentListOf(beforeEdit))

        val changes = tracker.update(persistentListOf(afterEdit, appended))

        assertEquals(persistentListOf(afterEdit, appended), changes.changedEntries)
    }

    @Test
    fun replacementAndDeletionAreReportedForReconciliation() {
        val tracker = SearchEntrySnapshotTracker<Entry> { it.id }
        val beforeEdit = Entry(7, "Ford")
        val afterEdit = Entry(7, "Toyota")
        tracker.seed(persistentListOf(beforeEdit))

        val replacement = tracker.update(persistentListOf(afterEdit))
        assertEquals(persistentListOf(afterEdit), replacement.changedEntries)
        assertEquals(persistentSetOf<DBID>(), replacement.removedIds)

        val deletion = tracker.update(persistentListOf())
        assertEquals(persistentListOf<Entry>(), deletion.changedEntries)
        assertEquals(persistentSetOf(7L), deletion.removedIds)
    }
}
