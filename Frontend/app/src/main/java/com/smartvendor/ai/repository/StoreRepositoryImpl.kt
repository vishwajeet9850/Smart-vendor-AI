package com.smartvendor.ai.repository

import com.smartvendor.ai.model.Store
import com.smartvendor.ai.network.ApiClient
import com.smartvendor.ai.network.models.StoreProfileRequest
import com.smartvendor.ai.network.models.StoreProfileResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StoreRepositoryImpl : StoreRepository {

    private val api = ApiClient.apiService

    private fun StoreProfileResponse.toDomain() = Store(
        storeId = userId,
        name = name,
        address = address,
        gst = gst,
        upi = upi,
        phone = phone
    )

    override fun getStoreInfo(): Flow<Store> = flow {
        try {
            val response = api.getStoreProfile()
            if (response.isSuccessful && response.body() != null) {
                emit(response.body()!!.toDomain())
            } else {
                emit(Store())
            }
        } catch (ex: Exception) {
            emit(Store())
        }
    }

    override suspend fun saveStoreInfo(store: Store): Result<Unit> {
        return try {
            val body = StoreProfileRequest(
                name = store.name,
                address = store.address,
                phone = store.phone,
                gst = store.gst,
                upi = store.upi
            )
            val response = api.updateStoreProfile(body)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update store profile: ${response.code()}"))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}
