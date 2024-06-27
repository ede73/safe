package fi.iki.ede.safe.notifications

import android.content.Context
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.safe.model.Preferences
import java.time.ZonedDateTime

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

        val lastBackup = Preferences.getLastBackupTime()
        if (lastBackup != null && 5 > DateUtils.getPeriodBetweenDates(
                ZonedDateTime.now(),
                lastBackup
            ).days
        ) {
            compareAndSetPreferenceWithCallback(
                "notification.getLastBackupTime",
                Preferences.getLastBackupTime()?.toLocalDate().toString()
            ) {
                BackupNotification(context, it).apply { setNotification(context) }
            }
        } else if (lastBackup == null) {
            compareAndSetPreferenceWithCallback(
                "notification.getLastBackupTime",
                // TODO: translate NEVER
                "???!"
            ) {
                BackupNotification(context, it).apply { setNotification(context) }
            }
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