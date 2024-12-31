package fi.iki.ede.safe.notifications

import android.content.Context
import fi.iki.ede.preferences.Preferences

object SetupNotifications {
    fun setup(context: Context) {
        compareAndSetPreferenceWithCallback(
            "notification.getAutoBackupStarts",
            fi.iki.ede.preferences.Preferences.getAutoBackupStarts()?.toLocalDate().toString()
        ) {
            GoogleAutoBackupNotification(context, it).apply { setNotification(context) }
        }
        compareAndSetPreferenceWithCallback(
            "notification.getAutoBackupQuotaExceeded",
            fi.iki.ede.preferences.Preferences.getAutoBackupQuotaExceeded()?.toLocalDate()
                .toString()
        ) {
            GoogleAutoBackupQuotaExceededNotification(context, it)
                .apply { setNotification(context) }
        }

        // keep nagging if there are local changes newer than backup!
        val lastBackup = fi.iki.ede.preferences.Preferences.getLastBackupTime()
        val lastModified = fi.iki.ede.preferences.Preferences.getLastModified()
        if (lastBackup == null || lastModified != null && lastModified > lastBackup) {
            BackupNotification(
                context,
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