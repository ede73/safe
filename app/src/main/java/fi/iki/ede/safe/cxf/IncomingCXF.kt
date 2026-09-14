package fi.iki.ede.safe.cxf

import fi.iki.ede.crypto.support.DisallowedFunctions
import fi.iki.ede.gpm.changeset.calculateSha128

data class IncomingCXF(
    val cxfItemId: String,
    val cxfAccountId: String,
    val cxfAccountEmail: String,
    val credentialType: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val rawCredentialJson: String = "",
    val note: String,
    val creationAt: Long? = null,
    val modifiedAt: Long? = null,
    val importedAt: Long = System.currentTimeMillis(),
    val hash: String
) : DisallowedFunctions {

    override fun toString(): String {
        return "IncomingCXF (cxfItemId=$cxfItemId, cxfAccountId=$cxfAccountId, type=$credentialType, name=$name, url=$url, username=$username, password=REDACTED, importedAt=$importedAt, hash=$hash)"
    }

    companion object {
        fun make(
            cxfItemId: String,
            cxfAccountId: String,
            cxfAccountEmail: String,
            credentialType: String,
            name: String,
            url: String,
            username: String,
            password: String,
            rawCredentialJson: String = "",
            note: String,
            creationAt: Long? = null,
            modifiedAt: Long? = null,
            importedAt: Long = System.currentTimeMillis()
        ): IncomingCXF =
            IncomingCXF(
                cxfItemId = cxfItemId,
                cxfAccountId = cxfAccountId,
                cxfAccountEmail = cxfAccountEmail,
                credentialType = credentialType,
                name = name,
                url = url,
                username = username,
                password = password,
                rawCredentialJson = rawCredentialJson,
                note = note,
                creationAt = creationAt,
                modifiedAt = modifiedAt,
                importedAt = importedAt,
                hash = calculateSha128(
                    listOf(
                        "cxfItemId=$cxfItemId",
                        "cxfAccountId=$cxfAccountId",
                        "name=$name",
                        "url=$url",
                        "username=$username",
                        "password=$password",
                        "type=$credentialType"
                    ), "makeIncomingCXF"
                )
            )
    }
}
