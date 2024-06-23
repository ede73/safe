package fi.iki.ede.safe.gpm.ui.models

import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.model.DecryptableSiteEntry

sealed class DNDObject {
    data object Spacer : DNDObject()
    data class JustString(val string: String) : DNDObject()
    data class GPM(val savedGPM: SavedGPM) : DNDObject()
    data class SiteEntry(val decryptableSiteEntry: DecryptableSiteEntry) : DNDObject()
}