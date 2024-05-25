package fi.iki.ede.crypto

abstract class DisallowedFunctions {
    @Deprecated("Not allowed")
    override fun toString(): String {
        throw Exception("toString() not allowed")
    }
}
