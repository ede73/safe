package fi.iki.ede.safe.notifications

import android.content.Context
import fi.iki.ede.notifications.ConfiguredNotifications
import fi.iki.ede.notifications.MainNotification


object SetupNotifications {
    fun setup(context: Context) {
        compareAndSetPreferenceWithCallback(
            "notification.getAutoBackupStarts",
            fi.iki.ede.preferences.Preferences.getAutoBackupStarts()?.toLocalDate().toString()
        ) {
            MainNotification(
                context,
                ConfiguredNotifications.get("google_auto_backup_notification")
            )
                .apply { setNotification(context) }
        }
        compareAndSetPreferenceWithCallback(
            "notification.getAutoBackupQuotaExceeded",
            fi.iki.ede.preferences.Preferences.getAutoBackupQuotaExceeded()?.toLocalDate()
                .toString()
        ) {
            MainNotification(
                context,
                ConfiguredNotifications.get("google_auto_backup_quota_exceeded_notification")
            )
                .apply { setNotification(context) }
        }

        // keep nagging if there are local changes newer than backup!
        val lastBackup = fi.iki.ede.preferences.Preferences.getLastBackupTime()
        val lastModified = fi.iki.ede.preferences.Preferences.getLastModified()
        if (lastBackup == null || lastModified != null && lastModified > lastBackup) {
            MainNotification(
                context,
                ConfiguredNotifications.get("backup_notification"),
                fi.iki.ede.preferences.Preferences.getLastBackupTime()?.toLocalDate().toString()
            ).apply { setNotification(context) }
        }
    }

    private fun compareAndSetPreferenceWithCallback(
        key: String,
        value: String?,
        callback: (value: String) -> Unit
    ) = fi.iki.ede.preferences.Preferences.sharedPreferences.getString(key, null)
        .let { currentValue ->
            if (currentValue != value) {
                fi.iki.ede.preferences.Preferences.sharedPreferences.edit().putString(key, value)
                    .apply()
                if (value != null) {
                    callback(value)
                }
            }
        }
}