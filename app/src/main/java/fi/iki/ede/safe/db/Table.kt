package fi.iki.ede.safe.db

internal interface Table {
    val tableName: String
    fun create(): List<String>
    fun drop(): List<String>
}
