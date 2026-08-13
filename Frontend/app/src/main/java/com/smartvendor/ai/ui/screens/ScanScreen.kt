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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartvendor.ai.ai.DetectionOverlayView
import com.smartvendor.ai.camera.CameraPreviewView
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BluePrimary
import com.smartvendor.ai.ui.theme.WarningYellow
import com.smartvendor.ai.utils.PermissionUtils

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
                            text = "Scan / Add Items to Bill",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Bill ID: ${billId.take(8)}...",
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
                        Icon(Icons.Default.Add, contentDescription = "Add Manually", tint = BluePrimary)
                    }
                    Surface(
                        color = BluePrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ShoppingCart,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${uiState.currentBill?.items?.sumOf { it.quantity } ?: 0} Items",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BluePrimary
                                )
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openManualEntryDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Product Manually") },
                containerColor = BluePrimary,
                contentColor = Color.White
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasCameraPermission) {
                // Live Camera View
                CameraPreviewView(
                    onFrameAvailable = { imageProxy ->
                        viewModel.processFrame(imageProxy)
                    },
                    onCameraInitialized = { },
                    onCameraError = { }
                )

                // Bounding Box Overlay (Object Detection)
                DetectionOverlayView(
                    detections = uiState.activeDetections
                )

                // Visual Barcode Scanner Target Box Overlay
                if (uiState.isBarcodeActive) {
                    BarcodeTargetOverlay()
                }

                // Top AI / Barcode / OCR Status Banner & Mode Toggle Row
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiStatusBanner(
                        status = uiState.aiStatus,
                        isBarcodeActive = uiState.isBarcodeActive || uiState.isOcrActive
                    )

                    // Scanner Mode Chips: Barcode vs OCR Label Reader
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !uiState.isOcrActive,
                            onClick = { viewModel.toggleScanMode(useOcr = false) },
                            label = { Text("📷 Barcode Scanner", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BluePrimary,
                                selectedLabelColor = Color.White
                            )
                        )

                        FilterChip(
                            selected = uiState.isOcrActive,
                            onClick = { viewModel.toggleScanMode(useOcr = true) },
                            label = { Text("📝 Label OCR", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BluePrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Bottom Content: Current Detected Product Card or Bill Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(
                        visible = uiState.detectedProduct != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        uiState.detectedProduct?.let { product ->
                            DetectedProductCard(
                                product = product,
                                selectedQuantity = uiState.selectedQuantity,
                                onIncrease = { viewModel.increaseQuantity() },
                                onDecrease = { viewModel.decreaseQuantity() },
                                onAdd = { viewModel.addProductToBill() },
                                onCancel = { viewModel.cancelDetection() }
                            )
                        }
                    }

                    // Bottom Billing Bar
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Current Total",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                )
                                Text(
                                    text = "₹${"%.2f".format(uiState.currentBill?.grandTotal ?: 0.0)}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            Button(
                                onClick = {
                                    val activeBillId = uiState.currentBill?.billId ?: billId
                                    if (activeBillId.isNotBlank()) {
                                        onNavigateToBilling(activeBillId)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("View Bill")
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            } else {
                // Permission Denied View
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Camera,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SmartVendor AI needs camera access to scan barcodes live or add items.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Camera Permission")
                        }
                    }
                }
            }

            if (uiState.showManualEntryDialog) {
                ManualProductEntryDialog(
                    initialName = uiState.ocrPrefilledName,
                    initialPrice = uiState.ocrPrefilledPrice,
                    inventoryProducts = uiState.inventoryProducts,
                    onDismiss = { viewModel.dismissManualEntryDialog() },
                    onAddExistingProduct = { product, qty ->
                        viewModel.addExistingProductToBill(product, qty)
                    },
                    onSaveNewProduct = { name, price, stock, cat, barcode, qty ->
                        viewModel.saveManualProduct(name, price, stock, cat, barcode, qty)
                    }
                )
            }
        }
    }
}

@Composable
fun BarcodeTargetOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserLine"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 260.dp, height = 170.dp)
                    .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .border(2.dp, BluePrimary.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            ) {
                // Red scanning laser line
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val laserY = size.height * laserYRatio
                    drawLine(
                        color = Color.Red,
                        start = Offset(x = 10.dp.toPx(), y = laserY),
                        end = Offset(x = size.width - 10.dp.toPx(), y = laserY),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Point camera barcode inside box",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
fun AiStatusBanner(
    status: String,
    isBarcodeActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = if (isBarcodeActive) BluePrimary else AccentGreen,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color.White, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isBarcodeActive) "📷 Barcode Scanner Active" else "🤖 AI Object Detection Active",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
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
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Category: ${product.category}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                }
                Text(
                    text = "₹${"%.2f".format(product.price)}",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BluePrimary
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (product.stock > 0) AccentGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (product.stock > 0) "In Stock (${product.stock})" else "Out of Stock",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (product.stock > 0) AccentGreen else Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onDecrease,
                        enabled = selectedQuantity > 1,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }

                    Text(
                        text = "$selectedQuantity",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    IconButton(
                        onClick = onIncrease,
                        enabled = true,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }

            if (selectedQuantity > product.stock) {
                Surface(
                    color = WarningYellow.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ Exceeds app stock (${product.stock} listed) — billing allowed",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = WarningYellow,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onAdd,
                    enabled = true,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add to Bill")
                }
            }
        }
    }
}

@Composable
fun ManualProductEntryDialog(
    initialName: String = "",
    initialPrice: String = "",
    inventoryProducts: List<Product> = emptyList(),
    onDismiss: () -> Unit,
    onAddExistingProduct: (Product, Int) -> Unit,
    onSaveNewProduct: (String, Double, Int, String, String, Int) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }

    var price by remember(initialPrice) { mutableStateOf(initialPrice) }
    var stock by remember { mutableStateOf("10") }
    var category by remember { mutableStateOf("General") }
    var barcode by remember { mutableStateOf("") }

    var quantityToSell by remember { mutableStateOf(1) }

    // Live search suggestions as user types name
    val suggestions = remember(name, selectedProduct, inventoryProducts) {
        if (name.isBlank() || selectedProduct != null) {
            emptyList()
        } else {
            inventoryProducts.filter {
                it.name.contains(name, ignoreCase = true)
            }.take(4)
        }
    }

    // Auto select exact match
    LaunchedEffect(name) {
        if (selectedProduct == null && name.isNotBlank()) {
            val exact = inventoryProducts.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (exact != null) {
                selectedProduct = exact
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (selectedProduct != null) "Select Quantity to Sell" else "Manual Product Entry",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (selectedProduct != null) {
                    val prod = selectedProduct!!
                    // Existing Inventory Product Found Banner Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0xFF4CAF50),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "INVENTORY MATCH",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        selectedProduct = null
                                        name = ""
                                    }
                                ) {
                                    Text("Change Item", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = prod.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Price: ₹${prod.price}  •  Stock Available: ${prod.stock}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Quantity selector (- 1 +)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quantity to Sell:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = { if (quantityToSell > 1) quantityToSell-- },
                                enabled = quantityToSell > 1,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }

                            Text(
                                text = "$quantityToSell",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )

                            IconButton(
                                onClick = { quantityToSell++ },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }

                    if (quantityToSell > prod.stock) {
                        Surface(
                            color = WarningYellow.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Selling $quantityToSell units exceeds listed stock (${prod.stock}). Billing allowed.",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = WarningYellow,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                } else {
                    // Search & New Product Form Mode
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                selectedProduct = null
                            },
                            label = { Text("Product Name") },
                            placeholder = { Text("Type to search inventory...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Autocomplete Suggestions List
                        if (suggestions.isNotEmpty()) {
                            Card(
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(4.dp)) {
                                    Text(
                                        text = "Matching Store Items:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    suggestions.forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedProduct = item
                                                    name = item.name
                                                }
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "₹${item.price} (Stock: ${item.stock})",
                                                fontSize = 12.sp,
                                                color = BluePrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Price (₹)") },
                            placeholder = { Text("e.g. 50.00") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = stock,
                            onValueChange = { stock = it },
                            label = { Text("Available Stock Quantity") },
                            placeholder = { Text("e.g. 20") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quantity to Sell row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quantity to Sell:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { if (quantityToSell > 1) quantityToSell-- },
                                    enabled = quantityToSell > 1,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                }

                                Text(
                                    text = "$quantityToSell",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                IconButton(
                                    onClick = { quantityToSell++ },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedProduct != null) {
                        onAddExistingProduct(selectedProduct!!, quantityToSell)
                    } else {
                        val priceVal = price.toDoubleOrNull() ?: 0.0
                        val stockVal = stock.toIntOrNull() ?: 10
                        if (name.isNotBlank() && priceVal > 0) {
                            onSaveNewProduct(name, priceVal, stockVal, category, barcode, quantityToSell)
                        }
                    }
                }
            ) {
                Text(
                    text = if (selectedProduct != null) "Add $quantityToSell to Bill" else "Save & Add to Bill"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
