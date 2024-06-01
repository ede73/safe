package fi.iki.ede.safe.ui.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.theme.SafeButton

@Composable
fun DeleteSiteEntryDialog(
    passEntry: DecryptableSiteEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { }, confirmButton = {
            SafeButton(onClick = {
                onConfirm()
            }) {
                Text(text = stringResource(id = R.string.generic_yes_delete))
            }
        }, dismissButton = {
            SafeButton(onClick = {
                onDismiss()
            }) {
                Text(text = stringResource(R.string.generic_dont_delete))
            }
        }, title = {
            Text(
                text = stringResource(
                    id = R.string.password_list_delete, passEntry.plainDescription
                )
            )
        })
}
