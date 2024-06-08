package fi.iki.ede.safe.ui.composable

import android.util.Log
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.SiteEntrySearchScreen
import fi.iki.ede.safe.ui.activities.SiteEntrySearchScreen.Companion.searchProgressPerThread
import fi.iki.ede.safe.ui.testTag
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Search view at the top of the PasswordSearchScreen, intrinsic search controls
 */
@Composable
fun SearchSiteEntryControls(
    matchingPasswordEntries: MutableStateFlow<List<DecryptableSiteEntry>>,
    searchTextField: MutableState<TextFieldValue>,
) {
    val focusRequester = remember { FocusRequester() }
    val searchNotes = remember { mutableStateOf(false) }
    val searchPasswords = remember { mutableStateOf(false) }
    val searchUsernames = remember { mutableStateOf(false) }
    val searchWebsites = remember { mutableStateOf(false) }

    // just small gather up for use in UI code below
    fun findNow() {
        matchingPasswordEntries.value = emptyList()
        beginSearch(
            DataModel.getPasswords(), // ugly but too many recomposes
            matchingPasswordEntries,
            searchTextField.value,
            searchWebsites.value,
            searchUsernames.value,
            searchPasswords.value,
            searchNotes.value
        )
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
                    findNow()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTag.TEST_TAG_SEARCH_TEXT_FIELD)
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
                    IconButton(onClick = { searchTextField.value = TextFieldValue("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "",
                            modifier = iconPadding
                        )
                    }
                }
            },
            singleLine = true,
            shape = RectangleShape
        )
        Row {
            TextualCheckbox(searchWebsites, R.string.password_search_websites, ::findNow)
            TextualCheckbox(searchUsernames, R.string.password_search_usernames, ::findNow)
        }
        Row {
            TextualCheckbox(searchPasswords, R.string.password_search_passwords, ::findNow)
            TextualCheckbox(searchNotes, R.string.password_search_notes, ::findNow)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

private var delayedSearchJob: Job? = null
private const val TAG = "SearchPasswordAndControls"

@OptIn(DelicateCoroutinesApi::class)
fun beginSearch(
    allPasswordEntries: List<DecryptableSiteEntry>,
    matchingPasswordEntries: MutableStateFlow<List<DecryptableSiteEntry>>,
    searchTextField: TextFieldValue,
    searchWebsites: Boolean,
    searchUsernames: Boolean,
    searchPasswords: Boolean,
    searchNotes: Boolean
) {
    try {
        synchronized(allPasswordEntries) {
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
            if (searchTextField.text.isEmpty() || allPasswordEntries.isEmpty()) {
                matchingPasswordEntries.value = emptyList()
                return
            }

            // Recreate search progresses to the size of the CPUs
            val cpus =
                if (allPasswordEntries.size < SiteEntrySearchScreen.MIN_PASSWORDS_FOR_THREADED_SEARCH) {
                    1
                } else Runtime.getRuntime().availableProcessors()
            searchProgressPerThread.clear()
            searchProgressPerThread.addAll(List(cpus) { 0f })

            delayedSearchJob = GlobalScope.launch {
                val ourJob = coroutineContext[Job]!!
                // Also due to the fact searching encrypted data is intensive operation, add a delay
                // so we don't ACTUALLY start the search until user has stopped typing
                // half a second sounds good enough
                delay(500L)

                // HACKY! I'm guessing there's a completion handler or something like that
                // this is just to pass the CoroutineScope to filter function (also maybe some other way doing this)
                fun localIsActive(): Boolean {
                    return ourJob.isActive && !ourJob.isCancelled
                }

                // user might have already typed new search terms, and we might have been cancelled
                // so no point going doing any heavy lifting
                if (!ourJob.isCancelled) {
                    // TODO: PULL UP. no point chunking on every key stroke!!
                    // make sure password entry list is split to chunks of number of CPUs
                    // make sure the nor matter how non-even the CPU count is, all passwords are included
                    val chunkedPasswordEntries =
                        allPasswordEntries.chunked((allPasswordEntries.size + cpus) / cpus)
                    require(chunkedPasswordEntries.size <= cpus) { "Logic error too many chunks" }

                    val routines = mutableListOf<Deferred<Unit>>()
                    for ((chunkIndex, passwordEntryChunk) in chunkedPasswordEntries.withIndex()) {
                        routines.add(
                            async {
                                withContext(Dispatchers.Default) {
                                    asyncFilterChunkOfPasswords(
                                        chunkIndex,
                                        ::localIsActive,
                                        searchTextField.text,
                                        passwordEntryChunk,
                                        searchWebsites,
                                        searchUsernames,
                                        searchPasswords,
                                        searchNotes,
                                        matchingPasswordEntries
                                    )
                                }
                            })
                    }
                }
            }
        }
    } catch (ex: Exception) {
        Log.e(TAG, "Something went wrong on search:", ex)
    }
}

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

private fun asyncFilterChunkOfPasswords(
    chunk: Int,
    isActive: () -> Boolean,
    searchText: String,
    chunkOfPasswordEntries: List<DecryptableSiteEntry>,
    searchWebsites: Boolean,
    searchUsernames: Boolean,
    searchPasswords: Boolean,
    searchNotes: Boolean,
    matchingPasswordEntries: MutableStateFlow<List<DecryptableSiteEntry>>,
) {
    var searchedPasswordCount = 0.0f
    val amountOfPasswords = chunkOfPasswordEntries.size * 1.0f

    chunkOfPasswordEntries.forEach {
        if (isActive()) {
            searchedPasswordCount++
            skippingProgressUpdate(chunk, searchedPasswordCount / amountOfPasswords)

            if (it.contains(
                    searchText,
                    searchWebsites,
                    searchUsernames,
                    searchPasswords,
                    searchNotes
                )
            ) {
                matchingPasswordEntries.update { currentList -> currentList + it }
            }
        }
    }
}