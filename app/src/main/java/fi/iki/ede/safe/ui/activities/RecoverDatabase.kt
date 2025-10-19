package fi.iki.ede.safe.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.runtime.Composable
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_ITERATION_COUNT
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_LENGTH_BITS
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.generateRandomBytes
import fi.iki.ede.crypto.keystore.KeyManagement.generatePBKDF2AESKey
import fi.iki.ede.crypto.keystore.KeyManagement.makeFreshNewKey
import fi.iki.ede.crypto.keystore.KeyStoreHelper.Companion.ANDROID_KEYSTORE
import fi.iki.ede.crypto.keystore.KeyStoreHelper.Companion.importExistingEncryptedMasterKey
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.ui.composable.CopyDatabase
import fi.iki.ede.safe.ui.composable.DualModePreview
import fi.iki.ede.theme.SafeThemeSurface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.security.Key
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec


// am start -n  fi.iki.ede.safe.debug/fi.iki.ede.safe.ui.activities.RecoverDatabase
class RecoverDatabase : ComponentActivity() {
    private var output: String = ""
//    private val requestPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { isGranted: Boolean ->
//        if (isGranted) {
//            // Permission is granted. Continue the action or workflow in your app.
//        } else {
//            // Explain to the user that the feature is unavailable because the
//            // features requires a permission that the user has denied.
//        }
//    }

    private val createDocumentLauncher = registerForActivityResult(
        CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            contentResolver.openFileDescriptor(uri, "w")?.use {
                FileOutputStream(it.fileDescriptor).channel.use { fileChannel ->
                    copyDatabaseToPublicFolder(fileChannel, output)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish()
        }
        setContent {
            CopyDatabase(createDocumentLauncher) {
                output = it
            }
        }
    }

    private fun copyDatabaseToPublicFolder(dstChannel: FileChannel, output: String) {
        val dbFile = File(output)
        if (dbFile.exists()) {
            FileInputStream(dbFile).channel.use { srcChannel ->
                dstChannel.transferFrom(srcChannel, 0, srcChannel.size())
            }
        }
    }
}

fun reconvertDatabase(pwd: String, completed: () -> Unit) {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)
    val existingUnencryptedMasterKey = keyStore.getKey("secret_masterkey", null)!!
    val ks = KeyStoreHelperFactory.getKeyStoreHelper()

    fun decryptWithExistingMasterKey(encrypted: IVCipherText): ByteArray {
        return ks.decryptByteArray(encrypted, existingUnencryptedMasterKey)
    }

    val salt = Salt(generateRandomBytes(64))
    val saltedPassword = SaltedPassword(salt, Password(pwd))
    val pbkdf2key = generatePBKDF2AESKey(
        salt,
        KEY_ITERATION_COUNT,
        saltedPassword.password,
        KEY_LENGTH_BITS
    )
    val (unencryptedKey, cipheredKey) = makeFreshNewKey(
        KEY_LENGTH_BITS,
        pbkdf2key
    )

//    fun generateAESKey(bits: Int): ByteArray =
//        KeyGenerator.getInstance("AES").let {
//            it.init(bits)
//            it.generateKey().encoded
//        }
    //val a = existingUnencryptedMasterKey.encoded
    //val secretKeySpec = SecretKeySpec(existingUnencryptedMasterKey.encoded, "AES")

//    fun encryptWithNewMasterKey(input: ByteArray, sm: SecretKeySpec): IVCipherText {
//
//    }

//    encryptWithNewMasterKey("".toByteArray(), secretKeySpec)
    //val z = ks.encryptByteArray("".toByteArray(), unencryptedKey)

    val db = DBHelperFactory.getDBHelper()
    val cats = db.fetchAllCategoryRows()

    fun reEncrypt(
        oldEncrypted: IVCipherText,
        existingUnencryptedMasterKey: Key,
        unencryptedKey: SecretKeySpec
    ): IVCipherText {
        val decrypted = ks.decryptByteArray(oldEncrypted, existingUnencryptedMasterKey)
        return ks.encryptByteArray(decrypted, unencryptedKey)
    }

    val newCategories = cats.map { cat ->
        DecryptableCategoryEntry().apply {
            id = cat.id
            encryptedName =
                reEncrypt(cat.encryptedName, existingUnencryptedMasterKey, unencryptedKey)
        }
    }

    val pwds = db.fetchAllRows()
    val newPwds = pwds.map { pwd ->
        DecryptableSiteEntry(pwd.categoryId!!).apply {
            id = pwd.id
            categoryId = pwd.categoryId
            description = reEncrypt(pwd.description, existingUnencryptedMasterKey, unencryptedKey)
            password = reEncrypt(pwd.password, existingUnencryptedMasterKey, unencryptedKey)
            note = reEncrypt(pwd.note, existingUnencryptedMasterKey, unencryptedKey)
            photo = reEncrypt(pwd.photo, existingUnencryptedMasterKey, unencryptedKey)
            website = reEncrypt(pwd.website, existingUnencryptedMasterKey, unencryptedKey)
            username = reEncrypt(pwd.username, existingUnencryptedMasterKey, unencryptedKey)
            passwordChangedDate = pwd.passwordChangedDate
        }
    }

    db.writableDatabase.execSQL("DELETE FROM passwords")
    db.writableDatabase.execSQL("DELETE FROM categories")

    newCategories.forEach {
        db.addCategory(it)
    }
    newPwds.forEach {
        db.addSiteEntry(it)
    }
    db.storeSaltAndEncryptedMasterKey(salt, cipheredKey)

    importExistingEncryptedMasterKey(saltedPassword, cipheredKey)
    completed()
}

private fun encryptMasterPassword(salt: Salt, newPBKDF2Key: SecretKeySpec): String {
    try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val sm = keyStore.getKey("secret_masterkey", null)!!
        val ks = KeyStoreHelperFactory.getKeyStoreHelper()


        val encryptedMasterKey = ks.encryptByteArray("".toByteArray(), sm)

        val db = DBHelperFactory.getDBHelper()
        db.storeSaltAndEncryptedMasterKey(salt, encryptedMasterKey)
        return "Changed"
    } catch (e: Exception) {
        return e.toString()
    }
}

fun nudepwd(): String {
    try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val sm = keyStore.getKey("secret_masterkey", null)!!
        val ks = KeyStoreHelperFactory.getKeyStoreHelper()
        val db = DBHelperFactory.getDBHelper()
        val cats = db.fetchAllCategoryRows()

        val encrypted = cats.first().encryptedName
        val res = String(ks.decryptByteArray(encrypted, sm))
        return res
    } catch (e: Exception) {
        return e.toString()
    }
}

@DualModePreview
@Composable
fun RecoverDatabasePreview() {
    SafeThemeSurface {
        CopyDatabase(null) {}
    }
}

