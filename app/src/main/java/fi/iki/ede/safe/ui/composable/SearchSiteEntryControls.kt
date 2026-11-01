package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.keystore.MockKeyStoreHelper
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.db.DBID
import fi.iki.ede.logger.Logger
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.SiteEntrySearchScreen
import fi.iki.ede.safe.ui.activities.SiteEntrySearchScreen.Companion.searchProgressPerThread
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.SafeThemeSurface
import fi.iki.ede.theme.TextualCheckbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlin.math.abs
import kotlin.time.ExperimentalTime
import java.util.concurrent.atomic.AtomicLong

/**
 * Search view at the top of the PasswordSearchScreen, intrinsic search controls
 */
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun SearchSiteEntryControls(
    matchingSiteEntries: MutableStateFlow<PersistentList<DecryptableSiteEntry>>,
    searchTextField: MutableState<TextFieldValue>,
) {
    val coroutineScope = rememberCoroutineScope()
    var activeSearchCollectionJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }
    val searchExtensions = remember { mutableStateOf(false) }
    val searchNotes = remember { mutableStateOf(false) }
    val searchPasswords = remember { mutableStateOf(false) }
    val searchUsernames = remember { mutableStateOf(false) }
    val searchWebsites = remember { mutableStateOf(false) }

    // just small gather up for use in UI code below
    fun findNow(checked: Boolean) {
        val generation = activeSearchGeneration.incrementAndGet()
        matchingSiteEntries.value = persistentListOf()
        activeSearchCollectionJob?.cancel()
        val entriesAtSearchStart = DataModel.siteEntriesStateFlow.value.toPersistentList()
        beginSearch(
            coroutineScope,
            entriesAtSearchStart,
            matchingSiteEntries,
            searchTextField.value,
            searchWebsites.value,
            searchUsernames.value,
            searchPasswords.value,
            searchNotes.value,
            searchExtensions.value,
            generation
        )

        val query = searchTextField.value.text.trim()
        if (query.isBlank()) return

        val tracker = SearchEntrySnapshotTracker<DecryptableSiteEntry> { it.id }
        val initialSearchJob = delayedSearchJob
        activeSearchCollectionJob = coroutineScope.launch {
            DataModel.siteEntriesStateFlow
                .debouncedSearchEntryChanges(tracker, entriesAtSearchStart)
                .collect { changes ->
                    searchNewEntriesInChunks(
                        changes,
                        query,
                        searchWebsites.value,
                        searchUsernames.value,
                        searchPasswords.value,
                        searchNotes.value,
                        searchExtensions.value,
                        matchingSiteEntries,
                        initialSearchJob,
                        generation
                    )
                }
        }
    }

    // Even focus changes, keyboard hidden etc.
    // TextField will invoke onValueChange()
    // We ONLY want to do something if actual search term, so alas need this
    var hackToInvokeSearchOnlyIfTextValueChanges by remember { mutableStateOf(TextFieldValue("")) }
    Column {
        val iconPadding = Modifier
            .padding(15.dp)
            .size(24.dp)
        TextField(
            value = searchTextField.value,
            onValueChange = { value ->
                searchTextField.value = value
                if (value.text != hackToInvokeSearchOnlyIfTextValueChanges.text) {
                    hackToInvokeSearchOnlyIfTextValueChanges = value
                    findNow(true)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTag.SEARCH_TEXT_FIELD)
                .focusRequester(focusRequester),
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "",
                    modifier = iconPadding
                )
            },
            placeholder = { Text(stringResource(id = R.string.password_search_search_hint)) },
            trailingIcon = {
                if (searchTextField.value != TextFieldValue("")) {
                    IconButton(onClick = {
                        val clearedValue = TextFieldValue("")
                        searchTextField.value = clearedValue
                        hackToInvokeSearchOnlyIfTextValueChanges = clearedValue
                        findNow(true)
                    }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "",
                            modifier = iconPadding.testTag(TestTag.SEARCH_CLEAR_BUTTON)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RectangleShape
        )
        Row {
            TextualCheckbox(
                searchWebsites,
                R.string.password_search_websites,
                checkedChanged = ::findNow
            )
            TextualCheckbox(
                searchUsernames,
                R.string.password_search_usernames,
                checkedChanged = ::findNow
            )
        }
        Row {
            TextualCheckbox(
                searchPasswords,
                R.string.password_search_passwords,
                checkedChanged = ::findNow
            )
            TextualCheckbox(searchNotes, R.string.password_search_notes, checkedChanged = ::findNow)
            TextualCheckbox(
                searchExtensions,
                R.string.password_search_extensions,
                checkedChanged = ::findNow
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
internal fun <T> Flow<List<T>>.debouncedSearchEntryChanges(
    tracker: SearchEntrySnapshotTracker<T>,
    initialEntries: PersistentList<T>,
    debounceMillis: Long = 500L
): Flow<SearchEntryChanges<T>> {
    tracker.seed(initialEntries)
    return map { it.toPersistentList() }
        .debounce(debounceMillis)
        .map(tracker::update)
        .filter { it.changedEntries.isNotEmpty() || it.removedIds.isNotEmpty() }
}

/** Search only the entries added or replaced since the active query began. */
@ExperimentalTime
@ExperimentalFoundationApi
private suspend fun searchNewEntriesInChunks(
    changes: SearchEntryChanges<DecryptableSiteEntry>,
    searchText: String,
    searchWebsites: Boolean,
    searchUsernames: Boolean,
    searchPasswords: Boolean,
    searchNotes: Boolean,
    searchExtensions: Boolean,
    matchingSiteEntries: MutableStateFlow<PersistentList<DecryptableSiteEntry>>,
    initialSearchJob: Job?,
    generation: Long
) = kotlinx.coroutines.coroutineScope {
    initialSearchJob?.join()
    if (!coroutineContext[Job]!!.isActive || activeSearchGeneration.get() != generation) {
        return@coroutineScope
    }

    val newEntries = changes.changedEntries
    val batchMatches = MutableStateFlow(persistentListOf<DecryptableSiteEntry>())

    if (newEntries.isNotEmpty()) {
        val cpus =
            if (newEntries.size < SiteEntrySearchScreen.MIN_PASSWORDS_FOR_THREADED_SEARCH) {
                1
            } else Runtime.getRuntime().availableProcessors()
        val chunkSize = (newEntries.size + cpus) / cpus
        val chunkedSiteEntries = mutableListOf<PersistentList<DecryptableSiteEntry>>()
        newEntries.chunked(chunkSize).forEach { chunkedSiteEntries += it.toPersistentList() }
        require(chunkedSiteEntries.size <= cpus) { "Logic error too many chunks" }

        val parentJob = coroutineContext[Job]!!
        searchProgressPerThread.clear()
        searchProgressPerThread.addAll(MutableList(cpus) { 0f })
        val routines = mutableListOf<Deferred<Unit>>()
        for ((chunkIndex, siteEntryChunk) in chunkedSiteEntries.withIndex()) {
            routines.add(
                async {
                    withContext(Dispatchers.Default) {
                        asyncFilterChunkOfSiteEntries(
                            chunkIndex,
                            parentJob::isActive,
                            searchText,
                            siteEntryChunk,
                            searchWebsites,
                            searchUsernames,
                            searchPasswords,
                            searchNotes,
                            searchExtensions,
                            batchMatches,
                            generation
                        )
                    }
                }
            )
        }
        routines.forEach { it.await() }
    }
    matchingSiteEntries.update { current ->
        reconcileSearchResults(
            current,
            changes.changedEntries,
            changes.removedIds,
            batchMatches.value,
            keyOf = { it.id }
        ).takeIf { activeSearchGeneration.get() == generation } ?: current
    }
}

internal fun <T, K> reconcileSearchResults(
    current: PersistentList<T>,
    changedEntries: PersistentList<T>,
    removedKeys: PersistentSet<K>,
    matchingChangedEntries: PersistentList<T>,
    keyOf: (T) -> K?
): PersistentList<T> {
    val affectedKeys = changedEntries.mapNotNull(keyOf).toPersistentSet() + removedKeys
    return current.filterNot { keyOf(it) in affectedKeys }
        .toPersistentList()
        .addingAll(matchingChangedEntries)
}

internal data class SearchEntryChanges<T>(
    val changedEntries: PersistentList<T>,
    val removedIds: PersistentSet<DBID>
)

/** Diffs snapshots without re-running the encrypted search for unchanged entries. */
internal class SearchEntrySnapshotTracker<T>(private val idOf: (T) -> DBID?) {
    private var entriesById: MutableMap<DBID, T> = mutableMapOf()
    private var entriesWithoutId = persistentListOf<T>()

    fun seed(snapshot: PersistentList<T>) {
        val entriesWithIds = mutableMapOf<DBID, T>()
        val noIdEntries = mutableListOf<T>()
        snapshot.forEach { item ->
            val id = idOf(item)
            if (id == null) noIdEntries += item else entriesWithIds[id] = item
        }
        entriesById = entriesWithIds
        entriesWithoutId = noIdEntries.toPersistentList()
    }

    fun update(snapshot: PersistentList<T>): SearchEntryChanges<T> {
        val nextEntriesById = mutableMapOf<DBID, T>()
        val nextEntriesWithoutId = mutableListOf<T>()
        val changed = mutableListOf<T>()
        snapshot.forEach { item ->
            val id = idOf(item)
            if (id == null) {
                if (entriesWithoutId.none { it === item }) changed += item
                nextEntriesWithoutId += item
            } else {
                if (entriesById[id] !== item) changed += item
                nextEntriesById[id] = item
            }
        }
        val removed = entriesById.keys - nextEntriesById.keys
        entriesById = nextEntriesById
        entriesWithoutId = nextEntriesWithoutId.toPersistentList()
        return SearchEntryChanges(changed.toPersistentList(), removed.toPersistentSet())
    }
}

private var delayedSearchJob: Job? = null
private const val TAG = "SearchPasswordAndControls"
private val activeSearchGeneration = AtomicLong(0L)

@ExperimentalTime
@ExperimentalFoundationApi
fun beginSearch(
    coroutineScope: CoroutineScope,
    allSiteEntries: PersistentList<DecryptableSiteEntry>,
    matchingSiteEntries: MutableStateFlow<PersistentList<DecryptableSiteEntry>>,
    searchTextField: TextFieldValue,
    searchWebsites: Boolean,
    searchUsernames: Boolean,
    searchPasswords: Boolean,
    searchNotes: Boolean,
    searchExtensions: Boolean,
    generation: Long
) {
    try {
        synchronized(allSiteEntries) {
            // IF (and only if) new search term starts with old search term, we could
            // optimize the search by searching the only what was previously found and unsearched entries
            // E.g. user types "a", search begins, user types "b", we could start new search for "ab"
            // but then again we might have searched already 25% of password entries with "a" in it
            // or in other words excluded lot of entries that don't contain "a", hence they cannot
            // contain "ab" either
            // Also since everything is kept encrypted (and only way to search is descrypt) - search is
            // intensive operation
            if (delayedSearchJob?.isActive == true) {
                delayedSearchJob?.cancel()
            }
            searchProgressPerThread.indices.forEach { index ->
                searchProgressPerThread[index] = 100.0f
            }

            // no point searching for something we will never find
            if (searchTextField.text.isEmpty() || allSiteEntries.isEmpty()) {
                matchingSiteEntries.value = persistentListOf()
                return
            }

            // Recreate search progresses to the size of the CPUs
            val cpus =
                if (allSiteEntries.size < SiteEntrySearchScreen.MIN_PASSWORDS_FOR_THREADED_SEARCH) {
                    1
                } else Runtime.getRuntime().availableProcessors()
            searchProgressPerThread.clear()
            searchProgressPerThread.addAll(List(cpus) { 0f })

            delayedSearchJob = coroutineScope.launch {
                val ourJob = coroutineContext[Job]!!
                // Also due to the fact searching encrypted data is intensive operation, add a delay
                // so we don't ACTUALLY start the search until user has stopped typing
                // half a second sounds good enough
                delay(500L)

                // HACKY! I'm guessing there's a completion handler or something like that
                // this is just to pass the CoroutineScope to filter function (also maybe some other way doing this)
                fun localIsActive(): Boolean {
                    return ourJob.isActive && !ourJob.isCancelled &&
                            activeSearchGeneration.get() == generation
                }

                // user might have already typed new search terms, and we might have been cancelled
                // so no point going doing any heavy lifting
                if (!ourJob.isCancelled) {
                    // TODO: PULL UP. no point chunking on every key stroke!!
                    // make sure password entry list is split to chunks of number of CPUs
                    // make sure the nor matter how non-even the CPU count is, all passwords are included
                    val chunkSize = (allSiteEntries.size + cpus) / cpus
                    val chunkedSiteEntries = mutableListOf<PersistentList<DecryptableSiteEntry>>()
                    allSiteEntries.chunked(chunkSize)
                        .forEach { chunkedSiteEntries += it.toPersistentList() }
                    require(chunkedSiteEntries.size <= cpus) { "Logic error too many chunks" }

                    val routines = mutableListOf<Deferred<Unit>>()
                    for ((chunkIndex, siteEntryChunk) in chunkedSiteEntries.withIndex()) {
                        routines.add(
                            async {
                                withContext(Dispatchers.Default) {
                                    asyncFilterChunkOfSiteEntries(
                                        chunkIndex,
                                        ::localIsActive,
                                        searchTextField.text.trim(),
                                        siteEntryChunk,
                                        searchWebsites,
                                        searchUsernames,
                                        searchPasswords,
                                        searchNotes,
                                        searchExtensions,
                                        matchingSiteEntries,
                                        generation
                                    )
                                }
                            })
                    }
                }
            }
        }
    } catch (ex: Exception) {
        Logger.e(TAG, "Something went wrong on search:", ex)
    }
}

@ExperimentalTime
@ExperimentalFoundationApi
private fun skippingProgressUpdate(threadIndex: Int, progress: Float) {
    if (threadIndex < searchProgressPerThread.size) {
        val changed =
            (searchProgressPerThread[threadIndex] * 100).toInt() - (progress * 100).toInt()
        // Save UI, only update if there's a big change
        if (abs(changed) > 2) {
            searchProgressPerThread[threadIndex] = progress
        }
    }
}

@ExperimentalTime
@ExperimentalFoundationApi
private fun asyncFilterChunkOfSiteEntries(
    chunk: Int,
    isActive: () -> Boolean,
    searchText: String,
    chunkOfSiteEntries: PersistentList<DecryptableSiteEntry>,
    searchWebsites: Boolean,
    searchUsernames: Boolean,
    searchPasswords: Boolean,
    searchNotes: Boolean,
    searchExtensions: Boolean,
    matchingSiteEntries: MutableStateFlow<PersistentList<DecryptableSiteEntry>>,
    generation: Long,
) {
    var searchedSiteEntryCount = 0.0f
    val amountOfSiteEntries = chunkOfSiteEntries.size * 1.0f

    chunkOfSiteEntries.forEach {
        if (isActive()) {
            searchedSiteEntryCount++
            skippingProgressUpdate(chunk, searchedSiteEntryCount / amountOfSiteEntries)

            if (it.contains(
                    searchText,
                    searchWebsites,
                    searchUsernames,
                    searchPasswords,
                    searchNotes,
                    searchExtensions,
                )
                && isActive()) {
                matchingSiteEntries.update { currentList ->
                    if (isActive() && activeSearchGeneration.get() == generation) {
                        currentList.adding(it)
                    } else currentList
                }
            }
        }
    }
}


@DualModePreview
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
private fun SearchSiteEntryPreview() {
    SafeThemeSurface {
        MockKeyStoreHelper.init()
        val site = DecryptableSiteEntry(1).apply {
            description = "Description".encrypt()
        }
        val search = remember { mutableStateOf(TextFieldValue()) }
        val stateFlow = MutableStateFlow(persistentListOf(site, site, site))
        SearchSiteEntryControls(stateFlow, search)
    }
}
