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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.activities.PasswordSearchScreen
import fi.iki.ede.safe.ui.activities.getSearchThreadCount
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search view at the top of the PasswordSearchScreen, intrinsic search controls
 */
@Composable
fun SearchPasswordAndControls(
    filteredPasswords: MutableStateFlow<List<DecryptablePasswordEntry>>,
    searchText: MutableState<TextFieldValue>,
) {
    val focusRequester = remember { FocusRequester() }
    val passwordEntries = DataModel.getPasswords()
    var searchWebsites by remember { mutableStateOf(false) }
    var searchUsernames by remember { mutableStateOf(false) }
    var searchPasswords by remember { mutableStateOf(false) }
    var searchNotes by remember { mutableStateOf(false) }
    val pad = Modifier
        .padding(15.dp)
        .size(24.dp)

    fun findNow() {
        filteredPasswords.value = emptyList()
        beginSearch(
            passwordEntries,
            filteredPasswords,
            searchText.value,
            searchWebsites,
            searchUsernames,
            searchPasswords,
            searchNotes
        )
    }

    // Even focus changes, keyboard hidden etc.
    // TextField will invoke onValueChange()
    // We ONLY want to do something if actual search term, so alas need this
    var hackToInvokeSearchOnlyIfTextValueChanges by remember { mutableStateOf(TextFieldValue("")) }
    Column {
        TextField(
            value = searchText.value,
            onValueChange = { value ->
                searchText.value = value
                if (value.text != hackToInvokeSearchOnlyIfTextValueChanges.text) {
                    hackToInvokeSearchOnlyIfTextValueChanges = value
                    findNow()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PasswordSearchScreen.TESTTAG_SEARCH_TEXTFIELD)
                .focusRequester(focusRequester),
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "",
                    modifier = pad
                )
            },
            placeholder = { Text(stringResource(id = R.string.password_search_search_hint)) },
            trailingIcon = {
                if (searchText.value != TextFieldValue("")) {
                    IconButton(onClick = { searchText.value = TextFieldValue("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "",
                            modifier = pad
                        )
                    }
                }
            },
            singleLine = true,
            shape = RectangleShape
        )
        Row {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = searchWebsites, onCheckedChange = {
                    searchWebsites = it
                    findNow()
                })
                Text(text = stringResource(id = R.string.password_search_websites))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = searchUsernames, onCheckedChange = {
                    searchUsernames = it
                    findNow()
                })
                Text(text = stringResource(id = R.string.password_search_usernames))
            }
        }
        Row {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = searchPasswords, onCheckedChange = {
                    searchPasswords = it
                    findNow()
                })
                Text(text = stringResource(id = R.string.password_search_passwords))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = searchNotes, onCheckedChange = {
                    searchNotes = it
                    findNow()
                })
                Text(text = stringResource(id = R.string.password_search_notes))
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}


private var delayedSearchJob: Job? = null


@OptIn(DelicateCoroutinesApi::class)
fun beginSearch(
    passwordEntries: List<DecryptablePasswordEntry>,
    filteredPasswords: MutableStateFlow<List<DecryptablePasswordEntry>>,
    searchText: TextFieldValue,
    searchWebsites: Boolean,
    searchUsernames: Boolean,
    searchPasswords: Boolean,
    searchNotes: Boolean
) {
    try {
        synchronized(passwordEntries) {
            // IF (and only if) new search term starts with old search term, we could
            // optimize the search by searching the only what was previously found and unsearched entries
            // E.g. user types "a", search begins, user types "b", we could start new search for "ab"
            // but then again we might have searched already 25% of password entries with "a" in it
            // or in other words excluded lot of entries that don't contain "a", hence they cannot
            // contain "ab" either
            // Also since everything is kept encrypted (and only way to search is descrypt) - search is
            // intensive operation
            delayedSearchJob?.cancel()
            for (i in 0 until getSearchThreadCount()) {
                PasswordSearchScreen.searchProgresses[i] = 0.0f
            }
            delayedSearchJob = GlobalScope.launch {
                // Also due to the fact searching encrypted data is intensive operation, add a delay
                // so we don't ACTUALLY start the search until user has stopped typing
                // half a second sounds good enough
                delay(500L)
                // HACKY! I'm guessing there's a completion handler or something like that
                // this is just to pass the CoroutineScope to filter function (also maybe some other way doing this)
                fun localIsActive() = isActive
                if (searchText.text.isEmpty() || passwordEntries.isEmpty()) {
                    for (i in 0 until getSearchThreadCount()) {
                        PasswordSearchScreen.searchProgresses[i] = 100.0f
                    }
                    // updates must be done in main thread
                    withContext(Dispatchers.Main) {
                        filteredPasswords.value = passwordEntries
                    }
                } else {
                    val cpus =
                        if (passwordEntries.size < PasswordSearchScreen.MIN_PASSWORDS_FOR_THREADED_SEARCH) {
                            1
                        } else Runtime.getRuntime().availableProcessors()
                    // make sure password entry list is split to chunks of number of CPUs
                    // make sure the nor matter how non-even the CPU count is, all passwords are included
                    val passwordEntryChunks =
                        passwordEntries.chunked((passwordEntries.size + cpus) / cpus)
                    val routines = mutableListOf<Deferred<Unit>>()
                    for ((chunkIndex, passwordEntryChunk) in passwordEntryChunks.withIndex()) {
                        routines.add(
                            async {
                                withContext(Dispatchers.Main) {
                                    filterPasswords(
                                        chunkIndex,
                                        ::localIsActive,
                                        searchText.text,
                                        passwordEntryChunk,
                                        searchWebsites,
                                        searchUsernames,
                                        searchPasswords,
                                        searchNotes,
                                        filteredPasswords
                                    )
                                }
                            })
                    }
                    routines.awaitAll()
                }
            }
        }
    } catch (ex: Exception) {
        Log.e("PasswordSearchScreen", "Something went wrong on search:", ex)
    }
}

private fun filterPasswords(
    chunk: Int,
    isActive: () -> Boolean,
    searchText: String,
    passwordEntries: List<DecryptablePasswordEntry>,
    searchWebsites: Boolean,
    searchUsernames: Boolean,
    searchPasswords: Boolean,
    searchNotes: Boolean,
    filteredPasswords: MutableStateFlow<List<DecryptablePasswordEntry>>,
) {
    var searchedPasswordCount = 0.0f
    val amountOfPasswords = passwordEntries.size * 1.0f

    passwordEntries.forEach {
        if (isActive()) {
            searchedPasswordCount++
            PasswordSearchScreen.searchProgresses[chunk] = searchedPasswordCount / amountOfPasswords
            if (it.contains(
                    searchText,
                    searchWebsites,
                    searchUsernames,
                    searchPasswords,
                    searchNotes
                )
            ) {
                filteredPasswords.update { currentList -> currentList + it }
            }
        }
    }
}