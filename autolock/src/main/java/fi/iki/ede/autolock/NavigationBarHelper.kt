package fi.iki.ede.autolock

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

object NavigationBarHelper {
    private val hideHandler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    fun enableDrawingBehindNavigationBar(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hideNavigationBar(window)
            // Listen for window insets changes
            window.decorView.setOnApplyWindowInsetsListener { _, insets ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (insets.isVisible(WindowInsetsCompat.Type.navigationBars())) {
                        // Navigation bar is visible, start the timer
                        scheduleHideNavigationBar(window)
                    } else {
                        // Navigation bar is hidden, cancel the timer
                        cancelHideNavigationBar()
                    }
                }
                insets
            }
        }
    }

    private fun hideNavigationBar(window: Window) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }

    private fun scheduleHideNavigationBar(window: Window) {
        cancelHideNavigationBar()
        hideRunnable = Runnable {
            hideNavigationBar(window)
        }
        hideRunnable?.let { hideHandler.postDelayed(it, 3000) } // 3 seconds delay
    }

    private fun cancelHideNavigationBar() {
        hideRunnable?.let { hideHandler.removeCallbacks(it) }
        hideRunnable = null
    }
}