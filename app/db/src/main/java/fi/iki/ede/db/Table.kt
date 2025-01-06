package fi.iki.ede.db

interface Table {
    val tableName: String
    fun create(): List<String>
    fun drop(): List<String>
}
