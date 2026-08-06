package com.smartvendor.ai.model

import androidx.annotation.Keep

@Keep
data class Bill(
    val billId: String = "",
    val cashierId: String = "",
    val storeId: String = "",
    val items: List<BillItem> = emptyList(),
    val subtotal: Double = 0.0,
    val gst: Double = 0.0,
    val discount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paymentMethod: String = "CASH", // CASH, UPI, CARD, OTHER
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = BILL_STATUS_OPEN // OPEN, COMPLETED, CANCELLED
) {
    companion object {
        const val BILL_STATUS_OPEN = "OPEN"
        const val BILL_STATUS_COMPLETED = "COMPLETED"
        const val BILL_STATUS_CANCELLED = "CANCELLED"
    }
}
