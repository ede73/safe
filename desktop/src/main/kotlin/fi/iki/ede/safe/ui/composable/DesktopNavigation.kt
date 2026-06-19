package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry

object DesktopNavigation {
    var activeCategory by mutableStateOf<DecryptableCategoryEntry?>(null)
    var dbRefreshTrigger by mutableStateOf(0)
}
