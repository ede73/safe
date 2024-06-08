package fi.iki.ede.safe.db

internal object GooglePasswordManager : Table {
    override val tableName: String
        get() = "googlepasswords"

    // if you EVER alter this, copy this as hardcoded string to onUpgrade above
    override fun create() = listOf(
        """CREATE TABLE IF NOT EXISTS $tableName (
    ${Columns.ID.columnName} INTEGER PRIMARY KEY AUTOINCREMENT,
    ${Columns.NAME.columnName} TEXT NOT NULL,
    ${Columns.URL.columnName} TEXT NOT NULL,
    ${Columns.PASSWORD.columnName} TEXT NOT NULL,
    ${Columns.NOTE.columnName} TEXT,
    ${Columns.HASH.columnName} TEXT,
    ${Columns.STATUS.columnName} INTEGER);"""
    )

    override fun drop() = listOf("DROP TABLE IF EXISTS ${tableName};")

    enum class Columns(override val columnName: String) : TableColumns<Keys> {
        ID("id"),
        NAME("name"),
        URL("url"),
        PASSWORD("password"),
        NOTE("note"),
        HASH("hash"),
        STATUS("status"),
    }
}
