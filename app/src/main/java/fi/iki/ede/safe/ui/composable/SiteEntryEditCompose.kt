package fi.iki.ede.safe.ui.composable

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlin.time.ExperimentalTime

@Composable
@ExperimentalTime
@ExperimentalFoundationApi
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
            val finnishTheActivity = remember { mutableStateOf(false) }
            val saveEntryRequested = remember { mutableStateOf(false) }
            val showSaveOrDiscardDialog = remember { mutableStateOf(false) }
            val somethingChanged = remember { mutableStateOf(false) }
            val (siteEntryChanged, passwordChanged) = resolveEditsAndChangedSiteEntry(
                editingSiteEntryId,
                edits,
            )
            somethingChanged.value = siteEntryChanged

            // If we've some edits AND back button is pressed, show dialog
            BackHandler(enabled = somethingChanged.value) {
                showSaveOrDiscardDialog.value = somethingChanged.value
            }

            if (showSaveOrDiscardDialog.value) {
                AlertDialog(onDismissRequest = {
                    showSaveOrDiscardDialog.value = false
                }, confirmButton = {
                    SafeButton(onClick = {
                        showSaveOrDiscardDialog.value = false
                        saveEntryRequested.value = true
                    }) { Text(text = stringResource(id = R.string.password_entry_save)) }
                }, dismissButton = {
                    SafeButton(onClick = {
                        setResult(Activity.RESULT_CANCELED, null)
                        showSaveOrDiscardDialog.value = false
                        finnishTheActivity.value = true
                    }) { Text(text = stringResource(id = R.string.password_entry_discard)) }
                }, title = {
                    Text(text = stringResource(id = R.string.password_entry_unsaved_changes_info))
                }, modifier = Modifier.testTag(TestTag.SITE_ENTRY_SAVE_DIALOG))
            } else if (saveEntryRequested.value) {
                TryPersistSiteEntryChanges(
                    edits,
                    passwordChanged,
                    onDismiss = {
                        saveEntryRequested.value = false
                    },
                    onSaved = {
                        // TODO: what if failed?
                        val resultIntent = Intent()
                        resultIntent.putExtra(SiteEntryEditScreen.SITE_ENTRY_ID, edits.id)
                        setResult(Activity.RESULT_OK, resultIntent)
                        saveEntryRequested.value = false
                        finnishTheActivity.value = true
                    }
                )
            }
            if (finnishTheActivity.value) {
                finishActivity()
            } else {
                SiteEntryView(viewModel, skipForPreviewToWork = skipForPreviewToWork)
            }
        }
    }
}