package com.smartvendor.ai.network.models

import com.google.gson.annotations.SerializedName

data class StockRecommendationRequest(
    @SerializedName("product_id") val productId: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("forecast_days") val forecastDays: Int = 7
)

data class MarketDemandInfo(
    @SerializedName("market_insight_available") val marketInsightAvailable: Boolean = true,
    @SerializedName("demand_level") val demandLevel: String = "NORMAL",
    @SerializedName("comparison_percentage") val comparisonPercentage: Double = 100.0,
    @SerializedName("market_average_sales") val marketAverageSales: Double = 0.0,
    @SerializedName("participating_vendors") val participatingVendors: Int = 0,
    @SerializedName("insight_text") val insightText: String? = null
)

data class StockRecommendationResponse(
    @SerializedName("product_id") val productId: String = "",
    @SerializedName("product_name") val productName: String = "",
    @SerializedName("category") val category: String = "General",
    @SerializedName("current_stock") val currentStock: Int = 0,
    @SerializedName("predicted_daily_demand") val predictedDailyDemand: Double = 0.0,
    @SerializedName("predicted_demand") val predictedDemand: Int = 0,
    @SerializedName("safety_stock") val safetyStock: Int = 0,
    @SerializedName("target_stock") val targetStock: Int = 0,
    @SerializedName("recommended_purchase") val recommendedPurchase: Int = 0,
    @SerializedName("status") val status: String = "STOCK_OK", // RESTOCK, LOW_STOCK, STOCK_OK, OVERSTOCK
    @SerializedName("trend") val trend: String = "STABLE", // INCREASING, STABLE, DECREASING
    @SerializedName("seasonal_profile") val seasonalProfile: String = "STABLE",
    @SerializedName("seasonal_factor") val seasonalFactor: Double = 1.0,
    @SerializedName("supplier_moq") val supplierMoq: Int = 1,
    @SerializedName("unit") val unit: String = "pcs",
    @SerializedName("market") val market: MarketDemandInfo = MarketDemandInfo(),
    @SerializedName("reason") val reason: String = "",
    @SerializedName("recommendation_type") val recommendationType: String = "URGENT_RESTOCK",
    @SerializedName("recommendation_title") val recommendationTitle: String = "🚨 Urgent Restock",
    @SerializedName("action_type") val actionType: String = "RESTOCK",
    @SerializedName("action_label") val actionLabel: String = "Order Stock",
    @SerializedName("simple_reason") val simpleReason: String = ""
)


data class BulkStockRecommendationResponse(
    @SerializedName("recommendations") val recommendations: List<StockRecommendationResponse> = emptyList(),
    @SerializedName("total_products") val totalProducts: Int = 0,
    @SerializedName("restock_count") val restockCount: Int = 0,
    @SerializedName("low_stock_count") val lowStockCount: Int = 0,
    @SerializedName("overstock_count") val overstockCount: Int = 0,
    @SerializedName("optimal_count") val optimalCount: Int = 0
)

data class MarketTrendInsight(
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("recommended_product") val recommendedProduct: String = "",
    @SerializedName("action_type") val actionType: String = "RESTOCK",
    @SerializedName("badge_label") val badgeLabel: String = "🌐 Market Trend"
)
