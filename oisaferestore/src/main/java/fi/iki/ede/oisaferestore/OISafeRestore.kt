package fi.iki.ede.oisaferestore

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_ITERATION_COUNT
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_LENGTH_BITS
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBHelper
import fi.iki.ede.safe.model.LoginHandler
import java.io.InputStream
import javax.crypto.spec.SecretKeySpec

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
                dbHelper.addSiteEntry(fromOiSafePwd(pwdId, password))
            } catch (ex: SQLiteException) {
                Log.e(TAG, "ERROR adding password $pwdId")
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

// this horrible lump here just to pass fake crypto stuff from test case
fun restoreOiSafeDump(
    context: Context,
    dbHelper: DBHelper,
    passwordOfBackup: Password,
    selectedDocStream: InputStream
): Int {
    val newRawMasterkey = KeyManagement.generateAESKey(KEY_LENGTH_BITS)
    val newMasterkey = SecretKeySpec(newRawMasterkey, "AES")

    val encryptedRenewedMasterKeyAndSalt =
        Salt(CipherUtilities.generateRandomBytes(8 * 8)).let { salt ->
            Pair(salt,
                KeyManagement.encryptMasterKey(
                    KeyManagement.generatePBKDF2AESKey(
                        salt,
                        KEY_ITERATION_COUNT,
                        passwordOfBackup,
                        KEY_LENGTH_BITS
                    ), newRawMasterkey
                ).let { (iv, ciphertext) ->
                    IVCipherText(iv, ciphertext)
                })
        }
    val ks = KeyStoreHelperFactory.getKeyStoreHelper() // needed, new master key
    fun encryptWithNewKey(value: ByteArray) = ks.encryptByteArray(value, newMasterkey)

    return try {
        val totalPasswords = OISafeRestore.readAndRestore(
            selectedDocStream,
            ::encryptWithNewKey,
            passwordOfBackup,
            encryptedRenewedMasterKeyAndSalt,
            dbHelper
        )
        if (totalPasswords >= 0) {
            // Also re-import(replace) existing key
            LoginHandler.passwordLogin(context, passwordOfBackup)
        }
        totalPasswords
    } catch (ex: Exception) {
        Log.e("RestoreDatabase", "$ex")
        -1
    }
}
