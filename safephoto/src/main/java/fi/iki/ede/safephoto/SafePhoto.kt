package fi.iki.ede.safephoto

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import fi.iki.ede.statemachine.AllowedEvents
import fi.iki.ede.statemachine.MainStateMachine.Companion.INITIAL
import fi.iki.ede.statemachine.StateMachine
import androidx.camera.core.Preview as CameraXPreview

private const val TAG = "SafePhoto"

@Composable
fun SafePhoto(
    // inactivity indicator, pause=true, resume=false
    inactivity: (pauseOrResume: Boolean, why: String) -> Unit,
    currentPhoto: Bitmap?,
    onBitmapCaptured: (Bitmap?) -> Unit,
    photoPermissionRequiredContent: @Composable (oldPhoto: Bitmap?, onBitmapCaptured: (Bitmap?) -> Unit, askPermission: MutableState<Boolean>) -> Unit,
    takePhotoContent: @Composable (oldPhoto: Bitmap?, onBitmapCaptured: (Bitmap?) -> Unit, takePhoto: MutableState<Boolean>) -> Unit,
    composeTakePhoto: @Composable (takePhoto: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

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
                inactivity(true, "Before photo preview")
                ShowPermissionRequestButton(
                    currentPhoto,
                    onBitmapCaptured,
                    this,
                    photoPermissionRequiredContent
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
                                // TODO: Temporarily disabled, should of course never happen as
                                // an error, but can't rely on main apps firebase (or other) logging
                                //firebaseRecordException("failed to request permission", e)
                            }
                        }
                    }
                }
            }
            StateEvent(
                "beforePhotoPreview", "DidNotGetPermission",
                AllowedEvents(INITIAL)
            ) {
                // explain to user why permission is required!
                // TODO:
                Log.e(TAG, "Explain to user why permission is required")
                DispatchEvent(INITIAL)
            }
            StateEvent(
                "showingPhotoPreview",
                INITIAL,
            ) {
                inactivity(true, "Showing preview")
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
                        pictureFailure = { exception ->
                            // TODO: Temporarily disabled
                            //firebaseRecordException("Photo failed", exception)
                            failedTakingThePicture = true
                        },
                        composeTakePhoto
                    )
                }
            }
            StateEvent(
                "showingTakeDeletePhoto", INITIAL,
                AllowedEvents("VerifyPermission")
            ) {
                inactivity(false, "Photo taken")
                ShowTakeDeletePhoto(
                    currentPhoto,
                    onBitmapCaptured,
                    this,
                    takePhotoContent
                )
            }
            StateEvent(
                "showingTakeDeletePhoto",
                "VerifyPermission",
            ) {
                TransitionTo("verifyingPermissionBeforePreview")
            }
        }
        if (currentPhoto != null) {
            ZoomableImageViewer(currentPhoto)
        }
    }
}

@Composable
private fun ShowPermissionRequestButton(
    oldPhoto: Bitmap?,
    onBitmapCaptured: (Bitmap?) -> Unit,
    state: StateMachine.StateEvent,
    content: @Composable (oldPhoto: Bitmap?, onBitmapCaptured: (Bitmap?) -> Unit, askPermission: MutableState<Boolean>) -> Unit
) {
    val askPermission = remember { mutableStateOf(false) }
    if (askPermission.value) {
        state.DispatchEvent("VerifyPermissionBeforePreview")
    } else {
        content(oldPhoto, onBitmapCaptured, askPermission)
    }
}

@Composable
private fun ShowPhotoPreview(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onBitmapCaptured: (Bitmap?) -> Unit,
    pictureSuccess: () -> Unit,
    pictureFailure: (exception: Exception) -> Unit,
    composeTakePhoto: @Composable (takePhoto: () -> Unit) -> Unit
) {
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val preview = remember { CameraXPreview.Builder().build() }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    cameraProviderFuture.get().let { cameraProvider ->
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        preview.surfaceProvider = previewView.surfaceProvider
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
        composeTakePhoto {
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
        }
    }
}

@Composable
private fun ShowTakeDeletePhoto(
    photo: Bitmap?,
    onBitmapCaptured: (Bitmap?) -> Unit,
    state: StateMachine.StateEvent,
    content: @Composable (oldPhoto: Bitmap?, onBitmapCaptured: (Bitmap?) -> Unit, takePhoto: MutableState<Boolean>) -> Unit
) {
    val takePhoto = remember { mutableStateOf(false) }
    if (takePhoto.value) {
        state.DispatchEvent("VerifyPermission")
    } else {
        content(photo, onBitmapCaptured, takePhoto)
    }
}

private fun takePhoto(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    onBitmapCaptured: (Bitmap?) -> Unit,
    pictureSuccess: () -> Unit,
    pictureFailure: (exception: Exception) -> Unit
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
                    pictureFailure(exception)
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SafePhotoPreview() {
    // SafeTheme
    MaterialTheme {
        val width = 100
        val height = 100
        val redColor = Color.RED
        val dummyBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                dummyBitmap.setPixel(x, y, redColor)
            }
        }
        SafePhoto(
            { isPausedOrResume, why ->
                if (isPausedOrResume) {
                    // pause inactivity - we're displaying preview
                } else {
                    //resumed inactivity measurements
                }
            },
            dummyBitmap,
            onBitmapCaptured = {},
            photoPermissionRequiredContent = { oldPhoto, onBitmapCaptured, askPermission ->
            },
            takePhotoContent = { oldPhoto, onBitmapCaptured, takePhoto ->
            },
            composeTakePhoto = { onClick -> }
        )
    }
}

