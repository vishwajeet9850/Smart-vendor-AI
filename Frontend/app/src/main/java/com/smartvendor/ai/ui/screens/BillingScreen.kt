package com.smartvendor.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartvendor.ai.model.BillItem
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    billId: String,
    viewModel: BillingViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onCheckoutSuccess: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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

            if (bill != null && bill.items.isNotEmpty()) {
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
                            viewModel.performCheckout { successBillId ->
                                onCheckoutSuccess(successBillId)
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
            // Payment Method Selector
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

            Divider()

            // Calculations breakdown
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
