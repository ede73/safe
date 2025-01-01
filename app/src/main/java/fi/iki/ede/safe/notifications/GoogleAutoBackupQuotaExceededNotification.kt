package fi.iki.ede.safe.notifications

import android.content.Context
import fi.iki.ede.safe.ui.activities.CategoryListScreen

class GoogleAutoBackupQuotaExceededNotification(
    context: Context,
    descriptionParam: String? = null
) :
    MainNotification(
        context,
        NotificationType.GOOGLE_AUTO_BACKUP_QUOTA_EXCEEDED,
        CategoryListScreen::class.java,
        descriptionParam
    ) {
    override fun setNotification(context: Context) {
        if (!isNotificationPermissionGranted(context)) return
        notify(context)
    }
}