package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.safe.ui.theme.SafeTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImportResultListPager(importChangeSet: MutableState<ImportChangeSet?>, done: () -> Unit) {
    Column {
        val pages = listOf<@Composable () -> Unit>(
            { Page1(importChangeSet) },
            { Page2(importChangeSet) },
            { Page3(importChangeSet) },
            { Page4(importChangeSet) },
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