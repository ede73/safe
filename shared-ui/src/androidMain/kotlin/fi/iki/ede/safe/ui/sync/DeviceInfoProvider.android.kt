package fi.iki.ede.safe.ui.sync

import android.os.Build

actual object DevicePlatformInfo {
    actual val modelName: String
        get() {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val model = Build.MODEL
            return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        }

    actual var appVersion: String = "4.1.8"

    actual val platformName: String
        get() = "Android"
}
