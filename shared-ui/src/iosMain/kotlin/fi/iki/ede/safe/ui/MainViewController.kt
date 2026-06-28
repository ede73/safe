@file:OptIn(kotlin.time.ExperimentalTime::class)

package fi.iki.ede.safe.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import fi.iki.ede.preferences.Preferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

fun MainViewController(): UIViewController = ComposeUIViewController {
    if (!Preferences.isDataStoreInitialized()) {
        Preferences.initialize()
    }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1a1a2e),
                            Color(0xFF16213e),
                            Color(0xFF0f172a)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            SharedLoginScreen(
                isFirstTimeLogin = true,
                isBiometricsEnabled = false,
                statusMessage = "Welcome to Safe for iOS!",
                onCreateVault = { password, biometrics -> },
                onUnlock = { password, biometrics -> }
            )
        }
    }
}
