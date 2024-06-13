package fi.iki.ede.safe.db

internal object Password2GooglePasswordManager : Table {
    override val tableName: String
        get() = "password2googlepasswords"

    // if you EVER alter this, copy this as hardcoded string to onUpgrade above
    override fun create() = listOf(
        """CREATE TABLE IF NOT EXISTS $tableName (
    ${Columns.PASSWORD_ID.columnName} INTEGER,
    ${Columns.GOOGLE_ID.columnName} INTEGER,
    PRIMARY KEY (${Columns.PASSWORD_ID.columnName}, ${Columns.GOOGLE_ID.columnName}),
    FOREIGN KEY (${Columns.PASSWORD_ID.columnName}) REFERENCES ${Password.tableName}(${Password.Columns.PWD_ID.columnName}) ON DELETE CASCADE,
    FOREIGN KEY (${Columns.GOOGLE_ID.columnName}) REFERENCES ${GooglePasswordManager.tableName}(${GooglePasswordManager.Columns.ID.columnName}) ON DELETE RESTRICT);"""
    )

    override fun drop() = listOf("DROP TABLE IF EXISTS ${tableName};")

    enum class Columns(override val columnName: String) :
        TableColumns<Password2GooglePasswordManager> {
        PASSWORD_ID("password_id"),
        GOOGLE_ID("gpm_id"),
    }
}