package fi.iki.ede.safe.notifications

import android.content.Context

class GoogleAutoBackupQuotaExceededNotification(
    context: Context,
    descriptionParam: String? = null
) :
    MainNotification(
        context,
        NotificationType.GOOGLE_AUTO_BACKUP_QUOTA_EXCEEDED, descriptionParam
    ) {
    override fun setNotification(context: Context) {
        if (!isNotificationPermissionGranted(context)) return
        notify(context)
    }
}