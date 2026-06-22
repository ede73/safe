package fi.iki.ede.theme

import androidx.compose.ui.graphics.Color

actual val BuildDependentSurfaceColor: Color? = run {
    val buildConfigClass = try {
        Class.forName("fi.iki.ede.safe.BuildConfig")
    } catch (e: ClassNotFoundException) {
        null
    }
    
    val buildType = try {
        buildConfigClass?.getField("BUILD_TYPE")?.get(null) as? String
    } catch (e: Exception) {
        null
    }
    
    val isDebug = try {
        buildConfigClass?.getField("DEBUG")?.get(null) as? Boolean ?: false
    } catch (e: Exception) {
        false
    }
    
    if (buildType == "instrumentationTest") {
        Color.Yellow
    } else if (isDebug) {
        Color.Red
    } else {
        null
    }
}
