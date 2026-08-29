package com.smartvendor.ai.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.smartvendor.ai.repository.LocalStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reliable Network Monitor utilizing Android ConnectivityManager.
 * Detects online/offline transitions in real-time and triggers automatic
 * synchronization when connectivity is restored.
 */
object NetworkMonitor {

    private const val TAG = "NetworkMonitor"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var isInitialized = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wasOffline = !_isOnline.value
            _isOnline.value = true
            Log.d(TAG, "Network available. Online = true (wasOffline=$wasOffline)")
            LocalStoreManager.setOnlineStatus(true)

            if (wasOffline) {
                // Auto-sync pending offline transactions when reconnected
                scope.launch {
                    Log.d(TAG, "Internet restored! Triggering automatic background sync...")
                    LocalStoreManager.syncWithServer()
                }
            }
        }

        override fun onLost(network: Network) {
            val hasOtherNet = checkCurrentConnectivity()
            _isOnline.value = hasOtherNet
            LocalStoreManager.setOnlineStatus(hasOtherNet)
            Log.d(TAG, "Network lost. Online = $hasOtherNet")
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _isOnline.value = hasInternet
            LocalStoreManager.setOnlineStatus(hasInternet)
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val initialOnline = checkCurrentConnectivity()
        _isOnline.value = initialOnline
        LocalStoreManager.setOnlineStatus(initialOnline)

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
            isInitialized = true
            Log.d(TAG, "NetworkMonitor initialized. Initial online status: $initialOnline")
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering network callback", e)
        }
    }

    fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
