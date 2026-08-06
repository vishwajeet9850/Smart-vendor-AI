package com.smartvendor.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartvendor.ai.ui.theme.BluePrimary

@Composable
fun BarChart(
    dataPoints: List<Pair<String, Double>>,
    barColor: Color = BluePrimary,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(230.dp)
) {
    if (dataPoints.isEmpty() || dataPoints.all { it.second == 0.0 }) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No sales data available for this timeframe",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
            )
        }
        return
    }

    val maxVal = (dataPoints.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)

    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            dataPoints.forEach { (label, value) ->
                val ratio = (value / maxVal).toFloat().coerceIn(0.05f, 1.0f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    if (value > 0) {
                        Text(
                            text = "₹${value.toInt()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight(ratio)
                            .width(28.dp)
                            .background(
                                color = if (value > 0) barColor else Color.LightGray.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // X-Axis Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dataPoints.forEach { (label, _) ->
                Text(
                    text = label.takeLast(5),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
