package com.smartvendor.ai.model

import androidx.annotation.Keep

@Keep
data class Store(
    val storeId: String = "",
    val name: String = "",
    val address: String = "",
    val gst: String = "",
    val upi: String = "",
    val phone: String = "",
    val email: String = ""
)
