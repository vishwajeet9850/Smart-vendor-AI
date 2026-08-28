package com.smartvendor.ai.ui.screens

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.network.ApiClient
import com.smartvendor.ai.network.models.AnalyticsSummaryResponse
import com.smartvendor.ai.network.models.StockUpdateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val urgencyLevel: String = ""
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
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = api.getAnalyticsSummary(days, rangeType)
                if (response.isSuccessful) {
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
                    }.ifEmpty { mapOf("General" to 1f) }

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
                            urgencyLevel = r.urgencyLevel
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
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Failed to load analytics (${response.code()})")
                    }
                }
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Cannot reach server: ${ex.message}")
                }
            }
        }
    }

    fun restockProduct(productId: String, amount: Int) {
        val updatedRecs = _uiState.value.stockRecommendations.map { rec ->
            if (rec.productId == productId) {
                val newStock = rec.currentStock + amount
                rec.copy(
                    currentStock = newStock,
                    reasoning = "Restock request applied (+${amount} units). Stock is now ${newStock}."
                )
            } else rec
        }
        _uiState.update { it.copy(stockRecommendations = updatedRecs) }

        viewModelScope.launch {
            try {
                val current = api.getProduct(productId)
                if (current.isSuccessful) {
                    val currentStock = current.body()?.stock ?: 0
                    val newStock = currentStock + amount
                    api.updateStock(productId, StockUpdateRequest(newStock))
                    loadAnalytics(currentDays, currentRangeType)
                }
            } catch (_: Exception) {}
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
