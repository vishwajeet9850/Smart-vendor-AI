package com.smartvendor.ai.model

import androidx.annotation.Keep

@Keep
data class Product(
    val id: String = "",
    val classId: Int = -1,
    val barcode: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val gst: Double = 0.0,
    val category: String = "",
    val stock: Int = 0,
    val lowStockThreshold: Int = 5,
    val imageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
