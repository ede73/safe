package fi.iki.ede.safe.db

interface Table {
    val tableName: String
    fun create(): List<String>
    fun drop(): List<String>
}
