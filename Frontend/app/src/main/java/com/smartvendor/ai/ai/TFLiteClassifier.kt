package com.smartvendor.ai.ai

import android.content.Context
import android.graphics.Bitmap
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

class TFLiteClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var isInitialized = false
    private val modelInputSize = 640

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
                Log.d(TAG, "TFLite Model loaded and warmed up successfully.")
                Result.success(Unit)
            } else {
                Log.w(TAG, "Model file best.tflite not found in assets, running fallback mode.")
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
            null
        }
    }

    private fun loadLabels(): List<String> {
        return try {
            context.assets.open("labels.txt").bufferedReader().useLines { it.toList() }
        } catch (e: Exception) {
            listOf("Product_0", "Product_1", "Product_2", "Product_3")
        }
    }

    private fun warmUpModel() {
        interpreter?.let {
            val dummyInput = java.nio.ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3)
            val dummyOutput = Array(1) { Array(84) { FloatArray(8400) } }
            try {
                it.run(dummyInput, dummyOutput)
            } catch (e: Exception) {
                Log.w(TAG, "Warmup model pass note: ${e.message}")
            }
        }
    }

    suspend fun detect(bitmap: Bitmap): List<DetectionResult> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        if (!isInitialized || interpreter == null) {
            return@withContext emptyList()
        }

        val (inputBuffer, scaleFactors) = YoloUtils.preprocessBitmap(bitmap, modelInputSize)
        val outputArray = Array(1) { Array(84) { FloatArray(8400) } }

        try {
            interpreter?.run(inputBuffer, outputArray)
        } catch (e: Exception) {
            Log.e(TAG, "Inference execution failed", e)
            return@withContext emptyList()
        }

        val rawDetections = mutableListOf<DetectionResult>()
        val predictions = outputArray[0] // [84][8400]
        val numClasses = 80
        val numPredictions = 8400

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

            if (maxConfidence >= 0.50f && maxClassId >= 0) {
                val cx = predictions[0][i] * scaleFactors[0]
                val cy = predictions[1][i] * scaleFactors[1]
                val w = predictions[2][i] * scaleFactors[0]
                val h = predictions[3][i] * scaleFactors[1]

                val rect = RectF(
                    cx - w / 2f,
                    cy - h / 2f,
                    cx + w / 2f,
                    cy + h / 2f
                )

                val labelName = labels.getOrElse(maxClassId) { "Class $maxClassId" }
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

        return@withContext YoloUtils.nonMaximumSuppression(rawDetections)
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
