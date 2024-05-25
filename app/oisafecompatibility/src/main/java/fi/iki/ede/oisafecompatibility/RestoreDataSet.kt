package fi.iki.ede.oisafecompatibility

import android.text.TextUtils
import android.util.Log
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.SaltedEncryptedPassword
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.date.DateUtils
import java.text.ParseException


@Deprecated("Just for backwards compatibility")
class RestoreDataSet(
    private var ch: OISafeCryptoHelper,
    val encryptByteArray: (ByteArray) -> IVCipherText
) {
    var version = 0
    var date: String? = null
    var saltedEncryptedPassword: SaltedEncryptedPassword =
        SaltedEncryptedPassword.getEmpty()
    private var totalEntries = 0
    val categories = ArrayList<DecryptableCategoryEntry>()
    val pass = ArrayList<DecryptablePasswordEntry>()
    private var currentCategoryId = 0L
    private var currentCategory: DecryptableCategoryEntry? = null
    private var currentEntry: DecryptablePasswordEntry? = null

    fun newCategory(extractedCategoryName: String) {
        currentCategory = DecryptableCategoryEntry()
        currentCategoryId++
        currentCategory!!.id = currentCategoryId
        currentCategory!!.encryptedName = encryptDecrypted(extractedCategoryName)
    }

    fun storyCategory() {
        if (currentCategory != null) {
            categories.add(currentCategory!!)
            currentCategory = null
        }
    }

    fun newEntry() {
        currentEntry = DecryptablePasswordEntry(currentCategoryId)
    }

    fun storeEntry() {
        if (currentEntry != null && currentEntry!!.description.isNotEmpty()) {
            pass.add(currentEntry!!)
            totalEntries++
        }
        currentEntry = null
    }

    private fun encryptDecrypted(value: String): IVCipherText {
        val plain = ch.decrypt(value)
        return encryptByteArray(plain)
    }

    fun setDescription(extractedDescription: String) {
        currentEntry?.description = encryptDecrypted(extractedDescription)
    }

    fun setWebsite(extractedWebsite: String) {
        currentEntry?.website = encryptDecrypted(extractedWebsite)
    }

    fun setUsername(extractedUsername: String) {
        currentEntry?.username = encryptDecrypted(extractedUsername)
    }

    fun setPassword(extractedPassword: String) {
        currentEntry?.password = encryptDecrypted(extractedPassword)
    }

    fun setNote(extractedNote: String) {
        // ok seriously what the fuck!?
        currentEntry?.note = encryptDecrypted(extractedNote)
    }

    fun setPasswordChangedDate(passwordChangedDate: String) {
        if (currentEntry != null && !TextUtils.isEmpty(passwordChangedDate)) {
            try {
                currentEntry!!.passwordChangedDate = DateUtils.newParse(passwordChangedDate)
            } catch (pe: ParseException) {
                Log.e(TAG, "failed setPasswordChangedDate($passwordChangedDate)")
            }
        }
    }

    fun gotSalt(
        saltedEncryptedMasterkeyOfBackup: SaltedEncryptedPassword,
        passwordOfBackup: Password
    ): SaltedEncryptedPassword {
        val salt = saltedEncryptedMasterkeyOfBackup.salt
        val encryptedMasterkeyOfBackup = saltedEncryptedMasterkeyOfBackup.encryptedPassword
        val currentSaltedPassword = SaltedPassword(salt, passwordOfBackup)
        ch.init(currentSaltedPassword)
        val unencryptedMasterKey = ch.decrypt(encryptedMasterkeyOfBackup)

        ch = OISafeCryptoHelper(Algorithm.IN_MEMORY_INTERNAL)
        ch.init(SaltedPassword(saltedEncryptedMasterkeyOfBackup.salt, unencryptedMasterKey))
        return saltedEncryptedMasterkeyOfBackup
    }

    companion object {
        private const val TAG = "RestoreDataSet"
    }
}