package fi.iki.ede.safe.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.activities.CategoryListScreen

class AutoLockNotification(val context: Context) {
    private val mNotifyManager: NotificationManagerCompat = NotificationManagerCompat.from(context)
    private lateinit var notificationBuilder: NotificationCompat.Builder

    init {
        createChannel(context)
    }

    fun clearNotification() {
        mNotifyManager.cancel(NOTIFICATION_ID)
    }

    fun setNotification(context: Context) {
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, CategoryListScreen::class.java),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.application_name))
            .setContentText(
                context
                    .getString(R.string.notification_lock_logged_in)
            ).setSmallIcon(R.drawable.passicon)
            .setOngoing(true)
            .setContentIntent(pi)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setProgress(100, 0, false)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            flagToRequestNotificationPermission()
            return
        }
        Preferences.setNotificationPermissionRequired(false)
        mNotifyManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * Update the existing notification progress bar. This should start with
     * progress == max and progress decreasing over time to depict time running
     * out.
     */
    fun updateProgress(max: Int, progress: Int) {
        notificationBuilder.setProgress(max, progress, false)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            flagToRequestNotificationPermission()
            return
        }
        Preferences.setNotificationPermissionRequired(false)
        mNotifyManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun createChannel(context: Context) {
        val name = context.getString(R.string.notification_lock_title)
        val description = context.getString(R.string.notification_lock_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
        mChannel.enableLights(false)
        mChannel.enableVibration(false)
        mChannel.description = description
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(mChannel)
    }

    // Alas permissions can ONLY be requested from Activity
    // And this is running as services, so we need to route the request
    // And pop up the question once activity is opened (let's say CategoryList)
    private fun flagToRequestNotificationPermission() {
        Preferences.setNotificationPermissionRequired(true)
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "safe"
    }
}