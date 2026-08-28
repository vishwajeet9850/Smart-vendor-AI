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
    val dominantBrandKeywords: List<String> = emptyList(),
    val topBrandTitle: String = "",
    val fullScannedText: String = "",
    val detectedPrice: Double? = null,
    val quantityUnit: String? = null
)

class OcrScannerManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val TAG = "OcrScannerManager"

    private val finePrintNoise = setOf(
        "net", "wt", "mfg", "exp", "batch", "pack", "ingredients", "made", "india",
        "mrp", "incl", "taxes", "tax", "customer", "care", "lic", "iso", "store",
        "cool", "dry", "place", "recyclable", "use", "best", "before", "date",
        "weight", "grams", "kilograms", "quantity", "address", "marketed",
        "manufactured", "ltd", "pvt", "corp", "inc", "product", "details", "contact",
        "nutrition", "nutritional", "facts", "information", "per", "serve", "serving",
        "size", "energy", "protein", "carbohydrate", "sugar", "fat", "saturated",
        "vegetarian", "veg", "green", "dot", "fssai", "license", "reg", "tm",
        "copyright", "all", "rights", "reserved", "keep", "away", "direct", "sunlight",
        "hygienic", "conditions", "dispose", "dustbin", "scan", "qr", "feedback",
        "helpline", "toll", "free", "email", "website", "www", "com", "in",
        "super", "saver", "offer", "inside", "new", "improved", "taste", "delicious",
        "preservative", "acidity", "regulator", "emulsifier", "stabilizer", "flavour",
        "warning", "caution", "safety", "seal", "contain", "contains", "added", "synthetic",
        "code", "barcode", "label", "price", "only", "amount", "total", "rupees", "rs"
    )

    private val priceRegex = Regex(
        """(?:₹|MRP|Rs\.?|INR)\s*[:\.]?\s*(\d+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val standalonePriceRegex = Regex("""(\d{1,4}(?:\.\d{1,2})?)""")

    private val quantityUnitRegex = Regex(
        """(\d+(?:\.\d+)?\s*(?:kg|g|gm|l|ml|ltr|litre|pack|pc|pcs|pouch|sachet))""",
        RegexOption.IGNORE_CASE
    )

    @OptIn(ExperimentalGetImage::class)
    fun processImage(
        imageProxy: ImageProxy,
        onSuccess: (OcrResult) -> Unit,
        onNotFound: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            try { imageProxy.close() } catch (_: Exception) {}
            onNotFound()
            return
        }

        try {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    try {
                        val fullText = visionText.text.trim()
                        if (fullText.isBlank()) {
                            onNotFound()
                            return@addOnSuccessListener
                        }

                        val allLines = visionText.textBlocks.flatMap { it.lines }
                        if (allLines.isEmpty()) {
                            onNotFound()
                            return@addOnSuccessListener
                        }

                        // 1. Extract Price & Unit
                        var detectedPrice: Double? = null
                        val priceMatch = priceRegex.find(fullText)
                        if (priceMatch != null) {
                            detectedPrice = priceMatch.groupValues[1].toDoubleOrNull()
                        }

                        var detectedUnit: String? = null
                        val unitMatch = quantityUnitRegex.find(fullText)
                        if (unitMatch != null) {
                            detectedUnit = unitMatch.groupValues[1].uppercase(Locale.getDefault())
                        }

                        if (detectedPrice == null) {
                            for (line in allLines) {
                                val lText = line.text ?: ""
                                if (lText.contains("₹") || lText.contains("Rs", ignoreCase = true) || lText.contains("MRP", ignoreCase = true)) {
                                    val match = standalonePriceRegex.find(lText)
                                    if (match != null) {
                                        val p = match.groupValues[1].toDoubleOrNull()
                                        if (p != null && p in 1.0..9999.0) {
                                            detectedPrice = p
                                            break
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Extract brand words and top title cleanly
                        val validLines = allLines.filter { line ->
                            val text = line.text?.trim() ?: ""
                            text.length in 2..50 && !priceRegex.containsMatchIn(text)
                        }

                        val brandWords = fullText.lowercase(Locale.getDefault())
                            .split(Regex("""[\s\-_,.:;/\]+"""))
                            .filter { it.length >= 3 && it !in finePrintNoise }

                        val topTitle = if (validLines.isNotEmpty()) {
                            validLines.maxByOrNull {
                                (it.boundingBox?.width() ?: 0) * (it.boundingBox?.height() ?: 0)
                            }?.text?.trim() ?: ""
                        } else {
                            ""
                        }

                        onSuccess(
                            OcrResult(
                                dominantBrandKeywords = brandWords,
                                topBrandTitle = topTitle,
                                fullScannedText = fullText,
                                detectedPrice = detectedPrice,
                                quantityUnit = detectedUnit
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in OCR callback", e)
                        onNotFound()
                    } finally {
                        try { imageProxy.close() } catch (_: Exception) {}
                    }
                }
                .addOnFailureListener { e ->
                    try { imageProxy.close() } catch (_: Exception) {}
                    Log.e(TAG, "OCR Recognition error", e)
                    onError(e)
                }
        } catch (e: Exception) {
            try { imageProxy.close() } catch (_: Exception) {}
            Log.e(TAG, "Failed starting OCR image processing", e)
            onError(e)
        }
    }

    /**
     * Highly sensitive keyword & token matching against Store Inventory
     */
    fun findRankedInventoryMatches(
        ocrResult: OcrResult,
        inventoryProducts: List<Product>,
        threshold: Float = 0.25f
    ): List<Product> {
        if (inventoryProducts.isEmpty()) return emptyList()

        val fullTextLower = ocrResult.fullScannedText.lowercase(Locale.getDefault())
        val brandTokens = ocrResult.dominantBrandKeywords.toSet()

        val matchedProducts = mutableListOf<Pair<Product, Float>>()

        for (product in inventoryProducts) {
            val pNameLower = product.name.lowercase(Locale.getDefault())

            // 1. Direct Keyword Substring Match (e.g. "maggi", "oreo", "bourbon", "jim jam", "surf excel", "dettol", "tata", "atta", "rice", "lays")
            val pWords = pNameLower.split(Regex("""[\s\-_,.:;/\]+""")).filter { it.length >= 3 && it !in finePrintNoise }
            if (pWords.isEmpty()) continue

            // Check if full product name is inside scanned text
            if (fullTextLower.contains(pNameLower)) {
                matchedProducts.add(Pair(product, 1.0f))
                continue
            }

            // Check primary brand word match (e.g. "maggi", "oreo", "bourbon", "amul", "lays", "surf")
            val firstWord = pWords.first()
            if (firstWord.length >= 4 && (fullTextLower.contains(firstWord) || brandTokens.contains(firstWord))) {
                matchedProducts.add(Pair(product, 0.85f))
                continue
            }

            // Check token intersection score
            val matchedTokens = pWords.filter { w -> fullTextLower.contains(w) || brandTokens.contains(w) }
            val score = matchedTokens.size.toFloat() / pWords.size.toFloat()
            if (score >= threshold) {
                matchedProducts.add(Pair(product, score))
            }
        }

        return matchedProducts.sortedByDescending { it.second }.map { it.first }
    }
}
