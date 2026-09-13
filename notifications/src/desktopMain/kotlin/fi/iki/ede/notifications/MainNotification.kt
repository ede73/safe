package fi.iki.ede.notifications

import fi.iki.ede.logger.Logger
import java.awt.SystemTray
import java.awt.TrayIcon

private const val TAG = "MainNotification"

actual class MainNotification actual constructor(
    private val notificationConfig: NotificationSetup,
    private val descriptionParam: String?
) {
    actual fun clearNotification() {
        Logger.d(TAG, "Desktop clearNotification: ${notificationConfig.notificationID}")
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
        val title = notificationConfig.channel
        val body = descriptionParam ?: ""
        Logger.i(TAG, "Desktop notify: $title ($body)")

        val os = System.getProperty("os.name")?.lowercase() ?: ""
        try {
            if (os.contains("mac")) {
                val escapedTitle = title
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", " ")
                    .replace("\r", " ")
                val escapedBody = body
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", " ")
                    .replace("\r", " ")
                Runtime.getRuntime().exec(arrayOf("osascript", "-e", "display notification \"$escapedBody\" with title \"$escapedTitle\""))
                return
            } else if (os.contains("linux")) {
                Runtime.getRuntime().exec(arrayOf("notify-send", title, body))
                return
            }
        } catch (e: Throwable) {
            Logger.d(TAG, "Desktop native notification failed: ${e.message}")
        }

        try {
            if (SystemTray.isSupported()) {
                val tray = SystemTray.getSystemTray()
                var icon = tray.trayIcons.firstOrNull()
                if (icon == null) {
                    val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    icon = TrayIcon(img, "Safe")
                    icon.isImageAutoSize = true
                    tray.add(icon)
                }
                icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
            }
        } catch (e: Throwable) {
            Logger.d(TAG, "Desktop notification failed: ${e.message}")
        }
    }
}
