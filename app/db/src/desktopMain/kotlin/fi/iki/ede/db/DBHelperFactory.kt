package fi.iki.ede.db

object DBHelperFactory {
    private val instance = DBHelper()

    fun initializeDatabase(dbHelper: DBHelper): DBHelper {
        return instance
    }

    fun getDBHelper(notUsed: Any? = null): DBHelper {
        return instance
    }
}
