package com.smartvendor.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.repository.SalesRepository
import com.smartvendor.ai.repository.SalesRepositoryImpl
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    salesRepository: SalesRepository = remember { SalesRepositoryImpl() },
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var selectedBillForDetail by remember { mutableStateOf<Bill?>(null) }

    LaunchedEffect(Unit) {
        salesRepository.getSalesHistoryStream().collect { list ->
            bills = list.filter { it.status == Bill.BILL_STATUS_COMPLETED }
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

                if (filteredBills.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
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
                    text = bill.billId,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatDate(bill.timestamp)}  |  ${bill.items.sumOf { it.quantity }} Items",
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invoice ${bill.billId}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date: ${formatDate(bill.timestamp)}")
                Text("Payment Method: ${bill.paymentMethod}")
                Divider()
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
                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Grand Total", fontWeight = FontWeight.Bold)
                    Text("₹${"%.2f".format(bill.grandTotal)}", fontWeight = FontWeight.Bold, color = BluePrimary)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
