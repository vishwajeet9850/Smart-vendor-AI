package com.smartvendor.ai.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import com.smartvendor.ai.model.DetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * 100% Offline On-Device YOLOv8/v11 Classifier using TensorFlow Lite.
 * Includes:
 * - Ultralytics-Matched Letterbox preprocessing
 * - Black Screen / Covered Camera rejection guard
 * - Physical Packaging Color & Spectrum validation (eliminates Jim Jam vs Hide & Seek confusion)
 */
class TFLiteClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = listOf("appe_fizz", "haldiram_soya_stick", "hide_and_seek", "jim_jam", "maggi", "nivea_deodorant", "oreo", "surf_excel", "tresemme_shampoo")
    private var isInitialized = false
    private val modelInputSize = 640
    private val numClasses = 9
    private val numPredictions = 8400

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) return@withContext Result.success(Unit)

            labels = loadLabels()
            val modelBuffer = loadModelFile()
            if (modelBuffer != null) {
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                }
                interpreter = Interpreter(modelBuffer, options)
                warmUpModel()
                isInitialized = true
                Log.d(TAG, "On-device YOLO TFLite loaded (Classes: ${labels.joinToString()})")
                Result.success(Unit)
            } else {
                Log.w(TAG, "Model file best.tflite not found in assets.")
                isInitialized = true
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TFLite Classifier", e)
            Result.failure(e)
        }
    }

    private fun loadModelFile(): MappedByteBuffer? {
        return try {
            val fileDescriptor = context.assets.openFd("best.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: IOException) {
            Log.e(TAG, "Failed loading best.tflite from assets", e)
            null
        }
    }

    private fun loadLabels(): List<String> {
        return try {
            val list = context.assets.open("labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }
            if (list.isNotEmpty()) list else listOf("appe_fizz", "haldiram_soya_stick", "hide_and_seek", "jim_jam", "maggi", "nivea_deodorant", "oreo", "surf_excel", "tresemme_shampoo")
        } catch (e: Exception) {
            listOf("appe_fizz", "haldiram_soya_stick", "hide_and_seek", "jim_jam", "maggi", "nivea_deodorant", "oreo", "surf_excel", "tresemme_shampoo")
        }
    }

    private fun warmUpModel() {
        interpreter?.let {
            try {
                val dummyInput = java.nio.ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3)
                dummyInput.order(java.nio.ByteOrder.nativeOrder())
                val outputMap = HashMap<Int, Any>()
                val dummyOutput = Array(1) { Array(10) { FloatArray(numPredictions) } }
                outputMap[0] = dummyOutput
                it.runForMultipleInputsOutputs(arrayOf(dummyInput), outputMap)
            } catch (e: Exception) {
                Log.w(TAG, "Warmup pass note: ${e.message}")
            }
        }
    }

    suspend fun detect(bitmap: Bitmap, confThreshold: Float = 0.55f): List<DetectionResult> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val interp = interpreter ?: return@withContext emptyList()

        val letterbox = YoloUtils.letterboxBitmap(bitmap, modelInputSize)
        val outputArray = Array(1) { Array(10) { FloatArray(numPredictions) } }
        val outputMap = HashMap<Int, Any>()
        outputMap[0] = outputArray

        try {
            interp.runForMultipleInputsOutputs(arrayOf(letterbox.byteBuffer), outputMap)
        } catch (e: Exception) {
            Log.e(TAG, "TFLite inference error", e)
            return@withContext emptyList()
        }

        val rawDetections = mutableListOf<DetectionResult>()
        val predictions = outputArray[0] // [10][8400]

        val r = letterbox.scale
        val padX = letterbox.padX
        val padY = letterbox.padY

        for (i in 0 until numPredictions) {
            var maxConfidence = 0.0f
            var maxClassId = -1

            for (c in 0 until numClasses) {
                val score = predictions[4 + c][i]
                if (score > maxConfidence) {
                    maxConfidence = score
                    maxClassId = c
                }
            }

            if (maxConfidence >= confThreshold && maxClassId >= 0) {
                val cx = predictions[0][i]
                val cy = predictions[1][i]
                val w = predictions[2][i]
                val h = predictions[3][i]

                // Unpad from Letterbox back to original image space
                val origLeft = ((cx - w / 2f) - padX) / r
                val origTop = ((cy - h / 2f) - padY) / r
                val origRight = ((cx + w / 2f) - padX) / r
                val origBottom = ((cy + h / 2f) - padY) / r

                val left = origLeft.coerceIn(0f, bitmap.width.toFloat() - 1f)
                val top = origTop.coerceIn(0f, bitmap.height.toFloat() - 1f)
                val right = origRight.coerceIn(left + 1f, bitmap.width.toFloat())
                val bottom = origBottom.coerceIn(top + 1f, bitmap.height.toFloat())

                val rect = RectF(left, top, right, bottom)
                var labelName = labels.getOrElse(maxClassId) { "class_$maxClassId" }

                // 1. Verify Color & Black Screen Guard (<0.2ms)
                val (isValid, correctedLabel) = verifyAndCorrectColorGuard(bitmap, rect, labelName)
                if (!isValid) {
                    continue
                }
                labelName = correctedLabel

                val elapsedTime = System.currentTimeMillis() - startTime

                rawDetections.add(
                    DetectionResult(
                        classId = maxClassId,
                        label = labelName,
                        confidence = maxConfidence,
                        boundingBox = rect,
                        inferenceTimeMs = elapsedTime
                    )
                )
            }
        }

        return@withContext YoloUtils.nonMaximumSuppression(rawDetections, iouThreshold = 0.45f)
    }

    /**
     * Rejects Black/Covered Screen and corrects Hide & Seek vs Jim Jam confusion
     */
    private fun verifyAndCorrectColorGuard(bitmap: Bitmap, box: RectF, label: String): Pair<Boolean, String> {
        try {
            val left = box.left.toInt().coerceIn(0, bitmap.width - 1)
            val top = box.top.toInt().coerceIn(0, bitmap.height - 1)
            val width = (box.width().toInt()).coerceIn(1, bitmap.width - left)
            val height = (box.height().toInt()).coerceIn(1, bitmap.height - top)

            if (width < 15 || height < 15) return Pair(true, label)

            var totalLuminance = 0L
            var sampleCount = 0
            var redCount = 0
            var purpleCount = 0
            var yellowCount = 0
            var greenCount = 0

            val stepX = maxOf(1, width / 10)
            val stepY = maxOf(1, height / 10)
            val hsv = FloatArray(3)

            for (y in top until (top + height) step stepY) {
                for (x in left until (left + width) step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    totalLuminance += lum
                    sampleCount++

                    Color.colorToHSV(pixel, hsv)
                    val hue = hsv[0]
                    val sat = hsv[1]
                    val value = hsv[2]

                    if (sat > 0.20f && value > 0.20f) {
                        if (hue >= 340f || hue <= 18f) redCount++
                        if (hue in 260f..339f) purpleCount++
                        if (hue in 38f..75f) yellowCount++
                        if (hue in 80f..165f) greenCount++
                    }
                }
            }

            if (sampleCount == 0) return Pair(true, label)
            val avgLuminance = totalLuminance / sampleCount

            val lbl = label.lowercase().trim()

            // 1. Black Screen / Covered Camera Guard (Appy Fizz must not trigger on dark/black screens)
            if (lbl == "appe_fizz" || lbl == "appy") {
                if (avgLuminance < 35) {
                    return Pair(false, label) // Pitch black / covered camera
                }
            }

            // 2. Hide & Seek vs Jim Jam Discrimination
            if (lbl == "jim_jam" || lbl == "jimjam") {
                // If the packaging is heavily purple/violet with low red, it is actually Hide & Seek!
                if (purpleCount > 4 && redCount <= 2) {
                    return Pair(true, "hide_and_seek")
                }
            } else if (lbl == "hide_and_seek" || lbl == "hide_seek") {
                // If the packaging is bright red with low purple, it is actually Jim Jam!
                if (redCount > 4 && purpleCount <= 2) {
                    return Pair(true, "jim_jam")
                }
            } else if (lbl == "maggi") {
                if (greenCount > 6 && yellowCount < 3) {
                    return Pair(false, label)
                }
            }

            return Pair(true, label)
        } catch (e: Exception) {
            return Pair(true, label)
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "TFLiteClassifier"
    }
}
