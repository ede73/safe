package fi.iki.ede.safe.model

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.hexToByteArray
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.toHexString

object Preferences {

    const val PASSWORDSAFE_EXPORT_FILE = "passwordsafe.xml"

    // only used as accessors in SharedPrerefencesChange
    const val PREFERENCE_BACKUP_PATH = "backup_path"

    // only used as accessors in SharedPrerefencesChange
    const val PREFERENCE_BIOMETRICS_ENABLED = "biometrics"

    private const val NOTIFICATION_PERMISSION_REQUIRED = "notification_permission_required"
    private const val PREFERENCE_BACKUP_DOCUMENT = "backup_document"
    private const val PREFERENCE_LOCK_ON_SCREEN_LOCK = "lock_on_screen_lock"
    const val PREFERENCE_LOCK_TIMEOUT = "lock_timeout"
    private const val PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE = "5"
    private const val PREFERENCE_BIO_CIPHER = "bio_cipher"
    private val PREFERENCE_BACKUP_PATH_DEFAULT_VALUE =
        Environment.getExternalStorageDirectory().absolutePath + "/" + PASSWORDSAFE_EXPORT_FILE

    fun getBackupPath(context: Context): String? {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(
                PREFERENCE_BACKUP_PATH,
                PREFERENCE_BACKUP_PATH_DEFAULT_VALUE
            )
    }

    fun setBackupDocumentAndMethod(context: Context, uriString: String?) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putString(PREFERENCE_BACKUP_DOCUMENT, uriString)
        editor.apply()
    }

    fun getLockOnScreenLock(context: Context, default: Boolean) =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(
                PREFERENCE_LOCK_ON_SCREEN_LOCK, default
            )

    fun getLockTimeoutMinutes(
        context: Context
    ) = PreferenceManager.getDefaultSharedPreferences(context).getString(
        PREFERENCE_LOCK_TIMEOUT,
        PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE
    )?.toIntOrNull() ?: PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE.toInt()

    fun setNotificationPermissionRequired(context: Context, value: Boolean) =
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(NOTIFICATION_PERMISSION_REQUIRED, value).apply()

    fun getBiometricsEnabled(context: Context, default: Boolean): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREFERENCE_BIOMETRICS_ENABLED, default)

    fun setBiometricsEnabled(context: Context, value: Boolean) =
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(
                PREFERENCE_BIOMETRICS_ENABLED,
                value
            )
            .apply()

    fun storeBioCipher(
        context: Context,
        cipher: IVCipherText
    ) = PreferenceManager.getDefaultSharedPreferences(context).edit()
        .putString(PREFERENCE_BIO_CIPHER, cipher.combineIVAndCipherText().toHexString())
        .apply()

    fun clearBioCipher(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(PREFERENCE_BIO_CIPHER)
            .apply()

    fun getBioCipher(context: Context): IVCipherText {
        val pm = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREFERENCE_BIO_CIPHER, null) ?: return IVCipherText.getEmpty()
        return IVCipherText(pm.hexToByteArray(), KeyStoreHelper.IV_LENGTH)
    }
}