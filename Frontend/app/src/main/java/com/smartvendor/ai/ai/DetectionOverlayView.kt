package com.smartvendor.ai.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    Canvas(modifier = modifier.fillMaxSize()) {
        for (detection in detections) {
            val box = detection.boundingBox
            val color = when {
                detection.confidence >= 0.90f -> AccentGreen
                detection.confidence >= 0.80f -> WarningYellow
                else -> BluePrimary
            }

            drawRect(
                color = color,
                topLeft = Offset(box.left, box.top),
                size = Size(box.width(), box.height()),
                style = Stroke(width = 6f)
            )

            val labelText = "${detection.label} (${(detection.confidence * 100).toInt()}%)"

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    textSize = 38f
                    isFakeBoldText = true
                }
                val backgroundPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.BLACK
                    alpha = 180
                }

                val textWidth = paint.measureText(labelText)
                drawRect(
                    box.left,
                    box.top - 46f,
                    box.left + textWidth + 24f,
                    box.top,
                    backgroundPaint
                )
                drawText(labelText, box.left + 12f, box.top - 12f, paint)
            }
        }
    }
}
