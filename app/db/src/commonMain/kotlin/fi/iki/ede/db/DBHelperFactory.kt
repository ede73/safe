package fi.iki.ede.db

import kotlin.time.ExperimentalTime

object DBHelperFactory {
    @ExperimentalTime
    private var instance: DBHelper? = null

    @ExperimentalTime
    fun initializeDatabase(dbHelper: DBHelper): DBHelper {
        instance = dbHelper
        return instance!!
    }

    @ExperimentalTime
    fun clearDatabase() {
        instance = null
    }

    @ExperimentalTime
    fun getDBHelper(): DBHelper {
        if (instance == null) {
            instance = DBHelper()
        }
        return instance!!
    }
}
