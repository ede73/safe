package fi.iki.ede.gpmui.models

import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.gpm.model.*
import kotlin.time.ExperimentalTime

@ExperimentalTime
data class SiteEntryToGPM(
    val siteEntry: DecryptableSiteEntry?,
    val gpm: SavedGPM?,
    val connected: Boolean = false
)