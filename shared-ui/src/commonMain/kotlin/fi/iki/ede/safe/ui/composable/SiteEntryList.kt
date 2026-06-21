package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry

@Composable
fun SiteEntryList(
    siteEntries: List<DecryptableSiteEntry>,
    categoriesState: List<DecryptableCategoryEntry>,
    onSiteEntryClick: (DecryptableSiteEntry) -> Unit,
    onDeleteSiteEntry: (DecryptableSiteEntry) -> Unit,
    modifier: Modifier = Modifier,
    onMoveSiteEntry: ((DecryptableSiteEntry, DecryptableCategoryEntry) -> Unit)? = null
) {
    var previousValue = ""

    Column(
        modifier = modifier
            .padding(0.dp)
            .verticalScroll(rememberScrollState())
    ) {
        siteEntries.forEach { siteEntry ->
            val beginning = siteEntry.cachedPlainDescription.substring(0, 1).uppercase()
            Box {
                if (previousValue != beginning) {
                    previousValue = beginning
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
