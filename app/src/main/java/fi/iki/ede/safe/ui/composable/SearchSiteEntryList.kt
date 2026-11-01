package fi.iki.ede.safe.ui.composable

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import fi.iki.ede.crypto.keystore.MockKeyStoreHelper
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.datamodel.DataModel.getCategory
import fi.iki.ede.logger.Logger
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.theme.SafeThemeSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.time.ExperimentalTime

@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun SearchSiteEntryList(
    filteredSiteEntries: MutableStateFlow<PersistentList<DecryptableSiteEntry>>
) {
    val context = LocalContext.current
    val sortedPasswords by remember(filteredSiteEntries) {
        filteredSiteEntries
            .map { entries ->
                entries.sortedBy { it.plainDescription.lowercase() }.toPersistentList()
            }
            .flowOn(Dispatchers.Default)
    }.collectAsState(initial = persistentListOf())

    fun updateEntry(siteEntryToUpdate: DecryptableSiteEntry) {
        filteredSiteEntries.value = replaceSearchResultEntry(
            filteredSiteEntries.value,
            siteEntryToUpdate,
            keyOf = { it.id }
        )
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Handle the result here
                val resultIntent = result.data
                if (resultIntent != null) {
                    val passwordId =
                        resultIntent.getLongExtra(SiteEntryEditScreen.SITE_ENTRY_ID, -1L)
                    updateEntry(DataModel.getSiteEntry(passwordId))
                }
            }
        }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items = sortedPasswords, itemContent = { filteredItem ->
            // Merge with PasswordRow
            MatchingSiteEntry(
                siteEntry = filteredItem,
                categoryEntry = filteredItem.getCategory(), onSiteEntryClick = { siteEntry ->
                    val id = siteEntry.id
                    if (id != null) {
                        launcher.launch(
                            IntentManager.getEditSiteEntryIntent(context, id)
                        )
                    } else {
                        Logger.i("SearchSiteEntryList", "site entry is null")
                    }
                }, onDeleteSiteEntry = { deletedEntry ->
                    // it is gone already
                    val newList = filteredSiteEntries.value.filter { it != deletedEntry }
                        .toPersistentList()
                    filteredSiteEntries.value = newList
                }, onUpdateSiteEntry = { entryToUpdate ->
                    updateEntry(entryToUpdate)
                })
        })
    }
}

internal fun <T, K> replaceSearchResultEntry(
    current: PersistentList<T>,
    updated: T,
    keyOf: (T) -> K?
): PersistentList<T> {
    val updatedKey = keyOf(updated) ?: return current
    return current.map { if (keyOf(it) == updatedKey) updated else it }.toPersistentList()
}

@DualModePreview
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
private fun SearchSiteEntryListPreview() {
    SafeThemeSurface {
        MockKeyStoreHelper.init()
        val site1 = DecryptableSiteEntry(1).apply {
            description = "Description1".encrypt()
        }
        val site2 = DecryptableSiteEntry(1).apply {
            description = "Description2".encrypt()
        }
        DecryptableCategoryEntry().apply {
            id = 1
            encryptedName = "Category".encrypt()
        }
        val sitesFlow = MutableStateFlow(persistentListOf(site1, site2))
        // TODO: No mock..
        //DataModel._categories[cat] = lst
        SearchSiteEntryList(sitesFlow)
    }
}
