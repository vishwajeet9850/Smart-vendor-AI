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
    val aiStatus: String = "⚡ Smart Auto-Add Active",
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
    val lastAutoAddedTimestamp: Long = 0L,
    val ignoredProductsCount: Int = 0
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

    // Stop Timers / Cooldown Maps
    private val recentlyAddedTimestampMap = ConcurrentHashMap<String, Long>()
    private val ignoredProductsTimestampMap = ConcurrentHashMap<String, Long>()

    private val addedCooldownMs = 3500L     // 3.5 seconds stop timer after adding
    private val ignoredCooldownMs = 8000L   // 8 seconds stop timer after ignoring/undoing

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
            _uiState.update { it.copy(aiStatus = "⚡ Auto-Add Active (YOLOv11)") }
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
                    ignoredProductsTimestampMap.clear()
                    _uiState.update { it.copy(currentBill = newBill) }
                }
            }.onFailure {
                val newBill = Bill(billId = validId, items = emptyList(), subtotal = 0.0, gst = 0.0, grandTotal = 0.0)
                salesRepository.saveBill(newBill)
                _uiState.update { it.copy(currentBill = newBill) }
            }
        }
    }

    fun toggleAutoAdd() {
        _uiState.update {
            val nextState = !it.autoAddEnabled
            it.copy(
                autoAddEnabled = nextState,
                aiStatus = if (nextState) "⚡ Auto-Add Enabled" else "✋ Manual Add Mode"
            )
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
                aiStatus = if (useOcr) "Price & Label Reader Active" else "⚡ Auto-Add Active"
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
                aiStatus = if (useBarcode) "Barcode Scanner Active" else "⚡ Auto-Add Active"
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

            // 3. Smart Auto-Add Hybrid Mode: YOLOv11 Detections
            val yoloResponse = yoloDetector.detectFromImageProxy(imageProxy, confThreshold = 0.50f)
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

                val topDet = detections.first()
                val labelClean = topDet.label.lowercase().trim()
                val now = System.currentTimeMillis()

                val storeProducts = _uiState.value.inventoryProducts
                val matchedProduct = findProductForLabel(labelClean, storeProducts)

                if (matchedProduct != null) {
                    val pId = matchedProduct.id
                    val pNameLower = matchedProduct.name.lowercase()

                    // Check Stop Timers (Added Cooldown & Ignored Cooldown)
                    val lastAddedTime = maxOf(recentlyAddedTimestampMap[pId] ?: 0L, recentlyAddedTimestampMap[pNameLower] ?: 0L)
                    val lastIgnoredTime = maxOf(ignoredProductsTimestampMap[pId] ?: 0L, ignoredProductsTimestampMap[pNameLower] ?: 0L)

                    if (now - lastIgnoredTime < ignoredCooldownMs) {
                        // Product is ignored / paused - do not add or alert
                        _uiState.update {
                            it.copy(
                                activeDetections = overlayDetections,
                                isProcessingFrame = false
                            )
                        }
                        return@launch
                    }

                    if (now - lastAddedTime < addedCooldownMs) {
                        // Product was just added - cooldown active to prevent spam duplicates
                        _uiState.update {
                            it.copy(
                                activeDetections = overlayDetections,
                                isProcessingFrame = false
                            )
                        }
                        return@launch
                    }

                    // Auto-Add or Manual Mode
                    if (_uiState.value.autoAddEnabled) {
                        addDirectlyToBill(matchedProduct)
                        _uiState.update {
                            it.copy(
                                activeDetections = overlayDetections,
                                isProcessingFrame = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                activeDetections = overlayDetections,
                                detectedProduct = matchedProduct,
                                detectedProductsList = listOf(matchedProduct),
                                selectedQuantity = 1,
                                aiStatus = "Found: ${matchedProduct.name}",
                                isProcessingFrame = false
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            activeDetections = overlayDetections,
                            aiStatus = "Detected: ${topDet.label} (${(topDet.confidence * 100).toInt()}%)",
                            isProcessingFrame = false
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isProcessingFrame = false) }
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
                val lastIgnored = ignoredProductsTimestampMap[match.id] ?: 0L

                if (now - lastIgnored >= ignoredCooldownMs && now - lastAdded >= addedCooldownMs) {
                    if (_uiState.value.autoAddEnabled) {
                        addDirectlyToBill(match)
                    } else {
                        _uiState.update {
                            it.copy(
                                detectedProduct = match,
                                detectedProductsList = listOf(match),
                                selectedQuantity = 1,
                                aiStatus = "Found: ${match.name}"
                            )
                        }
                    }
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
                    val lastAdded = maxOf(recentlyAddedTimestampMap[storeMatch.id] ?: 0L, recentlyAddedTimestampMap[storeMatch.name.lowercase()] ?: 0L)
                    val lastIgnored = maxOf(ignoredProductsTimestampMap[storeMatch.id] ?: 0L, ignoredProductsTimestampMap[storeMatch.name.lowercase()] ?: 0L)

                    if (now - lastIgnored < ignoredCooldownMs || now - lastAdded < addedCooldownMs) {
                        continue
                    }

                    if (_uiState.value.autoAddEnabled) {
                        addDirectlyToBill(storeMatch)
                    } else {
                        _uiState.update {
                            it.copy(
                                detectedProduct = storeMatch,
                                detectedProductsList = listOf(storeMatch),
                                selectedQuantity = 1,
                                aiStatus = "Found: ${storeMatch.name}",
                                isProcessingFrame = false
                            )
                        }
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

                        val lastAdded = maxOf(recentlyAddedTimestampMap[targetProduct.id] ?: 0L, recentlyAddedTimestampMap[targetProduct.name.lowercase()] ?: 0L)
                        val lastIgnored = maxOf(ignoredProductsTimestampMap[targetProduct.id] ?: 0L, ignoredProductsTimestampMap[targetProduct.name.lowercase()] ?: 0L)

                        if (now - lastIgnored < ignoredCooldownMs || now - lastAdded < addedCooldownMs) {
                            continue
                        }

                        if (_uiState.value.autoAddEnabled) {
                            addDirectlyToBill(targetProduct)
                        } else {
                            _uiState.update {
                                it.copy(
                                    detectedProduct = targetProduct,
                                    detectedProductsList = listOf(targetProduct),
                                    selectedQuantity = 1,
                                    aiStatus = "Found: ${targetProduct.name}",
                                    isProcessingFrame = false
                                )
                            }
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
                    aiStatus = "⚡ Auto-Added +1 ${product.name} (₹${product.price.toInt()})"
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

    fun cancelDetection() {
        val prod = _uiState.value.detectedProduct
        val now = System.currentTimeMillis()
        if (prod != null) {
            ignoredProductsTimestampMap[prod.id] = now
            ignoredProductsTimestampMap[prod.name.lowercase()] = now
        }
        _uiState.update {
            it.copy(
                detectedProduct = null,
                detectedProductsList = emptyList(),
                activeDetections = emptyList(),
                aiStatus = "Ignored: ${prod?.name ?: "Item"} (paused 8s)",
                ignoredProductsCount = ignoredProductsTimestampMap.size
            )
        }
    }

    fun ignoreProduct(product: Product) {
        val now = System.currentTimeMillis()
        ignoredProductsTimestampMap[product.id] = now
        ignoredProductsTimestampMap[product.name.lowercase()] = now
        _uiState.update {
            it.copy(
                detectedProduct = null,
                detectedProductsList = emptyList(),
                activeDetections = emptyList(),
                aiStatus = "Ignored: ${product.name} (paused 8s)",
                ignoredProductsCount = ignoredProductsTimestampMap.size
            )
        }
    }

    fun resetIgnoredList() {
        ignoredProductsTimestampMap.clear()
        _uiState.update {
            it.copy(
                ignoredProductsCount = 0,
                aiStatus = "Stop Timers Cleared"
            )
        }
    }

    fun undoLastAutoAddedProduct() {
        val lastAdded = _uiState.value.lastAutoAddedProduct ?: return
        val currentBill = _uiState.value.currentBill ?: return
        val now = System.currentTimeMillis()

        // Place on ignored stop timer for 8s so it does not immediately re-auto-add
        ignoredProductsTimestampMap[lastAdded.id] = now
        ignoredProductsTimestampMap[lastAdded.name.lowercase()] = now

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
                        aiStatus = "Undone: Removed ${lastAdded.name} (paused 8s)"
                    )
                }
            }
        }
    }

    fun increaseQuantity() {
        _uiState.update { it.copy(selectedQuantity = it.selectedQuantity + 1) }
    }

    fun decreaseQuantity() {
        _uiState.update { it.copy(selectedQuantity = (it.selectedQuantity - 1).coerceAtLeast(1)) }
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
