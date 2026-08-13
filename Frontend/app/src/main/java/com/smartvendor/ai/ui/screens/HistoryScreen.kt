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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bill #${bill.billId.takeLast(8)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${bill.items.sumOf { it.quantity }} Items",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = BluePrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "MODE: ${bill.paymentMethod}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = BluePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Text(
                text = "₹${"%.2f".format(bill.grandTotal)}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
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
        title = { Text("Invoice Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bill ID: ${bill.billId}", fontWeight = FontWeight.Bold)
                Text("Payment Method: ${bill.paymentMethod}")
                HorizontalDivider()
                Text("Items Purchased:", fontWeight = FontWeight.Bold)
                bill.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${item.name} x${item.quantity}")
                        Text("₹${"%.2f".format(item.lineTotal)}")
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Grand Total", fontWeight = FontWeight.Bold)
                    Text("₹${"%.2f".format(bill.grandTotal)}", fontWeight = FontWeight.Bold, color = BluePrimary)
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
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚀 ", fontSize = 16.sp)
                            Text("Send Silent SMS Receipt", fontWeight = FontWeight.Bold)
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Digital Receipt 📱", fontWeight = FontWeight.Bold)
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
