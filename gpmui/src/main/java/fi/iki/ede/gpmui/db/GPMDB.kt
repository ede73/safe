package fi.iki.ede.gpmui.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import fi.iki.ede.cryptoobjects.encrypt
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.db.DBID
import fi.iki.ede.db.GooglePasswordManager
import fi.iki.ede.db.SiteEntry2GooglePasswordManager
import fi.iki.ede.db.delete
import fi.iki.ede.db.getDBID
import fi.iki.ede.db.getIVCipher
import fi.iki.ede.db.getString
import fi.iki.ede.db.insert
import fi.iki.ede.db.put
import fi.iki.ede.db.query
import fi.iki.ede.db.update
import fi.iki.ede.db.whereEq
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpm.model.SavedGPM.Companion.makeFromEncryptedStringFields
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "GPMDB"

object GPMDB {
    private fun getReadableDatabase(): SQLiteDatabase =
        DBHelperFactory.getDBHelper().readableDatabase

    private fun getWritableDatabase(): SQLiteDatabase =
        DBHelperFactory.getDBHelper().writableDatabase

    // if user imports new DB , encryption changes and
    // we don't currently convert GPMs too..all data in the table is irrevocably lost
    fun deleteAllSavedGPMs() = getWritableDatabase().let { db ->
        db.execSQL("DELETE FROM ${GooglePasswordManager.tableName};")
        db.execSQL("DELETE FROM ${SiteEntry2GooglePasswordManager.tableName};")
    }

    fun markSavedGPMIgnored(savedGPMID: DBID) =
        getWritableDatabase().let { db ->
            db.update(GooglePasswordManager, ContentValues().apply {
                put(GooglePasswordManager.Columns.STATUS, 1)
            }, whereEq(GooglePasswordManager.Columns.ID, savedGPMID)).toLong()
        }

    fun linkSaveGPMAndSiteEntry(siteEntryID: DBID, savedGPMID: DBID) =
        getWritableDatabase().apply {
            insert(SiteEntry2GooglePasswordManager, ContentValues().apply {
                put(SiteEntry2GooglePasswordManager.Columns.PASSWORD_ID, siteEntryID)
                put(SiteEntry2GooglePasswordManager.Columns.GOOGLE_ID, savedGPMID)
            }).let { Log.w(TAG, "DBLinker $siteEntryID to $savedGPMID") }
        }

    fun fetchAllSiteEntryGPMMappings(): Map<DBID, Set<DBID>> =
        getReadableDatabase().let { db ->
            db.query(
                SiteEntry2GooglePasswordManager,
                SiteEntry2GooglePasswordManager.Columns.entries.toSet(),
            ).use { c ->
                (0 until c.count)
                    .map { _ ->
                        c.moveToNext()
                        c.getDBID(SiteEntry2GooglePasswordManager.Columns.PASSWORD_ID) to
                                c.getDBID(SiteEntry2GooglePasswordManager.Columns.GOOGLE_ID)
                    }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, values) -> values.toSet() }
            }
        }

    fun fetchAllSavedGPMsFromDB(
        gpmsFlow: MutableStateFlow<Set<SavedGPM>>? = null
    ): Set<SavedGPM> =
        getReadableDatabase().let { db ->
            db.query(
                GooglePasswordManager,
                GooglePasswordManager.Columns.entries.toSet(),
            ).use {
                it.moveToFirst()
                ArrayList<SavedGPM>().apply {
                    (0 until it.count).forEach { _ ->
                        val gpm = makeFromEncryptedStringFields(
                            it.getDBID(GooglePasswordManager.Columns.ID),
                            it.getIVCipher(GooglePasswordManager.Columns.NAME),
                            it.getIVCipher(GooglePasswordManager.Columns.URL),
                            it.getIVCipher(GooglePasswordManager.Columns.USERNAME),
                            it.getIVCipher(GooglePasswordManager.Columns.PASSWORD),
                            it.getIVCipher(GooglePasswordManager.Columns.NOTE),
                            it.getDBID(GooglePasswordManager.Columns.STATUS) == 1L,
                            it.getString(GooglePasswordManager.Columns.HASH),
                        )
                        add(gpm)
                        if (gpmsFlow != null)
                            gpmsFlow.value += gpm
                        it.moveToNext()
                    }
                }.toSet()
            }
        }

    fun deleteObsoleteSavedGPMs(delete: Set<SavedGPM>) =
        delete.forEach { savedGPM ->
            getWritableDatabase().delete(
                GooglePasswordManager,
                whereEq(GooglePasswordManager.Columns.ID, savedGPM.id!!)
            )
        }


    fun updateSavedGPMByIncomingGPM(update: Map<IncomingGPM, SavedGPM>) =
        update.forEach { (incomingGPM, savedGPM) ->
            getWritableDatabase().update(
                GooglePasswordManager,
                ContentValues().apply {
                    put(GooglePasswordManager.Columns.NAME, incomingGPM.name.encrypt())
                    put(GooglePasswordManager.Columns.URL, incomingGPM.url.encrypt())
                    put(GooglePasswordManager.Columns.USERNAME, incomingGPM.username.encrypt())
                    put(GooglePasswordManager.Columns.PASSWORD, incomingGPM.password.encrypt())
                    put(GooglePasswordManager.Columns.NOTE, incomingGPM.note.encrypt())
                    put(GooglePasswordManager.Columns.HASH, incomingGPM.hash)
                },
                whereEq(GooglePasswordManager.Columns.ID, savedGPM.id!!)
            )
        }

    fun addNewIncomingGPM(add: Set<IncomingGPM>) =
        add.forEach { incomingGPM ->
            getWritableDatabase().insert(
                GooglePasswordManager,
                ContentValues().apply {
                    put(GooglePasswordManager.Columns.ID, null) // auto increment
                    put(GooglePasswordManager.Columns.NAME, incomingGPM.name.encrypt())
                    put(GooglePasswordManager.Columns.URL, incomingGPM.url.encrypt())
                    put(GooglePasswordManager.Columns.USERNAME, incomingGPM.username.encrypt())
                    put(GooglePasswordManager.Columns.PASSWORD, incomingGPM.password.encrypt())
                    put(GooglePasswordManager.Columns.NOTE, incomingGPM.note.encrypt())
                    put(GooglePasswordManager.Columns.STATUS, 0)
                    put(GooglePasswordManager.Columns.HASH, incomingGPM.hash)
                }
            )
        }

    fun addSavedGPM(savedGPM: SavedGPM) =
        getWritableDatabase().insert(
            GooglePasswordManager,
            ContentValues().apply {
                put(GooglePasswordManager.Columns.ID, savedGPM.id) // auto increment
                put(GooglePasswordManager.Columns.NAME, savedGPM.encryptedName)
                put(GooglePasswordManager.Columns.URL, savedGPM.encryptedUrl)
                put(GooglePasswordManager.Columns.USERNAME, savedGPM.encryptedUsername)
                put(GooglePasswordManager.Columns.PASSWORD, savedGPM.encryptedPassword)
                put(GooglePasswordManager.Columns.NOTE, savedGPM.encryptedNote)
                put(GooglePasswordManager.Columns.STATUS, if (savedGPM.flaggedIgnored) 1 else 0)
                put(GooglePasswordManager.Columns.HASH, savedGPM.hash)
            }
        )
}