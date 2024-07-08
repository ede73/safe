package fi.iki.ede.safe.gpm.ui.utilities

import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.model.DecryptableSiteEntry

data class SiteEntryToGPM(val siteEntry: DecryptableSiteEntry?, val gpm: SavedGPM?)