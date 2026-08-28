package com.smartvendor.ai.repository

import com.smartvendor.ai.model.Store
import kotlinx.coroutines.flow.Flow

class StoreRepositoryImpl : StoreRepository {

    override fun getStoreInfo(): Flow<Store> {
        return LocalStoreManager.storeFlow
    }

    override suspend fun saveStoreInfo(store: Store): Result<Unit> {
        LocalStoreManager.saveStoreInfo(store)
        return Result.success(Unit)
    }
}
