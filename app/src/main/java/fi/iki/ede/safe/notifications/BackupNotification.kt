package fi.iki.ede.safe.notifications

import android.content.Context

class BackupNotification(
    context: Context,
    descriptionParam: String? = null
) : MainNotification(
    context,
    NotificationType.BACKUP_REMINDER,
    descriptionParam,
)