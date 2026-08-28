package com.smartvendor.ai.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.smartvendor.ai.model.DetectionResult
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class LetterboxResult(
    val byteBuffer: ByteBuffer,
    val scale: Float,
    val padX: Float,
    val padY: Float
)

object YoloUtils {

    fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val rotationDegrees = image.imageInfo.rotationDegrees
            val bitmap = image.toBitmap()
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Throwable) {
            try {
                val planes = image.planes
                val yBuffer = planes[0].buffer.duplicate().apply { rewind() }
                val uBuffer = planes[1].buffer.duplicate().apply { rewind() }
                val vBuffer = planes[2].buffer.duplicate().apply { rewind() }

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
                val imageBytes = out.toByteArray()
                val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null

                val rotationDegrees = image.imageInfo.rotationDegrees
                if (rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                } else {
                    decoded
                }
            } catch (ex: Throwable) {
                null
            }
        }
    }

    /**
     * Ultralytics-Standard Letterbox Preprocessing (Preserves Aspect Ratio with 114 Gray Padding)
     * Identical to PyTorch YOLO inference pipeline
     */
    fun letterboxBitmap(bitmap: Bitmap, inputSize: Int = 640): LetterboxResult {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        val r = min(inputSize / w, inputSize / h)
        val newUnpadW = (w * r).toInt()
        val newUnpadH = (h * r).toInt()

        val padX = (inputSize - newUnpadW) / 2f
        val padY = (inputSize - newUnpadH) / 2f

        val letterboxedBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(letterboxedBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114))

        val resized = Bitmap.createScaledBitmap(bitmap, newUnpadW, newUnpadH, true)
        canvas.drawBitmap(resized, padX, padY, Paint(Paint.FILTER_BITMAP_FLAG))

        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        letterboxedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val valPixel = intValues[pixel++]
                byteBuffer.putFloat(((valPixel shr 16 and 0xFF) / 255.0f))
                byteBuffer.putFloat(((valPixel shr 8 and 0xFF) / 255.0f))
                byteBuffer.putFloat(((valPixel and 0xFF) / 255.0f))
            }
        }

        return LetterboxResult(
            byteBuffer = byteBuffer,
            scale = r,
            padX = padX,
            padY = padY
        )
    }

    /**
     * Class-Agnostic Non-Maximum Suppression (Identical to Server)
     */
    fun nonMaximumSuppression(
        detections: List<DetectionResult>,
        iouThreshold: Float = 0.45f
    ): List<DetectionResult> {
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectionResult>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!active[i]) continue
            val a = sorted[i]
            selected.add(a)

            for (j in i + 1 until sorted.size) {
                if (!active[j]) continue
                val b = sorted[j]
                if (calculateIoU(a.boundingBox, b.boundingBox) > iouThreshold) {
                    active[j] = false
                }
            }
        }
        return selected
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val intersectionLeft = max(a.left, b.left)
        val intersectionTop = max(a.top, b.top)
        val intersectionRight = min(a.right, b.right)
        val intersectionBottom = min(a.bottom, b.bottom)

        val intersectionArea = max(0f, intersectionRight - intersectionLeft) *
                max(0f, intersectionBottom - intersectionTop)

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = areaA + areaB - intersectionArea

        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }
}
