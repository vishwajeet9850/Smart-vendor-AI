package com.smartvendor.ai.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.repository.ProductRepository
import com.smartvendor.ai.repository.ProductRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InventoryUiState(
    val products: List<Product> = emptyList(),
    val filteredProducts: List<Product> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val categories: List<String> = listOf("All"),
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false,
    val editingProduct: Product? = null,
    val selectedProductForDetail: Product? = null,
    val errorMessage: String? = null
)

class InventoryViewModel(
    private val productRepository: ProductRepository = ProductRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            productRepository.getProductsStream().collect { list ->
                val categories = listOf("All") + list.map { it.category }.distinct().filter { it.isNotBlank() }
                _uiState.update { state ->
                    state.copy(
                        products = list,
                        categories = categories,
                        isLoading = false
                    )
                }
                applyFilter()
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        applyFilter()
    }

    private fun applyFilter() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val category = _uiState.value.selectedCategory

        val filtered = _uiState.value.products.filter { product ->
            val matchesQuery = query.isEmpty() ||
                    product.name.lowercase().contains(query) ||
                    product.category.lowercase().contains(query) ||
                    product.barcode.contains(query)

            val matchesCategory = category == "All" || product.category.equals(category, ignoreCase = true)
            matchesQuery && matchesCategory
        }

        _uiState.update { it.copy(filteredProducts = filtered) }
    }

    fun addNewProduct(name: String, price: Double, category: String, stock: Int, barcode: String, classId: Int) {
        viewModelScope.launch {
            val newProduct = Product(
                name = name,
                price = price,
                category = category,
                stock = stock,
                barcode = barcode,
                classId = classId
            )
            productRepository.addProduct(newProduct).onSuccess {
                _uiState.update { state -> state.copy(showAddDialog = false) }
                loadProducts()
            }.onFailure { err ->
                _uiState.update { state -> state.copy(errorMessage = err.message) }
            }
        }
    }

    fun setExactStock(productId: String, newStock: Int) {
        val targetStock = newStock.coerceAtLeast(0)
        // 1. Immediately update UI state in memory for instant visual feedback
        val updatedList = _uiState.value.products.map { p ->
            if (p.id == productId) p.copy(stock = targetStock) else p
        }
        _uiState.update { it.copy(products = updatedList) }
        applyFilter()

        // 2. Persist to FastAPI SQLite database
        viewModelScope.launch {
            productRepository.updateStock(productId, targetStock)
        }
    }

    fun updateProductDetails(productId: String, name: String, price: Double, category: String, stock: Int) {
        val targetStock = stock.coerceAtLeast(0)
        val updatedList = _uiState.value.products.map { p ->
            if (p.id == productId) p.copy(name = name, price = price, category = category, stock = targetStock) else p
        }
        _uiState.update { it.copy(products = updatedList, editingProduct = null) }
        applyFilter()

        viewModelScope.launch {
            val updated = Product(
                id = productId,
                name = name,
                price = price,
                category = category,
                stock = targetStock
            )
            productRepository.updateProduct(updated)
        }
    }

    fun deleteProduct(productId: String) {
        val updatedList = _uiState.value.products.filterNot { it.id == productId }
        _uiState.update { it.copy(products = updatedList, editingProduct = null) }
        applyFilter()

        viewModelScope.launch {
            productRepository.deleteProduct(productId)
        }
    }

    fun openEditProductDialog(product: Product) {
        _uiState.update { it.copy(editingProduct = product) }
    }

    fun closeEditProductDialog() {
        _uiState.update { it.copy(editingProduct = null) }
    }

    fun openAddProductDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun closeAddProductDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
