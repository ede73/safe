package fi.iki.ede.cryptoobjects

import android.graphics.Bitmap
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.logger.Logger
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Allows access to EncryptedPassEntry's fields decrypted
 *
 * NEVER PERSIST THIS CLASS, get rid of it as soon as possible.
 *
 * TODO: Doesn't really belong to this project, does it?
 */
@ExperimentalTime
class DecryptableSiteEntry(categoryId: Long) {
    companion object {
        const val TAG = "DecryptableSiteEntry"
    }

    var description: IVCipherText = IVCipherText.getEmpty()
        set(value) {
            if (field != value) {
                field = value
                decryptedCachedPlainDescription = null
            }
        }

    init {
        this.description = IVCipherText.getEmpty()
    }

    var categoryId: Long? = categoryId

    // soft deletion property, mainly used for backup/restore and Trash Can visuals
    var deleted: Long = 0
    var id: Long? = null
    var note: IVCipherText = IVCipherText.getEmpty()
    var password: IVCipherText = IVCipherText.getEmpty()
    val plainExtensions: Map<String, Set<String>>
        get() = try {
            if (extensions.isEmpty()) mapOf()
            else
                Json.decodeFromString<Map<String, Set<String>>>(
                    extensions.decrypt(decrypter).trim()
                )
        } catch (e: Exception) {
            // TODO: temporarily disabled
            //firebaseRecordException("Failed to import extension", e)
            mutableMapOf()
        }
    var extensions: IVCipherText = IVCipherText.getEmpty()

    // Password changed date(time) is not privacy critical (hence unencrypted)
    // TODO: LocalDateTime will suffice...
    var passwordChangedDate: Instant? = null
    var photo: IVCipherText = IVCipherText.getEmpty()
    var username: IVCipherText = IVCipherText.getEmpty()
    var website: IVCipherText = IVCipherText.getEmpty()

    private var decryptedCachedPlainDescription: String? = null

    val plainPassword: String
        get() = decrypt(password)
    val plainUsername: String
        get() = decrypt(username)
    val plainWebsite: String
        get() = decrypt(website)
    val plainNote: String
        get() = decrypt(note)
    val plainPhoto: Bitmap?
        get() = if (photo.isEmpty()) null else decryptPhoto(decrypter)

    // plain description is used A LOT everywhere (listing, sorting, displaying)
    // On a large password DB operating on decrypt-on-demand description is just too slow
    // Hence once description is decrypted, we'll keep it (unless encrypted description changes)
    val cachedPlainDescription: String
        get() {
            if (decryptedCachedPlainDescription == null && description != IVCipherText.getEmpty()) {
                decryptedCachedPlainDescription = decrypt(description)
            }
            return decryptedCachedPlainDescription ?: ""
        }

    fun contains(
        searchText: String,
        searchWebsites: Boolean,
        searchUsernames: Boolean,
        searchPasswords: Boolean,
        searchNotes: Boolean,
        searchExtensions: Boolean
    ) = // TODO: Might be able to optimize?
        cachedPlainDescription.contains(searchText, true) ||
                (searchWebsites && plainWebsite.contains(searchText, true)) ||
                (searchUsernames && plainUsername.contains(searchText, true)) ||
                (searchPasswords && plainPassword.contains(searchText, true)) ||
                (searchNotes && plainNote.contains(searchText, true)) ||
                (searchExtensions && plainExtensions.values.joinToString("")
                    .contains(searchText, true)).also {
                    if (BuildConfig.DEBUG) {
                        Logger.i(TAG, plainExtensions.values.joinToString(""))
                    }
                }

    fun isSame(
        description: String,
        website: String,
        username: IVCipherText,
        password: IVCipherText,
        passwordChangedDate: Instant?,
        note: IVCipherText,
        photo: Bitmap?,
        extensions: Map<String, Set<String>>
    ) = cachedPlainDescription == description &&
            plainWebsite == website &&
            plainUsername == decrypt(username) &&
            isSamePassword(password) &&
            this.passwordChangedDate == passwordChangedDate &&
            plainNote == decrypt(note) &&
            (photo?.sameAs(plainPhoto) ?: (plainPhoto == null)) &&
            !areMapsDifferent(extensions, this.plainExtensions)

    private fun areMapsDifferent(
        map1: Map<String, Set<String>>,
        map2: Map<String, Set<String>>
    ): Boolean {
        // Clean maps from empty strings and empty sets
        val cleanMap1 = map1.mapValues { (_, value) -> value.filterNot { it.isBlank() }.toSet() }
            .filterNot { it.value.isEmpty() }
        val cleanMap2 = map2.mapValues { (_, value) -> value.filterNot { it.isBlank() }.toSet() }
            .filterNot { it.value.isEmpty() }

        // Check if keys are the same after cleaning
        if (cleanMap1.keys != cleanMap2.keys) return true

        // Check if values are the same for each key
        return cleanMap1.any { (key, value) ->
            value != cleanMap2[key]
        }
    }

    fun isSamePassword(comparePassword: IVCipherText) = plainPassword == decrypt(comparePassword)

    // Flow state is annoying since it requires NEW ENTITIES for changes to register
    fun copy(): DecryptableSiteEntry = DecryptableSiteEntry(categoryId!!).apply {
        description = this@DecryptableSiteEntry.description
    }

    private val decrypter = KeyStoreHelperFactory.getDecrypter()

    private fun decrypt(value: IVCipherText) = try {
        String(decrypter(value))
    } catch (e: Exception) {
        "Failed decr $e"
    }

    fun encryptExtension(plainExtensions: Map<String, Set<String>>): IVCipherText =
        Json.encodeToString(plainExtensions).encrypt()
}