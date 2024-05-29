package fi.iki.ede.safe.ui.composable

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.activities.PasswordEntryScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PasswordRow(
    passEntry: DecryptablePasswordEntry, /* TODO: NOt needed*/
    categoriesState: List<DecryptableCategoryEntry>,
    onRefreshEntries: () -> Unit
) {
    val context = LocalContext.current
    var displayMenu by remember { mutableStateOf(false) }
    var displayDeleteDialog by remember { mutableStateOf(false) }
    var displayMoveDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val editCompleted = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    // TODO: should not be needed
                    onRefreshEntries()
                }
            }
        }
    )

    Card(
        modifier = Modifier.padding(start = 32.dp, 6.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 0.dp)
                .fillMaxWidth()
                .combinedClickable(onClick = {
                    editCompleted.launch(
                        PasswordEntryScreen.getEditPassword(context, passEntry.id!!)
                    )
                    // TODO: Wait for result..(until roomdb ready), so we can refresh the list...
                }, onLongClick = {
                    displayMenu = true
                })
        ) {
            Text(
                text = passEntry.plainDescription,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Move to bounding box (up)
                modifier = Modifier
                    .padding(12.dp)
                    .weight(1f)
            )

            if (passEntry.passwordChangedDate != null) {
                Spacer(modifier = Modifier.weight(1f)) // This will push the Text to the end
                val duration = DateUtils.durationBetweenDateAndNow(passEntry.passwordChangedDate!!)
                Text(
                    text = pluralStringResource(
                        id = R.plurals.password_list_password_age,
                        count = duration.toDays().toInt(),
                        duration.toDays().toInt()
                    ), Modifier.padding(12.dp)
                )
            }
            DropdownMenu(expanded = displayMenu, onDismissRequest = { displayMenu = false }) {
                DropdownMenuItem(text = {
                    Text(
                        text = stringResource(
                            id = R.string.password_list_delete_password,
                            passEntry.plainDescription
                        )
                    )
                }, onClick = {
                    displayMenu = false
                    displayDeleteDialog = true
                })
                DropdownMenuItem(text = {
                    Text(
                        text = stringResource(
                            id = R.string.password_list_move_password,
                            passEntry.plainDescription
                        )
                    )
                }, onClick = {
                    displayMenu = false
                    displayMoveDialog = true
                })
            }
            if (displayDeleteDialog) {
                DeletePasswordEntry(passEntry, onConfirm = {
                    coroutineScope.launch {
                        DataModel.deletePassword(passEntry)
                    }
                    displayDeleteDialog = false
                    onRefreshEntries()
                }, onDismiss = {
                    displayDeleteDialog = false
                })
            }
            if (displayMoveDialog) {
                val currentCategory =
                    DataModel.getCategories().first { it.id == passEntry.categoryId }
                val filteredCategories = categoriesState.filter { it != currentCategory }
                    .sortedBy { it.plainName.lowercase() }

                MovePasswordEntry(filteredCategories, onConfirm = { newCategory ->
                    coroutineScope.launch {
                        DataModel.movePassword(passEntry, newCategory)
                    }
                    displayMoveDialog = false
                    onRefreshEntries()
                }, onDismiss = {
                    displayMoveDialog = false
                })
            }
        }
    }
}
