package fi.iki.ede.autolock

import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object NavigationBarHelper {
    fun enableDrawingBehindNavigationBar(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    fun hideNavigationBar(window: Window) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hide(WindowInsetsCompat.Type.navigationBars())
            } else {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            }
        }
    }
}