package fi.iki.ede.safe.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry

@Composable
fun DeleteSiteEntryDialog(
    siteEntry: DecryptableSiteEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text(text = "Yes, delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(text = "Cancel")
            }
        },
        title = {
            Text(text = "Delete Password: ${siteEntry.cachedPlainDescription}?")
        },
        modifier = modifier
    )
}
