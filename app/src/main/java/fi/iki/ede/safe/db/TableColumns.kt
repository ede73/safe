package fi.iki.ede.safe.db

internal interface TableColumns<T : Table> {
    val columnName: String
}
