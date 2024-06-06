package fi.iki.ede.crypto.support

/**
 * List of functions one might accidentally use and expose unwanted information
 */
abstract class DisallowedFunctions {
    @Deprecated("Not allowed")
    override fun toString(): String {
        throw Exception("toString() not allowed")
    }
}
