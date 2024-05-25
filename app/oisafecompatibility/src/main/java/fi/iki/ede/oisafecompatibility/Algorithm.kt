package fi.iki.ede.oisafecompatibility

@Deprecated("Just for backwards compatibility")
enum class Algorithm(val algorithm: String) {
    // Used in app + in the database
    IN_MEMORY_INTERNAL("PBEWithMD5And128BitAES-CBC-OpenSSL"),

    // Used for exporting passwords as XML
    EXTERNAL_OLD("PBEWithSHA1And256BitAES-CBC-BC");
}