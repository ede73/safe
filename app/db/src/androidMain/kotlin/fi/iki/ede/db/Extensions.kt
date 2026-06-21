package fi.iki.ede.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.dateutils.DateUtils
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


fun ContentValues.put(column: TableColumns<*>, value: IVCipherText) =
    put(column.columnName, value.combineIVAndCipherText())

fun ContentValues.put(column: TableColumns<*>, value: Long?) =
    put(column.columnName, value)

fun ContentValues.put(column: TableColumns<*>, value: ByteArray) =
    put(column.columnName, value)

fun ContentValues.put(column: TableColumns<*>, value: String) =
    put(column.columnName, value)

@ExperimentalTime
fun ContentValues.put(column: TableColumns<*>, utcDate: Instant) =
    put(column, DateUtils.toUnixSeconds(utcDate))

fun Cursor.getColumnIndexOrThrow(column: TableColumns<*>) =
    getColumnIndexOrThrow(column.columnName)

fun Cursor.getIVCipher(
    column: TableColumns<*>,
    updateOnlyIfColumnExists: (IVCipherText) -> Unit
) = getColumnIndex(column.columnName)
    .takeIf { it != -1 }
    ?.let {
        IVCipherText(CipherUtilities.IV_LENGTH, getBlob(it) ?: byteArrayOf()).also(
            updateOnlyIfColumnExists
        )
    }

fun Cursor.getIVCipher(column: TableColumns<*>) =
    IVCipherText(
        CipherUtilities.IV_LENGTH,
        getBlob(getColumnIndexOrThrow(column.columnName)) ?: byteArrayOf(),
    )

fun Cursor.getString(column: TableColumns<*>): String =
    getString(getColumnIndexOrThrow(column.columnName))

fun Cursor.getDBID(column: TableColumns<*>) =
    getLong(getColumnIndexOrThrow(column.columnName))

fun <T : Table, C : TableColumns<T>> SQLiteDatabase.update(
    table: T,
    values: ContentValues,
    selection: SelectionCondition? = null
) = update(table.tableName, values, selection?.query(), selection?.args())

fun <T : Table, C : TableColumns<T>> SQLiteDatabase.query(
    distinct: Boolean,
    table: T,
    columns: Set<C>,
    selection: SelectionCondition? = null // TODO: THIS
) = query(
    distinct, table.tableName, columns.map { it.columnName }.toTypedArray(),
    selection?.query(), selection?.args(), null, null, null, null
)

fun <T : Table, C : TableColumns<T>> SQLiteDatabase.query(
    table: T,
    columns: Set<C>,
    selection: SelectionCondition? = null // TODO: THIS
) = query(false, table, columns, selection)

fun <T : Table, C : TableColumns<T>> SQLiteDatabase.delete(
    table: T,
    selection: SelectionCondition? = null
) = delete(table.tableName, selection?.query(), selection?.args())

fun <T : Table, C : TableColumns<T>> SQLiteDatabase.insert(
    table: T,
    values: ContentValues
) =
    insert(table.tableName, null, values)

fun <T : Table, C : TableColumns<T>> SQLiteDatabase.insertOrThrow(
    table: T,
    values: ContentValues
) =
    insertOrThrow(table.tableName, null, values)


fun <T : Table, C : TableColumns<T>> whereEq(
    column: TableColumns<T>,
    whereArg: Any
) = SelectionCondition(column, whereArg, "=")

fun <T : Table, C : TableColumns<T>> whereNot(
    column: TableColumns<T>,
    whereArg: Any
) = SelectionCondition(column, whereArg, "<>")

// something broken here..
fun <T : Table, C : TableColumns<T>> whereNullOr0(
    column: TableColumns<T>,
    whereArg: Any,
    coalesce: Any
) = SelectionCondition(column, whereArg, "=", coalesce = coalesce)
