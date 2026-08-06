package com.smartvendor.ai.network

import com.smartvendor.ai.network.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ─── Store Profile ─────────────────────────────────────────────────────────

    @GET("store")
    suspend fun getStoreProfile(): Response<StoreProfileResponse>

    @PUT("store")
    suspend fun updateStoreProfile(@Body body: StoreProfileRequest): Response<StoreProfileResponse>

    // ─── Products ──────────────────────────────────────────────────────────────

    @GET("products")
    suspend fun getProducts(
        @Query("category") category: String? = null,
        @Query("search") search: String? = null
    ): Response<List<ProductResponse>>

    @POST("products")
    suspend fun createProduct(@Body body: ProductRequest): Response<ProductResponse>

    @GET("products/barcode/{barcode}")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): Response<ProductResponse>

    @GET("products/{id}")
    suspend fun getProduct(@Path("id") id: String): Response<ProductResponse>

    @PUT("products/{id}")
    suspend fun updateProduct(
        @Path("id") id: String,
        @Body body: ProductUpdateRequest
    ): Response<ProductResponse>

    @PUT("products/{id}/stock")
    suspend fun updateStock(
        @Path("id") id: String,
        @Body body: StockUpdateRequest
    ): Response<ProductResponse>

    @DELETE("products/{id}")
    suspend fun deleteProduct(@Path("id") id: String): Response<Unit>

    // ─── Bills ─────────────────────────────────────────────────────────────────

    @POST("bills")
    suspend fun createBill(@Body body: BillRequest): Response<BillResponse>

    @GET("bills")
    suspend fun getBills(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<BillResponse>>

    @GET("bills/{id}")
    suspend fun getBill(@Path("id") id: String): Response<BillResponse>

    // ─── Analytics ─────────────────────────────────────────────────────────────

    @GET("analytics/summary")
    suspend fun getAnalyticsSummary(
        @Query("days") days: Int = 30,
        @Query("range_type") rangeType: String? = null
    ): Response<AnalyticsSummaryResponse>
}
