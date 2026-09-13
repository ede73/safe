package fi.iki.ede.notifications

import fi.iki.ede.logger.Logger
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

actual object ToastHelper {
    private const val TAG = "ToastHelper"

    actual fun showToast(message: String, duration: ToastDuration) {
        Logger.i(TAG, "Desktop Toast [$duration]: $message")
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        try {
            if (os.contains("mac")) {
                val escaped = message
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", " ")
                    .replace("\r", " ")
                Runtime.getRuntime().exec(arrayOf("osascript", "-e", "display notification \"$escaped\" with title \"Safe\""))
                return
            } else if (os.contains("linux")) {
                Runtime.getRuntime().exec(arrayOf("notify-send", "Safe", message))
                return
            }
        } catch (e: Throwable) {
            Logger.d(TAG, "Native notification command failed: ${e.message}")
        }

        try {
            if (SystemTray.isSupported()) {
                val tray = SystemTray.getSystemTray()
                var icon = tray.trayIcons.firstOrNull()
                if (icon == null) {
                    val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                    icon = TrayIcon(img, "Safe")
                    icon.isImageAutoSize = true
                    tray.add(icon)
                }
                icon.displayMessage("Safe", message, TrayIcon.MessageType.INFO)
            }
        } catch (e: Throwable) {
            Logger.d(TAG, "Could not display desktop system tray message: ${e.message}")
        }
    }
}
