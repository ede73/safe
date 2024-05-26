package fi.iki.ede.safe.model

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.hexToByteArray
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.toHexString

object Preferences {
    // only used as accessors in SharedPrerefencesChange
    private const val PASSWORDSAFE_EXPORT_FILE = "passwordsafe.xml"

    // See ExportConfig for woes
    const val SUPPORT_EXPORT_LOCATION_MEMORY = false

    @Deprecated(
        "Mustn't be used, no point for now, see ExportConfig/SUPPORT_EXPORT_LOCATION_MEMORY",
        level = DeprecationLevel.WARNING
    )
    const val PREFERENCE_BACKUP_DOCUMENT = "backup_document"
    val PREFERENCE_BACKUP_PATH_DEFAULT_VALUE =
        Environment.getExternalStorageDirectory().absolutePath + "/" + PASSWORDSAFE_EXPORT_FILE
    const val PREFERENCE_BIOMETRICS_ENABLED = "biometrics"
    const val PREFERENCE_LOCK_TIMEOUT = "lock_timeout"
    private const val PREFERENCE_DEFAULT_USER_NAME = "default_user_name"
    private const val NOTIFICATION_PERMISSION_REQUIRED = "notification_permission_required"
    private const val PREFERENCE_BIO_CIPHER = "bio_cipher"
    private const val PREFERENCE_LOCK_ON_SCREEN_LOCK = "lock_on_screen_lock"
    private const val PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE = "5"

    fun getBackupDocument(context: Context): String {
        if (SUPPORT_EXPORT_LOCATION_MEMORY) {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(
                    PREFERENCE_BACKUP_DOCUMENT,
                    PREFERENCE_BACKUP_PATH_DEFAULT_VALUE
                ) ?: PREFERENCE_BACKUP_PATH_DEFAULT_VALUE
        } else {
            return PREFERENCE_BACKUP_PATH_DEFAULT_VALUE
        }
    }

    fun setBackupDocument(context: Context, uriString: String?) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putString(PREFERENCE_BACKUP_DOCUMENT, uriString)
        editor.apply()
    }

    fun getDefaultUserName(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(
                PREFERENCE_DEFAULT_USER_NAME, ""
            ) ?: ""

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

    // We're checking notification permission in service (countdown timer)
    // if missing, we'll flag here to request the permission when user is
    // at screen (ie. from activity)
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