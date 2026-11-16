object SafeLocalSigning {
    const val KEYSTORE_PASSWORD_SERVICE = "Safe Android upload keystore password"
    const val PRIVATE_KEY_PASSWORD_SERVICE = "Safe Android upload key password"

    fun passwordServices(keystoreType: String): List<String> =
        if (keystoreType.equals("PKCS12", ignoreCase = true)) {
            listOf(KEYSTORE_PASSWORD_SERVICE)
        } else {
            listOf(KEYSTORE_PASSWORD_SERVICE, PRIVATE_KEY_PASSWORD_SERVICE)
        }

    fun hasNonEmptyPassword(exitCode: Int, output: String): Boolean =
        exitCode == 0 && output.trimEnd('\r', '\n').isNotEmpty()

}
