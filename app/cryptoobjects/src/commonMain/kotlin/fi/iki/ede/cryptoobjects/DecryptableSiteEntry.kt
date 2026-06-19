package fi.iki.ede.cryptoobjects

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.logger.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
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
                    extensions.decrypt().trim()
                )
        } catch (e: Exception) {
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
        get() = password.decrypt()
    val plainUsername: String
        get() = username.decrypt()
    val plainWebsite: String
        get() = website.decrypt()
    val plainNote: String
        get() = note.decrypt()
    val plainPhoto: PlatformBitmap?
        get() = if (photo.isEmpty()) null else decryptPhoto()

    // plain description is used A LOT everywhere (listing, sorting, displaying)
    // On a large password DB operating on decrypt-on-demand description is just too slow
    // Hence once description is decrypted, we'll keep it (unless encrypted description changes)
    val cachedPlainDescription: String
        get() {
            if (decryptedCachedPlainDescription == null && description != IVCipherText.getEmpty()) {
                decryptedCachedPlainDescription = description.decrypt()
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
                    Logger.d(TAG, plainExtensions.values.joinToString(""))
                }

    // Addressed PR5 comment: Reverted isSame and areMapsDifferent back inside the class definition
    fun isSame(
        description: String,
        website: String,
        username: IVCipherText,
        password: IVCipherText,
        passwordChangedDate: Instant?,
        note: IVCipherText,
        photo: PlatformBitmap?,
        extensions: Map<String, Set<String>>
    ) = cachedPlainDescription == description &&
            plainWebsite == website &&
            plainUsername == username.decrypt() &&
            isSamePassword(password) &&
            this.passwordChangedDate == passwordChangedDate &&
            plainNote == note.decrypt() &&
            sameAs(photo, plainPhoto) &&
            !areMapsDifferent(extensions, this.plainExtensions)

    private fun areMapsDifferent(
        map1: Map<String, Set<String>>,
        map2: Map<String, Set<String>>
    ): Boolean {
        val cleanMap1 = map1.mapValues { (_, value) -> value.filterNot { it.isBlank() }.toSet() }
            .filterNot { it.value.isEmpty() }
        val cleanMap2 = map2.mapValues { (_, value) -> value.filterNot { it.isBlank() }.toSet() }
            .filterNot { it.value.isEmpty() }

        if (cleanMap1.keys != cleanMap2.keys) return true

        return cleanMap1.any { (key, value) ->
            value != cleanMap2[key]
        }
    }

    fun isSamePassword(comparePassword: IVCipherText) = plainPassword == comparePassword.decrypt()

    // Flow state is annoying since it requires NEW ENTITIES for changes to register
    fun copy(): DecryptableSiteEntry = DecryptableSiteEntry(categoryId!!).apply {
        description = this@DecryptableSiteEntry.description
    }

    fun encryptExtension(plainExtensions: Map<String, Set<String>>): IVCipherText =
        Json.encodeToString(plainExtensions).encrypt()
}

@ExperimentalTime
expect fun DecryptableSiteEntry.decryptPhoto(): PlatformBitmap?
