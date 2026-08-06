package com.smartvendor.ai

import android.app.Application
import android.util.Log

class SmartVendorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SmartVendorApplication initializing core services...")
        // Services, Firebase, Local cache preparation initialized here
    }

    companion object {
        private const val TAG = "SmartVendorApp"
    }
}
