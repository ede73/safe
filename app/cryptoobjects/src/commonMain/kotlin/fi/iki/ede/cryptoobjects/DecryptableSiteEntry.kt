package fi.iki.ede.cryptoobjects

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.logger.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.time.Instant

/**
 * Allows access to EncryptedPassEntry's fields decrypted
 *
 * NEVER PERSIST THIS CLASS, get rid of it as soon as possible.
 *
 * TODO: Doesn't really belong to this project, does it?
 */
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Ignore

@Entity(tableName = "passwords")
class DecryptableSiteEntry(
    @ColumnInfo(name = "category")
    var categoryId: Long = 0L
) {
    companion object {
        const val TAG = "DecryptableSiteEntry"
    }

    @ColumnInfo(name = "description")
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

    // soft deletion property, mainly used for backup/restore and Trash Can visuals
    @ColumnInfo(name = "deleted")
    var deleted: Long = 0

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long? = null

    @ColumnInfo(name = "note")
    var note: IVCipherText = IVCipherText.getEmpty()

    @ColumnInfo(name = "password")
    var password: IVCipherText = IVCipherText.getEmpty()

    @Ignore
    var plainExtensions: Map<String, Set<String>> = mapOf()
        get() {
            if (field.isEmpty() && extensions.isNotEmpty()) {
                field = try {
                    Json.decodeFromString<Map<String, Set<String>>>(
                        extensions.decrypt().trim()
                    )
                } catch (e: Exception) {
                    mapOf()
                }
            }
            return field
        }
        set(value) {
            field = value
        }

    @ColumnInfo(name = "extensions")
    var extensions: IVCipherText = IVCipherText.getEmpty()

    // Password changed date(time) is not privacy critical (hence unencrypted)
    // TODO: LocalDateTime will suffice...
    @ColumnInfo(name = "passwordchangeddate")
    var passwordChangedDate: Instant? = null

    @ColumnInfo(name = "photo")
    var photoFilename: String? = null

    @Ignore
    var photo: IVCipherText = IVCipherText.getEmpty()

    @ColumnInfo(name = "username")
    var username: IVCipherText = IVCipherText.getEmpty()

    @ColumnInfo(name = "website")
    var website: IVCipherText = IVCipherText.getEmpty()

    @Ignore
    private var decryptedCachedPlainDescription: String? = null

    @Ignore
    var plainPassword: String = ""
        get() {
            if (field.isEmpty() && password.isNotEmpty()) {
                field = password.decrypt()
            }
            return field
        }
        set(value) {
            field = value
        }
    @Ignore
    var plainUsername: String = ""
        get() {
            if (field.isEmpty() && username.isNotEmpty()) {
                field = username.decrypt()
            }
            return field
        }
        set(value) {
            field = value
        }
    @Ignore
    var plainWebsite: String = ""
        get() {
            if (field.isEmpty() && website.isNotEmpty()) {
                field = website.decrypt()
            }
            return field
        }
        set(value) {
            field = value
        }
    @Ignore
    var plainNote: String = ""
        get() {
            if (field.isEmpty() && note.isNotEmpty()) {
                field = note.decrypt()
            }
            return field
        }
        set(value) {
            field = value
        }
    @Ignore
    var plainPhoto: PlatformBitmap? = null
        get() {
            if (field == null && photo.isNotEmpty()) {
                field = decryptPhoto()
            }
            return field
        }
        set(value) {
            field = value
        }

    // plain description is used A LOT everywhere (listing, sorting, displaying)
    // On a large password DB operating on decrypt-on-demand description is just too slow
    // Hence once description is decrypted, we'll keep it (unless encrypted description changes)
    @Ignore
    var cachedPlainDescription: String = ""
        get() {
            if (decryptedCachedPlainDescription == null && description != IVCipherText.getEmpty()) {
                decryptedCachedPlainDescription = description.decrypt()
            }
            return decryptedCachedPlainDescription ?: ""
        }
        set(value) {
            field = value
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
    fun copy(): DecryptableSiteEntry = DecryptableSiteEntry(categoryId).apply {
        description = this@DecryptableSiteEntry.description
    }

    fun encryptExtension(plainExtensions: Map<String, Set<String>>): IVCipherText =
        Json.encodeToString(plainExtensions).encrypt()
}

expect fun DecryptableSiteEntry.decryptPhoto(): PlatformBitmap?
