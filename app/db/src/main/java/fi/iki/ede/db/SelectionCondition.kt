package fi.iki.ede.db

class SelectionCondition(
    private val column: TableColumns<*>,
    private val singleArg: Any,
    private val comparison: String = "=",
    private val coalesce: Any? = null
) {
    private val conditions = mutableListOf<SelectionCondition>()
    private var joinCondition = ""

    init {
        conditions.add(this)
    }

    fun and(condition: SelectionCondition) = conditions.add(condition).let {
        condition.joinCondition = " AND "
        this
    }

    fun or(condition: SelectionCondition) = conditions.add(condition).let {
        condition.joinCondition = " OR "
        this
    }

    fun query(): String =
        conditions.joinToString(separator = "") { condition ->
            condition.joinCondition + (if (condition.coalesce != null)
                "(IFNULL(${condition.column.columnName}, ${condition.coalesce}) ${condition.comparison} ?)"
            else
                "(${condition.column.columnName} ${condition.comparison} ?)")
        }

    fun args() = conditions.map { condition ->
        condition.singleArg.toString()
    }.toTypedArray()

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