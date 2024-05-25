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
import fi.iki.ede.safe.R

// https://developer.android.com/develop/ui/views/touch-and-input/copy-paste
object ClipboardUtils {
    private const val PASSWORD_SAFE = "PasswordSafe"

    // TODO: Add to prefs
    private const val CLEAR_CLIPBOARD_TIMEOUT_SECONDS = 15
    private fun getClipboardManager(ctx: Context): ClipboardManager {
        return ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    /**
     * After certain amount of time, clear the password from clipboard
     */
    fun clearClipboard(context: Context) {
        getClipboardManager(context).setPrimaryClip(ClipData.newPlainText(PASSWORD_SAFE, ""))
    }

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

    private fun sendClearClipboardBroadcast(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(
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
            (CLEAR_CLIPBOARD_TIMEOUT_SECONDS * 1000).toLong()
        )
    }
}