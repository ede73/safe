package fi.iki.ede.gpmui.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.changeset.ScoredMatch
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpmui.BuildConfig

@Composable
fun ShowInfoDialog(item: SavedGPM, onDismiss: () -> Unit) {
    val dump =
        "Name: ${item.cachedDecryptedName}\nUser: ${item.cachedDecryptedUsername}\nUrl: ${item.cachedDecryptedUrl}".let {
            if (BuildConfig.DEBUG) {
                it + "\nID=${item.id}\nPassword=${item.cachedDecryptedPassword}"
            } else it
        }
    UsageInfoDialog(dump, onDismiss = onDismiss)
}

@Composable
fun ShowInfoDialog(item: IncomingGPM, onDismiss: () -> Unit) {
    val dump = "Name: ${item.name}\nUser: ${item.username}\nUrl: ${item.url}".let {
        if (BuildConfig.DEBUG) {
            it + "\nNote: ${item.note}\nPassword: ${item.password}"
        } else it
    }
    UsageInfoDialog(dump, onDismiss = onDismiss)
}

@Composable
fun ShowInfoDialog(item: ScoredMatch, onDismiss: () -> Unit) {
    val dump =
        "Match: ${item.matchScore}\nHashMatch: ${item.hashMatch}\nName: ${item.item.cachedDecryptedName}\nUser: ${item.item.cachedDecryptedUsername}\nUrl: ${item.item.cachedDecryptedUrl}".let {
            if (BuildConfig.DEBUG) {
                it + "\nNote: ${item.item.cachedDecryptedNote}\nPassword: ${item.item.cachedDecryptedPassword}"
            } else it
        }
    UsageInfoDialog(dump, onDismiss = onDismiss)
}

@Preview(showBackground = true)
@Composable
fun ShowInfoPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    val fakeSavedGPM =
        SavedGPM(0, IncomingGPM.makeFromCSVImport("name", "http://acme", "user", "pwd", "note"))
    MaterialTheme {
        ShowInfoDialog(fakeSavedGPM) {}
    }
}