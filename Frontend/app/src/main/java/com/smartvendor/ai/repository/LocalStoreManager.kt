package com.smartvendor.ai.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.model.BillItem
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.model.Store
import com.smartvendor.ai.network.ApiClient
import com.smartvendor.ai.network.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * 100% Offline-First Standalone Store & Sync Manager
 *
 * - Stores Active Store Inventory (Top 100 seeded on launch + user offline edits)
 * - Indexes 6,000 Indian Master Catalog items for instant offline search & recommendation
 * - Persists all local Bills, Stock updates, and Store Profile to disk
 * - Safely merges bidirectional data with FastAPI backend without duplicate keys or data loss
 */
object LocalStoreManager {

    private const val TAG = "LocalStoreManager"
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var appContext: Context? = null
    private var isInitialized = false

    // State flows for real-time UI updates
    private val _productsFlow = MutableStateFlow<List<Product>>(emptyList())
    val productsFlow: StateFlow<List<Product>> = _productsFlow.asStateFlow()

    private val _storeFlow = MutableStateFlow(Store())
    val storeFlow: StateFlow<Store> = _storeFlow.asStateFlow()

    private val _billsFlow = MutableStateFlow<List<Bill>>(emptyList())
    val billsFlow: StateFlow<List<Bill>> = _billsFlow.asStateFlow()

    // In-memory master catalog (6,000 reference products)
    private var masterCatalogList: List<MasterCatalogResponse> = emptyList()

    // Pending Sync Queues (persisted to disk)
    data class SyncQueue(
        val pendingStockUpdates: MutableMap<String, Int> = mutableMapOf(),
        val pendingProductUpserts: MutableList<Product> = mutableListOf(),
        val pendingProductDeletions: MutableList<String> = mutableListOf(),
        val pendingBills: MutableList<Bill> = mutableListOf(),
        var pendingStore: Store? = null
    )

    private var syncQueue = SyncQueue()

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext

        scope.launch {
            loadInitialData()
            loadMasterCatalog()
            loadSyncQueue()
            isInitialized = true

            // Trigger background sync with server if live
            syncWithServer()
        }
    }

    // -------------------------------------------------------------------------
    // 1. Local Data Loading & Persistence
    // -------------------------------------------------------------------------

    private fun loadInitialData() {
        val ctx = appContext ?: return
        val productsFile = File(ctx.filesDir, "local_products.json")

        if (productsFile.exists()) {
            try {
                val json = productsFile.readText(Charsets.UTF_8)
                val type = object : TypeToken<List<Product>>() {}.type
                val loadedList: List<Product> = gson.fromJson(json, type) ?: emptyList()
                if (loadedList.isNotEmpty()) {
                    val cleanList = ensureUniqueProductIds(loadedList)
                    _productsFlow.value = cleanList
                    Log.d(TAG, "Loaded ${cleanList.size} products from local storage.")
                } else {
                    seedTop100FromAssets()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading local products, seeding defaults", e)
                seedTop100FromAssets()
            }
        } else {
            seedTop100FromAssets()
        }

        // Load Store Info
        val storeFile = File(ctx.filesDir, "local_store.json")
        if (storeFile.exists()) {
            try {
                val json = storeFile.readText(Charsets.UTF_8)
                val s: Store = gson.fromJson(json, Store::class.java) ?: Store()
                _storeFlow.value = s
            } catch (_: Exception) {}
        }

        // Load Bills History
        val billsFile = File(ctx.filesDir, "local_bills.json")
        if (billsFile.exists()) {
            try {
                val json = billsFile.readText(Charsets.UTF_8)
                val type = object : TypeToken<List<Bill>>() {}.type
                val list: List<Bill> = gson.fromJson(json, type) ?: emptyList()
                _billsFlow.value = list
            } catch (_: Exception) {}
        }
    }

    private fun seedTop100FromAssets() {
        val ctx = appContext ?: return
        try {
            val json = ctx.assets.open("default_inventory_top100.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Product>>() {}.type
            val list: List<Product> = gson.fromJson(json, type) ?: emptyList()
            val cleanList = ensureUniqueProductIds(list)
            _productsFlow.value = cleanList
            saveProductsToDisk(cleanList)
            Log.d(TAG, "Successfully seeded Top ${cleanList.size} Kirana store inventory from assets.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading default_inventory_top100.json", e)
        }
    }

    private fun ensureUniqueProductIds(list: List<Product>): List<Product> {
        val seenIds = HashSet<String>()
        val result = ArrayList<Product>(list.size)

        for (p in list) {
            val uniqueId = if (p.id.isBlank() || seenIds.contains(p.id)) {
                "PROD_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            } else {
                p.id
            }
            seenIds.add(uniqueId)
            result.add(p.copy(id = uniqueId))
        }
        return result
    }

    private fun loadMasterCatalog() {
        val ctx = appContext ?: return
        try {
            val json = ctx.assets.open("master_catalog.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<MasterCatalogItemJson>>() {}.type
            val rawList: List<MasterCatalogItemJson> = gson.fromJson(json, type) ?: emptyList()

            masterCatalogList = rawList.map {
                MasterCatalogResponse(
                    id = it.id,
                    name = it.name,
                    category = it.category,
                    suggestedPrice = it.mrp,
                    barcode = it.barcode
                )
            }
            Log.d(TAG, "Indexed ${masterCatalogList.size} master reference items in offline catalog.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading master catalog assets", e)
        }
    }

    private data class MasterCatalogItemJson(
        val id: String = "",
        val name: String = "",
        val brand: String = "",
        val category: String = "",
        val mrp: Double = 0.0,
        val gst: Int = 0,
        val barcode: String = ""
    )

    private fun saveProductsToDisk(list: List<Product>) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, "local_products.json")
            file.writeText(gson.toJson(list), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving products to disk", e)
        }
    }

    private fun saveBillsToDisk(list: List<Bill>) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, "local_bills.json")
            file.writeText(gson.toJson(list), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bills to disk", e)
        }
    }

    private fun saveStoreToDisk(store: Store) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, "local_store.json")
            file.writeText(gson.toJson(store), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving store info to disk", e)
        }
    }

    private fun loadSyncQueue() {
        val ctx = appContext ?: return
        val file = File(ctx.filesDir, "local_sync_queue.json")
        if (file.exists()) {
            try {
                val json = file.readText(Charsets.UTF_8)
                syncQueue = gson.fromJson(json, SyncQueue::class.java) ?: SyncQueue()
            } catch (_: Exception) {}
        }
    }

    private fun saveSyncQueue() {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, "local_sync_queue.json")
            file.writeText(gson.toJson(syncQueue), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // 2. Product Operations (Instant Offline + Sync Enqueue)
    // -------------------------------------------------------------------------

    fun getProducts(): List<Product> = _productsFlow.value

    fun getProductById(productId: String): Product? {
        return _productsFlow.value.find { it.id == productId }
    }

    fun getProductByBarcode(barcode: String): Product? {
        if (barcode.isBlank()) return null
        return _productsFlow.value.find { it.barcode.equals(barcode.trim(), ignoreCase = true) }
    }

    fun getProductByClassId(classId: Int): Product? {
        return _productsFlow.value.getOrNull(classId)
    }

    fun updateStock(productId: String, newStock: Int) {
        val currentList = _productsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == productId }
        if (index >= 0) {
            val updated = currentList[index].copy(stock = newStock.coerceAtLeast(0))
            currentList[index] = updated
            _productsFlow.value = currentList
            saveProductsToDisk(currentList)

            // Queue for server sync
            synchronized(syncQueue) {
                syncQueue.pendingStockUpdates[productId] = updated.stock
                saveSyncQueue()
            }
            scope.launch { syncWithServer() }
        }
    }

    fun addProduct(product: Product): String {
        val newId = if (product.id.isBlank()) "PROD_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}" else product.id
        val newProduct = product.copy(id = newId)

        val currentList = _productsFlow.value.toMutableList()
        val existingIndex = currentList.indexOfFirst {
            it.id == newId || (newProduct.barcode.isNotBlank() && it.barcode == newProduct.barcode)
        }
        if (existingIndex >= 0) {
            currentList[existingIndex] = newProduct
        } else {
            currentList.add(0, newProduct) // Add to top of inventory
        }
        val cleanList = ensureUniqueProductIds(currentList)
        _productsFlow.value = cleanList
        saveProductsToDisk(cleanList)

        synchronized(syncQueue) {
            syncQueue.pendingProductUpserts.add(newProduct)
            saveSyncQueue()
        }
        scope.launch { syncWithServer() }
        return newId
    }

    fun updateProduct(product: Product) {
        val currentList = _productsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == product.id }
        if (index >= 0) {
            currentList[index] = product
            val cleanList = ensureUniqueProductIds(currentList)
            _productsFlow.value = cleanList
            saveProductsToDisk(cleanList)

            synchronized(syncQueue) {
                syncQueue.pendingProductUpserts.add(product)
                saveSyncQueue()
            }
            scope.launch { syncWithServer() }
        }
    }

    fun deleteProduct(productId: String) {
        val currentList = _productsFlow.value.toMutableList()
        currentList.removeAll { it.id == productId }
        _productsFlow.value = currentList
        saveProductsToDisk(currentList)

        synchronized(syncQueue) {
            syncQueue.pendingProductDeletions.add(productId)
            saveSyncQueue()
        }
        scope.launch { syncWithServer() }
    }

    // -------------------------------------------------------------------------
    // 3. Offline Search & Catalog Recommendations (Instant)
    // -------------------------------------------------------------------------

    fun searchMasterCatalog(query: String, limit: Int = 30): List<MasterCatalogResponse> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isBlank()) return emptyList()

        val tokens = cleanQuery.split(" ").filter { it.isNotBlank() }

        return masterCatalogList.asSequence()
            .filter { item ->
                val nameLower = item.name.lowercase()
                val catLower = item.category.lowercase()
                val barcode = item.barcode ?: ""
                tokens.all { t -> nameLower.contains(t) || catLower.contains(t) || barcode.contains(t) }
            }
            .take(limit)
            .toList()
    }

    // -------------------------------------------------------------------------
    // 4. Billing Operations (Local Session + Stock Deduction)
    // -------------------------------------------------------------------------

    fun getBills(): List<Bill> = _billsFlow.value

    fun getBillById(billId: String): Bill? {
        return _billsFlow.value.find { it.billId == billId }
    }

    fun saveBill(bill: Bill) {
        val currentBills = _billsFlow.value.toMutableList()
        val index = currentBills.indexOfFirst { it.billId == bill.billId }
        if (index >= 0) {
            currentBills[index] = bill
        } else {
            currentBills.add(0, bill)
        }
        _billsFlow.value = currentBills
        saveBillsToDisk(currentBills)

        // If completed bill, immediately deduct stock in local inventory
        if (bill.status == Bill.BILL_STATUS_COMPLETED) {
            bill.items.forEach { item ->
                val product = getProductById(item.productId)
                if (product != null) {
                    val newStock = (product.stock - item.quantity).coerceAtLeast(0)
                    updateStock(product.id, newStock)
                }
            }

            synchronized(syncQueue) {
                syncQueue.pendingBills.add(bill)
                saveSyncQueue()
            }
            scope.launch { syncWithServer() }
        }
    }

    // -------------------------------------------------------------------------
    // 5. Store Profile Operations
    // -------------------------------------------------------------------------

    fun getStoreInfo(): Store = _storeFlow.value

    fun saveStoreInfo(store: Store) {
        _storeFlow.value = store
        saveStoreToDisk(store)

        synchronized(syncQueue) {
            syncQueue.pendingStore = store
            saveSyncQueue()
        }
        scope.launch { syncWithServer() }
    }

    // -------------------------------------------------------------------------
    // 6. Safe Bidirectional Sync Engine (NEVER OVERWRITES OFFLINE PRODUCTS)
    // -------------------------------------------------------------------------

    suspend fun syncWithServer() = withContext(Dispatchers.IO) {
        val api = ApiClient.apiService

        try {
            // 1. Sync Pending Product Deletions
            val deletionsToProcess = synchronized(syncQueue) { syncQueue.pendingProductDeletions.toList() }
            for (prodId in deletionsToProcess) {
                try {
                    val res = api.deleteProduct(prodId)
                    if (res.isSuccessful || res.code() == 404) {
                        synchronized(syncQueue) { syncQueue.pendingProductDeletions.remove(prodId) }
                    }
                } catch (_: Exception) {}
            }

            // 2. Sync Pending Product Upserts
            val upsertsToProcess = synchronized(syncQueue) { syncQueue.pendingProductUpserts.toList() }
            for (p in upsertsToProcess) {
                try {
                    val req = ProductRequest(
                        name = p.name,
                        barcode = p.barcode.ifBlank { null },
                        category = p.category,
                        price = p.price,
                        stock = p.stock,
                        lowStockThreshold = p.lowStockThreshold,
                        imageUrl = p.imageUrl.ifBlank { null }
                    )
                    val res = api.createProduct(req)
                    if (res.isSuccessful) {
                        val body = res.body()
                        if (body != null) {
                            val currentList = _productsFlow.value.toMutableList()
                            val idx = currentList.indexOfFirst { it.id == p.id || (p.barcode.isNotBlank() && it.barcode == p.barcode) }
                            if (idx >= 0) {
                                currentList[idx] = currentList[idx].copy(id = body.id)
                                val cleanList = ensureUniqueProductIds(currentList)
                                _productsFlow.value = cleanList
                                saveProductsToDisk(cleanList)
                            }
                        }
                        synchronized(syncQueue) { syncQueue.pendingProductUpserts.remove(p) }
                    }
                } catch (_: Exception) {}
            }

            // 3. Sync Pending Stock Updates
            val stockUpdates = synchronized(syncQueue) { syncQueue.pendingStockUpdates.toMap() }
            for ((prodId, targetStock) in stockUpdates) {
                try {
                    val res = api.updateStock(prodId, StockUpdateRequest(targetStock))
                    if (res.isSuccessful) {
                        synchronized(syncQueue) { syncQueue.pendingStockUpdates.remove(prodId) }
                    }
                } catch (_: Exception) {}
            }

            // 4. Sync Pending Bills
            val billsToProcess = synchronized(syncQueue) { syncQueue.pendingBills.toList() }
            for (b in billsToProcess) {
                try {
                    val billReq = BillRequest(
                        items = b.items.map { item ->
                            BillItemRequest(
                                productId = item.productId.ifBlank { null },
                                productName = item.name,
                                quantity = item.quantity,
                                unitPrice = item.unitPrice,
                                totalPrice = item.lineTotal
                            )
                        },
                        totalAmount = b.grandTotal,
                        taxAmount = b.gst,
                        paymentMode = b.paymentMethod.lowercase()
                    )
                    val res = api.createBill(billReq)
                    if (res.isSuccessful) {
                        synchronized(syncQueue) { syncQueue.pendingBills.remove(b) }
                    }
                } catch (_: Exception) {}
            }

            // 5. Sync Pending Store Info
            val storeToProcess = synchronized(syncQueue) { syncQueue.pendingStore }
            if (storeToProcess != null) {
                try {
                    val storeReq = StoreProfileRequest(
                        name = storeToProcess.name,
                        address = storeToProcess.address,
                        phone = storeToProcess.phone,
                        gst = storeToProcess.gst,
                        upi = storeToProcess.upi
                    )
                    val res = api.updateStoreProfile(storeReq)
                    if (res.isSuccessful) {
                        synchronized(syncQueue) { syncQueue.pendingStore = null }
                    }
                } catch (_: Exception) {}
            }

            saveSyncQueue()

            // 6. Safe Bidirectional Merge of Products with Unique ID Assurance
            try {
                val serverProductsRes = api.getProducts()
                if (serverProductsRes.isSuccessful && serverProductsRes.body() != null) {
                    val serverList = serverProductsRes.body()!!.map { resp ->
                        Product(
                            id = resp.id,
                            name = resp.name,
                            barcode = resp.barcode ?: "",
                            category = resp.category,
                            price = resp.price,
                            stock = resp.stock,
                            lowStockThreshold = resp.lowStockThreshold,
                            imageUrl = resp.imageUrl ?: ""
                        )
                    }

                    val merged = _productsFlow.value.toMutableList()

                    for (sprod in serverList) {
                        val localIdx = merged.indexOfFirst {
                            it.id == sprod.id ||
                            (!it.barcode.isNullOrBlank() && it.barcode == sprod.barcode) ||
                            it.name.equals(sprod.name, ignoreCase = true)
                        }
                        if (localIdx >= 0) {
                            val localItem = merged[localIdx]
                            merged[localIdx] = localItem.copy(id = sprod.id)
                        } else {
                            merged.add(sprod)
                        }
                    }

                    val cleanMerged = ensureUniqueProductIds(merged)
                    _productsFlow.value = cleanMerged
                    saveProductsToDisk(cleanMerged)
                    Log.d(TAG, "Merged inventory cleanly: Total ${cleanMerged.size} products preserved.")
                }
            } catch (_: Exception) {}

        } catch (e: Exception) {
            Log.d(TAG, "Server offline or unreachable, continuing in standalone offline mode.")
        }
    }
}
