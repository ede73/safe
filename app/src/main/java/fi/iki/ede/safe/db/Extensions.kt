package fi.iki.ede.safe.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.CipherUtilities
import java.time.ZonedDateTime

internal fun ContentValues.put(column: TableColumns<*>, value: IVCipherText) =
    put(column.columnName, value.combineIVAndCipherText())

internal fun ContentValues.put(column: TableColumns<*>, value: Long?) =
    put(column.columnName, value)

internal fun ContentValues.put(column: TableColumns<*>, value: ByteArray) =
    put(column.columnName, value)

internal fun ContentValues.put(column: TableColumns<*>, date: ZonedDateTime) =
    put(column, DateUtils.toUnixSeconds(date))

internal fun Cursor.getColumnIndexOrThrow(column: TableColumns<*>) =
    getColumnIndexOrThrow(column.columnName)

internal fun Cursor.getZonedDateTimeOfPasswordChange(): ZonedDateTime? =
    getString(getColumnIndexOrThrow(Password.Columns.PASSWORD_CHANGE_DATE))?.let { date ->
        date.toLongOrNull()?.let {
            DateUtils.unixEpochSecondsToLocalZonedDateTime(it)
        } ?: run {
            //ok, we have something that isn't numerical
            DateUtils.newParse(date)
        }
    }

internal fun Cursor.getIVCipher(column: TableColumns<*>) =
    IVCipherText(
        CipherUtilities.IV_LENGTH,
        getBlob(getColumnIndexOrThrow(column.columnName)) ?: byteArrayOf(),
    )

internal fun Cursor.getDBID(column: TableColumns<*>) =
    getLong(getColumnIndexOrThrow(column.columnName))

internal fun <T : Table, C : TableColumns<T>> SQLiteDatabase.update(
    table: T,
    values: ContentValues,
    selection: SelectionCondition? = null
) = update(table.tableName, values, selection?.query(), selection?.args())

internal fun <T : Table, C : TableColumns<T>> SQLiteDatabase.query(
    distinct: Boolean,
    table: T,
    columns: Set<C>,
    selection: SelectionCondition? = null // TODO: THIS
) = query(
    distinct, table.tableName, columns.map { it.columnName }.toTypedArray(),
    selection?.query(), selection?.args(), null, null, null, null
)

internal fun <T : Table, C : TableColumns<T>> SQLiteDatabase.query(
    table: T,
    columns: Set<C>,
    selection: SelectionCondition? = null // TODO: THIS
) = query(false, table, columns, selection)

internal fun <T : Table, C : TableColumns<T>> SQLiteDatabase.delete(
    table: T,
    selection: SelectionCondition? = null
) = delete(table.tableName, selection?.query(), selection?.args())

internal fun <T : Table, C : TableColumns<T>> SQLiteDatabase.insert(
    table: T,
    values: ContentValues
) =
    insert(table.tableName, null, values)

internal fun <T : Table, C : TableColumns<T>> SQLiteDatabase.insertOrThrow(
    table: T,
    values: ContentValues
) =
    insert(table.tableName, null, values)


internal fun <T : Table, C : TableColumns<T>> whereEq(
    column: TableColumns<T>,
    whereArg: Any
) = SelectionCondition(column, whereArg, "=")
