package fi.iki.ede.safe.ui.models

import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.gpm.model.SavedGPM

sealed class DNDObject {
    data object Spacer : DNDObject()
    data class JustString(val string: String) : DNDObject()
    data class GPM(val savedGPM: SavedGPM) : DNDObject()
    data class SiteEntry(val decryptableSiteEntry: DecryptableSiteEntry) : DNDObject()
}