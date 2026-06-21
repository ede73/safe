package fi.iki.ede.safe.password

import fi.iki.ede.crypto.keystore.fillRandomBytes
import kotlin.math.abs
import kotlin.math.min

const val PG_SYMBOLS = "!@#$%^&*()[]{}:;'\"/><.,-_=+~"

object PasswordGenerator {

    // korlibs SecureRandom.nextInt() is broken (always returns 0),
    // but nextBytes() works correctly on all KMP platforms
    private fun secureNextInt(bound: Int): Int {
        require(bound > 0)
        val bytes = ByteArray(4)
        fillRandomBytes(bytes)
        val raw = ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
        return abs(raw % bound)
    }

    fun genPassword(
        passUpper: Boolean,
        passLower: Boolean,
        passNum: Boolean,
        passSymbols: Boolean,
        symbols: String = PG_SYMBOLS,
        length: Int
    ): String {
        val upperCases = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowerCases = "abcdefghijklmnopqrstuvwxyz"
        val numbers = "0123456789"
        val minSymbols = if (passSymbols) min(2, symbols.length) else 0
        // just refuse to make less than 8 char passwords
        if (length < 8) {
            return ""
        }
        val charset = buildString {
            append(if (passUpper) upperCases else "")
            append(if (passLower) lowerCases else "")
            append(if (passNum) numbers else "")
            append(if (passSymbols) symbols else "")

        }
        if (charset.isEmpty()) {
            return ""
        }

        val pass = StringBuilder()
        do {
            pass.clear()
            for (i in 0 until length) {
                val pos = secureNextInt(charset.length)
                // kinda defeats the randomness, but let's ensure
                // we've no duplicates
                val candidate = charset[pos]
                if (!pass.contains(candidate)) {
                    pass.append(candidate)
                }
            }
            // also ensure there's at least 2(*) characters from each class
            // since we randomly generate the password, there's a probability that all are
            // say just numbers
            // (*) except symbols IF passed symbol list<2
        } while ((passUpper && pass.count { character -> upperCases.contains(character) } < 2) ||
            (passLower && pass.count { character -> lowerCases.contains(character) } < 2) ||
            (passNum && pass.count { character -> numbers.contains(character) } < 2) ||
            (passSymbols && pass.count { character -> symbols.contains(character) } < minSymbols)
        )

        return pass.toString()
    }
}