package com.smartvendor.ai.repository

import com.smartvendor.ai.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    suspend fun getProductByClassId(classId: Int): Result<Product?>
    suspend fun getProductByBarcode(barcode: String): Result<Product?>
    fun getProductsStream(): Flow<List<Product>>
    suspend fun updateStock(productId: String, targetStock: Int): Result<Unit>
    suspend fun updateProduct(product: Product): Result<Unit>
    suspend fun addProduct(product: Product): Result<String>
    suspend fun deleteProduct(productId: String): Result<Unit>
}
