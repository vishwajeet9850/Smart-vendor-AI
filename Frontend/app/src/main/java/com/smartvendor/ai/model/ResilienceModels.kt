package com.smartvendor.ai.model

import com.google.gson.annotations.SerializedName

data class JournalTransaction(
    @SerializedName("id") val id: String = "",
    @SerializedName("transaction_id") val transactionId: String = "",
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("type") val type: String = "SALE", // SALE, RETURN, STOCK_ADJUST
    @SerializedName("bill_id") val billId: String? = null,
    @SerializedName("product_id") val productId: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("quantity") val quantity: Int = 0,
    @SerializedName("unit_price") val unitPrice: Double = 0.0,
    @SerializedName("total_amount") val totalAmount: Double = 0.0,
    @SerializedName("previous_stock") val previousStock: Int? = null,
    @SerializedName("new_stock") val newStock: Int? = null,
    @SerializedName("return_condition") val returnCondition: String = "GOOD",
    @SerializedName("status") val status: String = "APPLIED", // PENDING, APPLIED, RECOVERED, FAILED
    @SerializedName("payload_json") val payloadJson: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("created_at") val createdAt: String = ""
)

data class SystemStatus(
    @SerializedName("system_status") val systemStatus: String = "HEALTHY", // HEALTHY, BLACKOUT_ACTIVE, RECOVERING, RECOVERED
    @SerializedName("is_blackout_active") val isBlackoutActive: Boolean = false,
    @SerializedName("blackout_started_at") val blackoutStartedAt: String? = null,
    @SerializedName("simulated_failure_reason") val simulatedFailureReason: String? = null,
    @SerializedName("primary_database_status") val primaryDatabaseStatus: String = "ONLINE", // ONLINE, CORRUPTED_UNAVAILABLE
    @SerializedName("last_verified_checkpoint") val lastVerifiedCheckpoint: String? = null,
    @SerializedName("last_checkpoint_id") val lastCheckpointId: String? = null,
    @SerializedName("total_journaled_transactions") val totalJournaledTransactions: Int = 0,
    @SerializedName("pending_recovery_count") val pendingRecoveryCount: Int = 0,
    @SerializedName("recovered_transactions_count") val recoveredTransactionsCount: Int = 0,
    @SerializedName("latest_report") val latestReport: RecoveryReport? = null
)

data class RecoveryReport(
    @SerializedName("system_status") val systemStatus: String = "HEALTHY",
    @SerializedName("last_checkpoint_id") val lastCheckpointId: String? = null,
    @SerializedName("last_checkpoint_timestamp") val lastCheckpointTimestamp: String? = null,
    @SerializedName("transactions_discovered") val transactionsDiscovered: Int = 0,
    @SerializedName("successfully_recovered") val successfullyRecovered: Int = 0,
    @SerializedName("already_present") val alreadyPresent: Int = 0,
    @SerializedName("unrecoverable") val unrecoverable: Int = 0,
    @SerializedName("unrecoverable_details") val unrecoverableDetails: List<String> = emptyList(),
    @SerializedName("inventory_summary") val inventorySummary: List<InventoryItemSummary> = emptyList(),
    @SerializedName("bills_count") val billsCount: Int = 0,
    @SerializedName("report_generated_at") val reportGeneratedAt: String = ""
)

data class InventoryItemSummary(
    @SerializedName("product_id") val productId: String = "",
    @SerializedName("product_name") val productName: String = "",
    @SerializedName("current_stock") val currentStock: Int = 0,
    @SerializedName("price") val price: Double = 0.0
)

data class SystemCheckpoint(
    @SerializedName("id") val id: String = "",
    @SerializedName("checkpoint_id") val checkpointId: String = "",
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("checkpoint_type") val checkpointType: String = "AUTO",
    @SerializedName("products_count") val productsCount: Int = 0,
    @SerializedName("bills_count") val billsCount: Int = 0,
    @SerializedName("last_transaction_id") val lastTransactionId: String? = null,
    @SerializedName("created_at") val createdAt: String = ""
)

data class CIEAlertModel(
    @SerializedName("id") val id: String = "",
    @SerializedName("incident_id") val incidentId: String = "cross_vendor_return_demo",
    @SerializedName("product_name") val productName: String = "",
    @SerializedName("affected_vendors_count") val affectedVendorsCount: Int = 0,
    @SerializedName("total_returns_count") val totalReturnsCount: Int = 0,
    @SerializedName("time_window_minutes") val timeWindowMinutes: Int = 25,
    @SerializedName("alert_title") val alertTitle: String = "",
    @SerializedName("alert_message") val alertMessage: String = "",
    @SerializedName("status") val status: String = "ACTIVE",
    @SerializedName("created_at") val createdAt: String = ""
)

data class CIEStatusResponse(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("has_active_incident") val hasActiveIncident: Boolean = false,
    @SerializedName("active_alert") val activeAlert: CIEAlertModel? = null,
    @SerializedName("alert") val alert: CIEAlertModel? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("cleaned_bills_count") val cleanedBillsCount: Int = 0
)
