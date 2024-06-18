package fi.iki.ede.safe.splits

import android.content.Context
import androidx.compose.runtime.Composable
import fi.iki.ede.crypto.IVCipherText

interface GetComposable {
    @Composable
    fun getComposable(context: Context, encryptedPassword: IVCipherText): @Composable () -> Unit
}