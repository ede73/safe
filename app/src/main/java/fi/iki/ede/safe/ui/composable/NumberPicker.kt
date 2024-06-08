package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberPicker(
    numbers: List<String>,
    selectedNumber: MutableState<String>,
    pagerState: PagerState
) {
    val currentPage by snapshotFlow { pagerState.currentPage }.collectAsState(initial = pagerState.currentPage)

    LaunchedEffect(currentPage) {
        selectedNumber.value = numbers[currentPage]
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.height(35.dp)
    ) { page ->
        Text(
            text = numbers[page],
            color = if (numbers[page].contains("-")) Color.Red else Color.Unspecified,
        )
    }
}