package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fi.iki.ede.crypto.DecryptableCategoryEntry

@Composable
fun CategoryList(categories: List<DecryptableCategoryEntry>) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        categories.forEach { category ->
            CategoryRow(category)
        }
    }
}