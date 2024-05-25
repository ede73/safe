package fi.iki.ede.safe.ui.composable

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput


@Composable
fun ZoomableImageViewer(bitmap: Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Preview bitmap is about 120x160 pixels (only)
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Zoomable image",
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // maximize or minimize the image
                        if (scale == 1f) {
                            // maximize
                            scale = 6f
                            offset = Offset.Zero
                        } else {
                            // minimize
                            scale = 1f
                            offset = Offset.Zero
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offset += pan
                }
            }
            .graphicsLayer(
                scaleX = maxOf(1f, scale),
                scaleY = maxOf(1f, scale),
                translationX = offset.x,
                translationY = offset.y
            )
            .focusable()
    )
}
