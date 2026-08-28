package com.smartvendor.ai.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.RedPrimary
import com.smartvendor.ai.ui.theme.WarningYellow
import com.smartvendor.ai.voice.KiranaVoiceOrderParser
import com.smartvendor.ai.voice.ParsedVoiceItem
import com.smartvendor.ai.voice.VoiceBillingManager
import com.smartvendor.ai.voice.VoiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceBillingSheet(
    onDismiss: () -> Unit,
    onAddItemsToBill: (List<ParsedVoiceItem>) -> Unit
) {
    val context = LocalContext.current
    val voiceManager = remember { VoiceBillingManager(context) }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            voiceManager.startListening()
        }
    }

    val voiceState by voiceManager.voiceState.collectAsState()
    val audioRms by voiceManager.audioRms.collectAsState()

    var selectedLanguage by remember { mutableStateOf("mr-IN") }
    var currentTranscript by remember { mutableStateOf("") }
    var parsedItems by remember { mutableStateOf<List<ParsedVoiceItem>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Pulsing Mic Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceState is VoiceState.Listening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Handle incoming voice recognition events
    LaunchedEffect(voiceState) {
        when (val state = voiceState) {
            is VoiceState.Recognized -> {
                currentTranscript = state.text
                errorMessage = null
                if (state.text.isNotBlank()) {
                    val items = KiranaVoiceOrderParser.parseVoiceTranscript(state.text)
                    parsedItems = items

                    // If final results, speak feedback
                    if (state.isFinal && items.isNotEmpty()) {
                        val outOfStockItems = items.filter { it.isOutOfStock }
                        val inStockItems = items.filter { !it.isOutOfStock }

                        val feedbackMessage = when (selectedLanguage) {
                            "mr-IN" -> {
                                if (outOfStockItems.isNotEmpty()) {
                                    val outNames = outOfStockItems.joinToString(", ") { it.matchedProduct?.name ?: it.rawSpokenText }
                                    "$outNames संपले आहेत!"
                                } else {
                                    "${inStockItems.size} वस्तू जोडल्या."
                                }
                            }
                            "hi-IN" -> {
                                if (outOfStockItems.isNotEmpty()) {
                                    val outNames = outOfStockItems.joinToString(", ") { it.matchedProduct?.name ?: it.rawSpokenText }
                                    "$outNames स्टॉक में नहीं है!"
                                } else {
                                    "${inStockItems.size} आइटम जोड़े गए."
                                }
                            }
                            else -> {
                                if (outOfStockItems.isNotEmpty()) {
                                    val outNames = outOfStockItems.joinToString(", ") { it.matchedProduct?.name ?: it.rawSpokenText }
                                    "$outNames is out of stock!"
                                } else {
                                    "Added ${inStockItems.size} items."
                                }
                            }
                        }
                        voiceManager.speakFeedback(feedbackMessage)
                    }
                }
            }
            is VoiceState.Error -> {
                errorMessage = state.message
            }
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.destroy()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = RedPrimary.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Voice Billing Assistant", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            if (voiceManager.useGroqCloud) {
                                Surface(
                                    color = AccentGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Groq Whisper",
                                        color = AccentGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text("Speak in Marathi, Hindi, or English", color = Color.Gray, fontSize = 12.sp)
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Language Selector Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val languages = listOf(
                    Triple("mr-IN", "मराठी", "Marathi"),
                    Triple("hi-IN", "हिंदी", "Hindi"),
                    Triple("en-IN", "English", "English")
                )

                languages.forEach { (code, nativeLabel, _) ->
                    val isSelected = selectedLanguage == code
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedLanguage = code
                            voiceManager.setLanguage(code)
                            if (voiceState is VoiceState.Listening) {
                                voiceManager.startListening()
                            }
                        },
                        label = { Text(nativeLabel, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RedPrimary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Central Pulsing Microphone Button
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (voiceState is VoiceState.Listening) {
                    Box(
                        modifier = Modifier
                            .size((75 * pulseScale).dp)
                            .background(RedPrimary.copy(alpha = 0.2f), CircleShape)
                    )
                }

                IconButton(
                    onClick = {
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (voiceState is VoiceState.Listening) {
                                voiceManager.stopListening()
                            } else {
                                voiceManager.startListening()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            if (voiceState is VoiceState.Listening) RedPrimary else RedPrimary.copy(alpha = 0.85f),
                            CircleShape
                        )
                ) {
                    if (voiceState is VoiceState.Transcribing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (voiceState is VoiceState.Listening) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = "Tap to speak",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Status / Prompt Text
            Text(
                text = when (voiceState) {
                    is VoiceState.Listening -> "Listening... Tap to finish & process (e.g. '२ मॅगी आणि १ अमूल बटर')"
                    is VoiceState.Transcribing -> "Transcribing with Groq Whisper AI (~200ms)..."
                    is VoiceState.Recognized -> "Heard: \"${currentTranscript}\""
                    is VoiceState.Error -> errorMessage ?: "Tap mic to speak"
                    else -> "Tap microphone and speak products with quantity"
                },
                fontSize = 13.sp,
                color = if (voiceState is VoiceState.Error) Color.Red else Color.Gray,
                fontWeight = FontWeight.Medium
            )

            // Parsed Items List
            if (parsedItems.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Detected Items (${parsedItems.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val validItems = parsedItems.filter { !it.isOutOfStock }
                            val totalEst = validItems.sumOf { (it.matchedProduct?.price ?: 0.0) * it.quantity }
                            Text("Est. ₹${totalEst.toInt()}", fontWeight = FontWeight.Bold, color = RedPrimary, fontSize = 14.sp)
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(parsedItems) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (item.isOutOfStock) Color.Red.copy(alpha = 0.08f)
                                            else MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.matchedProduct?.name ?: item.rawSpokenText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        if (item.warningMessage != null) {
                                            Text(
                                                text = item.warningMessage,
                                                color = Color.Red,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Text(
                                                text = "${item.quantity} x ₹${item.matchedProduct?.price?.toInt() ?: 0}  |  In Stock: ${item.availableStock}",
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    if (item.isOutOfStock) {
                                        Surface(
                                            color = Color.Red.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "OUT OF STOCK",
                                                color = Color.Red,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "₹${((item.matchedProduct?.price ?: 0.0) * item.quantity).toInt()}",
                                            fontWeight = FontWeight.Bold,
                                            color = RedPrimary,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons
            val inStockCount = parsedItems.count { !it.isOutOfStock }
            Button(
                onClick = {
                    val validItems = parsedItems.filter { !it.isOutOfStock }
                    if (validItems.isNotEmpty()) {
                        onAddItemsToBill(validItems)
                        onDismiss()
                    }
                },
                enabled = inStockCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (inStockCount > 0) "Add $inStockCount Available Items to Bill" else "Speak Products to Add",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
