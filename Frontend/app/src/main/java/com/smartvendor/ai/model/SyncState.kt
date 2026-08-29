package com.smartvendor.ai.model

/**
 * Represents the current connectivity and synchronization state.
 */
data class SyncState(
    val isOnline: Boolean = true,
    val pendingBillsCount: Int = 0,
    val pendingStockCount: Int = 0,
    val pendingProductCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val lastSyncStatus: String = "Ready"
) {
    val totalPendingCount: Int
        get() = pendingBillsCount + pendingStockCount + pendingProductCount

    val isAllSynced: Boolean
        get() = totalPendingCount == 0 && !isSyncing
}
