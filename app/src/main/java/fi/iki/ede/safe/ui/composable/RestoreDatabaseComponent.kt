package fi.iki.ede.safe.ui.composable

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_ITERATION_COUNT
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_LENGTH_BITS
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.R
import fi.iki.ede.safe.backupandrestore.RestoreDatabase
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.oisafecompatibility.OISafeRestore
import fi.iki.ede.safe.ui.activities.PrepareDataBaseRestorationScreen
import fi.iki.ede.safe.ui.activities.throwIfFeatureNotEnabled
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.time.ZonedDateTime
import javax.crypto.spec.SecretKeySpec

@Composable
fun RestoreDatabaseComponent(
    context: PrepareDataBaseRestorationScreen,
    compatibility: Boolean,
    backupPassword: Password,
    selectedDoc: Uri,
    onFinished: (passwords: Int, ex: Exception?) -> Unit
) {
    val stream = LocalContext.current.contentResolver.openInputStream(selectedDoc)!!
    val coroutineScope = rememberCoroutineScope()

    val restoringOldBackupTitle = stringResource(R.string.restore_screen_not_most_recent_backup)
    val restoreAnyway = stringResource(R.string.restore_screen_not_most_recent_backup_restore)
    val cancelRestoration = stringResource(R.string.restore_screen_not_most_recent_backup_cancel)

    suspend fun verifyUserWantsToRestoreOldBackup(
        coroutineScope: CoroutineScope,
        backupCreationTime: ZonedDateTime,
        lastBackupDone: ZonedDateTime
    ): Boolean {
        val result = CompletableDeferred<Boolean>()
        val days = DateUtils.getPeriodBetweenDates(backupCreationTime, lastBackupDone)
        //val hours = Duration.between(backupCreationTime, lastBackupDone)

        val restoreOldBackupMessage = context.getString(
            R.string.restore_screen_not_most_recent_backup_age,
            days.days,
            backupCreationTime.toLocalDateTime().toString(),
            lastBackupDone.toLocalDateTime().toString()
        )
        coroutineScope.launch(Dispatchers.Main) {
            AlertDialog.Builder(context)
                .setTitle(restoringOldBackupTitle)
                .setMessage(restoreOldBackupMessage)
                .setPositiveButton(restoreAnyway) { _, _ ->
                    result.complete(true)
                }
                .setNegativeButton(cancelRestoration) { _, _ ->
                    result.complete(false)
                }
                .setOnDismissListener {
                    // Handle the case where the dialog is dismissed without an explicit action
                    result.complete(false)
                }
                .show()
        }

        // Wait for the result to be set by the dialog actions
        return result.await()
    }

    val dbHelper = DBHelperFactory.getDBHelper(LocalContext.current)

    if (compatibility) {
        try {
            val passwords = restoreOiSafeDump(context, dbHelper, backupPassword, stream)
            onFinished(passwords, null)
        } catch (ex: Exception) {
            onFinished(0, ex)
        }
    } else {
        val ctx = LocalContext.current
        LaunchedEffect(Unit) {
            launch(Dispatchers.IO + CoroutineName("DATABASE_RESTORATION")) {
                try {
                    val passwords = RestoreDatabase().doRestore(
                        ctx,
                        String(stream.readBytes()),
                        backupPassword,
                        dbHelper
                    ) { backupCreationTime, lastBackupDone ->
                        val restoreAnyway = runBlocking {
                            verifyUserWantsToRestoreOldBackup(
                                coroutineScope,
                                backupCreationTime,
                                lastBackupDone
                            )
                        }
                        restoreAnyway
                    }
                    onFinished(passwords, null)
                } catch (ex: Exception) {
                    onFinished(0, ex)
                }
            }
        }
    }
}

// this horrible lump here just to pass fake crypto stuff from test case
private fun restoreOiSafeDump(
    context: Context,
    dbHelper: DBHelper,
    passwordOfBackup: Password,
    selectedDocStream: InputStream
): Int {
    throwIfFeatureNotEnabled(BuildConfig.ENABLE_OIIMPORT)
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
    val ks = KeyStoreHelperFactory.getKeyStoreHelper()
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
