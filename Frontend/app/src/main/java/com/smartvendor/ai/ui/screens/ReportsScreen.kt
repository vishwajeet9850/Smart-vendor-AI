package com.smartvendor.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartvendor.ai.ui.components.BarChart
import com.smartvendor.ai.ui.components.PieChart
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.RedPrimary
import com.smartvendor.ai.ui.theme.WarningYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val timeRanges = remember { listOf("Today", "Yesterday", "Last 7 Days", "Last 30 Days") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sales & Analytics", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = if (uiState.isOfflineMode) "⚡ On-Device Mode (Local Data)" else "☁️ Live Backend Synced",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (uiState.isOfflineMode) WarningYellow else AccentGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Time Range Filter Row
                item(key = "time_range_chips") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(timeRanges, key = { it }) { range ->
                            val isSelected = range == uiState.selectedTimeRange
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectTimeRange(range) },
                                label = { Text(range, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RedPrimary,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // Summary Metric Cards
                item(key = "metric_cards") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                title = "Total Revenue",
                                value = "₹${if (uiState.totalRevenue % 1.0 == 0.0) uiState.totalRevenue.toInt() else uiState.totalRevenue}",
                                icon = Icons.Outlined.CurrencyRupee,
                                color = RedPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Total Bills",
                                value = "${uiState.totalTransactions}",
                                icon = Icons.Outlined.Receipt,
                                color = AccentGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                title = "Avg Bill Value",
                                value = "₹${if (uiState.averageBillValue % 1.0 == 0.0) uiState.averageBillValue.toInt() else uiState.averageBillValue.toInt()}",
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                color = Color(0xFF1976D2),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Top Seller",
                                value = uiState.bestSellingProduct.take(12),
                                icon = Icons.Outlined.Star,
                                color = WarningYellow,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // AI Stock Recommendations Card
                if (uiState.stockRecommendations.isNotEmpty()) {
                    item(key = "stock_recommendations_card") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        color = RedPrimary.copy(alpha = 0.12f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Outlined.AutoAwesome,
                                                contentDescription = null,
                                                tint = RedPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Restock Recommendations",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Predictions based on store velocity & demand",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                        )
                                    }
                                }

                                uiState.stockRecommendations.forEach { rec ->
                                    val badgeColor = when (rec.recommendationType) {
                                        "URGENT_RESTOCK" -> Color(0xFFD32F2F)
                                        "FESTIVAL_SURGE" -> Color(0xFFE65100)
                                        "MARKET_TREND" -> Color(0xFF0288D1)
                                        "LOW_STOCK_BUFFER" -> Color(0xFFF57C00)
                                        "NEAR_EXPIRY" -> Color(0xFF7B1FA2)
                                        "OVERSTOCK_CLEARANCE" -> Color(0xFF1976D2)
                                        "BUNDLE_OPPORTUNITY" -> Color(0xFF00796B)
                                        else -> Color(0xFF2E7D32)
                                    }
                                    val badgeBg = badgeColor.copy(alpha = 0.12f)

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.25f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = rec.productName,
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = rec.category,
                                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                                    )
                                                }
                                                Surface(
                                                    color = badgeBg,
                                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = rec.recommendationTitle.ifBlank { "Stock Alert" },
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = badgeColor,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                }
                                            }

                                            // Plain terms explanation
                                            Text(
                                                text = rec.simpleReason.ifBlank { rec.reasoning },
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 18.sp
                                                )
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                                ) {
                                                    Text(
                                                        text = "Current Stock: ${rec.currentStock}",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (rec.currentStock <= 5) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    )
                                                }

                                                if (rec.recommendedReorder > 0 && rec.actionType in listOf("RESTOCK", "REORDER", "FESTIVAL_ORDER", "MARKET_ORDER")) {
                                                    Button(
                                                        onClick = { viewModel.restockProduct(rec.productId, rec.recommendedReorder) },
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = badgeColor),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(rec.actionLabel.ifBlank { "Reorder +${rec.recommendedReorder}" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    Surface(
                                                        color = badgeBg,
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = rec.actionLabel.ifBlank { "No Action" },
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = badgeColor,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                // Revenue Bar Chart Card
                item(key = "revenue_trend_card") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Revenue Trend (₹)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            BarChart(dataPoints = uiState.revenueDataPoints, barColor = RedPrimary)
                        }
                    }
                }

                // Category Distribution Card
                item(key = "category_distribution_card") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Product Sales Distribution",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            PieChart(categoryData = uiState.categoryDistribution)
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = RedPrimary
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(95.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1
            )
        }
    }
}
