package com.smartvendor.ai.ocr

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartvendor.ai.model.Product
import java.util.Locale

data class OcrResult(
    val productName: String,
    val quantityUnit: String? = null,
    val fullCombinedName: String,
    val price: Double? = null,
    val matchScore: Float = 0.0f
)

class OcrScannerManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val priceRegex = Regex(
        """(?:₹|MRP|Rs\.?|INR)\s*[:\.]?\s*(\d+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val standalonePriceRegex = Regex("""\b(\d{1,4}(?:\.\d{1,2})?)\b""")

    private val quantityUnitRegex = Regex(
        """\b(\d+(?:\.\d+)?\s*(?:kg|g|gm|l|ml|ltr|litre|pack|pc|pcs|pouch|sachet|g|kg))\b""",
        RegexOption.IGNORE_CASE
    )

    @OptIn(ExperimentalGetImage::class)
    fun processImage(
        imageProxy: ImageProxy,
        onSuccess: (OcrResult) -> Unit,
        onNotFound: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onNotFound()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                imageProxy.close()

                val fullText = visionText.text
                if (fullText.isBlank()) {
                    onNotFound()
                    return@addOnSuccessListener
                }

                var detectedPrice: Double? = null
                var detectedName: String? = null
                var detectedUnit: String? = null

                // 1. Extract Price
                val priceMatch = priceRegex.find(fullText)
                if (priceMatch != null) {
                    val priceStr = priceMatch.groupValues[1]
                    detectedPrice = priceStr.toDoubleOrNull()
                }

                // 2. Extract Quantity/Unit (e.g. 1kg, 500ml, 1L)
                val unitMatch = quantityUnitRegex.find(fullText)
                if (unitMatch != null) {
                    detectedUnit = unitMatch.groupValues[1].uppercase(Locale.getDefault())
                }

                // 3. Extract Clean Product Name Lines
                val lines = visionText.textBlocks.flatMap { it.lines }
                val nameCandidates = lines
                    .map { it.text.trim() }
                    .filter { line ->
                        line.length in 3..40 &&
                                !priceRegex.containsMatchIn(line) &&
                                !line.contains("NET WT", ignoreCase = true) &&
                                !line.contains("MFG", ignoreCase = true) &&
                                !line.contains("EXP", ignoreCase = true) &&
                                !line.contains("BATCH", ignoreCase = true) &&
                                !line.contains("PACK", ignoreCase = true) &&
                                !line.contains("INGREDIENTS", ignoreCase = true)
                    }

                if (nameCandidates.isNotEmpty()) {
                    detectedName = nameCandidates.first()
                        .lowercase(Locale.getDefault())
                        .split(" ")
                        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                }

                // Fallback Price detection
                if (detectedPrice == null) {
                    lines.forEach { line ->
                        if (line.text.contains("₹") || line.text.contains("Rs", ignoreCase = true)) {
                            val match = standalonePriceRegex.find(line.text)
                            if (match != null) {
                                detectedPrice = match.groupValues[1].toDoubleOrNull()
                            }
                        }
                    }
                }

                if (!detectedName.isNullOrBlank()) {
                    val combinedName = if (!detectedUnit.isNullOrBlank() && !detectedName!!.contains(detectedUnit!!, ignoreCase = true)) {
                        "$detectedName $detectedUnit"
                    } else {
                        detectedName!!
                    }

                    onSuccess(
                        OcrResult(
                            productName = detectedName!!,
                            quantityUnit = detectedUnit,
                            fullCombinedName = combinedName,
                            price = detectedPrice
                        )
                    )
                } else {
                    onNotFound()
                }
            }
            .addOnFailureListener { e ->
                imageProxy.close()
                Log.e(TAG, "OCR recognition error", e)
                onError(e)
            }
    }

    /**
     * Fuzzy Product Matcher Engine
     * Evaluates OCR extracted result against store inventory using an 80%+ threshold requirement.
     * Compares both Product Name and Quantity/Unit.
     */
    fun findBestInventoryMatch(
        ocrResult: OcrResult,
        inventoryProducts: List<Product>,
        threshold: Float = 0.80f
    ): Product? {
        if (inventoryProducts.isEmpty()) return null

        var bestProduct: Product? = null
        var highestScore = 0.0f

        val scannedNameLower = ocrResult.fullCombinedName.lowercase(Locale.getDefault())
        val scannedTokens = scannedNameLower.split(Regex("""\s+""")).filter { it.length > 1 }

        for (product in inventoryProducts) {
            val catalogNameLower = product.name.lowercase(Locale.getDefault())
            val catalogTokens = catalogNameLower.split(Regex("""\s+""")).filter { it.length > 1 }

            if (catalogTokens.isEmpty() || scannedTokens.isEmpty()) continue

            // 1. Token Overlap Score
            val matchingTokens = scannedTokens.count { token ->
                catalogTokens.any { catToken -> catToken.contains(token) || token.contains(catToken) }
            }

            var tokenScore = matchingTokens.toFloat() / maxOf(scannedTokens.size, catalogTokens.size).toFloat()

            // 2. Exact match boost
            if (scannedNameLower == catalogNameLower) {
                tokenScore = 1.0f
            }

            // 3. Unit Check: If unit specified in OCR (e.g. 1kg vs 500g), enforce unit match
            if (!ocrResult.quantityUnit.isNullOrBlank()) {
                val scannedUnit = ocrResult.quantityUnit.lowercase()
                val catalogHasUnit = catalogNameLower.contains(scannedUnit)
                if (!catalogHasUnit) {
                    // Reduce score if quantity unit doesn't match!
                    tokenScore *= 0.60f
                } else {
                    tokenScore = minOf(1.0f, tokenScore + 0.15f)
                }
            }

            if (tokenScore > highestScore) {
                highestScore = tokenScore
                bestProduct = product
            }
        }

        return if (highestScore >= threshold) bestProduct else null
    }

    fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing OCR recognizer", e)
        }
    }

    companion object {
        private const val TAG = "OcrScannerManager"
    }
}
