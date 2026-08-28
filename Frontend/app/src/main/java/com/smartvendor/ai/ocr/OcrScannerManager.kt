package com.smartvendor.ai.ocr

import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartvendor.ai.model.Product
import com.smartvendor.ai.network.models.MasterCatalogResponse
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

    // Ignore tiny fine-print words & ambient packaging noise
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
            imageProxy.close()
            onNotFound()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                imageProxy.close()

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

                // 1. Extract Price & Quantity
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
                    allLines.forEach { line ->
                        if (line.text.contains("₹") || line.text.contains("Rs", ignoreCase = true) || line.text.contains("MRP", ignoreCase = true)) {
                            val match = standalonePriceRegex.find(line.text)
                            if (match != null) {
                                val p = match.groupValues[1].toDoubleOrNull()
                                if (p != null && p in 1.0..9999.0) detectedPrice = p
                            }
                        }
                    }
                }

                // 2. Strict Font-Size Filter: Line must be at least 50% of the largest font on packet
                val lineHeights = allLines.map { (it.boundingBox?.height() ?: 0) }
                val maxFontHeight = lineHeights.maxOrNull() ?: 0
                val dominantHeightThreshold = (maxFontHeight * 0.50f).coerceAtLeast(24f)

                val mainTitleLines = allLines.filter { line ->
                    val h = line.boundingBox?.height() ?: 0
                    val text = line.text.trim()
                    h >= dominantHeightThreshold && text.length in 3..40 && !priceRegex.containsMatchIn(text)
                }

                val dominantText = mainTitleLines.joinToString(" ") { it.text }
                val brandWords = dominantText.lowercase(Locale.getDefault())
                    .split(Regex("""[\s\-_,.:;/\]+"""))
                    .filter { it.length >= 4 && it !in finePrintNoise && it.all { c -> c.isLetter() } }

                val topTitle = mainTitleLines.maxByOrNull { (it.boundingBox?.width() ?: 0) * (it.boundingBox?.height() ?: 0) }?.text ?: ""

                if (brandWords.isNotEmpty()) {
                    onSuccess(
                        OcrResult(
                            dominantBrandKeywords = brandWords,
                            topBrandTitle = topTitle,
                            fullScannedText = dominantText,
                            detectedPrice = detectedPrice,
                            quantityUnit = detectedUnit
                        )
                    )
                } else {
                    onNotFound()
                }
            }
            .addOnFailureListener { e ->
                imageProxy.close()
                Log.e(TAG, "OCR error", e)
                onError(e)
            }
    }

    /**
     * Matches dominant brand keywords strictly against active Store Inventory
     */
    fun findRankedInventoryMatches(
        ocrResult: OcrResult,
        inventoryProducts: List<Product>,
        threshold: Float = 0.70f
    ): List<Product> {
        if (inventoryProducts.isEmpty() || ocrResult.dominantBrandKeywords.isEmpty()) return emptyList()

        val fullTextLower = ocrResult.fullScannedText.lowercase(Locale.getDefault())
        val brandTokens = ocrResult.dominantBrandKeywords.toSet()

        val scoredMatches = mutableListOf<Pair<Product, Float>>()

        for (product in inventoryProducts) {
            val pNameLower = product.name.lowercase(Locale.getDefault())
            val pTokens = pNameLower.split(Regex("""[\s\-_,.:;/\]+""")).filter { it.length >= 4 && it !in finePrintNoise }

            if (pTokens.isEmpty()) continue

            // Direct exact token matching
            val intersection = pTokens.filter { token -> brandTokens.any { it.contains(token) || token.contains(it) } }
            val score = intersection.size.toFloat() / pTokens.size.toFloat()

            if (score >= threshold) {
                scoredMatches.add(Pair(product, score))
            }
        }

        return scoredMatches.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * Matches dominant brand keywords strictly against Master Catalog
     */
    fun findRankedCatalogMatches(
        ocrResult: OcrResult,
        catalogItems: List<MasterCatalogResponse>,
        threshold: Float = 0.70f
    ): List<MasterCatalogResponse> {
        if (catalogItems.isEmpty() || ocrResult.dominantBrandKeywords.isEmpty()) return emptyList()

        val brandTokens = ocrResult.dominantBrandKeywords.toSet()
        val scoredMatches = mutableListOf<Pair<MasterCatalogResponse, Float>>()

        for (item in catalogItems) {
            val nameLower = item.name.lowercase(Locale.getDefault())
            val cTokens = nameLower.split(Regex("""[\s\-_,.:;/\]+""")).filter { it.length >= 4 && it !in finePrintNoise }

            if (cTokens.isEmpty()) continue

            val intersection = cTokens.filter { token -> brandTokens.any { it.contains(token) || token.contains(it) } }
            val score = intersection.size.toFloat() / cTokens.size.toFloat()

            if (score >= threshold) {
                scoredMatches.add(Pair(item, score))
            }
        }

        return scoredMatches.sortedByDescending { it.second }.map { it.first }
    }
}
