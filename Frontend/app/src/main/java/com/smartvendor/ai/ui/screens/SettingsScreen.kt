package com.smartvendor.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartvendor.ai.model.Store
import com.smartvendor.ai.repository.AuthRepository
import com.smartvendor.ai.repository.AuthRepositoryImpl
import com.smartvendor.ai.repository.StoreRepository
import com.smartvendor.ai.repository.StoreRepositoryImpl
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.DangerRed
import com.smartvendor.ai.ui.theme.RedPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val authRepository: AuthRepository = remember { AuthRepositoryImpl() }
    val storeRepository: StoreRepository = remember { StoreRepositoryImpl() }

    val currentUser by authRepository.getCurrentUser().collectAsState(initial = null)
    val storeInfo by storeRepository.getStoreInfo().collectAsState(initial = Store())

    var showStoreInfoDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Store Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (saveSuccessMessage != null) {
                Surface(
                    color = AccentGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = saveSuccessMessage!!,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Store Profile Card
            SettingsItemRow(
                icon = Icons.Outlined.Storefront,
                title = "Store Profile",
                subtitle = storeInfo.name.ifBlank { "SmartVendor Kirana Store" },
                onClick = { showStoreInfoDialog = true }
            )

            // About App Card
            SettingsItemRow(
                icon = Icons.Outlined.Info,
                title = "About SmartVendor AI",
                subtitle = "Version 1.0.0 • AI-Powered Kirana POS",
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
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
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
                        val result = storeRepository.saveStoreInfo(updatedStore)
                        showStoreInfoDialog = false
                        saveSuccessMessage = if (result.isSuccess) "Store profile saved successfully!" else "Saved locally (Offline mode)"
                    }
                }
            )
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("About SmartVendor AI", fontWeight = FontWeight.Bold) },
                text = {
                    Text("Smart Kirana retail billing and inventory management application with smart camera detection, barcode scanning, multilingual voice billing in Marathi & Hindi with Groq Whisper AI, and instant digital receipts.")
                },
                confirmButton = {
                    Button(
                        onClick = { showAboutDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
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
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                        .size(42.dp)
                        .background(RedPrimary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
