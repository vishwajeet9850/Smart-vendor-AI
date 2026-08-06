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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartvendor.ai.ui.components.BarChart
import com.smartvendor.ai.ui.components.PieChart
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BluePrimary
import com.smartvendor.ai.ui.theme.WarningYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sales Reports & Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Time Filter Row
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(listOf("Today", "Yesterday", "Last 7 Days", "Last 30 Days")) { range ->
                            FilterChip(
                                selected = uiState.selectedTimeRange == range,
                                onClick = { viewModel.selectTimeRange(range) },
                                label = { Text(range, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BluePrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Metric Overview Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Total Revenue",
                            value = "₹${"%.2f".format(uiState.totalRevenue)}",
                            icon = Icons.Outlined.Payments,
                            color = BluePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Transactions",
                            value = "${uiState.totalTransactions}",
                            icon = Icons.Outlined.ReceiptLong,
                            color = AccentGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Avg Bill Value",
                            value = "₹${"%.2f".format(uiState.averageBillValue)}",
                            icon = Icons.Outlined.TrendingUp,
                            color = Color(0xFFFF9800),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Best Seller",
                            value = uiState.bestSellingProduct,
                            icon = Icons.Outlined.Star,
                            color = Color(0xFF9C27B0),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Smart AI Inventory Restock Recommendations Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = BluePrimary.copy(alpha = 0.12f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = BluePrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Smart Restock Recommendations",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "AI predictions based on peak hours & velocity",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                    )
                                }
                            }

                            if (uiState.stockRecommendations.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    uiState.stockRecommendations.forEach { rec ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
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
                                                    Text(
                                                        text = rec.productName.replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    Surface(
                                                        color = when (rec.urgencyLevel) {
                                                            "HIGH" -> Color.Red.copy(alpha = 0.15f)
                                                            "MEDIUM" -> WarningYellow.copy(alpha = 0.18f)
                                                            else -> BluePrimary.copy(alpha = 0.15f)
                                                        },
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = rec.salesVelocity,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = when (rec.urgencyLevel) {
                                                                    "HIGH" -> Color.Red
                                                                    "MEDIUM" -> Color(0xFFD84315)
                                                                    else -> BluePrimary
                                                                },
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        )
                                                    }
                                                }

                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        color = BluePrimary.copy(alpha = 0.1f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = rec.peakWindow,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = BluePrimary,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        )
                                                    }
                                                    Text(
                                                        text = "Stock Left: ${rec.currentStock}",
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = if (rec.currentStock <= 5) Color.Red else Color.Gray,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                }

                                                Text(
                                                    text = rec.reasoning,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                                        lineHeight = 16.sp
                                                    )
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    Button(
                                                        onClick = { viewModel.restockProduct(rec.productId, rec.recommendedReorder) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Restock +${rec.recommendedReorder}",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AccentGreen)
                                    Text(
                                        text = "All products have healthy inventory levels!",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.DarkGray)
                                    )
                                }
                            }
                        }
                    }
                }

                // Cross-Vendor Market Intelligence Section (Redesigned)
                if (uiState.marketTrends.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFF3F51B5).copy(alpha = 0.25f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF3F51B5).copy(alpha = 0.12f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Outlined.Public,
                                                contentDescription = null,
                                                tint = Color(0xFF3F51B5),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Cross-Vendor Market Insights",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Market demand & opportunities across local stores",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    uiState.marketTrends.forEach { trend ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, Color(0xFF3F51B5).copy(alpha = 0.18f))
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                // Left color accent bar
                                                Box(
                                                    modifier = Modifier
                                                        .width(5.dp)
                                                        .fillMaxHeight()
                                                        .background(
                                                            if (trend.actionType == "ADD_PRODUCT") WarningYellow
                                                            else Color(0xFF3F51B5)
                                                        )
                                                )

                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Text(
                                                            text = trend.title,
                                                            style = MaterialTheme.typography.titleSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 15.sp
                                                            ),
                                                            modifier = Modifier.weight(1f)
                                                        )

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        Surface(
                                                            color = if (trend.actionType == "ADD_PRODUCT") WarningYellow.copy(alpha = 0.15f)
                                                                    else Color(0xFF3F51B5).copy(alpha = 0.12f),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Text(
                                                                text = trend.badgeLabel,
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    color = if (trend.actionType == "ADD_PRODUCT") Color(0xFFE65100)
                                                                            else Color(0xFF3F51B5),
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            )
                                                        }
                                                    }

                                                    Text(
                                                        text = trend.description,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                                            lineHeight = 16.sp
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

                // Revenue Bar Chart Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Revenue Trend (₹)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            BarChart(dataPoints = uiState.revenueDataPoints)
                        }
                    }
                }

                // Category Breakdown Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Category Distribution",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            PieChart(categoryData = uiState.categoryDistribution)
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = BluePrimary
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
    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
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
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
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
