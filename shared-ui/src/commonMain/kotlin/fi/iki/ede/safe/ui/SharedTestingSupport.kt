package fi.iki.ede.safe.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

fun Modifier.testTag(tag: TestTag): Modifier = semantics {
    testTag = tag.name
}
