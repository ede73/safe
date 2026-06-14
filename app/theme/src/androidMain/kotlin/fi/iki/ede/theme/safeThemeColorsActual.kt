package fi.iki.ede.theme

import androidx.compose.ui.graphics.Color

actual val BuildDependentSurfaceColor: Color? = if (BuildConfig.BUILD_TYPE == "instrumentationTest") Color.Yellow
else if (BuildConfig.DEBUG) Color.Red else null
