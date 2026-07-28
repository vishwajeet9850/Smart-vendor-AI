package com.smartvendor.ai.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartvendor.ai.camera.CameraPreview

@Composable
fun ScanScreen(
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Scan Products",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        CameraPreview(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

    }

}