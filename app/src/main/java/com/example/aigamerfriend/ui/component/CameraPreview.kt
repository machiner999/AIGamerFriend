package com.example.aigamerfriend.ui.component

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.annotation.VisibleForTesting
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrameCaptured: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView =
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview =
                    Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val imageAnalysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, SnapshotFrameAnalyzer(onFrameCaptured))
                        }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                )
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}

@VisibleForTesting
internal fun shouldCaptureFrame(now: Long, lastCaptureTimeMs: Long, captureIntervalMs: Long): Boolean =
    now - lastCaptureTimeMs >= captureIntervalMs

private class SnapshotFrameAnalyzer(
    private val onFrameCaptured: (Bitmap) -> Unit,
) : ImageAnalysis.Analyzer {
    private var lastCaptureTimeMs = 0L
    private val captureIntervalMs = 1000L // 1 FPS

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (shouldCaptureFrame(now, lastCaptureTimeMs, captureIntervalMs)) {
            lastCaptureTimeMs = now

            val bitmap = imageProxy.toBitmap()
            // ImageProxy from back camera may need rotation
            val rotated = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
            if (rotated !== bitmap) bitmap.recycle()
            onFrameCaptured(rotated)
        }
        imageProxy.close()
    }

    private fun rotateBitmap(
        bitmap: Bitmap,
        degrees: Float,
    ): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
