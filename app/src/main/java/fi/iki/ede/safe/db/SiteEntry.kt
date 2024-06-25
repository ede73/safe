package fi.iki.ede.safe.db

internal object SiteEntry : Table {
    override val tableName: String
        get() = "passwords"

    enum class Columns(override val columnName: String) : TableColumns<SiteEntry> {
        SITEENTRY_ID("id"),
        CATEGORY_ID("category"),
        PASSWORD("password"),
        DESCRIPTION("description"),
        USERNAME("username"),
        WEBSITE("website"),
        NOTE("note"),
        PHOTO("photo"),
        PASSWORD_CHANGE_DATE("passwordchangeddate"),
        DELETED("deleted")
    }

    override fun create() = listOf(
        """CREATE TABLE IF NOT EXISTS $tableName (
    ${Columns.SITEENTRY_ID.columnName} INTEGER PRIMARY KEY AUTOINCREMENT,
    ${Columns.CATEGORY_ID.columnName} INTEGER NOT NULL,
    ${Columns.PASSWORD.columnName} TEXT NOT NULL,
    ${Columns.DESCRIPTION.columnName} TEXT NOT NULL,
    ${Columns.USERNAME.columnName} TEXT,
    ${Columns.WEBSITE.columnName} TEXT,
    ${Columns.NOTE.columnName} TEXT,
    ${Columns.PHOTO.columnName} TEXT,
    ${Columns.PASSWORD_CHANGE_DATE.columnName} TEXT),
    ${Columns.DELETED.columnName} INTEGER DEFAULT 0;"""
    )

    override fun drop() = listOf("DROP TABLE IF EXISTS $tableName;")
}
