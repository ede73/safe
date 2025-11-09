package fi.iki.ede.gpm.csv

import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString
import okio.BufferedSource
import okio.IOException

fun readCsv(inputStream: BufferedSource): Set<IncomingGPM> =
    inputStream.let { reader ->
        val header = reader.readUtf8Line()!!.toLowerCasedTrimmedString()
        if (header == "name,url,username,password,note".toLowerCasedTrimmedString()) {
            throw IOException("Unrecognized Google Password manager format")
        }
        generateSequence { reader.readUtf8Line() }
            .filter { it.isNotBlank() }
            .map { it ->
                val (name, url, username, password, note) = processInputLine(it, 5)
                IncomingGPM.makeFromCSVImport(
                    name.trim(),
                    url.trim(),
                    username.trim(),
                    password.trim(),
                    note.trim()
                )
            }.toSet()
    }

fun processInputLine(givenInput: String, expectItems: Int): List<String> {
    val expectCommas = expectItems - 1

    val input = givenInput.count { it == ',' }.let {
        if (it < expectCommas)
            givenInput + ",".repeat(expectCommas - it).trim()
        else
            givenInput.trim()
    }

    val seenCommas = input.count { it == ',' }
    val inputHasQuotes = input.contains("\"")

    val params = input.split(",").toMutableList()

    // easy case
    if (!inputHasQuotes && seenCommas == expectCommas) {
        return params.map { removeSurroundingQuotesAndFlatten(it) }
    }

    // try combine fields with quotes
    val iterator = params.listIterator()
    while (iterator.hasNext()) {
        val current = iterator.next()
        if (current.startsWith("\"") && iterator.hasNext()) {
            val next = iterator.next()
            if (next.endsWith("\"")) {
                iterator.remove() // Remove 'next' element
                iterator.previous() // Move back to 'current'
                iterator.set("$current,$next") // Combine and replace 'current'
            } else {
                iterator.previous() // Move back to the position before 'next'
            }
        }
    }

    while (params.size < expectItems) {
        params.add("")
    }
    // we know the FIRST column works always (record name)
    // and we know last 3 work too (username, password, note
    val head = mutableListOf<String>()
    val tail = mutableListOf<String>()
    // SDK 35 has bug with removeFirst
    head.add(params.removeFirstOrNull()!!)

    tail.add(params.removeLastOrNull()!!)
    tail.add(params.removeLastOrNull()!!)
    tail.add(params.removeLastOrNull()!!)

    // This is REALLY google specific reader
    // GPM export usually adds quotes " correctly, but
    // for instance for this web site, not
    // https://www.apress.com/customer/account/login/referer/...0L2luZGV4Lw,,/
    // YES, it really has ,,/ in it!
    // so..for this to be import correctly, we have to process the commas from every side

    // ANYTHING in the params is a URL
    val url = params.joinToString(",")

    return head.map { removeSurroundingQuotesAndFlatten(it) } + removeSurroundingQuotesAndFlatten(
        url
    ) + tail.reversed().map {
        removeSurroundingQuotesAndFlatten(it)
    }
}

fun removeSurroundingQuotesAndFlatten(input: String) =
    input.removeSurrounding("\"").replace("\"\"", "\"").trim()
