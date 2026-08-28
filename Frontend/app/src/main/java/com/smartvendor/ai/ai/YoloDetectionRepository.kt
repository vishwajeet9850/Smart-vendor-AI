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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

class YoloDetectionRepository {

    private val api = ApiClient.apiService
    private val TAG = "YoloDetection"

    suspend fun detectFromBitmap(
        bitmap: Bitmap,
        confThreshold: Float = 0.50f
    ): YoloDetectResponse? = withContext(Dispatchers.IO) {
        try {
            val scaled = scaleBitmap(bitmap, maxDim = 480)
            val base64Jpeg = bitmapToBase64Jpeg(scaled)

            // Allow 2500ms timeout for reliable initial connection handshake
            withTimeoutOrNull(2500L) {
                try {
                    val response = api.detectFromBase64(
                        YoloDetectRequest(image = base64Jpeg, conf = confThreshold)
                    )
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.detections.isNotEmpty()) {
                            Log.d(TAG, "Server YOLO found ${body.detections.size} products: ${body.detections.map { it.label }}")
                            body
                        } else null
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "YOLO network request error", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "YOLO Bitmap processing error", e)
            null
        }
    }

    suspend fun detectFromImageProxy(
        imageProxy: ImageProxy,
        confThreshold: Float = 0.50f
    ): YoloDetectResponse? = withContext(Dispatchers.IO) {
        try {
            val bitmap = imageProxyToRotatedBitmap(imageProxy) ?: return@withContext null
            detectFromBitmap(bitmap, confThreshold)
        } catch (e: Exception) {
            null
        } finally {
            try {
                imageProxy.close()
            } catch (_: Exception) {}
        }
    }

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
