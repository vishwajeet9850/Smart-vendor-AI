package com.smartvendor.ai.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onFrameAvailable: (ImageProxy) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastProcessedTimestamp = 0L
    private val frameIntervalMs = 150L // ~6-7 FPS target

    fun startCamera(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(onSuccess)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraX provider", e)
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(onSuccess: () -> Unit) {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder()
            .setTargetResolution(Size(1280, 720))
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 640))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProcessedTimestamp >= frameIntervalMs) {
                        lastProcessedTimestamp = currentTime
                        onFrameAvailable(imageProxy)
                    } else {
                        imageProxy.close()
                    }
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}
