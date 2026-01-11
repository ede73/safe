package fi.iki.ede.safe.ui.utilities

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

fun setBackupDueIconEnabled(context: Context, isEnabled: Boolean) {
    setBackupDueAliasIcon(context, isEnabled)
    setNormalAppIcon(context, !isEnabled)
}

private fun setBackupDueAliasIcon(context: Context, isEnabled: Boolean) =
    setComponentEnabled(
        context, "fi.iki.ede.safe.ui.activities.MainAppWithBackupIcon", isEnabled
    )

private fun setNormalAppIcon(context: Context, isEnabled: Boolean) =
    setComponentEnabled(
        context,
        "fi.iki.ede.safe.ui.activities.MainAppWithoutBackupIcon",
        isEnabled
    )

private fun setComponentEnabled(context: Context, className: String, isEnabled: Boolean) {
    val newState = if (isEnabled) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    context.packageManager.setComponentEnabledSetting(
        ComponentName(context.packageName, className),
        newState,
        PackageManager.DONT_KILL_APP
    )
}