package fi.iki.ede.safe.password

import java.security.SecureRandom

object PasswordGenerator {
    fun genPassword(
        passUpper: Boolean,
        passLower: Boolean,
        passNum: Boolean,
        passSymbol: Boolean,
        length: Int
    ): String {
        val upperCases = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowerCases = "abcdefghijklmnopqrstuvwxyz"
        val numbers = "0123456789"
        val symbols = "!@#\$%^&*()[]{}:;'\"/><.,-_=+~"

        // just refuse to make less than 8 char pwds
        if (length < 8) {
            return ""
        }
        val charset = buildString {
            append(if (passUpper) upperCases else "")
            append(if (passLower) lowerCases else "")
            append(if (passNum) numbers else "")
            append(if (passSymbol) symbols else "")

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
            // also ensure there's at least 2 characters from each class
            // since we randomly generate the password, there's a probability that all are
            // say just numbers
        } while ((passUpper && pass.count { character -> upperCases.contains(character) } < 2) ||
            (passLower && pass.count { character -> lowerCases.contains(character) } < 2) ||
            (passNum && pass.count { character -> numbers.contains(character) } < 2) ||
            (passSymbol && pass.count { character -> symbols.contains(character) } < 2)
        )

        return pass.toString()
    }
}