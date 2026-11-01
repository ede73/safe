package fi.iki.ede.safe.ui.composable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import fi.iki.ede.db.DBID

class SearchResultUpdateTest {
    private data class Entry(val id: DBID?, val description: String)

    @Test
    fun savedSearchResultIsReplacedWithCurrentOpenableInstance() {
        val original = Entry(42L, "Old title")
        val savedModelEntry = Entry(42L, "Updated title")

        val result = replaceSearchResultEntry(persistentListOf(original), savedModelEntry) { it.id }

        assertEquals(1, result.size)
        assertSame(savedModelEntry, result.single())
        assertEquals(42L, result.single().id)
    }

    @Test
    fun itemWithoutIdDoesNotReplaceAnOpenableResult() {
        val original = Entry(42L, "Original")
        val idlessCopy = Entry(null, "Updated")

        val result = replaceSearchResultEntry(persistentListOf(original), idlessCopy) { it.id }

        assertEquals(persistentListOf(original), result)
    }

    @Test
    fun editedEntryReplacesOldMatchAndCanBeRemovedWhenItNoLongerMatches() {
        val oldMatch = Entry(42L, "Ford")
        val updatedMatch = Entry(42L, "Ford Transit")

        val replacement = reconcileSearchResults(
            current = persistentListOf(oldMatch),
            changedEntries = persistentListOf(updatedMatch),
            removedKeys = persistentSetOf(),
            matchingChangedEntries = persistentListOf(updatedMatch),
            keyOf = { it.id }
        )
        assertEquals(1, replacement.size)
        assertSame(updatedMatch, replacement.single())

        val noLongerMatching = reconcileSearchResults(
            current = replacement,
            changedEntries = persistentListOf(Entry(42L, "Toyota")),
            removedKeys = persistentSetOf(),
            matchingChangedEntries = persistentListOf(),
            keyOf = { it.id }
        )
        assertEquals(persistentListOf<Entry>(), noLongerMatching)

        val deleted = reconcileSearchResults(
            current = replacement,
            changedEntries = persistentListOf(),
            removedKeys = persistentSetOf(42L),
            matchingChangedEntries = persistentListOf(),
            keyOf = { it.id }
        )
        assertEquals(persistentListOf<Entry>(), deleted)
    }
}
