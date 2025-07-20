package fi.iki.ede.safe.ui.utilities

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

fun setBackupDueIconEnabled(context: Context, isEnabled: Boolean) {
    setComponentEnabled(
        context,
        "fi.iki.ede.safe.ui.activities.LoginScreen", true
    )
    setBackupDueAliasIcon(context, isEnabled)
    setNormalAppIcon(context, !isEnabled)
}

private fun setBackupDueAliasIcon(context: Context, isEnabled: Boolean) = setComponentEnabled(
    context,
    "fi.iki.ede.safe.ui.activities.LoginScreenWithBackupIcon", isEnabled
)

private fun setNormalAppIcon(context: Context, isEnabled: Boolean) = setComponentEnabled(
    context,
    "fi.iki.ede.safe.ui.activities.LoginScreenWithoutBackupIcon", isEnabled
)

private fun setComponentEnabled(context: Context, className: String, isEnabled: Boolean) {
    val componentName = ComponentName(context.packageName, className)
    val newState = if (isEnabled) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    context.packageManager.setComponentEnabledSetting(
        componentName,
        newState,
        PackageManager.DONT_KILL_APP
    )
}