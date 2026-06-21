package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable

@Composable
expect fun getString(id: String): String

@Composable
expect fun getString(id: String, formatArg: String): String

@Composable
expect fun getPluralString(id: String, quantity: Int, formatArg: Int): String
