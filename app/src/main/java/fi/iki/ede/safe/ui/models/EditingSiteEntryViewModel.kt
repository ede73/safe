package fi.iki.ede.safe.ui.models

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DecryptableSiteEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZonedDateTime

// This will hold COPY of the decryptable password for purposes of editing
// Once finished, changes are persisted
// If device is rotated or paused/restarted, killed/restarted
// We want to KEEP EDITING what ever we were editing until discarded or saved
open class EditingSiteEntryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EditableSiteEntry(-1))
    val uiState: StateFlow<EditableSiteEntry> = _uiState.asStateFlow()

    fun editPassword(password: DecryptableSiteEntry) {
        _uiState.value = EditableSiteEntry(
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

    fun addPassword(newPassword: IVCipherText, categoryId: DBID, defaultUsername: IVCipherText) {
        _uiState.value = EditableSiteEntry(
            categoryId,
            password = newPassword,
        )
        updateUsername(defaultUsername)
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

    fun updatePasswordChangedDate(value: ZonedDateTime?) {
        // TODO: Changed
        val updatedState = _uiState.value.copy(passwordChangedDate = value)
        _uiState.value = updatedState
    }

    fun updatePhoto(value: Bitmap?) {
        // TODO: Changed
        val updatedState = _uiState.value.copy(plainPhoto = value)
        _uiState.value = updatedState
    }
}