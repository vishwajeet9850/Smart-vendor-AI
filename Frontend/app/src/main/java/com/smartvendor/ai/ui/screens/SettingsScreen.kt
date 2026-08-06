package com.smartvendor.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartvendor.ai.model.Store
import com.smartvendor.ai.repository.AuthRepository
import com.smartvendor.ai.repository.AuthRepositoryImpl
import com.smartvendor.ai.repository.StoreRepository
import com.smartvendor.ai.repository.StoreRepositoryImpl
import com.smartvendor.ai.ui.theme.BluePrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authRepository: AuthRepository = remember { AuthRepositoryImpl() },
    onNavigateBack: () -> Unit,
    onLogoutSuccess: () -> Unit = onNavigateBack
) {
    val storeRepository: StoreRepository = remember { StoreRepositoryImpl() }
    val storeInfo by storeRepository.getStoreInfo().collectAsState(initial = Store())
    val currentUser by authRepository.getCurrentUser().collectAsState(initial = null)

    val coroutineScope = rememberCoroutineScope()
    var isDarkMode by remember { mutableStateOf(false) }
    var showStoreInfoDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Dynamic Store Name (uses registered User Name if Store Name is not set)
    val displayStoreName = storeInfo.name.ifBlank { currentUser?.name ?: "My Store" }
    val displayAddress = storeInfo.address.ifBlank { "Tap to set store address" }
    val displayGst = if (storeInfo.gst.isNotBlank()) "GST: ${storeInfo.gst}" else "GST: Not Specified"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Store Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Dynamic Store Profile Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showStoreInfoDialog = true },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(BluePrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Store, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = displayStoreName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = displayAddress,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (storeInfo.address.isNotBlank()) BluePrimary else Color.Gray
                            )
                        )
                        Text(
                            text = "$displayGst  |  Role: Admin",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                }
            }

            Text("Preferences", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

            SettingsItemRow(
                icon = Icons.Outlined.Storefront,
                title = "Edit Store Profile & Address",
                subtitle = "Set your store address, phone, GST, and UPI ID",
                onClick = { showStoreInfoDialog = true }
            )

            SettingsItemRow(
                icon = Icons.Outlined.DarkMode,
                title = "Dark Mode",
                subtitle = "Toggle dark visual theme",
                trailing = {
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { isDarkMode = it }
                    )
                },
                onClick = { isDarkMode = !isDarkMode }
            )

            SettingsItemRow(
                icon = Icons.Outlined.Sync,
                title = "Cloud Synchronization",
                subtitle = "FastAPI backend & SQLite active",
                onClick = { }
            )

            SettingsItemRow(
                icon = Icons.Outlined.Info,
                title = "About SmartVendor AI",
                subtitle = "Version 1.0.0 (FastAPI & YOLO TFLite Powered)",
                onClick = { showAboutDialog = true }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    coroutineScope.launch {
                        authRepository.logout()
                        onLogoutSuccess()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Editable Store Info Dialog
        if (showStoreInfoDialog) {
            EditStoreInfoDialog(
                currentStore = storeInfo,
                defaultName = currentUser?.name ?: "",
                onDismiss = { showStoreInfoDialog = false },
                onSave = { updatedStore ->
                    coroutineScope.launch {
                        storeRepository.saveStoreInfo(updatedStore)
                        showStoreInfoDialog = false
                    }
                }
            )
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("About SmartVendor AI") },
                text = {
                    Text("Production-quality AI Retail Billing & Inventory App built with Jetpack Compose, CameraX, TensorFlow Lite (YOLO object detection), ML Kit Barcode Scanner, FastAPI, SQLite, and Firebase Auth.")
                },
                confirmButton = {
                    Button(onClick = { showAboutDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun EditStoreInfoDialog(
    currentStore: Store,
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (Store) -> Unit
) {
    var name by remember { mutableStateOf(currentStore.name.ifBlank { defaultName }) }
    var address by remember { mutableStateOf(currentStore.address) }
    var gst by remember { mutableStateOf(currentStore.gst) }
    var phone by remember { mutableStateOf(currentStore.phone) }
    var upi by remember { mutableStateOf(currentStore.upi) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Store Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Store Name") },
                    placeholder = { Text("Enter your store name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Store Address / Location") },
                    placeholder = { Text("Enter full store address") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("e.g. +91 9876543210") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gst,
                    onValueChange = { gst = it },
                    label = { Text("GSTIN Number (Optional)") },
                    placeholder = { Text("e.g. 27AAAAA0000A1Z5") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = upi,
                    onValueChange = { upi = it },
                    label = { Text("UPI Payment ID (Optional)") },
                    placeholder = { Text("e.g. storename@upi") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        currentStore.copy(
                            name = name,
                            address = address,
                            phone = phone,
                            gst = gst,
                            upi = upi
                        )
                    )
                }
            ) {
                Text("Save Store Profile")
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
fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(BluePrimary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = BluePrimary)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                }
            }
            trailing?.invoke()
        }
    }
}
