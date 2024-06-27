package fi.iki.ede.safe.notifications

import android.content.Context

class GoogleAutoBackupNotification(context: Context, descriptionParam: String? = null) :
    MainNotification(
        context,
        NotificationType.GOOGLE_AUTO_BACKUP, descriptionParam
    ) {
    override fun setNotification(context: Context) {
        if (!isNotificationPermissionGranted(context)) return
        notify(context)
    }
}