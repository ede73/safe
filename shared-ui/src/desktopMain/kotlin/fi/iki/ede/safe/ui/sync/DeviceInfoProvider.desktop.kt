package fi.iki.ede.safe.ui.sync

import java.net.InetAddress

actual object DevicePlatformInfo {
    actual val modelName: String
        get() {
            val os = System.getProperty("os.name") ?: "Desktop"
            val host = try {
                InetAddress.getLocalHost().hostName.takeWhile { it != '.' }
            } catch (_: Exception) {
                ""
            }
            return if (host.isNotBlank()) "$os ($host)" else os
        }

    actual var appVersion: String = "1.0.0"

    actual val platformName: String
        get() = "Desktop"
}
