package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.keystore.MockKeyStoreHelper
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.theme.SafeThemeSurface
import kotlin.time.ExperimentalTime

@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun SiteEntryList(siteEntries: List<DecryptableSiteEntry>) {
    val categoriesState by DataModel.categoriesStateFlow.collectAsState(initial = emptyList())
    var previousValue = ""

    Column(
        modifier = Modifier
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
                SiteEntryRow(siteEntry, categoriesState)
            }
        }
    }
}

@DualModePreview
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
private fun SiteEntryListPreview() {
    SafeThemeSurface {
        MockKeyStoreHelper.init()
        val lst = mutableListOf(DecryptableSiteEntry(1).apply {
            description = "Description1".encrypt()
        }, DecryptableSiteEntry(1).apply {
            description = "Description2".encrypt()
        }, DecryptableSiteEntry(1).apply {
            description = "Kuvaus".encrypt()
        })
        SiteEntryList(lst)
    }
}
