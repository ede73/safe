package fi.iki.ede.safe.ui.composable

import android.content.Context
import androidx.compose.runtime.Composable
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.safe.splits.PluginManager
import fi.iki.ede.safe.splits.PluginName
import kotlin.time.ExperimentalTime

@Composable
@ExperimentalTime
fun breachCheckButton(
    context: Context,
    encryptedPassword: IVCipherText
): @Composable () -> Unit = {
    if (PluginManager.isPluginEnabled(PluginName.HIBP))
        PluginManager.getComposableInterface(PluginName.HIBP)
            ?.getComposable(context, encryptedPassword)
            ?.invoke()
}