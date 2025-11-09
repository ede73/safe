package fi.iki.ede.backup

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.BackupManager
import android.app.backup.FullBackupDataOutput
import android.content.Context
import android.os.ParcelFileDescriptor
import fi.iki.ede.logger.Logger
import fi.iki.ede.preferences.Preferences
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.time.ExperimentalTime

const val TAG = "MyBackupAgent"


@ExperimentalTime
class MyBackupAgent : BackupAgentHelper() {

    override fun onCreate() {
        super.onCreate()
        Preferences.initialize(this.applicationContext)
        Logger.e(TAG, "onCreate")
    }

    private fun log(message: String) {
        Logger.e(TAG, message)
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
            (context.filesDir.absolutePath.toPath() / RESTORE_MARK).let { path ->
                if (!FileSystem.SYSTEM.exists(path)) {
                    FileSystem.SYSTEM.write(path) {} // creates empty file
                }
            }
        }

        fun haveRestoreMark(context: Context) =
            runCatching {
                (context.filesDir.absolutePath.toPath() / RESTORE_MARK).let {
                    FileSystem.SYSTEM.exists(
                        it
                    )
                }
            }.getOrDefault(false)

        fun removeRestoreMark(context: Context) {
            (context.filesDir.absolutePath.toPath() / RESTORE_MARK).let { path ->
                if (FileSystem.SYSTEM.exists(path)) {
                    FileSystem.SYSTEM.delete(path)
                }
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