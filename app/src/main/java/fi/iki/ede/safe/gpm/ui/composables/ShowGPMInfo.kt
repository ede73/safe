package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.BuildConfig
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun ShowGPMInfo(item: SavedGPM, onDismiss: () -> Unit) {
    val dump =
        "Name: ${item.cachedDecryptedName}\nUser: ${item.cachedDecryptedUsername}\nUrl: ${item.cachedDecryptedUrl}".let {
            if (BuildConfig.DEBUG) {
                it + "\nID=${item.id}"
            } else it
        }
    UsageInfo(dump, onDismiss = onDismiss)
}

@Preview(showBackground = true)
@Composable
fun ShowGPMInfoPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    val fakeSavedGPM =
        SavedGPM(0, IncomingGPM.makeFromCSVImport("name", "http://acme", "user", "pwd", "note"))
    SafeTheme {
        ShowGPMInfo(fakeSavedGPM, {})
    }
}