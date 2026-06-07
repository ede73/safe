package fi.iki.ede.cryptoobjects

import android.graphics.BitmapFactory
import android.util.Base64
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.logger.Logger
import kotlin.time.ExperimentalTime

@ExperimentalTime
actual fun DecryptableSiteEntry.decryptPhoto(): PlatformBitmap? =
    try {
        val base64Photo = photo.decrypt()
        val bitmapOrJpeg = Base64.decode(base64Photo, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bitmapOrJpeg, 0, bitmapOrJpeg.size)
    } catch (ex: Exception) {
        Logger.e("decryptPhoto", ex.toString())
        null
    }
