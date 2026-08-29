package com.smartvendor.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartvendor.ai.ui.theme.BluePrimary

@Composable
fun BarChart(
    dataPoints: List<Pair<String, Double>>,
    barColor: Color = BluePrimary,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(240.dp)
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
    
    // Auto-select the latest non-zero day, or the last day by default
    var selectedIndex by remember(dataPoints) {
        val lastNonZero = dataPoints.indexOfLast { it.second > 0 }
        mutableStateOf(if (lastNonZero >= 0) lastNonZero else (dataPoints.size - 1))
    }

    val selectedPoint = dataPoints.getOrElse(selectedIndex) { dataPoints.last() }
    val formattedSelectedDate = formatFullDate(selectedPoint.first)

    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Interactive Info Bar (shows selected day & value cleanly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (dataPoints.size > 7) "Tap any bar to inspect daily sales" else "Daily Breakdown",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            )

            Surface(
                color = barColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, barColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(barColor, CircleShape)
                    )
                    Text(
                        text = formattedSelectedDate,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = "₹${formatCurrency(selectedPoint.second)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = barColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        // Chart Area with Bars
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Subtle horizontal reference grid line at 50%
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val count = dataPoints.size
                val barWidth = when {
                    count <= 4 -> 32.dp
                    count <= 7 -> 20.dp
                    count <= 14 -> 12.dp
                    else -> 6.dp
                }

                dataPoints.forEachIndexed { index, (label, value) ->
                    val isSelected = index == selectedIndex
                    val ratio = (value / maxVal).toFloat().coerceIn(if (value > 0) 0.06f else 0.02f, 1.0f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                selectedIndex = index
                            }
                    ) {
                        // Only show floating text above bars if there are 7 or fewer data points
                        if (count <= 7 && value > 0) {
                            Text(
                                text = "₹${formatCompactCurrency(value)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) barColor else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        // The Bar
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(ratio)
                                .width(barWidth)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    color = when {
                                        value <= 0 -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        isSelected -> barColor
                                        count > 7 -> barColor.copy(alpha = 0.75f)
                                        else -> barColor
                                    }
                                )
                        )
                    }
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
            val totalPoints = dataPoints.size
            val step = when {
                totalPoints > 20 -> 6
                totalPoints > 12 -> 3
                totalPoints > 7 -> 2
                else -> 1
            }

            dataPoints.forEachIndexed { index, (label, _) ->
                val showLabel = index == 0 || index == totalPoints - 1 || index % step == 0
                val formattedLabel = if (showLabel) formatAxisDate(label) else ""

                Text(
                    text = formattedLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = if (index == selectedIndex) barColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                    ),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}

private fun formatAxisDate(rawDate: String): String {
    if (rawDate.isBlank()) return ""
    if (rawDate.equals("Today", ignoreCase = true)) return "Today"
    if (rawDate.equals("Yesterday", ignoreCase = true)) return "Yest"

    return try {
        val parts = rawDate.split("-")
        if (parts.size == 3) {
            val day = parts[2].toIntOrNull() ?: return rawDate
            val monthInt = parts[1].toIntOrNull() ?: 1
            val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val monthStr = monthNames.getOrElse((monthInt - 1).coerceIn(0, 11)) { "" }
            "$day $monthStr"
        } else {
            rawDate
        }
    } catch (_: Exception) {
        rawDate
    }
}

private fun formatFullDate(rawDate: String): String {
    if (rawDate.isBlank()) return "Today"
    if (rawDate.equals("Today", ignoreCase = true)) return "Today"
    if (rawDate.equals("Yesterday", ignoreCase = true)) return "Yesterday"

    return try {
        val parts = rawDate.split("-")
        if (parts.size == 3) {
            val day = parts[2].toIntOrNull() ?: return rawDate
            val monthInt = parts[1].toIntOrNull() ?: 1
            val year = parts[0]
            val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val monthStr = monthNames.getOrElse((monthInt - 1).coerceIn(0, 11)) { "" }
            "$day $monthStr $year"
        } else {
            rawDate
        }
    } catch (_: Exception) {
        rawDate
    }
}

private fun formatCurrency(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        "%,d".format(amount.toLong())
    } else {
        "%,.2f".format(amount)
    }
}

private fun formatCompactCurrency(amount: Double): String {
    return when {
        amount >= 100_000 -> String.format("%.1fL", amount / 100_000)
        amount >= 1_000 -> String.format("%.1fk", amount / 1_000)
        amount % 1.0 == 0.0 -> "${amount.toInt()}"
        else -> "${amount.toInt()}"
    }
}

