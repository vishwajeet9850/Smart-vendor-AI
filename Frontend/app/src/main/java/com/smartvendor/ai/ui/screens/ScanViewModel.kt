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
import com.smartvendor.ai.repository.ProductRepository
import com.smartvendor.ai.repository.ProductRepositoryImpl
import com.smartvendor.ai.repository.SalesRepository
import com.smartvendor.ai.repository.SalesRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val aiStatus: String = "Initializing AI...",
    val isBarcodeActive: Boolean = false,
    val activeDetections: List<DetectionResult> = emptyList(),
    val detectedProduct: Product? = null,
    val selectedQuantity: Int = 1,
    val currentBill: Bill? = null,
    val consecutiveFailedDetections: Int = 0,
    val showManualEntryDialog: Boolean = false,
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

    private var lastDetectedProductId: String? = null
    private var lastDetectedTimestamp: Long = 0L
    private val debounceWindowMs = 3000L // 3-second duplicate detection window

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
        }
    }

    private fun loadBill(billId: String) {
        viewModelScope.launch {
            salesRepository.getBillById(billId).onSuccess { bill ->
                _uiState.update { it.copy(currentBill = bill) }
            }.onFailure {
                // If bill doesn't exist yet, create a new local bill instance
                val newBill = Bill(billId = billId, items = emptyList(), subtotal = 0.0, gst = 0.0, grandTotal = 0.0)
                _uiState.update { it.copy(currentBill = newBill) }
            }
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (_uiState.value.isProcessingFrame || _uiState.value.detectedProduct != null) {
            imageProxy.close()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingFrame = true) }

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

            // YOLO AI Inference
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
        val currentProduct = _uiState.value.detectedProduct ?: return
        val currentQty = _uiState.value.selectedQuantity
        if (currentQty < currentProduct.stock) {
            _uiState.update { it.copy(selectedQuantity = currentQty + 1) }
        }
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
                        aiStatus = "Live Barcode Scanner Active"
                    )
                }
            }.onFailure {
                // Local state fallback if backend call fails
                _uiState.update { state ->
                    state.copy(
                        currentBill = updatedBill,
                        detectedProduct = null,
                        selectedQuantity = 1,
                        showManualEntryDialog = false
                    )
                }
            }
        }
    }

    fun saveManualProduct(name: String, price: Double, stock: Int, category: String, barcode: String) {
        viewModelScope.launch {
            val initialStock = if (stock > 0) stock else 1
            val newProduct = Product(
                name = name,
                price = price,
                category = category,
                barcode = barcode,
                stock = initialStock
            )

            // Save to product catalog first
            productRepository.addProduct(newProduct).onSuccess { id ->
                val createdProduct = newProduct.copy(id = id)
                // Immediately add to bill!
                appendProductToActiveBill(createdProduct, 1)
            }.onFailure {
                // If offline or product save fails, still add item directly to current bill!
                val fallbackProduct = newProduct.copy(id = "MANUAL_${System.currentTimeMillis()}")
                appendProductToActiveBill(fallbackProduct, 1)
            }
        }
    }

    fun openManualEntryDialog() {
        _uiState.update { it.copy(showManualEntryDialog = true) }
    }

    fun dismissManualEntryDialog() {
        _uiState.update { it.copy(showManualEntryDialog = false) }
    }

    fun cancelDetection() {
        _uiState.update {
            it.copy(
                detectedProduct = null,
                activeDetections = emptyList(),
                selectedQuantity = 1,
                aiStatus = "Live Barcode Scanner Active"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        classifier?.close()
        barcodeScanner.close()
    }
}
