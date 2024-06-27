package fi.iki.ede.safe.notifications

import android.app.Notification
import android.app.NotificationManager
import fi.iki.ede.safe.R

data class NotificationSetup(
    val notificationID: Int,
    val channel: String,
    val channelName: Int,
    val channelDescription: Int,
    val category: String,
    val importance: Int = NotificationManager.IMPORTANCE_LOW
)

enum class NotificationType(val cfg: NotificationSetup) {
    AUTO_LOCK(
        NotificationSetup(
            1,
            "autolock_notification",
            R.string.notification_lock_title,
            R.string.notification_lock_description,
            Notification.CATEGORY_SERVICE
        )
    ),
    BACKUP_REMINDER(
        NotificationSetup(
            2,
            "backup_notification",
            R.string.notification_backup_reminder_title,
            R.string.notification_backup_reminder_description,
            Notification.CATEGORY_REMINDER
        )
    ),
    GOOGLE_AUTO_BACKUP(
        NotificationSetup(
            3,
            "google_auto_backup_notification",
            R.string.notification_google_autobackup_title,
            R.string.notification_google_autobackup_description,
            Notification.CATEGORY_STATUS
        )
    ),
    GOOGLE_AUTO_BACKUP_QUOTA_EXCEEDED(
        NotificationSetup(
            3,
            "google_auto_backup_quota_exteed_notification",
            R.string.notification_google_autobackup_quota_exceeded_title,
            R.string.notification_google_autobackup_quota_exceeded_description,
            Notification.CATEGORY_ERROR
        )
    )
}