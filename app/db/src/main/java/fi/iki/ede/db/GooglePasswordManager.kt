package fi.iki.ede.db

object GooglePasswordManager : Table {
    override val tableName: String
        get() = "googlepasswords"

    // if you EVER alter this, copy this as hardcoded string to onUpgrade above
    override fun create() = listOf(
        """CREATE TABLE IF NOT EXISTS $tableName (
    ${Columns.ID.columnName} INTEGER PRIMARY KEY AUTOINCREMENT,
    ${Columns.NAME.columnName} TEXT NOT NULL,
    ${Columns.URL.columnName} TEXT NOT NULL,
    ${Columns.USERNAME.columnName} TEXT NOT NULL,
    ${Columns.PASSWORD.columnName} TEXT NOT NULL,
    ${Columns.NOTE.columnName} TEXT,
    ${Columns.HASH.columnName} TEXT,
    ${Columns.STATUS.columnName} INTEGER);"""
    )

    override fun drop() = listOf("DROP TABLE IF EXISTS $tableName;")

    enum class Columns(override val columnName: String) :
        TableColumns<GooglePasswordManager> {
        ID("id"),
        NAME("name"),
        URL("url"),
        USERNAME("username"),
        PASSWORD("password"),
        NOTE("note"),
        HASH("hash"),
        STATUS("status"),
    }
}
