package fi.iki.ede.safe.ui.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import fi.iki.ede.safe.ui.theme.SafeButton

@Composable
fun YesNoDialog(
    openDialog: MutableState<Boolean>,
    title: String,
    text: String? = null,
    positiveText: String,
    positive: () -> Unit,
    negativeText: String? = null,
    negative: () -> Unit = {},
    dismissed: () -> Unit = {}
) {
    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = {
                dismissed()
            },
            confirmButton = {
                SafeButton(onClick = {
                    positive()
                    openDialog.value = false
                }) { Text(positiveText) }
            },
            dismissButton = {
                SafeButton(onClick = {
                    negative()
                    openDialog.value = false
                }) { if (negativeText != null) Text(negativeText) }
            },
            title = { Text(title) },
            text = {
                if (text != null)
                    Text(text)
            }
        )
    }
}