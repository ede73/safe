package fi.iki.ede.safe.ui.activities

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Context
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
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.safe.R
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.password.PasswordGenerator
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen.Companion.PASSWORD_ID
import fi.iki.ede.safe.ui.composable.SiteEntryView
import fi.iki.ede.safe.ui.composable.TryPersistPasswordEntryChanges
import fi.iki.ede.safe.ui.models.EditableSiteEntry
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import java.time.ZonedDateTime

class SiteEntryEditScreen : AutolockingBaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val encrypter = KeyStoreHelperFactory.getEncrypter()

        val viewModel: EditingSiteEntryViewModel by viewModels()

        if (savedInstanceState == null) {
            if (intent.hasExtra(PASSWORD_ID)) {
                // Edit a password
                val passwordId = intent.getLongExtra(PASSWORD_ID, -1L)
                require(passwordId != -1L) { "Password must be value and exist" }
                val password = DataModel.getPassword(passwordId)
                viewModel.editPassword(password)
            } else if (intent.hasExtra(CATEGORY_ID)) {
                // Add a new password
                val categoryId = intent.getLongExtra(CATEGORY_ID, -1L)
                require(categoryId != -1L) { "Category must be a value and exist" }

                val passwordLength = this.resources.getInteger(R.integer.password_default_length)
                // new password - auto gen proper pwd
                val newPassword = PasswordGenerator.genPassword(
                    passUpper = true,
                    passLower = true,
                    passNum = true,
                    passSymbol = true,
                    length = passwordLength
                )

                viewModel.addPassword(
                    newPassword.encrypt(encrypter),
                    categoryId,
                    Preferences.getDefaultUserName().encrypt(encrypter)
                )
            } else {
                require(true) { "Must have password or category ID" }
            }
        }

        val editingPasswordId = intent.getLongExtra(PASSWORD_ID, -1L)

        setContent {
            SiteEntryEditCompose(
                viewModel,
                editingPasswordId.takeIf { it != -1L },
                ::resolveEditsAndChangedPassword,
                ::setResult,
                ::finishActivity
            )
        }
    }

    private fun finishActivity() {
        finish()
    }

    private fun resolveEditsAndChangedPassword(
        editingPasswordId: DBID?,
        edits: EditableSiteEntry,
    ) = if (editingPasswordId != null) {
        val originalPassword = DataModel.getPassword(editingPasswordId)
        wasPasswordEntryChanged(
            edits,
            originalPassword
        ) to !originalPassword.isSamePassword(edits.password)
    } else {
        // We're adding new password, so consider changed
        true to true
    }

    private fun wasPasswordEntryChanged(
        edits: EditableSiteEntry,
        originalPassword: DecryptableSiteEntry
    ) =
        !originalPassword.isSame(
            edits.description,
            edits.website,
            edits.username,
            edits.password,
            edits.passwordChangedDate,
            edits.note,
            edits.plainPhoto
        )

    companion object {
        const val PASSWORD_ID = "password_id"
        const val CATEGORY_ID = "category_id"
        const val TAG = "PasswordEntryScreen"

        fun getEditPassword(context: Context, passwordId: DBID) =
            getIntent(context).putExtra(PASSWORD_ID, passwordId)

        fun getAddPassword(context: Context, categoryId: DBID) =
            getIntent(context).putExtra(CATEGORY_ID, categoryId)

        private fun getIntent(context: Context) = Intent(context, SiteEntryEditScreen::class.java)
    }
}

@Composable
private fun SiteEntryEditCompose(
    viewModel: EditingSiteEntryViewModel,
    editingPasswordId: DBID?,
    resolveEditsAndChangedPassword: (DBID?, EditableSiteEntry) -> Pair<Boolean, Boolean>,
    setResult: (Int, Intent?) -> Unit,
    finishActivity: () -> Unit,
) {
    SafeTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
            // Something return this activity, despite calling finish()
            // TODO: Not sure! AlertDialogs are both closed, anyway this solves the issue
            var finnishTheActivity by remember { mutableStateOf(false) }
            var showSaveOrDiscardDialog by remember { mutableStateOf(false) }
            var saveEntryRequested by remember { mutableStateOf(false) }

            val edits = viewModel.uiState.collectAsState().value
            var somethingChanged by remember { mutableStateOf(false) }

            val (passwordEntryChanged, passwordChanged) = resolveEditsAndChangedPassword(
                editingPasswordId,
                edits,
            )
            somethingChanged = passwordEntryChanged

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
                        setResult(RESULT_CANCELED, null)
                        showSaveOrDiscardDialog = false
                        finnishTheActivity = true
                    }) { Text(text = stringResource(id = R.string.password_entry_discard)) }
                }, title = {
                    Text(text = stringResource(id = R.string.password_entry_save_info))
                })
            } else if (saveEntryRequested) {
                TryPersistPasswordEntryChanges(
                    edits,
                    passwordChanged,
                    onDismiss = {
                        saveEntryRequested = false
                    },
                    onSaved = {
                        // TODO: what if failed?
                        val resultIntent = Intent()
                        resultIntent.putExtra(PASSWORD_ID, edits.id)
                        setResult(RESULT_OK, resultIntent)
                        saveEntryRequested = false
                        finnishTheActivity = true
                    }
                )
            }
            if (finnishTheActivity) {
                finishActivity()
            } else {
                SiteEntryView(viewModel)
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PasswordEntryScreenPreview() {
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
    class FakeEditingPasswordViewModel : EditingSiteEntryViewModel()

    val fakeViewModel = FakeEditingPasswordViewModel().apply {
        // Set up the ViewModel with test data as needed
        editPassword(entry)
    }
    SafeTheme {
        //TODO:
        //SiteEntryView(fakeViewModel)
        SiteEntryEditCompose(
            fakeViewModel,
            1,
            { _, _ -> true to true },
            { _, _ -> },
            {})
    }
}

