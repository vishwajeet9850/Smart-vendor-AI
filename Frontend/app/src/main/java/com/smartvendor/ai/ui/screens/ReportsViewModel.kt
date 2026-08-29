package com.smartvendor.ai.ui.screens

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.network.ApiClient
import com.smartvendor.ai.network.NetworkMonitor
import com.smartvendor.ai.network.models.AnalyticsSummaryResponse
import com.smartvendor.ai.network.models.StockUpdateRequest
import com.smartvendor.ai.repository.LocalStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Immutable
data class StockRecommendation(
    val productId: String = "",
    val productName: String = "",
    val currentStock: Int = 0,
    val recommendedReorder: Int = 0,
    val category: String = "",
    val peakWindow: String = "",
    val salesVelocity: String = "",
    val reasoning: String = "",
    val urgencyLevel: String = "",
    val recommendationType: String = "URGENT_RESTOCK",
    val recommendationTitle: String = "🚨 Urgent Restock",
    val actionType: String = "RESTOCK",
    val actionLabel: String = "Order Stock",
    val simpleReason: String = ""
)

@Immutable
data class MarketTrend(
    val title: String = "",
    val description: String = "",
    val recommendedProduct: String = "",
    val actionType: String = "",
    val badgeLabel: String = ""
)

@Immutable
data class ReportsUiState(
    val selectedTimeRange: String = "Last 30 Days",
    val totalRevenue: Double = 0.0,
    val totalTransactions: Int = 0,
    val averageBillValue: Double = 0.0,
    val bestSellingProduct: String = "N/A",
    val lowStockCount: Int = 0,
    val totalProducts: Int = 0,
    val revenueDataPoints: List<Pair<String, Double>> = emptyList(),
    val topProducts: List<Pair<String, Int>> = emptyList(),
    val categoryDistribution: Map<String, Float> = emptyMap(),
    val stockRecommendations: List<StockRecommendation> = emptyList(),
    val marketTrends: List<MarketTrend> = emptyList(),
    val isOfflineMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ReportsViewModel : ViewModel() {

    private val api = ApiClient.apiService

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private var currentDays = 30
    private var currentRangeType: String? = "30days"

    init {
        selectTimeRange("Last 30 Days")
    }

    fun selectTimeRange(range: String) {
        _uiState.update { it.copy(selectedTimeRange = range) }
        val (days, rangeType) = when (range) {
            "Today" -> Pair(0, "today")
            "Yesterday" -> Pair(1, "yesterday")
            "Last 7 Days" -> Pair(7, "7days")
            "Last 30 Days" -> Pair(30, "30days")
            else -> Pair(30, "30days")
        }
        currentDays = days
        currentRangeType = rangeType
        loadAnalytics(days, rangeType)
    }

    private fun loadAnalytics(days: Int, rangeType: String?) {
        viewModelScope.launch {
            // 1. Immediately load local on-device analytics so UI renders with zero delay
            loadOfflineAnalytics(days, rangeType)

            // 2. If online, fetch authoritative analytics from backend in background
            if (NetworkMonitor.isOnline.value) {
                try {
                    val response = api.getAnalyticsSummary(days, rangeType)
                    if (response.isSuccessful && response.body() != null) {
                        val data: AnalyticsSummaryResponse = response.body()!!
                        val avg = if (data.totalBills > 0) data.totalRevenue / data.totalBills else 0.0
                        val bestSeller = data.topProducts.firstOrNull()?.productName ?: "N/A"

                        val chartPoints = data.dailyRevenue.map { day ->
                            Pair(day.date, day.revenue)
                        }.ifEmpty { listOf(Pair("Today", data.totalRevenue)) }

                        val topProductPairs = data.topProducts.map { tp ->
                            Pair(tp.productName, tp.quantitySold)
                        }

                        val catDist = data.topProducts.associate { tp ->
                            tp.productName to tp.quantitySold.toFloat()
                        }

                        val recs = data.stockRecommendations.map { r ->
                            StockRecommendation(
                                productId = r.productId,
                                productName = r.productName,
                                currentStock = r.currentStock,
                                recommendedReorder = r.recommendedReorder,
                                category = r.category,
                                peakWindow = r.peakWindow,
                                salesVelocity = r.salesVelocity,
                                reasoning = r.reasoning,
                                urgencyLevel = r.urgencyLevel,
                                recommendationType = r.recommendationType,
                                recommendationTitle = r.recommendationTitle,
                                actionType = r.actionType,
                                actionLabel = r.actionLabel,
                                simpleReason = r.simpleReason.ifBlank { r.reasoning }
                            )
                        }


                        val trends = data.marketTrends.map { mt ->
                            MarketTrend(
                                title = mt.title,
                                description = mt.description,
                                recommendedProduct = mt.recommendedProduct,
                                actionType = mt.actionType,
                                badgeLabel = mt.badgeLabel
                            )
                        }

                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                isOfflineMode = false,
                                totalRevenue = data.totalRevenue,
                                totalTransactions = data.totalBills,
                                averageBillValue = avg,
                                bestSellingProduct = bestSeller,
                                lowStockCount = data.lowStockCount,
                                totalProducts = data.totalProducts,
                                revenueDataPoints = chartPoints,
                                topProducts = topProductPairs,
                                categoryDistribution = catDist,
                                stockRecommendations = recs,
                                marketTrends = trends
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Local analytics already rendered
                }
            }
        }
    }


    private suspend fun loadOfflineAnalytics(days: Int, rangeType: String?) = withContext(Dispatchers.IO) {
        try {
            val allBills = LocalStoreManager.getBills().filter { it.status == Bill.BILL_STATUS_COMPLETED }
            val allProducts = LocalStoreManager.getProducts()
            val now = System.currentTimeMillis()

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis

            val (startTime, endTime) = when (rangeType) {
                "today" -> Pair(todayStart, Long.MAX_VALUE)
                "yesterday" -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    val yesterdayStart = calendar.timeInMillis
                    Pair(yesterdayStart, todayStart)
                }
                "7days" -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -6)
                    Pair(calendar.timeInMillis, Long.MAX_VALUE)
                }
                "30days" -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -29)
                    Pair(calendar.timeInMillis, Long.MAX_VALUE)
                }
                else -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -29)
                    Pair(calendar.timeInMillis, Long.MAX_VALUE)
                }
            }

            val filteredBills = allBills.filter { it.timestamp in startTime..endTime }
            val salesBills = filteredBills.filter { it.transactionType != Bill.TRANSACTION_TYPE_RETURN }
            val returnBills = filteredBills.filter { it.transactionType == Bill.TRANSACTION_TYPE_RETURN }

            val totalRev = (salesBills.sumOf { it.grandTotal } - returnBills.sumOf { it.grandTotal }).coerceAtLeast(0.0)
            val totalTrans = salesBills.size
            val avgBill = if (totalTrans > 0) totalRev / totalTrans else 0.0

            // Top selling products aggregation (strictly for sales bills)
            val itemSalesMap = mutableMapOf<String, Pair<String, Int>>() // productId -> (name, qty)
            val itemRevenueMap = mutableMapOf<String, Double>()

            salesBills.forEach { b ->
                b.items.forEach { item ->
                    val current = itemSalesMap[item.productId]
                    val newQty = (current?.second ?: 0) + item.quantity
                    itemSalesMap[item.productId] = Pair(item.name, newQty)
                    itemRevenueMap[item.productId] = (itemRevenueMap[item.productId] ?: 0.0) + item.lineTotal
                }
            }


            val topProductsList = itemSalesMap.values
                .sortedByDescending { it.second }
                .take(5)
                .map { Pair(it.first, it.second) }

            val bestSeller = topProductsList.firstOrNull()?.first ?: "N/A"

            // Distribution mapping
            val catDist = if (topProductsList.isNotEmpty()) {
                topProductsList.associate { it.first to it.second.toFloat() }
            } else {
                emptyMap()
            }

            // Daily Revenue breakdown
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val revenuePoints = when (rangeType) {
                "today" -> listOf(Pair("Today", totalRev))
                "yesterday" -> listOf(Pair("Yesterday", totalRev))
                "7days" -> {
                    val points = mutableListOf<Pair<String, Double>>()
                    val tempCal = Calendar.getInstance()
                    for (i in 6 downTo 0) {
                        tempCal.timeInMillis = now
                        tempCal.add(Calendar.DAY_OF_YEAR, -i)
                        tempCal.set(Calendar.HOUR_OF_DAY, 0)
                        tempCal.set(Calendar.MINUTE, 0)
                        tempCal.set(Calendar.SECOND, 0)
                        tempCal.set(Calendar.MILLISECOND, 0)
                        val dayStart = tempCal.timeInMillis
                        val dayEnd = dayStart + 24 * 60 * 60 * 1000
                        val dayRev = filteredBills.filter { it.timestamp in dayStart until dayEnd }.sumOf { it.grandTotal }
                        points.add(Pair(dateFormat.format(Date(dayStart)), dayRev))
                    }
                    points
                }
                else -> { // 30 days
                    val points = mutableListOf<Pair<String, Double>>()
                    val tempCal = Calendar.getInstance()
                    for (i in 29 downTo 0) {
                        tempCal.timeInMillis = now
                        tempCal.add(Calendar.DAY_OF_YEAR, -i)
                        tempCal.set(Calendar.HOUR_OF_DAY, 0)
                        tempCal.set(Calendar.MINUTE, 0)
                        tempCal.set(Calendar.SECOND, 0)
                        tempCal.set(Calendar.MILLISECOND, 0)
                        val dayStart = tempCal.timeInMillis
                        val dayEnd = dayStart + 24 * 60 * 60 * 1000
                        val dayRev = filteredBills.filter { it.timestamp in dayStart until dayEnd }.sumOf { it.grandTotal }
                        points.add(Pair(dateFormat.format(Date(dayStart)), dayRev))
                    }
                    points
                }
            }

            val totalProds = allProducts.size
            val lowStock = allProducts.count { it.stock <= it.lowStockThreshold || it.stock <= 5 }

            // Offline Stock Recommendations based on local stock & threshold
            val offlineRecs = allProducts
                .take(12)
                .mapNotNull { p ->
                    val isOutOfStock = p.stock == 0
                    val isLowStock = p.stock <= p.lowStockThreshold || p.stock <= 5
                    val targetStock = maxOf(p.lowStockThreshold * 2, 15)
                    val purchaseNeeded = maxOf(0, targetStock - p.stock)
                    val isSweets = p.category.contains("Sweets", ignoreCase = true) || p.name.contains("Gulab Jamun", ignoreCase = true)
                    val isBeverage = p.category.contains("Beverages", ignoreCase = true) || p.name.contains("Milk", ignoreCase = true)

                    val recType = when {
                        isOutOfStock -> "URGENT_RESTOCK"
                        isSweets && p.stock <= 8 -> "FESTIVAL_SURGE"
                        isBeverage && p.stock in 4..12 -> "MARKET_TREND"
                        isLowStock -> "LOW_STOCK_BUFFER"
                        p.stock > targetStock * 2 -> "OVERSTOCK_CLEARANCE"
                        else -> "HEALTHY_STOCK"
                    }

                    if (recType == "HEALTHY_STOCK" && allProducts.any { it.stock <= it.lowStockThreshold }) {
                        null // prioritize actionable recommendations
                    } else {
                        val unitsToBuy = if (recType in listOf("URGENT_RESTOCK", "FESTIVAL_SURGE", "MARKET_TREND", "LOW_STOCK_BUFFER")) {
                            maxOf(purchaseNeeded, if (recType == "FESTIVAL_SURGE") 24 else 10)
                        } else 0

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
                            "BUNDLE_OPPORTUNITY" -> "🔄 Smart Combo Opportunity: '${p.name}' is frequently co-purchased with snacks. Offer a combo deal to boost bill size."
                            else -> "🟢 Healthy Stock: Current inventory of ${p.stock} units is optimal. No reorder required."
                        }

                        StockRecommendation(
                            productId = p.id,
                            productName = p.name,
                            currentStock = p.stock,
                            recommendedReorder = unitsToBuy,
                            category = p.category,
                            peakWindow = if (recType == "FESTIVAL_SURGE") "Festive Peak" else "General Demand",
                            salesVelocity = if (isOutOfStock) "Out of Stock" else "Active Sales",
                            reasoning = simpleReason,
                            urgencyLevel = if (recType in listOf("URGENT_RESTOCK", "FESTIVAL_SURGE")) "HIGH" else "NORMAL",
                            recommendationType = recType,
                            recommendationTitle = recTitle,
                            actionType = actType,
                            actionLabel = actLabel,
                            simpleReason = simpleReason
                        )
                    }
                }
                .sortedWith(compareBy({ when (it.recommendationType) { "URGENT_RESTOCK" -> 0; "FESTIVAL_SURGE" -> 1; "MARKET_TREND" -> 2; "LOW_STOCK_BUFFER" -> 3; else -> 4 } }, { -it.recommendedReorder }))



            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isOfflineMode = true,
                    errorMessage = null,
                    totalRevenue = totalRev,
                    totalTransactions = totalTrans,
                    averageBillValue = avgBill,
                    bestSellingProduct = bestSeller,
                    lowStockCount = lowStock,
                    totalProducts = totalProds,
                    revenueDataPoints = revenuePoints,
                    topProducts = topProductsList,
                    categoryDistribution = catDist,
                    stockRecommendations = offlineRecs
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "Error computing sales report: ${e.message}")
            }
        }
    }

    fun restockProduct(productId: String, amount: Int) {
        val currentProd = LocalStoreManager.getProductById(productId)
        val currentStock = currentProd?.stock ?: 0
        val newStock = currentStock + amount

        // Immediately update offline inventory
        LocalStoreManager.updateStock(productId, newStock)

        val updatedRecs = _uiState.value.stockRecommendations.map { rec ->
            if (rec.productId == productId) {
                rec.copy(
                    currentStock = newStock,
                    reasoning = "Restock request applied (+${amount} units). Stock is now ${newStock}."
                )
            } else rec
        }
        _uiState.update { it.copy(stockRecommendations = updatedRecs) }

        // Also push to backend if online
        if (NetworkMonitor.isOnline.value) {
            viewModelScope.launch {
                try {
                    api.updateStock(productId, StockUpdateRequest(newStock))
                    loadAnalytics(currentDays, currentRangeType)
                } catch (_: Exception) {}
            }
        } else {
            loadAnalytics(currentDays, currentRangeType)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

