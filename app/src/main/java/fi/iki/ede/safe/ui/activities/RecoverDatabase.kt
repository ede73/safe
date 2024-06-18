package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
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
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.password.ChangeMasterKeyAndPassword
import fi.iki.ede.safe.ui.composable.EnterNewMasterPassword
import fi.iki.ede.safe.ui.theme.SafeTheme
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
        setContent {
            CopyDatabase(createDocumentLauncher, null) {
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

@Composable
private fun CopyDatabase(
    createDocumentLauncher: ActivityResultLauncher<String>?,
    requestPermissionLauncher: ActivityResultLauncher<String>?,
    updateOutput: (String) -> Unit
) {
    val context: Context = LocalContext.current
    val dbInput =
        remember {
            mutableStateOf(
                context.getDatabasePath(DBHelper.DATABASE_NAME)?.path ?: "unknown"
            )
        }
    val dbOutput =
        remember {
            mutableStateOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
                    ?: ""
            )
        }

    Column {
        Text("Input")
        TextField(value = dbInput.value, onValueChange = {
            dbInput.value = it
            updateOutput(it)
        })

        Text("Output")
        TextField(value = dbOutput.value, onValueChange = { dbOutput.value = it })

//        var text by remember {
//            mutableStateOf(
//                when (ContextCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.WRITE_EXTERNAL_STORAGE
//                )) {
//                    PackageManager.PERMISSION_GRANTED -> "Copy"
//                    else -> "Request Permission"
//                }
//            )

        var text by remember { mutableStateOf("Copy") }
        ///text = "Copy"

        Button(onClick = {
            createDocumentLauncher?.launch(File(dbInput.value).name)
//            when (ContextCompat.checkSelfPermission(
//                context,
//                Manifest.permission.WRITE_EXTERNAL_STORAGE
//            )) {
//                PackageManager.PERMISSION_GRANTED ->
//                    createDocumentLauncher?.launch(dbInput.value)
//
//                else -> {
//                    requestPermissionLauncher?.launch(
//                        Manifest.permission.WRITE_EXTERNAL_STORAGE
//                    )
//                    text = "Requesting"
//                }
//            }
        }) {
            Text(text)
        }

        var pwd by remember { mutableStateOf("12345678") }
        Row {
            TextField(value = pwd, onValueChange = { pwd = it })
            Button(onClick = {
                reconvertDatabase(pwd) {
                    Toast.makeText(context, "Reset", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Reset DB Password")
            }
        }

        var changePassword by remember { mutableStateOf(false) }

        if (changePassword) {
            EnterNewMasterPassword {
                val (oldMasterPassword, newMasterPassword) = it
                ChangeMasterKeyAndPassword.changeMasterPassword(
                    oldMasterPassword,
                    newMasterPassword
                ) { success ->
                    changePassword = false
                }
            }
        }

        Button(onClick = {
            changePassword = true
        }) {
            Text("Change password")
        }

        val res = nudepwd()
        Text(res)

        //        val newPass = Password("newpass")
//        val salt = Salt(generateRandomBytes(64))
//        val newPBKDF2Key = generatePBKDF2AESKey(salt, KEY_ITERATION_COUNT, newPass, KEY_LENGTH_BITS)
//        Cipher.getInstance("AES/CBC/PKCS7Padding").let {
//            it.init(
//                Cipher.ENCRYPT_MODE,
//                sm,
//                IvParameterSpec(generateRandomBytes(it.blockSize * 8))
//            )
//            IVCipherText(it.iv, it.doFinal(input))
//        }

    }
}

private fun reconvertDatabase(pwd: String, completed: () -> Unit) {
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
        db.addPassword(it)
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

private fun nudepwd(): String {
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

@Preview(showBackground = true)
@Composable
fun RecoverDatabasePreview() {
    SafeTheme {
        CopyDatabase(null, null) {}
    }
}

