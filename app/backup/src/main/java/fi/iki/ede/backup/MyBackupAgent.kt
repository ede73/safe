package fi.iki.ede.backup

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.BackupManager
import android.app.backup.FullBackupDataOutput
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import fi.iki.ede.preferences.Preferences
import java.io.File

const val TAG = "MyBackupAgent"

class MyBackupAgent : BackupAgentHelper() {

    override fun onCreate() {
        super.onCreate()
        Preferences.initialize(this.applicationContext)
        Log.e(TAG, "onCreate")
    }

    private fun log(message: String) {
        Log.e(TAG, message)
    }

    override fun onFullBackup(data: FullBackupDataOutput?) {
        log("onFullBackup")
        Preferences.autoBackupStarts()
        super.onFullBackup(data)
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?, data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
        log("onBackup")
        Preferences.autoBackupStarts()
        super.onBackup(oldState, data, newState)
    }

    override fun onRestore(
        data: BackupDataInput?, appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
        log("onRestore")
        Preferences.autoBackupRestoreStarts()
        super.onRestore(data, appVersionCode, newState)
    }

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Long,
        newState: ParcelFileDescriptor?
    ) {
        log("onRestore")
        Preferences.autoBackupRestoreStarts()
        super.onRestore(data, appVersionCode, newState)
    }

    override fun onRestoreFinished() {
        log("onRestoreFinished")
        Preferences.autoBackupRestoreFinished()
        markRestored(applicationContext)
        super.onRestoreFinished()
    }

    override fun onQuotaExceeded(backupDataBytes: Long, quotaBytes: Long) {
        log("onQuotaExceeded")
        Preferences.autoBackupQuotaExceeded()
        super.onQuotaExceeded(backupDataBytes, quotaBytes)
    }

    companion object {
        private const val RESTORE_MARK = ".restored"
        fun markRestored(context: Context) {
            val restored = File(context.filesDir, RESTORE_MARK)
            restored.createNewFile()
        }

        fun haveRestoreMark(context: Context) =
            File(context.filesDir, RESTORE_MARK).exists()

        fun removeRestoreMark(context: Context) {
            val restored = File(context.filesDir, RESTORE_MARK)
            if (restored.exists()) {
                restored.delete()
            }
        }
    }
}

// Method to request a backup
fun requestBackup(context: Context) {
    // Instantiate a BackupManager
    val backupManager = BackupManager(context)

    // Request a backup
    backupManager.dataChanged()
}