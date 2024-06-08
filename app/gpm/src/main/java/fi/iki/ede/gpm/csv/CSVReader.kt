package fi.iki.ede.gpm.csv

import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString
import java.io.IOException
import java.io.InputStream

fun splitInputLine(input: String): List<String> =
    input.trim().split(',', ignoreCase = false, limit = 5).map {
        it.removeSurrounding("\"").trim().removeSurrounding("\"").trim()
    }

fun readCsv(inputStream: InputStream): Set<IncomingGPM> =
    inputStream.bufferedReader().let { reader ->
        val header = reader.readLine().toLowerCasedTrimmedString()
        if (header == "name,url,username,password,note".toLowerCasedTrimmedString()) {
            throw IOException("Unrecognized Google Password manager format")
        }
        reader.lineSequence()
            .filter { it.isNotBlank() }
            .map { it ->
                val (name, url, username, password, note) = splitInputLine(it).let { l -> l + List(5 - l.size) { "" } }
                IncomingGPM.makeFromCSVImport(name.trim(), url.trim(), username.trim(), password.trim(), note.trim())
            }.toSet()
    }
