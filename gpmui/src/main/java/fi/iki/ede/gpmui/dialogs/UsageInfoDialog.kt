package fi.iki.ede.gpmui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.theme.SafeTextButton

@Composable
fun UsageInfoDialog(
    message: String,
    onDismiss: () -> Unit
) = AlertDialog(
    onDismissRequest = { onDismiss() },
    confirmButton = {},
    dismissButton = {
        SafeTextButton(onClick = onDismiss) {
            Text("OK")
        }
    },
    title = { Text("Important!") },
    text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(text = message)
        }
    }
)

@Preview(showBackground = true)
@Composable
fun UsageInfoPreview() {
    MaterialTheme {
        UsageInfoDialog("message") {}
    }
}