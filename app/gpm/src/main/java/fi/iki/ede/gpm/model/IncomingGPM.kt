package fi.iki.ede.gpm.model

import fi.iki.ede.crypto.support.DisallowedFunctions
import fi.iki.ede.gpm.changeset.calculateSha128

data class IncomingGPM private constructor(
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val note: String,
    val hash: String
) : DisallowedFunctions() {
    fun toStringRedacted(): String {
        return "IncomingGPM (name=${name}, url=${url}, username=${username}, password=REDACTED, note=$note, hash=$hash))"
    }

    companion object {
        fun makeFromCSVImport(
            name: String,
            url: String,
            username: String,
            password: String,
            note: String
        ): IncomingGPM =
            IncomingGPM(
                name,
                url,
                username,
                password,
                note,
                calculateSha128(
                    // consider position and emptiness :)
                    listOf(
                        "name=$name",
                        "url=$url",
                        "username=$username",
                        "password=$password",
                        "note=$note"
                    ), "makeFromCSVImport"
                )
            )
    }
}