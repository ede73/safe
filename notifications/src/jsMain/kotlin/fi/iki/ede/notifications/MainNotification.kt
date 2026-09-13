package fi.iki.ede.notifications

actual class MainNotification actual constructor(
    private val notificationConfig: NotificationSetup,
    private val descriptionParam: String?
) {
    actual fun clearNotification() {}
    actual fun setNotification(
        customSetup: ((mainNotification: MainNotification) -> Unit)?
    ) {}
    actual fun notify(
        augmentNotificationBuilder: ((Any) -> Unit)?
    ) {}
}
