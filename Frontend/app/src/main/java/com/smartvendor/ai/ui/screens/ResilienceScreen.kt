package com.smartvendor.ai.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartvendor.ai.model.JournalTransaction
import com.smartvendor.ai.model.RecoveryReport
import com.smartvendor.ai.model.SystemStatus
import java.util.Locale

// Color Palette for Resilience
private val ResilienceGreen = Color(0xFF10B981)
private val ResilienceRed = Color(0xFFEF4444)
private val ResilienceAmber = Color(0xFFF59E0B)
private val ResilienceBlue = Color(0xFF3B82F6)
private val DarkBg = Color(0xFF0F172A)
private val CardDark = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResilienceScreen(
    onNavigateBack: () -> Unit,
    viewModel: ResilienceViewModel = viewModel()
) {
    val status by viewModel.systemStatus.collectAsState()
    val journal by viewModel.journal.collectAsState()
    val latestReport by viewModel.latestReport.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val showReportDialog by viewModel.showReportDialog.collectAsState()
    val showResetConfirmDialog by viewModel.showResetConfirmDialog.collectAsState()
    val cieAlert by viewModel.cieAlert.collectAsState()
    val hasActiveCIEIncident by viewModel.hasActiveCIEIncident.collectAsState()
    val localProducts by com.smartvendor.ai.repository.LocalStoreManager.productsFlow.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Resilience & Recovery",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        LiveStatusBadge(status.systemStatus)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            // 1. Primary Status Header Card
            item {
                ResilienceStatusHeaderCard(status = status)
            }

            // 2. Action Control Panel
            item {
                ResilienceControlPanel(
                    isBlackoutActive = status.isBlackoutActive,
                    isLoading = isLoading,
                    onSimulateBlackout = { viewModel.simulateBlackout() },
                    onRestore = { viewModel.restoreSystem() },
                    onCheckpoint = { viewModel.createCheckpoint() },
                    onResetDemo = { viewModel.resetDemo() },
                    onViewLatestReport = { viewModel.setShowReportDialog(true) },
                    hasReport = latestReport != null
                )
            }

            // 2.5. CIE Cross-Vendor Incident Simulator Card
            item {
                CIEIncidentSimulatorCard(
                    hasActiveIncident = hasActiveCIEIncident,
                    cieAlert = cieAlert,
                    isLoading = isLoading,
                    onSimulateIncident = { viewModel.simulateCIEIncident() },
                    onOpenResetDialog = { viewModel.setShowResetConfirmDialog(true) }
                )
            }

            // 3. Metrics Overview
            item {
                ResilienceMetricsGrid(status = status, journalSize = journal.size)
            }

            // 4. Live Real-Time Dual-State Comparison (Local vs Cloud DB)
            item {
                LiveDualStateComparisonCard(status = status, products = localProducts)
            }

            // 5. Transaction Journal Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Append-Only Transaction Journal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = "${journal.size} Entries",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. Journal Items
            if (journal.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "No transactions journaled yet",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Sales, returns, and stock operations will automatically append here.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            } else {
                items(journal, key = { "${it.transactionId.ifBlank { it.id }}_${it.status}" }) { tx ->
                    JournalItemCard(tx = tx)
                }
            }
        }
    }

    // Recovery Report Dialog
    if (showReportDialog && latestReport != null) {
        RecoveryReportDialog(
            report = latestReport!!,
            onDismiss = { viewModel.setShowReportDialog(false) }
        )
    }

    // Reset Demo Incident Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowResetConfirmDialog(false) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = ResilienceAmber,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Reset Demo Incident?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will remove only the injected demo return records, clear the CIE anomaly alert, and restore the database to its exact prior state.\n\nReal user returns, real bills, real stock, and vendor data will not be modified or deleted.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetCIEIncident() },
                    colors = ButtonDefaults.buttonColors(containerColor = ResilienceAmber)
                ) {
                    Text("Reset Demo Incident", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowResetConfirmDialog(false) }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ─── Status Header Card ────────────────────────────────────────────────────────

@Composable
fun ResilienceStatusHeaderCard(status: SystemStatus) {
    val isBlackout = status.isBlackoutActive
    val bgGradient = if (isBlackout) {
        Brush.horizontalGradient(listOf(Color(0xFF7F1D1D), Color(0xFF991B1B)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF064E3B), Color(0xFF047857)))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgGradient)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "THE BLACKOUT RESILIENCE ENGINE",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Icon(
                        if (isBlackout) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = if (isBlackout) "🔴 BLACKOUT DETECTED — RECOVERY MODE" else "🟢 SYSTEM HEALTHY & DURABLE",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )

                Text(
                    text = if (isBlackout) {
                        "Primary storage is simulated offline. All billing, returns, and stock operations are safely committed to the append-only journal for zero data loss."
                    } else {
                        "All transactions are recorded with unique idempotency keys. Checkpoints are active and verified."
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ─── Live Status Badge ─────────────────────────────────────────────────────────

@Composable
fun LiveStatusBadge(status: String) {
    val (bgColor, textColor, text) = when (status) {
        "BLACKOUT_ACTIVE" -> Triple(ResilienceRed.copy(alpha = 0.2f), ResilienceRed, "BLACKOUT")
        "RECOVERED" -> Triple(ResilienceGreen.copy(alpha = 0.2f), ResilienceGreen, "RECOVERED")
        "RECOVERING" -> Triple(ResilienceAmber.copy(alpha = 0.2f), ResilienceAmber, "RECOVERING")
        else -> Triple(ResilienceGreen.copy(alpha = 0.2f), ResilienceGreen, "HEALTHY")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

// ─── Control Panel ────────────────────────────────────────────────────────────

@Composable
fun ResilienceControlPanel(
    isBlackoutActive: Boolean,
    isLoading: Boolean,
    onSimulateBlackout: () -> Unit,
    onRestore: () -> Unit,
    onCheckpoint: () -> Unit,
    onResetDemo: () -> Unit,
    onViewLatestReport: () -> Unit,
    hasReport: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Hackathon Resilience Controls",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // Big Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Simulate Blackout Button
                Button(
                    onClick = onSimulateBlackout,
                    enabled = !isLoading && !isBlackoutActive,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResilienceRed,
                        disabledContainerColor = ResilienceRed.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FlashOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Simulate Blackout",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Restore System Button
                Button(
                    onClick = onRestore,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResilienceGreen,
                        disabledContainerColor = ResilienceGreen.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Restore System",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Secondary Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCheckpoint,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Snapshot", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onResetDemo,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset Demo", fontSize = 12.sp)
                }

                if (hasReport) {
                    OutlinedButton(
                        onClick = onViewLatestReport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Report", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ─── Metrics Grid ─────────────────────────────────────────────────────────────

@Composable
fun ResilienceMetricsGrid(status: SystemStatus, journalSize: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ResilienceMetricCard(
                title = "Primary Database",
                value = if (status.isBlackoutActive) "CORRUPTED / OFFLINE" else "ONLINE",
                color = if (status.isBlackoutActive) ResilienceRed else ResilienceGreen,
                icon = if (status.isBlackoutActive) Icons.Default.CloudOff else Icons.Default.CloudDone,
                modifier = Modifier.weight(1f)
            )

            ResilienceMetricCard(
                title = "Total Journaled",
                value = "${journalSize.coerceAtLeast(status.totalJournaledTransactions)} Txns",
                color = ResilienceBlue,
                icon = Icons.Default.ReceiptLong,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ResilienceMetricCard(
                title = "Pending Recovery",
                value = "${status.pendingRecoveryCount} Items",
                color = if (status.pendingRecoveryCount > 0) ResilienceAmber else MaterialTheme.colorScheme.onSurface,
                icon = Icons.Default.Pending,
                modifier = Modifier.weight(1f)
            )

            ResilienceMetricCard(
                title = "Recovered",
                value = "${status.recoveredTransactionsCount} Items",
                color = ResilienceGreen,
                icon = Icons.Default.Verified,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ResilienceMetricCard(
    title: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// ─── Journal Item Card ─────────────────────────────────────────────────────────

@Composable
fun JournalItemCard(tx: JournalTransaction) {
    val isReturn = tx.type.uppercase(Locale.US) == "RETURN"
    val isPending = tx.status.uppercase(Locale.US) == "PENDING"
    val isRecovered = tx.status.uppercase(Locale.US) == "RECOVERED"

    val (badgeBg, badgeText, badgeColor) = when {
        isPending -> Triple(ResilienceAmber.copy(alpha = 0.15f), "PENDING REPLAY", ResilienceAmber)
        isRecovered -> Triple(ResilienceGreen.copy(alpha = 0.15f), "RECOVERED ✓", ResilienceGreen)
        else -> Triple(ResilienceBlue.copy(alpha = 0.15f), "APPLIED ✓", ResilienceBlue)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Type Tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isReturn) Color(0xFFFEE2E2) else Color(0xFFE0F2FE))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isReturn) "RETURN" else "SALE",
                            color = if (isReturn) Color(0xFFB91C1C) else Color(0xFF0369A1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = tx.productName ?: "Item",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Status Tag (guaranteed to never wrap)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Qty: ${tx.quantity} pcs  •  ₹${String.format(Locale.US, "%.2f", tx.totalAmount)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (tx.previousStock != null && tx.newStock != null) {
                    Text(
                        text = "Stock: ${tx.previousStock} ➔ ${tx.newStock}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isReturn) ResilienceGreen else if (isPending) ResilienceAmber else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Txn ID watermark footer
            Text(
                text = "ID: ${tx.transactionId.ifBlank { tx.id }}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ─── Recovery Report Dialog ───────────────────────────────────────────────────

@Composable
fun RecoveryReportDialog(
    report: RecoveryReport,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = ResilienceGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Recovery Audit Report",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider()

                // Counters Breakdown
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportRow(label = "System Health", value = "🟢 HEALTHY / RESTORED", isBold = true)
                    ReportRow(label = "Transactions Discovered", value = "${report.transactionsDiscovered}")
                    ReportRow(label = "Successfully Recovered", value = "${report.successfullyRecovered}", valueColor = ResilienceGreen)
                    ReportRow(label = "Already Present (Skipped)", value = "${report.alreadyPresent}", valueColor = ResilienceBlue)
                    ReportRow(label = "Unrecoverable / Corrupted", value = "${report.unrecoverable}", valueColor = if (report.unrecoverable > 0) ResilienceRed else MaterialTheme.colorScheme.onSurface)
                }

                // Inventory Summary Table
                if (report.inventorySummary.isNotEmpty()) {
                    Divider()
                    Text(
                        text = "Inventory Verification:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        report.inventorySummary.take(5).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = item.productName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "Current Stock: ${item.currentStock}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ResilienceGreen
                                )
                            }
                        }
                    }
                }

                // Action
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close Report", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReportRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}

// ─── Dual-State Real-Time Inventory Comparison ────────────────────────────────

@Composable
fun LiveDualStateComparisonCard(
    status: SystemStatus,
    products: List<com.smartvendor.ai.model.Product>
) {
    val isBlackout = status.isBlackoutActive
    val standardBaselines = mapOf(
        "Soya" to 80,
        "Jim" to 65,
        "Oreo" to 80,
        "Appy" to 75,
        "Rice" to 50,
        "Atta" to 30,
        "Milk" to 20,
        "Sugar" to 40
    )
    val targetKeywords = listOf("Soya", "Jim", "Oreo", "Appy", "Rice", "Atta", "Milk", "Sugar")
    val demoProducts = products.filter { p ->
        targetKeywords.any { k -> p.name.contains(k, ignoreCase = true) }
    }.ifEmpty { products.take(6) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.CompareArrows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Real-Time DB vs Shelf Telemetry",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isBlackout) ResilienceRed.copy(alpha = 0.15f) else ResilienceGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isBlackout) "DB LOCKED" else "DB SYNCED ✓",
                        color = if (isBlackout) ResilienceRed else ResilienceGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Clean single-line headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Product", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.3f))
                Text("📱 Shelf", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                Text("☁️ DB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                Text("Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
            }

            // Product Rows
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                demoProducts.forEach { p ->
                    val baselineStock = standardBaselines.entries.firstOrNull { (k, _) ->
                        p.name.contains(k, ignoreCase = true)
                    }?.value ?: p.stock
                    val dbStock = if (isBlackout) baselineStock else p.stock
                    val isPendingSync = isBlackout && (p.stock != dbStock)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = p.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.3f)
                        )

                        // Local Shelf Stock
                        Text(
                            text = "${p.stock} pcs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPendingSync) ResilienceAmber else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.9f),
                            textAlign = TextAlign.Center
                        )

                        // Cloud DB Stock
                        Text(
                            text = "$dbStock pcs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBlackout) MaterialTheme.colorScheme.onSurfaceVariant else ResilienceGreen,
                            modifier = Modifier.weight(0.9f),
                            textAlign = TextAlign.Center
                        )

                        // Sync Indicator
                        Box(
                            modifier = Modifier.weight(0.8f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (isPendingSync) {
                                Text(
                                    text = "⚡ PENDING",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ResilienceAmber,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            } else {
                                Text(
                                    text = "✓ SYNC",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ResilienceGreen,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── CIE Cross-Vendor Incident Simulator Card ────────────────────────────────

@Composable
fun CIEIncidentSimulatorCard(
    hasActiveIncident: Boolean,
    cieAlert: com.smartvendor.ai.model.CIEAlertModel?,
    isLoading: Boolean,
    onSimulateIncident: () -> Unit,
    onOpenResetDialog: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasActiveIncident) Color(0xFF2A1215) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (hasActiveIncident) Color(0xFFEF4444).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = (if (hasActiveIncident) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (hasActiveIncident) Icons.Default.Warning else Icons.Default.Hub,
                                contentDescription = null,
                                tint = if (hasActiveIncident) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "CIE Incident Simulator",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cross-Vendor Anomaly Detection",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = (if (hasActiveIncident) Color(0xFFEF4444) else ResilienceGreen).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (hasActiveIncident) "INCIDENT ACTIVE" else "IDLE / READY",
                        color = if (hasActiveIncident) Color(0xFFEF4444) else ResilienceGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Description
            Text(
                text = "Simulates clustered returns across 7–8 partner stores within a 15–30 min window. Records are stamped with isolated demoIncidentId for 100% reversible reset.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            // Active CIE Alert Box (Exact Wording Requirement)
            if (hasActiveIncident) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3B151A)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🚨 CIE ALERT",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = Color(0xFFFF6B6B)
                        )

                        Text(
                            text = "Unusual cross-vendor return pattern detected.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.3f),
                            thickness = 0.5.dp
                        )

                        Text(
                            text = "Product: ${cieAlert?.productName?.ifBlank { "Soya Sticks" } ?: "Soya Sticks"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Affected vendors: ${cieAlert?.affectedVendorsCount?.takeIf { it > 0 } ?: 8}",
                            fontSize = 12.sp,
                            color = Color(0xFFFFD1D1)
                        )
                        Text(
                            text = "Returns: ${cieAlert?.totalReturnsCount?.takeIf { it > 0 } ?: 8}",
                            fontSize = 12.sp,
                            color = Color(0xFFFFD1D1)
                        )
                        Text(
                            text = "Time window: ${cieAlert?.timeWindowMinutes ?: 25} minutes",
                            fontSize = 12.sp,
                            color = Color(0xFFFFD1D1)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.3f),
                            thickness = 0.5.dp
                        )

                        Text(
                            text = "Possible network-wide product issue.\nVerification required.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFC1C1),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Two Action Buttons Together: [ 🚨 Simulate Cross-Vendor Incident ] and [ ↩️ Reset Demo Incident ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Simulate
                Button(
                    onClick = onSimulateIncident,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "🚨",
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Simulate Incident",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Button 2: Reset Demo Incident (Disabled when no demo incident exists)
                OutlinedButton(
                    onClick = onOpenResetDialog,
                    enabled = hasActiveIncident && !isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (hasActiveIncident) ResilienceAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (hasActiveIncident) ResilienceAmber.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "↩️",
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Reset Incident",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}


