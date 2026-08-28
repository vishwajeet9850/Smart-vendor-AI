package com.smartvendor.ai.repository

import com.smartvendor.ai.model.Product
import com.smartvendor.ai.network.models.MasterCatalogResponse
import kotlinx.coroutines.flow.Flow

class ProductRepositoryImpl : ProductRepository {

    override suspend fun getProductByClassId(classId: Int): Result<Product?> {
        val p = LocalStoreManager.getProductByClassId(classId)
        return Result.success(p)
    }

    override suspend fun getProductByBarcode(barcode: String): Result<Product?> {
        val p = LocalStoreManager.getProductByBarcode(barcode)
        return Result.success(p)
    }

    override fun getProductsStream(): Flow<List<Product>> {
        return LocalStoreManager.productsFlow
    }

    override suspend fun updateStock(productId: String, targetStock: Int): Result<Unit> {
        LocalStoreManager.updateStock(productId, targetStock)
        return Result.success(Unit)
    }

    override suspend fun updateProduct(product: Product): Result<Unit> {
        LocalStoreManager.updateProduct(product)
        return Result.success(Unit)
    }

    override suspend fun addProduct(product: Product): Result<String> {
        val id = LocalStoreManager.addProduct(product)
        return Result.success(id)
    }

    override suspend fun deleteProduct(productId: String): Result<Unit> {
        LocalStoreManager.deleteProduct(productId)
        return Result.success(Unit)
    }

    override suspend fun searchMasterCatalog(query: String): Result<List<MasterCatalogResponse>> {
        val results = LocalStoreManager.searchMasterCatalog(query, limit = 30)
        return Result.success(results)
    }
}
