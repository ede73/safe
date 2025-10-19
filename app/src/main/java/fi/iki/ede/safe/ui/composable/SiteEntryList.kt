package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.theme.SafeThemeSurface

@Composable
fun SiteEntryList(siteEntries: List<DecryptableSiteEntry>) {

    val siteEntryItems = remember { mutableStateListOf<@Composable () -> Unit>() }
    val siteEntryListHash = remember(siteEntries) { siteEntries.hashCode() }
    val categoriesState by DataModel.categoriesStateFlow
        .collectAsState(initial = emptyList())

    LaunchedEffect(siteEntryListHash) {
        var previousValue = ""
        siteEntryItems.clear()
        siteEntries.forEach { siteEntry ->
            val beginning = siteEntry.cachedPlainDescription.substring(0, 1).uppercase()
            if (previousValue != beginning) {
                previousValue = beginning
                siteEntryItems.add { SiteEntryRowHeader(headerString = beginning) }
            }
            siteEntryItems.add { SiteEntryRow(siteEntry, categoriesState) }
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        siteEntryItems.forEach { composable ->
            composable()
        }
    }
}

@DualModePreview
@Composable
fun SiteEntryListPreview() {
    SafeThemeSurface {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val site1 = DecryptableSiteEntry(1).apply {
            description = encrypter("Description1".toByteArray())
        }
        val site2 = DecryptableSiteEntry(1).apply {
            description = encrypter("Description2".toByteArray())
        }
        val lst = mutableListOf(site1, site2)
        SiteEntryList(lst)
    }
}