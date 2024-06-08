package fi.iki.ede.crypto.support

import android.graphics.Bitmap
import android.util.Base64
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import java.io.ByteArrayOutputStream

fun Password.encrypt(encrypter: (ByteArray) -> IVCipherText) =
    encrypter(String(this.utf8password).toByteArray())

fun String.encrypt(encrypter: (ByteArray) -> IVCipherText) = encrypter(this.trim().toByteArray())

// TODO: just because DecryptableSiteEntry uses IVCipher and its decrypt returns String
// ie. it doesnt support byte[] storage directly, so convert bitmap to base64 ie.string
fun Bitmap.encrypt(encrypter: (ByteArray) -> IVCipherText) = makeBase64(this).encrypt(encrypter)

private fun makeBase64(bitmap: Bitmap?): String {
    if (bitmap == null) {
        return ""
    }
    ByteArrayOutputStream().apply {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, this)
        val byteArray = toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
}

