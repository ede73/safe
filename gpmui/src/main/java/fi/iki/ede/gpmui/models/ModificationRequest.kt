package fi.iki.ede.gpmui.models

import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.gpm.model.SavedGPM
import kotlinx.coroutines.CompletableDeferred

sealed class ModificationRequest {
    data class AddGpmToDisplayList(val savedGPM: SavedGPM) :
        ModificationRequest()

    data class AddSiteEntryToDisplayList(val siteEntry: DecryptableSiteEntry) :
        ModificationRequest()

    data class AddConnectedDisplayItem(val connectedEntry: Pair<DecryptableSiteEntry, SavedGPM>) :
        ModificationRequest()

    data class RemoveConnectedDisplayItem(val connectedEntry: Pair<DecryptableSiteEntry, SavedGPM>) :
        ModificationRequest()

    data class RemoveAllMatchingGpmsFromDisplayAndUnprocessedLists(val id: Long) :
        ModificationRequest()

    data object ResetSiteEntryDisplayListToAllSaved : ModificationRequest()
    data object ResetGPMDisplayListToAllUnprocessed : ModificationRequest()
    data class EmptyGpmDisplayLists(val completion: CompletableDeferred<Unit>) :
        ModificationRequest()

    data class EmptySiteEntryDisplayLists(val completion: CompletableDeferred<Unit>) :
        ModificationRequest()
}
