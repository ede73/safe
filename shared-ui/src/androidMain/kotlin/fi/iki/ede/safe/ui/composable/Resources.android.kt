package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun getString(id: String): String {
    val context = LocalContext.current
    val resId = context.resources.getIdentifier(id, "string", context.packageName)
    return if (resId != 0) context.resources.getString(resId) else id
}

@Composable
actual fun getString(id: String, formatArg: String): String {
    val context = LocalContext.current
    val resId = context.resources.getIdentifier(id, "string", context.packageName)
    return if (resId != 0) context.resources.getString(resId, formatArg) else id.replace("%s", formatArg)
}

@Composable
actual fun getPluralString(id: String, quantity: Int, formatArg: Int): String {
    val context = LocalContext.current
    val resId = context.resources.getIdentifier(id, "plurals", context.packageName)
    return if (resId != 0) context.resources.getQuantityString(resId, quantity, formatArg) else "$formatArg $id"
}
