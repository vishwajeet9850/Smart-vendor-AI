package com.smartvendor.ai.repository

import com.smartvendor.ai.model.Store
import kotlinx.coroutines.flow.Flow

interface StoreRepository {
    fun getStoreInfo(): Flow<Store>
    suspend fun saveStoreInfo(store: Store): Result<Unit>
}
