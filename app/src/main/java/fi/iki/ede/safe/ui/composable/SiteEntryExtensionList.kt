package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SiteEntryExtensionList(
    viewModel: EditingSiteEntryViewModel,
) {
    // TODO: NONO..flow!
    val allExtensions = produceState<Map<String, Set<String>>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            DataModel.getAllSiteEntryExtensions()
        }
    }.value

    VerticalCollapsible(stringResource(id = R.string.site_entry_extension_collapsible)) {
        Preferences.getAllExtensions().sortedBy { it }.forEach {
            Column {
                SiteEntryExtensionSelector(
                    viewModel,
                    allExtensions?.getOrDefault(it, emptySet()) ?: emptySet(),
                    it
                )
            }
        }
    }
}