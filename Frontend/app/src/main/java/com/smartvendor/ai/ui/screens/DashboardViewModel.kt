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

data class DashboardUiState(
    val userName: String = "Shopkeeper",
    val storeName: String = "SmartVendor Store",
    val openBillId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl(),
    private val salesRepository: SalesRepository = SalesRepositoryImpl(),
    private val storeRepository: StoreRepository = StoreRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
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

    private suspend fun checkOpenBillingSession() {
        val result = salesRepository.getOpenBillingSession()
        result.onSuccess { openBill ->
            _uiState.update { it.copy(openBillId = openBill?.billId) }
        }
    }

    fun startOrResumeNewBill(onBillCreated: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val existingOpenId = _uiState.value.openBillId
            if (existingOpenId != null) {
                _uiState.update { it.copy(isLoading = false) }
                onBillCreated(existingOpenId)
                return@launch
            }

            val result = salesRepository.createNewBillingSession("cashier_01", "store_01")
            result.onSuccess { bill ->
                _uiState.update { it.copy(isLoading = false, openBillId = bill.billId) }
                onBillCreated(bill.billId)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Failed to start bill") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
