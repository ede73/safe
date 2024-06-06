package fi.iki.ede.crypto.support

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import fi.iki.ede.crypto.BuildConfig
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelper

fun IVCipherText.decrypt(ks: KeyStoreHelper) = String(ks.decryptByteArray(this))

fun DecryptableSiteEntry.decryptPhoto(ks: KeyStoreHelper) = try {
    val base64Photo = photo.decrypt(ks)
    val imageBytes = Base64.decode(base64Photo, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
} catch (ex: Exception) {
    if (BuildConfig.DEBUG) {
        Log.e("decryptPhoto", ex.toString())
    }
    null
}
