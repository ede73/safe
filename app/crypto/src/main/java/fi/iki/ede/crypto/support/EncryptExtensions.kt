package fi.iki.ede.crypto.support

import android.graphics.Bitmap
import android.util.Base64
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import java.io.ByteArrayOutputStream

// TODO: move to crypto project
fun Password.encrypt(ks: KeyStoreHelper) =
    ks.encryptByteArray(String(this.utf8password).toByteArray())

// TODO: move to crypto project
fun String.encrypt(ks: KeyStoreHelper) = ks.encryptByteArray(this.trim().toByteArray())

//fun Bitmap.encryptNEW(ks: KeyStoreHelper) = ByteArrayOutputStream().let { ba ->
//    this.compress(Bitmap.CompressFormat.JPEG, 100, ba)
//    ks.encryptByteArray(ba.toByteArray())
//}

// TODO: just because DecryptableSiteEntry uses IVCipher and its decrypt returns String
// ie. it doesnt support byte[] storage directly, so convert bitmap to base64 ie.string
fun Bitmap.encrypt(ks: KeyStoreHelper) = makeBase64(this).encrypt(ks)

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

