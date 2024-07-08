package fi.iki.ede.safe.gpm.ui.models

import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.model.DecryptableSiteEntry

sealed class ModificationRequest {
    data class AddGpmToDisplayList(val savedGPM: SavedGPM) : ModificationRequest()
    data class AddSiteEntryToDisplayList(val siteEntry: DecryptableSiteEntry) :
        ModificationRequest()

    data class RemoveAllMatchingGpmsFromDisplayAndUnprocessedLists(val id: Long) :
        ModificationRequest()

    data object ResetSiteEntryDisplayListToAllSaved : ModificationRequest()
    data object ResetGPMDisplayListToAllUnprocessed : ModificationRequest()
    data object EmptyGpmDisplayLists : ModificationRequest()
    data object EmptySiteEntryDisplayLists : ModificationRequest()
}
