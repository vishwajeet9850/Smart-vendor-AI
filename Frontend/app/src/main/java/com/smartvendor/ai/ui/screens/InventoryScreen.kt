package com.smartvendor.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.network.models.MasterCatalogResponse
import com.smartvendor.ai.ui.theme.AccentGreen
import com.smartvendor.ai.ui.theme.RedPrimary
import com.smartvendor.ai.ui.theme.WarningYellow

private val ColorLowStock = Color(0xFFFF5722)
private val ColorOutOfStock = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel = viewModel(),
    onNavigateToProductDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Store Inventory", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddProductDialog() },
                containerColor = RedPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search your store items or catalog...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                // Category Filter Chips
                if (uiState.categories.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(
                            items = uiState.categories,
                            key = { it },
                            contentType = { "category_chip" }
                        ) { category ->
                            val isSelected = category == uiState.selectedCategory
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onCategorySelected(category) },
                                label = { Text(category, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RedPrimary,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // Inventory List & Master Catalog Recommendations
                if (uiState.isLoading && uiState.products.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RedPrimary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // ─────────────────────────────────────────────────────────────
                        // 1. ACTIVE STORE INVENTORY (Always at the TOP)
                        // ─────────────────────────────────────────────────────────────
                        if (uiState.filteredProducts.isNotEmpty()) {
                            if (uiState.searchQuery.isNotBlank()) {
                                item(key = "store_products_header", contentType = "header") {
                                    Text(
                                        text = "Your Store Products (${uiState.filteredProducts.size})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }

                            items(
                                items = uiState.filteredProducts,
                                key = { it.id },
                                contentType = { "product_item" }
                            ) { product ->
                                ProductInventoryRow(
                                    product = product,
                                    onEditClick = { viewModel.openEditProductDialog(product) },
                                    onIncreaseStock = { viewModel.setExactStock(product.id, product.stock + 1) },
                                    onDecreaseStock = { viewModel.setExactStock(product.id, product.stock - 1) }
                                )
                            }
                        } else if (uiState.catalogRecommendations.isEmpty()) {
                            item(key = "empty_inventory", contentType = "empty_placeholder") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                                        Text("No matching store products", fontWeight = FontWeight.Bold)
                                        Text("Type in search above to discover catalog items", color = Color.Gray, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        // ─────────────────────────────────────────────────────────────
                        // 2. MASTER CATALOG RECOMMENDATIONS (At the BOTTOM)
                        // ─────────────────────────────────────────────────────────────
                        if (uiState.catalogRecommendations.isNotEmpty()) {
                            item(key = "catalog_divider", contentType = "divider") {
                                HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }

                            item(key = "catalog_header", contentType = "header") {
                                Surface(
                                    color = RedPrimary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(18.dp))
                                            Text(
                                                text = "Add from 6,000 Catalog (${uiState.catalogRecommendations.size})",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = RedPrimary)
                                            )
                                        }
                                        Text(
                                            text = "Tap 'Add' to enter your stock quantity & customize details.",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                }
                            }

                            items(
                                items = uiState.catalogRecommendations,
                                key = { it.id },
                                contentType = { "catalog_item" }
                            ) { catalogItem ->
                                MasterCatalogRecommendationRow(
                                    item = catalogItem,
                                    onAddClick = { viewModel.openAddProductDialog(catalogItem) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Product Dialog (Supports empty or pre-filled from master catalog recommendation)
    if (uiState.showAddDialog) {
        AddProductDialog(
            initialData = uiState.addProductInitialData,
            availableCategories = uiState.categories,
            onDismiss = { viewModel.closeAddProductDialog() },
            onSearchCatalog = { query, cb -> viewModel.searchMasterCatalogQuick(query, cb) },
            onSave = { name, price, category, stock, barcode, classId ->
                viewModel.addNewProduct(name, price, category, stock, barcode, classId)
            }
        )
    }

    // Edit Product Dialog
    uiState.editingProduct?.let { product ->
        EditProductStockDialog(
            product = product,
            availableCategories = uiState.categories,
            onDismiss = { viewModel.closeEditProductDialog() },
            onSave = { name, price, category, stock ->
                viewModel.updateProductDetails(product.id, name, price, category, stock)
            },
            onDelete = { viewModel.deleteProduct(product.id) }
        )
    }
}

@Composable
fun SearchableCategoryDropdown(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    availableCategories: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var textValue by remember(selectedCategory) { mutableStateOf(selectedCategory) }

    val defaultKiranaCategories = remember {
        listOf(
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

    val allCategories = remember(availableCategories) {
        (defaultKiranaCategories + availableCategories)
            .distinct()
            .filter { it.isNotBlank() && it != "All" }
    }

    val filteredCategories = remember(textValue, allCategories) {
        allCategories.filter {
            textValue.isBlank() || it.contains(textValue.trim(), ignoreCase = true)
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                onCategorySelected(it)
                expanded = true
            },
            label = { Text("Category (Optional)") },
            placeholder = { Text("Search category (Default: General)") },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Select Category"
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Live Category Dropdown Menu
        if (expanded && filteredCategories.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 180.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    filteredCategories.take(10).forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    textValue = cat
                                    onCategorySelected(cat)
                                    expanded = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat,
                                fontSize = 13.sp,
                                fontWeight = if (cat.equals(textValue, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal,
                                color = if (cat.equals(textValue, ignoreCase = true)) RedPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MasterCatalogRecommendationRow(
    item: MasterCatalogResponse,
    onAddClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                    Text(
                        text = "Suggested: ₹${if (item.suggestedPrice % 1.0 == 0.0) item.suggestedPrice.toInt() else item.suggestedPrice}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = RedPrimary)
                    )
                }
            }

            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProductInventoryRow(
    product: Product,
    onEditClick: () -> Unit,
    onIncreaseStock: () -> Unit,
    onDecreaseStock: () -> Unit
) {
    val (statusText, badgeColor) = when {
        product.stock > 20 -> "In Stock (${product.stock})" to AccentGreen
        product.stock in 6..20 -> "Medium Stock (${product.stock})" to WarningYellow
        product.stock in 1..5 -> "Low Stock (${product.stock})" to ColorLowStock
        else -> "Out of Stock (0)" to ColorOutOfStock
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        text = "Category: ${product.category}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                }

                Text(
                    text = "₹${if (product.price % 1.0 == 0.0) product.price.toInt() else product.price}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = RedPrimary
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Quick Stock Increment / Decrement
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onDecreaseStock,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Text(
                        text = "${product.stock}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = onIncreaseStock,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EditProductStockDialog(
    product: Product,
    availableCategories: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (String, Double, String, Int) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var price by remember { mutableStateOf(if (product.price % 1.0 == 0.0) "${product.price.toInt()}" else "${product.price}") }
    var category by remember { mutableStateOf(product.category) }
    var stock by remember { mutableStateOf(product.stock.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Product", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                SearchableCategoryDropdown(
                    selectedCategory = category,
                    onCategorySelected = { category = it },
                    availableCategories = availableCategories
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (₹)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("Adjust Stock Quantity") },
                    placeholder = { Text("Enter remaining stock") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete Product", tint = Color.Red)
                }
                Button(
                    onClick = {
                        val priceVal = price.toDoubleOrNull() ?: product.price
                        val stockVal = stock.toIntOrNull() ?: product.stock
                        val catVal = if (category.isBlank()) "General" else category.trim()
                        if (name.isNotBlank()) {
                            onSave(name, priceVal, catVal, stockVal)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddProductDialog(
    initialData: MasterCatalogResponse? = null,
    availableCategories: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSearchCatalog: (String, (List<MasterCatalogResponse>) -> Unit) -> Unit,
    onSave: (String, Double, String, Int, String, Int) -> Unit
) {
    var name by remember(initialData) { mutableStateOf(initialData?.name ?: "") }
    var price by remember(initialData) {
        mutableStateOf(
            if (initialData != null) {
                if (initialData.suggestedPrice % 1.0 == 0.0) "${initialData.suggestedPrice.toInt()}"
                else "${initialData.suggestedPrice}"
            } else ""
        )
    }
    var category by remember(initialData) { mutableStateOf(initialData?.category ?: "") }
    var stock by remember(initialData) { mutableStateOf(if (initialData != null) "20" else "50") }
    var barcode by remember(initialData) { mutableStateOf(initialData?.barcode ?: "") }
    var classId by remember { mutableStateOf("0") }
    var catalogSuggestions by remember { mutableStateOf<List<MasterCatalogResponse>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialData != null) "Add Product to Store" else "Add New Product",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.length >= 2) {
                            onSearchCatalog(it) { suggestions -> catalogSuggestions = suggestions }
                        } else {
                            catalogSuggestions = emptyList()
                        }
                    },
                    label = { Text("Product Name") },
                    placeholder = { Text("Type product name...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Suggestions from Master Catalog (only if typing free-form)
                if (initialData == null && catalogSuggestions.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Recommendations:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedPrimary)
                            catalogSuggestions.take(4).forEach { catItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            name = catItem.name
                                            price = if (catItem.suggestedPrice % 1.0 == 0.0) "${catItem.suggestedPrice.toInt()}" else "${catItem.suggestedPrice}"
                                            category = catItem.category
                                            barcode = catItem.barcode ?: ""
                                            catalogSuggestions = emptyList()
                                        }
                                        .padding(vertical = 4.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(catItem.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${catItem.category} • ₹${catItem.suggestedPrice}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Text("Select", fontSize = 12.sp, color = RedPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Initial Stock Quantity (Highlighted for user attention)
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("Stock Quantity (Units in shop)") },
                    placeholder = { Text("Enter current quantity in your shop") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = RedPrimary.copy(alpha = 0.5f)
                    )
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Selling Price (₹)") },
                    placeholder = { Text("Enter selling price") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                SearchableCategoryDropdown(
                    selectedCategory = category,
                    onCategorySelected = { category = it },
                    availableCategories = availableCategories
                )

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val priceVal = price.toDoubleOrNull() ?: 0.0
                    val stockVal = stock.toIntOrNull() ?: 0
                    val classIdVal = classId.toIntOrNull() ?: 0
                    val catVal = if (category.isBlank()) "General" else category.trim()
                    if (name.isNotBlank() && priceVal > 0) {
                        onSave(name, priceVal, catVal, stockVal, barcode, classIdVal)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Text("Save to Inventory")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
