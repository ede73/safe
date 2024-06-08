package fi.iki.ede.crypto

import android.graphics.Bitmap
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.decryptPhoto
import java.time.ZonedDateTime

/**
 * Allows access to EncryptedPassEntry's fields decrypted
 *
 * NEVER PERSIST THIS CLASS, get rid of it as soon as possible.
 *
 * TODO: Doesn't really belong to this project, does it?
 */
class DecryptableSiteEntry(categoryId: Long) {
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
    var id: Long? = null
    var note: IVCipherText = IVCipherText.getEmpty()
    var password: IVCipherText = IVCipherText.getEmpty()

    // Password changed date(time) is not privacy critical (hence unencrypted)
    // TODO: LocalDateTime will suffice...
    var passwordChangedDate: ZonedDateTime? = null
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
        get() = if (photo.isEmpty()) null else decryptPhoto(keyStore)

    // plain description is used A LOT everywhere (listing, sorting, displaying)
    // On a large password DB operating on decrypt-on-demand description is just too slow
    // Hence once description is decrypted, we'll keep it (unless encrypted description changes)
    val plainDescription: String
        get() {
            if (decryptedCachedPlainDescription == null && description != IVCipherText.getEmpty()) {
                decryptedCachedPlainDescription = decrypt(description)
            }
            return decryptedCachedPlainDescription ?: ""
        }

    // TODO: UGLY, could be single use on demand
    // some other interface would be better for testing, we don't really need FULL keystore
    // and (lack of android keystore) causes issues with @Preview
    private val keyStore = KeyStoreHelperFactory.getKeyStoreHelper()
    private fun decrypt(value: IVCipherText) = try {
        String(keyStore.decryptByteArray(value))
    } catch (e: Exception) {
        "Failed decr $e"
    }


    fun contains(
        searchText: String,
        searchWebsites: Boolean,
        searchUsernames: Boolean,
        searchPasswords: Boolean,
        searchNotes: Boolean
    ) =
        // TODO: Might be able to optimize?
        plainDescription.contains(searchText, true) ||
                (searchWebsites && plainWebsite.contains(searchText, true)) ||
                (searchUsernames && plainUsername.contains(searchText, true)) ||
                (searchPasswords && plainPassword.contains(searchText, true)) ||
                (searchNotes && plainNote.contains(searchText, true))

    fun isSame(
        description: String,
        website: String,
        username: IVCipherText,
        password: IVCipherText,
        passwordChangedDate: ZonedDateTime?,
        note: IVCipherText,
        photo: Bitmap?
    ) = !(plainDescription != description ||
            plainWebsite != website ||
            plainUsername != decrypt(username) ||
            !isSamePassword(password) ||
            this.passwordChangedDate != passwordChangedDate ||
            plainNote != decrypt(note) ||
            !(photo?.sameAs(plainPhoto) ?: (plainPhoto == null)))

    fun isSamePassword(comparePassword: IVCipherText) = plainPassword == decrypt(comparePassword)

    // Flow state is annoying since it requires NEW ENTITIES for changes to register
    fun copy(): DecryptableSiteEntry = DecryptableSiteEntry(categoryId!!).apply {
        description = this@DecryptableSiteEntry.description
        decryptedCachedPlainDescription =
            this@DecryptableSiteEntry.decryptedCachedPlainDescription
        id = this@DecryptableSiteEntry.id
        note = this@DecryptableSiteEntry.note
        password = this@DecryptableSiteEntry.password
        passwordChangedDate = this@DecryptableSiteEntry.passwordChangedDate
        photo = this@DecryptableSiteEntry.photo
        username = this@DecryptableSiteEntry.username
        website = this@DecryptableSiteEntry.website
    }
}
