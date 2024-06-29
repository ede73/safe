package fi.iki.ede.crypto.support

/**
 * List of functions one might accidentally use and expose unwanted information
 */
abstract class DisallowedFunctions {
    @Deprecated("Not allowed", ReplaceWith("Custom print routine, excluding sensitive information"))
    override fun toString(): String {
        throw Exception("toString() not allowed")
    }
}
