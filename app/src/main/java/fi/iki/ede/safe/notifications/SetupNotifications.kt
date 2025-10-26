package fi.iki.ede.safe.notifications

import android.content.Context
import androidx.core.content.edit
import fi.iki.ede.dateutils.toLocalDate
import fi.iki.ede.logger.Logger
import fi.iki.ede.notifications.ConfiguredNotifications
import fi.iki.ede.notifications.MainNotification
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.ui.utilities.setBackupDueIconEnabled


object SetupNotifications {
    fun setup(context: Context) {
        compareAndSetPreferenceWithCallback(
            "notification.getAutoBackupStarts",
            Preferences.getAutoBackupStarts()?.toLocalDate().toString()
        ) {
            MainNotification(
                context,
                ConfiguredNotifications.get("google_auto_backup_notification")
            )
                .apply { setNotification(context) }
        }
        compareAndSetPreferenceWithCallback(
            "notification.getAutoBackupQuotaExceeded",
            Preferences.getAutoBackupQuotaExceeded()?.toLocalDate()
                .toString()
        ) {
            MainNotification(
                context,
                ConfiguredNotifications.get("google_auto_backup_quota_exceeded_notification")
            )
                .apply { setNotification(context) }
        }

        // keep nagging if there are local changes newer than backup!
        val lastBackup = Preferences.getLastBackupTime()
        val lastModified = Preferences.getLastModified()
        Logger.e("SetupNotifications", "lastBackup < lastModified $lastBackup < $lastModified")
        try {
            if (lastBackup == null || lastModified != null && lastModified > lastBackup) {
                setBackupDueIconEnabled(context, true)
                MainNotification(
                    context,
                    ConfiguredNotifications.get("backup_notification"),
                    Preferences.getLastBackupTime()?.toLocalDate().toString()
                ).apply { setNotification(context) }
            }
        } catch (e: Exception) {
            Logger.e("SetupNotifications", "error $e")
            throw (e)
        }
    }

    private fun compareAndSetPreferenceWithCallback(
        key: String,
        value: String?,
        callback: (value: String) -> Unit
    ) = Preferences.sharedPreferences.getString(key, null)
        .let { currentValue ->
            if (currentValue != value) {
                Preferences.sharedPreferences.edit(commit = true) {
                    putString(key, value)
                }
                if (value != null) {
                    callback(value)
                }
            }
        }
}