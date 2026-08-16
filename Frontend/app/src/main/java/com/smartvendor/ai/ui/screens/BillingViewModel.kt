package com.smartvendor.ai.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.model.BillItem
import com.smartvendor.ai.repository.ProductRepository
import com.smartvendor.ai.repository.ProductRepositoryImpl
import com.smartvendor.ai.repository.SalesRepository
import com.smartvendor.ai.repository.SalesRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BillingUiState(
    val bill: Bill? = null,
    val selectedPaymentMethod: String = "CASH",
    val discountInput: Double = 0.0,
    val isProcessingCheckout: Boolean = false,
    val checkoutSuccess: Boolean = false,
    val errorMessage: String? = null
)

class BillingViewModel(
    private val salesRepository: SalesRepository = SalesRepositoryImpl(),
    private val productRepository: ProductRepository = ProductRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    fun loadBill(billId: String) {
        viewModelScope.launch {
            salesRepository.getBillById(billId).onSuccess { bill ->
                _uiState.update { it.copy(bill = bill) }
            }
        }
    }

    fun setPaymentMethod(method: String) {
        _uiState.update { it.copy(selectedPaymentMethod = method) }
    }

    fun increaseItemQuantity(productId: String) {
        val currentBill = _uiState.value.bill ?: return
        val updatedItems = currentBill.items.map { item ->
            if (item.productId == productId) {
                val newQty = item.quantity + 1
                item.copy(
                    quantity = newQty,
                    lineTotal = (newQty * item.unitPrice) + (((newQty * item.unitPrice) * item.gst) / 100.0)
                )
            } else item
        }
        recalculateBill(currentBill, updatedItems)
    }

    fun decreaseItemQuantity(productId: String) {
        val currentBill = _uiState.value.bill ?: return
        val updatedItems = currentBill.items.mapNotNull { item ->
            if (item.productId == productId) {
                if (item.quantity > 1) {
                    val newQty = item.quantity - 1
                    item.copy(
                        quantity = newQty,
                        lineTotal = (newQty * item.unitPrice) + (((newQty * item.unitPrice) * item.gst) / 100.0)
                    )
                } else null
            } else item
        }
        recalculateBill(currentBill, updatedItems)
    }

    fun removeItem(productId: String) {
        val currentBill = _uiState.value.bill ?: return
        val updatedItems = currentBill.items.filterNot { it.productId == productId }
        recalculateBill(currentBill, updatedItems)
    }

    private fun recalculateBill(currentBill: Bill, items: List<BillItem>) {
        val subtotal = items.sumOf { it.quantity * it.unitPrice }
        val gst = items.sumOf { (it.quantity * it.unitPrice * it.gst) / 100.0 }
        val grandTotal = (subtotal + gst - currentBill.discount).coerceAtLeast(0.0)

        val updatedBill = currentBill.copy(
            items = items,
            subtotal = subtotal,
            gst = gst,
            grandTotal = grandTotal
        )
        _uiState.update { it.copy(bill = updatedBill) }
        viewModelScope.launch {
            salesRepository.saveBill(updatedBill)
        }
    }

    fun performCheckout(onSuccess: (String) -> Unit) {
        val bill = _uiState.value.bill
        if (bill == null || bill.items.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Cannot checkout an empty bill.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingCheckout = true, errorMessage = null) }
            val paymentMethod = _uiState.value.selectedPaymentMethod

            val completedBill = bill.copy(
                paymentMethod = paymentMethod,
                status = Bill.BILL_STATUS_COMPLETED
            )

            salesRepository.saveBill(completedBill).onSuccess {
                _uiState.update { it.copy(isProcessingCheckout = false, checkoutSuccess = true) }
                onSuccess(completedBill.billId)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessingCheckout = false,
                        errorMessage = err.message ?: "Checkout failed"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
