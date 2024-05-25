package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.password.PasswordGenerator
import fi.iki.ede.safe.ui.composable.PasswordViewComponent
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.time.ZonedDateTime

data class EditablePasswordEntry(
    val categoryId: DBID,
    val id: DBID? = null,
    // For purposes of editing fields, description and website probably aren't super sensitie
    val description: String = "",
    val website: String = "",
    // Trying to keep these as secure as possible all the time
    val username: IVCipherText = IVCipherText.getEmpty(),
    val password: IVCipherText = IVCipherText.getEmpty(),
    val note: IVCipherText = IVCipherText.getEmpty(),
    // Since we're actually dislaying the photo in UI unconditionally
    // it doesn't lessen security having it as bitmap here
    val plainPhoto: Bitmap? = null,
    val passwordChangedDate: ZonedDateTime? = null
)

// This will hold COPY of the decryptable password for purposes of editing
// Once finished, changes are persisted
// If device is rotated or paused/restarted, killed/restarted
// We want to KEEP EDITING what ever we were editing until discarded or saved
open class EditingPasswordViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EditablePasswordEntry(-1))
    val uiState: StateFlow<EditablePasswordEntry> = _uiState.asStateFlow()

    fun editPassword(password: DecryptablePasswordEntry) {
        _uiState.value = EditablePasswordEntry(
            password.categoryId as DBID,
            password.id as DBID,
            password.plainDescription,
            password.plainWebsite,
            password.username,
            password.password,
            password.note,
            password.plainPhoto,
            password.passwordChangedDate
        )
    }

    fun addPassword(newPassword: IVCipherText, categoryId: DBID) {
        _uiState.value = EditablePasswordEntry(
            categoryId,
            password = newPassword
        )
    }

    fun updateDescription(value: String) {
        val updatedState = _uiState.value.copy(description = value)
        _uiState.value = updatedState
    }

    fun updateWebSite(value: String) {
        val updatedState = _uiState.value.copy(website = value)
        _uiState.value = updatedState
    }

    fun updateUsername(value: IVCipherText) {
        val updatedState = _uiState.value.copy(username = value)
        _uiState.value = updatedState
    }

    fun updatePassword(value: IVCipherText) {
        val updatedState = _uiState.value.copy(password = value)
        _uiState.value = updatedState
    }

    fun updateNote(value: IVCipherText) {
        // TODO: Changed
        val updatedState = _uiState.value.copy(note = value)
        _uiState.value = updatedState
    }

    fun updatePhoto(value: Bitmap?) {
        // TODO: Changed
        val updatedState = _uiState.value.copy(plainPhoto = value)
        _uiState.value = updatedState
    }
}

class PasswordEntryScreen : AutoLockingComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // we have description, check for edits(changes!)
        val ks = KeyStoreHelperFactory.getKeyStoreHelper()

        val viewModel: EditingPasswordViewModel by viewModels()

        if (savedInstanceState == null) {
            if (intent.hasExtra(PASSWORD_ID)) {
                // Edit a password
                val passwordId = intent.getLongExtra(PASSWORD_ID, -1L)
                require(passwordId != -1L) { "Password must be valud and exist" }
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

                viewModel.addPassword(newPassword.encrypt(ks), categoryId)
            } else {
                require(true) { "Must have password or category ID" }
            }
        }

        setContent {
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
                    val editingPasswordId: DBID? =
                        intent.getLongExtra(PASSWORD_ID, -1).takeIf { it != -1L }

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
                            TextButton(onClick = {
                                showSaveOrDiscardDialog = false
                                saveEntryRequested = true
                            }) { Text(text = stringResource(id = R.string.password_entry_save)) }
                        }, dismissButton = {
                            TextButton(onClick = {
                                setResult(RESULT_CANCELED)
                                showSaveOrDiscardDialog = false
                                finnishTheActivity = true
                            }) { Text(text = stringResource(id = R.string.password_entry_discard)) }
                        }, title = {
                            Text(text = stringResource(id = R.string.password_entry_save_info))
                        })
                    } else if (saveEntryRequested) {
                        TryPersistPasswordEntryChanges(
                            edits,
                            ks,
                            passwordChanged,
                            onDismiss = {
                                saveEntryRequested = false
                            },
                            onSaved = {
                                // TODO: what if failed?
                                setResult(RESULT_OK)
                                saveEntryRequested = false
                                finnishTheActivity = true
                            }
                        )
                    }
                    if (finnishTheActivity) {
                        finish()
                    } else {
                        PasswordViewComponent(viewModel)
                    }
                }
            }
        }
    }

    @Composable
    private fun TryPersistPasswordEntryChanges(
        edits: EditablePasswordEntry,
        ks: KeyStoreHelper,
        passwordChanged: Boolean,
        onDismiss: () -> Unit,
        onSaved: (Boolean) -> Unit
    ) {
        var emptyDescription by remember { mutableStateOf(false) }
        // test for empty
        if (TextUtils.isEmpty(edits.description)) {
            emptyDescription = true
        }
        if (emptyDescription) {
            AlertDialog(onDismissRequest = {
                onDismiss()
                emptyDescription = false
            },
                confirmButton = {
                    TextButton(onClick = {
                        onDismiss()
                        emptyDescription = false
                    }) {
                        Text(text = stringResource(id = R.string.generic_ok))
                    }
                },
                title = { Text(text = stringResource(id = R.string.password_entry_description_required)) })
            return
        }

        PersistPasswordEntryChanges(
            edits,
            ks,
            passwordChanged,
            onSaved
        )
    }

    @Composable
    private fun PersistPasswordEntryChanges(
        edits: EditablePasswordEntry,
        ks: KeyStoreHelper,
        passwordChanged: Boolean,
        onSaved: (Boolean) -> Unit
    ) {
        require(!TextUtils.isEmpty(edits.description)) { "Description must be set" }

        val passwordEntry = DecryptablePasswordEntry(edits.categoryId)
        passwordEntry.apply {
            id = edits.id
            description = edits.description.encrypt(ks)
            website = edits.website.encrypt(ks)
            username = edits.username
            password = edits.password
            note = edits.note
            photo =
                if (edits.plainPhoto == null) IVCipherText.getEmpty()
                else edits.plainPhoto.encrypt(ks)

            if (passwordChanged) {
                passwordChangedDate = ZonedDateTime.now()
            }
        }

        // TODO: MAKE ASYNC
        //coroutineScope.launch {
        runBlocking {
            DataModel.addOrUpdatePassword(passwordEntry)
        }
        // TODO: What if failed
        onSaved(true)
    }

    private fun resolveEditsAndChangedPassword(
        editingPasswordId: DBID?,
        edits: EditablePasswordEntry,
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
        edits: EditablePasswordEntry,
        originalPassword: DecryptablePasswordEntry
    ) =
        !originalPassword.isSame(
            edits.description,
            edits.website,
            edits.username,
            edits.password,
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

        private fun getIntent(context: Context) = Intent(context, PasswordEntryScreen::class.java)
        // TODO: (NavigationFlow) Not sure why this FLAG_ACTIVITY_REORDER_TO_FRONT was added (or used), but this makes
        // Search behave oddly. Repro:
        // Search for smthg, open password entry (call it A), lock the screen
        // open screen lock, PasswordSafe is also locked, open it, it's in main view
        // Search again (same maybe), open another entry(call it B), previous entry opens instead
        // Correct password ID is passed in here, however REORDER notices
        // PasswordEntryScreen already exists, and pops that in front - just happens to be the entry A
        // PasswordEntryScreen is not refreshed, it's CTOR not called
        // EDIT: HA! ok, without this one, we'll end up multiple password entries and they fill up window back stack
        // Basically most logical fix would be enabling this AND
        // making sure if password entry WAS open during lock (or rather IF there is a password entry at unlock, then pop that one open!)
        //.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    }
}

private fun makeBase64(bitmap: Bitmap?): String {
    if (bitmap == null) {
        return ""
    }
    ByteArrayOutputStream().apply {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, this)
        val byteArray = toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
}

private fun String.encrypt(ks: KeyStoreHelper) = ks.encryptByteArray(this.trim().toByteArray())
private fun Bitmap.encrypt(ks: KeyStoreHelper) = makeBase64(this).encrypt(ks)
//private fun IVCipherText.decrypt(ks: KeyStoreHelper) =
//    String(ks.decryptByteArray(this))

@Preview(showBackground = true)
@Composable
fun PasswordEntryScreenPreview() {
    val entry = DecryptablePasswordEntry(1)
    entry.id = 1
    entry.categoryId = 1
    entry.note = IVCipherText(byteArrayOf(), "note\nmay have\nmultiple lines".toByteArray())
    entry.description = IVCipherText(byteArrayOf(), "Secret site".toByteArray())
    entry.password = IVCipherText(byteArrayOf(), "password".toByteArray())
    entry.passwordChangedDate = ZonedDateTime.now()
    entry.username = IVCipherText(byteArrayOf(), "username".toByteArray())
    entry.website = IVCipherText(byteArrayOf(), "website".toByteArray())
    //entry.photo=
    class FakeEditingPasswordViewModel : EditingPasswordViewModel()

    val fakeViewModel = FakeEditingPasswordViewModel().apply {
        // Set up the ViewModel with test data as needed
        editPassword(entry)
    }
    SafeTheme {
        //TODO:
        PasswordViewComponent(fakeViewModel)
    }
}