package fi.iki.ede.safe.ui.composable

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.backupandrestore.Restore
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.oisafecompatibility.OISafeRestore
import fi.iki.ede.safe.ui.activities.AvertInactivityDuringLongTask
import fi.iki.ede.safe.ui.activities.RestoreScreen
import java.io.InputStream
import javax.crypto.spec.SecretKeySpec

@Composable
fun askToRestoreDatabase(
    inactivity: AvertInactivityDuringLongTask,
    context: RestoreScreen,
    compatibility: Boolean,
    backupPassword: Password,
    selectedDoc: Uri
): Int {
    val stream = context.contentResolver.openInputStream(selectedDoc)!!

    fun resetInactivityTimer() {
        // just try to make sure inactivity timer wont go on..even how small timeout it has
        inactivity.avertInactivity(context, "Restore DB inputstream")
    }

    resetInactivityTimer()

    if (compatibility) {
        val dbHelper = DBHelperFactory.getDBHelper(context)
        return try {
            restoreOiSafeDump(context, dbHelper, backupPassword, stream)
        } finally {
            resetInactivityTimer()
        }
    } else {
        return try {
            Restore().doRestore(
                context,
                String(stream.readBytes()),
                backupPassword,
                DBHelperFactory.getDBHelper(context)
            )
        } catch (ex: Exception) {
            // something failed
            Log.e("RestoreScreen", ex.toString())
            0
        } finally {
            resetInactivityTimer()
        }
    }
}

private fun restoreOiSafeDump(
    context: Context,
    dbHelper: DBHelper,
    passwordOfBackup: Password,
    selectedDocStream: InputStream
): Int {
    val salt = Salt(KeyManagement.generateRandomBytes(8 * 8))
    val newPBKDF2Key = KeyStoreHelper.generatePBKDF2(salt, passwordOfBackup)
    val newRawMasterkey = KeyManagement.generateAESKey(KeyStoreHelper.KEY_LENGTH_BITS)
    val newMasterkey = SecretKeySpec(newRawMasterkey, "AES")
    val (iv, ciphertext) = KeyManagement.encryptMasterKey(newPBKDF2Key, newRawMasterkey)
    val encryptedRenewedMasterKey = IVCipherText(iv, ciphertext)

    val ks = KeyStoreHelperFactory.getKeyStoreHelper()
    fun encryptWithNewKey(value: ByteArray) = ks.encryptByteArray(value, newMasterkey)

    return try {
        val totalPasswords = OISafeRestore.readAndRestore(
            selectedDocStream,
            ::encryptWithNewKey,
            passwordOfBackup,
            Pair(salt, encryptedRenewedMasterKey),
            dbHelper
        )
        if (totalPasswords >= 0) {
            // Also re-import(replace) existing key
            LoginHandler.passwordLogin(context, passwordOfBackup)
            // This should be loaded in activities/RestoreScreen
            //DataModel.reset()
        }
        totalPasswords
    } catch (ex: Exception) {
        -1
    }
}