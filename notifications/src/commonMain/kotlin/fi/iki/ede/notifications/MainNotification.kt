package fi.iki.ede.notifications

expect class MainNotification(
    notificationConfig: NotificationSetup,
    descriptionParam: String? = null
) {
    fun clearNotification()
    fun setNotification(
        customSetup: ((mainNotification: MainNotification) -> Unit)? = null
    )
    fun notify(
        augmentNotificationBuilder: ((Any) -> Unit)? = null
    )
}
