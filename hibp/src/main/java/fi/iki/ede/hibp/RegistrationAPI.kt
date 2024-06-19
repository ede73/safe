package fi.iki.ede.hibp

import android.content.Context
import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.safe.R
import fi.iki.ede.safe.splits.GetComposable
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.splits.RegistrationAPI
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.theme.SafeButton

private val TAG = PluginName.HIBP.pluginName

class RegistrationAPIImpl : RegistrationAPI {
    override fun register(context: Context) {
        Log.e(TAG, "RegistrationAPIImpl::register()")
    }

    override fun deregister() {
        Log.e(TAG, "RegistrationAPIImpl::deregister()")
    }

    override fun getName() = PluginName.HIBP

    override fun requestToDeregister(ex: Exception?) {
        // Implementation
        Log.e(TAG, "RegistrationAPIImpl::requestToDeregister()")
    }
}

enum class BreachCheckEnum {
    NOT_CHECKED, BREACHED, NOT_BREACHED
}

class RegistrationAPIProviderImpl : RegistrationAPI.Provider, GetComposable {
    override fun get(): RegistrationAPI {
        Log.e(TAG, "RegistrationAPIProviderImpl::get()")
        return RegistrationAPIImpl()
    }

    @Composable
    override fun getComposable(
        context: Context,
        encryptedPassword: IVCipherText
    ): @Composable () -> Unit {
        return {
            var breachCheckResult by remember(encryptedPassword) {
                mutableStateOf(
                    BreachCheckEnum.NOT_CHECKED
                )
            }
            if (breachCheckResult == BreachCheckEnum.NOT_CHECKED) {
                SafeButton(onClick = {
                    val decrypter = KeyStoreHelperFactory.getDecrypter()
                    BreachCheck.doBreachCheck(
                        KAnonymity(encryptedPassword.decrypt(decrypter)),
                        context,
                        { breached ->
                            breachCheckResult = when (breached) {
                                true -> BreachCheckEnum.BREACHED
                                false -> BreachCheckEnum.NOT_BREACHED
                            }
                        },
                        { error -> Log.e(SiteEntryEditScreen.TAG, "Error: $error") })
                }) { Text(stringResource(id = R.string.password_entry_breach_check)) }
            }
            when (breachCheckResult) {
                BreachCheckEnum.BREACHED -> Text(stringResource(id = R.string.password_entry_breached))
                BreachCheckEnum.NOT_BREACHED -> Text(stringResource(id = R.string.password_entry_not_breached))
                else -> {}
            }
        }
    }
}

/*
                when (breachCheckResult) {
                    BreachCheckEnum.BREACHED -> Text(stringResource(id = R.string.password_entry_breached))
                    BreachCheckEnum.NOT_BREACHED -> Text(stringResource(id = R.string.password_entry_not_breached))
                    else -> {}
                }

 */
