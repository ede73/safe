package fi.iki.ede.crypto

import fi.iki.ede.crypto.support.DisallowedFunctions

/**
 * Convenience class representing unencrypted password
 * You should avoid using this class as much as possible
 *
 * Even at the expense of using more resources
 *
 * According to Java Cryptography Architecture Reference guide,
 * due to garbage collection deficiencies, use char[] instead of String to store passwords
 *
 * DisallowedFunctions try to limit unintentional exposure
 *
 * Avoid using, prefer SaltedEncryptedPassword
 */
open class Password
@Deprecated("Internally used, you should prefer String constructor")
constructor(
    open val utf8password: CharArray
) : DisallowedFunctions() {
    val length: Int
        get() {
            return utf8password.size
        }

    @Deprecated("Please don't use, there should be no need to convert strings to bytes")
    constructor(utf8PasswordAsByteArray: ByteArray) : this(
        String(utf8PasswordAsByteArray, Charsets.UTF_8).toCharArray()
    )

    constructor(utf8PasswordAsString: String) : this(
        utf8PasswordAsString.toCharArray()
    )

    open fun isEmpty(): Boolean = utf8password.isEmpty()
    override fun hashCode() = utf8password.contentHashCode()

    override fun equals(other: Any?): Boolean =
        (other != null) && (this.hashCode() == (other as? Password)?.hashCode())

    // In java at least it's impossible to 'delete' a string from heap reliably
    // And for char/byte array, end result is unpredictable even after GC
    // Since kotlin is just java, I assume the same, so
    // as soon as we don't need the password anymore, make sure it's can't be memory dumped
    fun finalize() {
        if (!isEmpty()) {
            for (i in utf8password.indices) {
                utf8password[i] = 0.toChar()
            }
        }
    }

    companion object {
        fun getEmpty() = Password(charArrayOf())
    }
}