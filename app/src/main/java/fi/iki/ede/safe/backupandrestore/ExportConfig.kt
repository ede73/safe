package fi.iki.ede.safe.backupandrestore

import android.content.Context
import android.content.Intent
import fi.iki.ede.safe.model.Preferences

abstract class ExportConfig(currentCodedVersion: ExportVersion) {
    // IMPORTANT: If you ever introduce a breaking change, make sure to advance the version code
    val currentVersion = currentCodedVersion

    enum class ExportVersion(val version: String) {
        V1("1")
    }

    companion object {
        fun getCreateDocumentIntent(context: Context): Intent {
            // https://developer.android.com/reference/kotlin/android/content/Intent#action_create_document
            val backupDocument = Preferences.PREFERENCE_BACKUP_PATH_DEFAULT_VALUE

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(Backup.MIME_TYPE_BACKUP)
                .putExtra(
                    Intent.EXTRA_TITLE,
                    backupDocument
                )

            // I was hoping to be able to start user off from where they store the doc last time
            // (which most probably and hopefully is google drive or other cloud provider, not phone's SD CARD)
            // But modern couldn't find any way to do this in modern androids (and google drive)
            //
            // Reality ->
            // Google doesn't support android storage framework(SAF) / (ACTION_OPEN_DOCUMENT_TREE) :O
            // reported around 2021 as an issue? They closed it as obsolete, so I guess SAF is obsolete :)
            // OTOH:
            // ACTION_CREATE_DOCUMENT used to work with:
            // .setData(docUri) , but nowadays it throws..
            // Also worked sometime but no longer
            // .putExtra(Intent.EXTRA_TITLE, backupDocument) to set initial location
            // There's also (supposedly) 26+(Android8)
            // .putExtra(DocumentsContract.EXTRA_INITIAL_URI, docUri)
            // But any attempt to use any combination of those oe
            // .setDataAndType(docUri, Backup.MIME_TYPE_BACKUP) results in exception
            // One can check if intent even resolves..doesn't
            //  if (intent.resolveActivity(context.packageManager) != null) { return intent }
            return intent
        }

        fun getOpenDocumentIntent(context: Context) = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType(Backup.MIME_TYPE_BACKUP)
    }
}
