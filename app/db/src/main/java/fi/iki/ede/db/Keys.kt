package fi.iki.ede.db

object Keys : Table {
    override val tableName: String
        get() = "keys"

    // if you EVER alter this, copy this as hardcoded string to onUpgrade above
    override fun create() = listOf(
        """CREATE TABLE IF NOT EXISTS $tableName (
    ${Columns.ENCRYPTED_KEY.columnName} TEXT NOT NULL,
    ${Columns.SALT.columnName} TEXT NOT NULL);"""
    )

    override fun drop() = listOf("DROP TABLE IF EXISTS $tableName;")

    enum class Columns(override val columnName: String) : TableColumns<Keys> {
        ENCRYPTED_KEY("encryptedkey"),
        SALT("salt"),
    }
}