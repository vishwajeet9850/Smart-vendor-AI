package com.smartvendor.ai.network

import com.smartvendor.ai.network.models.*
import retrofit2.Response
import retrofit2.http.*
import okhttp3.MultipartBody
import okhttp3.RequestBody

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

    // ─── YOLO Detection ────────────────────────────────────────────────────────

    /** Send a base64 JPEG camera frame and get back YOLO product detections. */
    @POST("detect/base64")
    suspend fun detectFromBase64(@Body body: YoloDetectRequest): Response<YoloDetectResponse>

    /** Get the list of product classes the model was trained on. */
    @GET("detect/classes")
    suspend fun getYoloClasses(): Response<Map<String, Any>>

    // ─── Master Catalog ────────────────────────────────────────────────────────

    @GET("catalog")
    suspend fun searchMasterCatalog(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<List<MasterCatalogResponse>>

    // ─── Stock Recommendations & Market Intelligence ───────────────

    @POST("api/stock/recommend")
    suspend fun getStockRecommendation(@Body body: StockRecommendationRequest): Response<StockRecommendationResponse>

    @GET("api/stock/recommendations")
    suspend fun getBulkStockRecommendations(
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("status_filter") statusFilter: String? = null
    ): Response<BulkStockRecommendationResponse>

    @GET("api/stock/market-trends")
    suspend fun getMarketTrends(): Response<List<MarketTrendInsight>>

    // ─── Voice Transcription (Groq Whisper) ──────────────────────────

    @Multipart
    @POST("api/voice/transcribe")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part("language") language: RequestBody? = null,
        @Header("X-Groq-Api-Key") groqApiKey: String? = null
    ): Response<VoiceTranscribeResponse>
}