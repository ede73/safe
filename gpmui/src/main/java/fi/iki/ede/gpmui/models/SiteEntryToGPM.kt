package fi.iki.ede.gpmui.models

import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.gpm.model.SavedGPM

data class SiteEntryToGPM(
    val siteEntry: DecryptableSiteEntry?,
    val gpm: SavedGPM?,
    val connected: Boolean = false
)