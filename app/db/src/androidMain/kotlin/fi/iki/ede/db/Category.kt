package fi.iki.ede.db

internal object Category : Table {
    override val tableName: String
        get() = "categories"

    override fun create() = listOf(
        """CREATE TABLE IF NOT EXISTS $tableName (
    ${Columns.CAT_ID.columnName} INTEGER PRIMARY KEY AUTOINCREMENT,
    ${Columns.NAME.columnName} TEXT NOT NULL);"""
    )

    override fun drop() = listOf("DROP TABLE IF EXISTS $tableName;")

    enum class Columns(override val columnName: String) : TableColumns<Category> {
        CAT_ID("id"),
        NAME("name"),
    }
}

