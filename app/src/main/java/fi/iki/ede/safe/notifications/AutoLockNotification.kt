package fi.iki.ede.safe.notifications

import android.content.Context
import fi.iki.ede.safe.ui.activities.CategoryListScreen

class AutoLockNotification(context: Context) :
    MainNotification(context, NotificationType.AUTO_LOCK, CategoryListScreen::class.java) {

    override fun setNotification(context: Context) {
        if (!isNotificationPermissionGranted(context)) return
        notify(context) {
            it.setProgress(100, 0, false)
        }
    }

    /**
     * Update the existing notification progress bar. This should start with
     * progress == max and progress decreasing over time to depict time running
     * out.
     */
    fun updateProgress(context: Context, max: Int, progress: Int) {
        if (!isNotificationPermissionGranted(context)) return
        notify(context) {
            it.setProgress(max, progress, false)
        }
    }
}