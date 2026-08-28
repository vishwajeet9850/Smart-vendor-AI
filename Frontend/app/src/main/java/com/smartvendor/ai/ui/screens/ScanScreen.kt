package com.smartvendor.ai.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
                            text = "Smart POS Scanner",
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
                    IconButton(onClick = { viewModel.openManualEntryDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Manually", tint = RedPrimary)
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

                // Top Mode Toggle Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                    }
                }

                // Auto-Added Multi-Product Toast Notification with Live Undo
                if (uiState.lastAddedProducts.isNotEmpty()) {
                    MultiAddedUndoToast(
                        products = uiState.lastAddedProducts,
                        timestamp = uiState.lastAddedTimestamp,
                        onUndo = { viewModel.undoLastAddedBatch() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 92.dp, start = 16.dp, end = 16.dp)
                    )
                }

                // Bottom POS Bill Bar: Items Count, Whole Bill Amount & Review Bill Action
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val totalCount = uiState.currentBill?.items?.sumOf { it.quantity } ?: 0
                            val grandTotal = uiState.currentBill?.grandTotal ?: 0.0

                            Text(
                                text = "$totalCount Items in Bill",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = "₹${"%.2f".format(grandTotal)}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RedPrimary
                                )
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Voice Bill Shortcut in Scanner POS bar
                            IconButton(
                                onClick = { viewModel.openVoiceDialog() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(RedPrimary.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Bill",
                                    tint = RedPrimary
                                )
                            }

                            // Review Bill Button
                            Button(
                                onClick = { onNavigateToBilling(billId) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                            ) {
                                Text("Review Bill", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Camera Permission")
                    }
                }
            }

            // Voice Billing Modal Sheet
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
fun MultiAddedUndoToast(
    products: List<Product>,
    timestamp: Long,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember(timestamp) { mutableStateOf(true) }

    LaunchedEffect(timestamp) {
        delay(3500L)
        isVisible = false
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = AccentGreen,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column {
                        val title = if (products.size == 1) "Added +1" else "Added +${products.size} Products"
                        val subtitle = products.joinToString(", ") { "${it.name} (₹${it.price.toInt()})" }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall.copy(color = AccentGreen, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = subtitle,
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
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
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
