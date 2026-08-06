package com.smartvendor.ai.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PersistentCacheSettings

object FirebaseManager {

    private const val TAG = "FirebaseManager"

    fun initializeFirestoreSettings() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .build()
                )
                .build()
            firestore.firestoreSettings = settings
            Log.d(TAG, "Firestore offline persistence enabled successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Firestore settings initialization note: ${e.message}")
        }
    }
}
