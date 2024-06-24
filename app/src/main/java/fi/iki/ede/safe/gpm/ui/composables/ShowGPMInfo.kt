package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.runtime.Composable
import fi.iki.ede.crypto.BuildConfig
import fi.iki.ede.gpm.model.SavedGPM

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