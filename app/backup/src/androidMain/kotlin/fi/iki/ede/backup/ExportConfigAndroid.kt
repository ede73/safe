package fi.iki.ede.backup

import android.content.Intent
import fi.iki.ede.preferences.Preferences
import kotlin.time.ExperimentalTime

@ExperimentalTime
fun ExportConfig.Companion.getCreateDocumentIntent(): Intent {
    val backupDocument = Preferences.PASSWORDSAFE_EXPORT_FILE
    return Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(BackupDatabase.MIME_TYPE_BACKUP)
        .putExtra(Intent.EXTRA_TITLE, backupDocument)
}

@ExperimentalTime
fun ExportConfig.Companion.getOpenDocumentIntent(): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT)
        .setType(BackupDatabase.MIME_TYPE_BACKUP)
