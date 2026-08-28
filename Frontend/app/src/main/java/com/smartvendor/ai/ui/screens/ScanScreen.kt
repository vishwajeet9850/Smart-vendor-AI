package com.smartvendor.ai.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartvendor.ai.ai.DetectionOverlayView
import com.smartvendor.ai.camera.CameraPreviewView
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.ui.components.VoiceBillingSheet
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BluePrimary
import com.smartvendor.ai.ui.theme.RedPrimary
import com.smartvendor.ai.ui.theme.WarningYellow
import com.smartvendor.ai.utils.PermissionUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    billId: String,
    viewModel: ScanViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToBilling: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(PermissionUtils.hasCameraPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBill()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(key1 = billId) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        viewModel.initialize(context, billId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Smart Scanner & Bill",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Bill #${billId.takeLast(6)}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openVoiceDialog() }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Billing", tint = RedPrimary)
                    }
                    IconButton(onClick = { viewModel.openManualEntryDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Manually", tint = RedPrimary)
                    }
                    Surface(
                        color = RedPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { onNavigateToBilling(billId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ShoppingCart,
                                contentDescription = null,
                                tint = RedPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${uiState.currentBill?.items?.sumOf { it.quantity } ?: 0} Items",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RedPrimary
                                )
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = { viewModel.openVoiceDialog() },
                    containerColor = RedPrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Bill")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Voice Bill", fontWeight = FontWeight.Bold)
                    }
                }

                FloatingActionButton(
                    onClick = { onNavigateToBilling(billId) },
                    containerColor = AccentGreen,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Review Bill")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Review Bill", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasCameraPermission) {
                CameraPreviewView(
                    onFrameAvailable = { imageProxy ->
                        viewModel.processFrame(imageProxy)
                    },
                    onCameraInitialized = { },
                    onCameraError = { }
                )

                DetectionOverlayView(
                    detections = uiState.activeDetections
                )

                if (uiState.isBarcodeActive) {
                    BarcodeTargetOverlay()
                }

                // Top Mode Toggle Row with Auto-Add Switch
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !uiState.isBarcodeActive && !uiState.isOcrActive,
                            onClick = { viewModel.toggleScanMode(useOcr = false) },
                            label = { Text("Smart AI") }
                        )
                        FilterChip(
                            selected = uiState.isBarcodeActive,
                            onClick = { viewModel.toggleBarcodeMode(!uiState.isBarcodeActive) },
                            label = { Text("Barcode") }
                        )
                        FilterChip(
                            selected = uiState.isOcrActive,
                            onClick = { viewModel.toggleScanMode(useOcr = !uiState.isOcrActive) },
                            label = { Text("Price/OCR") }
                        )

                        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                        // Auto-Add Toggle Chip
                        FilterChip(
                            selected = uiState.autoAddEnabled,
                            onClick = { viewModel.toggleAutoAdd() },
                            label = {
                                Text(
                                    text = if (uiState.autoAddEnabled) "⚡ Auto: ON" else "✋ Auto: OFF",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Auto-Add Toast / Banner with Live Countdown Timer & Undo Button
                if (uiState.lastAutoAddedProduct != null) {
                    AutoAddUndoBanner(
                        product = uiState.lastAutoAddedProduct!!,
                        timestamp = uiState.lastAutoAddedTimestamp,
                        onUndo = { viewModel.undoLastAutoAddedProduct() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 88.dp, start = 16.dp, end = 16.dp)
                    )
                }

                // Manual Detected Product Card (when Auto-Add is OFF)
                if (!uiState.autoAddEnabled && uiState.detectedProduct != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                    ) {
                        DetectedProductCard(
                            product = uiState.detectedProduct!!,
                            selectedQuantity = uiState.selectedQuantity,
                            onIncrease = { viewModel.increaseQuantity() },
                            onDecrease = { viewModel.decreaseQuantity() },
                            onAdd = { viewModel.addProductToBill() },
                            onCancel = { viewModel.cancelDetection() }
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Camera Permission")
                    }
                }
            }

            // Voice Billing Modal
            if (uiState.showVoiceDialog) {
                VoiceBillingSheet(
                    onDismiss = { viewModel.closeVoiceDialog() },
                    onAddItemsToBill = { voiceItems ->
                        viewModel.addVoiceItemsToBill(voiceItems)
                    }
                )
            }

            // Manual Product Entry Dialog
            if (uiState.showManualEntryDialog) {
                ManualEntryDialog(
                    prefilledName = uiState.ocrPrefilledName,
                    prefilledPrice = uiState.ocrPrefilledPrice,
                    onDismiss = { viewModel.closeManualEntryDialog() },
                    onConfirm = { name, price, quantity ->
                        viewModel.addManualProductToBill(name, price, quantity)
                    }
                )
            }
        }
    }
}

@Composable
fun AutoAddUndoBanner(
    product: Product,
    timestamp: Long,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember(timestamp) { mutableFloatStateOf(1f) }
    var isVisible by remember(timestamp) { mutableStateOf(true) }

    LaunchedEffect(timestamp) {
        val durationMs = 3500L
        val stepMs = 50L
        val totalSteps = durationMs / stepMs

        for (i in totalSteps downTo 0) {
            progress = i.toFloat() / totalSteps.toFloat()
            delay(stepMs)
        }
        isVisible = false
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = AccentGreen,
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Auto-Added +1",
                                style = MaterialTheme.typography.labelSmall.copy(color = AccentGreen, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${product.name} (₹${product.price.toInt()})",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = {
                            isVisible = false
                            onUndo()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Undo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Smooth countdown timer bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = AccentGreen,
                    trackColor = Color.White.copy(alpha = 0.1f),
                )
            }
        }
    }
}

@Composable
fun BarcodeTargetOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val boxWidth = size.width * 0.7f
        val boxHeight = size.height * 0.25f
        val left = (size.width - boxWidth) / 2
        val top = (size.height - boxHeight) / 2

        drawRoundRect(
            color = Color.Red,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f))
        )
    }
}

@Composable
fun DetectedProductCard(
    product: Product,
    selectedQuantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Category: ${product.category}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                }

                Text(
                    text = "₹${product.price.toInt()}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = RedPrimary
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onDecrease,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                    }

                    Text(
                        text = "$selectedQuantity",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    IconButton(
                        onClick = onIncrease,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCancel) {
                        Text("Ignore (8s)", color = Color.Gray)
                    }

                    Button(
                        onClick = onAdd,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add to Bill")
                    }
                }
            }
        }
    }
}

@Composable
fun ManualEntryDialog(
    prefilledName: String,
    prefilledPrice: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Int) -> Unit
) {
    var name by remember { mutableStateOf(prefilledName) }
    var price by remember { mutableStateOf(prefilledPrice) }
    var quantity by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Item Entry", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (₹)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = price.toDoubleOrNull() ?: 0.0
                    val q = quantity.toIntOrNull() ?: 1
                    if (name.isNotBlank() && p > 0) {
                        onConfirm(name, p, q)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Text("Add to Bill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
