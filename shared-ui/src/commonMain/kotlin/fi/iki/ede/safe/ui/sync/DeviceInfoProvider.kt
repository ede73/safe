package fi.iki.ede.safe.ui.sync

import fi.iki.ede.preferences.Preferences
import kotlin.time.ExperimentalTime

expect object DevicePlatformInfo {
    val modelName: String
    var appVersion: String
    val platformName: String
}

@OptIn(ExperimentalTime::class)
object DeviceInfoProvider {
    fun getFormattedDeviceName(customNameOverride: String? = null): String {
        val customName = (customNameOverride ?: Preferences.getSyncDeviceCustomName()).trim()
        if (customName.isNotBlank()) {
            return customName
        }
        val model = DevicePlatformInfo.modelName
        return "$model (${DevicePlatformInfo.platformName})"
    }

    fun getPlatformDisplayName(): String =
        DevicePlatformInfo.platformName
}
