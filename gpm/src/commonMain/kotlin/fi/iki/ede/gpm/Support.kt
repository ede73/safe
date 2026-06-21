package fi.iki.ede.gpm

expect fun isDebug(): Boolean

fun debug(action: () -> Unit) {
    if (isDebug()) action()
}