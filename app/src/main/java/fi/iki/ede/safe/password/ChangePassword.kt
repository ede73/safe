package fi.iki.ede.safe.password

import android.content.Context
import android.util.Log
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyManagement.decryptMasterKey
import fi.iki.ede.crypto.keystore.KeyManagement.encryptMasterKey
import fi.iki.ede.crypto.keystore.KeyStoreHelper.Companion.generatePBKDF2
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


object ChangePassword {

    // Change the user password derived (PBKDF2) key that is used to encrypt the actual master key
    // the master key remains unchanged, so no need to 'convert' the database
    fun changeMasterPassword(
        context: Context,
        oldPass: Password,
        newPass: Password,
        finished: (Boolean) -> Unit
    ): Boolean {
        val dbHelper = DBHelperFactory.getDBHelper(context)
        val (salt, ivCipher) = dbHelper.fetchSaltAndEncryptedMasterKey()
        val existingPBKDF2Key = generatePBKDF2(salt, oldPass)
        val newPBKDF2Key = generatePBKDF2(salt, newPass)
        val myScope = CoroutineScope(Dispatchers.Default)

        try {
            val decryptedMasterKey = decryptMasterKey(existingPBKDF2Key, ivCipher)
            val newEncryptedMasterKey = encryptMasterKey(newPBKDF2Key, decryptedMasterKey.encoded)
            dbHelper.storeSaltAndEncryptedMasterKey(salt, newEncryptedMasterKey)
            myScope.launch {
                withContext(Dispatchers.IO) {
                    DataModel.loadFromDatabase()
                    // TODO: REALLY issue info to the caller that we're finished..
                    // else there's few seconds data is actually flaky..
                    finished(true)
                }
            }
            // Actually why should we clear the bio..it is not tied directly to the
            // key generation in anyway (at the moment!) - it COULD THOUGH!
            //Biometrics.clearBiometricKeys(context)
            return true
        } catch (e: Exception) {
            // TODO: Figure out, basically theres not much to do here, its not like we can
            // setup a transaction to protect stuff, also user always has backup, right? :)
            Log.e("ChangePassword", "Failed modifying master password $e")
        } finally {
            dbHelper.close()
        }
        finished(false)
        return false
    }
}