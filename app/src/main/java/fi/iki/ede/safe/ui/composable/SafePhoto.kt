package fi.iki.ede.safe.ui.composable

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.AllowedEvents
import fi.iki.ede.safe.model.MainStateMachine.Companion.INITIAL
import fi.iki.ede.safe.model.StateMachine
import fi.iki.ede.safe.ui.theme.SafeTextButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AvertInactivityDuringLongTask
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import androidx.camera.core.Preview as CameraXPreview

private const val TAG = "SafePhoto"

@Composable
fun SafePhoto(
    inactivity: AvertInactivityDuringLongTask,
    photo: Bitmap?,
    onBitmapCaptured: (Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun hasPhotoPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    Column {
        StateMachine.Create("beforePhotoPreview") {
            StateEvent(
                "beforePhotoPreview",
                INITIAL,
                AllowedEvents("VerifyPermissionBeforePreview")
            ) {
                ShowPermissionRequestButton(
                    photo,
                    onBitmapCaptured,
                    this
                )
            }
            StateEvent(
                "beforePhotoPreview",
                "VerifyPermissionBeforePreview",
            ) {
                TransitionTo("verifyingPermissionBeforePreview")
            }
            StateEvent(
                "verifyingPermissionBeforePreview",
                INITIAL,
                AllowedEvents("DidNotGetPermission")
            ) {
                var transitionToShowPhotoPreview by remember { mutableStateOf(false) }
                var handleNoPermissionGranted by remember { mutableStateOf(false) }
                if (transitionToShowPhotoPreview) {
                    TransitionTo("showingPhotoPreview")
                } else if (handleNoPermissionGranted) {
                    DispatchEvent("DidNotGetPermission")
                } else {
                    if (hasPhotoPermission()) {
                        TransitionTo("showingPhotoPreview")
                    } else {
                        val requestPermissionLauncher =
                            rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission(),
                                onResult = { isGranted: Boolean ->
                                    if (isGranted) {
                                        transitionToShowPhotoPreview = true
                                    } else {
                                        handleNoPermissionGranted = true
                                    }
                                })
                        LaunchedEffect(Unit) {
                            try {
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } catch (e: Exception) {
                                firebaseRecordException("failed to request permission", e)
                            }
                        }
                    }
                }
            }
            StateEvent("beforePhotoPreview", "DidNotGetPermission", AllowedEvents(INITIAL)) {
                // explain to user why permission is required!
                // TODO:
                Log.e(TAG, "Explain to user why permission is required")
                DispatchEvent(INITIAL)
            }
            StateEvent(
                "showingPhotoPreview",
                INITIAL,
            ) {
                var tookThePicture by remember { mutableStateOf(false) }
                var failedTakingThePicture by remember { mutableStateOf(false) }
                if (tookThePicture) {
                    TransitionTo("showingTakeDeletePhoto")
                } else if (failedTakingThePicture) {
                    TransitionTo("beforePhotoPreview")
                } else {
                    ShowPhotoPreview(
                        context,
                        lifecycleOwner,
                        onBitmapCaptured,
                        pictureSuccess = { tookThePicture = true },
                        pictureFailure = { failedTakingThePicture = true }
                    )
                }
            }
            StateEvent("showingPhotoPreview", "TakePhoto") {
                TransitionTo("takingPhoto")
            }
            StateEvent("showingTakeDeletePhoto", INITIAL, AllowedEvents("VerifyPermission")) {
                ShowTakeDeletePhoto(
                    photo,
                    onBitmapCaptured,
                    this
                )
            }
            StateEvent(
                "showingTakeDeletePhoto",
                "VerifyPermission",
            ) {
                TransitionTo("verifyingPermissionBeforePreview")
            }
        }
        if (photo != null) {
            ZoomableImageViewer(photo)
        }
    }
}

@Composable
private fun ShowPermissionRequestButton(
    photo: Bitmap?,
    onBitmapCaptured: (Bitmap?) -> Unit,
    state: StateMachine.StateEvent
) {
    var askPermission by remember { mutableStateOf(false) }
    if (askPermission) {
        state.DispatchEvent("VerifyPermissionBeforePreview")
    } else {
        Row {
            SafeTextButton(onClick = {
                askPermission = true
            }) { Text(text = stringResource(id = R.string.password_entry_capture_photo)) }
            if (photo != null) {
                SafeTextButton(onClick = {
                    onBitmapCaptured(null)
                }) { Text(text = stringResource(id = R.string.password_entry_delete_photo)) }
            }
        }
    }
}

@Composable
private fun ShowPhotoPreview(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onBitmapCaptured: (Bitmap?) -> Unit,
    pictureSuccess: () -> Unit,
    pictureFailure: () -> Unit
) {
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val preview = remember { CameraXPreview.Builder().build() }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    cameraProviderFuture.get().let { cameraProvider ->
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        preview.setSurfaceProvider(previewView.surfaceProvider)
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner, cameraSelector, preview, imageCapture
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        SafeTextButton(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            onClick = {
                cameraProviderFuture.addListener(
                    takePhoto(
                        context,
                        lifecycleOwner,
                        cameraProviderFuture,
                        onBitmapCaptured,
                        pictureSuccess,
                        pictureFailure
                    ),
                    ContextCompat.getMainExecutor(context)
                )
            }) { Text(text = stringResource(id = R.string.password_entry_capture_photo)) }
    }
}

@Composable
private fun ShowTakeDeletePhoto(
    photo: Bitmap?,
    onBitmapCaptured: (Bitmap?) -> Unit,
    state: StateMachine.StateEvent
) {
    var takePhoto by remember { mutableStateOf(false) }
    if (takePhoto) {
        state.DispatchEvent("VerifyPermission")
    } else {
        Row {
            SafeTextButton(onClick = {
                takePhoto = true
            }) { Text(text = stringResource(id = R.string.password_entry_capture_photo)) }
            if (photo != null) {
                SafeTextButton(onClick = {
                    onBitmapCaptured(null)
                }) { Text(text = stringResource(id = R.string.password_entry_delete_photo)) }
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    onBitmapCaptured: (Bitmap?) -> Unit,
    pictureSuccess: () -> Unit,
    pictureFailure: () -> Unit
): Runnable {
    return Runnable {
        // CameraProvider is used to bind the lifecycle of cameras to the lifecycle owner
        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

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
                    pictureSuccess()
                }

                override fun onError(exception: ImageCaptureException) {
                    // Handle any errors during capture here
                    firebaseRecordException("Photo failed", exception)
                    pictureFailure()
                }
            }
        )
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