package com.smartvendor.ai.ui.screens

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.ai.TFLiteClassifier
import com.smartvendor.ai.ai.YoloUtils
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val aiStatus: String = "Initializing AI...",
    val isBarcodeActive: Boolean = false,
    val isOcrActive: Boolean = false,
    val activeDetections: List<DetectionResult> = emptyList(),
    val detectedProduct: Product? = null,
    val selectedQuantity: Int = 1,
    val currentBill: Bill? = null,
    val consecutiveFailedDetections: Int = 0,
    val showManualEntryDialog: Boolean = false,
    val ocrPrefilledName: String = "",
    val ocrPrefilledPrice: String = "",
    val inventoryProducts: List<Product> = emptyList(),
    val errorMessage: String? = null,
    val isProcessingFrame: Boolean = false
)

class ScanViewModel(
    private val productRepository: ProductRepository = ProductRepositoryImpl(),
    private val salesRepository: SalesRepository = SalesRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var classifier: TFLiteClassifier? = null
    private val barcodeScanner = BarcodeScannerManager()
    private val ocrScanner = OcrScannerManager()

    private var lastDetectedProductId: String? = null
    private var lastDetectedTimestamp: Long = 0L
    private val debounceWindowMs = 3000L

    fun initialize(context: Context, billId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(aiStatus = "Loading AI Model...") }
            classifier = TFLiteClassifier(context)
            val result = classifier?.initialize()
            if (result?.isSuccess == true) {
                _uiState.update { it.copy(aiStatus = "AI Object Detection Active") }
            } else {
                _uiState.update { it.copy(aiStatus = "Live Barcode Scanner Active", isBarcodeActive = true) }
            }

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

    private fun loadBill(billId: String) {
        viewModelScope.launch {
            val validId = if (billId.isNotBlank()) billId else "BILL_${System.currentTimeMillis()}"
            salesRepository.getBillById(validId).onSuccess { bill ->
                if (bill != null) {
                    _uiState.update { it.copy(currentBill = bill) }
                } else {
                    val newBill = Bill(billId = validId, items = emptyList(), subtotal = 0.0, gst = 0.0, grandTotal = 0.0)
                    salesRepository.saveBill(newBill)
                    _uiState.update { it.copy(currentBill = newBill) }
                }
            }.onFailure {
                val newBill = Bill(billId = validId, items = emptyList(), subtotal = 0.0, gst = 0.0, grandTotal = 0.0)
                salesRepository.saveBill(newBill)
                _uiState.update { it.copy(currentBill = newBill) }
            }
        }
    }

    fun toggleScanMode(useOcr: Boolean) {
        _uiState.update {
            it.copy(
                isOcrActive = useOcr,
                isBarcodeActive = !useOcr,
                aiStatus = if (useOcr) "📝 Live Label & Price OCR Active" else "📷 Live Barcode Scanner Active"
            )
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (_uiState.value.isProcessingFrame || _uiState.value.detectedProduct != null) {
            imageProxy.close()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingFrame = true) }

            // 1. OCR Label & Price Scanning Mode
            if (_uiState.value.isOcrActive) {
                ocrScanner.processImage(
                    imageProxy = imageProxy,
                    onSuccess = { ocrResult ->
                        handleOcrDetected(ocrResult)
                    },
                    onNotFound = {
                        _uiState.update { it.copy(isProcessingFrame = false) }
                    },
                    onError = {
                        _uiState.update { it.copy(isProcessingFrame = false) }
                    }
                )
                return@launch
            }

            // 2. Barcode Scanner Mode
            if (_uiState.value.isBarcodeActive) {
                barcodeScanner.scanImage(
                    imageProxy = imageProxy,
                    onSuccess = { barcode ->
                        handleBarcodeDetected(barcode)
                    },
                    onNotFound = {
                        _uiState.update { it.copy(isProcessingFrame = false) }
                    },
                    onError = {
                        _uiState.update { it.copy(isProcessingFrame = false) }
                    }
                )
                return@launch
            }

            // 3. YOLO AI Object Detection Mode
            val bitmap = YoloUtils.imageProxyToBitmap(imageProxy)
            imageProxy.close()

            if (bitmap == null) {
                _uiState.update { it.copy(isProcessingFrame = false) }
                return@launch
            }

            val detections = classifier?.detect(bitmap) ?: emptyList()
            _uiState.update { it.copy(activeDetections = detections) }

            if (detections.isNotEmpty()) {
                val highestConfidenceDetection = detections.first()
                if (highestConfidenceDetection.confidence >= 0.80f) {
                    _uiState.update { it.copy(consecutiveFailedDetections = 0) }
                    fetchProductByClassId(highestConfidenceDetection.classId)
                } else {
                    handleLowConfidence()
                }
            } else {
                handleLowConfidence()
            }
        }
    }

    private fun handleOcrDetected(ocrResult: OcrResult) {
        viewModelScope.launch {
            try {
                val products = _uiState.value.inventoryProducts
                // Use 80%+ threshold Fuzzy Matcher linked with Product Name & Quantity/Unit!
                val matchingProduct = ocrScanner.findBestInventoryMatch(
                    ocrResult = ocrResult,
                    inventoryProducts = products,
                    threshold = 0.80f
                )

                if (matchingProduct != null) {
                    // Match found in inventory (>= 80% score)! Select product and display detected card for instant add
                    _uiState.update {
                        it.copy(
                            detectedProduct = matchingProduct,
                            selectedQuantity = 1,
                            aiStatus = "Product Matched (${matchingProduct.name})",
                            isProcessingFrame = false
                        )
                    }
                } else {
                    // Match < 80% (New Variant or New Product) -> Show manual entry dialog asking for Stock!
                    _uiState.update {
                        it.copy(
                            ocrPrefilledName = ocrResult.fullCombinedName,
                            ocrPrefilledPrice = ocrResult.price?.toString() ?: "",
                            showManualEntryDialog = true,
                            aiStatus = "New Product Variant Detected — Set Stock",
                            isProcessingFrame = false
                        )
                    }
                }
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(
                        ocrPrefilledName = ocrResult.fullCombinedName,
                        ocrPrefilledPrice = ocrResult.price?.toString() ?: "",
                        showManualEntryDialog = true,
                        isProcessingFrame = false
                    )
                }
            }
        }
    }

    private fun handleLowConfidence() {
        val currentFailed = _uiState.value.consecutiveFailedDetections + 1
        if (currentFailed >= 2) {
            _uiState.update {
                it.copy(
                    isBarcodeActive = true,
                    aiStatus = "Live Barcode Scanner Active",
                    consecutiveFailedDetections = currentFailed,
                    isProcessingFrame = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    consecutiveFailedDetections = currentFailed,
                    isProcessingFrame = false
                )
            }
        }
    }

    private suspend fun fetchProductByClassId(classId: Int) {
        productRepository.getProductByClassId(classId).onSuccess { product ->
            if (product != null) {
                val now = System.currentTimeMillis()
                if (product.id == lastDetectedProductId && (now - lastDetectedTimestamp) < debounceWindowMs) {
                    _uiState.update { it.copy(isProcessingFrame = false) }
                    return@onSuccess
                }

                lastDetectedProductId = product.id
                lastDetectedTimestamp = now

                _uiState.update {
                    it.copy(
                        detectedProduct = product,
                        selectedQuantity = 1,
                        isProcessingFrame = false
                    )
                }
            } else {
                handleLowConfidence()
            }
        }.onFailure {
            handleLowConfidence()
        }
    }

    private fun handleBarcodeDetected(barcode: String) {
        viewModelScope.launch {
            productRepository.getProductByBarcode(barcode).onSuccess { product ->
                if (product != null) {
                    _uiState.update {
                        it.copy(
                            detectedProduct = product,
                            selectedQuantity = 1,
                            isBarcodeActive = false,
                            aiStatus = "Product Found via Barcode",
                            isProcessingFrame = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            errorMessage = "Barcode ($barcode) not found in store catalog.",
                            showManualEntryDialog = true,
                            isProcessingFrame = false
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        errorMessage = "Barcode query error: ${err.message}",
                        isProcessingFrame = false
                    )
                }
            }
        }
    }

    fun increaseQuantity() {
        val currentQty = _uiState.value.selectedQuantity
        _uiState.update { it.copy(selectedQuantity = currentQty + 1) }
    }

    fun decreaseQuantity() {
        val currentQty = _uiState.value.selectedQuantity
        if (currentQty > 1) {
            _uiState.update { it.copy(selectedQuantity = currentQty - 1) }
        }
    }

    fun addProductToBill() {
        val product = _uiState.value.detectedProduct ?: return
        val qty = _uiState.value.selectedQuantity
        appendProductToActiveBill(product, qty)
    }

    fun addExistingProductToBill(product: Product, quantity: Int) {
        appendProductToActiveBill(product, quantity)
        _uiState.update {
            it.copy(
                showManualEntryDialog = false,
                ocrPrefilledName = "",
                ocrPrefilledPrice = ""
            )
        }
    }

    private fun appendProductToActiveBill(product: Product, qty: Int) {
        val currentBillState = _uiState.value.currentBill ?: Bill(
            billId = "BILL_${System.currentTimeMillis()}",
            items = emptyList()
        )

        viewModelScope.launch {
            val existingItems = currentBillState.items.toMutableList()
            val existingIndex = existingItems.indexOfFirst { it.productId == product.id }

            if (existingIndex >= 0) {
                val oldItem = existingItems[existingIndex]
                val newQty = oldItem.quantity + qty
                existingItems[existingIndex] = oldItem.copy(
                    quantity = newQty,
                    lineTotal = (newQty * oldItem.unitPrice) + (((newQty * oldItem.unitPrice) * oldItem.gst) / 100.0)
                )
            } else {
                existingItems.add(
                    BillItem(
                        productId = product.id,
                        name = product.name,
                        quantity = qty,
                        unitPrice = product.price,
                        gst = product.gst
                    )
                )
            }

            val newSubtotal = existingItems.sumOf { it.quantity * it.unitPrice }
            val newGst = existingItems.sumOf { (it.quantity * it.unitPrice * it.gst) / 100.0 }
            val newGrandTotal = newSubtotal + newGst - currentBillState.discount

            val updatedBill = currentBillState.copy(
                items = existingItems,
                subtotal = newSubtotal,
                gst = newGst,
                grandTotal = newGrandTotal
            )

            salesRepository.saveBill(updatedBill).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        currentBill = updatedBill,
                        detectedProduct = null,
                        selectedQuantity = 1,
                        activeDetections = emptyList(),
                        showManualEntryDialog = false,
                        ocrPrefilledName = "",
                        ocrPrefilledPrice = "",
                        aiStatus = "Live Scanner Active"
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        currentBill = updatedBill,
                        detectedProduct = null,
                        selectedQuantity = 1,
                        showManualEntryDialog = false,
                        ocrPrefilledName = "",
                        ocrPrefilledPrice = ""
                    )
                }
            }
        }
    }

    fun saveManualProduct(name: String, price: Double, stock: Int, category: String, barcode: String, quantity: Int = 1) {
        viewModelScope.launch {
            val matchedProduct = _uiState.value.inventoryProducts.firstOrNull {
                it.name.equals(name, ignoreCase = true) || (barcode.isNotBlank() && it.barcode == barcode)
            }

            if (matchedProduct != null) {
                appendProductToActiveBill(matchedProduct, quantity)
                _uiState.update {
                    it.copy(
                        showManualEntryDialog = false,
                        ocrPrefilledName = "",
                        ocrPrefilledPrice = ""
                    )
                }
                return@launch
            }

            val initialStock = if (stock > 0) stock else 10
            val newProduct = Product(
                name = name,
                price = price,
                category = category,
                barcode = barcode,
                stock = initialStock
            )

            productRepository.addProduct(newProduct).onSuccess { id ->
                val createdProduct = newProduct.copy(id = id)
                appendProductToActiveBill(createdProduct, quantity)
            }.onFailure {
                val fallbackProduct = newProduct.copy(id = "MANUAL_${System.currentTimeMillis()}")
                appendProductToActiveBill(fallbackProduct, quantity)
            }
        }
    }

    fun openManualEntryDialog() {
        _uiState.update { it.copy(showManualEntryDialog = true) }
    }

    fun dismissManualEntryDialog() {
        _uiState.update {
            it.copy(
                showManualEntryDialog = false,
                ocrPrefilledName = "",
                ocrPrefilledPrice = ""
            )
        }
    }

    fun cancelDetection() {
        _uiState.update {
            it.copy(
                detectedProduct = null,
                activeDetections = emptyList(),
                selectedQuantity = 1,
                aiStatus = "Live Scanner Active"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        classifier?.close()
        barcodeScanner.close()
        ocrScanner.close()
    }
}
