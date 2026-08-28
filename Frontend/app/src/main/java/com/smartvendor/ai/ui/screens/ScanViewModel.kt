package com.smartvendor.ai.ui.screens

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.ai.YoloDetectionRepository
import com.smartvendor.ai.barcode.BarcodeScannerManager
import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.model.BillItem
import com.smartvendor.ai.model.DetectionResult
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.ocr.OcrResult
import com.smartvendor.ai.ocr.OcrScannerManager
import com.smartvendor.ai.repository.ProductRepository
import com.smartvendor.ai.repository.ProductRepositoryImpl
import com.smartvendor.ai.repository.SalesRepository
import com.smartvendor.ai.repository.SalesRepositoryImpl
import com.smartvendor.ai.voice.ParsedVoiceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class ScanUiState(
    val aiStatus: String = "Smart Scanner Active",
    val isBarcodeActive: Boolean = false,
    val isOcrActive: Boolean = false,
    val activeDetections: List<DetectionResult> = emptyList(),
    val detectedProduct: Product? = null,
    val detectedProductsList: List<Product> = emptyList(),
    val selectedQuantity: Int = 1,
    val currentBill: Bill? = null,
    val consecutiveFailedDetections: Int = 0,
    val showManualEntryDialog: Boolean = false,
    val showVoiceDialog: Boolean = false,
    val ocrPrefilledName: String = "",
    val ocrPrefilledPrice: String = "",
    val inventoryProducts: List<Product> = emptyList(),
    val errorMessage: String? = null,
    val isProcessingFrame: Boolean = false,
    val autoAddEnabled: Boolean = true,
    val lastAutoAddedProduct: Product? = null,
    val lastAutoAddedTimestamp: Long = 0L
)

class ScanViewModel(
    private val productRepository: ProductRepository = ProductRepositoryImpl(),
    private val salesRepository: SalesRepository = SalesRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val yoloDetector = YoloDetectionRepository()
    private val barcodeScanner = BarcodeScannerManager()
    private val ocrScanner = OcrScannerManager()

    private val dismissedProductIds = ConcurrentHashMap.newKeySet<String>()
    private val recentlyAddedTimestampMap = ConcurrentHashMap<String, Long>()
    private val addedCooldownMs = 4000L

    fun initialize(context: Context, billId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(aiStatus = "Smart Scanner Ready") }
            loadBill(billId)
            observeInventory()
        }
    }

    private fun observeInventory() {
        viewModelScope.launch {
            productRepository.getProductsStream().collect { products ->
                _uiState.update { it.copy(inventoryProducts = products) }
            }
        }
    }

    fun refreshBill() {
        val currentId = _uiState.value.currentBill?.billId
        if (!currentId.isNullOrBlank()) {
            loadBill(currentId)
        }
    }

    private fun loadBill(billId: String) {
        viewModelScope.launch {
            val validId = if (billId.isNotBlank()) billId else "BILL_${System.currentTimeMillis()}"
            salesRepository.getBillById(validId).onSuccess { bill ->
                if (bill != null) {
                    _uiState.update { it.copy(currentBill = bill) }
                } else {
                    val newBill = Bill(billId = validId, items = emptyList(), subtotal = 0.0, gst = 0.0, grandTotal = 0.0)
                    salesRepository.saveBill(newBill)
                    recentlyAddedTimestampMap.clear()
                    dismissedProductIds.clear()
                    _uiState.update { it.copy(currentBill = newBill) }
                }
            }.onFailure {
                val newBill = Bill(billId = validId, items = emptyList(), subtotal = 0.0, gst = 0.0, grandTotal = 0.0)
                salesRepository.saveBill(newBill)
                _uiState.update { it.copy(currentBill = newBill) }
            }
        }
    }

    fun openVoiceDialog() {
        _uiState.update { it.copy(showVoiceDialog = true) }
    }

    fun closeVoiceDialog() {
        _uiState.update { it.copy(showVoiceDialog = false) }
    }

    fun addVoiceItemsToBill(voiceItems: List<ParsedVoiceItem>) {
        val currentBill = _uiState.value.currentBill ?: return
        val currentItems = currentBill.items.toMutableList()

        for (vItem in voiceItems) {
            val prod = vItem.matchedProduct ?: continue
            val existingIndex = currentItems.indexOfFirst { it.productId == prod.id }

            if (existingIndex >= 0) {
                val existing = currentItems[existingIndex]
                val newQty = existing.quantity + vItem.quantity
                currentItems[existingIndex] = existing.copy(
                    quantity = newQty,
                    lineTotal = (newQty * existing.unitPrice) + (((newQty * existing.unitPrice) * existing.gst) / 100.0)
                )
            } else {
                val lineTotal = (vItem.quantity * prod.price) + (((vItem.quantity * prod.price) * prod.gst) / 100.0)
                currentItems.add(
                    BillItem(
                        productId = prod.id,
                        name = prod.name,
                        unitPrice = prod.price,
                        quantity = vItem.quantity,
                        gst = prod.gst,
                        lineTotal = lineTotal
                    )
                )
            }
        }

        val newSubtotal = currentItems.sumOf { it.quantity * it.unitPrice }
        val newGst = currentItems.sumOf { (it.quantity * it.unitPrice * it.gst) / 100.0 }
        val newGrandTotal = (newSubtotal + newGst - currentBill.discount).coerceAtLeast(0.0)

        val updated = currentBill.copy(
            items = currentItems,
            subtotal = newSubtotal,
            gst = newGst,
            grandTotal = newGrandTotal
        )
        _uiState.update { it.copy(currentBill = updated) }
        viewModelScope.launch {
            salesRepository.saveBill(updated)
        }
    }

    fun toggleScanMode(useOcr: Boolean) {
        _uiState.update {
            it.copy(
                isOcrActive = useOcr,
                isBarcodeActive = false,
                activeDetections = emptyList(),
                detectedProduct = null,
                detectedProductsList = emptyList(),
                aiStatus = if (useOcr) "Price & Label Reader Active" else "Smart Scanner Active"
            )
        }
    }

    fun toggleBarcodeMode(useBarcode: Boolean) {
        _uiState.update {
            it.copy(
                isBarcodeActive = useBarcode,
                isOcrActive = false,
                activeDetections = emptyList(),
                detectedProduct = null,
                detectedProductsList = emptyList(),
                aiStatus = if (useBarcode) "Barcode Scanner Active" else "Smart Scanner Active"
            )
        }
    }

    private var lastFrameProcessTime: Long = 0L

    fun processFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (_uiState.value.isProcessingFrame || (now - lastFrameProcessTime < 200L)) {
            imageProxy.close()
            return
        }
        lastFrameProcessTime = now

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingFrame = true) }

            // 1. OCR Label & Price Reader Mode
            if (_uiState.value.isOcrActive) {
                ocrScanner.processImage(
                    imageProxy = imageProxy,
                    onSuccess = { ocrResult -> handleOcrDetected(ocrResult) },
                    onNotFound = { _uiState.update { it.copy(isProcessingFrame = false) } },
                    onError = { _uiState.update { it.copy(isProcessingFrame = false) } }
                )
                return@launch
            }

            // 2. Barcode Mode
            if (_uiState.value.isBarcodeActive) {
                barcodeScanner.scanImage(
                    imageProxy = imageProxy,
                    onSuccess = { barcode -> handleBarcodeDetected(barcode) },
                    onNotFound = { _uiState.update { it.copy(isProcessingFrame = false) } },
                    onError = { _uiState.update { it.copy(isProcessingFrame = false) } }
                )
                return@launch
            }

            // 3. Smart Hybrid Mode
            ocrScanner.processImage(
                imageProxy = imageProxy,
                onSuccess = { ocrResult ->
                    handleOcrDetected(ocrResult)
                },
                onNotFound = {
                    viewModelScope.launch {
                        _uiState.update { it.copy(isProcessingFrame = false) }
                    }
                },
                onError = {
                    _uiState.update { it.copy(isProcessingFrame = false) }
                }
            )
        }
    }

    private fun handleBarcodeDetected(barcode: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val products = _uiState.value.inventoryProducts
            val match = products.firstOrNull { it.barcode == barcode }

            if (match != null) {
                val lastAdded = recentlyAddedTimestampMap[match.id] ?: 0L
                if (now - lastAdded >= addedCooldownMs) {
                    addDirectlyToBill(match)
                }
            } else {
                val catMatches = productRepository.searchMasterCatalog(barcode).getOrDefault(emptyList())
                if (catMatches.isNotEmpty()) {
                    val catItem = catMatches.first()
                    val newProd = Product(
                        id = catItem.id,
                        name = catItem.name,
                        price = catItem.suggestedPrice,
                        category = catItem.category,
                        barcode = catItem.barcode ?: barcode,
                        stock = 30
                    )
                    addDirectlyToBill(newProd)
                } else {
                    _uiState.update {
                        it.copy(
                            ocrPrefilledName = "Product ($barcode)",
                            showManualEntryDialog = true,
                            isProcessingFrame = false
                        )
                    }
                }
            }
            _uiState.update { it.copy(isProcessingFrame = false) }
        }
    }

    private fun handleOcrDetected(ocrResult: OcrResult) {
        viewModelScope.launch {
            try {
                val products = _uiState.value.inventoryProducts
                val now = System.currentTimeMillis()

                val rankedInventoryMatches = ocrScanner.findRankedInventoryMatches(
                    ocrResult = ocrResult,
                    inventoryProducts = products,
                    threshold = 0.35f
                )

                for (storeMatch in rankedInventoryMatches) {
                    val lastAdded = maxOf(
                        recentlyAddedTimestampMap[storeMatch.id] ?: 0L,
                        recentlyAddedTimestampMap[storeMatch.name.lowercase()] ?: 0L
                    )

                    if (now - lastAdded < addedCooldownMs || dismissedProductIds.contains(storeMatch.id)) {
                        continue
                    }

                    _uiState.update {
                        it.copy(
                            detectedProduct = storeMatch,
                            detectedProductsList = listOf(storeMatch),
                            selectedQuantity = 1,
                            aiStatus = "Found: ${storeMatch.name}",
                            isProcessingFrame = false
                        )
                    }
                    return@launch
                }

                val keywords: List<String> = ocrResult.dominantBrandKeywords.filter { it.length >= 3 }
                for (kw in keywords.take(3)) {
                    val catResults = productRepository.searchMasterCatalog(kw).getOrDefault(emptyList())
                    val rankedCatalogMatches = ocrScanner.findRankedCatalogMatches(
                        ocrResult = ocrResult,
                        catalogItems = catResults,
                        threshold = 0.45f
                    )

                    for (catItem in rankedCatalogMatches) {
                        val targetProduct = Product(
                            id = catItem.id,
                            name = catItem.name,
                            price = catItem.suggestedPrice,
                            stock = 30,
                            category = catItem.category,
                            barcode = catItem.barcode ?: ""
                        )

                        val lastAdded = maxOf(
                            recentlyAddedTimestampMap[targetProduct.id] ?: 0L,
                            recentlyAddedTimestampMap[targetProduct.name.lowercase()] ?: 0L
                        )

                        if (now - lastAdded < addedCooldownMs || dismissedProductIds.contains(targetProduct.id)) {
                            continue
                        }

                        _uiState.update {
                            it.copy(
                                detectedProduct = targetProduct,
                                detectedProductsList = listOf(targetProduct),
                                selectedQuantity = 1,
                                aiStatus = "Found Catalog: ${targetProduct.name}",
                                isProcessingFrame = false
                            )
                        }
                        return@launch
                    }
                }

                _uiState.update { it.copy(isProcessingFrame = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isProcessingFrame = false) }
            }
        }
    }

    private fun addDirectlyToBill(product: Product) {
        val now = System.currentTimeMillis()
        recentlyAddedTimestampMap[product.id] = now
        recentlyAddedTimestampMap[product.name.lowercase()] = now

        val currentBillState = _uiState.value.currentBill ?: Bill(billId = "BILL_${System.currentTimeMillis()}")
        val items = currentBillState.items.toMutableList()
        val index = items.indexOfFirst { it.productId == product.id || it.name.equals(product.name, ignoreCase = true) }

        if (index >= 0) {
            val old = items[index]
            val newQty = old.quantity + 1
            items[index] = old.copy(quantity = newQty, lineTotal = newQty * old.unitPrice)
        } else {
            items.add(
                BillItem(
                    productId = product.id,
                    name = product.name,
                    quantity = 1,
                    unitPrice = product.price
                )
            )
        }

        val newSubtotal = items.sumOf { it.quantity * it.unitPrice }
        val newGrandTotal = newSubtotal + (newSubtotal * 0.05) - currentBillState.discount

        val updated = currentBillState.copy(
            items = items,
            subtotal = newSubtotal,
            gst = newSubtotal * 0.05,
            grandTotal = newGrandTotal
        )

        viewModelScope.launch {
            salesRepository.saveBill(updated)
            _uiState.update {
                it.copy(
                    currentBill = updated,
                    lastAutoAddedProduct = product,
                    lastAutoAddedTimestamp = now,
                    aiStatus = "Added +1 ${product.name}"
                )
            }
        }
    }

    fun addProductToBill() {
        val prod = _uiState.value.detectedProduct ?: return
        val qty = _uiState.value.selectedQuantity
        val now = System.currentTimeMillis()
        recentlyAddedTimestampMap[prod.id] = now
        recentlyAddedTimestampMap[prod.name.lowercase()] = now

        val currentBillState = _uiState.value.currentBill ?: Bill(billId = "BILL_${System.currentTimeMillis()}")
        val items = currentBillState.items.toMutableList()
        val index = items.indexOfFirst { it.productId == prod.id || it.name.equals(prod.name, ignoreCase = true) }

        if (index >= 0) {
            val old = items[index]
            val newQty = old.quantity + qty
            items[index] = old.copy(quantity = newQty, lineTotal = newQty * old.unitPrice)
        } else {
            items.add(
                BillItem(
                    productId = prod.id,
                    name = prod.name,
                    quantity = qty,
                    unitPrice = prod.price
                )
            )
        }

        val newSubtotal = items.sumOf { it.quantity * it.unitPrice }
        val newGrandTotal = newSubtotal + (newSubtotal * 0.05) - currentBillState.discount

        val updated = currentBillState.copy(
            items = items,
            subtotal = newSubtotal,
            gst = newSubtotal * 0.05,
            grandTotal = newGrandTotal
        )

        viewModelScope.launch {
            salesRepository.saveBill(updated)
            _uiState.update {
                it.copy(
                    currentBill = updated,
                    detectedProduct = null,
                    detectedProductsList = emptyList(),
                    lastAutoAddedProduct = prod,
                    lastAutoAddedTimestamp = now,
                    aiStatus = "Added +$qty ${prod.name}"
                )
            }
        }
    }

    fun addAllDetectedProductsToBill() {
        addProductToBill()
    }

    fun selectDetectedProduct(product: Product) {
        _uiState.update { it.copy(detectedProduct = product) }
    }

    fun removeDetectedProductFromList(product: Product) {
        val updated = _uiState.value.detectedProductsList.filterNot { it.id == product.id }
        _uiState.update {
            it.copy(
                detectedProductsList = updated,
                detectedProduct = updated.firstOrNull()
            )
        }
    }

    fun increaseQuantity() {
        _uiState.update { it.copy(selectedQuantity = it.selectedQuantity + 1) }
    }

    fun decreaseQuantity() {
        _uiState.update { it.copy(selectedQuantity = (it.selectedQuantity - 1).coerceAtLeast(1)) }
    }

    fun cancelDetection() {
        val prod = _uiState.value.detectedProduct
        if (prod != null) {
            dismissedProductIds.add(prod.id)
            dismissedProductIds.add(prod.name.lowercase())
        }
        _uiState.update {
            it.copy(
                detectedProduct = null,
                detectedProductsList = emptyList(),
                activeDetections = emptyList()
            )
        }
    }

    fun undoLastAutoAddedProduct() {
        val lastAdded = _uiState.value.lastAutoAddedProduct ?: return
        val currentBill = _uiState.value.currentBill ?: return
        val items = currentBill.items.toMutableList()
        val index = items.indexOfLast { it.productId == lastAdded.id || it.name.equals(lastAdded.name, ignoreCase = true) }

        if (index >= 0) {
            val item = items[index]
            if (item.quantity > 1) {
                val newQty = item.quantity - 1
                items[index] = item.copy(quantity = newQty, lineTotal = newQty * item.unitPrice)
            } else {
                items.removeAt(index)
            }

            val newSubtotal = items.sumOf { it.quantity * it.unitPrice }
            val newGrandTotal = newSubtotal + (newSubtotal * 0.05) - currentBill.discount

            val updated = currentBill.copy(
                items = items,
                subtotal = newSubtotal,
                gst = newSubtotal * 0.05,
                grandTotal = newGrandTotal
            )

            viewModelScope.launch {
                salesRepository.saveBill(updated)
                _uiState.update {
                    it.copy(
                        currentBill = updated,
                        lastAutoAddedProduct = null,
                        aiStatus = "Undone: Removed ${lastAdded.name}"
                    )
                }
            }
        }
    }

    fun openManualEntryDialog() {
        _uiState.update { it.copy(showManualEntryDialog = true) }
    }

    fun closeManualEntryDialog() {
        _uiState.update { it.copy(showManualEntryDialog = false, ocrPrefilledName = "", ocrPrefilledPrice = "") }
    }

    fun addManualProductToBill(name: String, price: Double, quantity: Int) {
        val currentBillState = _uiState.value.currentBill ?: Bill(billId = "BILL_${System.currentTimeMillis()}")
        val items = currentBillState.items.toMutableList()

        items.add(
            BillItem(
                productId = "CUSTOM_${System.currentTimeMillis()}",
                name = name,
                quantity = quantity,
                unitPrice = price
            )
        )

        val newSubtotal = items.sumOf { it.quantity * it.unitPrice }
        val newGrandTotal = newSubtotal + (newSubtotal * 0.05) - currentBillState.discount

        val updated = currentBillState.copy(
            items = items,
            subtotal = newSubtotal,
            gst = newSubtotal * 0.05,
            grandTotal = newGrandTotal
        )

        viewModelScope.launch {
            salesRepository.saveBill(updated)
            _uiState.update {
                it.copy(
                    currentBill = updated,
                    showManualEntryDialog = false,
                    ocrPrefilledName = "",
                    ocrPrefilledPrice = "",
                    aiStatus = "Added $quantity x $name"
                )
            }
        }
    }
}
