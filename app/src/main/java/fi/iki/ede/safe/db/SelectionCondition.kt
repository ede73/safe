package fi.iki.ede.safe.db

class SelectionCondition(
    private val column: TableColumns<*>,
    private val singleArg: Any,
    private val comparison: String = "=",
    private val coalesce: Any? = null
) {
    fun query() =
        if (coalesce != null)
            "(IFNULL(${column.columnName}, $coalesce) $comparison ?)"
        else
            "(${column.columnName} $comparison ?)"

    fun args() = arrayOf(singleArg.toString())

    companion object {
        fun alwaysMatch() = SelectionCondition(
            column = object : TableColumns<Nothing> {
                override val columnName = "1"
            },
            singleArg = "1",
            comparison = "="
        )
    }
}