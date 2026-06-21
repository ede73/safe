package fi.iki.ede.safe.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry

import fi.iki.ede.safe.ui.composable.getString

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
                Text(text = getString("generic_yes_delete"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(text = getString("generic_dont_delete"))
            }
        },
        title = {
            Text(text = getString("password_list_delete_password", siteEntry.cachedPlainDescription))
        },
        modifier = modifier
    )
}
