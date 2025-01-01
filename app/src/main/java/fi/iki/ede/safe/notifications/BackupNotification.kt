package fi.iki.ede.safe.notifications

import android.content.Context
import fi.iki.ede.safe.ui.activities.CategoryListScreen

class BackupNotification(context: Context, descriptionParam: String? = null) :
    MainNotification(
        context,
        NotificationType.BACKUP_REMINDER,
        CategoryListScreen::class.java,
        descriptionParam,
    ) {
    override fun setNotification(context: Context) {
        if (!isNotificationPermissionGranted(context)) return
        notify(context)
    }
}