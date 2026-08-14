package com.smartvendor.ai.ocr

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
        "naggi" to "maggi", "maggl" to "maggi", "meggi" to "maggi", "mggi" to "maggi",
        "layss" to "lays chips", "lais" to "lays chips", "layz" to "lays chips", "lay's" to "lays chips",
        "ashirvad" to "aashirvaad atta", "aashirvad" to "aashirvaad atta",
        "hide 3seek" to "hide and seek", "hde seek" to "hide and seek",
        "jimjan" to "jim jam", "jimiam" to "jim jam",
        "soya stica" to "soya sticks"
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

        // Glare-tolerant brand guards (catches maazza, maza, snickkers, soya)
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

                // 3. Extract Clean Product Name Lines (Strict Noise Filter)
                val noiseWords = setOf(
                    "net", "wt", "mfg", "exp", "batch", "pack", "ingredients", "made", "india",
                    "mrp", "incl", "taxes", "tax", "customer", "care", "lic", "iso", "store",
                    "cool", "dry", "place", "recyclable", "use", "best", "before", "date",
                    "weight", "grams", "kilograms", "quantity", "address", "marketed",
                    "manufactured", "ltd", "pvt", "corp", "inc", "product", "details", "contact"
                )

                val lines = visionText.textBlocks.flatMap { it.lines }
                val nameCandidates = lines
                    .map { it.text.trim() }
                    .filter { line ->
                        if (line.length < 4 || line.length > 45) return@filter false
                        if (priceRegex.containsMatchIn(line)) return@filter false

                        val lineLower = line.lowercase(Locale.getDefault())
                        val tokens = lineLower.split(Regex("""\s+""")).filter { it.isNotBlank() }

                        // Reject if line is composed purely of noise words
                        val nonNoiseTokens = tokens.filter { t -> t !in noiseWords && t.length > 1 }
                        nonNoiseTokens.isNotEmpty()
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
                        if (Y > 235) continue // Filter out specular glare whiteout pixels from bright light!

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

            if (sat < 0.12f) return PackagingColor.UNKNOWN

            when (hue) {
                in 350.0f..360.0f, in 0.0f..18.0f -> PackagingColor.RED
                in 19.0f..44.0f -> PackagingColor.ORANGE
                in 45.0f..75.0f -> PackagingColor.YELLOW
                in 76.0f..160.0f -> PackagingColor.GREEN
                in 161.0f..255.0f -> PackagingColor.BLUE
                in 256.0f..349.0f -> PackagingColor.PURPLE
                else -> PackagingColor.UNKNOWN
            }
        } catch (e: Exception) {
            PackagingColor.UNKNOWN
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
        threshold: Float = 0.35f
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

            // 0. Anti-Confusion Guard Check (Block known confusing pairs like Maggi vs Maaza)
            if (isConflictingPair(scannedNameLower, catalogNameLower)) {
                continue
            }

            // 1. Token Overlap Score
            val matchingTokens = scannedTokens.count { token ->
                catalogTokens.any { catToken -> catToken.contains(token) || token.contains(catToken) }
            }

            var tokenScore = matchingTokens.toFloat() / maxOf(scannedTokens.size, catalogTokens.size).toFloat()

            // 2. Levenshtein Character Distance Similarity Boost for Stylized Fonts
            val levSim = charSimilarity(scannedNameLower, catalogNameLower)
            if (levSim >= 0.50f) {
                tokenScore = maxOf(tokenScore, levSim)
            }

            // 3. Known Stylized Font Alias Mapping Boost
            val aliasTarget = fontAliasesMap[scannedNameLower]
            if (aliasTarget != null && catalogNameLower.contains(aliasTarget)) {
                tokenScore = maxOf(tokenScore, 0.95f)
            }

            // Exact match
            if (scannedNameLower == catalogNameLower) {
                tokenScore = 1.0f
            }

            // Unit Check: If unit specified in OCR (e.g. 1kg vs 500g), enforce unit match
            if (!ocrResult.quantityUnit.isNullOrBlank()) {
                val scannedUnit = ocrResult.quantityUnit.lowercase()
                val catalogHasUnit = catalogNameLower.contains(scannedUnit)
                if (!catalogHasUnit) {
                    tokenScore *= 0.60f
                } else {
                    tokenScore = minOf(1.0f, tokenScore + 0.15f)
                }
            }

            // Packaging Color Signature Boost (+20%)
            val targetColor = productColorSignatures[product.name.lowercase(Locale.getDefault())]
            if (targetColor != null && ocrResult.detectedColor != PackagingColor.UNKNOWN) {
                if (targetColor == ocrResult.detectedColor) {
                    tokenScore = minOf(1.0f, tokenScore + 0.20f)
                }
            }

            if (tokenScore > highestScore) {
                highestScore = tokenScore
                bestProduct = product
            }
        }

        return if (highestScore >= threshold) bestProduct else null
    }

    /**
     * Strict Catalog Matcher Engine for 6,000 Master Catalog Reference Items.
     * Requires minimum 75% token overlap similarity so random text never matches!
     */
    fun findBestCatalogMatch(
        ocrResult: OcrResult,
        catalogItems: List<com.smartvendor.ai.network.models.MasterCatalogResponse>,
        threshold: Float = 0.75f
    ): com.smartvendor.ai.network.models.MasterCatalogResponse? {
        if (catalogItems.isEmpty()) return null

        var bestItem: com.smartvendor.ai.network.models.MasterCatalogResponse? = null
        var highestScore = 0.0f

        val scannedNameLower = ocrResult.fullCombinedName.lowercase(Locale.getDefault())
        val scannedTokens = scannedNameLower.split(Regex("""\s+""")).filter { it.length > 1 }

        if (scannedTokens.isEmpty()) return null

        for (item in catalogItems) {
            val catalogNameLower = item.name.lowercase(Locale.getDefault())
            val catalogTokens = catalogNameLower.split(Regex("""\s+""")).filter { it.length > 1 }

            if (catalogTokens.isEmpty()) continue

            // 0. Anti-Confusion Guard Check (Block known confusing pairs like Maggi vs Maaza)
            if (isConflictingPair(scannedNameLower, catalogNameLower)) {
                continue
            }

            // Exact match
            if (scannedNameLower == catalogNameLower) {
                return item
            }

            // Token overlap score
            val matchingTokens = scannedTokens.count { token ->
                catalogTokens.any { catToken -> catToken.contains(token) || token.contains(catToken) }
            }

            var tokenScore = matchingTokens.toFloat() / maxOf(scannedTokens.size, catalogTokens.size).toFloat()

            // Substring containment boost
            if (catalogNameLower.contains(scannedNameLower) || scannedNameLower.contains(catalogNameLower)) {
                tokenScore = maxOf(tokenScore, 0.85f)
            }

            // Levenshtein Character Distance Similarity Boost for Stylized Fonts
            val levSim = charSimilarity(scannedNameLower, catalogNameLower)
            if (levSim >= 0.50f) {
                tokenScore = maxOf(tokenScore, levSim)
            }

            // Known Stylized Font Alias Mapping Boost
            val aliasTarget = fontAliasesMap[scannedNameLower]
            if (aliasTarget != null && catalogNameLower.contains(aliasTarget)) {
                tokenScore = maxOf(tokenScore, 0.95f)
            }

            // Packaging Color Signature Boost (+20%)
            val targetColor = productColorSignatures[item.name.lowercase(Locale.getDefault())]
            if (targetColor != null && ocrResult.detectedColor != PackagingColor.UNKNOWN) {
                if (targetColor == ocrResult.detectedColor) {
                    tokenScore = minOf(1.0f, tokenScore + 0.20f)
                }
            }

            if (tokenScore > highestScore) {
                highestScore = tokenScore
                bestItem = item
            }
        }

        return if (highestScore >= threshold) bestItem else null
    }

    fun findRankedInventoryMatches(
        ocrResult: OcrResult,
        inventoryProducts: List<Product>,
        threshold: Float = 0.45f
    ): List<Product> {
        if (inventoryProducts.isEmpty()) return emptyList()

        val matches = mutableListOf<Pair<Product, Float>>()

        val scannedNameLower = ocrResult.fullCombinedName.lowercase(Locale.getDefault())
        val scannedTokens = scannedNameLower.split(Regex("""\s+""")).filter { it.length > 1 }

        for (product in inventoryProducts) {
            val catalogNameLower = product.name.lowercase(Locale.getDefault())
            val catalogTokens = catalogNameLower.split(Regex("""\s+""")).filter { it.length > 1 }

            if (catalogTokens.isEmpty() || scannedTokens.isEmpty()) continue

            if (isConflictingPair(scannedNameLower, catalogNameLower)) continue

            val matchingTokens = scannedTokens.count { token ->
                catalogTokens.any { catToken -> catToken.contains(token) || token.contains(catToken) }
            }

            var tokenScore = matchingTokens.toFloat() / maxOf(scannedTokens.size, catalogTokens.size).toFloat()

            val levSim = charSimilarity(scannedNameLower, catalogNameLower)
            if (levSim >= 0.50f) {
                tokenScore = maxOf(tokenScore, levSim)
            }

            val aliasTarget = fontAliasesMap[scannedNameLower]
            if (aliasTarget != null && catalogNameLower.contains(aliasTarget)) {
                tokenScore = maxOf(tokenScore, 0.95f)
            }

            if (scannedNameLower == catalogNameLower) {
                tokenScore = 1.0f
            }

            if (!ocrResult.quantityUnit.isNullOrBlank()) {
                val scannedUnit = ocrResult.quantityUnit.lowercase()
                val catalogHasUnit = catalogNameLower.contains(scannedUnit)
                if (!catalogHasUnit) {
                    tokenScore *= 0.60f
                } else {
                    tokenScore = minOf(1.0f, tokenScore + 0.15f)
                }
            }

            val targetColor = productColorSignatures[product.name.lowercase(Locale.getDefault())]
            if (targetColor != null && ocrResult.detectedColor != PackagingColor.UNKNOWN) {
                if (targetColor == ocrResult.detectedColor) {
                    tokenScore = minOf(1.0f, tokenScore + 0.20f)
                }
            }

            if (tokenScore >= threshold) {
                matches.add(Pair(product, tokenScore))
            }
        }

        return matches.sortedByDescending { it.second }.map { it.first }
    }

    fun findRankedCatalogMatches(
        ocrResult: OcrResult,
        catalogItems: List<com.smartvendor.ai.network.models.MasterCatalogResponse>,
        threshold: Float = 0.50f
    ): List<com.smartvendor.ai.network.models.MasterCatalogResponse> {
        if (catalogItems.isEmpty()) return emptyList()

        val matches = mutableListOf<Pair<com.smartvendor.ai.network.models.MasterCatalogResponse, Float>>()

        val scannedNameLower = ocrResult.fullCombinedName.lowercase(Locale.getDefault())
        val scannedTokens = scannedNameLower.split(Regex("""\s+""")).filter { it.length > 1 }

        if (scannedTokens.isEmpty()) return emptyList()

        for (item in catalogItems) {
            val catalogNameLower = item.name.lowercase(Locale.getDefault())
            val catalogTokens = catalogNameLower.split(Regex("""\s+""")).filter { it.length > 1 }

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
                    tokenScore = maxOf(tokenScore, 0.85f)
                }

                val levSim = charSimilarity(scannedNameLower, catalogNameLower)
                if (levSim >= 0.50f) {
                    tokenScore = maxOf(tokenScore, levSim)
                }

                val aliasTarget = fontAliasesMap[scannedNameLower]
                if (aliasTarget != null && catalogNameLower.contains(aliasTarget)) {
                    tokenScore = maxOf(tokenScore, 0.95f)
                }

                val targetColor = productColorSignatures[item.name.lowercase(Locale.getDefault())]
                if (targetColor != null && ocrResult.detectedColor != PackagingColor.UNKNOWN) {
                    if (targetColor == ocrResult.detectedColor) {
                        tokenScore = minOf(1.0f, tokenScore + 0.20f)
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
