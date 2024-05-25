package fi.iki.ede.crypto

open class SaltedPassword(val salt: Salt, open val password: Password) : DisallowedFunctions() {
    open fun isEmpty() = salt.isEmpty() || password.isEmpty()

    companion object {
        fun getEmpty(): SaltedPassword =
            SaltedPassword(Salt.getEmpty(), Password.getEmpty())
    }
}