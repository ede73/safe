package fi.iki.ede.safe.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag

import fi.iki.ede.safe.ui.composable.getString

@Composable
fun DeleteCategoryDialog(
    category: DecryptableCategoryEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                modifier = Modifier.testTag(TestTag.CATEGORY_ROW_DELETE_CONFIRM),
                onClick = onConfirm
            ) {
                Text(text = getString("generic_yes_delete"))
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(TestTag.CATEGORY_ROW_DELETE_CANCEL),
                onClick = onDismiss
            ) {
                Text(text = getString("generic_dont_delete"))
            }
        },
        title = {
            Text(text = getString("category_list_delete", category.plainName))
        },
        modifier = modifier
    )
}
