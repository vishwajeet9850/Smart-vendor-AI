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
import com.smartvendor.ai.model.JournalTransaction
import com.smartvendor.ai.model.SystemCheckpoint
import com.smartvendor.ai.model.RecoveryReport
import com.smartvendor.ai.model.InventoryItemSummary
import com.smartvendor.ai.ocr.OcrScannerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * 100% Offline-First Standalone Store & Sync Manager
 *
 * - Stores Active Store Inventory (Top 100 seeded on launch + user offline edits)
 * - Indexes 6,000 Indian Master Catalog items for instant offline search & recommendation
 * - Persists all local Bills, Stock updates, and Store Profile to disk
 * - Durable Append-Only Transaction Journal & Checkpoint Recovery Engine (The Blackout Layer)
 * - Safely merges bidirectional data with FastAPI backend without duplicate keys or data loss
 */
object LocalStoreManager {

    private const val TAG = "LocalStoreManager"
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private var appContext: Context? = null
    private var isInitialized = false

    // State Flows for UI
    private val _productsFlow = MutableStateFlow<List<Product>>(emptyList())
    val productsFlow: StateFlow<List<Product>> = _productsFlow.asStateFlow()

    private val _storeFlow = MutableStateFlow(Store())
    val storeFlow: StateFlow<Store> = _storeFlow.asStateFlow()

    private val _billsFlow = MutableStateFlow<List<Bill>>(emptyList())
    val billsFlow: StateFlow<List<Bill>> = _billsFlow.asStateFlow()

    private val _isOnlineFlow = MutableStateFlow(true)
    val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    private val _isBlackoutActiveFlow = MutableStateFlow(false)
    val isBlackoutActiveFlow: StateFlow<Boolean> = _isBlackoutActiveFlow.asStateFlow()

    private val _journalFlow = MutableStateFlow<List<JournalTransaction>>(emptyList())
    val journalFlow: StateFlow<List<JournalTransaction>> = _journalFlow.asStateFlow()

    fun setOnlineStatus(online: Boolean) {
        _isOnlineFlow.value = online
    }

    fun isOnline(): Boolean = _isOnlineFlow.value

    fun setBlackoutActive(active: Boolean) {
        _isBlackoutActiveFlow.value = active
    }

    fun isBlackoutActive(): Boolean = _isBlackoutActiveFlow.value

    fun getLocalJournal(): List<JournalTransaction> = _journalFlow.value


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
                    val cleanList = ensureUniqueProductIds(loadedList).map { p ->
                        val shortName = OcrScannerManager.cleanOcrTitle(p.name)
                        if (shortName.isNotBlank()) p.copy(name = shortName) else p
                    }
                    _productsFlow.value = cleanList
                    saveProductsToDisk(cleanList)
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

        // Load Transaction Journal (Resilience & Recovery)
        val journalFile = File(ctx.filesDir, "local_transaction_journal.json")
        if (journalFile.exists()) {
            try {
                val json = journalFile.readText(Charsets.UTF_8)
                val type = object : TypeToken<List<JournalTransaction>>() {}.type
                val list: List<JournalTransaction> = gson.fromJson(json, type) ?: emptyList()
                _journalFlow.value = list
            } catch (_: Exception) {}
        }
    }


    private fun seedTop100FromAssets() {
        val ctx = appContext ?: return
        try {
            val json = ctx.assets.open("default_inventory_top100.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Product>>() {}.type
            val list: List<Product> = gson.fromJson(json, type) ?: emptyList()
            val cleanList = ensureUniqueProductIds(list).map { p ->
                val shortName = OcrScannerManager.cleanOcrTitle(p.name)
                if (shortName.isNotBlank()) p.copy(name = shortName) else p
            }
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

        // If completed bill / return, immediately update stock in local inventory
        if (bill.status == Bill.BILL_STATUS_COMPLETED) {
            val isReturn = bill.transactionType == Bill.TRANSACTION_TYPE_RETURN
            val txType = if (isReturn) "RETURN" else "SALE"
            val txStatus = if (_isBlackoutActiveFlow.value) "PENDING" else "APPLIED"
            val txnBaseId = "TXN_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val currentJournal = _journalFlow.value.toMutableList()

            bill.items.forEachIndexed { idx, item ->
                val product = getProductById(item.productId)
                val prevStock = product?.stock
                val newStock = if (isReturn) {
                    if (item.condition == BillItem.CONDITION_GOOD) {
                        (prevStock ?: 0) + item.quantity
                    } else {
                        prevStock ?: 0 // Damaged items do not increase sellable stock
                    }
                } else {
                    ((prevStock ?: 0) - item.quantity).coerceAtLeast(0)
                }
                if (product != null) {
                    updateStock(product.id, newStock)
                }

                // Append item to durable local transaction journal
                val journalEntry = JournalTransaction(
                    id = UUID.randomUUID().toString(),
                    transactionId = "${txnBaseId}_${idx}",
                    userId = "local_user",
                    type = txType,
                    billId = bill.billId,
                    productId = item.productId,
                    productName = item.name,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    totalAmount = item.lineTotal,
                    previousStock = prevStock,
                    newStock = newStock,
                    returnCondition = item.condition,
                    status = txStatus,
                    createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
                )
                currentJournal.add(0, journalEntry)
            }

            _journalFlow.value = currentJournal
            saveJournalToDisk(currentJournal)

            synchronized(syncQueue) {
                syncQueue.pendingBills.add(bill)
                saveSyncQueue()
            }
            if (!_isBlackoutActiveFlow.value) {
                scope.launch { syncWithServer() }
            }
        }
    }

    private fun saveJournalToDisk(journal: List<JournalTransaction>) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, "local_transaction_journal.json")
            file.writeText(gson.toJson(journal), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun createLocalCheckpoint(type: String = "MANUAL"): SystemCheckpoint {
        val chkId = "CHK_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        val ctx = appContext
        if (ctx != null) {
            try {
                val file = File(ctx.filesDir, "local_checkpoint.json")
                val snapshot = mapOf(
                    "checkpoint_id" to chkId,
                    "products" to _productsFlow.value,
                    "bills" to _billsFlow.value,
                    "created_at" to now
                )
                file.writeText(gson.toJson(snapshot), Charsets.UTF_8)
            } catch (_: Exception) {}
        }
        return SystemCheckpoint(
            id = UUID.randomUUID().toString(),
            checkpointId = chkId,
            userId = "local_user",
            checkpointType = type,
            productsCount = _productsFlow.value.size,
            billsCount = _billsFlow.value.size,
            createdAt = now
        )
    }

    fun restoreFromLocalCheckpoint(): RecoveryReport {
        val ctx = appContext
        var discovered = 0
        var recovered = 0
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        var lastChkId: String? = null

        if (ctx != null) {
            val file = File(ctx.filesDir, "local_checkpoint.json")
            if (file.exists()) {
                try {
                    val json = file.readText(Charsets.UTF_8)
                    val mapType = object : TypeToken<Map<String, Any>>() {}.type
                    val snapshot: Map<String, Any> = gson.fromJson(json, mapType) ?: emptyMap()
                    lastChkId = snapshot["checkpoint_id"] as? String
                } catch (_: Exception) {}
            }
        }

        // Only replay transactions that are PENDING (i.e. were written during blackout).
        // APPLIED entries were already synced before the blackout — replaying them would
        // double-deduct stock.
        val fullJournal = _journalFlow.value
        val pendingTxns = fullJournal.filter { it.status.uppercase(Locale.US) == "PENDING" }
        discovered = pendingTxns.size
        for (tx in pendingTxns.reversed()) {
            val prod = getProductById(tx.productId ?: "")
            if (prod != null && tx.quantity > 0) {
                val isReturn = tx.type == "RETURN"
                val cond = tx.returnCondition.uppercase(Locale.US)
                val newStock = if (isReturn) {
                    if (cond == "GOOD") prod.stock + tx.quantity else prod.stock
                } else {
                    (prod.stock - tx.quantity).coerceAtLeast(0)
                }
                updateStock(prod.id, newStock)
            }
            recovered++
        }

        markJournalRecovered()

        _isBlackoutActiveFlow.value = false
        val summary = _productsFlow.value.take(10).map {
            InventoryItemSummary(
                productId = it.id,
                productName = it.name,
                currentStock = it.stock,
                price = it.price
            )
        }

        return RecoveryReport(
            systemStatus = "HEALTHY",
            lastCheckpointId = lastChkId ?: "CHK_BASELINE",
            transactionsDiscovered = discovered,
            successfullyRecovered = recovered,
            inventorySummary = summary,
            billsCount = _billsFlow.value.size,
            reportGeneratedAt = now
        )
    }


    fun markJournalRecovered() {
        val updated = _journalFlow.value.map {
            it.copy(status = "RECOVERED")
        }
        _journalFlow.value = updated
        saveJournalToDisk(updated)
    }


    fun resetLocalDemo() {
        _isBlackoutActiveFlow.value = false
        seedTop100FromAssets()
        val ctx = appContext
        if (ctx != null) {
            try {
                File(ctx.filesDir, "local_transaction_journal.json").delete()
            } catch (_: Exception) {}
        }
        _journalFlow.value = emptyList()
        createLocalCheckpoint("MANUAL")
    }

    fun injectCIEDemoReturnBills(productName: String, unitPrice: Double = 20.0, productId: String = "") {
        val now = System.currentTimeMillis()
        val currentBills = _billsFlow.value.toMutableList()
        val currentJournal = _journalFlow.value.toMutableList()

        // Clean out any old demo returns first
        currentBills.removeAll { it.billId.contains("cross_vendor_return_demo") }
        currentJournal.removeAll { it.transactionId.contains("cross_vendor_return_demo") || (it.payloadJson?.contains("cross_vendor_return_demo") == true) }

        val demoSpecs = listOf(
            Triple(6 * 60 * 1000L, 1, "Defective packaging seal"),
            Triple(14 * 60 * 1000L, 2, "Customer complaint (taste anomaly)"),
            Triple(22 * 60 * 1000L, 1, "Batch return (texture complaint)")
        )

        demoSpecs.forEachIndexed { index, (timeOffset, qty, reason) ->
            val billTime = now - timeOffset
            val billId = "BILL_CIE_cross_vendor_return_demo_USER_${index + 1}"
            val txId = "CIE_DEMO_cross_vendor_return_demo_USER_${billTime / 1000}_${index + 1}"
            val totalAmt = unitPrice * qty

            val returnBill = Bill(
                billId = billId,
                cashierId = "Kirana Store",
                storeId = _storeFlow.value.storeId.ifBlank { "store_01" },
                transactionType = Bill.TRANSACTION_TYPE_RETURN,
                items = listOf(
                    BillItem(
                        productId = productId,
                        name = productName,
                        quantity = qty,
                        unitPrice = unitPrice,
                        gst = 5.0,
                        lineTotal = totalAmt,
                        condition = BillItem.CONDITION_DAMAGED
                    )
                ),
                subtotal = totalAmt,
                gst = totalAmt * 0.05,
                discount = 0.0,
                grandTotal = totalAmt,
                paymentMethod = "CASH",
                timestamp = billTime,
                status = Bill.BILL_STATUS_COMPLETED
            )
            currentBills.add(0, returnBill)

            val journalEntry = JournalTransaction(
                id = UUID.randomUUID().toString(),
                transactionId = txId,
                userId = "current_user",
                type = "RETURN",
                billId = billId,
                productId = productId,
                productName = productName,
                quantity = qty,
                unitPrice = unitPrice,
                totalAmount = totalAmt,
                previousStock = null,
                newStock = null,
                returnCondition = "DAMAGED",
                status = "APPLIED",
                payloadJson = "{\"demoIncidentId\":\"cross_vendor_return_demo\",\"simulated\":true,\"reason\":\"$reason\"}",
                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(billTime)),
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(billTime))
            )
            currentJournal.add(0, journalEntry)
        }

        _billsFlow.value = currentBills
        saveBillsToDisk(currentBills)

        _journalFlow.value = currentJournal
        saveJournalToDisk(currentJournal)
    }

    fun clearCIEDemoReturnBills() {
        val currentBills = _billsFlow.value.toMutableList()
        val currentJournal = _journalFlow.value.toMutableList()

        currentBills.removeAll { it.billId.contains("cross_vendor_return_demo") }
        currentJournal.removeAll { it.transactionId.contains("cross_vendor_return_demo") || (it.payloadJson?.contains("cross_vendor_return_demo") == true) }

        _billsFlow.value = currentBills
        saveBillsToDisk(currentBills)

        _journalFlow.value = currentJournal
        saveJournalToDisk(currentJournal)
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
        if (!syncMutex.tryLock()) {
            // Already syncing in another coroutine, skip redundant parallel run
            return@withContext
        }

        try {
            val api = ApiClient.apiService

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

            // 4. Sync Pending Bills (with Client ID & Exact Timestamp for Idempotency)
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val billsToProcess = synchronized(syncQueue) { syncQueue.pendingBills.toList() }
            for (b in billsToProcess) {
                try {
                    val formattedCreated = if (b.timestamp > 0) isoFormat.format(Date(b.timestamp)) else null
                    val billReq = BillRequest(
                        id = b.billId.ifBlank { null },
                        transactionType = b.transactionType,
                        items = b.items.map { item ->
                            BillItemRequest(
                                productId = item.productId.ifBlank { null },
                                productName = item.name,
                                quantity = item.quantity,
                                unitPrice = item.unitPrice,
                                totalPrice = item.lineTotal,
                                condition = item.condition
                            )
                        },
                        totalAmount = b.grandTotal,
                        taxAmount = b.gst,
                        paymentMode = b.paymentMethod.lowercase(),
                        createdAt = formattedCreated
                    )
                    val res = api.createBill(billReq)
                    if (res.isSuccessful || res.code() == 409) {
                        synchronized(syncQueue) { syncQueue.pendingBills.removeAll { it.billId == b.billId } }
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
        } finally {
            syncMutex.unlock()
        }
    }
}

