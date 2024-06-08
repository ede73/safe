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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun SiteEntryList(passwords: List<DecryptableSiteEntry>) {

    val passwordItems = remember { mutableStateListOf<@Composable () -> Unit>() }
    val passwordListHash = remember(passwords) { passwords.hashCode() }
    val categoriesState by DataModel.categoriesStateFlow
        .collectAsState(initial = emptyList())

    LaunchedEffect(passwordListHash) {
        var previousValue = ""
        passwordItems.clear()
        passwords.forEach { password ->
            val beginning = password.plainDescription.substring(0, 1).uppercase()
            if (previousValue != beginning) {
                previousValue = beginning
                passwordItems.add { SiteEntryRowHeader(headerString = beginning) }
            }
            passwordItems.add { SiteEntryRow(password, categoriesState) }
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        passwordItems.forEach { composable ->
            composable()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SiteEntryListPreview() {
    SafeTheme {
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