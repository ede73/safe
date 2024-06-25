package fi.iki.ede.safe.ui.composable

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.theme.SafeTextButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AvertInactivityDuringLongTask

@Composable
fun SafePhoto(
    inactivity: AvertInactivityDuringLongTask,
    photo: Bitmap?,
    onBitmapCaptured: (Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val takePictureLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicturePreview(),
            onResult = { bitmap: Bitmap? ->
                // User might have spent quite some time with photo permission, so try to refresh
                inactivity.avertInactivity(context, "SafePhoto got bitmap")
                onBitmapCaptured(bitmap)
            })
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted: Boolean ->
                // User might have spent quite some time with photo permission, so try to refresh
                inactivity.avertInactivity(context, "SafePhoto permission")
                if (isGranted) {
                    takePictureLauncher.launch(null)
                } else {
                    // TODO: Handle permission denial
                }
            })

    var capturePhoto by remember { mutableStateOf(false) }

    Column {
        Row {
            SafeTextButton(onClick = {
                capturePhoto = true
            }) { Text(text = stringResource(id = R.string.password_entry_capture_photo)) }

            SafeTextButton(onClick = {
                onBitmapCaptured(null)
            }) { Text(text = stringResource(id = R.string.password_entry_delete_photo)) }
        }
        if (photo != null) {
            ZoomableImageViewer(photo)
        }
    }

    if (capturePhoto) {
        // Side effect to request permission when the composable enters the composition
        LaunchedEffect(key1 = true) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}


@Preview(showBackground = true)
@Composable
fun SafePhotoPreview() {
    val mockInactivity = object : AvertInactivityDuringLongTask {
        override fun avertInactivity(context: Context, why: String) {
            // Mock implementation
            println("Averting inactivity because: $why")
        }
    }

    SafeTheme {
        val width = 100
        val height = 100
        val redColor = Color.RED
        val dummyBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                dummyBitmap.setPixel(x, y, redColor)
            }
        }
        SafePhoto(mockInactivity, dummyBitmap, onBitmapCaptured = {})
    }
}