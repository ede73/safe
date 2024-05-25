package fi.iki.ede.safe.backupandrestore

abstract class ExportConfig(currentCodedVersion: ExportVersion) {
    // IMPORTANT: If you ever introduce a breaking change, make sure to advance the version code
    val currentVersion = currentCodedVersion

    enum class ExportVersion(val version: String) {
        V1("1")
    }
}
