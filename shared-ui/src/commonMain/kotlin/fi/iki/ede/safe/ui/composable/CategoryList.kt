package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry

@Composable
fun CategoryList(
    categories: List<DecryptableCategoryEntry>,
    onCategoryClick: (DecryptableCategoryEntry) -> Unit,
    onRenameCategory: (DecryptableCategoryEntry, String) -> Unit,
    onDeleteCategory: (DecryptableCategoryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
    ) {
        categories.forEach { category ->
            CategoryRow(
                category = category,
                onCategoryClick = onCategoryClick,
                onRenameCategory = onRenameCategory,
                onDeleteCategory = onDeleteCategory
            )
        }
    }
}
