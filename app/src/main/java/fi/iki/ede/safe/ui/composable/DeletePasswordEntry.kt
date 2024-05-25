package fi.iki.ede.safe.ui.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.safe.R

@Composable
fun DeletePasswordEntry(
    passEntry: DecryptablePasswordEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { }, confirmButton = {
            TextButton(onClick = {
                onConfirm()
            }) {
                Text(text = stringResource(id = R.string.generic_yes_delete))
            }
        }, dismissButton = {
            TextButton(onClick = {
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
