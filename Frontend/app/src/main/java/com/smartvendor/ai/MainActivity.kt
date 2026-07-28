package com.smartvendor.ai

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.smartvendor.ai.screens.HomeScreen
import com.smartvendor.ai.screens.ScanScreen
import com.smartvendor.ai.ui.theme.SmartVendorAITheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {

            SmartVendorAITheme {

                var currentScreen by remember {
                    mutableStateOf("home")
                }

                when (currentScreen) {

                    "home" -> HomeScreen(
                        onScanClick = {
                            currentScreen = "scan"
                        }
                    )

                    "scan" -> ScanScreen(
                        onBack = {
                            currentScreen = "home"
                        }
                    )
                }
            }
        }
    }
}