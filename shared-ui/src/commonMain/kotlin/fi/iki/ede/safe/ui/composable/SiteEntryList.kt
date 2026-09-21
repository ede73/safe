package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fi.iki.ede.cryptoobjects.*

import androidx.compose.foundation.lazy.itemsIndexed

@Composable
fun SiteEntryList(
    siteEntries: List<DecryptableSiteEntry>,
    categoriesState: List<DecryptableCategoryEntry>,
    onSiteEntryClick: (DecryptableSiteEntry) -> Unit,
    onDeleteSiteEntry: (DecryptableSiteEntry) -> Unit,
    modifier: Modifier = Modifier,
    onMoveSiteEntry: ((DecryptableSiteEntry, DecryptableCategoryEntry) -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = siteEntries,
            key = { _, siteEntry -> siteEntry.id ?: siteEntry.hashCode().toLong() }
        ) { index, siteEntry ->
            val desc = siteEntry.plainDescription.ifBlank { " " }
            val beginning = desc.substring(0, 1).uppercase()
            val showHeader = index == 0 ||
                    siteEntries[index - 1].plainDescription.ifBlank { " " }
                        .substring(0, 1).uppercase() != beginning

            Box {
                if (showHeader) {
                    SiteEntryRowHeader(headerString = beginning)
                }
                SiteEntryRow(
                    siteEntry = siteEntry,
                    categoriesState = categoriesState,
                    onSiteEntryClick = onSiteEntryClick,
                    onDeleteSiteEntry = onDeleteSiteEntry,
                    onMoveSiteEntry = onMoveSiteEntry
                )
            }
        }
    }
}
