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
import kotlin.reflect.KClass

class MainNotification(
    context: Context,
    private val notificationConfig: NotificationSetup,
    descriptionParam: String? = null,
) {
    private val mNotifyManager: NotificationManagerCompat = NotificationManagerCompat.from(context)
    private val notificationBuilder: NotificationCompat.Builder

    init {
        createChannel(context)
        notificationBuilder = getNotificationBuilder(
            context,
            getPendingIntent(context, notificationConfig.type),
            context.getString(notificationConfig.channelDescription, descriptionParam)
        ).apply {
            // Extension point (just make public)
            augmentNotificationBuilder(this)
        }
    }

    private fun augmentNotificationBuilder(augmentNotificationBuilder: NotificationCompat.Builder) {
    }

    fun clearNotification() {
        mNotifyManager.cancel(notificationConfig.notificationID)
    }

    private fun getNotificationManager(context: Context) =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    private fun createChannel(context: Context) {
        val mChannel = NotificationChannel(
            notificationConfig.channel,
            context.getString(notificationConfig.channelName),
            notificationConfig.importance
        )
        mChannel.enableLights(false)
        mChannel.enableVibration(false)
        mChannel.description = context.getString(notificationConfig.channelDescription)
        getNotificationManager(context).createNotificationChannel(mChannel)
    }

    private fun getNotificationBuilder(context: Context, pi: PendingIntent, content: String) =
        NotificationCompat.Builder(context, notificationConfig.channel)
            .setContentTitle(context.getString(notificationConfig.channelName))
            .setContentText(content)
            .setSmallIcon(notificationConfig.icon)
            .setContentIntent(pi)
            .setCategory(notificationConfig.category).apply {
                if (notificationConfig.category == NotificationCompat.CATEGORY_SERVICE)
                    setOngoing(true)
            }

    private fun getPendingIntent(
        context: Context,
        type: KClass<*>,
    ): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, type::class.java),
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun isNotificationPermissionGranted(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            isNotificationPermissionGrantedTiraMisu(context)
        else NotificationManagerCompat.from(context).areNotificationsEnabled()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun isNotificationPermissionGrantedTiraMisu(context: Context) =
        (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED).also {
            Preferences.setNotificationPermissionRequired(!it)
            if (!it) flagToRequestNotificationPermission()
        }

    fun setNotification(
        context: Context,
        customSetup: ((mainNotification: MainNotification) -> Unit)? = null
    ) {
        if (!isNotificationPermissionGranted(context)) return
        if (customSetup == null) {
            notify(context)
        } else {
            customSetup(this)
        }
    }

    @SuppressLint("MissingPermission", "Broken linter")
    fun notify(
        context: Context,
        augmentNotificationBuilder: (NotificationCompat.Builder) -> Unit = {}
    ) {
        if (!isNotificationPermissionGranted(context)) return
        mNotifyManager.notify(
            notificationConfig.notificationID,
            notificationBuilder.apply {
                augmentNotificationBuilder(this)
            }.build()
        )
    }

    // Alas permissions can ONLY be requested from Activity
    // And this is running as services, so we need to route the request
    // And pop up the question once activity is opened (let's say CategoryList)
    private fun flagToRequestNotificationPermission() {
        Preferences.setNotificationPermissionRequired(true)
    }
}