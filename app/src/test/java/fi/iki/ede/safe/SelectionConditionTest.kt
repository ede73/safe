package fi.iki.ede.safe

import fi.iki.ede.db.Keys
import fi.iki.ede.db.SelectionCondition
import fi.iki.ede.db.Table
import fi.iki.ede.db.TableColumns
import fi.iki.ede.db.whereEq
import fi.iki.ede.db.whereNot
import fi.iki.ede.db.whereNullOr0
import junit.framework.TestCase.assertEquals
import org.junit.Test

class SelectionConditionTest {

    // naive! no string encapsulation
    // but we're testing SelectionCondition functionality, not SQLIte statement forming
    private fun sqlize(query: SelectionCondition) = query.let {
        var queryString = it.query()
        it.args().forEach { arg ->
            queryString = queryString.replaceFirst("?", arg)
        }
        queryString
    }

    @Test
    fun alwaysMatchTest() {
        sqlize(SelectionCondition.alwaysMatch())
        assertEquals("(1 = ?)", SelectionCondition.alwaysMatch().query())
    }

    @Test
    fun whereEqTest() {
        assertEquals("(string = foo)", sqlize(whereEq(TestTable.Columns.STRING, "foo")))
        assertEquals("(integer = 1)", sqlize(whereEq(TestTable.Columns.INTEGER, "1")))
    }

    @Test
    fun whereNotTest() {
        assertEquals("(string <> foo)", sqlize(whereNot(TestTable.Columns.STRING, "foo")))
        assertEquals("(integer <> 1)", sqlize(whereNot(TestTable.Columns.INTEGER, "1")))
    }

    @Test
    fun whereNullOr0Test() {
        assertEquals(
            "(IFNULL(string, '') = foo)",
            sqlize(whereNullOr0(TestTable.Columns.STRING, "foo", "''")),
        )
        assertEquals(
            "(IFNULL(integer, 0) = 1)",
            sqlize(whereNullOr0(TestTable.Columns.INTEGER, "1", "0")),
        )
    }

    @Test
    fun complexConditions() {
        val eq = whereEq(TestTable.Columns.STRING, "foo")
        val ne = whereNot(TestTable.Columns.STRING, "foo")
        val coalesce = whereNullOr0(TestTable.Columns.STRING, "foo", "''")
        val query = eq.and(ne).or(coalesce)
        assertEquals(
            "(string = foo) AND (string <> foo) OR (IFNULL(string, '') = foo)",
            sqlize(query),
        )
        assertEquals(
            "(string = ?) AND (string <> ?) OR (IFNULL(string, '') = ?)",
            query.query(),
        )
    }
}

internal object TestTable : Table {
    override val tableName: String
        get() = "testTable"

    // if you EVER alter this, copy this as hardcoded string to onUpgrade above
    override fun create() = listOf(
        """CREATE TABLE IF NOT EXISTS $tableName (
    ${Columns.STRING.columnName} TEXT NOT NULL,
    ${Columns.INTEGER.columnName} INTEGER NOT NULL);"""
    )

    override fun drop() = listOf("DROP TABLE IF EXISTS ${tableName};")

    enum class Columns(override val columnName: String) : TableColumns<Keys> {
        STRING("string"),
        INTEGER("integer"),
    }
}