package fi.iki.ede.safe.ui.composable

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import fi.iki.ede.db.DBID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DebouncedSearchEntryFlowTest {
    private data class Entry(val id: DBID, val description: String)

    @Test
    fun queryStartedBeforeLoadSearchesAllArrivalsAfterDebounceAndStopsOnCancellation() = runBlocking {
        val entries = MutableStateFlow(mutableListOf<Entry>())
        val tracker = SearchEntrySnapshotTracker<Entry> { it.id }
        val searchedBatches = mutableListOf<PersistentList<DBID>>()
        val collector = launch {
            entries.debouncedSearchEntryChanges(tracker, persistentListOf(), debounceMillis = 100L)
                .collect { changes ->
                    searchedBatches += changes.changedEntries.map { it.id }.toPersistentList()
                }
        }

        val first = Entry(1, "ford")
        val second = Entry(2, "ford transit")
        entries.value = mutableListOf(first)
        delay(50L)
        entries.value = mutableListOf(first, second)
        delay(150L)

        assertEquals(
            persistentListOf(persistentListOf<DBID>(1L, 2L)),
            searchedBatches
        )

        val late = Entry(3, "ford explorer")
        entries.value = mutableListOf(first, second, late)
        delay(150L)
        assertEquals(
            persistentListOf(persistentListOf<DBID>(1L, 2L), persistentListOf<DBID>(3L)),
            searchedBatches
        )

        collector.cancelAndJoin()
        assertFalse(collector.isActive)
        entries.value = mutableListOf(first, second, late, Entry(4, "ford pickup"))
        delay(150L)
        assertEquals(2, searchedBatches.size)
    }
}
