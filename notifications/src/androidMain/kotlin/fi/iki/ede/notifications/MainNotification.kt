package fi.iki.ede.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fi.iki.ede.preferences.Preferences
import kotlin.time.ExperimentalTime

@ExperimentalTime
actual class MainNotification(
    context: Context,
    private val notificationConfig: NotificationSetup,
    private val descriptionParam: String? = null
) {

    private val mNotifyManager: NotificationManagerCompat = NotificationManagerCompat.from(context)
    private val notificationBuilder: NotificationCompat.Builder

    init {
        createChannel(context)
        notificationBuilder = getNotificationBuilder(
            context,
            getPendingIntent(context, notificationConfig.activityToStartOnClick),
            context.getString(notificationConfig.channelDescription, descriptionParam)
        ).apply {
            // Extension point (just make public)
            augmentNotificationBuilder(this)
        }
    }

    actual fun clearNotification() {
        mNotifyManager.cancel(notificationConfig.notificationID)
    }

    actual fun setNotification(
        getContext: () -> Any,
        customSetup: ((mainNotification: MainNotification) -> Unit)?
    ) {
        val context = getContext() as Context
        if (!isNotificationPermissionGranted(context)) return
        if (customSetup == null) {
            notify({ context })
        } else {
            customSetup(this)
        }
    }

    @SuppressLint("MissingPermission", "Broken linter")
    actual fun notify(
        getContext: () -> Any,
        augmentNotificationBuilder: (Any) -> Unit
    ) {
        val context = getContext() as Context
        if (!isNotificationPermissionGranted(context)) return
        mNotifyManager.notify(
            notificationConfig.notificationID,
            notificationBuilder.apply {
                augmentNotificationBuilder(this)
            }.build()
        )
    }


    private fun augmentNotificationBuilder(augmentNotificationBuilder: NotificationCompat.Builder) {
    }

    private fun getNotificationManager(context: Context) =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    private fun createChannel(context: Context) {
        val mChannel = NotificationChannel(
            notificationConfig.channel,
            context.getString(notificationConfig.channelName),
            notificationConfig.importance.toAndroid()
        )
        mChannel.enableLights(false)
        mChannel.enableVibration(false)
        mChannel.description = context.getString(notificationConfig.channelDescription)
        getNotificationManager(context).createNotificationChannel(mChannel)
    }

    private fun getNotificationBuilder(
        context: Context,
        pendingIntent: PendingIntent,
        content: String
    ) = NotificationCompat.Builder(context, notificationConfig.channel)
        .setContentTitle(context.getString(notificationConfig.channelName))
        .setContentText(content)
        .setChannelId(notificationConfig.channel) // TODO: REMOVE
        .setSmallIcon(notificationConfig.icon)
        .setContentIntent(pendingIntent)
        .setCategory(notificationConfig.category).apply {
            if (notificationConfig.category == NotificationCompat.CATEGORY_SERVICE)
                setOngoing(true)
        }

    private fun getPendingIntent(
        context: Context,
        activityToStartOnClick: Class<*>
    ): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, activityToStartOnClick),
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun isNotificationPermissionGranted(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            isNotificationPermissionGrantedTiraMisu(context)
        else mNotifyManager.areNotificationsEnabled()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun isNotificationPermissionGrantedTiraMisu(context: Context) =
        (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED).also {
            Preferences.setNotificationPermissionRequired(!it)
            if (!it) flagToRequestNotificationPermission()
        }

    private fun flagToRequestNotificationPermission() {
        Preferences.setNotificationPermissionRequired(true)
    }
}


fun NotificationImportance.toAndroid() = when (this) {
    NotificationImportance.Low -> NotificationManager.IMPORTANCE_LOW
    NotificationImportance.High -> NotificationManager.IMPORTANCE_HIGH
}
