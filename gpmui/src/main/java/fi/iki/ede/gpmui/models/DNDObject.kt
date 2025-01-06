package fi.iki.ede.gpmui.models

import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.gpm.model.SavedGPM

sealed class DNDObject {
    data object Spacer : DNDObject()
    data class JustString(val string: String) : DNDObject()
    data class GPM(val savedGPM: SavedGPM) : DNDObject()
    data class SiteEntry(val decryptableSiteEntry: DecryptableSiteEntry) : DNDObject()
}