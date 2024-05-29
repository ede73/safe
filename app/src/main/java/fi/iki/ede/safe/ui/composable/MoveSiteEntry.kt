package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.activities.CategoryListScreen

@Composable
fun MoveSiteEntry(
    targetCategories: List<DecryptableCategoryEntry>,
    onConfirm: (newCategory: DecryptableCategoryEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedEntry by remember { mutableStateOf<DecryptableCategoryEntry?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    stringResource(
                        id = R.string.move_password_title,
                        selectedEntry!!.plainName
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedEntry?.let { onConfirm(it) }
                        showDialog = false
                    }
                ) {
                    Text(
                        stringResource(
                            id = R.string.move_password_confirm,
                            selectedEntry!!.plainName
                        )
                    )
                }
            },
            dismissButton = {
                Button(onClick = { showDialog = false }) {
                    Text(stringResource(id = R.string.move_password_cancel))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(stringResource(id = R.string.move_password_select_category)) },
            text = {
                LazyColumn {
                    items(targetCategories.sortedBy { it.plainName }) { entry ->
                        Card(modifier = Modifier.padding(6.dp), shape = RoundedCornerShape(20.dp)) {
                            Text(
                                text = entry.plainName,
                                modifier = Modifier
                                    .clickable {
                                        selectedEntry = entry
                                        showDialog = true
                                    }
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .testTag(CategoryListScreen.TESTTAG_CATEGORY_MOVE_ROW)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Button(onClick = { onDismiss() }) {
                    Text(stringResource(id = R.string.move_password_cancel))
                }
            }
        )
    }
}