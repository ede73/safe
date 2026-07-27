package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.password.PG_SYMBOLS
import fi.iki.ede.safe.password.PasswordGenerator
import fi.iki.ede.theme.SafeButton

@Composable
fun PopCustomPasswordDialog(
    onDismiss: (password: Password?) -> Unit
) {
    val upperCases = remember { mutableStateOf(true) }
    val lowerCases = remember { mutableStateOf(true) }
    val numbers = remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(PG_SYMBOLS) }
    var passwordLength by remember { mutableIntStateOf(18) }
    var regenerate by remember { mutableIntStateOf(0) }
    val password by remember(
        upperCases.value,
        lowerCases.value,
        numbers.value,
        passwordLength,
        symbols,
        regenerate
    ) {
        mutableStateOf(
            PasswordGenerator.genPassword(
                passUpper = upperCases.value,
                passLower = lowerCases.value,
                passNum = numbers.value,
                passSymbols = symbols.isNotEmpty(),
                symbols = symbols,
                length = passwordLength
            )
        )
    }

    AlertDialog(
        onDismissRequest = {
            onDismiss(Password(password))
        },
        title = { Text(getString("action_bar_generate_custom_password")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = upperCases.value,
                        onCheckedChange = { upperCases.value = it }
                    )
                    Text(text = getString("site_entry_uppercases"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = lowerCases.value,
                        onCheckedChange = { lowerCases.value = it }
                    )
                    Text(text = getString("site_entry_lowercases"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = numbers.value,
                        onCheckedChange = { numbers.value = it }
                    )
                    Text(text = getString("site_entry_numbers"))
                }
                TextField(
                    value = symbols,
                    onValueChange = { newSymbolCandidates ->
                        val filtered =
                            newSymbolCandidates.filter { !it.isLetterOrDigit() && !it.isWhitespace() }
                        val uniqueSymbols = filtered.toSet().joinToString("")
                        symbols = uniqueSymbols
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(passwordLength.toString())
                    Slider(
                        value = passwordLength.toFloat(),
                        onValueChange = { passwordLength = it.toInt() },
                        valueRange = 8.0f..30.0f,
                        steps = 22
                    )
                }
                SafeButton(onClick = { regenerate++ }) {
                    Text(getString("site_entry_regenerate"))
                }
                PasswordTextField(
                    label = getString("site_entry_generated_password"),
                    inputValue = password,
                    enableZoom = true
                )
            }
        },
        confirmButton = {
            SafeButton(
                onClick = {
                    onDismiss(Password(password))
                }
            ) { Text(getString("generic_ok")) }
        },
        dismissButton = {
            SafeButton(onClick = { onDismiss(null) }) {
                Text(getString("generic_cancel"))
            }
        }
    )
}
