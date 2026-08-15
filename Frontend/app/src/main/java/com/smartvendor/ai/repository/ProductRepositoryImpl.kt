package com.smartvendor.ai.repository

import com.smartvendor.ai.model.Product
import com.smartvendor.ai.network.ApiClient
import com.smartvendor.ai.network.models.ProductRequest
import com.smartvendor.ai.network.models.ProductResponse
import com.smartvendor.ai.network.models.ProductUpdateRequest
import com.smartvendor.ai.network.models.StockUpdateRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ProductRepositoryImpl : ProductRepository {

    private val api = ApiClient.apiService

    // ─── Map API response → domain model ──────────────────────────────────────

    private fun ProductResponse.toDomain() = Product(
        id = id,
        name = name,
        barcode = barcode ?: "",
        category = category,
        price = price,
        stock = stock,
        lowStockThreshold = lowStockThreshold,
        imageUrl = imageUrl ?: ""
    )

    // ─── Interface implementations ─────────────────────────────────────────────

    override suspend fun getProductByClassId(classId: Int): Result<Product?> {
        return try {
            val response = api.getProducts()
            if (response.isSuccessful) {
                val product = response.body()
                    ?.getOrNull(classId)
                    ?.toDomain()
                Result.success(product)
            } else {
                Result.success(null)
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override suspend fun getProductByBarcode(barcode: String): Result<Product?> {
        return try {
            val response = api.getProductByBarcode(barcode)
            if (response.isSuccessful) {
                Result.success(response.body()?.toDomain())
            } else if (response.code() == 404) {
                Result.success(null)
            } else {
                Result.failure(Exception("Server error: ${response.code()}"))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override fun getProductsStream(): Flow<List<Product>> = flow {
        if (isLoaded) {
            emit(cachedProducts)
        }
        try {
            val response = api.getProducts()
            if (response.isSuccessful) {
                val list = response.body()?.map { it.toDomain() } ?: emptyList()
                cachedProducts = list
                isLoaded = true
                emit(list)
            } else if (!isLoaded) {
                emit(emptyList())
            }
        } catch (ex: Exception) {
            if (!isLoaded) {
                emit(emptyList())
            }
        }
    }

    companion object {
        @Volatile var cachedProducts: List<Product> = emptyList()
        @Volatile var isLoaded: Boolean = false
    }

    override suspend fun updateStock(productId: String, targetStock: Int): Result<Unit> {
        return try {
            val response = api.updateStock(productId, StockUpdateRequest(targetStock.coerceAtLeast(0)))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Stock update failed: ${response.code()}"))
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override suspend fun updateProduct(product: Product): Result<Unit> {
        return try {
            val body = ProductUpdateRequest(
                name = product.name,
                barcode = product.barcode.ifBlank { null },
                category = product.category,
                price = product.price,
                stock = product.stock,
                lowStockThreshold = product.lowStockThreshold,
                imageUrl = product.imageUrl.ifBlank { null }
            )
            val response = api.updateProduct(product.id, body)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Update failed: ${response.code()}"))
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override suspend fun addProduct(product: Product): Result<String> {
        return try {
            if (product.id.isBlank()) {
                val body = ProductRequest(
                    name = product.name,
                    barcode = product.barcode.ifBlank { null },
                    category = product.category,
                    price = product.price,
                    stock = product.stock,
                    lowStockThreshold = product.lowStockThreshold,
                    imageUrl = product.imageUrl.ifBlank { null }
                )
                val response = api.createProduct(body)
                if (response.isSuccessful) {
                    Result.success(response.body()?.id ?: "")
                } else {
                    Result.failure(Exception("Create failed: ${response.code()}"))
                }
            } else {
                val body = ProductUpdateRequest(
                    name = product.name,
                    barcode = product.barcode.ifBlank { null },
                    category = product.category,
                    price = product.price,
                    stock = product.stock,
                    lowStockThreshold = product.lowStockThreshold,
                    imageUrl = product.imageUrl.ifBlank { null }
                )
                val response = api.updateProduct(product.id, body)
                if (response.isSuccessful) {
                    Result.success(product.id)
                } else {
                    Result.failure(Exception("Update failed: ${response.code()}"))
                }
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override suspend fun deleteProduct(productId: String): Result<Unit> {
        return try {
            val response = api.deleteProduct(productId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Delete failed: ${response.code()}"))
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override suspend fun searchMasterCatalog(query: String): Result<List<com.smartvendor.ai.network.models.MasterCatalogResponse>> {
        return try {
            val response = api.searchMasterCatalog(search = query, limit = 20)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.success(emptyList())
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}
