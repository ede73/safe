package fi.iki.ede.gpm

fun debug(action: () -> Unit) {
    if (BuildConfig.DEBUG) action()
}