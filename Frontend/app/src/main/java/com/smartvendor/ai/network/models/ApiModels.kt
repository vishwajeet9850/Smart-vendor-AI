package com.smartvendor.ai.network.models

import com.google.gson.annotations.SerializedName

// ─── Store Profile ─────────────────────────────────────────────────────────────

data class StoreProfileRequest(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val gst: String = "",
    val upi: String = ""
)

data class StoreProfileResponse(
    @SerializedName("user_id") val userId: String,
    val name: String,
    val address: String,
    val phone: String,
    val gst: String,
    val upi: String
)

// ─── Product ───────────────────────────────────────────────────────────────────

data class ProductRequest(
    val name: String,
    val barcode: String? = null,
    val category: String = "General",
    val price: Double,
    val stock: Int = 0,
    @SerializedName("low_stock_threshold") val lowStockThreshold: Int = 5,
    @SerializedName("image_url") val imageUrl: String? = null
)

data class ProductUpdateRequest(
    val name: String? = null,
    val barcode: String? = null,
    val category: String? = null,
    val price: Double? = null,
    val stock: Int? = null,
    @SerializedName("low_stock_threshold") val lowStockThreshold: Int? = null,
    @SerializedName("image_url") val imageUrl: String? = null
)

data class StockUpdateRequest(
    val stock: Int
)

data class ProductResponse(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val name: String,
    val barcode: String?,
    val category: String,
    val price: Double,
    val stock: Int,
    @SerializedName("low_stock_threshold") val lowStockThreshold: Int,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

// ─── Bill ──────────────────────────────────────────────────────────────────────

data class BillItemRequest(
    @SerializedName("product_id") val productId: String? = null,
    @SerializedName("product_name") val productName: String,
    val quantity: Int,
    @SerializedName("unit_price") val unitPrice: Double,
    @SerializedName("total_price") val totalPrice: Double
)

data class BillRequest(
    val items: List<BillItemRequest>,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("tax_amount") val taxAmount: Double = 0.0,
    @SerializedName("payment_mode") val paymentMode: String = "cash"
)

data class BillItemResponse(
    val id: String,
    @SerializedName("product_id") val productId: String?,
    @SerializedName("product_name") val productName: String,
    val quantity: Int,
    @SerializedName("unit_price") val unitPrice: Double,
    @SerializedName("total_price") val totalPrice: Double
)

data class BillResponse(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("tax_amount") val taxAmount: Double,
    @SerializedName("payment_mode") val paymentMode: String,
    @SerializedName("created_at") val createdAt: String,
    val items: List<BillItemResponse> = emptyList()
)

// ─── Analytics ─────────────────────────────────────────────────────────────────

data class DailyRevenue(
    val date: String,
    val revenue: Double,
    @SerializedName("bill_count") val billCount: Int
)

data class TopProduct(
    @SerializedName("product_name") val productName: String,
    @SerializedName("quantity_sold") val quantitySold: Int,
    val revenue: Double
)

data class StockRecommendationItemResponse(
    @SerializedName("product_id") val productId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("current_stock") val currentStock: Int,
    @SerializedName("recommended_reorder") val recommendedReorder: Int,
    val category: String,
    @SerializedName("peak_window") val peakWindow: String = "General Demand",
    @SerializedName("sales_velocity") val salesVelocity: String = "Moderate",
    val reasoning: String = "",
    @SerializedName("urgency_level") val urgencyLevel: String = "MEDIUM"
)

data class MarketTrendInsightResponse(
    val title: String,
    val description: String,
    @SerializedName("recommended_product") val recommendedProduct: String,
    @SerializedName("action_type") val actionType: String = "RESTOCK",
    @SerializedName("badge_label") val badgeLabel: String = "🌐 Market Trend"
)

data class MasterCatalogResponse(
    val id: String,
    val name: String,
    val category: String,
    @SerializedName("suggested_price") val suggestedPrice: Double,
    val barcode: String? = null
)

data class AnalyticsSummaryResponse(
    @SerializedName("total_revenue") val totalRevenue: Double,
    @SerializedName("total_bills") val totalBills: Int,
    @SerializedName("total_products") val totalProducts: Int,
    @SerializedName("low_stock_count") val lowStockCount: Int,
    @SerializedName("top_products") val topProducts: List<TopProduct>,
    @SerializedName("daily_revenue") val dailyRevenue: List<DailyRevenue>,
    @SerializedName("stock_recommendations") val stockRecommendations: List<StockRecommendationItemResponse> = emptyList(),
    @SerializedName("market_trends") val marketTrends: List<MarketTrendInsightResponse> = emptyList()
)
