package com.smartvendor.ai.camera

import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    onFrameAvailable: (ImageProxy) -> Unit,
    onCameraInitialized: () -> Unit,
    onCameraError: (Throwable) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            if (cameraManager == null) {
                val manager = CameraManager(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    onFrameAvailable = onFrameAvailable
                )
                cameraManager = manager
                manager.startCamera(
                    onSuccess = onCameraInitialized,
                    onError = onCameraError
                )
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.stopCamera()
        }
    }
}
