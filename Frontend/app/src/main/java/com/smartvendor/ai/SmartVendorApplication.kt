package com.smartvendor.ai

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartvendor.ai.repository.LocalStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmartVendorApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SmartVendorApplication initializing core services & pre-warming...")
        
        // Initialize 100% Offline-First Local Store Manager
        LocalStoreManager.init(this)

        preWarmServices()
    }

    private fun preWarmServices() {
        applicationScope.launch {
            try {
                // Pre-warm ML Kit Text Recognizer C++ engine
                Log.d(TAG, "Pre-warming ML Kit Text Recognizer C++ engine...")
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val dummyBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                val dummyImage = InputImage.fromBitmap(dummyBitmap, 0)
                recognizer.process(dummyImage).addOnCompleteListener {
                    dummyBitmap.recycle()
                    recognizer.close()
                    Log.d(TAG, "ML Kit Text Recognizer successfully pre-warmed!")
                }
            } catch (e: Exception) {
                Log.w(TAG, "App pre-warm completed with minor note: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "SmartVendorApp"
    }
}
