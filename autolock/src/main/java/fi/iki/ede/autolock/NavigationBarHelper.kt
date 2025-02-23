package fi.iki.ede.autolock

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

object NavigationBarHelper {
    fun enableDrawingBehindNavigationBar(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    fun hideNavigationBar(window: Window) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}