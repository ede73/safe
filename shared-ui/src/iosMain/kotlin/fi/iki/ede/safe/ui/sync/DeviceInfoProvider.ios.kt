package fi.iki.ede.safe.ui.sync

import platform.UIKit.UIDevice
import platform.Foundation.NSBundle

actual object DevicePlatformInfo {
    actual val modelName: String
        get() = UIDevice.currentDevice.name.ifBlank { UIDevice.currentDevice.model }

    actual var appVersion: String =
        (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String) ?: "1.0"

    actual val platformName: String
        get() = "iOS"
}
