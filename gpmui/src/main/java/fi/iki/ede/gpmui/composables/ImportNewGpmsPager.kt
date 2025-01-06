package fi.iki.ede.gpmui.composables

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpmui.utilities.makeFakeImportForTesting
import fi.iki.ede.theme.SafeTheme

@Composable
fun ImportNewGpmsPager(importChangeSet: MutableState<ImportChangeSet?>, done: () -> Unit) {
    Column {
        val pages = listOf<@Composable () -> Unit>(
            { ImportNewGpmsToBeAddedPage(importChangeSet) },
            { MatchingGpmsToBeUpdatedPage(importChangeSet) },
            { ConflictingGpmsPage(importChangeSet) },
            { ObsoleteGpmsToBeDeletedPage(importChangeSet) },
        )

        val pagerState = rememberPagerState(pageCount = { 4 })

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = true,
            key = { i -> i }) { page ->
            Column(
                Modifier
                    .border(2.dp, SafeTheme.colorScheme.onSurface)
                    .shadow(3.dp, shape = RectangleShape, clip = false)
                    .fillMaxHeight()
                    .padding(10.dp),
            ) {
                pages[page].invoke()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ImportResultListPagerPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    MaterialTheme {
        val m =
            remember { mutableStateOf<ImportChangeSet?>(makeFakeImportForTesting()) }
        ImportNewGpmsPager(m, {})
    }
}