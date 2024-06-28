package fi.iki.ede.safe.db

internal object SiteEntry2GooglePasswordManager : Table {
    override val tableName: String
        get() = "password2googlepasswords"

    // if you EVER alter this, copy this as hardcoded string to onUpgrade above
    override fun create() = listOf(
        """CREATE TABLE IF NOT EXISTS password2googlepasswords (
    password_id INTEGER,
    gpm_id INTEGER,
    PRIMARY KEY (password_id, gpm_id),
    FOREIGN KEY (password_id) REFERENCES passwords(id) ON DELETE CASCADE,
    FOREIGN KEY (gpm_id) REFERENCES googlepasswords(id) ON DELETE CASCADE);"""
    )

    override fun drop() = listOf("DROP TABLE IF EXISTS ${tableName};")

    enum class Columns(override val columnName: String) :
        TableColumns<SiteEntry2GooglePasswordManager> {
        PASSWORD_ID("password_id"),
        GOOGLE_ID("gpm_id"),
    }
}