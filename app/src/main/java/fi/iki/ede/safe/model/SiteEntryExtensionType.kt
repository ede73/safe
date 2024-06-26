package fi.iki.ede.safe.model

enum class SiteEntryExtensionType(val extensionName: String) {
    PHONE_NUMBERS("phones"),
    PAYMENTS("payments"),
    AUTHENTICATORS("authenticators"),
    EMAILS("emails")
}
