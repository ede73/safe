package fi.iki.ede.safe.ui.composable

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
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.theme.SafeThemeSurface

@Composable
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
fun SiteEntryListPreview() {
    SafeThemeSurface {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val lst = mutableListOf(DecryptableSiteEntry(1).apply {
            description = encrypter("Description1".toByteArray())
        }, DecryptableSiteEntry(1).apply {
            description = encrypter("Description2".toByteArray())
        }, DecryptableSiteEntry(1).apply {
            description = encrypter("Kuvaus".toByteArray())
        })
        SiteEntryList(lst)
    }
}
