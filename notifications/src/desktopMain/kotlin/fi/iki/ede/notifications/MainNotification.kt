package fi.iki.ede.notifications

actual class MainNotification {
    actual fun clearNotification() {}
    actual fun setNotification(
        getContext: () -> Any,
        customSetup: ((mainNotification: MainNotification) -> Unit)?
    ) {}
    actual fun notify(
        getContext: () -> Any,
        augmentNotificationBuilder: (Any) -> Unit
    ) {}
}
