package fi.iki.ede.hibp

import okio.ByteString.Companion.encodeUtf8

/**
 * Utility class hiding K-Anonymity
 */
class KAnonymity(passwordNotToBeStored: String) {
    private val kanonHash: String = passwordNotToBeStored
        .encodeUtf8()   // convert to ByteString
        .sha1()         // SHA-1 hash
        .hex()          // hex string
        .lowercase()    // lowercase

    fun getPrefix(i: Int): String = kanonHash.take(i)

    fun isMatch(prefixLength: Int, breachedPasswordHashes: List<String>): Boolean =
        breachedPasswordHashes.any {
            kanonHash.substring(prefixLength) == it.take(kanonHash.length - prefixLength)
                .lowercase()
        }
}
