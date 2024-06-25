package fi.iki.ede.safe

import fi.iki.ede.safe.db.Keys
import fi.iki.ede.safe.db.SelectionCondition
import fi.iki.ede.safe.db.Table
import fi.iki.ede.safe.db.TableColumns
import fi.iki.ede.safe.db.whereEq
import fi.iki.ede.safe.db.whereNot
import fi.iki.ede.safe.db.whereNullOr0
import junit.framework.TestCase.assertEquals
import org.junit.Test

class SelectionConditionTest {

    // naive! supports only 1 arg! also no string encapsulation
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
        assert(SelectionCondition.alwaysMatch().query() == "(1 = ?)")
    }

    @Test
    fun whereEqTest() {
        assertEquals(sqlize(whereEq(TestTable.Columns.STRING, "foo")), "(string = foo)")
        assertEquals(sqlize(whereEq(TestTable.Columns.INTEGER, "1")), "(integer = 1)")
    }

    @Test
    fun whereNotTest() {
        assertEquals(sqlize(whereNot(TestTable.Columns.STRING, "foo")), "(string <> foo)")
        assertEquals(sqlize(whereNot(TestTable.Columns.INTEGER, "1")), "(integer <> 1)")
    }

    @Test
    fun whereNullOr0Test() {
        assertEquals(
            sqlize(whereNullOr0(TestTable.Columns.STRING, "foo", "''")),
            "(IFNULL(string, '') = foo)"
        )
        assertEquals(
            sqlize(whereNullOr0(TestTable.Columns.INTEGER, "1", "0")),
            "(IFNULL(integer, 0) = 1)"
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