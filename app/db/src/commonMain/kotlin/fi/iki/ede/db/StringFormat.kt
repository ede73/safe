package fi.iki.ede.db

fun String.format(vararg args: Any?): String {
    var result = this
    for (arg in args) {
        val hexStr = when (arg) {
            is Long -> arg.toULong().toString(16).padStart(16, '0')
            else -> arg?.toString() ?: ""
        }
        val index = result.indexOf('%')
        if (index != -1) {
            val specifierEnd = result.indexOfAny(charArrayOf('x', 'd', 's', 'f'), index)
            if (specifierEnd != -1) {
                result = result.substring(0, index) + hexStr + result.substring(specifierEnd + 1)
            } else {
                result = result.substring(0, index) + hexStr + result.substring(index + 1)
            }
        }
    }
    return result
}
