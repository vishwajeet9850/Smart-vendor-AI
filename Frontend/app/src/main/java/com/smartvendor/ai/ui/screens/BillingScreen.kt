package com.smartvendor.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.model.BillItem
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BluePrimary
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
    var showDigitalReceiptDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = billId) {
        viewModel.loadBill(billId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billing Summary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            val bill = uiState.bill

            if (uiState.checkoutSuccess && bill != null) {
                // Checkout Success View with 3 Clear Post-Checkout Options
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
                                text = "Bill Total: ₹${"%.2f".format(bill.grandTotal)}  •  ${bill.paymentMethod}",
                                style = MaterialTheme.typography.titleMedium.copy(color = BluePrimary, fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // 1. Send Digital Receipt
                            Button(
                                onClick = { showDigitalReceiptDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send Digital Receipt 📱", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            // 2. Scan New Bill (Directly opens Camera Scanner for Next Customer)
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
                                Text("Scan New Bill 📷", fontWeight = FontWeight.Bold, color = Color.White)
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
                                Text("Return to Dashboard 🏠", fontWeight = FontWeight.Bold)
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        onCheckout = {
                            viewModel.performCheckout {
                                // Checkout completes, presenting option to share digital receipt
                            }
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Current Bill is Empty",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scan products from live camera feed to add items.",
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Back to Scanner")
                        }
                    }
                }
            }

            if (showDigitalReceiptDialog && uiState.bill != null) {
                DigitalReceiptDialog(
                    bill = uiState.bill!!,
                    onDismiss = { showDigitalReceiptDialog = false },
                    onSendSms = { phone ->
                        val sent = SmsUtils.sendSilentSmsReceipt(context, phone, uiState.bill!!)
                        if (sent) showDigitalReceiptDialog = false
                    },
                    onSendWhatsApp = { phone ->
                        WhatsAppUtils.sendWhatsAppBill(context, phone, uiState.bill!!)
                        showDigitalReceiptDialog = false
                    }
                )
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
}

@Composable
fun BillItemRow(
    item: BillItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "₹${"%.2f".format(item.unitPrice)}  |  GST: ${item.gst}%",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(18.dp))
                }

                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                IconButton(
                    onClick = onIncrease,
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "₹${"%.2f".format(item.lineTotal)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = BluePrimary
                    )
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
    onCheckout: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select Payment Mode",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("CASH", "UPI", "CARD", "OTHER").forEach { mode ->
                    val isSelected = selectedPaymentMethod == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPaymentMethodSelect(mode) },
                        label = { Text(mode) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluePrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal", color = Color.Gray)
                Text("₹${"%.2f".format(subtotal)}")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("GST Tax", color = Color.Gray)
                Text("₹${"%.2f".format(gst)}")
            }
            if (discount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Discount", color = AccentGreen)
                    Text("-₹${"%.2f".format(discount)}", color = AccentGreen)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Grand Total", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = "₹${"%.2f".format(grandTotal)}",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BluePrimary
                    )
                )
            }

            Button(
                onClick = onCheckout,
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Complete Checkout", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DigitalReceiptDialog(
    bill: Bill,
    onDismiss: () -> Unit,
    onSendSms: (String) -> Unit,
    onSendWhatsApp: (String) -> Unit
) {
    val context = LocalContext.current
    var phoneInput by remember { mutableStateOf("") }
    val dailySmsCount = remember { SmsUtils.getDailySmsCount(context) }
    val isLimitReached = dailySmsCount >= SmsUtils.DAILY_SMS_LIMIT

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onSendSms(phoneInput)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = BluePrimary)
                Text("Send Digital Receipt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enter Customer Mobile Number for digital receipt delivery:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Customer Mobile Number") },
                    placeholder = { Text("e.g. 9876543210") },
                    leadingIcon = { Text("🇮🇳 +91 ", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLimitReached) {
                    Surface(
                        color = Color.Red.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ Daily free SMS limit ($dailySmsCount/${SmsUtils.DAILY_SMS_LIMIT}) reached. Please send via WhatsApp below 💬",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.Red, fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    Text(
                        text = "Daily Free SMS Used: $dailySmsCount / ${SmsUtils.DAILY_SMS_LIMIT}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontWeight = FontWeight.Medium)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Option 1: Direct Silent SMS (Icon at Front)
                Button(
                    onClick = {
                        if (phoneInput.length >= 10) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                                onSendSms(phoneInput)
                            } else {
                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            }
                        }
                    },
                    enabled = !isLimitReached && phoneInput.length >= 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("🚀 ", fontSize = 16.sp)
                        Text("Send Silent SMS Receipt", fontWeight = FontWeight.Bold)
                    }
                }

                // Option 2: WhatsApp Receipt (Icon at Front - WhatsApp Green)
                Button(
                    onClick = {
                        if (phoneInput.length >= 10) {
                            onSendWhatsApp(phoneInput)
                        }
                    },
                    enabled = phoneInput.length >= 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("💬 ", fontSize = 16.sp)
                        Text("Send via WhatsApp", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", fontWeight = FontWeight.Bold, color = Color.Gray)
            }
        }
    )
}
