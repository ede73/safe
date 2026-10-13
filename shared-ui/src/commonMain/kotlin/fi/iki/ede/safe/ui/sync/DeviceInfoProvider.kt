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
        val baseName = if (customName.isNotBlank()) customName else "${DevicePlatformInfo.platformName} Safe"
        val version = DevicePlatformInfo.appVersion
        val model = DevicePlatformInfo.modelName
        return "$baseName v$version on $model"
    }

    fun getPlatformDisplayName(): String =
        DevicePlatformInfo.platformName
}
