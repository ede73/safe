package fi.iki.ede.gpmui.models

import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.gpm.model.*
import kotlin.time.ExperimentalTime

sealed class DNDObject {
    data object Spacer : DNDObject()
    data class JustString(val string: String) : DNDObject()
    data class GPM(val savedGPM: SavedGPM) : DNDObject()

    @ExperimentalTime
    data class SiteEntry(val decryptableSiteEntry: DecryptableSiteEntry) : DNDObject()
}