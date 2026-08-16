package com.smartvendor.ai.ocr

import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartvendor.ai.model.Product
import java.util.Locale

enum class PackagingColor {
    YELLOW,   // Maggi, Lays, Good Day, Parle G, Frooti, Maaza, Everest Turmeric
    BLUE,     // Oreo, Dairy Milk, Surf Excel, Tata Salt, Sprite, Thums Up
    RED,      // KitKat, Red Label Tea, Coca Cola, Kissan Ketchup, Appy Fizz, Vim Bar
    GREEN,    // Aashirvaad Atta, Patanjali Atta, Moong Dal
    PURPLE,   // Dairy Milk Silk, 5 Star, Jim Jam
    ORANGE,   // Kurkure, Bourbon, Hide and Seek, Haldiram Bhujia, Soya Sticks
    UNKNOWN
}

data class OcrResult(
    val productName: String,
    val quantityUnit: String? = null,
    val fullCombinedName: String,
    val price: Double? = null,
    val matchScore: Float = 0.0f,
    val detectedColor: PackagingColor = PackagingColor.UNKNOWN
)

class OcrScannerManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val productColorSignatures = mapOf(
        "oreo" to PackagingColor.BLUE,
        "maggi" to PackagingColor.YELLOW,
        "lays chips" to PackagingColor.YELLOW,
        "lays" to PackagingColor.YELLOW,
        "kitkat" to PackagingColor.RED,
        "good day" to PackagingColor.YELLOW,
        "parle g" to PackagingColor.YELLOW,
        "dairy milk" to PackagingColor.BLUE,
        "dairy milk silk" to PackagingColor.PURPLE,
        "jim jam" to PackagingColor.PURPLE,
        "jimjam" to PackagingColor.PURPLE,
        "soya sticks" to PackagingColor.ORANGE,
        "hide and seek" to PackagingColor.ORANGE,
        "bourbon" to PackagingColor.ORANGE,
        "kurkure" to PackagingColor.ORANGE,
        "aashirvaad atta" to PackagingColor.GREEN,
        "patanjali atta" to PackagingColor.GREEN,
        "surf excel" to PackagingColor.BLUE,
        "tata salt" to PackagingColor.BLUE,
        "coca cola" to PackagingColor.RED,
        "frooti" to PackagingColor.YELLOW,
        "maaza" to PackagingColor.YELLOW,
        "appy fizz" to PackagingColor.RED,
        "sprite" to PackagingColor.GREEN,
        "thums up" to PackagingColor.BLUE,
        "red label tea" to PackagingColor.RED,
        "dettol soap" to PackagingColor.GREEN
    )

    private val fontAliasesMap = mapOf(
        "ore0" to "oreo", "0reo" to "oreo", "oreq" to "oreo", "orco" to "oreo", "cakoy" to "oreo", "qikany" to "oreo",
        "naggi" to "maggi", "maggl" to "maggi", "meggi" to "maggi", "mggi" to "maggi", "2-minute" to "maggi", "2 minute" to "maggi", "masala maggi" to "maggi",
        "layss" to "lays chips", "lais" to "lays chips", "layz" to "lays chips", "lay's" to "lays chips",
        "ashirvad" to "aashirvaad atta",
        "hide 3seek" to "hide and seek", "hde seek" to "hide and seek", "hide & seek" to "hide and seek",
        "jimjan" to "jim jam", "jimiam" to "jim jam", "jimyam" to "jim jam", "jimjam" to "jim jam", "naughty jam" to "jim jam",
        "soya stica" to "soya sticks", "soya stic" to "soya sticks",
        "surf excel" to "surf excel", "surf" to "surf excel", "excel" to "surf excel",
        "appy" to "appy fizz", "fizz" to "appy fizz", "appe fizz" to "appy fizz"
    )

    private val noiseWords = setOf(
        "net", "wt", "mfg", "exp", "batch", "pack", "ingredients", "made", "india",
        "mrp", "incl", "taxes", "tax", "customer", "care", "lic", "iso", "store",
        "cool", "dry", "place", "recyclable", "use", "best", "before", "date",
        "weight", "grams", "kilograms", "quantity", "address", "marketed",
        "manufactured", "ltd", "pvt", "corp", "inc", "product", "details", "contact",
        "nutrition", "nutritional", "facts", "information", "per", "serve", "serving",
        "size", "energy", "protein", "carbohydrate", "sugar", "fat", "saturated",
        "trans", "cholesterol", "sodium", "calcium", "iron", "vitamins", "minerals",
        "vegetarian", "veg", "green", "dot", "fssai", "license", "reg", "tm",
        "copyright", "all", "rights", "reserved", "keep", "away", "direct", "sunlight",
        "hygienic", "conditions", "dispose", "dustbin", "scan", "qr", "feedback",
        "helpline", "toll", "free", "email", "website", "www", "com", "in",
        "barcode", "dop", "pkd", "by", "months", "from", "packaging", "super",
        "saver", "offer", "inside", "new", "improved", "taste", "delicious",
        "crunchy", "crispy", "snack", "tasty", "yummy", "original", "formula",
        "imported", "distributed", "packed", "contains", "added", "flavour",
        "artificial", "natural", "identical", "flavouring", "substances", "preservative",
        "acidity", "regulator", "emulsifier", "stabilizer", "thickener", "color", "colour",
        "allergen", "advice", "may", "contain", "traces", "of", "milk", "wheat", "soy",
        "nuts", "gluten", "peanuts", "sesame", "warning", "caution", "safety", "seal"
    )

    fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1].equals(s2[j - 1], ignoreCase = true)) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[len1][len2]
    }

    fun charSimilarity(s1: String, s2: String): Float {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0f
        val dist = levenshteinDistance(s1.lowercase(Locale.getDefault()), s2.lowercase(Locale.getDefault()))
        return 1.0f - (dist.toFloat() / maxLen.toFloat())
    }

    private val antiConfusionGuards = listOf(
        Pair("maggi", "maaza"),
        Pair("maaza", "maggi"),
        Pair("maggi", "munch"),
        Pair("munch", "maggi"),
        Pair("soya sticks", "snickers"),
        Pair("snickers", "soya sticks"),
        Pair("soya", "snickers"),
        Pair("snickers", "soya"),
        Pair("lays chips", "lizol"),
        Pair("lizol", "lays chips"),
        Pair("colgate", "close up"),
        Pair("close up", "colgate"),
        Pair("surf excel", "soya sticks"),
        Pair("soya sticks", "surf excel")
    )

    private fun isConflictingPair(scanned: String, target: String): Boolean {
        val sRaw = scanned.lowercase(Locale.getDefault())
        val sNorm = sRaw.replace(Regex("(.)\\1+"), "$1")
        val tNorm = target.lowercase(Locale.getDefault()).replace(Regex("(.)\\1+"), "$1")

        for ((word1, word2) in antiConfusionGuards) {
            val w1 = word1.replace(Regex("(.)\\1+"), "$1")
            val w2 = word2.replace(Regex("(.)\\1+"), "$1")
            if ((sRaw.contains(word1) || sNorm.contains(w1)) && tNorm.contains(w2)) {
                return true
            }
        }

        if ((sRaw.contains("soya") || sNorm.contains("soya")) && !tNorm.contains("soya")) return true
        if ((sRaw.contains("snicker") || sNorm.contains("sniker")) && !tNorm.contains("sniker")) return true
        if ((sRaw.contains("maaza") || sNorm.contains("maza")) && !tNorm.contains("maza")) return true
        if ((sRaw.contains("maggi") || sNorm.contains("magi")) && !tNorm.contains("magi")) return true

        return false
    }

    private val priceRegex = Regex(
        """(?:₹|MRP|Rs\.?|INR)\s*[:\.]?\s*(\d+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val standalonePriceRegex = Regex("""\b(\d{1,4}(?:\.\d{1,2})?)\b""")

    private val quantityUnitRegex = Regex(
        """\b(\d+(?:\.\d+)?\s*(?:kg|g|gm|l|ml|ltr|litre|pack|pc|pcs|pouch|sachet))\b""",
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

                // 3. Extract Clean Brand/Product Lines (Sort by Bounding Box Area -> Biggest font title first!)
                val allLines = visionText.textBlocks.flatMap { it.lines }
                val validLines = allLines
                    .filter { line ->
                        val text = line.text.trim()
                        if (text.length < 3 || text.length > 40) return@filter false
                        if (priceRegex.containsMatchIn(text)) return@filter false

                        val lineLower = text.lowercase(Locale.getDefault())
                        val tokens = lineLower.split(Regex("""[\s\-_,.:;]+""")).filter { it.isNotBlank() }

                        // Check if line is meaningful non-noise text
                        val nonNoiseTokens = tokens.filter { t -> t !in noiseWords && t.length >= 2 }
                        nonNoiseTokens.isNotEmpty()
                    }
                    // Sort descending by text area so largest brand logo font is ranked first!
                    .sortedByDescending { line ->
                        val box = line.boundingBox ?: Rect()
                        box.width() * box.height()
                    }

                if (validLines.isNotEmpty()) {
                    val topCandidateText = validLines.first().text.trim()
                    detectedName = topCandidateText
                        .lowercase(Locale.getDefault())
                        .split(" ")
                        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                }

                // Fallback Price detection
                if (detectedPrice == null) {
                    allLines.forEach { line ->
                        if (line.text.contains("₹") || line.text.contains("Rs", ignoreCase = true)) {
                            val match = standalonePriceRegex.find(line.text)
                            if (match != null) {
                                detectedPrice = match.groupValues[1].toDoubleOrNull()
                            }
                        }
                    }
                }

                val sampledColor = detectDominantColor(mediaImage)

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
                            price = detectedPrice,
                            detectedColor = sampledColor
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

    private fun detectDominantColor(yuvImage: android.media.Image): PackagingColor {
        return try {
            val yBuffer = yuvImage.planes[0].buffer
            val uBuffer = yuvImage.planes[1].buffer
            val vBuffer = yuvImage.planes[2].buffer

            val width = yuvImage.width
            val height = yuvImage.height

            var totalR = 0L
            var totalG = 0L
            var totalB = 0L
            var sampleCount = 0

            val startX = width / 4
            val endX = (width * 3) / 4
            val startY = height / 4
            val endY = (height * 3) / 4

            val stepX = maxOf(1, (endX - startX) / 8)
            val stepY = maxOf(1, (endY - startY) / 8)

            for (y in startY until endY step stepY) {
                for (x in startX until endX step stepX) {
                    val yIndex = y * width + x
                    val uvIndex = (y / 2) * (width / 2) + (x / 2)

                    if (yIndex < yBuffer.capacity() && uvIndex < uBuffer.capacity() && uvIndex < vBuffer.capacity()) {
                        val Y = yBuffer.get(yIndex).toInt() and 0xFF
                        if (Y > 235) continue

                        val U = uBuffer.get(uvIndex).toInt() and 0xFF - 128
                        val V = vBuffer.get(uvIndex).toInt() and 0xFF - 128

                        val R = (Y + 1.370705 * V).toInt().coerceIn(0, 255)
                        val G = (Y - 0.337633 * U - 0.698001 * V).toInt().coerceIn(0, 255)
                        val B = (Y + 1.732446 * U).toInt().coerceIn(0, 255)

                        totalR += R
                        totalG += G
                        totalB += B
                        sampleCount++
                    }
                }
            }

            if (sampleCount == 0) return PackagingColor.UNKNOWN

            val avgR = (totalR / sampleCount).toFloat()
            val avgG = (totalG / sampleCount).toFloat()
            val avgB = (totalB / sampleCount).toFloat()

            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(avgR.toInt(), avgG.toInt(), avgB.toInt(), hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            if (sat < 0.20f) return PackagingColor.UNKNOWN

            when {
                hue in 345f..360f || hue in 0f..20f -> PackagingColor.RED
                hue in 21f..50f -> PackagingColor.ORANGE
                hue in 51f..75f -> PackagingColor.YELLOW
                hue in 76f..160f -> PackagingColor.GREEN
                hue in 180f..260f -> PackagingColor.BLUE
                hue in 261f..320f -> PackagingColor.PURPLE
                else -> PackagingColor.UNKNOWN
            }
        } catch (e: Exception) {
            PackagingColor.UNKNOWN
        }
    }

    /**
     * Ranked Store Inventory Matcher Engine with High-Precision Filtering.
     */
    fun findRankedInventoryMatches(
        ocrResult: OcrResult,
        inventoryProducts: List<Product>,
        threshold: Float = 0.60f
    ): List<Product> {
        if (inventoryProducts.isEmpty()) return emptyList()

        val matches = mutableListOf<Pair<Product, Float>>()

        val scannedNameLower = ocrResult.fullCombinedName.lowercase(Locale.getDefault())
        val scannedTokens = scannedNameLower.split(Regex("""[\s\-_,.:;]+""")).filter { it.length >= 2 }

        if (scannedTokens.isEmpty()) return emptyList()

        for (product in inventoryProducts) {
            val catalogNameLower = product.name.lowercase(Locale.getDefault())
            val catalogTokens = catalogNameLower.split(Regex("""[\s\-_,.:;]+""")).filter { it.length >= 2 }

            if (catalogTokens.isEmpty()) continue
            if (isConflictingPair(scannedNameLower, catalogNameLower)) continue

            // 1. Direct Keyword / Token Containment (e.g. "maggi" in "Maggi 2-Minute")
            val matchingTokens = scannedTokens.count { token ->
                catalogTokens.any { catToken -> catToken.contains(token) || token.contains(catToken) }
            }

            var tokenScore = if (matchingTokens > 0) {
                matchingTokens.toFloat() / maxOf(scannedTokens.size, catalogTokens.size).toFloat()
            } else 0f

            // 2. Substring Match Boost
            if (catalogNameLower.contains(scannedNameLower) || scannedNameLower.contains(catalogNameLower)) {
                tokenScore = maxOf(tokenScore, 0.90f)
            }

            // 3. Known Stylized Font Alias Mapping Boost
            val aliasTarget = fontAliasesMap[scannedNameLower]
            if (aliasTarget != null && catalogNameLower.contains(aliasTarget)) {
                tokenScore = maxOf(tokenScore, 0.95f)
            }

            // 4. Levenshtein Character Distance Similarity Boost
            val levSim = charSimilarity(scannedNameLower, catalogNameLower)
            if (levSim >= 0.65f) {
                tokenScore = maxOf(tokenScore, levSim)
            }

            if (scannedNameLower == catalogNameLower) {
                tokenScore = 1.0f
            }

            // Color boost
            val targetColor = productColorSignatures[product.name.lowercase(Locale.getDefault())]
            if (targetColor != null && ocrResult.detectedColor != PackagingColor.UNKNOWN) {
                if (targetColor == ocrResult.detectedColor) {
                    tokenScore = minOf(1.0f, tokenScore + 0.15f)
                }
            }

            if (tokenScore >= threshold) {
                matches.add(Pair(product, tokenScore))
            }
        }

        return matches.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * Strict Catalog Matcher Engine for 6,000 Master Catalog Reference Items.
     */
    fun findRankedCatalogMatches(
        ocrResult: OcrResult,
        catalogItems: List<com.smartvendor.ai.network.models.MasterCatalogResponse>,
        threshold: Float = 0.75f
    ): List<com.smartvendor.ai.network.models.MasterCatalogResponse> {
        if (catalogItems.isEmpty()) return emptyList()

        val matches = mutableListOf<Pair<com.smartvendor.ai.network.models.MasterCatalogResponse, Float>>()

        val scannedNameLower = ocrResult.fullCombinedName.lowercase(Locale.getDefault())
        val scannedTokens = scannedNameLower.split(Regex("""[\s\-_,.:;]+""")).filter { it.length >= 2 }

        if (scannedTokens.isEmpty()) return emptyList()

        for (item in catalogItems) {
            val catalogNameLower = item.name.lowercase(Locale.getDefault())
            val catalogTokens = catalogNameLower.split(Regex("""[\s\-_,.:;]+""")).filter { it.length >= 2 }

            if (catalogTokens.isEmpty()) continue
            if (isConflictingPair(scannedNameLower, catalogNameLower)) continue

            var tokenScore = 0.0f

            if (scannedNameLower == catalogNameLower) {
                tokenScore = 1.0f
            } else {
                val matchingTokens = scannedTokens.count { token ->
                    catalogTokens.any { catToken -> catToken.contains(token) || token.contains(catToken) }
                }

                tokenScore = matchingTokens.toFloat() / maxOf(scannedTokens.size, catalogTokens.size).toFloat()

                if (catalogNameLower.contains(scannedNameLower) || scannedNameLower.contains(catalogNameLower)) {
                    tokenScore = maxOf(tokenScore, 0.90f)
                }

                val levSim = charSimilarity(scannedNameLower, catalogNameLower)
                if (levSim >= 0.70f) {
                    tokenScore = maxOf(tokenScore, levSim)
                }

                val aliasTarget = fontAliasesMap[scannedNameLower]
                if (aliasTarget != null && catalogNameLower.contains(aliasTarget)) {
                    tokenScore = maxOf(tokenScore, 0.95f)
                }

                val targetColor = productColorSignatures[item.name.lowercase(Locale.getDefault())]
                if (targetColor != null && ocrResult.detectedColor != PackagingColor.UNKNOWN) {
                    if (targetColor == ocrResult.detectedColor) {
                        tokenScore = minOf(1.0f, tokenScore + 0.10f)
                    }
                }
            }

            if (tokenScore >= threshold) {
                matches.add(Pair(item, tokenScore))
            }
        }

        return matches.sortedByDescending { it.second }.map { it.first }
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
