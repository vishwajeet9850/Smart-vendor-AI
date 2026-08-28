package com.smartvendor.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartvendor.ai.model.Store
import com.smartvendor.ai.repository.StoreRepositoryImpl
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.BluePrimary
import com.smartvendor.ai.utils.QrCodeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutSuccessScreen(
    billId: String,
    onNavigateHome: () -> Unit
) {
    val storeRepository = remember { StoreRepositoryImpl() }
    val storeInfo by storeRepository.getStoreInfo().collectAsState(initial = Store())

    val upiUri = remember(billId, storeInfo) {
        QrCodeUtils.generateUpiPayUri(
            upiId = storeInfo.upi.ifBlank { "smartvendor@upi" },
            storeName = storeInfo.name.ifBlank { "SmartVendor Store" },
            amount = 0.0,
            billId = billId
        )
    }
    val qrBitmap = remember(upiUri) {
        QrCodeUtils.generateQrCodeBitmap(upiUri, sizePx = 450)
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(AccentGreen.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Success",
                        tint = AccentGreen,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Text(
                    text = "Bill Generated Successfully!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Invoice Reference",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                        Text(
                            text = billId,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary
                            )
                        )
                    }
                }

                if (qrBitmap != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, tint = BluePrimary)
                                Text(
                                    text = "Scan & Pay via UPI 📲",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "UPI Payment QR Code",
                                modifier = Modifier
                                    .size(160.dp)
                                    .padding(4.dp)
                            )

                            Text(
                                text = "Payee: ${storeInfo.upi.ifBlank { "smartvendor@upi" }}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onNavigateHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Return to Dashboard 🏠", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
