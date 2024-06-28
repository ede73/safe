package fi.iki.ede.safe.ui.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CompletableDeferred

fun restoreOldBackupDialog(
    context: Context,
    restoringOldBackupTitle: String,
    restoreOldBackupMessage: String,
    restoreAnywayText: String,
    cancelRestoration: String,
    result: CompletableDeferred<Boolean>
) {
    AlertDialog.Builder(context)
        .setTitle(restoringOldBackupTitle)
        .setMessage(restoreOldBackupMessage)
        .setPositiveButton(restoreAnywayText) { _, _ ->
            result.complete(true)
        }
        .setNegativeButton(cancelRestoration) { _, _ ->
            result.complete(false)
        }
        .setOnDismissListener {
            // Handle the case where the dialog is dismissed without an explicit action
            result.complete(false)
        }
        .show()
}