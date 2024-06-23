package fi.iki.ede.safe.gpm.ui.models

import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.model.DecryptableSiteEntry

sealed class ModificationRequest {
    data class DisplayGPM(val savedGPM: SavedGPM) : ModificationRequest()
    data class DisplaySiteEntry(val siteEntry: DecryptableSiteEntry) : ModificationRequest()
    data class RemoveGPM(val id: Long) : ModificationRequest()
    data object ResetSiteEntryDisplayListToAllSaved : ModificationRequest()
    data object ResetGPMDisplayListToAllUnprocessed : ModificationRequest()
    data object EmptyGPMDisplayLists : ModificationRequest()
    data object EmptySiteEntryDisplayLists : ModificationRequest()
    data class InitializeSiteEntryListAndDisplayListToGivenList(val siteEntries: List<DecryptableSiteEntry>) :
        ModificationRequest()

    data class InitializeUnprocessedGPMAndDisplayListToGivenList(val savedGPMs: List<SavedGPM>) :
        ModificationRequest()
}
