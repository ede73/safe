package fi.iki.ede.gpmdatamodel.db

import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.db.DBID
import fi.iki.ede.db.SiteEntryGPMJoin
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.time.ExperimentalTime

@ExperimentalTime
object GPMDB {
    private val database get() = DBHelperFactory.getDBHelper().database

    fun getExternalTables(): List<Any> = emptyList()

    fun upgradeTables(db: Any, upgrade: Int) {}

    fun deleteAllSavedGPMs() = runBlocking {
        database.gpmDao().deleteAll()
        database.siteEntryGPMJoinDao().deleteAll()
    }

    fun markSavedGPMIgnored(savedGPMID: DBID): Long = runBlocking {
        database.gpmDao().updateStatus(savedGPMID, 1).toLong()
    }

    fun linkSaveGPMAndSiteEntry(siteEntryID: DBID, savedGPMID: DBID) = runBlocking {
        database.siteEntryGPMJoinDao().insert(SiteEntryGPMJoin(siteEntryID, savedGPMID))
    }

    fun fetchAllSiteEntryGPMMappings(): Map<DBID, Set<DBID>> = runBlocking {
        database.siteEntryGPMJoinDao().getAll()
            .groupBy({ it.passwordId }, { it.gpmId })
            .mapValues { (_, values) -> values.toSet() }
    }

    fun fetchAllSavedGPMsFromDB(
        gpmsFlow: MutableStateFlow<Set<SavedGPM>>? = null
    ): Set<SavedGPM> = runBlocking {
        val gpms = database.gpmDao().getAll().toSet()
        if (gpmsFlow != null) {
            gpmsFlow.value = gpms
        }
        gpms
    }

    fun deleteObsoleteSavedGPMs(delete: Set<SavedGPM>) = runBlocking {
        delete.forEach { database.gpmDao().delete(it) }
    }

    fun updateSavedGPMByIncomingGPM(update: Map<IncomingGPM, SavedGPM>) = runBlocking {
        update.forEach { (incomingGPM, savedGPM) ->
            val updated = savedGPM.copy(
                encryptedName = incomingGPM.name.encrypt(),
                encryptedUrl = incomingGPM.url.encrypt(),
                encryptedUsername = incomingGPM.username.encrypt(),
                encryptedPassword = incomingGPM.password.encrypt(),
                encryptedNote = incomingGPM.note.encrypt(),
                hash = incomingGPM.hash
            )
            database.gpmDao().update(updated)
        }
    }

    fun addNewIncomingGPM(add: Set<IncomingGPM>) = runBlocking {
        add.forEach { incomingGPM ->
            database.gpmDao().insert(SavedGPM(importing = incomingGPM))
        }
    }

    fun addSavedGPM(savedGPM: SavedGPM) = runBlocking {
        database.gpmDao().insert(savedGPM)
    }
}