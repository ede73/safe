package fi.iki.ede.autolock

import android.content.Context
import androidx.activity.ComponentActivity

interface AutoLockingFeatures {
    fun lockApplication(context: Context)
    fun startLoginScreen(context: Context)
    fun isLoggedIn(): Boolean
    fun isThisLoginScreen(componentActivity: ComponentActivity): Boolean
}

