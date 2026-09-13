package fi.iki.ede.notifications

import fi.iki.ede.logger.Logger

private const val TAG = "MainNotification"

actual class MainNotification actual constructor(
    private val notificationConfig: NotificationSetup,
    private val descriptionParam: String?
) {
    actual fun clearNotification() {
        Logger.d(TAG, "iOS clearNotification: ${notificationConfig.notificationID}")
    }

    actual fun setNotification(
        customSetup: ((mainNotification: MainNotification) -> Unit)?
    ) {
        if (customSetup == null) {
            notify()
        } else {
            customSetup(this)
        }
    }

    actual fun notify(
        augmentNotificationBuilder: ((Any) -> Unit)?
    ) {
        Logger.i(TAG, "iOS notify: ${notificationConfig.channel} ($descriptionParam)")
    }
}
