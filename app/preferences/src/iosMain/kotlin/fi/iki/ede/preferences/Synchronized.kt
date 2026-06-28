package fi.iki.ede.preferences

actual inline fun <R> synchronized(lock: Any, block: () -> R): R {
    return block()
}
