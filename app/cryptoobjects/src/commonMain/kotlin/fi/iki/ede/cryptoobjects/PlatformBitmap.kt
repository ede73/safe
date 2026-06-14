package fi.iki.ede.cryptoobjects

// Addressed PR5 comment: Declare PlatformBitmap expect class and sameAs helper in commonMain
expect class PlatformBitmap

expect fun sameAs(a: PlatformBitmap?, b: PlatformBitmap?): Boolean
