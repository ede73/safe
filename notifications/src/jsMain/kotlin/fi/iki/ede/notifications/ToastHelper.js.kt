package fi.iki.ede.notifications

import fi.iki.ede.logger.Logger

actual object ToastHelper {
    actual fun showToast(message: String, duration: ToastDuration) {
        Logger.i("ToastHelper", "Toast: $message")
    }
}
