package fi.iki.ede.cryptoobjects

actual typealias PlatformBitmap = android.graphics.Bitmap

// Addressed PR5 comment: Implement actual sameAs for Android using Bitmap.sameAs
actual fun sameAs(a: PlatformBitmap?, b: PlatformBitmap?): Boolean {
    return a?.sameAs(b) ?: (b == null)
}
