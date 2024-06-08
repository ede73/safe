package fi.iki.ede.gpm.similarity

import kotlin.math.max
import kotlin.math.min

private fun getLevenshteinDistance(xl: LowerCaseTrimmedString, yl: LowerCaseTrimmedString): Int {
    val x = xl.lowercasedTrimmed
    val y = yl.lowercasedTrimmed
    val m = x.length
    val n = y.length
    val t = Array(m + 1) { IntArray(n + 1) }

    for (i in 1..m) {
        t[i][0] = i
    }
    for (j in 1..n) {
        t[0][j] = j
    }

    var cost: Int
    for (i in 1..m) {
        for (j in 1..n) {
            cost = if (x[i - 1] == y[j - 1]) 0 else 1
            t[i][j] = min(min(t[i - 1][j] + 1, t[i][j - 1] + 1), t[i - 1][j - 1] + cost)
        }
    }
    return t[m][n]
}

class LowerCaseTrimmedString(val lowercasedTrimmed: String) {
    companion object {
        fun from(value: String): LowerCaseTrimmedString {
            return LowerCaseTrimmedString(value.lowercase().trim())
        }
    }
}

fun String.toLowerCasedTrimmedString(): LowerCaseTrimmedString = LowerCaseTrimmedString.from(this)

fun findSimilarity(x: LowerCaseTrimmedString, y: LowerCaseTrimmedString): Double {
    val maxLength = max(x.lowercasedTrimmed.length, y.lowercasedTrimmed.length)
    return if (maxLength > 0) {
        (maxLength - getLevenshteinDistance(x, y)).toDouble() / maxLength
    } else 1.0
}
