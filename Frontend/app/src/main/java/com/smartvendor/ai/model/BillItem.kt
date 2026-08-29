package com.smartvendor.ai.model

import androidx.annotation.Keep

@Keep
data class BillItem(
    val productId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val gst: Double = 0.0,
    val lineTotal: Double = (quantity * unitPrice) + (((quantity * unitPrice) * gst) / 100.0),
    val condition: String = CONDITION_GOOD // GOOD, DAMAGED
) {
    companion object {
        const val CONDITION_GOOD = "GOOD"
        const val CONDITION_DAMAGED = "DAMAGED"
    }
}

