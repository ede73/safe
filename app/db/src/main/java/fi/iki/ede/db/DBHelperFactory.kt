package fi.iki.ede.db

import android.content.Context
import kotlin.time.ExperimentalTime

/**
 * Construct new DBHelper
 *
 * Mockk has has bugs in constructor mocking (results in android verifier rejecting the class)
 * So as a work around, DBHelperFactory will provide the DBHelper (or DBHelper mock) in test
 */
object DBHelperFactory {
    @ExperimentalTime
    private var instance: DBHelper? = null

    @ExperimentalTime
    fun initializeDatabase(dbHelper: DBHelper): DBHelper {
        instance = dbHelper
        return instance!!
    }

    @ExperimentalTime
    fun getDBHelper(notUsed: Context? = null): DBHelper {
        require(instance != null) { "Someone forgot to call DBHelperFactory.initializeDatabase()" }
        return instance!!
    }
}