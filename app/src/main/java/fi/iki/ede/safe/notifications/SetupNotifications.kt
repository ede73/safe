package fi.iki.ede.safe.notifications

import android.content.Context
import fi.iki.ede.safe.model.Preferences

object SetupNotifications {
    fun setup(context: Context) {
        compareAndSetPreferenceWithCallback(
            "notification.getAutoBackupStarts",
            Preferences.getAutoBackupStarts()?.toLocalDate().toString()
        ) {
            GoogleAutoBackupNotification(context, it).apply { setNotification(context) }
        }
        compareAndSetPreferenceWithCallback(
            "notification.getAutoBackupQuotaExceeded",
            Preferences.getAutoBackupQuotaExceeded()?.toLocalDate().toString()
        ) {
            GoogleAutoBackupQuotaExceededNotification(context, it)
                .apply { setNotification(context) }
        }

        // keep nagging if there are local changes newer than backup!
        val lastBackup = Preferences.getLastBackupTime()
        val lastModified = Preferences.getLastModified()
        if (lastModified != null && lastModified > lastBackup) {
            BackupNotification(
                context,
                Preferences.getLastBackupTime()?.toLocalDate().toString()
            ).apply { setNotification(context) }
        }
    }

    private fun compareAndSetPreferenceWithCallback(
        key: String,
        value: String?,
        callback: (value: String) -> Unit
    ) = Preferences.sharedPreferences.getString(key, null).let { currentValue ->
        if (currentValue != value) {
            Preferences.sharedPreferences.edit().putString(key, value).apply()
            if (value != null) {
                callback(value)
            }
        }
    }
}