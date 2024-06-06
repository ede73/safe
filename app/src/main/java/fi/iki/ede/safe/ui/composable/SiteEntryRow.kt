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
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.theme.LocalSafeTheme
import fi.iki.ede.safe.ui.theme.SafeListItem
import kotlinx.coroutines.launch
import java.time.Period

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SiteEntryRow(
    passEntry: DecryptableSiteEntry,
    categoriesState: List<DecryptableCategoryEntry>,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val safeTheme = LocalSafeTheme.current
    var displayDeleteDialog by remember { mutableStateOf(false) }
    var displayMenu by remember { mutableStateOf(false) }
    var displayMoveDialog by remember { mutableStateOf(false) }

    val editCompleted = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    // TODO: should not be needed
                }
            }
        }
    )

    // bit more padding on the start to emphasize difference between header and entry
    // TODO: themable?
    SafeListItem(
        modifier = Modifier.padding(start = 32.dp, 6.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 0.dp)
                .fillMaxWidth()
                .combinedClickable(onClick = {
                    editCompleted.launch(
                        SiteEntryEditScreen.getEditPassword(context, passEntry.id!!)
                    )
                }, onLongClick = {
                    displayMenu = true
                })
        ) {
            Text(
                text = passEntry.plainDescription,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                // Move to bounding box (up)
                modifier = Modifier
                    .padding(12.dp)
                    .weight(2f)
            )

            if (passEntry.passwordChangedDate != null) {
                Spacer(modifier = Modifier.weight(1f)) // This will push the Text to the end

                Text(
                    text = getPasswordAgePlurality(DateUtils.getPeriodBetweenDates(passEntry.passwordChangedDate!!)),
                    modifier = Modifier.padding(12.dp),
                    style = safeTheme.customFonts.smallNote
                )
            }
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
            DeleteSiteEntryDialog(passEntry, onConfirm = {
                coroutineScope.launch {
                    DataModel.deletePassword(passEntry)
                }
                displayDeleteDialog = false
            }, onDismiss = {
                displayDeleteDialog = false
            })
        }
        if (displayMoveDialog) {
            val currentCategory =
                DataModel.getCategories().first { it.id == passEntry.categoryId }
            val filteredCategories = categoriesState.filter { it != currentCategory }
                .sortedBy { it.plainName.lowercase() }

            MoveSiteEntry(filteredCategories, onConfirm = { newCategory ->
                coroutineScope.launch {
                    DataModel.movePassword(passEntry, newCategory)
                }
                displayMoveDialog = false
            }, onDismiss = {
                displayMoveDialog = false
            })
        }
    }
}

@Composable
fun getPasswordAgePlurality(duration: Period): String {
    val z = buildList {
        if (duration.years > 0) {
            add(
                pluralStringResource(
                    id = R.plurals.password_list_password_age_years,
                    count = duration.years,
                    duration.years
                )
            )
        }
        if (duration.months > 0) {
            add(
                pluralStringResource(
                    id = R.plurals.password_list_password_age_months,
                    count = duration.months,
                    duration.months
                )
            )
        }
        // if we're at scale of years and months...just skip days, wont make a big difference
        if (!(duration.years > 0 && duration.months > 0) && duration.days > 1 || (duration.months == 0 && duration.years == 0)) {
            add(
                pluralStringResource(
                    id = R.plurals.password_list_password_age_days,
                    count = duration.days,
                    duration.days
                )
            )
        }
    }.joinToString(" ")
    return z
}

