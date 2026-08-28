package com.smartvendor.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.model.BillItem
import com.smartvendor.ai.model.Store
import com.smartvendor.ai.repository.StoreRepositoryImpl
import com.smartvendor.ai.ui.components.VoiceBillingSheet
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.RedPrimary
import com.smartvendor.ai.ui.theme.WarningYellow
import com.smartvendor.ai.utils.QrCodeUtils
import com.smartvendor.ai.utils.SmsUtils
import com.smartvendor.ai.utils.WhatsAppUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    billId: String,
    viewModel: BillingViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onStartNewBill: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val storeRepository = remember { StoreRepositoryImpl() }
    val storeInfo by storeRepository.getStoreInfo().collectAsState(initial = Store())

    var showDigitalReceiptDialog by remember { mutableStateOf(false) }
    var showUpiQrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = billId) {
        viewModel.loadBill(billId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billing Summary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!uiState.checkoutSuccess) {
                        IconButton(onClick = { viewModel.openVoiceDialog() }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Billing", tint = RedPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {}
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val bill = uiState.bill

            if (uiState.checkoutSuccess && bill != null) {
                // Checkout Success View
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Success",
                                modifier = Modifier.size(72.dp),
                                tint = AccentGreen
                            )

                            Text(
                                text = "Checkout Successful!",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )

                            Text(
                                text = "Bill Total: ₹${if (bill.grandTotal % 1.0 == 0.0) bill.grandTotal.toInt() else bill.grandTotal}  •  ${bill.paymentMethod}",
                                style = MaterialTheme.typography.titleMedium.copy(color = RedPrimary, fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // 1. Send Digital Receipt
                            Button(
                                onClick = { showDigitalReceiptDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send Digital Receipt", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            // 2. Scan New Bill
                            Button(
                                onClick = onStartNewBill,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan New Bill", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            // 3. Return to Dashboard
                            OutlinedButton(
                                onClick = onNavigateToDashboard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Home, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Return to Dashboard", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (bill != null && bill.items.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(bill.items, key = { it.productId }) { item ->
                            BillItemRow(
                                item = item,
                                onIncrease = { viewModel.increaseItemQuantity(item.productId) },
                                onDecrease = { viewModel.decreaseItemQuantity(item.productId) },
                                onDelete = { viewModel.removeItem(item.productId) }
                            )
                        }
                    }

                    // Checkout & Summary Footer
                    BillingFooterCard(
                        subtotal = bill.subtotal,
                        gst = bill.gst,
                        discount = bill.discount,
                        grandTotal = bill.grandTotal,
                        selectedPaymentMethod = uiState.selectedPaymentMethod,
                        isProcessing = uiState.isProcessingCheckout,
                        onPaymentMethodSelect = { viewModel.setPaymentMethod(it) },
                        onShowUpiQr = { showUpiQrDialog = true },
                        onCheckout = {
                            viewModel.performCheckout {
                                // Checkout completes
                            }
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Text(
                            text = "Current Bill is Empty",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Scan products with camera or tap 'Voice Bill' to speak items.",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onNavigateBack) {
                                Text("Back to Scanner")
                            }
                            Button(
                                onClick = { viewModel.openVoiceDialog() },
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Voice Bill")
                            }
                        }
                    }
                }
            }

            if (showUpiQrDialog && uiState.bill != null) {
                UpiPaymentQrDialog(
                    billId = uiState.bill!!.billId,
                    grandTotal = uiState.bill!!.grandTotal,
                    storeName = storeInfo.name.ifBlank { "SmartVendor Store" },
                    upiId = storeInfo.upi.ifBlank { "smartvendor@upi" },
                    onDismiss = { showUpiQrDialog = false },
                    onPaymentConfirmed = {
                        showUpiQrDialog = false
                        viewModel.setPaymentMethod("UPI")
                        viewModel.performCheckout {}
                    }
                )
            }

            if (showDigitalReceiptDialog && uiState.bill != null) {
                DigitalReceiptDialog(
                    bill = uiState.bill!!,
                    store = storeInfo,
                    onDismiss = { showDigitalReceiptDialog = false }
                )
            }

            // Voice Billing Modal Bottom Sheet
            if (uiState.showVoiceDialog) {
                VoiceBillingSheet(
                    onDismiss = { viewModel.closeVoiceDialog() },
                    onAddItemsToBill = { voiceItems ->
                        viewModel.addVoiceItemsToBill(voiceItems)
                    }
                )
            }
        }
    }
}

@Composable
fun BillItemRow(
    item: BillItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "₹${if (item.unitPrice % 1.0 == 0.0) item.unitPrice.toInt() else item.unitPrice} each",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Text(
                    text = "${item.quantity}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = onIncrease,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Text(
                    text = "₹${if (item.lineTotal % 1.0 == 0.0) item.lineTotal.toInt() else item.lineTotal}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = RedPrimary
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )

                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun BillingFooterCard(
    subtotal: Double,
    gst: Double,
    discount: Double,
    grandTotal: Double,
    selectedPaymentMethod: String,
    isProcessing: Boolean,
    onPaymentMethodSelect: (String) -> Unit,
    onShowUpiQr: () -> Unit,
    onCheckout: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Payment Mode Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("CASH", "UPI", "KHATA").forEach { mode ->
                    val isSelected = selectedPaymentMethod.equals(mode, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onPaymentMethodSelect(mode)
                            if (mode == "UPI") onShowUpiQr()
                        },
                        label = { Text(mode, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RedPrimary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Total Calculation Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Subtotal: ₹${if (subtotal % 1.0 == 0.0) subtotal.toInt() else subtotal}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                    Text(
                        text = "Grand Total",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "₹${if (grandTotal % 1.0 == 0.0) grandTotal.toInt() else grandTotal}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = RedPrimary
                    )
                )
            }

            // Checkout Button
            Button(
                onClick = onCheckout,
                enabled = !isProcessing && grandTotal > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Complete Checkout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun UpiPaymentQrDialog(
    billId: String,
    grandTotal: Double,
    storeName: String,
    upiId: String,
    onDismiss: () -> Unit,
    onPaymentConfirmed: () -> Unit
) {
    val upiPayload = "upi://pay?pa=$upiId&pn=${storeName.replace(" ", "%20")}&am=$grandTotal&cu=INR&tn=Bill_$billId"
    val qrBitmap = remember(upiPayload) { QrCodeUtils.generateQrCodeBitmap(upiPayload, 300, 300) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan UPI QR to Pay", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "UPI QR Code",
                        modifier = Modifier
                            .size(220.dp)
                            .padding(8.dp)
                    )
                }
                Text("Total: ₹${if (grandTotal % 1.0 == 0.0) grandTotal.toInt() else grandTotal}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = RedPrimary)
                Text("UPI ID: $upiId", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                onClick = onPaymentConfirmed,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Text("Payment Received")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DigitalReceiptDialog(
    bill: Bill,
    store: Store,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var customerPhone by remember { mutableStateOf("") }
    var sendStatus by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Digital Receipt", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text("Customer Mobile Number") },
                    placeholder = { Text("e.g. 9876543210") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (sendStatus != null) {
                    Text(
                        text = sendStatus!!,
                        color = if (sendStatus!!.startsWith("Sent")) AccentGreen else RedPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (customerPhone.length >= 10) {
                                val text = buildReceiptText(bill, store)
                                WhatsAppUtils.sendWhatsAppBill(context, customerPhone, bill)
                                sendStatus = "Opened WhatsApp to send."
                            } else {
                                sendStatus = "Please enter valid 10-digit number."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("WhatsApp", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (customerPhone.length >= 10) {
                                val text = buildReceiptText(bill, store)
                                SmsUtils.sendSilentSmsReceipt(context, customerPhone, bill)
                                sendStatus = "Sent SMS to $customerPhone."
                            } else {
                                sendStatus = "Please enter valid 10-digit number."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("SMS", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private fun buildReceiptText(bill: Bill, store: Store): String {
    val storeName = store.name.ifBlank { "Smart Vendor Kirana" }
    val itemsSummary = bill.items.joinToString("\n") {
        "- ${it.name} (${it.quantity}x) = ₹${it.lineTotal.toInt()}"
    }
    return """
        🛒 *${storeName}*
        Bill ID: ${bill.billId}
        -----------------------
        ${itemsSummary}
        -----------------------
        *Total: ₹${bill.grandTotal.toInt()}*
        Payment: ${bill.paymentMethod}
        
        Thank you for shopping with us! 🙏
    """.trimIndent()
}
