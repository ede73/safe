package fi.iki.ede.safe.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.Preferences

// https://developer.android.com/develop/ui/views/touch-and-input/copy-paste
object ClipboardUtils {
    private const val PASSWORD_SAFE = "PasswordSafe"

    // using Clipboard is 'iffy' someone might eavesdrop, even though since Android10
    // random apps reading clipboard are not allowed anymore(input method & current focus app)
    fun addToClipboard(ctx: Context, data: String?) {
        val cp = ClipData.newPlainText(PASSWORD_SAFE, data?.trim() ?: "")
        cp.apply {
            //We're compiled by API 33 or higher
            description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        getClipboardManager(ctx).setPrimaryClip(cp)
        sendClearClipboardBroadcast(ctx)
    }

    /**
     * After certain amount of time, clear the password from clipboard
     */
    fun clearClipboard(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clearClipboardNew(context)
        } else {
            clearClipboardOld(context)
        }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun clearClipboardNew(context: Context) =
        getClipboardManager(context).clearPrimaryClip()

    private fun clearClipboardOld(context: Context) =
        getClipboardManager(context).setPrimaryClip(ClipData.newPlainText(PASSWORD_SAFE, ""))

    private fun getClipboardManager(ctx: Context): ClipboardManager =
        ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun sendClearClipboardBroadcast(context: Context) =
        Handler(Looper.getMainLooper()).postDelayed(
            {
                clearClipboard(context)
                // Only show a toast for Android 12 and lower.
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    Toast.makeText(
                        context, context.getString(R.string.clipboard_cleared),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            (Preferences.getClipboardClearDelaySecs() * 1000).toLong()
        )
}