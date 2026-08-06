package com.smartvendor.ai.model

import androidx.annotation.Keep

@Keep
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "CASHIER", // CASHIER, ADMIN
    val storeId: String = "DEFAULT_STORE",
    val phone: String = ""
)
