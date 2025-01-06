package fi.iki.ede.safe.notifications

import android.content.Context
import android.util.Log
import fi.iki.ede.notifications.ConfiguredNotifications
import fi.iki.ede.notifications.MainNotification
import fi.iki.ede.preferences.Preferences


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
        Log.e("SetupNotifications", "$lastBackup < $lastModified")
        Log.e("SetupNotifications", "$lastBackup < $lastModified")
        Log.e("SetupNotifications", "$lastBackup < $lastModified")
        Log.e("SetupNotifications", "$lastBackup < $lastModified")
        if (lastBackup == null || lastModified != null && lastModified > lastBackup) {
            MainNotification(
                context,
                ConfiguredNotifications.get("backup_notification"),
                Preferences.getLastBackupTime()?.toLocalDate().toString()
            ).apply { setNotification(context) }
        }
    }

    private fun compareAndSetPreferenceWithCallback(
        key: String,
        value: String?,
        callback: (value: String) -> Unit
    ) = Preferences.sharedPreferences.getString(key, null)
        .let { currentValue ->
            if (currentValue != value) {
                Preferences.sharedPreferences.edit().putString(key, value)
                    .apply()
                if (value != null) {
                    callback(value)
                }
            }
        }
}