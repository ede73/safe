package fi.iki.ede.preferences

expect inline fun <R> synchronized(lock: Any, block: () -> R): R
