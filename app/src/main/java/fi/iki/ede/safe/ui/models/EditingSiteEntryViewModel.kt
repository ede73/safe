package fi.iki.ede.safe.ui.models

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.model.SiteEntryExtensionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZonedDateTime

// This will hold COPY of the decryptable password for purposes of editing
// Once finished, changes are persisted
// If device is rotated or paused/restarted, killed/restarted
// We want to KEEP EDITING what ever we were editing until discarded or saved
open class EditingSiteEntryViewModel : ViewModel() {
    private val _editableSiteEntryState = MutableStateFlow(EditableSiteEntry(-1))
    val editableSiteEntryState: StateFlow<EditableSiteEntry> = _editableSiteEntryState.asStateFlow()

    fun editSiteEntry(siteEntry: DecryptableSiteEntry) {
        _editableSiteEntryState.value = EditableSiteEntry(
            siteEntry.categoryId as DBID,
            siteEntry.id as DBID,
            siteEntry.cachedPlainDescription,
            siteEntry.plainWebsite,
            siteEntry.username,
            siteEntry.password,
            siteEntry.note,
            siteEntry.plainPhoto,
            siteEntry.passwordChangedDate,
            siteEntry.plainExtensions
        )
    }

    fun addPassword(newPassword: IVCipherText, categoryId: DBID, defaultUsername: IVCipherText) {
        _editableSiteEntryState.value = EditableSiteEntry(
            categoryId,
            password = newPassword,
        )
        updateUsername(defaultUsername)
    }

    fun updateDescription(value: String) {
        val updatedState = _editableSiteEntryState.value.copy(description = value)
        _editableSiteEntryState.value = updatedState
    }

    fun updateWebSite(value: String) {
        val updatedState = _editableSiteEntryState.value.copy(website = value)
        _editableSiteEntryState.value = updatedState
    }

    fun updateUsername(value: IVCipherText) {
        val updatedState = _editableSiteEntryState.value.copy(username = value)
        _editableSiteEntryState.value = updatedState
    }

    fun updatePassword(value: IVCipherText) {
        val updatedState = _editableSiteEntryState.value.copy(password = value)
        _editableSiteEntryState.value = updatedState
    }

    fun updateNote(value: IVCipherText) {
        // TODO: Changed
        val updatedState = _editableSiteEntryState.value.copy(note = value)
        _editableSiteEntryState.value = updatedState
    }

    fun updatePasswordChangedDate(value: ZonedDateTime?) {
        // TODO: Changed
        val updatedState = _editableSiteEntryState.value.copy(passwordChangedDate = value)
        _editableSiteEntryState.value = updatedState
    }

    fun updatePhoto(value: Bitmap?) {
        // TODO: Changed
        val updatedState = _editableSiteEntryState.value.copy(plainPhoto = value)
        _editableSiteEntryState.value = updatedState
    }

    fun updateExtensions(map: Map<SiteEntryExtensionType, Set<String>>) {
        val currentState = _editableSiteEntryState.value
        val updateState = currentState.copy(plainExtension = map)
        _editableSiteEntryState.value = updateState
    }
}