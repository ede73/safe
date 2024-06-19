package fi.iki.ede.hibp

import java.security.MessageDigest

/**
 * Utility class hiding K-Anonymity
 */
class KAnonymity(passwordNotToBeStored: String) {
    private val kanonHash: String

    init {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(passwordNotToBeStored.toByteArray())
        kanonHash = hash.joinToString("") {
            "%02x".format(it)
        }.lowercase()
    }

    fun getPrefix(i: Int): String {
        return kanonHash.substring(0, i)
    }

    fun isMatch(prefixLength: Int, breachedPasswordHashes: List<String>): Boolean {
        return breachedPasswordHashes.any {
            kanonHash.substring(prefixLength) == it.take(kanonHash.length - prefixLength)
                .lowercase()
        }
    }
}