package com.smartvendor.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.repository.ProductRepository
import com.smartvendor.ai.repository.ProductRepositoryImpl
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BluePrimary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    productRepository: ProductRepository = remember { ProductRepositoryImpl() },
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var product by remember { mutableStateOf<Product?>(null) }
    var showRestockDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = productId) {
        productRepository.getProductsStream().collect { list ->
            product = list.find { it.id == productId }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Details", fontWeight = FontWeight.Bold) },
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
            val p = product
            if (p != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = p.name,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Category: ${p.category}",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                            )
                            Divider()
                            DetailRow("Price", "₹${"%.2f".format(p.price)}")
                            DetailRow("GST Tax Rate", "${p.gst}%")
                            DetailRow("Current Stock", "${p.stock} units")
                            DetailRow("Barcode", p.barcode.ifBlank { "N/A" })
                            DetailRow("Item Code / Class", "${p.classId}")
                            DetailRow("Created Date", formatDate(p.createdAt))
                            DetailRow("Last Updated", formatDate(p.updatedAt))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showRestockDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restock")
                        }

                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete")
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            if (showRestockDialog) {
                var restockQty by remember { mutableStateOf("20") }
                AlertDialog(
                    onDismissRequest = { showRestockDialog = false },
                    title = { Text("Restock Product") },
                    text = {
                        OutlinedTextField(
                            value = restockQty,
                            onValueChange = { restockQty = it },
                            label = { Text("Additional Stock Quantity") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val qty = restockQty.toIntOrNull() ?: 0
                                if (qty > 0 && product != null) {
                                    coroutineScope.launch {
                                        productRepository.updateStock(product!!.id, -qty)
                                        showRestockDialog = false
                                    }
                                }
                            }
                        ) {
                            Text("Confirm Restock")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestockDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Product?") },
                    text = { Text("Are you sure you want to permanently delete this product from inventory?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (product != null) {
                                    coroutineScope.launch {
                                        productRepository.deleteProduct(product!!.id)
                                        showDeleteDialog = false
                                        onNavigateBack()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Delete Permanently")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))
}
