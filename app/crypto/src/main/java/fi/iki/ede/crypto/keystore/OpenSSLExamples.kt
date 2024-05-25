package fi.iki.ede.crypto.keystore

import android.util.Log
import fi.iki.ede.crypto.BuildConfig
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.toHexString
import java.util.Locale
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object OpenSSLExamples {
    @Suppress("SameReturnValue")
    private val OPENSSL_EXAMPLES: Boolean
        get() {
            check(BuildConfig.DEBUG)
            // DO NOT ENABLE THIS for any real use! It WILL print secret keys to the log
            // And keep this ugly getter, it will guarantee to fail if not in debug mode
            return false
        }

    // Use to log sensitive data during development. Using this in release intentionally crashes
    private fun sensitiveLog(superSensitiveMessage: String) {
        check(BuildConfig.DEBUG) { "You can't, shouldn't and mustn't log sensitive data" }
        // Double ensure this will NOT run anywhere else than in debug mode
        if (BuildConfig.DEBUG) {
            Log.i("SENSITIVE_DATA", superSensitiveMessage)
        }
    }


    fun debugPBKDFAESKey(
        password: Password,
        salt: Salt,
        iterationCount: Int,
        keyBytes: ByteArray
    ) {
        if (BuildConfig.DEBUG && OPENSSL_EXAMPLES) {
            sensitiveLog(
                "OpenSSL counterpart:\n" +
                        "openssl enc -aes-256-cbc -k ${String(password.password)} -P -md sha256 -S ${salt.toHex()} -iter $iterationCount -pbkdf2\n" +
                        "SALT=${salt.toHex().uppercase(Locale.getDefault())}\n" +
                        "KEY=${
                            keyBytes.toHexString().uppercase(Locale.getDefault())
                        }\n"
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun makeNewKey(iv: ByteArray, ciphertext: ByteArray) {
        if (BuildConfig.DEBUG && OPENSSL_EXAMPLES) {
            val previousRun =
                "openssl enc -aes-256-cbc -P -md sha256 -S PREVIOUSSALT -iter 20000 -pbkdf2|grep key|cut -d= -f2"
            sensitiveLog(
                "OpenSSL counterpart:\n" +
                        "#PKDF2 encrypted secret key is ${iv.toHexString()} ${ciphertext.toHexString()}\n" +
                        "echo ${Base64.encode(ciphertext)}| openssl enc -aes-256-cbc -d -a -iv ${iv.toHexString()} -K  \$($previousRun) -nosalt|xxd -p -c 255\n" + "" +
                        "# Should match above key if you remember the password"
            )
        }
    }

    fun dumpAESKey(truelySecretAESKey: ByteArray) {
        if (BuildConfig.DEBUG && OPENSSL_EXAMPLES) {
            sensitiveLog("Actual random AES key:\n${truelySecretAESKey.toHexString()}\n")
        }
    }
}