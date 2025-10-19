package fi.iki.ede.safe.ui.composable

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Light Mode")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, name = "Dark Mode")
annotation class DualModePreview
