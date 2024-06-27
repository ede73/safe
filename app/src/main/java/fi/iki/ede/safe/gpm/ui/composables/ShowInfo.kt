package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.BuildConfig
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.changeset.ScoredMatch
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun ShowInfo(item: SavedGPM, onDismiss: () -> Unit) {
    val dump =
        "Name: ${item.cachedDecryptedName}\nUser: ${item.cachedDecryptedUsername}\nUrl: ${item.cachedDecryptedUrl}".let {
            if (BuildConfig.DEBUG) {
                it + "\nID=${item.id}\nPassword=${item.cachedDecryptedPassword}"
            } else it
        }
    UsageInfo(dump, onDismiss = onDismiss)
}

@Composable
fun ShowInfo(item: IncomingGPM, onDismiss: () -> Unit) {
    val dump = "Name: ${item.name}\nUser: ${item.username}\nUrl: ${item.url}".let {
        if (BuildConfig.DEBUG) {
            it + "\nnote=${item.note}\nPassword=${item.password}"
        } else it
    }
    UsageInfo(dump, onDismiss = onDismiss)
}

@Composable
fun ShowInfo(item: ScoredMatch, onDismiss: () -> Unit) {
    val dump =
        "Match:${item.matchScore}\nHashMatch:${item.hashMatch}\nName: ${item.item.cachedDecryptedName}\nUser: ${item.item.cachedDecryptedUsername}\nUrl: ${item.item.cachedDecryptedUrl}".let {
            if (BuildConfig.DEBUG) {
                it + "\nnote=${item.item.cachedDecryptedNote}\nPassword=${item.item.cachedDecryptedPassword}"
            } else it
        }
    UsageInfo(dump, onDismiss = onDismiss)
}

@Preview(showBackground = true)
@Composable
fun ShowInfoPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    val fakeSavedGPM =
        SavedGPM(0, IncomingGPM.makeFromCSVImport("name", "http://acme", "user", "pwd", "note"))
    SafeTheme {
        ShowInfo(fakeSavedGPM, {})
    }
}