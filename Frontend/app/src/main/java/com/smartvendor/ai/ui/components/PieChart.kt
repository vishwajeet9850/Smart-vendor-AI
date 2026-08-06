package com.smartvendor.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BlueDark
import com.smartvendor.ai.ui.theme.BluePrimary
import com.smartvendor.ai.ui.theme.WarningYellow

@Composable
fun PieChart(
    categoryData: Map<String, Float>,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    if (categoryData.isEmpty() || categoryData.values.all { it == 0f }) {
        Box(
            modifier = modifier.height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No category breakdown data available",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
            )
        }
        return
    }

    val palette = listOf(
        BluePrimary,
        AccentGreen,
        WarningYellow,
        BlueDark,
        Color(0xFF9C27B0),
        Color(0xFFFF5722),
        Color(0xFF00BCD4)
    )
    val total = categoryData.values.sum().coerceAtLeast(1f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pie Canvas
        Canvas(modifier = Modifier.size(160.dp)) {
            var startAngle = -90f
            categoryData.entries.forEachIndexed { index, entry ->
                val sweepAngle = (entry.value / total) * 360f
                drawArc(
                    color = palette.getOrElse(index % palette.size) { Color.Gray },
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    size = Size(size.width, size.height)
                )
                startAngle += sweepAngle
            }
        }

        // Category Legend Labels
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categoryData.entries.forEachIndexed { index, entry ->
                val percentage = ((entry.value / total) * 100).toInt()
                val color = palette.getOrElse(index % palette.size) { Color.Gray }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(color, CircleShape)
                        )
                        Text(
                            text = entry.key,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "${entry.value.toInt()} sold ($percentage%)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.DarkGray
                        )
                    )
                }
            }
        }
    }
}
