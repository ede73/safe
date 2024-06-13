package fi.iki.ede.safe.oisafecompatibility

import android.database.sqlite.SQLiteException
import android.util.Log
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.oisafecompatibility.OISafeCategoryEntry
import fi.iki.ede.oisafecompatibility.OISafeRestoreHandler
import fi.iki.ede.oisafecompatibility.OISafeSiteEntry
import fi.iki.ede.oisafecompatibility.RestoreDataSet
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.utilities.throwIfFeatureNotEnabled
import java.io.InputStream

internal fun fromOiSafeCat(oiSafeCategoryEntry: OISafeCategoryEntry) =
    DecryptableCategoryEntry().apply {
        id = oiSafeCategoryEntry.id
        encryptedName = oiSafeCategoryEntry.encryptedName
    }

internal fun fromOiSafePwd(id: Long, oiSafeSiteEntry: OISafeSiteEntry) =
    DecryptableSiteEntry(oiSafeSiteEntry.categoryId).apply {
        this.id = id
        description = oiSafeSiteEntry.description
        website = oiSafeSiteEntry.website
        username = oiSafeSiteEntry.username
        password = oiSafeSiteEntry.password
        note = oiSafeSiteEntry.note
        passwordChangedDate = oiSafeSiteEntry.passwordChangedDate
    }


@Deprecated("Just for backwards compatibility")
object OISafeRestore {
    private const val TAG = "Restore"

    fun readAndRestore(
        streamData: InputStream,
        encryptWithNewKey: (ByteArray) -> IVCipherText,
        passwordOfBackup: Password,
        encryptedRenewedSaltedMasterKey: Pair<Salt, IVCipherText>,
        dbHelper: DBHelper,
    ): Int {
        throwIfFeatureNotEnabled(BuildConfig.ENABLE_OIIMPORT)
        require(!encryptedRenewedSaltedMasterKey.first.isEmpty()) { "Empty salt passed to readAndRestore" }
        require(!encryptedRenewedSaltedMasterKey.second.isEmpty()) { "Empty master cipher passed to readAndRestore" }
        require(!passwordOfBackup.isEmpty()) { "Empty backup password passed to readAndRestore" }

        return doRestoreDatabase(
            dbHelper,
            OISafeRestoreHandler(
                encryptWithNewKey,
                passwordOfBackup,
            ).parse(streamData),
            encryptedRenewedSaltedMasterKey,
        )
    }

    private fun doRestoreDatabase(
        dbHelper: DBHelper,
        dataSet: RestoreDataSet,
        encryptedRenewedSaltedMasterKey: Pair<Salt, IVCipherText>,
    ): Int {
        val db = dbHelper.beginRestoration()
        dbHelper.storeSaltAndEncryptedMasterKey(
            encryptedRenewedSaltedMasterKey.first,
            encryptedRenewedSaltedMasterKey.second
        )

        if (!db.inTransaction()) {
            throw Exception("Internal failure")
        }
        for (category in dataSet.categories) {
            dbHelper.addCategory(fromOiSafeCat(category))
        }
        var totalPasswords = 0
        for (password in dataSet.pass) {
            totalPasswords++
            val pwdId = totalPasswords.toLong()
            try {
                dbHelper.addPassword(fromOiSafePwd(pwdId, password))
            } catch (ex: SQLiteException) {
                Log.e(TAG, "ERROR adding password ${pwdId}")
                db.endTransaction()
                return -1
            }
        }
        db.setTransactionSuccessful()
        db.endTransaction()
        if (db.inTransaction()) {
            throw Exception("Internal failure")
        }

        // also MUST BE signed in
        if (!LoginHandler.isLoggedIn()) {
            Log.e(TAG, "This should never happen")
        }
        return totalPasswords
    }
}