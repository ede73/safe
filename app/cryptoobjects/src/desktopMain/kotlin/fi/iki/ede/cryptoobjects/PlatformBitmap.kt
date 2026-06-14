package fi.iki.ede.cryptoobjects

actual class PlatformBitmap

// Addressed PR5 comment: Implement actual sameAs for Desktop (dummy comparison)
actual fun sameAs(a: PlatformBitmap?, b: PlatformBitmap?): Boolean {
    return (a == null) && (b == null)
}
