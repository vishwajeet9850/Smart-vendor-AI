package com.smartvendor.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartvendor.ai.ui.theme.BlueDark
import com.smartvendor.ai.ui.theme.BluePrimary
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.DangerRed
import com.smartvendor.ai.ui.theme.ElectricCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onNavigateToScan: (String) -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            DashboardTopBar(
                userName = uiState.userName,
                storeName = uiState.storeName,
                urgentAlertCount = uiState.urgentStockAlerts.size,
                onNotificationClick = { viewModel.toggleNotificationDialog(true) },
                onProfileClick = { onNavigateToSettings() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                // Primary Action: New Bill / Resume Bill Card
                item {
                    NewBillCard(
                        openBillId = uiState.openBillId,
                        isLoading = uiState.isLoading,
                        onClick = {
                            viewModel.startOrResumeNewBill { billId ->
                                onNavigateToScan(billId)
                            }
                        }
                    )
                }

                // Grid/Row Actions
                item {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Voice Billing Quick Card
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.startOrResumeNewBill { billId ->
                                    onNavigateToScan(billId)
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, Color(0xFFD32F2F).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(Color(0xFFD32F2F).copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Voice Billing", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Surface(
                                            color = Color(0xFFD32F2F).copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "मराठी / हिंदी",
                                                color = Color(0xFFD32F2F),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "Speak items with quantity to build bill instantly",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DashboardActionCard(
                            title = "Inventory",
                            description = "Manage Products",
                            icon = Icons.Outlined.Inventory2,
                            accentColor = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToInventory
                        )
                        DashboardActionCard(
                            title = "Sales Reports",
                            description = "View Analytics",
                            icon = Icons.Outlined.BarChart,
                            accentColor = Color(0xFFFF9800),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToReports
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DashboardActionCard(
                            title = "Bill History",
                            description = "Past Invoices",
                            icon = Icons.Outlined.ReceiptLong,
                            accentColor = Color(0xFF9C27B0),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToHistory
                        )
                        DashboardActionCard(
                            title = "Settings",
                            description = "App Preferences",
                            icon = Icons.Outlined.Settings,
                            accentColor = Color(0xFF607D8B),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToSettings
                        )
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("DISMISS", color = Color.White)
                        }
                    }
                ) {
                    Text(uiState.errorMessage!!)
                }
            }
        }
    }

    if (uiState.showNotificationDialog) {
        UrgentStockNotificationSheet(
            alerts = uiState.urgentStockAlerts,
            onDismiss = { viewModel.toggleNotificationDialog(false) },
            onQuickRestock = { productId -> viewModel.quickRestock(productId) },
            onNavigateToInventory = {
                viewModel.toggleNotificationDialog(false)
                onNavigateToInventory()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    userName: String,
    storeName: String,
    urgentAlertCount: Int,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Welcome,",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Surface(
                        color = AccentGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentGreen, CircleShape)
                            )
                            Text(
                                text = "Smart Scanner Active",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = storeName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = BluePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onNotificationClick,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                        .size(42.dp)
                ) {
                    if (urgentAlertCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = DangerRed,
                                    contentColor = Color.White
                                ) {
                                    Text("$urgentAlertCount", fontWeight = FontWeight.Bold)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = "Urgent Stock Alerts ($urgentAlertCount)",
                                tint = DangerRed
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BluePrimary)
                        .clickable { onProfileClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Profile",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NewBillCard(
    openBillId: String?,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(BluePrimary)
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = "Scan Icon",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "AI Vision Scanner",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Start Live Scan & Bill",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                Text(
                    text = "Scan items using camera, barcode, or direct search for instant kirana billing.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = BluePrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Open Camera Scanner ⚡",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BluePrimary
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = BluePrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(144.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrgentStockNotificationSheet(
    alerts: List<UrgentStockAlert>,
    onDismiss: () -> Unit,
    onQuickRestock: (String) -> Unit,
    onNavigateToInventory: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "🚨 Urgent Stock Alerts",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (alerts.isNotEmpty())
                                "${alerts.size} items require immediate replenishment"
                            else
                                "All stock levels healthy",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider()

            if (alerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "All Inventory Healthy!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "No out-of-stock or critical items right now.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(alerts) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isOutOfStock)
                                    Color(0xFFFFEBEE)
                                else
                                    Color(0xFFFFF8E1)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = if (item.isOutOfStock) Color(0xFFD32F2F) else Color(0xFFFFA000),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (item.isOutOfStock) "OUT OF STOCK" else "LOW STOCK",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = "Current: ${item.currentStock} units (Min: ${item.lowStockThreshold})",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (item.isOutOfStock) Color(0xFFD32F2F) else Color(0xFFE65100),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }

                                Button(
                                    onClick = { onQuickRestock(item.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (item.isOutOfStock) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+20 Stock", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onDismiss()
                    onNavigateToInventory()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
            ) {
                Text("Open Full Inventory Management", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
