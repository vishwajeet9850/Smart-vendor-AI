package com.smartvendor.ai.model

import android.graphics.RectF

data class DetectionResult(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val inferenceTimeMs: Long = 0L
)
