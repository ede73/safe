package fi.iki.ede.cryptoobjects

actual class PlatformBitmap

actual fun sameAs(a: PlatformBitmap?, b: PlatformBitmap?): Boolean {
    return (a == null) && (b == null)
}
