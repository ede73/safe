package fi.iki.ede.safe.ui.composable

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import fi.iki.ede.autolock.AvertInactivityDuringLongTask
import fi.iki.ede.clipboardutils.ClipboardUtils
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.datepicker.DatePicker
import fi.iki.ede.db.DBID
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.R
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.dialogs.ShowLinkedGpmsDialog
import fi.iki.ede.safe.ui.models.EditableSiteEntry
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safephoto.SafePhoto
import fi.iki.ede.theme.SafeButton
import fi.iki.ede.theme.SafeTextButton
import fi.iki.ede.theme.SafeTheme
import kotlinx.coroutines.launch
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
        val context = LocalContext.current
        val edits = viewModel.editableSiteEntryState.collectAsState().value
        val finnishTheActivity = remember { mutableStateOf(false) }
        val saveEntryRequested = remember { mutableStateOf(false) }
        val showSaveOrDiscardDialog = remember { mutableStateOf(false) }
        val somethingChanged = remember { mutableStateOf(false) }
        val showLinkedInfo = remember { mutableStateOf<Set<SavedGPM>?>(null) }
        val (siteEntryChanged, passwordChanged) = resolveEditsAndChangedSiteEntry(
            editingSiteEntryId,
            edits,
        )
        somethingChanged.value = siteEntryChanged

        // If we've some edits AND back button is pressed, show dialog
        BackHandler(enabled = somethingChanged.value) {
            showSaveOrDiscardDialog.value = somethingChanged.value
        }

        Surface(
            modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
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
                SiteEntryView(
                    description = edits.description,
                    onDescriptionChange = { viewModel.updateDescription(it) },
                    website = edits.website,
                    onWebSiteChange = { viewModel.updateWebSite(it) },
                    username = edits.username.decrypt(),
                    onUsernameChange = { viewModel.updateUsername(it.encrypt()) },
                    password = edits.password.decrypt(),
                    onPasswordChange = { viewModel.updatePassword(it.encrypt()) },
                    note = edits.note.decrypt(),
                    onNoteChange = { viewModel.updateNote(it.encrypt()) },
                    onOpenBrowser = { url ->
                        val uri = tryParseUri(url)
                        if (uri.scheme != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    },
                    onCopyToClipboard = { text ->
                        ClipboardUtils.addToClipboard(
                            context,
                            text,
                            Preferences.getClipboardClearDelaySecs()
                        )
                    },
                    originalPassword = viewModel.originalPassword?.decrypt(),
                    breachCheckButtonContent = if (skipForPreviewToWork) null else {
                        @Composable {
                            breachCheckButton(context, edits.password)()
                        }
                    },
                    datePickerContent = {
                        Column {
                            Text(text = stringResource(id = R.string.password_entry_changed_date))
                            DatePicker(
                                utcInstant = edits.passwordChangedDate,
                                onValueChange = { date ->
                                    viewModel.updatePasswordChangedDate(date)
                                }
                            )
                        }
                    },
                    linkedGpmContent = {
                        edits.id?.let { pid ->
                            GPMDataModel.getLinkedGPMs(pid).takeIf { it.isNotEmpty() }?.let { gpms ->
                                Box(modifier = Modifier.clickable { showLinkedInfo.value = gpms }) {
                                    Text(text = pluralStringResource(R.plurals.site_entry_linked_gpms_count, gpms.size, gpms.size))
                                }
                            }
                        }
                    },
                    extensionsContent = {
                        SiteEntryExtensionList(viewModel)
                    },
                    photoContent = {
                        if (context is AvertInactivityDuringLongTask) {
                            SafePhoto(
                                { isPausedOrResume, why ->
                                    if (isPausedOrResume) {
                                        (context as AvertInactivityDuringLongTask).pauseInactivity(context, why)
                                    } else {
                                        (context as AvertInactivityDuringLongTask).resumeInactivity(context, why)
                                    }
                                },
                                currentPhoto = edits.plainPhoto,
                                onBitmapCaptured = {
                                    val samePhoto = it?.sameAs(edits.plainPhoto) ?: (edits.plainPhoto == null)
                                    if (!samePhoto) {
                                        viewModel.updatePhoto(it)
                                    }
                                },
                                photoPermissionRequiredContent = { oldPhoto, onBitmapCaptured, askPermission ->
                                    TakePhotoOrAskPermission(askPermission, oldPhoto, onBitmapCaptured)
                                },
                                takePhotoContent = { oldPhoto, onBitmapCaptured, takePhoto ->
                                    TakePhotoOrAskPermission(takePhoto, oldPhoto, onBitmapCaptured)
                                },
                                composeTakePhoto = { takePhoto ->
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        SafeTextButton(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(16.dp),
                                            onClick = { takePhoto() }
                                        ) {
                                            Text(text = stringResource(id = R.string.password_entry_capture_photo))
                                        }
                                    }
                                }
                            )
                        }
                    }
                )
            }

            if (showLinkedInfo.value != null) {
                ShowLinkedGpmsDialog(
                    showLinkedInfo.value!!,
                    onDismiss = { showLinkedInfo.value = null }
                )
            }
        }
    }
}

private fun tryParseUri(website: String): Uri =
    if (website.lowercase().startsWith("http://") ||
        website.lowercase().startsWith("https://")
    ) website.toUri()
    else "https://$website".toUri()

@Composable
private fun TakePhotoOrAskPermission(
    askPermission: MutableState<Boolean>,
    oldPhoto: Bitmap?,
    onBitmapCaptured: (Bitmap?) -> Unit
) {
    Row {
        SafeTextButton(onClick = { askPermission.value = true }) {
            Text(text = stringResource(id = R.string.password_entry_capture_photo))
        }
        if (oldPhoto != null) {
            SafeTextButton(onClick = { onBitmapCaptured(null) }) {
                Text(text = stringResource(id = R.string.password_entry_delete_photo))
            }
        }
    }
}