package com.smartvendor.ai.ui.screens

import android.content.Context
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.ai.YoloDetectionRepository
import com.smartvendor.ai.barcode.BarcodeScannerManager
import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.model.BillItem
import com.smartvendor.ai.model.DetectionResult
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.network.models.YoloDetectResponse
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
    val aiStatus: String = "Smart AI Scanner Ready",
    val isBarcodeActive: Boolean = false,
    val isOcrActive: Boolean = false,
    val activeDetections: List<DetectionResult> = emptyList(),
    val currentBill: Bill? = null,
    val showManualEntryDialog: Boolean = false,
    val showVoiceDialog: Boolean = false,
    val ocrPrefilledName: String = "",
    val ocrPrefilledPrice: String = "",
    val inventoryProducts: List<Product> = emptyList(),
    val isProcessingFrame: Boolean = false,
    val lastAddedProducts: List<Product> = emptyList(),
    val lastAddedTimestamp: Long = 0L
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

    // 3.5s cooldown per product to avoid double additions of the same item
    private val addedCooldownMs = 3500L
    private val ignoredCooldownMs = 8000L

    private val recentlyAddedTimestampMap = ConcurrentHashMap<String, Long>()
    private val ignoredTimestampMap = ConcurrentHashMap<String, Long>()

    // Confidence threshold for YOLO detection accuracy (0.50 = 50% confidence)
    val confidenceThreshold = 0.50f

    private val labelToProductMap = mapOf(
        "appe_fizz" to Product(id = "PROD_APPE_FIZZ", name = "Appy Fizz Sparkling Apple Drink", price = 20.0, stock = 50, category = "Beverages", barcode = "8902579100018"),
        "haldiram_soya_stick" to Product(id = "PROD_SOYA_STICK", name = "Haldiram Soya Sticks Masala", price = 20.0, stock = 40, category = "Snacks", barcode = "8904063200025"),
        "hide_and_seek" to Product(id = "PROD_HIDE_SEEK", name = "Parle Hide & Seek Chocolate Biscuits", price = 30.0, stock = 60, category = "Biscuits & Snacks", barcode = "8901719104014"),
        "jim_jam" to Product(id = "PROD_JIM_JAM", name = "Britannia Treat Jim Jam Biscuits", price = 35.0, stock = 45, category = "Biscuits & Snacks", barcode = "8901063013217"),
        "maggi" to Product(id = "PROD_MAGGI_2MIN", name = "Maggi 2-Minute Masala Instant Noodles", price = 14.0, stock = 100, category = "Instant Food", barcode = "8901058852302"),
        "nivea_deodorant" to Product(id = "PROD_NIVEA_DEO", name = "Nivea Men Fresh Active Deodorant", price = 199.0, stock = 25, category = "Personal Care", barcode = "4005900135804"),
        "oreo" to Product(id = "PROD_OREO_BISCUIT", name = "Cadbury Oreo Vanilla Cream Biscuits", price = 30.0, stock = 80, category = "Biscuits & Snacks", barcode = "7622201732014"),
        "surf_excel" to Product(id = "PROD_SURF_EXCEL", name = "Surf Excel Easy Wash Detergent Powder", price = 65.0, stock = 35, category = "Household Care", barcode = "8901030386009"),
        "tresemme_shampoo" to Product(id = "PROD_TRESEMME", name = "Tresemme Keratin Smooth Shampoo", price = 120.0, stock = 30, category = "Personal Care", barcode = "8901030700010")
    )

    fun initialize(context: Context, billId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(aiStatus = "Multi-Object AI Scanner Ready") }
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
                    ignoredTimestampMap.clear()
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
                aiStatus = if (useOcr) "Price & Label Reader Active" else "Smart AI Scanner Ready"
            )
        }
    }

    fun toggleBarcodeMode(useBarcode: Boolean) {
        _uiState.update {
            it.copy(
                isBarcodeActive = useBarcode,
                isOcrActive = false,
                activeDetections = emptyList(),
                aiStatus = if (useBarcode) "Barcode Scanner Active" else "Smart AI Scanner Ready"
            )
        }
    }

    private var lastFrameProcessTime: Long = 0L

    fun processFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (_uiState.value.isProcessingFrame || (now - lastFrameProcessTime < 180L)) {
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

            // 3. Smart Hybrid Mode: Runs YOLOv11 Multi-Object Detections directly into bill
            val yoloResponse = yoloDetector.detectFromImageProxy(imageProxy, confThreshold = confidenceThreshold)
            if (yoloResponse != null && yoloResponse.detections.isNotEmpty()) {
                handleYoloDetected(yoloResponse)
            } else {
                _uiState.update { it.copy(isProcessingFrame = false, activeDetections = emptyList()) }
            }
        }
    }

    private fun handleYoloDetected(response: YoloDetectResponse) {
        viewModelScope.launch {
            try {
                val detections = response.detections
                if (detections.isEmpty()) {
                    _uiState.update { it.copy(isProcessingFrame = false, activeDetections = emptyList()) }
                    return@launch
                }

                // Green bounding box overlays for all detected products in scene
                val overlayDetections = detections.map { det ->
                    val bbox = det.bbox
                    val rect = if (bbox.size == 4) {
                        RectF(bbox[0], bbox[1], bbox[2], bbox[3])
                    } else {
                        RectF(0.2f, 0.2f, 0.8f, 0.8f)
                    }
                    DetectionResult(
                        classId = 0,
                        label = det.label,
                        confidence = det.confidence.toFloat(),
                        boundingBox = rect
                    )
                }

                val now = System.currentTimeMillis()
                val storeProducts = _uiState.value.inventoryProducts
                val newProductsToAdd = mutableListOf<Product>()

                // Iterate through ALL detected products in the frame
                for (det in detections) {
                    val labelClean = det.label.lowercase().trim()
                    val matchedProduct = findProductForLabel(labelClean, storeProducts) ?: continue

                    val pId = matchedProduct.id
                    val pNameLower = matchedProduct.name.lowercase()

                    val lastAddedTime = maxOf(recentlyAddedTimestampMap[pId] ?: 0L, recentlyAddedTimestampMap[pNameLower] ?: 0L)
                    val lastIgnoredTime = maxOf(ignoredTimestampMap[pId] ?: 0L, ignoredTimestampMap[pNameLower] ?: 0L)

                    // If NOT on cooldown and NOT ignored, add to this frame's batch
                    if (now - lastIgnoredTime >= ignoredCooldownMs && now - lastAddedTime >= addedCooldownMs) {
                        // Prevent duplicate in same frame iteration
                        if (newProductsToAdd.none { it.id == matchedProduct.id }) {
                            newProductsToAdd.add(matchedProduct)
                        }
                    }
                }

                if (newProductsToAdd.isNotEmpty()) {
                    addMultipleDirectlyToBill(newProductsToAdd, overlayDetections)
                } else {
                    _uiState.update {
                        it.copy(
                            activeDetections = overlayDetections,
                            isProcessingFrame = false
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isProcessingFrame = false) }
            }
        }
    }

    private fun addMultipleDirectlyToBill(products: List<Product>, overlayDetections: List<DetectionResult>) {
        val now = System.currentTimeMillis()
        val currentBillState = _uiState.value.currentBill ?: Bill(billId = "BILL_${System.currentTimeMillis()}")
        val items = currentBillState.items.toMutableList()

        for (prod in products) {
            // Set cooldown timestamp per distinct product
            recentlyAddedTimestampMap[prod.id] = now
            recentlyAddedTimestampMap[prod.name.lowercase()] = now

            val index = items.indexOfFirst { it.productId == prod.id || it.name.equals(prod.name, ignoreCase = true) }
            if (index >= 0) {
                val old = items[index]
                val newQty = old.quantity + 1
                items[index] = old.copy(quantity = newQty, lineTotal = newQty * old.unitPrice)
            } else {
                items.add(
                    BillItem(
                        productId = prod.id,
                        name = prod.name,
                        quantity = 1,
                        unitPrice = prod.price
                    )
                )
            }
        }

        val newSubtotal = items.sumOf { it.quantity * it.unitPrice }
        val newGrandTotal = newSubtotal + (newSubtotal * 0.05) - currentBillState.discount

        val updated = currentBillState.copy(
            items = items,
            subtotal = newSubtotal,
            gst = newSubtotal * 0.05,
            grandTotal = newGrandTotal
        )

        val statusText = if (products.size == 1) {
            "Added +1 ${products[0].name}"
        } else {
            "Added ${products.size} Products (${products.joinToString(", ") { it.name.take(12) }})"
        }

        viewModelScope.launch {
            salesRepository.saveBill(updated)
            _uiState.update {
                it.copy(
                    currentBill = updated,
                    activeDetections = overlayDetections,
                    lastAddedProducts = products,
                    lastAddedTimestamp = now,
                    aiStatus = statusText,
                    isProcessingFrame = false
                )
            }
        }
    }

    private fun findProductForLabel(label: String, storeProducts: List<Product>): Product? {
        val normalized = label.replace("_", " ").lowercase().trim()

        val directMatch = storeProducts.firstOrNull { prod ->
            val pName = prod.name.lowercase()
            pName.contains(normalized) || normalized.contains(pName) ||
                    (label.contains("maggi") && pName.contains("maggi")) ||
                    (label.contains("oreo") && pName.contains("oreo")) ||
                    (label.contains("surf") && pName.contains("surf")) ||
                    (label.contains("appe") && (pName.contains("appe") || pName.contains("appy"))) ||
                    (label.contains("jim") && pName.contains("jim")) ||
                    (label.contains("hide") && pName.contains("hide")) ||
                    (label.contains("soya") && pName.contains("soya")) ||
                    (label.contains("nivea") && pName.contains("nivea")) ||
                    (label.contains("tresemme") && pName.contains("tresemme"))
        }
        if (directMatch != null) return directMatch

        return labelToProductMap[label]
    }

    private fun handleBarcodeDetected(barcode: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val products = _uiState.value.inventoryProducts
            val match = products.firstOrNull { it.barcode == barcode }

            if (match != null) {
                val lastAdded = recentlyAddedTimestampMap[match.id] ?: 0L
                val lastIgnored = ignoredTimestampMap[match.id] ?: 0L

                if (now - lastIgnored >= ignoredCooldownMs && now - lastAdded >= addedCooldownMs) {
                    addMultipleDirectlyToBill(listOf(match), emptyList())
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
                    addMultipleDirectlyToBill(listOf(newProd), emptyList())
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
                    val lastAdded = maxOf(recentlyAddedTimestampMap[storeMatch.id] ?: 0L, recentlyAddedTimestampMap[storeMatch.name.lowercase()] ?: 0L)
                    val lastIgnored = maxOf(ignoredTimestampMap[storeMatch.id] ?: 0L, ignoredTimestampMap[storeMatch.name.lowercase()] ?: 0L)

                    if (now - lastIgnored < ignoredCooldownMs || now - lastAdded < addedCooldownMs) {
                        continue
                    }

                    addMultipleDirectlyToBill(listOf(storeMatch), emptyList())
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

                        val lastAdded = maxOf(recentlyAddedTimestampMap[targetProduct.id] ?: 0L, recentlyAddedTimestampMap[targetProduct.name.lowercase()] ?: 0L)
                        val lastIgnored = maxOf(ignoredTimestampMap[targetProduct.id] ?: 0L, ignoredTimestampMap[targetProduct.name.lowercase()] ?: 0L)

                        if (now - lastIgnored < ignoredCooldownMs || now - lastAdded < addedCooldownMs) {
                            continue
                        }

                        addMultipleDirectlyToBill(listOf(targetProduct), emptyList())
                        return@launch
                    }
                }

                _uiState.update { it.copy(isProcessingFrame = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isProcessingFrame = false) }
            }
        }
    }

    fun undoLastAddedBatch() {
        val lastAddedList = _uiState.value.lastAddedProducts
        if (lastAddedList.isEmpty()) return
        val currentBill = _uiState.value.currentBill ?: return
        val now = System.currentTimeMillis()

        val items = currentBill.items.toMutableList()

        for (lastAdded in lastAddedList) {
            ignoredTimestampMap[lastAdded.id] = now
            ignoredTimestampMap[lastAdded.name.lowercase()] = now

            val index = items.indexOfLast { it.productId == lastAdded.id || it.name.equals(lastAdded.name, ignoreCase = true) }
            if (index >= 0) {
                val item = items[index]
                if (item.quantity > 1) {
                    val newQty = item.quantity - 1
                    items[index] = item.copy(quantity = newQty, lineTotal = newQty * item.unitPrice)
                } else {
                    items.removeAt(index)
                }
            }
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
                    lastAddedProducts = emptyList(),
                    aiStatus = "Undone: Removed items"
                )
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
