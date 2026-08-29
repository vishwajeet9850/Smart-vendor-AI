package com.smartvendor.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
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
import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.model.BillItem
import com.smartvendor.ai.repository.SalesRepository
import com.smartvendor.ai.repository.SalesRepositoryImpl
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BluePrimary
import com.smartvendor.ai.utils.SmsUtils
import com.smartvendor.ai.utils.WhatsAppUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    salesRepository: SalesRepository = remember { SalesRepositoryImpl() },
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var selectedBillForDetail by remember { mutableStateOf<Bill?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        salesRepository.getSalesHistoryStream().collect { list ->
            bills = list.filter { it.status == Bill.BILL_STATUS_COMPLETED }
            isLoading = false
        }
    }

    val filteredBills = remember(searchQuery, bills) {
        if (searchQuery.isBlank()) bills
        else bills.filter {
            it.billId.contains(searchQuery, ignoreCase = true) ||
                    it.paymentMethod.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bill History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by Bill ID or Payment Mode...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BluePrimary)
                    }
                } else if (filteredBills.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredBills, key = { it.billId }) { bill ->
                            BillHistoryCard(
                                bill = bill,
                                onClick = { selectedBillForDetail = bill }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Invoices Found",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Make sure server is running in CMD, then tap Refresh.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { refreshTrigger++ },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refresh History")
                            }
                        }
                    }
                }
            }

            selectedBillForDetail?.let { bill ->
                BillDetailDialog(
                    bill = bill,
                    onDismiss = { selectedBillForDetail = null }
                )
            }
        }
    }
}

@Composable
fun BillHistoryCard(
    bill: Bill,
    onClick: () -> Unit
) {
    val isReturn = bill.transactionType == Bill.TRANSACTION_TYPE_RETURN

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReturn) Color(0xFFFFF5F5).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isReturn) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.4f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isReturn) "RETURN #${bill.billId.takeLast(8)}" else "Bill #${bill.billId.takeLast(8)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isReturn) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1
                    )
                    if (isReturn) {
                        Surface(
                            color = Color(0xFFD32F2F),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "RETURN",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${bill.items.sumOf { it.quantity }} ${if (isReturn) "Items Returned" else "Items"}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = if (isReturn) Color(0xFFD32F2F).copy(alpha = 0.12f) else BluePrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isReturn) "REFUND: ${bill.paymentMethod}" else "MODE: ${bill.paymentMethod}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isReturn) Color(0xFFD32F2F) else BluePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Text(
                text = if (isReturn) "-₹${"%.2f".format(bill.grandTotal)}" else "₹${"%.2f".format(bill.grandTotal)}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isReturn) Color(0xFFD32F2F) else AccentGreen
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun BillDetailDialog(
    bill: Bill,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isReturn = bill.transactionType == Bill.TRANSACTION_TYPE_RETURN
    var showSendPrompt by remember { mutableStateOf(false) }
    var phoneInput by remember { mutableStateOf("") }
    val dailySmsCount = remember { SmsUtils.getDailySmsCount(context) }
    val isLimitReached = dailySmsCount >= SmsUtils.DAILY_SMS_LIMIT

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val sent = SmsUtils.sendSilentSmsReceipt(context, phoneInput, bill)
            if (sent) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isReturn) "Return Receipt Details" else "Invoice Details",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isReturn) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFD32F2F),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "RETURN",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transaction ID: ${bill.billId}", fontWeight = FontWeight.Bold)
                Text(if (isReturn) "Refund Method: ${bill.paymentMethod}" else "Payment Method: ${bill.paymentMethod}")
                HorizontalDivider()
                Text(if (isReturn) "Items Returned:" else "Items Purchased:", fontWeight = FontWeight.Bold)
                bill.items.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.name} ×${item.quantity}",
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isReturn) "-₹${"%.2f".format(item.lineTotal)}" else "₹${"%.2f".format(item.lineTotal)}",
                                color = if (isReturn) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (isReturn) {
                            val isGood = item.condition == BillItem.CONDITION_GOOD
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                color = if (isGood) AccentGreen.copy(alpha = 0.15f) else Color(0xFFD32F2F).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isGood) "🟢 Restocked (+${item.quantity})" else "🔴 Damaged (Not Restocked)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isGood) AccentGreen else Color(0xFFFF5252),
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (isReturn) "Refund Total" else "Grand Total", fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isReturn) "-₹${"%.2f".format(bill.grandTotal)}" else "₹${"%.2f".format(bill.grandTotal)}",
                        fontWeight = FontWeight.Bold,
                        color = if (isReturn) Color(0xFFD32F2F) else BluePrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (showSendPrompt) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Customer Mobile Number") },
                        placeholder = { Text("e.g. 9876543210") },
                        leadingIcon = { Text("🇮🇳 +91 ", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: Direct Silent SMS
                    Button(
                        onClick = {
                            if (phoneInput.length >= 10) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                                    val sent = SmsUtils.sendSilentSmsReceipt(context, phoneInput, bill)
                                    if (sent) onDismiss()
                                } else {
                                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                }
                            }
                        },
                        enabled = !isLimitReached && phoneInput.length >= 10,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isReturn) Color(0xFFD32F2F) else BluePrimary)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚀 ", fontSize = 16.sp)
                            Text(if (isReturn) "Send Return SMS Receipt" else "Send Silent SMS Receipt", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Option 2: Send via WhatsApp
                    Button(
                        onClick = {
                            if (phoneInput.length >= 10) {
                                WhatsAppUtils.sendWhatsAppBill(context, phoneInput, bill)
                                onDismiss()
                            }
                        },
                        enabled = phoneInput.length >= 10,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💬 ", fontSize = 16.sp)
                            Text("Send via WhatsApp", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!showSendPrompt) {
                Button(
                    onClick = { showSendPrompt = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReturn) Color(0xFFD32F2F) else BluePrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isReturn) "Send Return Receipt 📱" else "Send Digital Receipt 📱", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


