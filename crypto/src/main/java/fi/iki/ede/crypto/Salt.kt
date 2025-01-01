package fi.iki.ede.crypto

import fi.iki.ede.crypto.support.DisallowedFunctions
import fi.iki.ede.crypto.support.toHexString


/**
 * Convenience class representing a Salt specifically
 *
 * DisallowedFunctions try to limit unintentional exposure
 */
data class Salt(val salt: ByteArray) : DisallowedFunctions {
    fun isEmpty(): Boolean = salt.isEmpty()
    fun toHex() = salt.toHexString()
    override fun hashCode() = salt.contentHashCode()

    override fun equals(other: Any?): Boolean =
        (other != null) && (this.hashCode() == (other as? Salt)?.hashCode())

    companion object {
        fun getEmpty() = Salt(byteArrayOf())
    }
}
