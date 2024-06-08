package fi.iki.ede.gpm.model

import fi.iki.ede.gpm.changeset.calculateSha128

data class IncomingGPM private constructor(
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val note: String,
    val hash: String
) {
    override fun toString(): String {
        return "IncomingGPM (name=${name}, url=${url}, username=${username}, password=${password}, node=$note, hash=$hash))"
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
                calculateSha128(listOf(name, url, username, password, note), "makeFromCSVImport")
            )
    }
}