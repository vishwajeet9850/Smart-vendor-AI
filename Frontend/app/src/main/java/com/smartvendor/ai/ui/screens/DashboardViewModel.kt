package com.smartvendor.ai.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.repository.AuthRepository
import com.smartvendor.ai.repository.AuthRepositoryImpl
import com.smartvendor.ai.repository.SalesRepository
import com.smartvendor.ai.repository.SalesRepositoryImpl
import com.smartvendor.ai.repository.StoreRepository
import com.smartvendor.ai.repository.StoreRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UrgentStockAlert(
    val id: String,
    val name: String,
    val currentStock: Int,
    val lowStockThreshold: Int,
    val category: String,
    val isOutOfStock: Boolean
)

data class DashboardUiState(
    val userName: String = "Shopkeeper",
    val storeName: String = "SmartVendor Store",
    val openBillId: String? = null,
    val urgentStockAlerts: List<UrgentStockAlert> = emptyList(),
    val showNotificationDialog: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl(),
    private val salesRepository: SalesRepository = SalesRepositoryImpl(),
    private val storeRepository: StoreRepository = StoreRepositoryImpl(),
    private val productRepository: com.smartvendor.ai.repository.ProductRepository = com.smartvendor.ai.repository.ProductRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
        observeUrgentStockAlerts()
    }

    private fun loadDashboardData() {
        // Load User Name
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _uiState.update { state ->
                    state.copy(
                        userName = user?.name?.ifBlank { "Shopkeeper" } ?: "Shopkeeper"
                    )
                }
            }
        }

        // Load Dynamic Store Name from FastAPI Backend
        viewModelScope.launch {
            storeRepository.getStoreInfo().collect { store ->
                _uiState.update { state ->
                    state.copy(
                        storeName = store.name.ifBlank { "SmartVendor Store" }
                    )
                }
            }
        }

        viewModelScope.launch {
            checkOpenBillingSession()
        }
    }

    private fun observeUrgentStockAlerts() {
        viewModelScope.launch {
            productRepository.getProductsStream().collect { products ->
                val alerts = products
                    .filter { it.stock <= it.lowStockThreshold || it.stock == 0 }
                    .map {
                        UrgentStockAlert(
                            id = it.id,
                            name = it.name,
                            currentStock = it.stock,
                            lowStockThreshold = it.lowStockThreshold,
                            category = it.category,
                            isOutOfStock = it.stock == 0
                        )
                    }
                    .sortedWith(compareBy({ !it.isOutOfStock }, { it.currentStock }))

                _uiState.update { it.copy(urgentStockAlerts = alerts) }
            }
        }
    }

    private suspend fun checkOpenBillingSession() {
        val result = salesRepository.getOpenBillingSession()
        result.onSuccess { openBill ->
            _uiState.update { it.copy(openBillId = openBill?.billId) }
        }
    }

    fun toggleNotificationDialog(show: Boolean) {
        _uiState.update { it.copy(showNotificationDialog = show) }
    }

    fun quickRestock(productId: String, addedUnits: Int = 20) {
        viewModelScope.launch {
            val alert = _uiState.value.urgentStockAlerts.firstOrNull { it.id == productId } ?: return@launch
            val target = alert.currentStock + addedUnits
            productRepository.updateStock(productId, target)
        }
    }

    fun startOrResumeNewBill(onBillCreated: (String) -> Unit) {
        val newBillId = "BILL_${System.currentTimeMillis()}"
        onBillCreated(newBillId)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
