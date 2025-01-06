package fi.iki.ede.cryptoobjects

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.decrypt

fun DecryptableSiteEntry.decryptPhoto(decrypter: (IVCipherText) -> ByteArray) =
    try {
        val base64Photo = photo.decrypt(decrypter)
        val imageBytes = Base64.decode(base64Photo, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (ex: Exception) {
        if (BuildConfig.DEBUG) {
            Log.e("decryptPhoto", ex.toString())
        }
        null
    }
