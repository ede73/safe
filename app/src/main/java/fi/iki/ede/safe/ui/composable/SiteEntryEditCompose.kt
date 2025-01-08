package fi.iki.ede.safe.ui.composable

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fi.iki.ede.db.DBID
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.models.EditableSiteEntry
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.SafeButton
import fi.iki.ede.theme.SafeTheme

@Composable
internal fun SiteEntryEditCompose(
    viewModel: EditingSiteEntryViewModel,
    editingSiteEntryId: DBID?,
    resolveEditsAndChangedSiteEntry: (DBID?, EditableSiteEntry) -> Pair<Boolean, Boolean>,
    setResult: (Int, Intent?) -> Unit,
    finishActivity: () -> Unit,
    skipForPreviewToWork: Boolean = false
) {
    SafeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
            // TODO: Not sure! AlertDialogs are both closed, anyway this solves the issue
            val edits = viewModel.editableSiteEntryState.collectAsState().value
            var finnishTheActivity by remember { mutableStateOf(false) }
            var saveEntryRequested by remember { mutableStateOf(false) }
            var showSaveOrDiscardDialog by remember { mutableStateOf(false) }
            var somethingChanged by remember { mutableStateOf(false) }
            val (siteEntryChanged, passwordChanged) = resolveEditsAndChangedSiteEntry(
                editingSiteEntryId,
                edits,
            )
            somethingChanged = siteEntryChanged

            // If we've some edits AND back button is pressed, show dialog
            BackHandler(enabled = somethingChanged) {
                showSaveOrDiscardDialog = somethingChanged
            }

            if (showSaveOrDiscardDialog) {
                AlertDialog(onDismissRequest = {
                    showSaveOrDiscardDialog = false
                }, confirmButton = {
                    SafeButton(onClick = {
                        showSaveOrDiscardDialog = false
                        saveEntryRequested = true
                    }) { Text(text = stringResource(id = R.string.password_entry_save)) }
                }, dismissButton = {
                    SafeButton(onClick = {
                        setResult(Activity.RESULT_CANCELED, null)
                        showSaveOrDiscardDialog = false
                        finnishTheActivity = true
                    }) { Text(text = stringResource(id = R.string.password_entry_discard)) }
                }, title = {
                    Text(text = stringResource(id = R.string.password_entry_unsaved_changes_info))
                }, modifier = Modifier.testTag(TestTag.SITE_ENTRY_SAVE_DIALOG))
            } else if (saveEntryRequested) {
                TryPersistSiteEntryChanges(
                    edits,
                    passwordChanged,
                    onDismiss = {
                        saveEntryRequested = false
                    },
                    onSaved = {
                        // TODO: what if failed?
                        val resultIntent = Intent()
                        resultIntent.putExtra(SiteEntryEditScreen.SITE_ENTRY_ID, edits.id)
                        setResult(Activity.RESULT_OK, resultIntent)
                        saveEntryRequested = false
                        finnishTheActivity = true
                    }
                )
            }
            if (finnishTheActivity) {
                finishActivity()
            } else {
                SiteEntryView(viewModel, skipForPreviewToWork = skipForPreviewToWork)
            }
        }
    }
}