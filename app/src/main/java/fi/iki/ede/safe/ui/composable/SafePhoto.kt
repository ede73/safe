package fi.iki.ede.safe.ui.composable

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.theme.SafeTextButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AvertInactivityDuringLongTask
import fi.iki.ede.safe.ui.utilities.firebaseLog
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import androidx.camera.core.Preview as CameraXPreview

@Composable
fun SafePhoto(
    inactivity: AvertInactivityDuringLongTask,
    photo: Bitmap?,
    onBitmapCaptured: (Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val preview = remember { CameraXPreview.Builder().build() }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var capturePhoto by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    LaunchedEffect(preview) {
        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        preview.setSurfaceProvider(previewView.surfaceProvider)
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner, cameraSelector, preview, imageCapture
        )
    }

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted: Boolean ->
                if (isGranted) {
                    cameraProviderFuture.addListener({
                        // CameraProvider is used to bind the lifecycle of cameras to the lifecycle owner
                        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        inactivity.avertInactivity(context, "SafePhoto permission")

                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                            .setJpegQuality(50)
                            .build()

                        // Unbind all use cases before rebinding
                        cameraProvider.unbindAll()

                        // Bind use cases to camera
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            imageCapture
                        )

                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    onBitmapCaptured(image.toBitmap())
                                    image.close()
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    // Handle any errors during capture here
                                    firebaseRecordException("Photo failed", exception)
                                }
                            }
                        )
                    }, ContextCompat.getMainExecutor(context))
                } else {
                    // TODO: Handle permission denial
                    firebaseLog("Photo permission not granted")
                }
            })

    Column {
        if (showPreview) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
//                        .fillMaxWidth()
//                        .aspectRatio(4f / 3f)
                )
                SafeTextButton(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopCenter)
                        .padding(16.dp),
                    onClick = {
                        capturePhoto = true
                        showPreview = false
                    }) { Text(text = stringResource(id = R.string.password_entry_capture_photo)) }
            }
        } else {
            Row {
                SafeTextButton(
                    onClick = {
                        showPreview = true
                    }) { Text(text = stringResource(id = R.string.password_entry_capture_photo)) }
                if (photo != null) {
                    SafeTextButton(
                        onClick = {
                            onBitmapCaptured(null)
                        }) { Text(text = stringResource(id = R.string.password_entry_delete_photo)) }
                }
            }
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