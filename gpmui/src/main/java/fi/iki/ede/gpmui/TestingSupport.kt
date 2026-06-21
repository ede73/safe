package fi.iki.ede.gpmui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

fun Modifier.testTag(tag: TestTag) = semantics(
    properties = {
        // Make sure we don't leak stuff to production
        if (BuildConfig.DEBUG) {
            testTag = tag.name
        }
    }
)