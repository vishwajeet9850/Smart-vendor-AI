package com.smartvendor.ai.repository

import android.util.Log
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.network.ApiClient
import com.smartvendor.ai.network.NetworkMonitor
import com.smartvendor.ai.network.models.BulkStockRecommendationResponse
import com.smartvendor.ai.network.models.MarketDemandInfo
import com.smartvendor.ai.network.models.MarketTrendInsight
import com.smartvendor.ai.network.models.StockRecommendationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

interface StockRecommendationRepository {
    val recommendationsFlow: Flow<List<StockRecommendationResponse>>
    val marketTrendsFlow: Flow<List<MarketTrendInsight>>
    suspend fun refreshRecommendations(forecastDays: Int = 7): Result<List<StockRecommendationResponse>>
}

class StockRecommendationRepositoryImpl(
    private val productRepository: ProductRepository = ProductRepositoryImpl()
) : StockRecommendationRepository {

    private val api = ApiClient.apiService
    private val TAG = "StockRecRepo"

    private val _recommendationsFlow = MutableStateFlow<List<StockRecommendationResponse>>(emptyList())
    override val recommendationsFlow: Flow<List<StockRecommendationResponse>> = _recommendationsFlow.asStateFlow()

    private val _marketTrendsFlow = MutableStateFlow<List<MarketTrendInsight>>(emptyList())
    override val marketTrendsFlow: Flow<List<MarketTrendInsight>> = _marketTrendsFlow.asStateFlow()

    override suspend fun refreshRecommendations(forecastDays: Int): Result<List<StockRecommendationResponse>> =
        withContext(Dispatchers.IO) {
            // 1. 100% Offline On-Device Immediate Emission (0ms UI latency)
            try {
                val localProducts: List<Product> = productRepository.getProductsStream().first()
                val offlineRecs = computeOfflineRecommendations(localProducts)
                _recommendationsFlow.value = offlineRecs
            } catch (e: Exception) {
                Log.w(TAG, "Quick offline recommendation load note: ${e.message}")
            }

            // 2. Server API AI Enrichment (if online)
            if (NetworkMonitor.isOnline.value) {
                try {
                    val response = api.getBulkStockRecommendations(forecastDays = forecastDays)
                    if (response.isSuccessful && response.body() != null) {
                        val recs = response.body()!!.recommendations
                        _recommendationsFlow.value = recs
                        Log.d(TAG, "Loaded ${recs.size} stock recommendations from server")

                        // Also fetch market trends
                        try {
                            val trendsResp = api.getMarketTrends()
                            if (trendsResp.isSuccessful && trendsResp.body() != null) {
                                _marketTrendsFlow.value = trendsResp.body()!!
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Market trends request note: ${e.message}")
                        }

                        return@withContext Result.success(recs)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Server stock recommendation note: ${e.message}")
                }
            }

            Result.success(_recommendationsFlow.value)
        }

    private fun computeOfflineRecommendations(localProducts: List<Product>): List<StockRecommendationResponse> {
        return localProducts.map { p: Product ->
            val isOutOfStock = (p.stock == 0)
            val isLowStock = (p.stock <= p.lowStockThreshold || p.stock <= 5)
            val targetStock = maxOf(p.lowStockThreshold * 2, 15)
            val purchaseNeeded = maxOf(0, targetStock - p.stock)
            val isSweetsOrGifts = p.category.contains("Sweets", ignoreCase = true) || 
                                  p.category.contains("Dry Fruits", ignoreCase = true) ||
                                  p.name.contains("Gulab Jamun", ignoreCase = true) ||
                                  p.name.contains("Celebrations", ignoreCase = true)
            val isBeverage = p.category.contains("Beverages", ignoreCase = true) ||
                             p.category.contains("Dairy", ignoreCase = true) && p.name.contains("Milk", ignoreCase = true)
            val isSnack = p.category.contains("Snacks", ignoreCase = true) ||
                          p.category.contains("Biscuits", ignoreCase = true)

            val recType = when {
                isOutOfStock -> "URGENT_RESTOCK"
                isSweetsOrGifts && p.stock <= 8 -> "FESTIVAL_SURGE"
                isBeverage && p.stock in 4..12 -> "MARKET_TREND"
                isLowStock -> "LOW_STOCK_BUFFER"
                p.stock > targetStock * 2 -> "OVERSTOCK_CLEARANCE"
                isSnack && p.stock in 10..30 -> "BUNDLE_OPPORTUNITY"
                else -> "HEALTHY_STOCK"
            }

            val status = when (recType) {
                "URGENT_RESTOCK" -> "RESTOCK"
                "FESTIVAL_SURGE", "MARKET_TREND", "LOW_STOCK_BUFFER" -> "LOW_STOCK"
                "OVERSTOCK_CLEARANCE" -> "OVERSTOCK"
                else -> "STOCK_OK"
            }

            val recTitle = when (recType) {
                "URGENT_RESTOCK" -> "🚨 Urgent Restock"
                "FESTIVAL_SURGE" -> "🎉 Festival Surge"
                "MARKET_TREND" -> "📈 High Market Demand"
                "LOW_STOCK_BUFFER" -> "🟡 Reorder Buffer"
                "NEAR_EXPIRY" -> "⏳ Expiry Flash Deal"
                "OVERSTOCK_CLEARANCE" -> "❄️ Overstock Alert"
                "BUNDLE_OPPORTUNITY" -> "🔄 Smart Combo"
                else -> "🟢 Healthy Stock"
            }

            val actType = when (recType) {
                "URGENT_RESTOCK" -> "RESTOCK"
                "FESTIVAL_SURGE" -> "FESTIVAL_ORDER"
                "MARKET_TREND" -> "MARKET_ORDER"
                "LOW_STOCK_BUFFER" -> "REORDER"
                "NEAR_EXPIRY" -> "CLEARANCE_DISCOUNT"
                "OVERSTOCK_CLEARANCE" -> "PAUSE_REORDER"
                "BUNDLE_OPPORTUNITY" -> "COMBO_DEAL"
                else -> "MAINTAIN"
            }

            val unitsToBuy = if (recType in listOf("URGENT_RESTOCK", "FESTIVAL_SURGE", "MARKET_TREND", "LOW_STOCK_BUFFER")) {
                maxOf(purchaseNeeded, if (recType == "FESTIVAL_SURGE") 24 else 12)
            } else 0

            val actLabel = when (recType) {
                "URGENT_RESTOCK" -> "Restock +$unitsToBuy"
                "FESTIVAL_SURGE" -> "Festival Order +$unitsToBuy"
                "MARKET_TREND" -> "Trend Order +$unitsToBuy"
                "LOW_STOCK_BUFFER" -> "Reorder +$unitsToBuy"
                "NEAR_EXPIRY" -> "Flash 20% Off"
                "OVERSTOCK_CLEARANCE" -> "Pause & Clear"
                "BUNDLE_OPPORTUNITY" -> "Combo Deal"
                else -> "Stock Balanced"
            }

            val simpleReason = when (recType) {
                "URGENT_RESTOCK" -> "🚨 Out of Stock: '${p.name}' is completely sold out. Immediate restock of +$unitsToBuy units recommended to prevent lost sales."
                "FESTIVAL_SURGE" -> "🎉 Festival Demand Surge: Festive season increases customer buying. Your shelf stock (${p.stock} units) is low. Stock up +$unitsToBuy units."
                "MARKET_TREND" -> "📈 High Market Demand: Strong customer interest for '${p.name}'. Current stock of ${p.stock} units is running low. Order +$unitsToBuy units."
                "LOW_STOCK_BUFFER" -> "🟡 Low Stock Warning: Only ${p.stock} units remaining (below threshold ${p.lowStockThreshold}). Reorder +$unitsToBuy units."
                "OVERSTOCK_CLEARANCE" -> "❄️ Overstock Alert: Current inventory (${p.stock} units) exceeds sales velocity. Pause reorders to free cash."
                "BUNDLE_OPPORTUNITY" -> "🔄 Smart Combo Opportunity: '${p.name}' is frequently co-purchased with tea/snacks. Offer a combo deal to boost bill size."
                else -> "🟢 Healthy Stock: Current inventory of ${p.stock} units is optimal. No reorder required."
            }

            StockRecommendationResponse(
                productId = p.id,
                productName = p.name,
                category = p.category,
                currentStock = p.stock,
                predictedDailyDemand = (p.lowStockThreshold / 3.0),
                predictedDemand = targetStock,
                safetyStock = 3,
                targetStock = targetStock,
                recommendedPurchase = unitsToBuy,
                status = status,
                trend = if (recType in listOf("FESTIVAL_SURGE", "MARKET_TREND")) "INCREASING" else "STABLE",
                seasonalProfile = if (recType == "FESTIVAL_SURGE") "FESTIVE_PEAK" else "STABLE",
                seasonalFactor = if (recType == "FESTIVAL_SURGE") 1.8 else 1.0,
                supplierMoq = 1,
                unit = "pcs",
                market = MarketDemandInfo(
                    marketInsightAvailable = (recType == "MARKET_TREND"),
                    demandLevel = if (recType == "MARKET_TREND") "HIGH" else "NORMAL",
                    comparisonPercentage = 115.0,
                    marketAverageSales = p.stock.toDouble(),
                    participatingVendors = 3,
                    insightText = if (recType == "MARKET_TREND") "High demand across network" else "Running in offline mode."
                ),
                reason = simpleReason,
                recommendationType = recType,
                recommendationTitle = recTitle,
                actionType = actType,
                actionLabel = actLabel,
                simpleReason = simpleReason
            )
        }.sortedWith(
            compareBy(
                { when (it.recommendationType) {
                    "URGENT_RESTOCK" -> 0
                    "FESTIVAL_SURGE" -> 1
                    "MARKET_TREND" -> 2
                    "LOW_STOCK_BUFFER" -> 3
                    "NEAR_EXPIRY" -> 4
                    "OVERSTOCK_CLEARANCE" -> 5
                    "BUNDLE_OPPORTUNITY" -> 6
                    else -> 7
                } },
                { -it.recommendedPurchase }
            )
        )
    }
}



