package fi.iki.ede.safe.notifications

import android.app.Notification
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.activities.CategoryListScreen

enum class NotificationType(val cfg: NotificationSetup) {
    AUTO_LOCK(
        NotificationSetup(
            1,
            "autolock_notification",
            R.string.notification_lock_title,
            R.string.notification_lock_description,
            Notification.CATEGORY_SERVICE,
            CategoryListScreen::class,
            R.drawable.passicon
        )
    ),
    BACKUP_REMINDER(
        NotificationSetup(
            2,
            "backup_notification",
            R.string.notification_backup_reminder_title,
            R.string.notification_backup_reminder_description,
            Notification.CATEGORY_REMINDER,
            CategoryListScreen::class,
            R.drawable.passicon
        )
    ),
    GOOGLE_AUTO_BACKUP(
        NotificationSetup(
            3,
            "google_auto_backup_notification",
            R.string.notification_google_autobackup_title,
            R.string.notification_google_autobackup_description,
            Notification.CATEGORY_STATUS,
            CategoryListScreen::class,
            R.drawable.passicon
        )
    ),
    GOOGLE_AUTO_BACKUP_QUOTA_EXCEEDED(
        NotificationSetup(
            4,
            "google_auto_backup_quota_exceeded_notification",
            R.string.notification_google_autobackup_quota_exceeded_title,
            R.string.notification_google_autobackup_quota_exceeded_description,
            Notification.CATEGORY_ERROR,
            CategoryListScreen::class,
            R.drawable.passicon
        )
    )
}