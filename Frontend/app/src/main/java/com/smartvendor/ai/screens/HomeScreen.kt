package com.smartvendor.ai.screens
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartvendor.ai.components.DashboardCard

@Composable
fun HomeScreen(
    onScanClick: () -> Unit
){

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "SmartVendor AI",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "AI Powered Billing & Inventory",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(30.dp))

        DashboardCard(
            title = "Scan Products",
            icon = Icons.Default.CameraAlt,
            onClick = onScanClick
        )

        DashboardCard(
            title = "Generate Bill",
            icon = Icons.Default.Receipt
        )

        DashboardCard(
            title = "Inventory",
            icon = Icons.Default.Inventory
        )

        DashboardCard(
            title = "Sales Reports",
            icon = Icons.Default.BarChart
        )

        DashboardCard(
            title = "Settings",
            icon = Icons.Default.Settings
        )
    }

}