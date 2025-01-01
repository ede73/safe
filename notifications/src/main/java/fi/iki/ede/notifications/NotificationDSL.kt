package fi.iki.ede.notifications

import android.app.NotificationManager
import kotlin.reflect.KClass

class NotificationDSL {
    private val notifications = mutableListOf<NotificationSetup>()

    fun notification(
        notificationID: Int,
        channel: String,
        channelName: Int,
        channelDescription: Int,
        category: String,
        type: KClass<*>,
        icon: Int,
        importance: Int = NotificationManager.IMPORTANCE_LOW
    ) {
        notifications.add(
            NotificationSetup(
                notificationID,
                channel,
                channelName,
                channelDescription,
                category,
                type,
                icon,
                importance
            )
        )
    }

    fun build(): List<NotificationSetup> = notifications
}

fun initializeNotifications(init: NotificationDSL.() -> Unit) =
    NotificationDSL().apply(init).build()
