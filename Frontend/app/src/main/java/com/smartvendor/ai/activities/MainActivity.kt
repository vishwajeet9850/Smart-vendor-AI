package com.smartvendor.ai.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.smartvendor.ai.navigation.Screen
import com.smartvendor.ai.navigation.SmartVendorNavHost
import com.smartvendor.ai.ui.theme.SmartVendorAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Persistent Session Check: If user is logged in, jump straight to Dashboard
        val currentUser = FirebaseAuth.getInstance().currentUser
        val initialDestination = if (currentUser != null) {
            Screen.Dashboard.route
        } else {
            Screen.Login.route
        }

        setContent {
            SmartVendorAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartVendorNavHost(startDestination = initialDestination)
                }
            }
        }
    }
}
