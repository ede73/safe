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
import fi.iki.ede.logger.Logger
import fi.iki.ede.preferences.Preferences
import kotlin.jvm.java
import kotlin.time.ExperimentalTime

private const val TAG = "MainNotification"

@ExperimentalTime
actual class MainNotification actual constructor(
    private val notificationConfig: NotificationSetup,
    private val descriptionParam: String?
) {
    constructor(
        context: Context,
        notificationConfig: NotificationSetup,
        descriptionParam: String? = null
    ) : this(notificationConfig, descriptionParam) {
        setNotificationsContext(context)
    }

    private val context: Context?
        get() = getNotificationsContext()

    private val mNotifyManager: NotificationManagerCompat?
        get() = context?.let { NotificationManagerCompat.from(it) }

    private var notificationBuilder: NotificationCompat.Builder? = null

    init {
        val ctx = context
        if (ctx != null) {
            createChannel(ctx)
            notificationBuilder = getNotificationBuilder(
                ctx,
                getPendingIntent(ctx, notificationConfig.activityToStartOnClick),
                ctx.getString(notificationConfig.channelDescription, descriptionParam)
            )
        } else {
            Logger.w(TAG, "Notifications context not initialized during MainNotification initialization.")
        }
    }

    actual fun clearNotification() {
        mNotifyManager?.cancel(notificationConfig.notificationID)
    }

    actual fun setNotification(
        customSetup: ((mainNotification: MainNotification) -> Unit)?
    ) {
        val ctx = context ?: return
        if (!isNotificationPermissionGranted(ctx)) return
        if (customSetup == null) {
            notify()
        } else {
            customSetup(this)
        }
    }

    @SuppressLint("MissingPermission")
    actual fun notify(
        augmentNotificationBuilder: ((Any) -> Unit)?
    ) {
        val ctx = context ?: return
        if (!isNotificationPermissionGranted(ctx)) return
        val builder = notificationBuilder ?: getNotificationBuilder(
            ctx,
            getPendingIntent(ctx, notificationConfig.activityToStartOnClick),
            ctx.getString(notificationConfig.channelDescription, descriptionParam)
        ).also { notificationBuilder = it }

        augmentNotificationBuilder?.invoke(builder)

        mNotifyManager?.notify(
            notificationConfig.notificationID,
            builder.build()
        )
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
        .setChannelId(notificationConfig.channel)
        .setSmallIcon(notificationConfig.icon)
        .setContentIntent(pendingIntent)
        .setCategory(notificationConfig.category).apply {
            if (notificationConfig.category == NotificationCompat.CATEGORY_SERVICE)
                setOngoing(true)
        }

    private fun getPendingIntent(
        context: Context,
        activityToStartOnClick: kotlin.reflect.KClass<*>
    ): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, activityToStartOnClick.java),
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun isNotificationPermissionGranted(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            isNotificationPermissionGrantedTiraMisu(context)
        else mNotifyManager?.areNotificationsEnabled() ?: true

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun isNotificationPermissionGrantedTiraMisu(context: Context) =
        (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED).also { granted ->
            Preferences.setNotificationPermissionRequired(!granted)
        }
}

fun NotificationImportance.toAndroid() = when (this) {
    NotificationImportance.Low -> NotificationManager.IMPORTANCE_LOW
    NotificationImportance.High -> NotificationManager.IMPORTANCE_HIGH
}
