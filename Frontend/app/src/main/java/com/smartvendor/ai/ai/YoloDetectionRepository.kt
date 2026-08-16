package com.smartvendor.ai.ai

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageProxy
import com.smartvendor.ai.network.ApiClient
import com.smartvendor.ai.network.models.YoloDetectRequest
import com.smartvendor.ai.network.models.YoloDetectResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Fast Multi-Object YOLO Detection Repository
 * Sends lightweight, downscaled camera frames to the backend YOLO endpoint
 * and returns all detected products in real time.
 */
class YoloDetectionRepository {

    private val api = ApiClient.apiService
    private val TAG = "YoloDetection"

    /**
     * Convert CameraX ImageProxy to lightweight base64 JPEG and detect all products.
     * Always closes [imageProxy] in finally block so CameraX streaming never stalls.
     */
    suspend fun detectFromImageProxy(
        imageProxy: ImageProxy,
        confThreshold: Float = 0.65f
    ): YoloDetectResponse? = withContext(Dispatchers.IO) {
        try {
            val bitmap = imageProxyToRotatedBitmap(imageProxy) ?: return@withContext null
            // Downscale to 480px for ultra-low latency (<50ms network payload)
            val scaled = scaleBitmap(bitmap, maxDim = 480)
            val base64Jpeg = bitmapToBase64Jpeg(scaled)

            val response = api.detectFromBase64(
                YoloDetectRequest(image = base64Jpeg, conf = confThreshold)
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.detections.isNotEmpty()) {
                    Log.d(TAG, "YOLO found ${body.detections.size} products: ${body.detections.map { it.label }}")
                    body
                } else {
                    null
                }
            } else {
                Log.w(TAG, "Detection API error: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during YOLO detection", e)
            null
        } finally {
            try {
                imageProxy.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Convert a [Bitmap] to base64 JPEG and call backend.
     */
    suspend fun detectFromBitmap(
        bitmap: Bitmap,
        confThreshold: Float = 0.30f
    ): YoloDetectResponse? = withContext(Dispatchers.IO) {
        try {
            val scaled = scaleBitmap(bitmap, maxDim = 480)
            val base64Jpeg = bitmapToBase64Jpeg(scaled)
            val response = api.detectFromBase64(
                YoloDetectRequest(image = base64Jpeg, conf = confThreshold)
            )
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e(TAG, "Exception during YOLO bitmap detection", e)
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun imageProxyToRotatedBitmap(image: ImageProxy): Bitmap? {
        return try {
            val rawBitmap = image.toBitmap()
            val rotationDegrees = image.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed converting ImageProxy to Bitmap", e)
            null
        }
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * scale).toInt(),
            (h * scale).toInt(),
            true
        )
    }
}
