package com.smartvendor.ai.network.models

import com.google.gson.annotations.SerializedName

// ─── YOLO Detection ────────────────────────────────────────────────────────────

data class YoloDetection(
    val label: String,
    val confidence: Float,
    val bbox: List<Float>   // [x1, y1, x2, y2] normalised 0-1
)

data class YoloDetectResponse(
    val detections: List<YoloDetection>,
    @SerializedName("top_label") val topLabel: String?,
    @SerializedName("top_confidence") val topConfidence: Float?
)

data class YoloDetectRequest(
    val image: String,          // base64 JPEG
    val conf: Float = 0.35f
)
