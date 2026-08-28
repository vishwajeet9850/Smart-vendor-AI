package com.smartvendor.ai.ui.screens

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.network.models.MasterCatalogResponse
import com.smartvendor.ai.repository.ProductRepository
import com.smartvendor.ai.repository.ProductRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class InventoryUiState(
    val products: List<Product> = emptyList(),
    val filteredProducts: List<Product> = emptyList(),
    val catalogRecommendations: List<MasterCatalogResponse> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val categories: List<String> = defaultCategories,
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false,
    val addProductInitialData: MasterCatalogResponse? = null,
    val editingProduct: Product? = null,
    val selectedProductForDetail: Product? = null,
    val errorMessage: String? = null
) {
    companion object {
        val defaultCategories = listOf(
            "All",
            "General",
            "Snacks & Biscuits",
            "Chocolates & Sweets",
            "Instant Foods & Noodles",
            "Groceries & Staples",
            "Atta & Flours",
            "Dals & Pulses",
            "Rice & Grains",
            "Edible Oils & Ghee",
            "Spices & Masalas",
            "Dairy & Milk",
            "Beverages & Drinks",
            "Tea & Coffee",
            "Cleaning & Household",
            "Personal Care & Hygiene",
            "Oral Care",
            "Hair Care",
            "Breakfast & Cereals",
            "Bakery & Bread",
            "Pooja Essentials"
        )
    }
}

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
                // Clean and deduplicate by ID
                val cleanList = list.distinctBy { it.id }
                val dynamicCategories = cleanList.map { it.category }.filter { it.isNotBlank() }
                val allCategories = (InventoryUiState.defaultCategories + dynamicCategories).distinct()

                _uiState.update { state ->
                    state.copy(
                        products = cleanList,
                        categories = allCategories,
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

        if (query.trim().length >= 2) {
            viewModelScope.launch {
                val results = productRepository.searchMasterCatalog(query).getOrDefault(emptyList())
                _uiState.update { it.copy(catalogRecommendations = results) }
            }
        } else {
            _uiState.update { it.copy(catalogRecommendations = emptyList()) }
        }
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

            val matchesCategory = category == "All" ||
                    product.category.equals(category, ignoreCase = true) ||
                    (category.equals("General", ignoreCase = true) && (product.category.isBlank() || product.category.equals("General", ignoreCase = true))) ||
                    product.category.contains(category, ignoreCase = true) ||
                    category.contains(product.category, ignoreCase = true)

            matchesQuery && matchesCategory
        }

        _uiState.update { it.copy(filteredProducts = filtered) }
    }

    fun addNewProduct(name: String, price: Double, category: String, stock: Int, barcode: String, classId: Int) {
        viewModelScope.launch {
            val safeCategory = if (category.isBlank()) "General" else category.trim()
            val newProduct = Product(
                name = name,
                price = price,
                category = safeCategory,
                stock = stock,
                barcode = barcode,
                classId = classId
            )
            productRepository.addProduct(newProduct).onSuccess {
                _uiState.update { state -> 
                    state.copy(
                        showAddDialog = false,
                        addProductInitialData = null,
                        catalogRecommendations = emptyList(),
                        searchQuery = ""
                    ) 
                }
            }.onFailure { err ->
                _uiState.update { state -> state.copy(errorMessage = err.message) }
            }
        }
    }

    fun openAddProductDialog(prefillItem: MasterCatalogResponse? = null) {
        _uiState.update { it.copy(showAddDialog = true, addProductInitialData = prefillItem) }
    }

    fun closeAddProductDialog() {
        _uiState.update { it.copy(showAddDialog = false, addProductInitialData = null) }
    }

    fun setExactStock(productId: String, newStock: Int) {
        val targetStock = newStock.coerceAtLeast(0)
        viewModelScope.launch {
            productRepository.updateStock(productId, targetStock)
        }
    }

    fun updateProductDetails(productId: String, name: String, price: Double, category: String, stock: Int) {
        val targetStock = stock.coerceAtLeast(0)
        val safeCategory = if (category.isBlank()) "General" else category.trim()
        viewModelScope.launch {
            val updated = Product(
                id = productId,
                name = name,
                price = price,
                category = safeCategory,
                stock = targetStock
            )
            productRepository.updateProduct(updated)
            _uiState.update { it.copy(editingProduct = null) }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            productRepository.deleteProduct(productId)
            _uiState.update { it.copy(editingProduct = null) }
        }
    }

    fun openEditProductDialog(product: Product) {
        _uiState.update { it.copy(editingProduct = product) }
    }

    fun closeEditProductDialog() {
        _uiState.update { it.copy(editingProduct = null) }
    }

    fun searchMasterCatalogQuick(query: String, onResult: (List<MasterCatalogResponse>) -> Unit) {
        viewModelScope.launch {
            val results = productRepository.searchMasterCatalog(query).getOrDefault(emptyList())
            onResult(results)
        }
    }
}
