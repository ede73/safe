package fi.iki.ede.safe.ui.activities

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.R
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.notifications.SetupNotifications
import fi.iki.ede.safe.password.PasswordGenerator
import fi.iki.ede.safe.ui.AutolockingFeaturesImpl
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen.Companion.SITE_ENTRY_ID
import fi.iki.ede.safe.ui.composable.SiteEntryView
import fi.iki.ede.safe.ui.composable.TryPersistSiteEntryChanges
import fi.iki.ede.safe.ui.models.EditableSiteEntry
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import fi.iki.ede.safe.ui.testTag
import java.time.ZonedDateTime

class SiteEntryEditScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val encrypter = KeyStoreHelperFactory.getEncrypter()

        val viewModel: EditingSiteEntryViewModel by viewModels()

        if (savedInstanceState == null) {
            if (intent.hasExtra(SITE_ENTRY_ID)) {
                // Edit a password
                val siteEntryId = intent.getLongExtra(SITE_ENTRY_ID, -1L)
                require(siteEntryId != -1L) { "Password must be value and exist" }
                viewModel.editSiteEntry(DataModel.getSiteEntry(siteEntryId))
            } else if (intent.hasExtra(CATEGORY_ID)) {
                // Add a new siteentry
                val categoryId = intent.getLongExtra(CATEGORY_ID, -1L)
                require(categoryId != -1L) { "Category must be a value and exist" }

                val passwordLength = this.resources.getInteger(R.integer.password_default_length)
                // new password - auto gen proper pwd
                val newPassword = PasswordGenerator.genPassword(
                    passUpper = true,
                    passLower = true,
                    passNum = true,
                    passSymbols = true,
                    length = passwordLength
                )

                viewModel.addPassword(
                    newPassword.encrypt(encrypter),
                    categoryId,
                    fi.iki.ede.preferences.Preferences.getDefaultUserName().encrypt(encrypter)
                )
            } else {
                require(true) { "Must have siteEntry or category ID" }
            }
        }

        val editingSiteEntryId = intent.getLongExtra(SITE_ENTRY_ID, -1L)

        SetupNotifications.setup(this)
        setContent {
            SiteEntryEditCompose(
                viewModel,
                editingSiteEntryId.takeIf { it != -1L },
                ::resolveEditsAndChangedSiteEntry,
                ::setResult,
                ::finishActivity
            )
        }
    }

    private fun finishActivity() {
        finish()
    }

    private fun resolveEditsAndChangedSiteEntry(
        editingPasswordId: DBID?,
        edits: EditableSiteEntry,
    ) = if (editingPasswordId != null) {
        val originalSiteEntry = DataModel.getSiteEntry(editingPasswordId)
        wasSiteEntryChanged(
            edits,
            originalSiteEntry
        ) to !originalSiteEntry.isSamePassword(edits.password)
    } else {
        // We're adding new password, so consider changed
        true to true
    }

    private fun wasSiteEntryChanged(
        edits: EditableSiteEntry,
        originalSiteEntry: DecryptableSiteEntry
    ) = !originalSiteEntry.isSame(
        edits.description,
        edits.website,
        edits.username,
        edits.password,
        edits.passwordChangedDate,
        edits.note,
        edits.plainPhoto,
        edits.plainExtension
    )

    companion object {
        const val SITE_ENTRY_ID = "password_id"
        const val CATEGORY_ID = "category_id"
        const val TAG = "PasswordEntryScreen"
    }
}

@Composable
private fun SiteEntryEditCompose(
    viewModel: EditingSiteEntryViewModel,
    editingSiteEntryId: DBID?,
    resolveEditsAndChangedSiteEntry: (DBID?, EditableSiteEntry) -> Pair<Boolean, Boolean>,
    setResult: (Int, Intent?) -> Unit,
    finishActivity: () -> Unit,
    skipForPreviewToWork: Boolean = false
) {
    fi.iki.ede.theme.SafeTheme {
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
                    fi.iki.ede.theme.SafeButton(onClick = {
                        showSaveOrDiscardDialog = false
                        saveEntryRequested = true
                    }) { Text(text = stringResource(id = R.string.password_entry_save)) }
                }, dismissButton = {
                    fi.iki.ede.theme.SafeButton(onClick = {
                        setResult(RESULT_CANCELED, null)
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
                        resultIntent.putExtra(SITE_ENTRY_ID, edits.id)
                        setResult(RESULT_OK, resultIntent)
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

@Preview(showBackground = true)
@Composable
fun SiteEntryScreenPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    val entry = DecryptableSiteEntry(1)
    entry.id = 1
    entry.categoryId = 1
    entry.note = IVCipherText(byteArrayOf(), "note\nmay have\nmultiple lines".toByteArray())
    entry.description = IVCipherText(byteArrayOf(), "Secret site".toByteArray())
    entry.password = IVCipherText(byteArrayOf(), "password".toByteArray())
    entry.passwordChangedDate = ZonedDateTime.now()
    entry.username = IVCipherText(byteArrayOf(), "username".toByteArray())
    entry.website = IVCipherText(byteArrayOf(), "website".toByteArray())
    //entry.photo=
    class FakeEditingSiteViewModel : EditingSiteEntryViewModel()

    val fakeViewModel = FakeEditingSiteViewModel().apply {
        // Set up the ViewModel with test data as needed
        editSiteEntry(entry)
    }
    fi.iki.ede.theme.SafeTheme {
        SiteEntryEditCompose(
            fakeViewModel,
            1,
            { _, _ -> true to true },
            { _, _ -> },
            {},
            skipForPreviewToWork = true
        )
    }
}

