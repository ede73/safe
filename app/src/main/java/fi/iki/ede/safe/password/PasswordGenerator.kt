package fi.iki.ede.safe.password

import java.security.SecureRandom // KMP
import kotlin.math.min

const val PG_SYMBOLS = "!@#$%^&*()[]{}:;'\"/><.,-_=+~"

object PasswordGenerator {
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
        val generator = SecureRandom()
        val pass = StringBuilder()
        do {
            pass.clear()
            for (i in 0 until length) {
                val pos = generator.nextInt(charset.length)
                // kinda defeats the randomness, but let's ensure
                // we've no duplicates
                val candidate = charset[pos]
                if (!pass.contains(candidate)) {
                    pass.append(charset[pos])
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