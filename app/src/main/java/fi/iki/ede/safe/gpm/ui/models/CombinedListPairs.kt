package fi.iki.ede.safe.gpm.ui.models

import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.model.DecryptableSiteEntry

sealed class CombinedListPairs {
    data class SiteEntryToGPM(val siteEntry: DecryptableSiteEntry?, val gpm: SavedGPM?) :
        CombinedListPairs()
}