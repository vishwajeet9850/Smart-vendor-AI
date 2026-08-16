package com.smartvendor.ai.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.smartvendor.ai.model.DetectionResult
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BluePrimary
import com.smartvendor.ai.ui.theme.WarningYellow

@Composable
fun DetectionOverlayView(
    modifier: Modifier = Modifier,
    detections: List<DetectionResult>
) {
    if (detections.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        for (detection in detections) {
            val box = detection.boundingBox

            // Handle both normalized [0..1] and pixel coordinates
            val left = if (box.right <= 1.0f) box.left * canvasWidth else box.left
            val top = if (box.bottom <= 1.0f) box.top * canvasHeight else box.top
            val right = if (box.right <= 1.0f) box.right * canvasWidth else box.right
            val bottom = if (box.bottom <= 1.0f) box.bottom * canvasHeight else box.bottom

            val width = (right - left).coerceAtLeast(10f)
            val height = (bottom - top).coerceAtLeast(10f)

            val color = when {
                detection.confidence >= 0.75f -> AccentGreen
                detection.confidence >= 0.50f -> WarningYellow
                else -> BluePrimary
            }

            // Draw Bounding Box
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 6f)
            )

            // Friendly label formatting
            val labelText = "${detection.label} (${(detection.confidence * 100).toInt()}%)"

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    textSize = 36f
                    isFakeBoldText = true
                }
                val backgroundPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.argb(200, 20, 20, 20)
                }

                val textWidth = paint.measureText(labelText)
                val labelTop = (top - 46f).coerceAtLeast(0f)
                drawRect(
                    left,
                    labelTop,
                    left + textWidth + 24f,
                    labelTop + 46f,
                    backgroundPaint
                )
                drawText(labelText, left + 12f, labelTop + 34f, paint)
            }
        }
    }
}
