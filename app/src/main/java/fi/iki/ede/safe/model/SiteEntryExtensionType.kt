package fi.iki.ede.safe.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SiteEntryExtensionType(val extensionName: String) {
    @SerialName("phones")
    PHONE_NUMBERS("phones"),

    @SerialName("payments")
    PAYMENTS("payments"),

    @SerialName("authenticators")
    AUTHENTICATORS("authenticators"),

    @SerialName("emails")
    EMAILS("emails")
}
