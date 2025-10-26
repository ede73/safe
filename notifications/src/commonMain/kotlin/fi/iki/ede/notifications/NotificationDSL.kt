package fi.iki.ede.notifications

object ConfiguredNotifications {
    var notifications: List<NotificationSetup> = emptyList()

    // TODO: Remove (move to configured)
    fun get(key: String) = notifications.find { it.channel == key }!!
}

class NotificationDSL {
    private val notifications = mutableListOf<NotificationSetup>()

    fun notification(
        notificationID: Int,
        channel: String,
        channelName: Int,
        channelDescription: Int,
        category: String,
        activityToStartOnClick: Class<* >,
        icon: Int,
        importance: NotificationImportance = NotificationImportance.Low
    ) {
        notifications.add(
            NotificationSetup(
                notificationID,
                channel,
                channelName,
                channelDescription,
                category,
                activityToStartOnClick,
                icon,
                importance
            )
        )
    }

    fun build(): List<NotificationSetup> = notifications
}

fun initializeNotifications(init: NotificationDSL.() -> Unit) =
    NotificationDSL().apply(init).build()
