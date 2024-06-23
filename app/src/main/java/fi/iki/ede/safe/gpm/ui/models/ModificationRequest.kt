package fi.iki.ede.safe.gpm.ui.models

import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.model.DecryptableSiteEntry

sealed class ModificationRequest {
    data class DisplayGPM(val savedGPM: SavedGPM) : ModificationRequest()
    data class DisplaySiteEntry(val siteEntry: DecryptableSiteEntry) : ModificationRequest()
    data class RemoveGPM(val id: Long) : ModificationRequest()
    data object ResetSiteEntryDisplayList : ModificationRequest()
    data object ResetGPMDisplayList : ModificationRequest()
    data object EmptyDisplayLists : ModificationRequest()
    data class InitializeSiteEntries(val siteEntries: List<DecryptableSiteEntry>) :
        ModificationRequest()

    data class InitializeGPMs(val savedGPMs: List<SavedGPM>) : ModificationRequest()
}
