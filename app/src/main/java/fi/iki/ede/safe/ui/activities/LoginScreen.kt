package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.autolock.AutolockingService
import fi.iki.ede.backup.MyBackupAgent
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBHelper.Companion.DATABASE_NAME
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.gpmdatamodel.db.GPMDB
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.splits.PluginManager
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.LoginScreenCompose
import fi.iki.ede.safe.ui.utilities.startActivityForResults
import fi.iki.ede.theme.SafeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// This login logic is bit complex:
// But basically there are two outcomes:
//  - login (try) with password
//  - enter new password, which reinitializes DB and master key
enum class LoginStyle {
    FIRST_TIME_LOGIN_CLEAR_DATABASE,
    EXISTING_LOGIN
}

// And preconditions basically two:
// - we have no(actually empty) database
// - we have data base (with masterkey)
//   - why? because we've gone thru first part and this is 'relogin'
//   - or! app was perhap uninstalled, reinstalled and Google Autobackup gave us OLD database
//      - we can accept this and try to login
//      - or we can just discard it and make totally new one (everyone has backups right?)
enum class LoginPrecondition {
    FIRST_TIME_LOGIN_EMPTY_DATABASE,
    NORMAL_LOGIN,
    FIRST_TIME_LOGIN_RESTORED_DATABASE
}

open class LoginScreen : ComponentActivity() {
    private var openCategoryListScreen: Boolean = true
    private val biometricsFirstTimeRegister = biometricsFirstTimeActivity()
    private val biometricsVerify = biometricsVerifyActivity()
    private var serviceConnection: ServiceConnection? = null

    override fun onDestroy() {
        super.onDestroy()
        if (serviceConnection != null) {
            unbindService(serviceConnection!!)
            serviceConnection = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openCategoryListScreen = intent.getBooleanExtra(
            OPEN_CATEGORY_SCREEN_AFTER_LOGIN, true
        )
        if (BuildConfig.DEBUG) {
            val isBundleTest = intent.getStringExtra("isBundleTest") == "true"
            PluginManager.setBundleTestMode(isBundleTest)
        }
        // "upgrade" from old versions where first time login was solely
        // determined by existence of passwords in the database
        // we don't really know if this is first time login or not
        // (unless we literally DO go snooping in the database)

        if (BuildConfig.DEBUG) {
            if (intent.getStringExtra("WipeKeyStore") == "true") {
                val ks = KeyStoreHelperFactory.getKeyStoreHelper()
                ks.testingDeleteKeys_DO_NOT_USE()
                finish()
                return
            }
        }

        setContent {
            LoginScreenCompose(
                getLoginScreenPrecondition(),
                ::goodPasswordEntered,
                biometricsVerify
            )
        }
    }

    private fun getLoginScreenPrecondition(): LoginPrecondition =
        when (haveMasterkeyInDatabase() to isGoodRestoredContent(this)) {
            false to false -> LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE
            false to true -> LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE
            true to false -> LoginPrecondition.NORMAL_LOGIN
            true to true -> LoginPrecondition.FIRST_TIME_LOGIN_RESTORED_DATABASE
            else -> {
                // interesting linter error, 2 booleans can only make 4 states
                throw Exception("Unexpected state")
            }
        }

    private fun goodPasswordEntered(
        loginStyle: LoginStyle,
        it: Password,
    ): Boolean {
        val context = this
        val passwordIsAccepted = if (loginStyle == LoginStyle.FIRST_TIME_LOGIN_CLEAR_DATABASE) {
            context.deleteDatabase(DATABASE_NAME)
            DBHelperFactory.initializeDatabase(
                DBHelper(
                    context, DATABASE_NAME, true,
                    GPMDB::getExternalTables,
                    GPMDB::upgradeTables,
                )
            )
            LoginHandler.firstTimeLogin(it)
            true
        } else {
            LoginHandler.passwordLogin(context, it)
        }

        if (passwordIsAccepted) {
            val registerBiometricsActivity =
                (loginStyle == LoginStyle.FIRST_TIME_LOGIN_CLEAR_DATABASE && BiometricsActivity.isBiometricEnabled())
                        || (BiometricsActivity.isBiometricEnabled() &&
                        !BiometricsActivity.haveRecordedBiometric())
            if (registerBiometricsActivity) {
                biometricsFirstTimeRegister.launch(BiometricsActivity.getRegistrationIntent(context))
            } else {
                finishLoginProcess()
            }
        }
        return passwordIsAccepted
    }

    private fun finishLoginProcess() {
        beginToLoadDB()
        MyBackupAgent.removeRestoreMark(this)

        // We've LOGGED IN, so we must have master key ready and done
        // Mitigate coming from old client where first time login preference
        // wasn't used
        setResult(RESULT_OK, Intent())
        serviceConnection = AutolockingService.startAutolockingService(
            this,
            AutolockingFeaturesImpl,
            applicationContext
        )
        if (openCategoryListScreen) {
            IntentManager.startCategoryScreen(this)
        }
        finish()
    }

    private fun beginToLoadDB() {
        val myScope = CoroutineScope(Dispatchers.Main)
        myScope.launch {
            withContext(Dispatchers.IO) {
                DataModel.loadFromDatabase({
                    GPMDataModel.loadFromDatabase()
                })
            }
        }
    }

    private fun biometricsFirstTimeActivity() =
        startActivityForResults(TestTag.LOGIN_BIOMETRICS_REGISTER) { result ->
            // This is FIRST TIME call..we're just about to be set up...
            when (result.resultCode) {
                RESULT_OK -> {
                    BiometricsActivity.registerBiometric()
                    finishLoginProcess()
                }

                RESULT_CANCELED -> {
                    // We should fall back asking password but NOT disable biometrics
                }
                // may be called many times..eventually gets cancelled
                BiometricsActivity.RESULT_FAILED -> {}
            }
        }

    private fun biometricsVerifyActivity() =
        startActivityForResults(TestTag.LOGIN_BIOMETRICS_VERIFY) { result ->
            when (result.resultCode) {
                RESULT_OK -> {
                    if (!BiometricsActivity.verificationAccepted()) {
                        // should never happen
                        Log.e("---", "Biometric verification NOT accepted - perhaps a new backup?")
                    } else {
                        finishLoginProcess()
                    }
                }

                RESULT_CANCELED -> {
                    // We should fall back asking password but NOT disable biometrics
                }

                BiometricsActivity.RESULT_FAILED -> {
                    // Sometimes fingerprint reading just doesn't work
                    // This will be called for N subsequent misreads
                    // Then biometrics will be cancelled
                }
            }
        }

    companion object {
        const val OPEN_CATEGORY_SCREEN_AFTER_LOGIN = "open_category_screen"
    }
}

private fun haveMasterkeyInDatabase(): Boolean {
    val db = DBHelperFactory.getDBHelper()
    val (salt, cipheredMasterKey) = try {
        db.fetchSaltAndEncryptedMasterKey()
    } catch (n: Exception) {
        Salt.getEmpty() to IVCipherText.getEmpty()
    }
    if (salt.isEmpty()) return false
    if (cipheredMasterKey.isEmpty()) return false
    return true
}

fun isGoodRestoredContent(context: Context) =
    if (!MyBackupAgent.haveRestoreMark(context)) false
    else haveMasterkeyInDatabase()


@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    SafeTheme {
        LoginScreenCompose(
            LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE,
            goodPasswordEntered = { _, _ -> /* No-op for preview */ true },
        )
    }
}