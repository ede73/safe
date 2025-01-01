package fi.iki.ede.safe.notifications

import android.content.Context
import fi.iki.ede.safe.ui.activities.CategoryListScreen

class GoogleAutoBackupNotification(context: Context, descriptionParam: String? = null) :
    MainNotification(
        context,
        NotificationType.GOOGLE_AUTO_BACKUP, CategoryListScreen::class.java,
        descriptionParam
    ) {
    override fun setNotification(context: Context) {
        if (!isNotificationPermissionGranted(context)) return
        notify(context)
    }
}